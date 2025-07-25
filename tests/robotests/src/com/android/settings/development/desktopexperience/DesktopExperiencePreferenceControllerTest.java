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

package com.android.settings.development.desktopexperience;

import static android.provider.Settings.Global.DEVELOPMENT_OVERRIDE_DESKTOP_EXPERIENCE_FEATURES;
import static android.window.DesktopModeFlags.ToggleOverride.OVERRIDE_OFF;
import static android.window.DesktopModeFlags.ToggleOverride.OVERRIDE_ON;
import static android.window.DesktopModeFlags.ToggleOverride.OVERRIDE_UNSET;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
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
import androidx.test.core.app.ApplicationProvider;

import com.android.settings.R;
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
import org.robolectric.shadows.ShadowSystemProperties;

@RunWith(RobolectricTestRunner.class)
@Config(shadows = {
        com.android.settings.testutils.shadow.ShadowFragment.class,
})
@EnableFlags(Flags.FLAG_SHOW_DESKTOP_EXPERIENCE_DEV_OPTION)
public class DesktopExperiencePreferenceControllerTest {
    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

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

    private Resources mResources;
    private Context mContext;
    private DesktopExperiencePreferenceController mController;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        FragmentActivity activity = spy(Robolectric.buildActivity(
                FragmentActivity.class).create().get());
        mContext = spy(ApplicationProvider.getApplicationContext());
        doReturn(mTransaction).when(mFragmentManager).beginTransaction();
        doReturn(mFragmentManager).when(activity).getSupportFragmentManager();
        doReturn(activity).when(mFragment).requireActivity();

        mResources = spy(mContext.getResources());
        when(mContext.getResources()).thenReturn(mResources);

        mController = new DesktopExperiencePreferenceController(mContext, mFragment);

        when(mScreen.findPreference(mController.getPreferenceKey())).thenReturn(mPreference);
        mController.displayPreference(mScreen);

        // Set desktop mode available
        when(mResources.getBoolean(com.android.internal.R.bool.config_isDesktopModeSupported))
                .thenReturn(true);
        when(mResources
                .getBoolean(com.android.internal.R.bool.config_canInternalDisplayHostDesktops))
                .thenReturn(true);
        ShadowSystemProperties.override("persist.wm.debug.desktop_mode_enforce_device_restrictions",
                "false");
    }

    @Test
    public void isAvailable_returnsTrue() {
        mController = spy(mController);

        assertThat(mController.isAvailable()).isTrue();
    }

    @Test
    public void onPreferenceChange_switchEnabled_putsSettingsOverrideOnAndTriggersRestart() {
        mController.onPreferenceChange(mPreference, true /* new value */);

        final int mode = Settings.Global.getInt(mContext.getContentResolver(),
                DEVELOPMENT_OVERRIDE_DESKTOP_EXPERIENCE_FEATURES, -1 /* default */);
        assertThat(mode).isEqualTo(OVERRIDE_ON.getSetting());
        verify(mTransaction).add(any(RebootConfirmationDialogFragment.class), any());
    }

    @Test
    public void onPreferenceChange_switchDisabled_putsSettingsOverrideOffAndTriggersRestart() {
        mController.onPreferenceChange(mPreference, false /* new value */);

        int mode = Settings.Global.getInt(mContext.getContentResolver(),
                DEVELOPMENT_OVERRIDE_DESKTOP_EXPERIENCE_FEATURES, -1 /* default */);
        assertThat(mode).isEqualTo(OVERRIDE_OFF.getSetting());
        verify(mTransaction).add(any(RebootConfirmationDialogFragment.class), any());
    }

    @Test
    public void updateState_overrideOn_checksPreference() {
        Settings.Global.putInt(mContext.getContentResolver(),
                DEVELOPMENT_OVERRIDE_DESKTOP_EXPERIENCE_FEATURES, OVERRIDE_ON.getSetting());

        mController.updateState(mPreference);

        verify(mPreference).setChecked(true);
    }

    @Test
    public void updateState_overrideOff_unchecksPreference() {
        Settings.Global.putInt(mContext.getContentResolver(),
                DEVELOPMENT_OVERRIDE_DESKTOP_EXPERIENCE_FEATURES, OVERRIDE_OFF.getSetting());

        mController.updateState(mPreference);

        verify(mPreference).setChecked(false);
    }

    @Test
    public void updateState_noOverride_noNewSettingsOverride() {
        // Set no override
        Settings.Global.putString(mContext.getContentResolver(),
                DEVELOPMENT_OVERRIDE_DESKTOP_EXPERIENCE_FEATURES, null);

        mController.updateState(mPreference);

        int mode = Settings.Global.getInt(mContext.getContentResolver(),
                DEVELOPMENT_OVERRIDE_DESKTOP_EXPERIENCE_FEATURES, -2 /* default */);
        assertThat(mode).isEqualTo(-2);
    }

    @Test
    public void onDeveloperOptionsSwitchDisabled_putsSettingsOverrideOff() {
        mController.onDeveloperOptionsSwitchDisabled();

        final int mode = Settings.Global.getInt(mContext.getContentResolver(),
                DEVELOPMENT_OVERRIDE_DESKTOP_EXPERIENCE_FEATURES, -2 /* default */);
        assertThat(mode).isEqualTo(OVERRIDE_UNSET.getSetting());
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void updateState_whenDesktopModeAvailableButNotEnabled_checkSummary() {
        SwitchPreference pref = new SwitchPreference(mContext);

        mController.updateState(pref);

        assertThat(pref.getSummary()).isEqualTo(mContext.getString(
                R.string.enable_desktop_experience_features_summary_with_desktop));
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    public void updateState_whenDesktopModeAvailableAndEnabled_checkSummary() {
        SwitchPreference pref = new SwitchPreference(mContext);

        mController.updateState(pref);

        assertThat(pref.getSummary()).isEqualTo(mContext.getString(
                R.string.enable_desktop_experience_features_summary_without_desktop));
    }
}
