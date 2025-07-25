/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.settings.gestures;

import static android.provider.Settings.Secure.CAMERA_DOUBLE_TAP_POWER_GESTURE_DISABLED;
import static android.provider.Settings.System.TORCH_POWER_BUTTON_GESTURE;

import static com.android.settings.gestures.DoubleTapPowerSettingsUtils.DOUBLE_TAP_POWER_DISABLED_MODE;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnStart;
import com.android.settingslib.core.lifecycle.events.OnStop;
import com.android.settings.core.BasePreferenceController;

public class DoubleTapPowerPreferenceController extends BasePreferenceController
        implements LifecycleObserver, OnStart, OnStop {

    private Preference mPreference;

    private final ContentObserver mTorchObserver = new ContentObserver(Handler.getMain()) {
        @Override
        public void onChange(boolean selfChange) {
            if (mPreference != null)
                updateState(mPreference);
        }
    };

    public DoubleTapPowerPreferenceController(@NonNull Context context, @NonNull String key) {
        super(context, key);
    }

    public static boolean isSuggestionComplete(Context context, SharedPreferences prefs) {
        return !isGestureAvailable(context)
                || prefs.getBoolean(DoubleTapPowerSettings.PREF_KEY_SUGGESTION_COMPLETE,
                false);
    }

    private static boolean isGestureAvailable(@NonNull Context context) {
        if (!android.service.quickaccesswallet.Flags.launchWalletOptionOnPowerDoubleTap()) {
            return context.getResources()
                    .getBoolean(
                            com.android.internal.R.bool.config_cameraDoubleTapPowerGestureEnabled);
        }
        return context.getResources()
                .getInteger(
                        com.android.internal.R.integer.config_doubleTapPowerGestureMode)
                != DOUBLE_TAP_POWER_DISABLED_MODE;
    }

    public static boolean isAvailableDependent(Context context) {
        final int value = Settings.System.getInt(
                context.getContentResolver(), TORCH_POWER_BUTTON_GESTURE, 0);
        if (value == 1) return false;
        return true;
    }

    @Override
    public void onStart() {
        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(TORCH_POWER_BUTTON_GESTURE),
                false, mTorchObserver);
    }

    @Override
    public void onStop() {
        mContext.getContentResolver().unregisterContentObserver(mTorchObserver);
    }

    @Override
    public void updateState(Preference preference) {
        super.updateState(preference);
        preference.setEnabled(getAvailabilityStatus() == AVAILABLE);
    }

    @Override
    public int getAvailabilityStatus() {
        if (!isGestureAvailable(mContext)) return UNSUPPORTED_ON_DEVICE;
        if (!isAvailableDependent(mContext)) return DISABLED_DEPENDENT_SETTING;
        return AVAILABLE;
    }

    @Override
    public void displayPreference(@NonNull PreferenceScreen screen) {
        mPreference = screen.findPreference(getPreferenceKey());
        if (!android.service.quickaccesswallet.Flags.launchWalletOptionOnPowerDoubleTap()
                || !DoubleTapPowerSettingsUtils
                .isMultiTargetDoubleTapPowerButtonGestureAvailable(mContext)) {
            if (mPreference != null) {
                mPreference.setTitle(R.string.double_tap_power_for_camera_title);
            }
        }
        super.displayPreference(screen);
    }

    @Override
    @NonNull
    public CharSequence getSummary() {
        if (!android.service.quickaccesswallet.Flags.launchWalletOptionOnPowerDoubleTap()
                || !DoubleTapPowerSettingsUtils
                .isMultiTargetDoubleTapPowerButtonGestureAvailable(mContext)) {
            final boolean isCameraDoubleTapPowerGestureEnabled =
                    Settings.Secure.getInt(
                            mContext.getContentResolver(),
                            CAMERA_DOUBLE_TAP_POWER_GESTURE_DISABLED,
                            DoubleTapPowerToOpenCameraPreferenceController.ON)
                            == DoubleTapPowerToOpenCameraPreferenceController.ON;
            return mContext.getText(
                    isCameraDoubleTapPowerGestureEnabled
                            ? R.string.gesture_setting_on
                            : R.string.gesture_setting_off);
        }
        if (DoubleTapPowerSettingsUtils.isDoubleTapPowerButtonGestureEnabled(mContext)) {
            final CharSequence onString =
                    mContext.getText(com.android.settings.R.string.gesture_setting_on);
            final CharSequence actionString =
                    DoubleTapPowerSettingsUtils.isDoubleTapPowerButtonGestureForCameraLaunchEnabled(
                            mContext)
                            ? mContext.getText(R.string.double_tap_power_camera_action_summary)
                            : mContext.getText(R.string.double_tap_power_wallet_action_summary);
            return mContext.getString(R.string.double_tap_power_summary, onString, actionString);
        }
        return mContext.getText(com.android.settings.R.string.gesture_setting_off);
    }
}
