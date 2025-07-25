/*
 * Copyright (C) 2018 The Android Open Source Project
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
import android.hardware.display.ColorDisplayManager;
import android.text.TextUtils;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.core.SliderPreferenceController;
import com.android.settingslib.widget.SliderPreference;

public class NightDisplayIntensityPreferenceController extends SliderPreferenceController {

    private final ColorDisplayManager mColorDisplayManager;

    public NightDisplayIntensityPreferenceController(Context context, String key) {
        super(context, key);
        mColorDisplayManager = context.getSystemService(ColorDisplayManager.class);
    }

    @Override
    public int getAvailabilityStatus() {
        if (!ColorDisplayManager.isNightDisplayAvailable(mContext)) {
            return UNSUPPORTED_ON_DEVICE;
        } else if (!mColorDisplayManager.isNightDisplayActivated()) {
            return DISABLED_DEPENDENT_SETTING;
        }
        return AVAILABLE;
    }

    @Override
    public boolean isSliceable() {
        return TextUtils.equals(getPreferenceKey(), "night_display_temperature");
    }

    @Override
    public boolean isPublicSlice() {
        return true;
    }

    @Override
    public int getSliceHighlightMenuRes() {
        return R.string.menu_key_display;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        SliderPreference preference = screen.findPreference(getPreferenceKey());
        preference.setUpdatesContinuously(true);
        preference.setMax(getMax());
        preference.setMin(getMin());
        // TODO(b/394828723) add haptic feedback
    }

    @Override
    public final void updateState(Preference preference) {
        super.updateState(preference);
        preference.setEnabled(mColorDisplayManager.isNightDisplayActivated());
    }

    @Override
    public int getSliderPosition() {
        return convertTemperature(mColorDisplayManager.getNightDisplayColorTemperature());
    }

    @Override
    public boolean setSliderPosition(int position) {
        return mColorDisplayManager.setNightDisplayColorTemperature(convertTemperature(position));
    }

    @Override
    public int getMax() {
        return convertTemperature(ColorDisplayManager.getMinimumColorTemperature(mContext));
    }

    @Override
    public int getMin() {
        // The min should always be 0 in this case.
        return 0;
    }

    /**
     * Inverts and range-adjusts a raw value from the SeekBar (i.e. [0, maxTemp-minTemp]), or
     * converts an inverted and range-adjusted value to the raw SeekBar value, depending on the
     * adjustment status of the input.
     */
    private int convertTemperature(int temperature) {
        return ColorDisplayManager.getMaximumColorTemperature(mContext) - temperature;
    }
}