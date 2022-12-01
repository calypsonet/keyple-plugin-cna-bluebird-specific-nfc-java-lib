/* **************************************************************************************
 * Copyright (c) 2021 Calypso Networks Association https://calypsonet.org/
 *
 * See the NOTICE file(s) distributed with this work for additional information
 * regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ************************************************************************************** */
package org.calypsonet.keyple.plugin.bluebird

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.bluebird.extnfc.ExtNfcReader
import java.util.concurrent.atomic.AtomicBoolean
import org.eclipse.keyple.core.plugin.CardIOException
import org.eclipse.keyple.core.plugin.WaitForCardInsertionAutonomousReaderApi
import org.eclipse.keyple.core.plugin.spi.reader.ConfigurableReaderSpi
import org.eclipse.keyple.core.plugin.spi.reader.ReaderSpi
import org.eclipse.keyple.core.plugin.spi.reader.observable.ObservableReaderSpi
import org.eclipse.keyple.core.plugin.spi.reader.observable.state.insertion.WaitForCardInsertionAutonomousSpi
import org.eclipse.keyple.core.plugin.spi.reader.observable.state.processing.DontWaitForCardRemovalDuringProcessingSpi
import org.eclipse.keyple.core.plugin.spi.reader.observable.state.removal.WaitForCardRemovalNonBlockingSpi
import org.eclipse.keyple.core.util.Assert
import org.eclipse.keyple.core.util.HexUtil
import timber.log.Timber

/**
 * Implementation of the Bluebird Contactless Reader
 * @since 2.0.0
 */
