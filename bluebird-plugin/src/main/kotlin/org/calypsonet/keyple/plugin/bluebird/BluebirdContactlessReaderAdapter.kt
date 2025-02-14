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
import com.bluebird.extnfc.ExtNfcReader.ResultCode
import java.util.concurrent.atomic.AtomicBoolean
import org.eclipse.keyple.core.plugin.CardIOException
import org.eclipse.keyple.core.plugin.CardInsertionWaiterAsynchronousApi
import org.eclipse.keyple.core.plugin.spi.reader.ConfigurableReaderSpi
import org.eclipse.keyple.core.plugin.spi.reader.observable.ObservableReaderSpi
import org.eclipse.keyple.core.plugin.spi.reader.observable.state.insertion.CardInsertionWaiterAsynchronousSpi
import org.eclipse.keyple.core.plugin.spi.reader.observable.state.removal.CardRemovalWaiterNonBlockingSpi
import org.eclipse.keyple.core.util.Assert
import org.eclipse.keyple.core.util.HexUtil
import timber.log.Timber

internal class BluebirdContactlessReaderAdapter(
  private val activity: Activity
) :
    BluebirdContactlessReader,
    ObservableReaderSpi,
    ConfigurableReaderSpi,
    CardInsertionWaiterAsynchronousSpi,
    CardRemovalWaiterNonBlockingSpi,
    BroadcastReceiver() {

  private companion object {
    const val MIN_SDK_API_LEVEL_ECP = 28
  }

  @SuppressLint("WrongConstant")
  private val nfcReader: ExtNfcReader =
      activity.getSystemService(ExtNfcReader.READER_SERVICE_NAME) as ExtNfcReader
  private val nfcEcp: ExtNfcReader.ECP? =
      if (Build.VERSION.SDK_INT >= MIN_SDK_API_LEVEL_ECP) {
        nfcReader.ecp
      } else {
        null
      }
  private val isCardDiscovered = AtomicBoolean(false)
  private var isBroadcastReceiverRegistered: Boolean = false
  private var isCardChannelOpen: Boolean = false

  private lateinit var waitForCardInsertionAutonomousApi: CardInsertionWaiterAsynchronousApi

  private var currentPowerOnData: String? = null

  private var currentTag: Tag? = null
  private var pollingProtocols: Int = 0

  private var vasupPayload: ByteArray? = null
  private var vasupMode: Byte? = null

  override fun setSkyEcpVasupPayload(vasupPayload: ByteArray) {
    checkExpAvailability()
    Assert.getInstance()
        .notNull(vasupPayload, "vasupPayload")
        .isInRange(vasupPayload.size, 5, 20, "vasupPayload")
    this.vasupPayload = vasupPayload
  }

  override fun onStartDetection() {
    Timber.d("Start card scan using polling protocols $pollingProtocols configuration")
    startScan()
  }

  private fun onTagDiscovered(nfcResult: NfcResult) {
    isCardChannelOpen = false
    if (nfcResult is NfcResultSuccess) {
      Timber.d("Discovered tag: ${nfcResult.tag}")
      currentTag = nfcResult.tag
      currentPowerOnData = HexUtil.toHex(nfcResult.tag.data)
      isCardDiscovered.set(true)
      waitForCardInsertionAutonomousApi.onCardInserted()
    } else if (nfcResult is NfcResultError) {
      throw nfcResult.error
    }
  }

  override fun onStopDetection() {
    Timber.d("Stop card scan")
    var status = nfcReader.stopScan()
    if (status != ResultCode.SUCCESS) {
      Timber.d("Error while stopping the scan: {$status: ${getNfcErrorMessage(status)}}")
    }
    status = nfcReader.BBextNfcCarrierOff()
    if (status != ResultCode.SUCCESS) {
      Timber.d("Error while setting the RF field off: {$status: ${getNfcErrorMessage(status)}}")
    }
    nfcReader.disconnect()
    nfcReader.enable(false)
    unregisterBroadcastReceiver()
  }

  override fun getName(): String = BluebirdContactlessReader.READER_NAME

  override fun openPhysicalChannel() {
    val status = nfcReader.connect()
    if (status < 0) {
      throw CardIOException("Open physical channel error: {$status: ${getNfcErrorMessage(status)}}")
    }
    isCardChannelOpen = true
  }

  override fun closePhysicalChannel() {
    nfcReader.disconnect()
    isCardChannelOpen = false
  }

  override fun isPhysicalChannelOpen(): Boolean {
    return isCardChannelOpen
  }

  override fun checkCardPresence(): Boolean {
    throw UnsupportedOperationException("checkCardPresence() is not supported")
  }

  override fun getPowerOnData(): String = currentPowerOnData ?: ""

  override fun transmitApdu(apduIn: ByteArray): ByteArray {
    val transmitResult = nfcReader.transmit(apduIn)
    if (transmitResult.mData != null && transmitResult.mData.size > 256) {
      throw CardIOException(
          "Transmit APDU error: unexpected response length: ${transmitResult.mData.size}")
    }
    return transmitResult.mData
        ?: throw CardIOException("Transmit APDU error: ${transmitResult.mResult}")
  }

  override fun isContactless(): Boolean {
    return true
  }

  override fun onUnregister() {
    // Clear NFC reader
    if (nfcReader.isEnabled) {
      nfcReader.disconnect()
      nfcReader.enable(false)
    }
    // Clear NFC broadcast receiver
    unregisterBroadcastReceiver()
  }

  override fun isProtocolSupported(readerProtocol: String): Boolean {
    return try {
      BluebirdSupportContactlessProtocols.valueOf(readerProtocol)
      true
    } catch (e: IllegalArgumentException) {
      false
    }
  }

  override fun activateProtocol(readerProtocol: String) {
    handleProtocol(readerProtocol, ProtocolOperation.Activate)
  }

  override fun deactivateProtocol(readerProtocol: String) {
    handleProtocol(readerProtocol, ProtocolOperation.Deactivate)
  }

  override fun isCurrentProtocol(readerProtocol: String): Boolean {
    val protocol: BluebirdSupportContactlessProtocols?
    try {
      protocol = BluebirdSupportContactlessProtocols.valueOf(readerProtocol)
    } catch (e: IllegalArgumentException) {
      return false
    }
    if ((protocol == currentTag?.currentProtocol) ||
        (protocol == BluebirdSupportContactlessProtocols.ISO_14443_4_A_SKY_ECP &&
            currentTag?.currentProtocol == BluebirdSupportContactlessProtocols.ISO_14443_4_A) ||
        (protocol == BluebirdSupportContactlessProtocols.ISO_14443_4_B_SKY_ECP &&
            currentTag?.currentProtocol == BluebirdSupportContactlessProtocols.ISO_14443_4_B)) {
      return true
    }
    return false
  }

  override fun setCallback(callback: CardInsertionWaiterAsynchronousApi) {
    waitForCardInsertionAutonomousApi = callback
  }

  override fun getCardRemovalMonitoringSleepDuration(): Int = 500

  private fun checkExpAvailability() {
    if (Build.VERSION.SDK_INT < MIN_SDK_API_LEVEL_ECP) {
      throw UnsupportedOperationException(
          "The terminal Android SDK API level must be higher than $MIN_SDK_API_LEVEL_ECP when using the ECP mode. Current API level is " +
              Build.VERSION.SDK_INT)
    }
  }

  private fun getNfcErrorMessage(status: Int): String {
    return when (status) {
      ResultCode.SUCCESS -> "SUCCESS"
      ResultCode.ERROR_HAL_NORMALFAILURE -> "ERROR_HAL_NORMALFAILURE"
      ResultCode.ERROR_HAL_OPEN_FAILED -> "ERROR_HAL_OPEN_FAILED"
      ResultCode.ERROR_HAL_ALREADY_OPEN -> "ERROR_HAL_ALREADY_OPEN"
      ResultCode.ERROR_HAL_NOT_OPEN -> "ERROR_HAL_NOT_OPEN"
      ResultCode.ERROR_HAL_INVALID_PARAMETER -> "ERROR_HAL_INVALID_PARAMETER"
      ResultCode.ERROR_HAL_TIMEOUT -> "ERROR_HAL_TIMEOUT"
      ResultCode.ERROR_HAL_NOT_READY -> "ERROR_HAL_NOT_READY"
      ResultCode.ERROR_HAL_NOT_SUPPORT_CARD -> "ERROR_HAL_NOT_SUPPORT_CARD"
      ResultCode.ERROR_HAL_TRANSMISSION -> "ERROR_HAL_TRANSMISSION"
      ResultCode.ERROR_HAL_PROTOCOL -> "ERROR_HAL_PROTOCOL"
      ResultCode.ERROR_HAL_COLLISION -> "ERROR_HAL_COLLISION"
      ResultCode.ERROR_HAL_NO_CARD -> "ERROR_HAL_NO_CARD"
      ResultCode.ERROR_NFC_SERIVCE_EXCEPTION -> "ERROR_NFC_SERIVCE_EXCEPTION"
      ResultCode.ERROR_ALREADY_ON -> "ERROR_ALREADY_ON"
      ResultCode.ERROR_ALREADY_OFF -> "ERROR_ALREADY_OFF"
      ResultCode.ERROR_NOT_ON_STATUS -> "ERROR_NOT_ON_STATUS"
      ResultCode.ERROR_ALREADY_ON_SCANNING -> "ERROR_ALREADY_ON_SCANNING"
      ResultCode.ERROR_NOT_SCANNING_STATUS -> "ERROR_NOT_SCANNING_STATUS"
      ResultCode.ERROR_CARD_TYPE_FLAG_ERROR -> "ERROR_CARD_TYPE_FLAG_ERROR"
      ResultCode.ERROR_TRANSMIT_DATA_ERROR -> "ERROR_TRANSMIT_DATA_ERROR"
      ResultCode.ERROR_ALREADY_CONNECTED_ERROR -> "ERROR_ALREADY_CONNECTED_ERROR"
      ResultCode.ERROR_ALREADY_DISCONNECTED_ERROR -> "ERROR_ALREADY_DISCONNECTED_ERROR"
      ResultCode.ERROR_NOT_CONNECTED_ERROR -> "ERROR_NOT_CONNECTED_ERROR"
      else -> "Unknown BB error code: $status"
    }
  }

  private fun registerBroadcastReceiverIfNeeded() {
    Timber.d(
        "Register BB NFC broadcast receiver (already registered? $isBroadcastReceiverRegistered)")
    if (isBroadcastReceiverRegistered) {
      return
    }
    val filter = IntentFilter()
    filter.addAction(ExtNfcReader.Broadcast.EXTNFC_DETECTED_ACTION)
    activity.registerReceiver(this, filter)
    isBroadcastReceiverRegistered = true
  }

  private fun unregisterBroadcastReceiver() {
    Timber.d(
        "Unregister BB NFC broadcast receiver (already unregistered? ${!isBroadcastReceiverRegistered})")
    if (!isBroadcastReceiverRegistered) {
      return
    }
    activity.unregisterReceiver(this)
    isBroadcastReceiverRegistered = false
  }

  private fun startScan() {
    nfcReader.enable(true)
    nfcReader.cardTypeForScan = pollingProtocols

    var status = nfcReader.BBextNfcCarrierOn()
    if (status != ResultCode.SUCCESS) {
      Timber.e("Error while setting the RF field on: {$status: ${getNfcErrorMessage(status)}}")
      return
    }

    status = nfcReader.startScan()
    if (status == ResultCode.ERROR_ALREADY_ON_SCANNING) {
      status = nfcReader.stopScan()
      if (status == ResultCode.SUCCESS) {
        status = nfcReader.startScan()
      }
    }
    if (status != ResultCode.SUCCESS) {
      Timber.e("Card scan error: {$status: ${getNfcErrorMessage(status)}}")
      return
    }

    registerBroadcastReceiverIfNeeded()
  }

  sealed class ProtocolOperation {
    abstract fun applyTo(currentValue: Int, protocolValue: Int): Int

    object Activate : ProtocolOperation() {
      override fun applyTo(currentValue: Int, protocolValue: Int) = currentValue or protocolValue
    }

    object Deactivate : ProtocolOperation() {
      override fun applyTo(currentValue: Int, protocolValue: Int) = currentValue xor protocolValue
    }
  }

  private fun handleProtocol(readerProtocol: String, operation: ProtocolOperation) {
    val protocol = BluebirdSupportContactlessProtocols.valueOf(readerProtocol)

    when (protocol) {
      BluebirdSupportContactlessProtocols.ISO_14443_4_A,
      BluebirdSupportContactlessProtocols.ISO_14443_4_B,
      BluebirdSupportContactlessProtocols.INNOVATRON_B_PRIME -> {
        pollingProtocols = operation.applyTo(pollingProtocols, protocol.value)
      }
      BluebirdSupportContactlessProtocols.ISO_14443_4_A_SKY_ECP,
      BluebirdSupportContactlessProtocols.ISO_14443_4_B_SKY_ECP -> {
        checkExpAvailability()
        when (operation) {
          is ProtocolOperation.Activate -> handleSkyEcpActivation(protocol)
          is ProtocolOperation.Deactivate -> handleSkyEcpDeactivation()
        }
      }
      else ->
          throw IllegalArgumentException(
              "${operation.javaClass.simpleName} protocol error: '$readerProtocol' not allowed")
    }
  }

  private fun handleSkyEcpActivation(protocol: BluebirdSupportContactlessProtocols) {
    val (baseProtocol, vasupMode, invalidVasupType, errorMessage) =
        when (protocol) {
          BluebirdSupportContactlessProtocols.ISO_14443_4_A_SKY_ECP ->
              SkyEcpConfig(
                  baseProtocol = BluebirdSupportContactlessProtocols.ISO_14443_4_A,
                  vasupMode = ExtNfcReader.ECP.Mode.VASUP_A,
                  invalidVasupType = ExtNfcReader.ECP.Mode.VASUP_B,
                  errorMessage = "SKY ECP VASUP type B is set")
          BluebirdSupportContactlessProtocols.ISO_14443_4_B_SKY_ECP ->
              SkyEcpConfig(
                  baseProtocol = BluebirdSupportContactlessProtocols.ISO_14443_4_B,
                  vasupMode = ExtNfcReader.ECP.Mode.VASUP_B,
                  invalidVasupType = ExtNfcReader.ECP.Mode.VASUP_A,
                  errorMessage = "SKY ECP VASUP type A is set")
          else -> throw IllegalArgumentException("Invalid SKY ECP protocol")
        }

    pollingProtocols = pollingProtocols or baseProtocol.value

    if (this.vasupMode == invalidVasupType) {
      throw IllegalStateException(errorMessage)
    }

    vasupPayload?.let {
      nfcEcp!!.setConfiguration(vasupMode, it)
      this.vasupMode = vasupMode
    } ?: throw IllegalStateException("SKY ECP VASUP payload was not set")
  }

  private fun handleSkyEcpDeactivation() {
    nfcEcp!!.clearConfiguration()
    vasupMode = null
  }

  override fun onReceive(context: Context, intent: Intent) {
    if (ExtNfcReader.Broadcast.EXTNFC_DETECTED_ACTION == intent.action) {
      val cardType = intent.getIntExtra(ExtNfcReader.Broadcast.EXTNFC_CARD_TYPE_KEY, -1)
      val protocol = BluebirdSupportContactlessProtocols.fromValue(cardType)
      protocol?.let {
        val tag =
            Tag(protocol, intent.getByteArrayExtra(ExtNfcReader.Broadcast.EXTNFC_CARD_DATA_KEY))
        onTagDiscovered(NfcResultSuccess(tag))
      }
    }
  }

  private data class SkyEcpConfig(
      val baseProtocol: BluebirdSupportContactlessProtocols,
      val vasupMode: Byte,
      val invalidVasupType: Byte,
      val errorMessage: String
  )
}
