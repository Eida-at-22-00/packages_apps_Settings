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

package com.android.settings.wifi.repository

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.settingslib.spa.testutils.lastWithTimeoutOrNull
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WifiRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun wifiStateFlow_enabled() = runBlocking {
        val wifiStateChangedIntent =
            Intent().apply {
                putExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_ENABLED)
            }
        val repository = WifiRepository(context, flowOf(wifiStateChangedIntent))

        val wifiState = repository.wifiStateFlow().lastWithTimeoutOrNull()

        assertThat(wifiState).isEqualTo(WifiManager.WIFI_STATE_ENABLED)
    }

    @Test
    fun wifiStateFlow_unknown() = runBlocking {
        val repository = WifiRepository(context, emptyFlow())

        val wifiState = repository.wifiStateFlow().lastWithTimeoutOrNull()

        assertThat(wifiState).isEqualTo(WifiManager.WIFI_STATE_UNKNOWN)
    }
}
