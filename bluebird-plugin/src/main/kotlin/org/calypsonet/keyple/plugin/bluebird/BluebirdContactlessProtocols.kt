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

/**
 * Definition of all supported contactless protocols.
 *
 * Note: since they are not APDU based, support for SRT512 and MIFARE protocols would require an
 * upgrade.
 *
 * @since 3.0.0
 */
enum class BluebirdContactlessProtocols(private val techValue: Int) {

  /**
   * ISO 14443-4 A.
   *
   * @since 3.0.0
   */
  ISO_14443_4_A(0x01),

  /**
   * ISO 14443-4 A with ECP.
   *
   * @since 3.0.0
   */
  ISO_14443_4_A_SKY_ECP(0x81),

  /**
   * ISO 14443-4 B.
   *
   * @since 3.0.0
   */
  ISO_14443_4_B(0x02),

  /**
   * ISO 14443-4 B with ECP.
   *
   * @since 3.0.0
   */
  ISO_14443_4_B_SKY_ECP(0x82),

  /**
   * INNOVATRON B Prime.
   *
   * @since 3.0.0
   */
  INNOVATRON_B_PRIME(0x04),

  /**
   * STM ST25/SRT512.
   *
   * @since 3.1.0
   */
  ST25_SRT512(0x08),

  /**
   * NXP Mifare Ultralight.
   *
   * @since 3.1.0
   */
  MIFARE_ULTRALIGHT(0x20),

  /**
   * NXP Mifare Classic 1K.
   *
   * @since 3.2.0
   */
  MIFARE_CLASSIC_1K(0x10);

  internal fun getValue(): Int = techValue

  internal companion object {
    fun fromValue(value: Int): BluebirdContactlessProtocols? {
      for (protocol in values()) {
        if (protocol.techValue == value) {
          return protocol
        }
      }
      return null
    }
  }
}
