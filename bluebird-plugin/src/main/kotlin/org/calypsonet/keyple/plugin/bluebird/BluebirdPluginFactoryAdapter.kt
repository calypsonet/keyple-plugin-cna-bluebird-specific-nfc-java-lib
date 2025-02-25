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
import org.eclipse.keyple.core.common.CommonApiProperties
import org.eclipse.keyple.core.plugin.PluginApiProperties
import org.eclipse.keyple.core.plugin.spi.PluginFactorySpi
import org.eclipse.keyple.core.plugin.spi.PluginSpi
import org.eclipse.keyple.core.plugin.storagecard.ApduInterpreterFactory

/**
 * Implementation of the Bluebird Plugin factory.
 *
 * @since 2.0.0
 */
internal class BluebirdPluginFactoryAdapter
internal constructor(
    private val activity: Activity,
    private val apduInterpreterFactory: ApduInterpreterFactory?
) : BluebirdPluginFactory, PluginFactorySpi {

  override fun getPluginName(): String = BluebirdConstants.PLUGIN_NAME

  override fun getPlugin(): PluginSpi = BluebirdPluginAdapter(activity, apduInterpreterFactory)

  override fun getCommonApiVersion(): String = CommonApiProperties.VERSION

  override fun getPluginApiVersion(): String = PluginApiProperties.VERSION
}
