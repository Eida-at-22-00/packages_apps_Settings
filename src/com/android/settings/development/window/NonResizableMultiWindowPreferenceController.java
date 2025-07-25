/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.settings.development.window;

import static android.provider.Settings.Global.DEVELOPMENT_ENABLE_NON_RESIZABLE_MULTI_WINDOW;

import android.content.Context;
import android.provider.Settings;

import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.TwoStatePreference;

import com.android.settings.core.PreferenceControllerMixin;
import com.android.settingslib.development.DeveloperOptionsPreferenceController;

/**
 * Preference Controller for whether to allow launching non-resizable apps in multi window,
 * such as freeform and splitscreen.
 */
public class NonResizableMultiWindowPreferenceController
        extends DeveloperOptionsPreferenceController
        implements Preference.OnPreferenceChangeListener, PreferenceControllerMixin {

    private static final String ENABLE_NON_RESIZABLE_MULTI_WINDOW_KEY =
            "enable_non_resizable_multi_window";

    @VisibleForTesting
    static final int SETTING_VALUE_OFF = 0;
    @VisibleForTesting
    static final int SETTING_VALUE_ON = 1;

    public NonResizableMultiWindowPreferenceController(Context context) {
        super(context);
    }

    @Override
    public String getPreferenceKey() {
        return ENABLE_NON_RESIZABLE_MULTI_WINDOW_KEY;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final boolean isEnabled = (Boolean) newValue;
        Settings.Global.putInt(mContext.getContentResolver(),
                DEVELOPMENT_ENABLE_NON_RESIZABLE_MULTI_WINDOW,
                isEnabled ? SETTING_VALUE_ON : SETTING_VALUE_OFF);
        return true;
    }

    @Override
    public void updateState(Preference preference) {
        final int mode = Settings.Global.getInt(mContext.getContentResolver(),
                DEVELOPMENT_ENABLE_NON_RESIZABLE_MULTI_WINDOW, SETTING_VALUE_OFF);
        ((TwoStatePreference) mPreference).setChecked(mode != SETTING_VALUE_OFF);
    }

    @Override
    protected void onDeveloperOptionsSwitchDisabled() {
        super.onDeveloperOptionsSwitchDisabled();
        Settings.Global.putInt(mContext.getContentResolver(),
                DEVELOPMENT_ENABLE_NON_RESIZABLE_MULTI_WINDOW, SETTING_VALUE_OFF);
        ((TwoStatePreference) mPreference).setChecked(false);
    }
}
