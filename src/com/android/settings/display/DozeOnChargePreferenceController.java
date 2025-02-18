/*
 * Copyright (C) 2025 Yet Another AOSP Project
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
import android.provider.Settings;
import android.text.TextUtils;

import com.android.settings.R;

public class DozeOnChargePreferenceController extends AmbientDisplayAlwaysOnPreferenceController {

    public DozeOnChargePreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    public boolean isPublicSlice() {
        return TextUtils.equals(getPreferenceKey(), "doze_on_charge");
    }

    @Override
    public boolean isChecked() {
        return getConfig().alwaysOnChargingEnabled(MY_USER);
    }

    @Override
    public boolean setChecked(boolean isChecked) {
        int enabled = isChecked ? ON : OFF;
        Settings.Secure.putInt(
                mContext.getContentResolver(), Settings.Secure.DOZE_ON_CHARGE, enabled);
        return true;
    }

    @Override
    public CharSequence getSummary() {
        return mContext.getText(
                isAodSuppressedByBedtime(mContext) ? R.string.aware_summary_when_bedtime_on
                        : R.string.doze_on_charge_summary);
    }
}
