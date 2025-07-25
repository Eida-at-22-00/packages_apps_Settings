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

package com.android.settings.deviceinfo

import android.content.Context
import android.os.UserManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.dx.mockito.inline.extended.ExtendedMockito
import com.android.settings.R
import com.android.settings.core.BasePreferenceController
import com.android.settings.network.SubscriptionUtil
import com.android.settingslib.Utils
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockitoSession
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@RunWith(AndroidJUnit4::class)
class PhoneNumberPreferenceControllerTest {
    private lateinit var mockSession: MockitoSession

    private val mockUserManager = mock<UserManager>()
    private val mockTelephonyManager = mock<TelephonyManager>()
    private val mockSubscriptionManager = mock<SubscriptionManager>()

    private val context: Context =
        spy(ApplicationProvider.getApplicationContext()) {
            on { getSystemService(SubscriptionManager::class.java) } doReturn mockSubscriptionManager
            on { getSystemService(TelephonyManager::class.java) } doReturn mockTelephonyManager
            on { getSystemService(UserManager::class.java) } doReturn mockUserManager
        }

    private val subscriptionInfo = mock<SubscriptionInfo>()
    private val preference = spy(Preference(context))
    private val secondPreference = mock<Preference>()
    private var category = PreferenceCategory(context)
    private var preferenceScreen = PreferenceManager(context).createPreferenceScreen(context)

    private var controller = spy(PhoneNumberPreferenceController(context, "phone_number"))

    @Before
    fun setup() {
        mockSession =
            ExtendedMockito.mockitoSession()
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

        preference.setKey(controller.preferenceKey)
        preference.isVisible = true
        preferenceScreen.addPreference(preference)
        category.key = "basic_info_category"
        preferenceScreen.addPreference(category)

        doReturn(secondPreference).whenever(controller).createNewPreference(context)
    }

    @After
    fun teardown() {
        mockSession.finishMocking()
    }

    @Test
    fun displayPreference_multiSim_shouldAddSecondPreference() {
        whenever(mockTelephonyManager.phoneCount).thenReturn(2)

        val sim1Preference = Preference(context)
        category.addItemFromInflater(sim1Preference)
        controller.displayPreference(preferenceScreen)

        assertThat(category.preferenceCount).isEqualTo(2)
    }

    @Test
    fun updateState_singleSim_shouldUpdateTitleAndPhoneNumber() {
        val phoneNumber = "1111111111"
        doReturn(subscriptionInfo).whenever(controller).getSubscriptionInfo(any())
        doReturn(phoneNumber).whenever(controller).getFormattedPhoneNumber(subscriptionInfo)
        whenever(mockTelephonyManager.phoneCount).thenReturn(1)
        controller.displayPreference(preferenceScreen)

        controller.updateState(preference)

        verify(preference).title = context.getString(R.string.status_number)
        verify(preference).summary = phoneNumber
    }

    @Test
    fun updateState_multiSim_shouldUpdateTitleAndPhoneNumberOfMultiplePreferences() {
        val phoneNumber = "1111111111"
        doReturn(subscriptionInfo).whenever(controller).getSubscriptionInfo(any())
        doReturn(phoneNumber).whenever(controller).getFormattedPhoneNumber(subscriptionInfo)
        whenever(mockTelephonyManager.phoneCount).thenReturn(2)
        controller.displayPreference(preferenceScreen)

        controller.updateState(preference)

        verify(preference).title =
            context.getString(R.string.status_number_sim_slot, 1 /* sim slot */)
        verify(preference).summary = phoneNumber
        verify(secondPreference).title =
            context.getString(R.string.status_number_sim_slot, 2 /* sim slot */)
        verify(secondPreference).summary = phoneNumber
    }

    @Test
    fun getSummary_cannotGetActiveSubscriptionInfo_shouldShowNotAvailable() {
        whenever(mockSubscriptionManager.getActiveSubscriptionInfoList()).thenReturn(null)
        controller.displayPreference(preferenceScreen)

        controller.updateState(preference)

        verify(preference).summary = context.getString(R.string.device_info_not_available)
    }

    @Test
    fun getSummary_getEmptySubscriptionInfo_shouldShowNotAvailable() {
        whenever(mockSubscriptionManager.getActiveSubscriptionInfoList()).thenReturn(emptyList())
        controller.displayPreference(preferenceScreen)

        controller.updateState(preference)

        verify(preference).summary = context.getString(R.string.device_info_not_available)
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
}
