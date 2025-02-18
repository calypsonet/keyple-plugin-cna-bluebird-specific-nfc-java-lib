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
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.calypsonet.keypl.BluebirdConstants
import org.eclipse.keyple.core.plugin.CardIOException
import org.eclipse.keyple.core.plugin.CardInsertionWaiterAsynchronousApi
import org.eclipse.keyple.core.plugin.spi.reader.ConfigurableReaderSpi
import org.eclipse.keyple.core.plugin.spi.reader.observable.ObservableReaderSpi
import org.eclipse.keyple.core.plugin.spi.reader.observable.state.insertion.CardInsertionWaiterAsynchronousSpi
import org.eclipse.keyple.core.plugin.spi.reader.observable.state.removal.CardRemovalWaiterBlockingSpi
import org.eclipse.keyple.core.util.Assert
import org.eclipse.keyple.core.util.HexUtil
import timber.log.Timber

internal class BluebirdCardReaderAdapter(private val activity: Activity) :
    BluebirdCardReader,
    ObservableReaderSpi,
    ConfigurableReaderSpi,
    CardInsertionWaiterAsynchronousSpi,
    CardRemovalWaiterBlockingSpi,
    BroadcastReceiver() {

  private companion object {
    private const val MIN_SDK_API_LEVEL_ECP = 28
    private const val PING_APDU = "00C0000000"
  }

  @SuppressLint("WrongConstant")
  private val nfcReader: ExtNfcReader =
      activity.getSystemService(ExtNfcReader.READER_SERVICE_NAME) as ExtNfcReader
  private val nfcEcp: ExtNfcReader.ECP? =
      if (Build.VERSION.SDK_INT >= MIN_SDK_API_LEVEL_ECP) nfcReader.ecp else null

  private var isBroadcastReceiverRegistered: Boolean = false
  private var isCardChannelOpen: Boolean = false
  private var isWaitingForCardRemoval = false
  private var currentProtocol: BluebirdContactlessProtocols? = null
  private var currentPowerOnData: String? = null
  private var pollingProtocols: Int = 0
  private var vasupPayload: ByteArray? = null
  private var vasupMode: Byte? = null

  private lateinit var waitForCardInsertionAutonomousApi: CardInsertionWaiterAsynchronousApi

  override fun setSkyEcpVasupPayload(vasupPayload: ByteArray) {
    checkEcpAvailability()
    Assert.getInstance()
        .notNull(vasupPayload, "vasupPayload")
        .isInRange(vasupPayload.size, 5, 20, "vasupPayload")
    this.vasupPayload = vasupPayload
  }

  override fun onStartDetection() {
    Timber.d("Start card scan using polling protocols $pollingProtocols configuration")
    startScan()
  }

  override fun onStopDetection() {
    Timber.d("Stop card scan")
    var status = nfcReader.stopScan()
    if (status != ResultCode.SUCCESS) {
      Timber.w("Error while stopping the scan: {$status: ${getNfcErrorMessage(status)}}")
    }
    status = nfcReader.BBextNfcCarrierOff()
    if (status != ResultCode.SUCCESS) {
      Timber.w("Error while setting the RF field off: {$status: ${getNfcErrorMessage(status)}}")
    }
    nfcReader.disconnect()
    nfcReader.enable(false)
    unregisterBroadcastReceiver()
  }

  override fun getName(): String = BluebirdConstants.CARD_READER_NAME

  override fun openPhysicalChannel() {
    val status = nfcReader.connect()
    if (status < 0) {
      throw CardIOException("Open physical channel error: {$status: ${getNfcErrorMessage(status)}}")
    }
    isCardChannelOpen = true
  }

  override fun closePhysicalChannel() {
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
    if (nfcReader.isEnabled) {
      nfcReader.disconnect()
      nfcReader.enable(false)
    }
    unregisterBroadcastReceiver()
  }

  override fun isProtocolSupported(readerProtocol: String): Boolean {
    return try {
      BluebirdContactlessProtocols.valueOf(readerProtocol)
      true
    } catch (_: IllegalArgumentException) {
      false
    }
  }

  override fun activateProtocol(readerProtocol: String) {
    when (readerProtocol) {
      BluebirdContactlessProtocols.ISO_14443_4_A.name ->
          pollingProtocols =
              pollingProtocols or BluebirdContactlessProtocols.ISO_14443_4_A.getValue()
      BluebirdContactlessProtocols.ISO_14443_4_B.name ->
          pollingProtocols =
              pollingProtocols or BluebirdContactlessProtocols.ISO_14443_4_B.getValue()
      BluebirdContactlessProtocols.INNOVATRON_B_PRIME.name ->
          pollingProtocols =
              pollingProtocols or BluebirdContactlessProtocols.INNOVATRON_B_PRIME.getValue()
      BluebirdContactlessProtocols.ISO_14443_4_A_SKY_ECP.name -> {
        checkEcpAvailability()
        check(vasupMode != ExtNfcReader.ECP.Mode.VASUP_B) { "SKY ECP VASUP type B is set" }
        check(vasupPayload != null) { "SKY ECP VASUP payload was not set" }
        vasupMode = ExtNfcReader.ECP.Mode.VASUP_A
        nfcEcp!!.setConfiguration(ExtNfcReader.ECP.Mode.VASUP_A, vasupPayload)
        pollingProtocols = pollingProtocols or BluebirdContactlessProtocols.ISO_14443_4_A.getValue()
      }
      BluebirdContactlessProtocols.ISO_14443_4_B_SKY_ECP.name -> {
        checkEcpAvailability()
        check(vasupMode != ExtNfcReader.ECP.Mode.VASUP_A) { "SKY ECP VASUP type A is set" }
        check(vasupPayload != null) { "SKY ECP VASUP payload was not set" }
        vasupMode = ExtNfcReader.ECP.Mode.VASUP_B
        nfcEcp!!.setConfiguration(ExtNfcReader.ECP.Mode.VASUP_B, vasupPayload)
        pollingProtocols = pollingProtocols or BluebirdContactlessProtocols.ISO_14443_4_B.getValue()
      }
      else ->
          throw IllegalArgumentException("Activate protocol error: '$readerProtocol' not allowed")
    }
  }

  override fun deactivateProtocol(readerProtocol: String) {
    when (readerProtocol) {
      BluebirdContactlessProtocols.ISO_14443_4_A.name ->
          pollingProtocols =
              pollingProtocols and BluebirdContactlessProtocols.ISO_14443_4_A.getValue().inv()
      BluebirdContactlessProtocols.ISO_14443_4_B.name ->
          pollingProtocols =
              pollingProtocols and BluebirdContactlessProtocols.ISO_14443_4_B.getValue().inv()
      BluebirdContactlessProtocols.INNOVATRON_B_PRIME.name ->
          pollingProtocols =
              pollingProtocols and BluebirdContactlessProtocols.INNOVATRON_B_PRIME.getValue().inv()
      BluebirdContactlessProtocols.ISO_14443_4_A_SKY_ECP.name,
      BluebirdContactlessProtocols.ISO_14443_4_B_SKY_ECP.name -> {
        checkEcpAvailability()
        nfcEcp!!.clearConfiguration()
        vasupMode = null
      }
      else ->
          throw IllegalArgumentException("Deactivate protocol error: '$readerProtocol' not allowed")
    }
  }

  override fun isCurrentProtocol(readerProtocol: String): Boolean {
    val protocol: BluebirdContactlessProtocols
    try {
      protocol = BluebirdContactlessProtocols.valueOf(readerProtocol)
    } catch (_: IllegalArgumentException) {
      return false
    }
    return (protocol == currentProtocol) ||
        (protocol == BluebirdContactlessProtocols.ISO_14443_4_A_SKY_ECP &&
            currentProtocol == BluebirdContactlessProtocols.ISO_14443_4_A) ||
        (protocol == BluebirdContactlessProtocols.ISO_14443_4_B_SKY_ECP &&
            currentProtocol == BluebirdContactlessProtocols.ISO_14443_4_B)
  }

  override fun setCallback(callback: CardInsertionWaiterAsynchronousApi) {
    waitForCardInsertionAutonomousApi = callback
  }

  private fun checkEcpAvailability() {
    if (Build.VERSION.SDK_INT < MIN_SDK_API_LEVEL_ECP) {
      throw UnsupportedOperationException(
          "The terminal Android SDK API level must be higher than $MIN_SDK_API_LEVEL_ECP when using the ECP mode. Current API level is " +
              Build.VERSION.SDK_INT)
    }
  }

  @SuppressLint("UnspecifiedRegisterReceiverFlag")
  private fun registerBroadcastReceiverIfNeeded() {
    Timber.d(
        "Register BB NFC broadcast receiver (already registered? $isBroadcastReceiverRegistered)")
    if (isBroadcastReceiverRegistered) {
      return
    }
    val filter = IntentFilter().apply { addAction(ExtNfcReader.Broadcast.EXTNFC_DETECTED_ACTION) }
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

  override fun onReceive(context: Context, intent: Intent) {
    if (ExtNfcReader.Broadcast.EXTNFC_DETECTED_ACTION == intent.action) {
      isCardChannelOpen = false
      currentProtocol =
          BluebirdContactlessProtocols.fromValue(
              intent.getIntExtra(ExtNfcReader.Broadcast.EXTNFC_CARD_TYPE_KEY, -1))
      Timber.d("Discovered tag with protocol: $currentProtocol")
      currentProtocol?.let {
        currentPowerOnData =
            HexUtil.toHex(intent.getByteArrayExtra(ExtNfcReader.Broadcast.EXTNFC_CARD_DATA_KEY))
        waitForCardInsertionAutonomousApi.onCardInserted()
      }
    }
  }

  override fun waitForCardRemoval() {
    if (!isWaitingForCardRemoval) {
      isWaitingForCardRemoval = true
      while (isWaitingForCardRemoval) {
        try {
          transmitApdu(HexUtil.toByteArray(PING_APDU))
          runBlocking { delay(100) }
        } catch (_: CardIOException) {
          nfcReader.disconnect()
          isWaitingForCardRemoval = false
        }
      }
    }
  }

  override fun stopWaitForCardRemoval() {
    isWaitingForCardRemoval = false
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
}
