/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.settings.deviceinfo.simstatus

import android.content.Context
import android.os.UserManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.dx.mockito.inline.extended.ExtendedMockito
import com.android.settings.core.BasePreferenceController
import com.android.settings.network.SubscriptionUtil
import com.android.settingslib.Utils
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockitoSession
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.stub
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@RunWith(AndroidJUnit4::class)
class SimEidPreferenceControllerTest {
    private lateinit var mockSession: MockitoSession

    private val mockUserManager = mock<UserManager>()

    private val context: Context =
        spy(ApplicationProvider.getApplicationContext()) {
            on { getSystemService(UserManager::class.java) } doReturn mockUserManager
        }

    private val controller = SimEidPreferenceController(context, TEST_KEY)

    @Before
    fun setUp() {
        mockSession = ExtendedMockito.mockitoSession()
            .initMocks(this)
            .mockStatic(SubscriptionUtil::class.java)
            .mockStatic(Utils::class.java)
            .strictness(Strictness.LENIENT)
            .startMocking()

        // By default, available
        whenever(SubscriptionUtil.isSimHardwareVisible(context)).thenReturn(true)
        whenever(Utils.isWifiOnly(context)).thenReturn(false)
        mockUserManager.stub {
            on { isAdminUser } doReturn true
        }
    }

    @After
    fun tearDown() {
        mockSession.finishMocking()
    }

    @Test
    fun getAvailabilityStatus_simHardwareVisible_userAdmin_notWifiOnly_displayed() {
        // Use defaults from setup()
        val availabilityStatus = controller.availabilityStatus
        assertThat(availabilityStatus).isEqualTo(BasePreferenceController.AVAILABLE)
    }

    @Test
    fun getAvailabilityStatus_notSimHardwareVisible_userAdmin_notWifiOnly_notDisplayed() {
        whenever(SubscriptionUtil.isSimHardwareVisible(context)).thenReturn(false)

        val availabilityStatus = controller.availabilityStatus
        assertThat(availabilityStatus).isEqualTo(BasePreferenceController.UNSUPPORTED_ON_DEVICE)
    }

    @Test
    fun getAvailabilityStatus_simHardwareVisible_notUserAdmin_notWifiOnly_notDisplayed() {
        mockUserManager.stub {
            on { isAdminUser } doReturn false
        }

        val availabilityStatus = controller.availabilityStatus
        assertThat(availabilityStatus).isEqualTo(BasePreferenceController.DISABLED_FOR_USER)
    }

    @Test
    fun getAvailabilityStatus_simHardwareVisible_userAdmin_wifiOnly_notDisplayed() {
        whenever(Utils.isWifiOnly(context)).thenReturn(true)

        val availabilityStatus = controller.availabilityStatus
        assertThat(availabilityStatus).isEqualTo(BasePreferenceController.UNSUPPORTED_ON_DEVICE)
    }

    private companion object {
        const val TEST_KEY = "test_key"
    }
}
