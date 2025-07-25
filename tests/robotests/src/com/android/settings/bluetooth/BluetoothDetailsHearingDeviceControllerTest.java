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

package com.android.settings.bluetooth;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import com.android.settingslib.bluetooth.LocalBluetoothManager;
import com.android.settingslib.bluetooth.LocalBluetoothProfileManager;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;

/** Tests for {@link BluetoothDetailsHearingDeviceController}. */
@RunWith(RobolectricTestRunner.class)
public class BluetoothDetailsHearingDeviceControllerTest extends
        BluetoothDetailsControllerTestBase {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule
    public final MockitoRule mockito = MockitoJUnit.rule();

    @Mock
    private LocalBluetoothManager mLocalManager;
    @Mock
    private LocalBluetoothProfileManager mProfileManager;
    @Mock
    private BluetoothDetailsHearingAidsPresetsController mPresetsController;
    @Mock
    private BluetoothDetailsHearingDeviceSettingsController mHearingDeviceSettingsController;
    @Mock
    private BluetoothDetailsHearingDeviceInputRoutingController mInputRoutingController;

    private BluetoothDetailsHearingDeviceController mHearingDeviceController;

    @Override
    public void setUp() {
        super.setUp();

        when(mLocalManager.getProfileManager()).thenReturn(mProfileManager);
        mHearingDeviceController = new BluetoothDetailsHearingDeviceController(mContext,
                mFragment, mLocalManager, mCachedDevice, mLifecycle);
        mHearingDeviceController.setSubControllers(mHearingDeviceSettingsController,
                mPresetsController, mInputRoutingController);
    }

    @Test
    public void isAvailable_hearingDeviceSettingsAvailable_returnTrue() {
        when(mHearingDeviceSettingsController.isAvailable()).thenReturn(true);

        assertThat(mHearingDeviceController.isAvailable()).isTrue();
    }

    @Test
    public void isAvailable_presetsControlsAvailable_returnTrue() {
        when(mPresetsController.isAvailable()).thenReturn(true);

        assertThat(mHearingDeviceController.isAvailable()).isTrue();
    }

    @Test
    public void isAvailable_inputRoutingControllersAvailable_returnFalse() {
        when(mInputRoutingController.isAvailable()).thenReturn(true);

        assertThat(mHearingDeviceController.isAvailable()).isTrue();
    }

    @Test
    public void isAvailable_noControllersAvailable_returnFalse() {
        when(mHearingDeviceSettingsController.isAvailable()).thenReturn(false);
        when(mPresetsController.isAvailable()).thenReturn(false);

        assertThat(mHearingDeviceController.isAvailable()).isFalse();
    }


    @Test
    public void initSubControllers_launchFromHearingDevicePage_hearingDeviceSettingsNotExist() {
        mHearingDeviceController.initSubControllers(true);

        assertThat(mHearingDeviceController.getSubControllers().stream().anyMatch(
                c -> c instanceof BluetoothDetailsHearingDeviceSettingsController)).isFalse();
    }

    @Test
    public void initSubControllers_notLaunchFromHearingDevicePage_hearingDeviceSettingsExist() {
        mHearingDeviceController.initSubControllers(false);

        assertThat(mHearingDeviceController.getSubControllers().stream().anyMatch(
                c -> c instanceof BluetoothDetailsHearingDeviceSettingsController)).isTrue();
    }

    @Test
    public void initSubControllers_presetControllerExist() {
        mHearingDeviceController.initSubControllers(false);

        assertThat(mHearingDeviceController.getSubControllers().stream().anyMatch(
                c -> c instanceof BluetoothDetailsHearingAidsPresetsController)).isTrue();
    }

    @Test
    @RequiresFlagsEnabled(
            com.android.settingslib.flags.Flags.FLAG_HEARING_DEVICES_AMBIENT_VOLUME_CONTROL)
    public void initSubControllers_flagEnabled_ambientVolumeControllerExist() {
        mHearingDeviceController.initSubControllers(false);

        assertThat(mHearingDeviceController.getSubControllers().stream().anyMatch(
                c -> c instanceof BluetoothDetailsAmbientVolumePreferenceController)).isTrue();
    }

    @Test
    @RequiresFlagsDisabled(
            com.android.settingslib.flags.Flags.FLAG_HEARING_DEVICES_AMBIENT_VOLUME_CONTROL)
    public void initSubControllers_flagDisabled_ambientVolumeControllerNotExist() {
        mHearingDeviceController.initSubControllers(false);

        assertThat(mHearingDeviceController.getSubControllers().stream().anyMatch(
                c -> c instanceof BluetoothDetailsAmbientVolumePreferenceController)).isFalse();
    }

    @Test
    @RequiresFlagsEnabled(
            com.android.settingslib.flags.Flags.FLAG_HEARING_DEVICES_INPUT_ROUTING_CONTROL)
    public void initSubControllers_flagEnabled_inputRoutingControllerExist() {
        mHearingDeviceController.initSubControllers(false);

        assertThat(mHearingDeviceController.getSubControllers().stream().anyMatch(
                c -> c instanceof BluetoothDetailsHearingDeviceInputRoutingController)).isTrue();
    }

    @Test
    @RequiresFlagsDisabled(
            com.android.settingslib.flags.Flags.FLAG_HEARING_DEVICES_INPUT_ROUTING_CONTROL)
    public void initSubControllers_flagDisabled_inputRoutingControllerNotExist() {
        mHearingDeviceController.initSubControllers(false);

        assertThat(mHearingDeviceController.getSubControllers().stream().anyMatch(
                c -> c instanceof BluetoothDetailsHearingDeviceInputRoutingController)).isFalse();
    }
}
