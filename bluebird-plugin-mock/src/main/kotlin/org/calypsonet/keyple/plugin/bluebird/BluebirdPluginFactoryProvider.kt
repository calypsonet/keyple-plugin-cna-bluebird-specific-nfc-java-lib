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
import org.eclipse.keyple.core.plugin.storagecard.ApduInterpreterFactory

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
   * @param apduInterpreterFactory (Optional) The `ApduInterpreterFactory` dedicated to the
   *   management of storage cards. The interface of this factory is provided by the
   *   `keyple-plugin-storage-card-java-api` API, its implementation should be provided.
   * @since 3.0.0 (single-param usage), 3.1.0 (with APDU interpreter)
   */
  fun provideFactory(
      activity: Activity,
      apduInterpreterFactory: ApduInterpreterFactory? = null
  ): BluebirdPluginFactory {
    throw UnsupportedOperationException("Mocked plugin!")
  }
}
