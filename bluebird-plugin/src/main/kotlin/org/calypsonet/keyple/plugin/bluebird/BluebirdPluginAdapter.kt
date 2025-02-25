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

import android.app.Activity
import org.eclipse.keyple.core.plugin.spi.PluginSpi
import org.eclipse.keyple.core.plugin.spi.reader.ReaderSpi
import org.eclipse.keyple.core.plugin.storagecard.ApduInterpreterFactory

/**
 * Implementation of the Bluebird Plugin to handle Bluebird contact and contactless readers.
 *
 * @since 2.0.0
 */
internal class BluebirdPluginAdapter(
    private val activity: Activity,
    private val apduInterpreterFactory: ApduInterpreterFactory?
) : BluebirdPlugin, PluginSpi {

  override fun getName(): String = BluebirdConstants.PLUGIN_NAME

  override fun searchAvailableReaders(): Set<ReaderSpi> =
      setOf(BluebirdSamReaderAdapter(), BluebirdCardReaderAdapter(activity, apduInterpreterFactory))

  override fun onUnregister() {
    // Do nothing -> all unregisterBroadcastReceiver operations are handled by readers
  }
}
