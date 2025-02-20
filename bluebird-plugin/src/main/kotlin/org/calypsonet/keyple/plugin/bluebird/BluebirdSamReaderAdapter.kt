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
import kotlinx.coroutines.*
import org.calypsonet.keypl.BluebirdConstants
import org.eclipse.keyple.core.plugin.CardIOException
import org.eclipse.keyple.core.plugin.spi.reader.ReaderSpi
import org.eclipse.keyple.core.util.HexUtil

internal class BluebirdSamReaderAdapter : BluebirdSamReader, ReaderSpi {

  private val samInterface = SamInterface(SamMessageHandler)
  private var atr: ByteArray? = null

  override fun transmitApdu(apduIn: ByteArray): ByteArray {
    checkStatus(samInterface.device_SendCommand(apduIn))
    return runBlocking { SamMessageHandler.getSamMessage() }
  }

  override fun getPowerOnData(): String {
    return HexUtil.toHex(atr)
  }

  override fun closePhysicalChannel() {
    samInterface.device_Close()
  }

  override fun openPhysicalChannel() {
    checkStatus(samInterface.device_Open())
    runBlocking { atr = SamMessageHandler.getSamMessage() }
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
  }

  private object SamMessageHandler :
      Handler(HandlerThread("SamMessageHandlerThread").apply { start() }.looper) {

    private var deferredSamResponse = CompletableDeferred<ByteArray>()

    override fun handleMessage(msg: Message) {
      if (msg.what == SamInterface.SAM_DATA_RECEIVED_MSG_INT) {
        deferredSamResponse.complete(msg.data.getByteArray("receive") ?: byteArrayOf())
      } else {
        deferredSamResponse.completeExceptionally(
            CardIOException("Unexpected SAM message code received: {${msg.what}"))
      }
    }

    suspend fun getSamMessage(): ByteArray {
      try {
        return deferredSamResponse.await()
      } finally {
        deferredSamResponse = CompletableDeferred()
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
