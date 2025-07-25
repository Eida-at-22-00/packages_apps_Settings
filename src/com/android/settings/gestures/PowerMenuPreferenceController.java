/*
 * Copyright (C) 2020 The Android Open Source Project
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
import static android.provider.Settings.Secure.DOUBLE_TAP_POWER_BUTTON_GESTURE;
import static android.provider.Settings.System.TORCH_POWER_BUTTON_GESTURE;

import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;

import java.lang.StringBuilder;
import java.util.ArrayList;

public class PowerMenuPreferenceController extends BasePreferenceController {

    public PowerMenuPreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    public CharSequence getSummary() {
        final ContentResolver resolver = mContext.getContentResolver();
        // default value for summary if nothing is enabled
        StringBuilder summary = new StringBuilder(
                mContext.getString(R.string.power_menu_setting_summary));
        ArrayList<String> enabledStrings = new ArrayList<>();
        int torch = Settings.System.getInt(resolver, TORCH_POWER_BUTTON_GESTURE, 0);
        if (torch != 0)
            enabledStrings.add(mContext.getString(R.string.torch_power_button_gesture_title));
        int value = Settings.Secure.getInt(resolver, CAMERA_DOUBLE_TAP_POWER_GESTURE_DISABLED, 0);
        if (value == 0 && torch != 1) {
            int gesture = Settings.Secure.getInt(resolver, DOUBLE_TAP_POWER_BUTTON_GESTURE, 0);
            enabledStrings.add(mContext.getString(
                    gesture == 0 ? R.string.double_tap_power_for_camera_title
                                 : R.string.double_tap_power_wallet_action_summary));
        }
        boolean enabled = PowerMenuSettingsUtils.isLongPressPowerForAssistantEnabled(mContext);
        if (enabled)
            enabledStrings.add(mContext.getString(R.string.power_menu_summary_long_press_for_assistant));
        if (!enabledStrings.isEmpty()) {
            summary = new StringBuilder(enabledStrings.remove(0));
            for (String str : enabledStrings) summary.append(", ").append(str);
        }
        return summary.toString();
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }
}
