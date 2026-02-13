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
 * Constants for the Bluebird plugin.
 *
 * @since 3.0.0
 */
object BluebirdConstants {

  /**
   * The plugin name as registered to the Keyple smart card service.
   *
   * @since 3.0.0
   */
  const val PLUGIN_NAME = "BluebirdPluginMock"

  /**
   * The card reader name as provided by the plugin.
   *
   * @since 3.0.0
   */
  const val CARD_READER_NAME = "BluebirdCardReader"

  /**
   * The SAM reader name as provided by the plugin.
   *
   * @since 3.0.0
   */
  const val SAM_READER_NAME = "BluebirdSamReader"

  /**
   * The name of the permission required to access the Bluebird SAM.
   *
   * @since 3.0.0
   */
  const val BLUEBIRD_SAM_ANDROID_PERMISSION = "com.bluebird.permission.SAM_DEVICE_ACCESS"
}
