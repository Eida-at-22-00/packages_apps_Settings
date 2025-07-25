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

package com.android.settings.biometrics.fingerprint;

import static android.hardware.biometrics.Flags.screenOffUnlockUdfps;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.fingerprint.FingerprintManager;
import android.os.UserHandle;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.preference.Preference;

import com.android.internal.annotations.VisibleForTesting;
import com.android.settings.Utils;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

/**
 * Preference controller that controls whether show screen off UDFPS unlock toggle for users to
 * turn this feature ON or OFF
 */
@SearchIndexable
public class FingerprintSettingsScreenOffUnlockUdfpsPreferenceController
        extends FingerprintSettingsPreferenceController {
    private static final String TAG =
            "FingerprintSettingsScreenOffUnlockUdfpsPreferenceController";

    @VisibleForTesting
    protected FingerprintManager mFingerprintManager;

    public FingerprintSettingsScreenOffUnlockUdfpsPreferenceController(
            @NonNull Context context, @NonNull String prefKey) {
        super(context, prefKey);
        mFingerprintManager = Utils.getFingerprintManagerOrNull(context);
    }

    @Override
    public boolean isChecked() {
        if (!FingerprintSettings.isFingerprintHardwareDetected(mContext)) {
            return false;
        } else if (getRestrictingAdmin() != null) {
            return false;
        }
        final boolean defEnabled = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_screen_off_udfps_default_on);
        final int value = Settings.Secure.getIntForUser(
                mContext.getContentResolver(),
                Settings.Secure.SCREEN_OFF_UNLOCK_UDFPS_ENABLED,
                defEnabled ? 1 : 0 /* config_screen_off_udfps_enabled */,
                getUserHandle());
        return value == 1;
    }

    @Override
    public boolean setChecked(boolean isChecked) {
        Settings.Secure.putIntForUser(
                mContext.getContentResolver(),
                Settings.Secure.SCREEN_OFF_UNLOCK_UDFPS_ENABLED,
                isChecked ? 1 : 0,
                getUserHandle());
        return true;
    }

    @Override
    public void updateState(@NonNull Preference preference) {
        super.updateState(preference);
        if (!FingerprintSettings.isFingerprintHardwareDetected(mContext)) {
            preference.setEnabled(false);
        } else if (!mFingerprintManager.hasEnrolledTemplates(getUserId())) {
            preference.setEnabled(false);
        } else if (getRestrictingAdmin() != null) {
            preference.setEnabled(false);
        } else {
            preference.setEnabled(true);
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    public int getAvailabilityStatus() {
        if (mFingerprintManager != null
                && mFingerprintManager.isHardwareDetected()
                && screenOffUnlockUdfps()
                && !mFingerprintManager.isPowerbuttonFps()) {
            return mFingerprintManager.hasEnrolledTemplates(getUserId())
                    ? AVAILABLE : CONDITIONALLY_UNAVAILABLE;
        } else {
            return UNSUPPORTED_ON_DEVICE;
        }
    }

    private int getUserHandle() {
        return UserHandle.of(getUserId()).getIdentifier();
    }

    /**
     * This feature is not directly searchable.
     */
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider() {};

}
