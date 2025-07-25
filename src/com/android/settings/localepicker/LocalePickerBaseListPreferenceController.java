/**
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.localepicker;

import static com.android.settings.localepicker.RegionAndNumberingSystemPickerFragment.EXTRA_IS_NUMBERING_SYSTEM;

import android.app.Activity;
import android.app.settings.SettingsEnums;
import android.content.Context;
import android.os.Bundle;
import android.os.LocaleList;
import android.util.ArrayMap;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import com.android.internal.app.LocaleCollectorBase;
import com.android.internal.app.LocaleHelper;
import com.android.internal.app.LocalePicker;
import com.android.internal.app.LocaleStore;
import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;
import com.android.settings.core.SubSettingLauncher;
import com.android.settings.overlay.FeatureFactory;
import com.android.settingslib.core.instrumentation.Instrumentable;
import com.android.settingslib.core.instrumentation.MetricsFeatureProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** A base controller for handling locale controllers. */
public abstract class LocalePickerBaseListPreferenceController extends
        BasePreferenceController implements LocaleListSearchCallback {
    private static final String TAG = "LocalePickerBaseListPreference";
    private static final String PARENT_FRAGMENT_NAME = "localeListEditor";
    private static final String KEY_SUGGESTED = "suggested";
    private static final String KEY_SUPPORTED = "supported";

    private PreferenceCategory mPreferenceCategory;
    private Set<LocaleStore.LocaleInfo> mLocaleList;
    private List<LocaleStore.LocaleInfo> mLocaleOptions;
    private Map<String, Preference> mPreferences;
    private String mPackageName;
    private FragmentManager mFragmentManager;
    private boolean mIsCountryMode;
    @Nullable
    private LocaleStore.LocaleInfo mParentLocale;
    private boolean mIsSuggestedCategory;
    private MetricsFeatureProvider mMetricsFeatureProvider;

    public LocalePickerBaseListPreferenceController(@NonNull Context context,
            @NonNull String preferenceKey) {
        super(context, preferenceKey);
        mPreferences = new ArrayMap<>();
        mMetricsFeatureProvider = FeatureFactory.getFeatureFactory().getMetricsFeatureProvider();
    }

    @Override
    public void displayPreference(@NonNull PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreferenceCategory = screen.findPreference(getPreferenceCategoryKey());
        mIsSuggestedCategory = getPreferenceCategoryKey().contains(KEY_SUGGESTED);
        updatePreferences();
    }

    private void updatePreferences() {
        if (mPreferenceCategory == null) {
            Log.d(TAG, "updatePreferences, mPreferenceCategory is null");
            return;
        }

        List<LocaleStore.LocaleInfo> result;
        mParentLocale = getParentLocale();
        if (mParentLocale != null) {
            mIsCountryMode = true;
            if (!mIsSuggestedCategory) {
                mPreferenceCategory.setTitle(
                        mContext.getString(R.string.all_supported_locales_regions_title));
            }
        }

        result = getSortedLocaleList(mIsSuggestedCategory
                ? getSuggestedLocaleList()
                : getSupportedLocaleList());

        final Map<String, Preference> existingPreferences = mPreferences;
        mPreferences = new ArrayMap<>();
        setupPreference(result, existingPreferences);

        for (Preference pref : existingPreferences.values()) {
            mPreferenceCategory.removePreference(pref);
        }
    }

    @Override
    public void onSearchListChanged(@NonNull List<LocaleStore.LocaleInfo> newList,
            @Nullable CharSequence prefix) {
        mPreferenceCategory.removeAll();
        mPreferences.clear();
        final Map<String, Preference> existingPreferences = mPreferences;

        List<LocaleStore.LocaleInfo> sortedList =
                mIsSuggestedCategory ? getSuggestedLocaleList() : getSupportedLocaleList();
        newList = getSortedSuggestedLocaleFromSearchList(newList, sortedList);
        if (mIsSuggestedCategory && getParentLocale() != null) {
            newList = getSortedSuggestedRegionFromSearchList(prefix, newList, sortedList);
        }
        setupPreference(newList, existingPreferences);
    }

    private List<LocaleStore.LocaleInfo> getSortedSuggestedLocaleFromSearchList(
            List<LocaleStore.LocaleInfo> listOptions,
            List<LocaleStore.LocaleInfo> listSuggested) {
        List<LocaleStore.LocaleInfo> searchItem = new ArrayList<>();
        for (LocaleStore.LocaleInfo option : listOptions) {
            for (LocaleStore.LocaleInfo suggested : listSuggested) {
                if (suggested.toString().contains(option.toString())) {
                    searchItem.add(suggested);
                }
            }
        }
        searchItem = getSortedLocaleList(searchItem);
        return searchItem;
    }

    private List<LocaleStore.LocaleInfo> getSortedSuggestedRegionFromSearchList(
            @Nullable CharSequence prefix,
            List<LocaleStore.LocaleInfo> listOptions,
            List<LocaleStore.LocaleInfo> listSuggested) {
        List<LocaleStore.LocaleInfo> searchItem = new ArrayList<>();
        if (prefix == null || prefix.isEmpty()) {
            return getSortedLocaleList(listSuggested);
        }

        for (LocaleStore.LocaleInfo option : listOptions) {
            if (listSuggested.contains(option)) {
                searchItem.add(option);
            }
        }
        return getSortedLocaleList(searchItem);
    }

    @VisibleForTesting
    void setupPreference(List<LocaleStore.LocaleInfo> localeInfoList,
            Map<String, Preference> existingPreferences) {
        Log.d(TAG, "setupPreference: isNumberingMode = " + isNumberingMode());
        if (isNumberingMode() && getPreferenceCategoryKey().contains(KEY_SUPPORTED)) {
            mPreferenceCategory.setTitle(
                    mContext.getString(R.string.all_supported_numbering_system_title));
        }

        localeInfoList.stream().forEach(locale ->
        {
            Preference pref = existingPreferences.remove(locale.getId());
            if (pref == null) {
                pref = new Preference(mContext);
                mPreferenceCategory.addPreference(pref);
            }
            String localeName =
                    mIsCountryMode ? locale.getFullCountryNameNative() : locale.getFullNameNative();
            pref.setTitle(localeName);
            pref.setKey(locale.toString());
            pref.setOnPreferenceClickListener(clickedPref -> {
                // TODO: b/390347399 - Should pop up a dialog when changes the region.
                switchFragment(locale);
                ((Activity) mContext).finish();
                return true;
            });
            mPreferences.put(locale.getId(), pref);
        });
        mPreferenceCategory.setVisible(mPreferenceCategory.getPreferenceCount() > 0);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    protected abstract String getPreferenceCategoryKey();

    protected abstract LocaleCollectorBase getLocaleCollectorController(Context context);

    @Nullable
    protected abstract LocaleStore.LocaleInfo getParentLocale();

    protected abstract boolean isNumberingMode();

    @Nullable
    protected abstract LocaleList getExplicitLocaleList();

    protected String getPackageName() {
        return mPackageName;
    }

    protected List<LocaleStore.LocaleInfo> getSuggestedLocaleList() {
        setupLocaleList();
        if (mLocaleList != null && !mLocaleList.isEmpty()) {
            mLocaleOptions.addAll(mLocaleList.stream()
                    .filter(localeInfo -> localeInfo.isSuggested())
                    .collect(Collectors.toList()));
        } else {
            Log.d(TAG, "Can not get suggested locales because the locale list is null or empty.");
        }

        return mLocaleOptions;
    }

    protected List<LocaleStore.LocaleInfo> getSupportedLocaleList() {
        setupLocaleList();
        if (mLocaleList != null && !mLocaleList.isEmpty()) {
            mLocaleOptions.addAll(mLocaleList.stream()
                    .filter(localeInfo -> !localeInfo.isSuggested())
                    .collect(Collectors.toList()));
        } else {
            Log.d(TAG, "Can not get supported locales because the locale list is null or empty.");
        }
        return mLocaleOptions;
    }

    private void setupLocaleList() {
        mLocaleList = getLocaleCollectorController(mContext).getSupportedLocaleList(
                mParentLocale, false, mIsCountryMode);
        mLocaleOptions = new ArrayList<>(mLocaleList.size());
    }

    private List<LocaleStore.LocaleInfo> getSortedLocaleList(
            List<LocaleStore.LocaleInfo> localeInfos) {
        final Locale sortingLocale = Locale.getDefault();
        final LocaleHelper.LocaleInfoComparator comp =
                new LocaleHelper.LocaleInfoComparator(sortingLocale, mIsCountryMode);
        Collections.sort(localeInfos, comp);
        return localeInfos;
    }

    @VisibleForTesting
    void switchFragment(LocaleStore.LocaleInfo localeInfo) {
        boolean shouldShowLocaleEditor = shouldShowLocaleEditor(localeInfo);
        if (shouldShowLocaleEditor) {
            List<LocaleStore.LocaleInfo> feedItemList = getUserLocaleList();
            for (LocaleStore.LocaleInfo locale : mLocaleList) {
                feedItemList.add(locale);
            }
            LocaleList localeList = new LocaleList(feedItemList.stream()
                    .map(LocaleStore.LocaleInfo::getLocale)
                    .toArray(Locale[]::new));

            LocaleList.setDefault(localeList);
            LocalePicker.updateLocales(localeList);
            mMetricsFeatureProvider.action(mContext, SettingsEnums.ACTION_ADD_LANGUAGE);
            returnToParentFrame();
        } else {
            final Bundle extra = new Bundle();
            extra.putSerializable(RegionAndNumberingSystemPickerFragment.EXTRA_TARGET_LOCALE,
                    localeInfo);
            extra.putBoolean(EXTRA_IS_NUMBERING_SYSTEM, localeInfo.hasNumberingSystems());
            new SubSettingLauncher(mContext)
                    .setDestination(RegionAndNumberingSystemPickerFragment.class.getCanonicalName())
                    .setSourceMetricsCategory(Instrumentable.METRICS_CATEGORY_UNKNOWN)
                    .setArguments(extra)
                    .launch();
        }
    }

    public void setFragmentManager(@NonNull FragmentManager fragmentManager) {
        mFragmentManager = fragmentManager;
    }

    private void returnToParentFrame() {
        if (mFragmentManager != null) {
            mFragmentManager.popBackStack(PARENT_FRAGMENT_NAME,
                    FragmentManager.POP_BACK_STACK_INCLUSIVE);
        }
    }

    @VisibleForTesting
    boolean shouldShowLocaleEditor(LocaleStore.LocaleInfo localeInfo) {
        boolean isSystemLocale = localeInfo.isSystemLocale();
        boolean isRegionLocale = localeInfo.getParent() != null;
        boolean mayHaveDifferentNumberingSystem = localeInfo.hasNumberingSystems();
        mLocaleList = getLocaleCollectorController(mContext).getSupportedLocaleList(localeInfo,
                false, localeInfo != null);
        Log.d(TAG,
                "shouldShowLocaleEditor: isSystemLocale = " + isSystemLocale + ", isRegionLocale = "
                        + isRegionLocale + ", mayHaveDifferentNumberingSystem = "
                        + mayHaveDifferentNumberingSystem + ", isSuggested = "
                        + localeInfo.isSuggested() + ", isNumberingMode = " + isNumberingMode());

        return mLocaleList.size() == 1 || isSystemLocale || localeInfo.isSuggested()
                || (isRegionLocale && !mayHaveDifferentNumberingSystem)
                || isNumberingMode();
    }

    private List<LocaleStore.LocaleInfo> getUserLocaleList() {
        final List<LocaleStore.LocaleInfo> result = new ArrayList<>();
        final LocaleList localeList = LocalePicker.getLocales();
        for (int i = 0; i < localeList.size(); i++) {
            result.add(LocaleStore.getLocaleInfo(localeList.get(i)));
        }
        return result;
    }
}
