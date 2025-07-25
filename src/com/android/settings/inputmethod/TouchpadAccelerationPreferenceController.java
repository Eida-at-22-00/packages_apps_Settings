/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.settings.inputmethod;

import android.content.Context;
import android.hardware.input.InputSettings;

import androidx.annotation.NonNull;

import com.android.settings.R;
import com.android.settings.core.TogglePreferenceController;

public class TouchpadAccelerationPreferenceController extends TogglePreferenceController {

    public TouchpadAccelerationPreferenceController(@NonNull Context context, @NonNull String key) {
        super(context, key);
    }

    @Override
    public boolean isChecked() {
        return InputSettings.isTouchpadAccelerationEnabled(mContext);
    }

    @Override
    public boolean setChecked(boolean isChecked) {
        InputSettings.setTouchpadAccelerationEnabled(mContext, isChecked);
        return true;
    }

    @Override
    public int getAvailabilityStatus() {
        if (!InputSettings.isPointerAccelerationFeatureFlagEnabled()) {
            return UNSUPPORTED_ON_DEVICE;
        }
        return InputPeripheralsSettingsUtils.isTouchpad() ? AVAILABLE : CONDITIONALLY_UNAVAILABLE;
    }

    @Override
    public int getSliceHighlightMenuRes() {
        return R.string.menu_key_system;
    }
}
