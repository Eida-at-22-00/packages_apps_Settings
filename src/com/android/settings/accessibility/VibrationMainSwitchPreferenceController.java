/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.settings.accessibility;

import static com.android.settings.accessibility.AccessibilityUtil.State.OFF;
import static com.android.settings.accessibility.AccessibilityUtil.State.ON;

import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationAttributes;
import android.os.Vibrator;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.core.TogglePreferenceController;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnStart;
import com.android.settingslib.core.lifecycle.events.OnStop;

/**
 * Preference controller for the main switch setting for vibration and haptics screen.
 *
 * <p>This preference is controlled by the setting key{@link Settings.System#VIBRATE_ON}, and it
 * will disable the entire settings screen once the settings is turned OFF. All device haptics will
 * be disabled by this setting, except the flagged alerts and accessibility touch feedback.
 */
// LINT.IfChange
public class VibrationMainSwitchPreferenceController extends TogglePreferenceController
        implements LifecycleObserver, OnStart, OnStop {

    private final ContentObserver mSettingObserver;
    private final Vibrator mVibrator;
    private @Nullable Preference mPreference;

    public VibrationMainSwitchPreferenceController(Context context, String preferenceKey) {
        super(context, preferenceKey);
        mVibrator = context.getSystemService(Vibrator.class);
        Handler handler = Looper.myLooper() != null ? new Handler(/* async= */ true) : null;
        mSettingObserver = new ContentObserver(handler) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                if (mPreference != null) {
                    updateState(mPreference);
                }
            }
        };
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public void displayPreference(@NonNull PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = screen.findPreference(getPreferenceKey());
    }

    @Override
    public void onStart() {
        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(VibrationPreferenceConfig.MAIN_SWITCH_SETTING_KEY),
                /* notifyForDescendants= */ false,
                mSettingObserver);
    }

    @Override
    public void onStop() {
        mContext.getContentResolver().unregisterContentObserver(mSettingObserver);
    }

    @Override
    public boolean isChecked() {
        return VibrationPreferenceConfig.isMainVibrationSwitchEnabled(
                mContext.getContentResolver());
    }

    @Override
    public boolean setChecked(boolean isChecked) {
        // The main switch change can be triggered by both the user click and the
        // SettingsMainSwitchPreferenceController state change. Make sure we only do it once.
        boolean wasChecked = isChecked();
        boolean success = Settings.System.putInt(mContext.getContentResolver(),
                VibrationPreferenceConfig.MAIN_SWITCH_SETTING_KEY,
                isChecked ? ON : OFF);

        if (success && !wasChecked && isChecked) {
            // Play a haptic as preview for the main toggle only when touch feedback is enabled.
            VibrationPreferenceConfig.playVibrationPreview(
                    mVibrator, VibrationAttributes.USAGE_TOUCH);
        }

        return success;
    }

    @Override
    public int getSliceHighlightMenuRes() {
        return R.string.menu_key_accessibility;
    }
}
// LINT.ThenChange(VibrationMainSwitchPreference.kt)
