/* **************************************************************************************
 * Copyright (c) 2025 Calypso Networks Association https://calypsonet.org/
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.calypsonet.keyple.plugin.bluebird.spi.KeyProvider
import org.eclipse.keyple.core.plugin.CardIOException
import org.eclipse.keyple.core.plugin.CardInsertionWaiterAsynchronousApi
import org.eclipse.keyple.core.plugin.spi.reader.ConfigurableReaderSpi
import org.eclipse.keyple.core.plugin.spi.reader.observable.ObservableReaderSpi
import org.eclipse.keyple.core.plugin.spi.reader.observable.state.insertion.CardInsertionWaiterAsynchronousSpi
import org.eclipse.keyple.core.plugin.spi.reader.observable.state.removal.CardRemovalWaiterBlockingSpi
import org.eclipse.keyple.core.plugin.storagecard.ApduInterpreterFactory
import org.eclipse.keyple.core.plugin.storagecard.internal.CommandProcessorApi
import org.eclipse.keyple.core.plugin.storagecard.internal.KeyStorageType
import org.eclipse.keyple.core.plugin.storagecard.internal.spi.ApduInterpreterFactorySpi
import org.eclipse.keyple.core.plugin.storagecard.internal.spi.ApduInterpreterSpi
import org.eclipse.keyple.core.util.Assert
import org.eclipse.keyple.core.util.HexUtil
import org.json.JSONObject
import org.slf4j.LoggerFactory

internal class BluebirdCardReaderAdapter(
    private val activity: Activity,
    private val apduInterpreterFactory: ApduInterpreterFactory?,
    private val keyProvider: KeyProvider?,
) :
    BluebirdCardReader,
    ObservableReaderSpi,
    ConfigurableReaderSpi,
    CardInsertionWaiterAsynchronousSpi,
    CardRemovalWaiterBlockingSpi,
    CommandProcessorApi,
    BroadcastReceiver() {

  private companion object {
    private val logger = LoggerFactory.getLogger(BluebirdCardReaderAdapter::class.java)
    private const val MIFARE_KEY_A: Byte = 0x60
    private const val MIFARE_KEY_B: Byte = 0x61
    private const val CARD_REMOVAL_POLLING_INTERVAL_MS = 250L
    private const val STOP_WAIT_FOR_CARD_REMOVAL_TIMEOUT_MS = 3000L
  }

  @SuppressLint("WrongConstant")
  private val nfcReader: ExtNfcReader =
      activity.getSystemService(ExtNfcReader.READER_SERVICE_NAME) as ExtNfcReader
  private val nfcEcp: ExtNfcReader.ECP? = nfcReader.ecp

  // The native libextnfc library is not thread-safe: all calls to nfcReader must be serialized
  // through this lock (card removal monitoring thread vs. Keyple/main thread).
  private val nfcLock = Any()
  private var isBroadcastReceiverRegistered: Boolean = false
  @Volatile private var isWaitingForCardRemoval = false
  @Volatile private var cardRemovalLoopExitLatch: CountDownLatch? = null
  @Volatile private var cardRemovalThread: Thread? = null
  private var currentProtocol: BluebirdContactlessProtocols? = null
  private var currentPowerOnData: String? = null
  private var pollingProtocols: Int = 0
  private var vasupPayload: ByteArray? = null
  private var vasupMode: Byte? = null

  private lateinit var waitForCardInsertionAutonomousApi: CardInsertionWaiterAsynchronousApi
  private lateinit var uid: ByteArray

  private var loadedKey: ByteArray? = null

  private val apduInterpreter: ApduInterpreterSpi? =
      apduInterpreterFactory?.let {
        require(it is ApduInterpreterFactorySpi) {
          "The provided ApduInterpreterFactory is not an instance of ApduInterpreterFactorySpi"
        }
        it.createApduInterpreter()
      }

  init {
    apduInterpreter?.setCommandProcessor(this)
  }

  override fun setSkyEcpVasupPayload(vasupPayload: ByteArray) {
    Assert.getInstance()
        .notNull(vasupPayload, "vasupPayload")
        .isInRange(vasupPayload.size, 5, 20, "vasupPayload")
    this.vasupPayload = vasupPayload
  }

  override fun onStartDetection() {
    if (logger.isDebugEnabled) {
      logger.debug("Start card scan using polling protocols {} configuration", pollingProtocols)
    }
    startScan()
  }

  override fun onStopDetection() {
    if (logger.isDebugEnabled) {
      logger.debug("Stop card scan")
    }
    // Make sure no presence check is in flight before turning the RF field off.
    stopWaitForCardRemoval()
    synchronized(nfcLock) {
      try {
        var status = nfcReader.stopScan()
        if (status != ResultCode.SUCCESS) {
          logger.warn("Error while stopping the scan: {}: {}", status, getNfcErrorMessage(status))
        }
        status = nfcReader.BBextNfcCarrierOff()
        if (status != ResultCode.SUCCESS) {
          logger.warn(
              "Error while setting the RF field off: {}: {}",
              status,
              getNfcErrorMessage(status),
          )
        }
        nfcReader.disconnect()
        nfcReader.enable(false)
      } catch (e: Exception) {
        logger.warn("Error while stopping the NFC reader: {}", e.message)
      }
    }
    unregisterBroadcastReceiver()
  }

  override fun getName(): String = BluebirdConstants.CARD_READER_NAME

  override fun openPhysicalChannel() {
    synchronized(nfcLock) {
      val status = nfcReader.connect()
      if (status < 0) {
        throw CardIOException(
            "Open physical channel error: {$status: ${getNfcErrorMessage(status)}}"
        )
      }

      if (currentProtocol == BluebirdContactlessProtocols.ST25_SRT512) {
        // specific case for STM SRT512/ST25
        val response = nfcReader.BBextNfcSRT512GetUID()
        if (response[0] == 0.toByte()) {
          uid = response.copyOfRange(1, 9)
        }
      }
    }

    currentPowerOnData =
        JSONObject()
            .put("type", getTypeFromProtocol(currentProtocol!!))
            .put("uid", HexUtil.toHex(uid))
            .toString()
    if (logger.isDebugEnabled) {
      logger.debug("Power on data: {}", powerOnData)
    }
    loadedKey = null // Clear any previously loaded key
  }

  private fun getTypeFromProtocol(protocol: BluebirdContactlessProtocols): String {
    return when (protocol) {
      BluebirdContactlessProtocols.ISO_14443_4_A -> "ISO14443-4-A"
      BluebirdContactlessProtocols.ISO_14443_4_A_SKY_ECP -> "ISO14443-4-A"
      BluebirdContactlessProtocols.ISO_14443_4_B -> "ISO14443-4-B"
      BluebirdContactlessProtocols.ISO_14443_4_B_SKY_ECP -> "ISO14443-4-B"
      BluebirdContactlessProtocols.INNOVATRON_B_PRIME -> "INNOVATRON-B-PRIME"
      BluebirdContactlessProtocols.ST25_SRT512 -> "ISO14443-3-B"
      BluebirdContactlessProtocols.MIFARE_ULTRALIGHT -> "ISO14443-3-A"
      BluebirdContactlessProtocols.MIFARE_CLASSIC -> "ISO14443-3-A"
    }
  }

  override fun closePhysicalChannel() {
    // no-op
  }

  override fun isPhysicalChannelOpen(): Boolean {
    return synchronized(nfcLock) { nfcReader.isConnected }
  }

  override fun checkCardPresence(): Boolean {
    throw UnsupportedOperationException("checkCardPresence() is not supported")
  }

  override fun getPowerOnData(): String = currentPowerOnData ?: ""

  override fun transmitApdu(apduIn: ByteArray): ByteArray {
    try {
      return if (apduInterpreter == null) {
        transmitIsoApdu(apduIn)
      } else {
        apduInterpreter.processApdu(apduIn)
      }
    } catch (e: CardIOException) {
      throw e
    } catch (e: Exception) {
      throw CardIOException("Error while transmitting APDU: ${e.message}", e)
    }
  }

  override fun isContactless(): Boolean {
    return true
  }

  override fun onUnregister() {
    stopWaitForCardRemoval()
    synchronized(nfcLock) {
      try {
        if (nfcReader.isEnabled) {
          nfcReader.disconnect()
          nfcReader.enable(false)
        }
      } catch (e: Exception) {
        logger.warn("Error while releasing the NFC reader: {}", e.message)
      }
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
      BluebirdContactlessProtocols.ST25_SRT512.name ->
          pollingProtocols = pollingProtocols or BluebirdContactlessProtocols.ST25_SRT512.getValue()
      BluebirdContactlessProtocols.MIFARE_ULTRALIGHT.name ->
          pollingProtocols =
              pollingProtocols or BluebirdContactlessProtocols.MIFARE_ULTRALIGHT.getValue()
      BluebirdContactlessProtocols.MIFARE_CLASSIC.name ->
          pollingProtocols =
              pollingProtocols or BluebirdContactlessProtocols.MIFARE_CLASSIC.getValue()
      BluebirdContactlessProtocols.ISO_14443_4_A_SKY_ECP.name -> {
        check(vasupMode != ExtNfcReader.ECP.Mode.VASUP_B) { "SKY ECP VASUP type B is set" }
        check(vasupPayload != null) { "SKY ECP VASUP payload was not set" }
        vasupMode = ExtNfcReader.ECP.Mode.VASUP_A
        nfcEcp!!.setConfiguration(ExtNfcReader.ECP.Mode.VASUP_A, vasupPayload)
        pollingProtocols = pollingProtocols or BluebirdContactlessProtocols.ISO_14443_4_A.getValue()
      }
      BluebirdContactlessProtocols.ISO_14443_4_B_SKY_ECP.name -> {
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
      BluebirdContactlessProtocols.ST25_SRT512.name ->
          pollingProtocols =
              pollingProtocols and BluebirdContactlessProtocols.ST25_SRT512.getValue().inv()
      BluebirdContactlessProtocols.MIFARE_ULTRALIGHT.name ->
          pollingProtocols =
              pollingProtocols and BluebirdContactlessProtocols.MIFARE_ULTRALIGHT.getValue().inv()
      BluebirdContactlessProtocols.MIFARE_CLASSIC.name ->
          pollingProtocols =
              pollingProtocols and BluebirdContactlessProtocols.MIFARE_CLASSIC.getValue().inv()
      BluebirdContactlessProtocols.ISO_14443_4_A_SKY_ECP.name,
      BluebirdContactlessProtocols.ISO_14443_4_B_SKY_ECP.name -> {
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

  @SuppressLint("UnspecifiedRegisterReceiverFlag")
  private fun registerBroadcastReceiverIfNeeded() {
    if (logger.isDebugEnabled) {
      logger.debug(
          "Register BB NFC broadcast receiver (already registered? {})",
          isBroadcastReceiverRegistered,
      )
    }
    if (isBroadcastReceiverRegistered) {
      return
    }
    val filter = IntentFilter().apply { addAction(ExtNfcReader.Broadcast.EXTNFC_DETECTED_ACTION) }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      activity.registerReceiver(this, filter, Context.RECEIVER_EXPORTED)
    } else {
      @Suppress("DEPRECATION") activity.registerReceiver(this, filter)
    }
    isBroadcastReceiverRegistered = true
  }

  private fun unregisterBroadcastReceiver() {
    if (logger.isDebugEnabled) {
      logger.debug(
          "Unregister BB NFC broadcast receiver (already unregistered? {})",
          !isBroadcastReceiverRegistered,
      )
    }
    if (!isBroadcastReceiverRegistered) {
      return
    }
    try {
      activity.unregisterReceiver(this)
    } catch (e: IllegalArgumentException) {
      logger.warn("Broadcast receiver was not registered: {}", e.message)
    }
    isBroadcastReceiverRegistered = false
  }

  private fun startScan() {
    synchronized(nfcLock) {
      nfcReader.enable(true)
      nfcReader.cardTypeForScan = pollingProtocols

      var status = nfcReader.BBextNfcCarrierOn()
      if (status != ResultCode.SUCCESS) {
        logger.error(
            "Error while setting the RF field on: {}: {}",
            status,
            getNfcErrorMessage(status),
        )
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
        logger.error("Card scan error: {}: {}", status, getNfcErrorMessage(status))
        return
      }
    }

    registerBroadcastReceiverIfNeeded()
  }

  override fun onReceive(context: Context, intent: Intent) {
    if (ExtNfcReader.Broadcast.EXTNFC_DETECTED_ACTION == intent.action) {
      currentProtocol =
          BluebirdContactlessProtocols.fromValue(
              intent.getIntExtra(ExtNfcReader.Broadcast.EXTNFC_CARD_TYPE_KEY, -1)
          )
      if (logger.isDebugEnabled) {
        logger.debug("Discovered tag with protocol: {}", currentProtocol)
      }
      // the following UID may be overwritten later according to the card tech
      uid = intent.getByteArrayExtra(ExtNfcReader.Broadcast.EXTNFC_CARD_DATA_KEY) as ByteArray
      waitForCardInsertionAutonomousApi.onCardInserted()
    }
  }

  override fun waitForCardRemoval() {
    if (isWaitingForCardRemoval || !isPhysicalChannelOpen()) return
    val latch = CountDownLatch(1)
    cardRemovalLoopExitLatch = latch
    cardRemovalThread = Thread.currentThread()
    isWaitingForCardRemoval = true
    if (logger.isDebugEnabled) {
      logger.debug("Starting optimized card removal monitoring...")
    }

    try {
      while (isWaitingForCardRemoval) {
        val isPresent = synchronized(nfcLock) { isWaitingForCardRemoval && isCardPresent() }

        if (!isPresent) {
          if (logger.isDebugEnabled) {
            logger.debug("Card removal detected.")
          }
          break
        }

        runBlocking { delay(CARD_REMOVAL_POLLING_INTERVAL_MS) }
      }
    } finally {
      synchronized(nfcLock) {
        try {
          if (nfcReader.isConnected) {
            nfcReader.disconnect()
          }
        } catch (e: Exception) {
          logger.warn("Error while disconnecting the card: {}", e.message)
        }
      }
      isWaitingForCardRemoval = false
      cardRemovalThread = null
      latch.countDown()
    }
  }

  // Must be called with nfcLock held.
  private fun isCardPresent(): Boolean {
    return try {
      when (currentProtocol) {
        BluebirdContactlessProtocols.MIFARE_ULTRALIGHT,
        BluebirdContactlessProtocols.MIFARE_CLASSIC -> {
          val response = nfcReader.BBextNfcMifareRead(0.toByte())
          response != null && response.isNotEmpty() && response[0] == 0.toByte()
        }
        BluebirdContactlessProtocols.ST25_SRT512 -> {
          val response = nfcReader.BBextNfcSRT512GetUID()
          response != null && response.isNotEmpty() && response[0] == 0.toByte()
        }
        else -> {
          val res = nfcReader.transmit(byteArrayOf(0x00.toByte()))
          res != null && res.mResult == ResultCode.SUCCESS
        }
      }
    } catch (e: Exception) {
      if (logger.isDebugEnabled) {
        logger.debug("Presence check failed: {}", e.message)
      }
      false
    }
  }

  override fun stopWaitForCardRemoval() {
    isWaitingForCardRemoval = false
    val latch = cardRemovalLoopExitLatch ?: return
    if (Thread.currentThread() === cardRemovalThread) return
    // Block until the monitoring loop has really exited so that no native call is still in
    // flight when the caller goes on with stopScan()/BBextNfcCarrierOff().
    try {
      if (!latch.await(STOP_WAIT_FOR_CARD_REMOVAL_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
        logger.warn(
            "Card removal monitoring loop did not stop within {} ms",
            STOP_WAIT_FOR_CARD_REMOVAL_TIMEOUT_MS,
        )
      }
    } catch (_: InterruptedException) {
      Thread.currentThread().interrupt()
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

  override fun transmitIsoApdu(apdu: ByteArray): ByteArray {
    val transmitResult = synchronized(nfcLock) { nfcReader.transmit(apdu) }
    if (transmitResult.mData != null && transmitResult.mData.size > 256) {
      throw CardIOException(
          "Transmit APDU error: unexpected response length: ${transmitResult.mData.size}"
      )
    }
    return transmitResult.mData
        ?: throw CardIOException("Transmit APDU error: ${transmitResult.mResult}")
  }

  override fun getUID(): ByteArray? {
    return uid
  }

  override fun readBlock(blockNumber: Int, length: Int): ByteArray {
    when (currentProtocol) {
      BluebirdContactlessProtocols.MIFARE_ULTRALIGHT,
      BluebirdContactlessProtocols.MIFARE_CLASSIC -> {
        val response =
            synchronized(nfcLock) { nfcReader.BBextNfcMifareRead(blockNumber.toByte()) }
                ?: throw CardIOException("Read block error: BBextNfcMifareRead returned null")
        if (response.size == 17) {
          if (response[0] == 0.toByte()) {
            return response.copyOfRange(1, 17)
          } else {
            throw CardIOException(
                "Read block error: operation failed with result code ${response[0]}"
            )
          }
        } else {
          throw CardIOException("Read block error: invalid response format")
        }
      }
      BluebirdContactlessProtocols.ST25_SRT512 -> {
        val response =
            synchronized(nfcLock) { nfcReader.BBextNfcSRT512ReadBlock(blockNumber.toByte()) }
                ?: throw CardIOException("Read block error: BBextNfcSRT512ReadBlock returned null")
        if (response.size == 5) {
          if (response[0] == 0.toByte()) {
            return response.copyOfRange(1, 5)
          } else {
            throw CardIOException(
                "Read block error: operation failed with result code ${response[0]}"
            )
          }
        } else {
          throw CardIOException("Read block error: invalid response format")
        }
      }
      else -> {
        throw CardIOException("Read block error: protocol not supported: $currentProtocol")
      }
    }
  }

  override fun writeBlock(blockNumber: Int, data: ByteArray) {
    when (currentProtocol) {
      BluebirdContactlessProtocols.MIFARE_ULTRALIGHT,
      BluebirdContactlessProtocols.MIFARE_CLASSIC -> {
        val resultCode =
            synchronized(nfcLock) { nfcReader.BBextNfcMifareWrite(blockNumber.toByte(), data) }
        if (resultCode != 0) {
          throw CardIOException("Write block error: operation failed with result code $resultCode")
        }
      }
      BluebirdContactlessProtocols.ST25_SRT512 -> {
        val resultCode =
            synchronized(nfcLock) { nfcReader.BBextNfcSRT512WriteBlock(blockNumber.toByte(), data) }
        if (resultCode != 0) {
          throw CardIOException("Write block error: operation failed with result code $resultCode")
        }
      }
      else -> {
        throw CardIOException("Write block error: protocol not supported: $currentProtocol")
      }
    }
  }

  override fun loadKey(keyStorageType: KeyStorageType, keyNumber: Int, key: ByteArray) {
    // Only volatile (session-based) storage is supported
    // Keys are stored in memory and cleared when channel opens or after authentication
    loadedKey = key.copyOf()
  }

  override fun generalAuthenticate(blockAddress: Int, keyType: Int, keyNumber: Int): Boolean {
    // Only Mifare Classic requires authentication
    if (currentProtocol != BluebirdContactlessProtocols.MIFARE_CLASSIC) {
      throw IllegalStateException(
          "General Authenticate is only supported for Mifare Classic. Current protocol: $currentProtocol"
      )
    }

    // Retrieve the key: first check loaded key, then fall back to KeyProvider
    val key = loadedKey?.also { loadedKey = null }

    val usedKey =
        key
            ?: checkNotNull(keyProvider) {
                  "No key loaded and no key provider available for key number: $keyNumber"
                }
                .getKey(keyNumber)
            ?: throw IllegalStateException("No key found for key number: $keyNumber")

    // Validate key length (Mifare Classic uses 6-byte keys)
    require(usedKey.size == 6) {
      "Invalid key length: ${usedKey.size} bytes. Mifare Classic requires 6-byte keys."
    }

    // Convert keyType to Bluebird API format
    val bluebirdKeyType =
        when (keyType) {
          MIFARE_KEY_A.toInt() -> 0x00.toByte() // Bluebird uses 0x00 for KEY_A
          MIFARE_KEY_B.toInt() -> 0x01.toByte() // Bluebird uses 0x01 for KEY_B
          else ->
              throw IllegalArgumentException(
                  "Unsupported key type: 0x${keyType.toString(16)}. Only KEY_A (0x60) and KEY_B (0x61) are supported."
              )
        }

    // Perform authentication using Bluebird NFC API
    val resultCode =
        synchronized(nfcLock) {
          nfcReader.BBextNfciMifareAuthentication(bluebirdKeyType, blockAddress.toByte(), usedKey)
        }

    // Return true if authentication succeeded, false otherwise
    return resultCode == ResultCode.SUCCESS
  }
}
