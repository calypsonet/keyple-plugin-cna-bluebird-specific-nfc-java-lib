/* **************************************************************************************
 * Copyright (c) 2026 Calypso Networks Association https://calypsonet.org/
 *
 * See the NOTICE file(s) distributed with this work for additional information
 * regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ************************************************************************************** */
package org.calypsonet.keyple.plugin.bluebird.spi

/**
 * Interface allowing the application to provide authentication keys dynamically.
 *
 * <p>This interface enables external key management for Mifare Classic and similar storage card
 * technologies that require authentication before read/write operations.
 *
 * <p>When a key is needed for authentication but has not been loaded via {@link
 * CommandProcessorApi#loadKey}, the plugin will query the KeyProvider to obtain the key.
 *
 * @since 3.2.0
 */
interface KeyProvider {

  /**
   * Retrieves the key associated with the given key number.
   *
   * <p>The key number is provided in the authentication command and should correspond to a key
   * stored or managed by the application.
   *
   * @param keyNumber The number of the key requested.
   * @return The key as a byte array (6 bytes for Mifare Classic), or null if not found.
   * @since 3.2.0
   */
  fun getKey(keyNumber: Int): ByteArray?
}
