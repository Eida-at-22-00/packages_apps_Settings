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

import android.app.Activity;
import android.app.settings.SettingsEnums;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.os.UserHandle;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.CompoundButton;

import androidx.recyclerview.widget.RecyclerView;

import com.android.internal.accessibility.AccessibilityShortcutController;
import com.android.internal.annotations.VisibleForTesting;
import com.android.settings.R;
import com.android.settings.accessibility.AccessibilityFragmentUtils;
import com.android.settings.accessibility.AccessibilityShortcutPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;
import com.android.settingslib.search.SearchIndexableRaw;
import com.android.settingslib.widget.IllustrationPreference;
import com.android.settingslib.widget.MainSwitchPreference;

import java.util.List;

/**
 * Fragment for One-handed mode settings
 *
 * <p>The child {@link AccessibilityShortcutPreferenceFragment} shows the actual UI for
 * providing basic accessibility shortcut service setup.
 */
@SearchIndexable(forTarget = SearchIndexable.MOBILE)
public class OneHandedSettings extends AccessibilityShortcutPreferenceFragment {

    private static final String TAG = "OneHandedSettings";
    @VisibleForTesting
    static final String ONE_HANDED_SHORTCUT_KEY = "one_handed_shortcuts_preference";
    private static final String ONE_HANDED_ILLUSTRATION_KEY = "one_handed_header";
    protected static final String ONE_HANDED_MAIN_SWITCH_KEY =
            "gesture_one_handed_mode_enabled_main_switch";
    private String mFeatureName;
    private OneHandedSettingsUtils mUtils;

    /**
     * One handed settings no need to set any restriction key for pin protected.
     */
    public OneHandedSettings() {
        super(/* restrictionKey= */ null);
    }

    @Override
    protected void updatePreferenceStates() {
        OneHandedSettingsUtils.setUserId(UserHandle.myUserId());
        super.updatePreferenceStates();

        final IllustrationPreference illustrationPreference =
                getPreferenceScreen().findPreference(ONE_HANDED_ILLUSTRATION_KEY);
        final boolean isSwipeDownNotification =
                OneHandedSettingsUtils.isSwipeDownNotificationEnabled(getContext());
        illustrationPreference.setLottieAnimationResId(
                isSwipeDownNotification ? R.raw.lottie_swipe_for_notifications
                        : R.raw.lottie_one_hand_mode);

        final MainSwitchPreference mainSwitchPreference =
                getPreferenceScreen().findPreference(ONE_HANDED_MAIN_SWITCH_KEY);
        mainSwitchPreference.addOnSwitchChangeListener(CompoundButton::setChecked);
    }

    @Override
    public int getDialogMetricsCategory(int dialogId) {
        final int dialogMetrics = super.getDialogMetricsCategory(dialogId);
        return dialogMetrics == SettingsEnums.ACTION_UNKNOWN ? SettingsEnums.SETTINGS_ONE_HANDED
                : dialogMetrics;
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.SETTINGS_ONE_HANDED;
    }

    @Override
    protected String getShortcutPreferenceKey() {
        return ONE_HANDED_SHORTCUT_KEY;
    }

    @Override
    protected CharSequence getShortcutTitle() {
        return getText(R.string.one_handed_mode_shortcut_title);
    }

    @Override
    protected boolean showGeneralCategory() {
        return true;
    }

    @Override
    public void onStart() {
        super.onStart();
        mUtils = new OneHandedSettingsUtils(this.getContext());
        mUtils.registerToggleAwareObserver(uri -> {
            Activity activity = getActivity();
            if (activity != null) {
                activity.runOnUiThread(() -> updatePreferenceStates());
            }
        });
    }

    @Override
    public void onStop() {
        super.onStop();
        mUtils.unregisterToggleAwareObserver();
    }

    @Override
    protected ComponentName getComponentName() {
        return AccessibilityShortcutController.ONE_HANDED_COMPONENT_NAME;
    }

    @Override
    protected CharSequence getLabelName() {
        return mFeatureName;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.one_handed_settings;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        mFeatureName = getContext().getString(R.string.one_handed_title);
        super.onCreate(savedInstanceState);
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.one_handed_settings) {
                @Override
                protected boolean isPageSearchEnabled(Context context) {
                    return OneHandedSettingsUtils.isSupportOneHandedMode();
                }

                @Override
                public List<SearchIndexableRaw> getRawDataToIndex(Context context,
                        boolean enabled) {
                    final List<SearchIndexableRaw> rawData =
                            super.getRawDataToIndex(context, enabled);
                    if (!com.android.settings.accessibility.Flags.fixA11ySettingsSearch()) {
                        return rawData;
                    }
                    rawData.add(createShortcutPreferenceSearchData(context));
                    return rawData;
                }

                private SearchIndexableRaw createShortcutPreferenceSearchData(Context context) {
                    final SearchIndexableRaw raw = new SearchIndexableRaw(context);
                    raw.key = ONE_HANDED_SHORTCUT_KEY;
                    raw.title = context.getString(R.string.one_handed_mode_shortcut_title);
                    return raw;
                }
            };

    @Override
    public RecyclerView onCreateRecyclerView(LayoutInflater inflater, ViewGroup parent,
            Bundle savedInstanceState) {
        RecyclerView recyclerView =
                super.onCreateRecyclerView(inflater, parent, savedInstanceState);
        return AccessibilityFragmentUtils.addCollectionInfoToAccessibilityDelegate(recyclerView);
    }
}
