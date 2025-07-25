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

import android.app.settings.SettingsEnums;
import android.content.Context;
import android.util.FeatureFlagUtils;

import com.android.settings.R;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

@SearchIndexable
public class ModifierKeysSettings extends InputDeviceDashboardFragment {

    private static final String TAG = "ModifierKeysSettings";

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        use(ModifierKeysPreferenceController.class).setFragment(this /*parent*/);
        use(ModifierKeysRestorePreferenceController.class).setFragment(this /*parent*/);
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.SETTINGS_KEYBOARDS_MODIFIER_KEYS;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.modifier_keys_settings;
    }

    @Override
    protected boolean needToFinishEarly() {
        return isHardKeyboardDetached();
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.modifier_keys_settings) {
                @Override
                protected boolean isPageSearchEnabled(Context context) {
                    return FeatureFlagUtils
                            .isEnabled(
                                    context, FeatureFlagUtils.SETTINGS_NEW_KEYBOARD_MODIFIER_KEY)
                            && !PhysicalKeyboardFragment.getHardKeyboards(context).isEmpty();
                }
            };
}
