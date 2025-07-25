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

package com.android.settings.bluetooth.ui.viewmodel

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.android.settings.bluetooth.ui.model.DeviceSettingPreferenceModel
import com.android.settings.bluetooth.ui.model.FragmentTypeModel
import com.android.settings.testutils.FakeFeatureFactory
import com.android.settingslib.bluetooth.CachedBluetoothDevice
import com.android.settingslib.bluetooth.devicesettings.DeviceSettingId
import com.android.settingslib.bluetooth.devicesettings.data.repository.DeviceSettingRepository
import com.android.settingslib.bluetooth.devicesettings.shared.model.DeviceSettingConfigItemModel
import com.android.settingslib.bluetooth.devicesettings.shared.model.DeviceSettingConfigModel
import com.android.settingslib.bluetooth.devicesettings.shared.model.DeviceSettingIcon
import com.android.settingslib.bluetooth.devicesettings.shared.model.DeviceSettingModel
import com.android.settingslib.bluetooth.devicesettings.shared.model.DeviceSettingStateModel
import com.android.settingslib.bluetooth.devicesettings.shared.model.ToggleModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BluetoothDeviceDetailsViewModelTest {
    @get:Rule val mockitoRule: MockitoRule = MockitoJUnit.rule()

    @Mock private lateinit var cachedDevice: CachedBluetoothDevice

    @Mock private lateinit var bluetoothAdapter: BluetoothAdapter

    @Mock private lateinit var repository: DeviceSettingRepository

    private lateinit var underTest: BluetoothDeviceDetailsViewModel
    private lateinit var featureFactory: FakeFeatureFactory
    private val testScope = TestScope()

    @Before
    fun setUp() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        featureFactory = FakeFeatureFactory.setupForTest()

        `when`(
            featureFactory.bluetoothFeatureProvider.getDeviceSettingRepository(
                eq(application), eq(bluetoothAdapter), any()
            ))
            .thenReturn(repository)

        underTest =
            BluetoothDeviceDetailsViewModel(
                application,
                bluetoothAdapter,
                cachedDevice,
                testScope.testScheduler)
    }

    @Test
    fun getItems_returnConfigMainMainItems() {
        testScope.runTest {
            `when`(repository.getDeviceSettingsConfig(cachedDevice))
                .thenReturn(
                    DeviceSettingConfigModel(
                        listOf(BUILTIN_SETTING_ITEM_1, BUILDIN_SETTING_ITEM_2), listOf(), null))

            val keys = underTest.getItems(FragmentTypeModel.DeviceDetailsMainFragment)

            assertThat(keys).containsExactly(BUILTIN_SETTING_ITEM_1, BUILDIN_SETTING_ITEM_2)
        }
    }

    @Test
    fun getHelpItems_mainPage_returnNull() {
        testScope.runTest {
            `when`(repository.getDeviceSettingsConfig(cachedDevice))
                .thenReturn(
                    DeviceSettingConfigModel(
                        listOf(BUILTIN_SETTING_ITEM_1, BUILDIN_SETTING_ITEM_2),
                        listOf(),
                        SETTING_ITEM_HELP))

            val item = underTest.getHelpItem(FragmentTypeModel.DeviceDetailsMainFragment)

            assertThat(item).isNull()
        }
    }

    @Test
    fun getHelpItems_moreSettings_returnConfigHelpItem() {
        testScope.runTest {
            `when`(repository.getDeviceSettingsConfig(cachedDevice))
                .thenReturn(
                    DeviceSettingConfigModel(
                        listOf(BUILTIN_SETTING_ITEM_1, BUILDIN_SETTING_ITEM_2),
                        listOf(),
                        SETTING_ITEM_HELP))

            val item = underTest.getHelpItem(FragmentTypeModel.DeviceDetailsMoreSettingsFragment)

            assertThat(item).isSameInstanceAs(SETTING_ITEM_HELP)
        }
    }

    @Test
    fun getDeviceSetting_returnRepositoryResponse() {
        testScope.runTest {
            val remoteSettingId1 = 10001
            val pref = buildMultiTogglePreference(remoteSettingId1)
            `when`(repository.getDeviceSettingsConfig(cachedDevice))
                .thenReturn(
                    DeviceSettingConfigModel(
                        listOf(
                            BUILTIN_SETTING_ITEM_1,
                            buildRemoteSettingItem(remoteSettingId1),
                        ),
                        listOf(),
                        null))
            `when`(repository.getDeviceSetting(cachedDevice, remoteSettingId1))
                .thenReturn(flowOf(pref))

            var deviceSettingPreference: DeviceSettingPreferenceModel? = null
            underTest
                .getDeviceSetting(cachedDevice, remoteSettingId1)
                .onEach { deviceSettingPreference = it }
                .launchIn(testScope.backgroundScope)
            runCurrent()

            assertThat(deviceSettingPreference?.id).isEqualTo(pref.id)
            verify(repository, times(1)).getDeviceSetting(cachedDevice, remoteSettingId1)
        }
    }

    private fun buildMultiTogglePreference(settingId: Int) =
        DeviceSettingModel.MultiTogglePreference(
            cachedDevice,
            settingId,
            "title",
            toggles =
                listOf(
                    ToggleModel(
                        "toggle1",
                        DeviceSettingIcon.BitmapIcon(
                            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)))),
            isActive = true,
            state = DeviceSettingStateModel.MultiTogglePreferenceState(0),
            isAllowedChangingState = true,
            updateState = {})

    private fun buildActionSwitchPreference(settingId: Int) =
        DeviceSettingModel.ActionSwitchPreference(cachedDevice, settingId, "title")

    private fun buildRemoteSettingItem(settingId: Int) =
        DeviceSettingConfigItemModel.AppProvidedItem(settingId, false)

    private companion object {
        val BUILTIN_SETTING_ITEM_1 =
            DeviceSettingConfigItemModel.BuiltinItem.CommonBuiltinItem(
                DeviceSettingId.DEVICE_SETTING_ID_HEADER, false, "bluetooth_device_header")
        val BUILDIN_SETTING_ITEM_2 =
            DeviceSettingConfigItemModel.BuiltinItem.CommonBuiltinItem(
                DeviceSettingId.DEVICE_SETTING_ID_ACTION_BUTTONS, false, "action_buttons")
        val SETTING_ITEM_HELP = DeviceSettingConfigItemModel.AppProvidedItem(12345, false)
    }
}
