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

package com.android.settings.inputmethod;

import android.content.Context;
import android.hardware.input.InputManager;
import android.util.FeatureFlagUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;

import com.android.settings.core.BasePreferenceController;
import com.android.settings.core.PreferenceControllerMixin;
import com.android.settings.keyboard.Flags;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnStart;
import com.android.settingslib.core.lifecycle.events.OnStop;

public class TouchpadAndMouseSettingsController extends BasePreferenceController
        implements PreferenceControllerMixin, LifecycleObserver, OnStart, OnStop,
        InputManager.InputDeviceListener {

    private final InputManager mIm;

    @Nullable
    private Preference mPreference;

    public TouchpadAndMouseSettingsController(@NonNull Context context, @NonNull String key) {
        super(context, key);
        mIm = context.getSystemService(InputManager.class);
    }

    @Override
    public void onInputDeviceAdded(int deviceId) {
        updateEntry();
    }

    @Override
    public void onInputDeviceRemoved(int deviceId) {
        updateEntry();
    }

    @Override
    public void onInputDeviceChanged(int deviceId) {
        updateEntry();
    }

    @Override
    public void onStart() {
        mIm.registerInputDeviceListener(this, null);
    }

    @Override
    public void onStop() {
        mIm.unregisterInputDeviceListener(this);
    }

    @Override
    public void updateState(Preference preference) {
        mPreference = preference;
        updateEntry();
    }

    private void updateEntry() {
        if (mPreference == null) {
            return;
        }
        mPreference.setVisible(isAvailable());
        mPreference.setTitle(InputPeripheralsSettingsUtils.getTouchpadAndMouseTitleTitleResId());
    }

    @Override
    public int getAvailabilityStatus() {
        boolean isNewPageFlagDisabled = !Flags.keyboardAndTouchpadA11yNewPageEnabled();
        boolean isFeatureOn = FeatureFlagUtils
                .isEnabled(mContext, FeatureFlagUtils.SETTINGS_NEW_KEYBOARD_TRACKPAD);
        boolean isTouchpad = InputPeripheralsSettingsUtils.isTouchpad();
        boolean isPointerCustomizationEnabled =
                android.view.flags.Flags.enableVectorCursorA11ySettings();
        boolean isMouse = InputPeripheralsSettingsUtils.isMouse();
        return ((isFeatureOn && isTouchpad) || (isPointerCustomizationEnabled && isMouse))
                && isNewPageFlagDisabled ? AVAILABLE
                : CONDITIONALLY_UNAVAILABLE;
    }
}
