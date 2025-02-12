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
 *
 * @since 2.0.0
 */
enum class BluebirdSupportContactlessProtocols constructor(val value: Int) {
  ISO_14443_4_A(0x01),
  ISO_14443_4_A_SKY_ECP(0x81),
  ISO_14443_4_B(0x02),
  ISO_14443_4_B_SKY_ECP(0x82),
  INNOVATRON_B_PRIME(0x04);

  companion object {
    fun fromValue(value: Int): BluebirdSupportContactlessProtocols? {
      for (protocol in values()) {
        if (protocol.value == value) {
          return protocol
        }
      }
      return null
    }
  }
}
