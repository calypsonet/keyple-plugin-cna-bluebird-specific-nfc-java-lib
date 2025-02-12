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

import android.os.Handler
import android.os.Looper
import android.os.Message
import com.bluebird.payment.sam.SamInterface
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.calypsonet.keyple.plugin.bluebird.utils.suspendCoroutineWithTimeout
import org.eclipse.keyple.core.plugin.CardIOException
import org.eclipse.keyple.core.plugin.spi.reader.ReaderSpi
import org.eclipse.keyple.core.util.Assert
import org.eclipse.keyple.core.util.HexUtil
import timber.log.Timber

/**
 * Implementation of the Bluebird contact reader interface (usually dedicated to the SAM)
 *
 * @since 2.0.0
 */
@Suppress("INVISIBLE_ABSTRACT_MEMBER_FROM_SUPER_WARNING")
@ExperimentalCoroutinesApi
internal class BluebirdContactReaderAdapter(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : BluebirdContactReader, ReaderSpi {

  companion object {
    private const val APDU_TIMEOUT: Long = 1000
    private const val ATR_TIMEOUT: Long = 2000
  }

  private val samMessageHandler: SamMessageHandler = SamMessageHandler()
  private val samInterface: SamInterface = SamInterface(samMessageHandler)

  private var atr: ByteArray? = null

  /**
   * @see ReaderSpi.transmitApdu
   * @since 2.0.0
   */
  override fun transmitApdu(apduIn: ByteArray): ByteArray {
    var apduOut: ByteArray? = byteArrayOf()
    runBlocking { apduOut = executeApduAsync(apduIn) }
    if (apduOut == null) {
      throw CardIOException("Transmit APDU error: APDU OUT is null")
    }
    return apduOut as ByteArray
  }

  private suspend fun executeApduAsync(apduIn: ByteArray?): ByteArray? {
    Assert.getInstance().notEmpty(apduIn, "APDU IN")
    samMessageHandler.channel = Channel(Channel.UNLIMITED)
    return suspendCoroutineWithTimeout(APDU_TIMEOUT) { continuation ->
      val handler = CoroutineExceptionHandler { _, exception ->
        handleSamResponse(null, exception, continuation)
      }
      GlobalScope.launch(handler) {
        withContext(dispatcher) {
          // Send command to SAM
          val status = samInterface.device_SendCommand(apduIn)
          if (status < 0) {
            val errorMsg =
                when (status) {
                  SamInterface.SAM_COMMAND_NOT_SUPPORT -> "SAM_COMMAND_NOT_SUPPORT"
                  SamInterface.SAM_COMMAND_CONFLICT -> "SAM_COMMAND_CONFLICT"
                  SamInterface.SAM_COMMAND_BAD_CLASS -> "SAM_COMMAND_BAD_CLASS"
                  SamInterface.SAM_COMMAND_BAD_PROTOCOL -> "SAM_COMMAND_BAD_PROTOCOL"
                  SamInterface.SAM_COMMAND_BAD_TCK -> "SAM_COMMAND_BAD_TCK"
                  SamInterface.SAM_COMMAND_BAD_TS -> "SAM_COMMAND_BAD_TS"
                  SamInterface.SAM_COMMAND_HARDWARE_ERROR -> "SAM_COMMAND_HARDWARE_ERROR"
                  SamInterface.SAM_COMMAND_OVERRUN -> "SAM_COMMAND_OVERRUN"
                  SamInterface.SAM_COMMAND_PARITY_ERROR -> "SAM_COMMAND_PARITY_ERROR"
                  else -> "unknown BB error code: $status"
                }
            throw CardIOException(errorMsg)
          }
          handleSamResponse(getSamResponseFromChannel(), null, continuation)
        }
      }
    }
  }

  private suspend fun getSamResponseFromChannel(): ByteArray? {
    var samResponse: ByteArray? = null
    val channelIterator = samMessageHandler.channel?.iterator()
    channelIterator?.let {
      if (it.hasNext()) {
        samResponse = it.next()
      }
    }
    return samResponse
  }

  private fun handleSamResponse(
      samResponse: ByteArray?,
      throwable: Throwable?,
      continuation: CancellableContinuation<ByteArray>
  ) {
    if (continuation.isActive) {
      samMessageHandler.channel?.close()
      samMessageHandler.channel = null
      samResponse?.let { continuation.resume(it) }
      throwable?.let { continuation.resumeWithException(it) }
    }
  }

  /**
   * @see ReaderSpi.getPowerOnData
   * @since 2.0.0
   */
  override fun getPowerOnData(): String {
    return HexUtil.toHex(atr)
  }

  /**
   * @see ReaderSpi.closePhysicalChannel
   * @since 2.0.0
   */
  override fun closePhysicalChannel() {
    val status = samInterface.device_Close()
    if (status < 0) {
      Timber.v("Close SAM physical channel error: $status")
    }
  }

  /**
   * @see ReaderSpi.openPhysicalChannel
   * @since 2.0.0
   */
  override fun openPhysicalChannel() {
    runBlocking { atr = openPhysicalChannelAsync() }
  }

  private suspend fun openPhysicalChannelAsync(): ByteArray? {
    samMessageHandler.channel = Channel(Channel.UNLIMITED)
    return suspendCoroutineWithTimeout(ATR_TIMEOUT) { continuation ->
      val handler = CoroutineExceptionHandler { _, exception ->
        handleSamResponse(null, exception, continuation)
      }
      GlobalScope.launch(handler) {
        withContext(dispatcher) {
          val status = samInterface.device_Open()
          if (status < 0) {
            throw CardIOException("Open SAM physical channel error: $status")
          }
          handleSamResponse(getSamResponseFromChannel(), null, continuation)
        }
      }
    }
  }

  /**
   * @see ReaderSpi.isPhysicalChannelOpen
   * @since 2.0.0
   */
  override fun isPhysicalChannelOpen(): Boolean {
    return samInterface.device_GetStatus() == 0
  }

  /**
   * @see ReaderSpi.checkCardPresence
   * @since 2.0.0
   */
  override fun checkCardPresence(): Boolean {
    // since the BB API needs a channel opening to detect the card, we assume the card is present
    return true
  }

  /**
   * @see ReaderSpi.isContactless
   * @since 2.0.0
   */
  override fun isContactless(): Boolean {
    return false
  }

  /**
   * @see ReaderSpi.getName
   * @since 2.0.0
   */
  override fun getName(): String = BluebirdContactReader.READER_NAME

  /**
   * @see ReaderSpi.onUnregister
   * @since 2.0.0
   */
  override fun onUnregister() {
    Timber.d("Unregister BB SAM reader")
    samInterface.let {
      val status = it.device_Close()
      if (status < 0) {
        Timber.v("Unregister BB SAM reader error: $status")
      }
    }
    samMessageHandler.channel?.close()
    samMessageHandler.channel = null
  }

  /**
   * SAM message handler.
   *
   * @since 2.0.0
   */
  private class SamMessageHandler : Handler(Looper.getMainLooper()) {

    var channel: Channel<ByteArray?>? = null

    override fun handleMessage(msg: Message) {
      if (msg.what == SamInterface.SAM_DATA_RECEIVED_MSG_INT) {
        val samData = msg.data.getByteArray("receive")
        channel?.trySend(samData)
      }
    }
  }
}