@SuppressLint("WrongConstant")
internal class BluebirdContactlessReaderAdapter(activity: Activity) :
    BluebirdContactlessReader,
    ObservableReaderSpi,
    ConfigurableReaderSpi,
    WaitForCardInsertionAutonomousSpi,
    DontWaitForCardRemovalDuringProcessingSpi,
    WaitForCardRemovalNonBlockingSpi {

  private companion object {
    const val MIN_SDK_API_LEVEL_ECP = 28
  }

  private val nfcReader: ExtNfcReader
  private val nfcBroadcastReceiver: NfcBroadcastReceiver
  private val nfcEcp: ExtNfcReader.ECP?

  private lateinit var waitForCardInsertionAutonomousApi: WaitForCardInsertionAutonomousReaderApi

  private val isCardDiscovered = AtomicBoolean(false)

  private var lastTagTime: Long = 0
  private var lastTagData: String? = null

  private var currentTag: Tag? = null
  private var pollingProtocols: Int = 0

  private var vasupPayload: ByteArray? = null
  private var vasupMode: Byte? = null

  init {
    nfcReader = activity.getSystemService(ExtNfcReader.READER_SERVICE_NAME) as ExtNfcReader
    nfcReader.enable(true)
    nfcBroadcastReceiver = NfcBroadcastReceiver(activity)
    nfcEcp =
        if (Build.VERSION.SDK_INT >= MIN_SDK_API_LEVEL_ECP) {
          nfcReader.ecp
        } else {
          null
        }
  }

  /**
   * @see BluebirdContactlessReader.setSkyEcpVasupPayload
   * @since 2.1.0
   */
  override fun setSkyEcpVasupPayload(vasupPayload: ByteArray) {
    checkEcpAvailabilty()
    Assert.getInstance()
        .notNull(vasupPayload, "vasupPayload")
        .isInRange(vasupPayload.size, 5, 20, "vasupPayload")
    this.vasupPayload = vasupPayload
  }

  private fun checkEcpAvailabilty() {
    if (Build.VERSION.SDK_INT < MIN_SDK_API_LEVEL_ECP) {
      throw UnsupportedOperationException(
          "The terminal Android SDK API level must be higher than $MIN_SDK_API_LEVEL_ECP when using the ECP mode. Current API level is " +
              Build.VERSION.SDK_INT)
    }
  }

  /**
   * @see ConfigurableReaderSpi.isCurrentProtocol
   * @since 2.0.0
   */
  override fun isCurrentProtocol(readerProtocol: String): Boolean {
    val protocol: BluebirdSupportContactlessProtocols?
    try {
      protocol = BluebirdSupportContactlessProtocols.valueOf(readerProtocol)
    } catch (e: IllegalArgumentException) {
      return false
    }
    if ((protocol.value and pollingProtocols) != 0 ||
        (protocol == BluebirdSupportContactlessProtocols.ISO14443_4_SKY_ECP_A &&
            (pollingProtocols and BluebirdSupportContactlessProtocols.ISO_14443_4_A.value) != 0) ||
        (protocol == BluebirdSupportContactlessProtocols.ISO14443_4_SKY_ECP_B &&
            (pollingProtocols and BluebirdSupportContactlessProtocols.ISO_14443_4_B.value) != 0)) {
      return true
    }
    return false
  }

  /**
   * @see ConfigurableReaderSpi.isProtocolSupported
   * @since 2.0.0
   */
  override fun isProtocolSupported(readerProtocol: String): Boolean {
    return try {
      BluebirdSupportContactlessProtocols.valueOf(readerProtocol)
      true
    } catch (e: IllegalArgumentException) {
      false
    }
  }

  /**
   * @see ConfigurableReaderSpi.activateProtocol
   * @since 2.0.0
   */
  override fun activateProtocol(readerProtocol: String) {
    when (readerProtocol) {
      BluebirdSupportContactlessProtocols.ISO_14443_4_A.name ->
          pollingProtocols =
              pollingProtocols or BluebirdSupportContactlessProtocols.ISO_14443_4_A.value
      BluebirdSupportContactlessProtocols.ISO_14443_4_B.name ->
          pollingProtocols =
              pollingProtocols or BluebirdSupportContactlessProtocols.ISO_14443_4_B.value
      BluebirdSupportContactlessProtocols.INNOVATRON_B_PRIME.name ->
          pollingProtocols =
              pollingProtocols or BluebirdSupportContactlessProtocols.INNOVATRON_B_PRIME.value
      BluebirdSupportContactlessProtocols.ISO14443_4_SKY_ECP_A.name -> {
        checkEcpAvailabilty()
        if (vasupMode == ExtNfcReader.ECP.Mode.VASUP_B) {
          throw IllegalStateException("SKY ECP VASUP type B is set")
        }
        vasupPayload?.let {
          nfcEcp!!.setConfiguration(ExtNfcReader.ECP.Mode.VASUP_A, it)
          vasupMode = ExtNfcReader.ECP.Mode.VASUP_A
        }
            ?: throw IllegalStateException("SKY ECP VASUP payload was not set")
      }
      BluebirdSupportContactlessProtocols.ISO14443_4_SKY_ECP_B.name -> {
        checkEcpAvailabilty()
        if (vasupMode == ExtNfcReader.ECP.Mode.VASUP_A) {
          throw IllegalStateException("SKY ECP VASUP type A is set")
        }
        vasupPayload?.let {
          nfcEcp!!.setConfiguration(ExtNfcReader.ECP.Mode.VASUP_B, it)
          vasupMode = ExtNfcReader.ECP.Mode.VASUP_B
        }
            ?: throw java.lang.IllegalStateException("SKY ECP VASUP payload was not set")
      }
      else ->
          throw IllegalArgumentException("Activate protocol error: '$readerProtocol' not allowed")
    }
  }

  /**
   * @see ConfigurableReaderSpi.deactivateProtocol
   * @since 2.0.0
   */
  override fun deactivateProtocol(readerProtocol: String) {
    when (readerProtocol) {
      BluebirdSupportContactlessProtocols.ISO_14443_4_A.name ->
          pollingProtocols =
              pollingProtocols xor BluebirdSupportContactlessProtocols.ISO_14443_4_A.value
      BluebirdSupportContactlessProtocols.ISO_14443_4_B.name ->
          pollingProtocols =
              pollingProtocols xor BluebirdSupportContactlessProtocols.ISO_14443_4_B.value
      BluebirdSupportContactlessProtocols.INNOVATRON_B_PRIME.name ->
          pollingProtocols =
              pollingProtocols xor BluebirdSupportContactlessProtocols.INNOVATRON_B_PRIME.value
      BluebirdSupportContactlessProtocols.ISO14443_4_SKY_ECP_A.name,
      BluebirdSupportContactlessProtocols.ISO14443_4_SKY_ECP_B.name -> {
        checkEcpAvailabilty()
        nfcEcp!!.clearConfiguration()
        vasupMode = null
      }
      else ->
          throw IllegalArgumentException(
              "De-activate protocol error: '$readerProtocol' not allowed")
    }
  }

  /**
   * @see ReaderSpi.checkCardPresence
   * @since 2.0.0
   */
  override fun checkCardPresence(): Boolean {
    return currentTag != null
  }

  /**
   * @see ReaderSpi.getName
   * @since 2.0.0
   */
  override fun getName(): String = BluebirdContactlessReader.READER_NAME

  /**
   * @see ReaderSpi.transmitApdu
   * @since 2.0.0
   */
  override fun transmitApdu(apduIn: ByteArray): ByteArray {
    val result = nfcReader.transmit(apduIn)
    if (result.mData != null && result.mData.size > 256) {
      throw CardIOException("Transmit APDU error: unexpected response length: ${result.mData.size}")
    }
    return result.mData ?: throw CardIOException("Transmit APDU error: ${result.mResult}")
  }

  /**
   * @see ReaderSpi.isContactless
   * @since 2.0.0
   */
  override fun isContactless(): Boolean {
    return true
  }

  /**
   * @see ReaderSpi.onUnregister
   * @since 2.0.0
   */
  override fun onUnregister() {
    // Clear NFC reader
    if (nfcReader.isEnabled) {
      nfcReader.disconnect()
      nfcReader.enable(false)
    }
    // Clear NFC broadcast receiver
    nfcBroadcastReceiver.unregister()
  }

  /**
   * @see ReaderSpi.getPowerOnData
   * @since 2.0.0
   */
  override fun getPowerOnData(): String = lastTagData ?: ""

  /**
   * @see ReaderSpi.openPhysicalChannel
   * @since 2.0.0
   */
  override fun openPhysicalChannel() {
    val status = nfcReader.connect()
    if (status < 0) {
      throw CardIOException("Open physical channel error: $status")
    }
  }

  /**
   * @see ReaderSpi.isPhysicalChannelOpen
   * @since 2.0.0
   */
  override fun isPhysicalChannelOpen(): Boolean {
    return nfcReader.isConnected
  }

  /**
   * @see ReaderSpi.closePhysicalChannel
   * @since 2.0.0
   */
  override fun closePhysicalChannel() {
    val status = nfcReader.disconnect()
    if (status < 0) {
      Timber.v("Close physical channel error: $status")
    }
  }

  /** Start NFC reader and receiver scan */
  private fun startScan(pollingProtocols: Int, listener: (NfcResult) -> Unit) {
    try {
      nfcBroadcastReceiver.listener = listener
      nfcReader.cardTypeForScan = pollingProtocols
      var status = nfcReader.startScan()
      // If the reader is already started -> stop it then re-start it
      if (status == ExtNfcReader.ResultCode.ERROR_ALREADY_ON_SCANNING) {
        status = nfcReader.stopScan()
        if (status == ExtNfcReader.ResultCode.SUCCESS) {
          status = nfcReader.startScan()
        }
      }
      if (status == ExtNfcReader.ResultCode.SUCCESS) {
        // NFC reader started successfully -> register NFC broadcast receiver for tag listening
        if (!nfcBroadcastReceiver.isRegistered) {
          nfcBroadcastReceiver.register()
        }
      } else {
        // An error occurred during NFC reader start
        listener(NfcResultError(CardIOException("Card scan error: $status")))
      }
    } catch (e: Exception) {
      listener(NfcResultError(CardIOException("Card scan error: ${e.message}", e)))
    }
  }

  /**
   * @see ObservableReaderSpi.onStartDetection
   * @since 2.0.0
   */
  override fun onStartDetection() {
    Timber.d("Start card scan using polling protocols $pollingProtocols configuration")
    startScan(pollingProtocols) { nfcResult ->
      if (nfcResult is NfcResultSuccess) {
        Timber.d("Discovered tag: ${nfcResult.tag}")
        currentTag = nfcResult.tag
        lastTagTime = System.currentTimeMillis()
        lastTagData = HexUtil.toHex(nfcResult.tag.data)
        isCardDiscovered.set(true)
        waitForCardInsertionAutonomousApi.onCardInserted()
      } else if (nfcResult is NfcResultError) {
        throw nfcResult.error
      }
    }
  }

  /**
   * @see ObservableReaderSpi.onStopDetection
   * @since 2.0.0
   */
  override fun onStopDetection() {
    Timber.d("Stop card scan")
    nfcBroadcastReceiver.unregister()
  }

  /**
   * @see WaitForCardInsertionAutonomousSpi.connect
   * @since 2.0.0
   */
  override fun connect(
      waitForCardInsertionAutonomousReaderApi: WaitForCardInsertionAutonomousReaderApi
  ) {
    waitForCardInsertionAutonomousApi = waitForCardInsertionAutonomousReaderApi
  }

  /**
   * NFC broadcast receiver.
   * @since 2.0.0
   */
  private class NfcBroadcastReceiver(private val context: Context?) {

    private val broadcastReceiver =
        object : BroadcastReceiver() {
          override fun onReceive(context: Context, intent: Intent) {
            if (ExtNfcReader.Broadcast.EXTNFC_DETECTED_ACTION == intent.action) {
              listener?.let {
                val cardType = intent.getIntExtra(ExtNfcReader.Broadcast.EXTNFC_CARD_TYPE_KEY, -1)
                val tag =
                    Tag(
                        BluebirdSupportContactlessProtocols.fromValue(cardType),
                        intent.getByteArrayExtra(ExtNfcReader.Broadcast.EXTNFC_CARD_DATA_KEY))
                it(NfcResultSuccess(tag))
              }
            }
          }
        }

    var isRegistered: Boolean = false
    var listener: ((NfcResult) -> Unit)? = null

    fun register() {
      Timber.d("Register BB NFC broadcast receiver (already registered? $isRegistered)")
      if (isRegistered) {
        unregister()
      }
      if (context != null) {
        val filter = IntentFilter()
        filter.addAction(ExtNfcReader.Broadcast.EXTNFC_DETECTED_ACTION)
        context.registerReceiver(broadcastReceiver, filter)
        isRegistered = true
      }
    }

    fun unregister() {
      Timber.d("Unregister BB NFC broadcast receiver (already unregistered? ${!isRegistered})")
      if (!isRegistered) {
        return
      }
      isRegistered = false
      context?.unregisterReceiver(broadcastReceiver)
    }
  }
}
