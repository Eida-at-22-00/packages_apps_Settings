/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.biometrics.fingerprint2.domain.interactor

import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityEvent.TYPE_ANNOUNCEMENT
import android.view.accessibility.AccessibilityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn

/** Represents all of the information on accessibility state. */
interface AccessibilityInteractor {
  /** A flow that contains whether or not accessibility is enabled */
  fun isEnabledFlow(scope: CoroutineScope): Flow<Boolean>

  val isEnabled: Boolean

  fun announce(clazz: Class<*>, announcement: CharSequence?)

  fun interrupt()
}

class AccessibilityInteractorImpl(private val accessibilityManager: AccessibilityManager) :
  AccessibilityInteractor {
  /** A flow that contains whether or not accessibility is enabled */
  override fun isEnabledFlow(scope: CoroutineScope): Flow<Boolean> =
    callbackFlow {
        val listener =
          AccessibilityManager.AccessibilityStateChangeListener { enabled -> trySend(enabled) }
        accessibilityManager.addAccessibilityStateChangeListener(listener)

        // This clause will be called when no one is listening to the flow
        awaitClose { accessibilityManager.removeAccessibilityStateChangeListener(listener) }
      }
      .stateIn(
        scope,
        SharingStarted.WhileSubscribed(), // When no longer subscribed, we removeTheListener
        accessibilityManager.isEnabled,
      )

  override val isEnabled: Boolean
    get() = accessibilityManager.isEnabled

  override fun announce(clazz: Class<*>, announcement: CharSequence?) {
    val event = AccessibilityEvent(TYPE_ANNOUNCEMENT)
    event.className = clazz.javaClass.name
    event.packageName = clazz.packageName
    event.text.add(announcement)
    accessibilityManager.sendAccessibilityEvent(event)
  }

  /** Interrupts the current accessibility manager from announcing a phrase. */
  override fun interrupt() {
    try {
      accessibilityManager.interrupt()
    } catch (e: IllegalStateException) {
      Log.e(TAG, "Error trying to interrupt when accessibility isn't enabled $e")
    }
  }

  companion object {
    const val TAG = "AccessibilityInteractor"
  }
}
