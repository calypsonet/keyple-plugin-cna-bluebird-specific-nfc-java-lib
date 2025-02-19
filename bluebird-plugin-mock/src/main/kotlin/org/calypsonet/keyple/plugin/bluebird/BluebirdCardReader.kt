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

import org.eclipse.keyple.core.common.KeypleReaderExtension

/**
 * Extends the `KeypleReaderExtension` interface dedicated to the Bluebird card reader.
 *
 * @since 3.0.0
 */
interface BluebirdCardReader : KeypleReaderExtension {

  /**
   * Sets the payload to be sent with the VASUP polling mode. The expected content of the VASUP
   * payload is the one defined by the SKY ECP specification.
   *
   * @param vasupPayload A 5 to 20 bytes byte array.
   * @throws IllegalArgumentException If the parameter is null or out of range.
   * @throws UnsupportedOperationException When invoked in a context with Android SDK API level
   *   < 28.
   * @since 2.1.0
   */
  fun setSkyEcpVasupPayload(vasupPayload: ByteArray)
}
