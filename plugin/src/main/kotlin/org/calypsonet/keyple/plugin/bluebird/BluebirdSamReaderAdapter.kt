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

import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import com.bluebird.payment.sam.SamInterface
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.eclipse.keyple.core.plugin.CardIOException
import org.eclipse.keyple.core.plugin.spi.reader.ReaderSpi
import org.eclipse.keyple.core.util.HexUtil
import org.slf4j.LoggerFactory

internal class BluebirdSamReaderAdapter : BluebirdSamReader, ReaderSpi {

  private companion object {
    private val logger = LoggerFactory.getLogger(BluebirdSamReaderAdapter::class.java)
    private const val SAM_RESPONSE_TIMEOUT_MS = 10_000L
  }

  private val samMessageHandler = SamMessageHandler()
  private val samInterface = SamInterface(samMessageHandler)
  private var atr: ByteArray? = null

  override fun transmitApdu(apduIn: ByteArray): ByteArray {
    return exchange { samInterface.device_SendCommand(apduIn) }
  }

  override fun getPowerOnData(): String {
    return HexUtil.toHex(atr)
  }

  override fun closePhysicalChannel() {
    samInterface.device_Close()
  }

  override fun openPhysicalChannel() {
    atr = exchange { samInterface.device_Open() }
  }

  override fun isPhysicalChannelOpen(): Boolean {
    return samInterface.device_GetStatus() == 0
  }

  override fun checkCardPresence(): Boolean {
    // since the BB API needs a channel opening to detect the card, we assume the card is present
    return true
  }

  override fun isContactless(): Boolean {
    return false
  }

  override fun getName(): String = BluebirdConstants.SAM_READER_NAME

  override fun onUnregister() {
    samInterface.device_Close()
    samMessageHandler.quit()
  }

  // Arms the response handler before issuing the command so that no response can be missed.
  private fun exchange(command: () -> Int): ByteArray {
    val response = samMessageHandler.expectResponse()
    try {
      checkStatus(command())
    } catch (e: Exception) {
      samMessageHandler.cancelResponse(response)
      throw e
    }
    return runBlocking {
      try {
        withTimeout(SAM_RESPONSE_TIMEOUT_MS) { response.await() }
      } catch (_: TimeoutCancellationException) {
        samMessageHandler.cancelResponse(response)
        throw CardIOException("No response from the SAM within $SAM_RESPONSE_TIMEOUT_MS ms")
      }
    }
  }

  private class SamMessageHandler :
      Handler(HandlerThread("SamMessageHandlerThread").apply { start() }.looper) {

    @Volatile private var pendingResponse: CompletableDeferred<ByteArray>? = null

    fun expectResponse(): CompletableDeferred<ByteArray> {
      return CompletableDeferred<ByteArray>().also { pendingResponse = it }
    }

    fun cancelResponse(response: CompletableDeferred<ByteArray>) {
      if (pendingResponse === response) {
        pendingResponse = null
      }
    }

    fun quit() {
      looper.quitSafely()
    }

    override fun handleMessage(msg: Message) {
      val response = pendingResponse
      if (response == null) {
        logger.warn("Unexpected SAM message received while no command is pending: {}", msg.what)
        return
      }
      pendingResponse = null
      if (msg.what == SamInterface.SAM_DATA_RECEIVED_MSG_INT) {
        response.complete(msg.data.getByteArray("receive") ?: byteArrayOf())
      } else {
        response.completeExceptionally(
            CardIOException("Unexpected SAM message code received: {${msg.what}")
        )
      }
    }
  }

  private fun checkStatus(status: Int) {
    if (status < 0) {
      val errorMsg =
          when (status) {
            samInterface.SAM_RETURN_FAIL -> "SAM_RETURN_FAIL"
            SamInterface.SAM_COMMAND_ABORT -> "SAM_COMMAND_ABORT"
            SamInterface.SAM_COMMAND_NOT_REPONSE -> "SAM_COMMAND_NOT_REPONSE"
            SamInterface.SAM_COMMAND_PARITY_ERROR -> "SAM_COMMAND_PARITY_ERROR"
            SamInterface.SAM_COMMAND_OVERRUN -> "SAM_COMMAND_OVERRUN"
            SamInterface.SAM_COMMAND_HARDWARE_ERROR -> "SAM_COMMAND_HARDWARE_ERROR"
            SamInterface.SAM_COMMAND_BAD_TS -> "SAM_COMMAND_BAD_TS"
            SamInterface.SAM_COMMAND_BAD_TCK -> "SAM_COMMAND_BAD_TCK"
            SamInterface.SAM_COMMAND_BAD_PROTOCOL -> "SAM_COMMAND_BAD_PROTOCOL"
            SamInterface.SAM_COMMAND_BAD_CLASS -> "SAM_COMMAND_BAD_CLASS"
            SamInterface.SAM_COMMAND_CONFLICT -> "SAM_COMMAND_CONFLICT"
            SamInterface.SAM_COMMAND_NOT_SUPPORT -> "SAM_COMMAND_NOT_SUPPORT"
            SamInterface.SAM_COMMAND_TIMEOUT -> "SAM_COMMAND_TIMEOUT"
            else -> "unknown BB error code: $status"
          }
      throw CardIOException("BB SAM interface error: $errorMsg")
    }
  }
}
