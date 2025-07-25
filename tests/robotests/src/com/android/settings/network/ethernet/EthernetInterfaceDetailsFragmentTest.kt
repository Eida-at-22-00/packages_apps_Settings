/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.settings.network.ethernet

import android.app.settings.SettingsEnums
import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.settings.R
import com.android.settingslib.core.AbstractPreferenceController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EthernetInterfaceDetailsFragmentTest {
    private val ethernetInterfaceDetailsFragment = EthernetInterfaceDetailsFragment()

    private val context: Context =
        object : ContextWrapper(ApplicationProvider.getApplicationContext()) {}

    @Test
    fun getMetricsCategory_shouldReturnEthernetSettings() {
        assertEquals(
            ethernetInterfaceDetailsFragment.getMetricsCategory(),
            SettingsEnums.ETHERNET_SETTINGS,
        )
    }

    @Test
    fun getPreferenceScreenId_shouldReturnExpectedResource() {
        assertEquals(
            ethernetInterfaceDetailsFragment.getPreferenceScreenResId(),
            R.xml.ethernet_interface_details,
        )
    }

    @Test
    fun getLogTag_shouldReturnClassName() {
        assertEquals(
            ethernetInterfaceDetailsFragment.getLogTag(),
            "EthernetInterfaceDetailsFragment",
        )
    }

    @Test
    fun createPreferenceController_shouldReturnDetailController() {
        val preferenceController =
            ethernetInterfaceDetailsFragment.createPreferenceControllers(context)
        assertEquals(1, preferenceController.size)
        assertTrue(preferenceController[0] is AbstractPreferenceController)
    }
}
