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

import android.app.Activity
import java.util.concurrent.ConcurrentHashMap
import org.calypsonet.keypl.BluebirdConstants
import org.eclipse.keyple.core.plugin.spi.PluginSpi
import org.eclipse.keyple.core.plugin.spi.reader.ReaderSpi

/**
 * Implementation of the Bluebird Plugin to handle Bluebird contact and contactless readers.
 *
 * @since 2.0.0
 */
internal class BluebirdPluginAdapter(private val activity: Activity) : BluebirdPlugin, PluginSpi {

  override fun getName(): String = BluebirdConstants.PLUGIN_NAME

  override fun searchAvailableReaders(): MutableSet<ReaderSpi> {
    val readers = ConcurrentHashMap<String, ReaderSpi>(2)

    val contactReader = BluebirdSamReaderAdapter()
    readers[contactReader.name] = contactReader

    val contactlessReader = BluebirdCardReaderAdapter(activity)
    readers[contactlessReader.name] = contactlessReader

    return readers.map { it.value }.toMutableSet()
  }

  override fun onUnregister() {
    // Do nothing -> all unregisterBroadcastReceiver operations are handled by readers
  }
}
