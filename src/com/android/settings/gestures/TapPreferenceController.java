/*
 * Copyright (C) 2023 Yet Another AOSP Project
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

import android.content.Context;
import android.hardware.display.AmbientDisplayConfiguration;
import android.os.SystemProperties;
import android.os.Vibrator;
import android.provider.Settings;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.core.PreferenceControllerMixin;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settingslib.widget.MainSwitchPreference;

import com.yasp.settings.preferences.SecureSettingSwitchPreference;

public class TapPreferenceController extends AbstractPreferenceController
        implements PreferenceControllerMixin, OnCheckedChangeListener {

    private static final String KEY = "gesture_tap";
    private static final String AMBIENT_KEY = "doze_tap_gesture_ambient";
    private static final String AOD_KEY = "doze_tap_gesture_allow_ambient";
    private static final String VIB_KEY = "doze_tap_gesture_vibrate";

    private final Context mContext;
    private AmbientDisplayConfiguration mAmbientConfig;
    private MainSwitchPreference mSwitch;
    private SecureSettingSwitchPreference mAmbientPref;
    private SecureSettingSwitchPreference mAODPref;
    private SecureSettingSwitchPreference mVibPref;

    private boolean mIsVibAvailable;

    public TapPreferenceController(Context context) {
        super(context);
        mContext = context;
    }

    @Override
    public String getPreferenceKey() {
        return KEY;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mAmbientPref = screen.findPreference(AMBIENT_KEY);
        mAODPref = screen.findPreference(AOD_KEY);
        mSwitch = screen.findPreference(getPreferenceKey());
        mSwitch.setOnPreferenceClickListener(preference -> {
            final boolean enabled = Settings.Secure.getInt(mContext.getContentResolver(),
                    Settings.Secure.DOZE_TAP_SCREEN_GESTURE, 0) == 1;
            Settings.Secure.putInt(mContext.getContentResolver(),
                    Settings.Secure.DOZE_TAP_SCREEN_GESTURE,
                    enabled ? 0 : 1);
            updateEnablement(!enabled);
            return true;
        });
        mSwitch.addOnSwitchChangeListener(this);
        updateState(mSwitch);

        mVibPref = screen.findPreference(VIB_KEY);
        final Vibrator vibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
        mIsVibAvailable = vibrator != null && vibrator.hasVibrator();
        if (!mIsVibAvailable) mVibPref.setVisible(false);
    }

    public void setChecked(boolean isChecked) {
        if (mSwitch != null) {
            mSwitch.setChecked(isChecked);
        }
        updateEnablement(isChecked);
    }

    @Override
    public void updateState(Preference preference) {
        final boolean enabled = Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.DOZE_TAP_SCREEN_GESTURE, 0) == 1;
        setChecked(enabled);
    }

    @Override
    public boolean isAvailable() {
        return getAmbientConfig().tapSensorAvailable();
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.DOZE_TAP_SCREEN_GESTURE, isChecked ? 1 : 0);
        SystemProperties.set("persist.sys.tap_gesture", isChecked ? "1" : "0");
        updateEnablement(isChecked);
    }

    private void updateEnablement(boolean enabled) {
        if (mAmbientPref != null) mAmbientPref.setEnabled(enabled);
        if (mAODPref != null) mAODPref.setEnabled(enabled);
        if (mVibPref != null && mIsVibAvailable) mVibPref.setEnabled(enabled);
    }

    private AmbientDisplayConfiguration getAmbientConfig() {
        if (mAmbientConfig == null) {
            mAmbientConfig = new AmbientDisplayConfiguration(mContext);
        }

        return mAmbientConfig;
    }
}
