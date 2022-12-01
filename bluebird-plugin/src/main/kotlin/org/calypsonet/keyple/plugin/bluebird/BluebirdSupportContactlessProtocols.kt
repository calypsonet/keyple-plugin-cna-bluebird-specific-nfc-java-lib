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

/**
 * Definition of all supported contactless protocols.
 *
 * Note: since they are not APDU based, support for SRT512 and MIFARE protocols would require an
 * upgrade.
 * @since 2.0.0
 */
enum class BluebirdSupportContactlessProtocols constructor(val value: Int) {
  ISO_14443_4_A(0x01),
  ISO_14443_4_B(0x02),
  INNOVATRON_B_PRIME(0x04),
  SRT512(0x08),
  MIFARE_CLASSIC(0x10),
  MIFARE_ULTRALIGHT(0x20),
  ISO14443_4_SKY_ECP_A(0x81),
  ISO14443_4_SKY_ECP_B(0x82);

  companion object {
    fun fromValue(value: Int): BluebirdSupportContactlessProtocols {
      for (protocol in values()) {
        if (protocol.value == value) {
          return protocol
        }
      }
      throw IllegalArgumentException("BluebirdSupportContactlessProtocols '$value' is not defined")
    }
  }
}
