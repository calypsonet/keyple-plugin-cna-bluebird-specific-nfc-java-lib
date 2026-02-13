/* **************************************************************************************
 * Copyright (c) 2026 Calypso Networks Association https://calypsonet.org/
 *
 * See the NOTICE file(s) distributed with this work for additional information
 * regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ************************************************************************************** */
package org.calypsonet.keyple.example.plugin.bluebird

import android.util.Log
import timber.log.Timber

/**
 * A [Timber.Tree] implementation for release builds.
 *
 * This tree is designed to filter out less important log messages, only allowing logs with a
 * priority of `Log.INFO` or higher (i.e., `INFO`, `WARN`, `ERROR`, `ASSERT`) to be recorded. Debug
 * and verbose logs are ignored, which is a common practice for production/release applications to
 * reduce noise and improve performance.
 */
class ReleaseTree : Timber.DebugTree() {

  override fun isLoggable(tag: String?, priority: Int): Boolean {
    return priority >= Log.INFO
  }
}
