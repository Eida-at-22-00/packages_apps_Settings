/*
 * Copyright (C) 2021 The Proton AOSP Project
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

package com.android.settings.display;

import android.content.Context;
import android.os.SystemProperties;
import android.provider.Settings;

import com.android.settings.core.TogglePreferenceController;
import com.android.settings.R;

public class TouchRatePreferenceController extends TogglePreferenceController {

    // Settings can only set the debug.* property, so we need to persist it
    // in system settings. Match the stock setting name for backup compatibility.
    private static final String SETTINGS_KEY = Settings.Secure.HIGH_TOUCH_RATE_ENABLED;
    private static final String PROP_NAME = "debug.high_touch_rate";

    public TouchRatePreferenceController(Context context, String preferenceKey) {
        super(context, preferenceKey);
    }

    @Override
    public int getAvailabilityStatus() {
        return mContext.getResources().getBoolean(R.bool.config_supportHighTouchRate)
            ? AVAILABLE
            : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public boolean setChecked(boolean value) {
        Settings.Secure.putInt(mContext.getContentResolver(), SETTINGS_KEY, value ? 1 : 0);
        SystemProperties.set(PROP_NAME, value ? "1" : "0");
        return true;
    }

    @Override
    public boolean isChecked() {
        // debug prop isn't persistent
        return Settings.Secure.getInt(mContext.getContentResolver(), SETTINGS_KEY, 0) == 1;
    }

    @Override
    public int getSliceHighlightMenuRes() {
        return R.string.menu_key_display;
    }
}
