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

package com.android.settings.bluetooth.ui.view

import android.app.settings.SettingsEnums
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.fragment.app.FragmentActivity
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import androidx.test.core.app.ApplicationProvider
import com.android.settings.bluetooth.ui.model.DeviceSettingPreferenceModel
import com.android.settings.bluetooth.ui.model.FragmentTypeModel
import com.android.settings.dashboard.DashboardFragment
import com.android.settings.testutils.FakeFeatureFactory
import com.android.settingslib.bluetooth.CachedBluetoothDevice
import com.android.settingslib.bluetooth.devicesettings.DeviceSettingId
import com.android.settingslib.bluetooth.devicesettings.data.repository.DeviceSettingRepository
import com.android.settingslib.bluetooth.devicesettings.shared.model.DeviceSettingActionModel
import com.android.settingslib.bluetooth.devicesettings.shared.model.DeviceSettingConfigItemModel
import com.android.settingslib.bluetooth.devicesettings.shared.model.DeviceSettingConfigModel
import com.android.settingslib.bluetooth.devicesettings.shared.model.DeviceSettingIcon
import com.android.settingslib.bluetooth.devicesettings.shared.model.DeviceSettingModel
import com.android.settingslib.bluetooth.devicesettings.shared.model.DeviceSettingStateModel
import com.android.settingslib.bluetooth.devicesettings.shared.model.ToggleModel
import com.android.settingslib.core.AbstractPreferenceController
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.any
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.Spy
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.doNothing
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowLooper.shadowMainLooper

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class DeviceDetailsFragmentFormatterTest {
    @get:Rule val mockitoRule: MockitoRule = MockitoJUnit.rule()

    @Mock private lateinit var cachedDevice: CachedBluetoothDevice
    @Mock private lateinit var bluetoothAdapter: BluetoothAdapter
    @Mock private lateinit var repository: DeviceSettingRepository
    @Mock private lateinit var profileController: AbstractPreferenceController
    @Mock private lateinit var headerController: AbstractPreferenceController
    @Mock private lateinit var buttonController: AbstractPreferenceController

    @Spy private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var fragment: TestFragment
    private lateinit var underTest: DeviceDetailsFragmentFormatter
    private lateinit var featureFactory: FakeFeatureFactory
    private lateinit var fragmentActivity: FragmentActivity
    private val testScope = TestScope()

    @Before
    fun setUp() {
        featureFactory = FakeFeatureFactory.setupForTest()
        doNothing().`when`(context).startActivity(any(Intent::class.java))
        `when`(
                featureFactory.bluetoothFeatureProvider.getDeviceSettingRepository(
                    any(),
                    eq(bluetoothAdapter),
                    any(),
                )
            )
            .thenReturn(repository)
        fragmentActivity = Robolectric.setupActivity(FragmentActivity::class.java)
        assertThat(fragmentActivity.applicationContext).isNotNull()
        fragment = TestFragment(context)
        fragmentActivity.supportFragmentManager.beginTransaction().add(fragment, null).commit()
        shadowMainLooper().idle()

        fragment.preferenceScreen.run {
            addPreference(Preference(context).apply { key = "bluetooth_device_header" })
            addPreference(Preference(context).apply { key = "action_buttons" })
            addPreference(Preference(context).apply { key = "bluetooth_profiles" })
        }
        `when`(profileController.preferenceKey).thenReturn("bluetooth_profiles")
        `when`(headerController.preferenceKey).thenReturn("bluetooth_device_header")
        `when`(buttonController.preferenceKey).thenReturn("action_buttons")

        underTest =
            DeviceDetailsFragmentFormatterImpl(
                context,
                fragment,
                listOf(profileController, headerController, buttonController),
                bluetoothAdapter,
                cachedDevice,
                testScope.testScheduler,
            )
    }

    @Test
    fun getMenuItem_returnItem() {
        testScope.runTest {
            `when`(repository.getDeviceSettingsConfig(cachedDevice))
                .thenReturn(
                    DeviceSettingConfigModel(
                        listOf(),
                        listOf(),
                        DeviceSettingConfigItemModel.AppProvidedItem(12345, false),
                    )
                )
            val intent =
                Intent().apply {
                    setAction(Intent.ACTION_VIEW)
                    setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            `when`(repository.getDeviceSetting(cachedDevice, 12345))
                .thenReturn(
                    flowOf(
                        DeviceSettingModel.HelpPreference(
                            cachedDevice = cachedDevice,
                            id = 12345,
                            intent = intent,
                        )
                    )
                )

            var helpPreference: DeviceSettingPreferenceModel.HelpPreference? = null
            underTest
                .getMenuItem(FragmentTypeModel.DeviceDetailsMoreSettingsFragment)
                .onEach { helpPreference = it }
                .launchIn(testScope.backgroundScope)
            delay(100)
            runCurrent()
            ShadowLooper.idleMainLooper()

            assertThat(helpPreference?.intent).isSameInstanceAs(intent)
        }
    }

    @Test
    fun updateLayout_configIsNull_notChange() {
        testScope.runTest {
            `when`(repository.getDeviceSettingsConfig(cachedDevice)).thenReturn(null)

            underTest.updateLayout(FragmentTypeModel.DeviceDetailsMainFragment)

            assertThat(getDisplayedPreferences().mapNotNull { it.key })
                .containsExactly("bluetooth_device_header", "action_buttons", "bluetooth_profiles")
        }
    }

    @Test
    fun updateLayout_itemsNotInConfig_hide() {
        testScope.runTest {
            `when`(repository.getDeviceSettingsConfig(cachedDevice))
                .thenReturn(
                    DeviceSettingConfigModel(
                        listOf(
                            DeviceSettingConfigItemModel.BuiltinItem.CommonBuiltinItem(
                                DeviceSettingId.DEVICE_SETTING_ID_HEADER,
                                highlighted = false,
                                preferenceKey = "bluetooth_device_header",
                            ),
                            DeviceSettingConfigItemModel.BuiltinItem.CommonBuiltinItem(
                                DeviceSettingId.DEVICE_SETTING_ID_BLUETOOTH_PROFILES,
                                highlighted = false,
                                preferenceKey = "bluetooth_profiles",
                            ),
                        ),
                        listOf(),
                        null,
                    )
                )

            underTest.updateLayout(FragmentTypeModel.DeviceDetailsMainFragment)
            runCurrent()

            assertThat(getDisplayedPreferences().mapNotNull { it.key })
                .containsExactly("bluetooth_device_header", "bluetooth_profiles")
            verify(featureFactory.metricsFeatureProvider)
                .action(
                    SettingsEnums.PAGE_UNKNOWN,
                    SettingsEnums.ACTION_BLUETOOTH_DEVICE_DETAILS_ITEM_SHOWN,
                    0,
                    "bluetooth_device_header",
                    1,
                )
            verify(featureFactory.metricsFeatureProvider)
                .action(
                    SettingsEnums.PAGE_UNKNOWN,
                    SettingsEnums.ACTION_BLUETOOTH_DEVICE_DETAILS_ITEM_SHOWN,
                    0,
                    "bluetooth_profiles",
                    1,
                )
        }
    }

    @Test
    fun updateLayout_newItems_displayNewItems() {
        testScope.runTest {
            `when`(repository.getDeviceSettingsConfig(cachedDevice))
                .thenReturn(
                    DeviceSettingConfigModel(
                        listOf(
                            DeviceSettingConfigItemModel.BuiltinItem.CommonBuiltinItem(
                                DeviceSettingId.DEVICE_SETTING_ID_HEADER,
                                highlighted = false,
                                preferenceKey = "bluetooth_device_header",
                            ),
                            DeviceSettingConfigItemModel.AppProvidedItem(
                                DeviceSettingId.DEVICE_SETTING_ID_ANC,
                                highlighted = false,
                            ),
                            DeviceSettingConfigItemModel.BuiltinItem.CommonBuiltinItem(
                                DeviceSettingId.DEVICE_SETTING_ID_BLUETOOTH_PROFILES,
                                highlighted = false,
                                preferenceKey = "bluetooth_profiles",
                            ),
                        ),
                        listOf(),
                        null,
                    )
                )
            `when`(repository.getDeviceSetting(cachedDevice, DeviceSettingId.DEVICE_SETTING_ID_ANC))
                .thenReturn(
                    flowOf(
                        DeviceSettingModel.MultiTogglePreference(
                            cachedDevice,
                            DeviceSettingId.DEVICE_SETTING_ID_ANC,
                            "title",
                            toggles =
                                listOf(
                                    ToggleModel(
                                        "",
                                        DeviceSettingIcon.BitmapIcon(
                                            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                                        ),
                                    )
                                ),
                            isActive = true,
                            state = DeviceSettingStateModel.MultiTogglePreferenceState(0),
                            isAllowedChangingState = true,
                            updateState = {},
                        )
                    )
                )

            underTest.updateLayout(FragmentTypeModel.DeviceDetailsMainFragment)
            runCurrent()

            assertThat(getDisplayedPreferences().mapNotNull { it.key })
                .containsExactly(
                    "bluetooth_device_header",
                    "DEVICE_SETTING_${DeviceSettingId.DEVICE_SETTING_ID_ANC}",
                    "bluetooth_profiles",
                )
            verify(featureFactory.metricsFeatureProvider)
                .action(
                    SettingsEnums.PAGE_UNKNOWN,
                    SettingsEnums.ACTION_BLUETOOTH_DEVICE_DETAILS_ITEM_SHOWN,
                    0,
                    "DEVICE_SETTING_${DeviceSettingId.DEVICE_SETTING_ID_ANC}",
                    1,
                )
        }
    }

    @Test
    fun updateLayout_plainPreferenceClicked() {
        testScope.runTest {
            val settingId = 12345
            val intent = Intent("test_intent")
            `when`(repository.getDeviceSettingsConfig(cachedDevice))
                .thenReturn(
                    DeviceSettingConfigModel(
                        listOf(
                            DeviceSettingConfigItemModel.AppProvidedItem(
                                settingId,
                                highlighted = false,
                            )
                        ),
                        listOf(),
                        null,
                    )
                )

            `when`(repository.getDeviceSetting(cachedDevice, settingId))
                .thenReturn(
                    flowOf(
                        DeviceSettingModel.ActionSwitchPreference(
                            cachedDevice = cachedDevice,
                            id = settingId,
                            title = "title",
                            summary = "summary",
                            icon = null,
                            action = DeviceSettingActionModel.IntentAction(intent),
                        )
                    )
                )

            underTest.updateLayout(FragmentTypeModel.DeviceDetailsMainFragment)
            runCurrent()
            val displayedPrefs = getDisplayedPreferences()
            displayedPrefs[0].performClick()

            assertThat(displayedPrefs).hasSize(1)
            verify(context).startActivity(intent)
            verify(featureFactory.metricsFeatureProvider)
                .action(
                    SettingsEnums.PAGE_UNKNOWN,
                    SettingsEnums.ACTION_BLUETOOTH_DEVICE_DETAILS_ITEM_CLICKED,
                    0,
                    "DEVICE_SETTING_$settingId",
                    2,
                )
        }
    }

    @Test
    fun updateLayout_switchPreferenceClicked() {
        val settingId = 12345
        testScope.runTest {
            `when`(repository.getDeviceSettingsConfig(cachedDevice))
                .thenReturn(
                    DeviceSettingConfigModel(
                        listOf(
                            DeviceSettingConfigItemModel.AppProvidedItem(
                                settingId,
                                highlighted = false,
                            )
                        ),
                        listOf(),
                        null,
                    )
                )

            `when`(repository.getDeviceSetting(cachedDevice, settingId))
                .thenReturn(
                    flowOf(
                        DeviceSettingModel.ActionSwitchPreference(
                            cachedDevice = cachedDevice,
                            id = settingId,
                            title = "title",
                            summary = "summary",
                            icon = null,
                            action = null,
                            switchState = DeviceSettingStateModel.ActionSwitchPreferenceState(true),
                            isAllowedChangingState = true,
                            updateState = {},
                        )
                    )
                )

            underTest.updateLayout(FragmentTypeModel.DeviceDetailsMainFragment)
            runCurrent()
            val displayedPrefs = getDisplayedPreferences()
            displayedPrefs[0].performClick()

            assertThat(displayedPrefs).hasSize(1)
            assertThat(displayedPrefs[0]).isInstanceOf(SwitchPreferenceCompat::class.java)
            verify(featureFactory.metricsFeatureProvider)
                .action(
                    SettingsEnums.PAGE_UNKNOWN,
                    SettingsEnums.ACTION_BLUETOOTH_DEVICE_DETAILS_ITEM_CLICKED,
                    0,
                    "DEVICE_SETTING_$settingId",
                    0,
                )
        }
    }

    private fun getDisplayedPreferences(): List<Preference> {
        val prefs = mutableListOf<Preference>()
        for (i in 0..<fragment.preferenceScreen.preferenceCount) {
            prefs.add(fragment.preferenceScreen.getPreference(i))
        }
        return prefs.filter { it.key != null }
    }

    class TestFragment(context: Context) : DashboardFragment() {
        private val mPreferenceManager: PreferenceManager = PreferenceManager(context)

        init {
            mPreferenceManager.setPreferences(mPreferenceManager.createPreferenceScreen(context))
        }

        public override fun getPreferenceScreenResId(): Int = 0

        override fun getLogTag(): String = "TestLogTag"

        override fun getPreferenceScreen(): PreferenceScreen {
            return mPreferenceManager.preferenceScreen
        }

        override fun getMetricsCategory(): Int = 0

        override fun getPreferenceManager(): PreferenceManager {
            return mPreferenceManager
        }
    }

    private companion object {}
}
