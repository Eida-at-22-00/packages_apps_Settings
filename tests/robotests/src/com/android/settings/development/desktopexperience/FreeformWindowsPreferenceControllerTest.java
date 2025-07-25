/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.settings.development.desktopexperience;

import static android.content.pm.PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT;

import static com.android.settings.development.desktopexperience.FreeformWindowsPreferenceController.SETTING_VALUE_OFF;
import static com.android.settings.development.desktopexperience.FreeformWindowsPreferenceController.SETTING_VALUE_ON;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.provider.Settings;

import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import com.android.internal.R;
import com.android.settings.development.DevelopmentSettingsDashboardFragment;
import com.android.settings.development.RebootConfirmationDialogFragment;
import com.android.window.flags.Flags;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(shadows = {
        com.android.settings.testutils.shadow.ShadowFragment.class,
})
public class FreeformWindowsPreferenceControllerTest {

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Mock
    Context mContext;
    @Mock
    private PackageManager mPackageManager;
    @Mock
    private Resources mResources;
    @Mock
    private SwitchPreference mPreference;
    @Mock
    private PreferenceScreen mScreen;
    @Mock
    private DevelopmentSettingsDashboardFragment mFragment;
    @Mock
    private FragmentManager mFragmentManager;
    @Mock
    private FragmentTransaction mTransaction;

    private FreeformWindowsPreferenceController mController;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        FragmentActivity activity = spy(Robolectric.buildActivity(
                FragmentActivity.class).create().get());
        doReturn(mTransaction).when(mFragmentManager).beginTransaction();
        doReturn(mFragmentManager).when(activity).getSupportFragmentManager();
        doReturn(activity).when(mFragment).requireActivity();
        doReturn(true).when(mResources).getBoolean(R.bool.config_isDesktopModeSupported);
        doReturn(true).when(mResources).getBoolean(R.bool.config_canInternalDisplayHostDesktops);
        mController = new FreeformWindowsPreferenceController(mContext, mFragment);
        when(mScreen.findPreference(mController.getPreferenceKey())).thenReturn(mPreference);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mContext.getResources()).thenReturn(mResources);
        mController.displayPreference(mScreen);
    }

    @DisableFlags({Flags.FLAG_SHOW_DESKTOP_EXPERIENCE_DEV_OPTION,
            Flags.FLAG_SHOW_DESKTOP_WINDOWING_DEV_OPTION})
    @Test
    public void isAvailable_whenDesktopDevOptionsAreDisabled_returnsTrue() {
        assertThat(mController.isAvailable()).isTrue();
    }

    @EnableFlags(Flags.FLAG_SHOW_DESKTOP_WINDOWING_DEV_OPTION)
    @Test
    public void isAvailable_whenDesktopWindowingDevOptionIsEnabled_returnsFalse() {
        assertThat(mController.isAvailable()).isFalse();
    }

    @EnableFlags(Flags.FLAG_SHOW_DESKTOP_EXPERIENCE_DEV_OPTION)
    @Test
    public void isAvailable_whenDesktopExperienceDevOptionIsEnabled_returnsFalse() {
        assertThat(mController.isAvailable()).isFalse();
    }

    @DisableFlags({Flags.FLAG_SHOW_DESKTOP_EXPERIENCE_DEV_OPTION,
            Flags.FLAG_SHOW_DESKTOP_WINDOWING_DEV_OPTION})
    @Test
    public void isAvailable_deviceHasFreeformWindowSystemFeature_returnsFalse() {
        when(mPackageManager.hasSystemFeature(FEATURE_FREEFORM_WINDOW_MANAGEMENT)).thenReturn(true);

        assertThat(mController.isAvailable()).isFalse();
    }

    @DisableFlags({Flags.FLAG_SHOW_DESKTOP_EXPERIENCE_DEV_OPTION,
            Flags.FLAG_SHOW_DESKTOP_WINDOWING_DEV_OPTION})
    @Test
    public void isAvailable_deviceDoesNotHaveFreeformWindowSystemFeature_returnsTrue() {
        when(mPackageManager.hasSystemFeature(FEATURE_FREEFORM_WINDOW_MANAGEMENT)).thenReturn(
                false);

        assertThat(mController.isAvailable()).isTrue();
    }

    @Test
    public void onPreferenceChange_switchEnabled_shouldEnableFreeformWindows() {
        mController.onPreferenceChange(mPreference, true /* new value */);

        final int mode = Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.DEVELOPMENT_ENABLE_FREEFORM_WINDOWS_SUPPORT, -1 /* default */);
        assertThat(mode).isEqualTo(SETTING_VALUE_ON);

        verify(mTransaction).add(any(RebootConfirmationDialogFragment.class), any());
    }

    @Test
    public void onPreferenceChange_switchDisabled_shouldDisableFreeformWindows() {
        mController.onPreferenceChange(mPreference, false /* new value */);

        final int mode = Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.DEVELOPMENT_ENABLE_FREEFORM_WINDOWS_SUPPORT, -1 /* default */);
        assertThat(mode).isEqualTo(SETTING_VALUE_OFF);
    }

    @Test
    public void updateState_settingEnabled_preferenceShouldBeChecked() {
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.DEVELOPMENT_ENABLE_FREEFORM_WINDOWS_SUPPORT, SETTING_VALUE_ON);

        mController.updateState(mPreference);

        verify(mPreference).setChecked(true);
    }

    @Test
    public void updateState_settingDisabled_preferenceShouldNotBeChecked() {
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.DEVELOPMENT_ENABLE_FREEFORM_WINDOWS_SUPPORT, SETTING_VALUE_OFF);

        mController.updateState(mPreference);

        verify(mPreference).setChecked(false);
    }

    @Test
    public void onDeveloperOptionsSwitchDisabled_shouldDisablePreference() {
        mController.onDeveloperOptionsSwitchDisabled();

        final int mode = Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.DEVELOPMENT_ENABLE_FREEFORM_WINDOWS_SUPPORT, -1 /* default */);
        assertThat(mode).isEqualTo(SETTING_VALUE_OFF);
        verify(mPreference).setEnabled(false);
    }
}
