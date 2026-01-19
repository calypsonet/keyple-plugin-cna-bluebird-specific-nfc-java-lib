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
package org.calypsonet.keyple.plugin.bluebird.example

import org.calypsonet.keyple.plugin.bluebird.spi.KeyProvider
import org.eclipse.keyple.core.util.HexUtil

/**
 * A basic key provider for MIFARE Classic cards.
 *
 * For the sake's of simplicity, this provider returns a default FF key.
 *
 * A real-world application should implement a more secure key management logic (e.g. by retrieving
 * keys from a secure server).
 *
 * @since 3.2.0
 */
class MifareClassicKeyProvider : KeyProvider {
  /**
   * Gets the key associated with the provided key index.
   *
   * @param keyIndex The key index.
   * @return A 6-bytes array.
   * @since 3.2.0
   */
  override fun getKey(keyIndex: Int): ByteArray {
    return HexUtil.toByteArray("FFFFFFFFFFFF")
  }
}
