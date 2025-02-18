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

/**
 * Provider of [BluebirdPluginFactory] instances.
 *
 * @since 2.0.0
 */
object BluebirdPluginFactoryProvider {

  /**
   * Provides an instance of [BluebirdPluginFactory].
   *
   * @param activity The activity.
   * @since 3.0.0
   */
  fun provideFactory(activity: Activity): BluebirdPluginFactory {
    return BluebirdPluginFactoryAdapter(activity)
  }
}
