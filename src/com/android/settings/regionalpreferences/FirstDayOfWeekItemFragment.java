/**
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.settings.regionalpreferences;

import android.app.settings.SettingsEnums;
import android.content.Context;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionInfoCompat;
import androidx.preference.PreferenceRecyclerViewAccessibilityDelegate;
import androidx.recyclerview.widget.RecyclerView;

import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.overlay.FeatureFactory;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settingslib.core.instrumentation.MetricsFeatureProvider;
import com.android.settingslib.search.SearchIndexable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Main fragment to display first day of week. */
@SearchIndexable(forTarget = SearchIndexable.ALL & ~SearchIndexable.ARC)
public class FirstDayOfWeekItemFragment extends DashboardFragment {

    private static final String LOG_TAG = "FirstDayOfWeekItemFragment";
    private static final String KEY_PREFERENCE_CATEGORY_FIRST_DAY_OF_WEEK_ITEM =
            "first_day_of_week_item_category";

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        MetricsFeatureProvider metricsFeatureProvider =
                FeatureFactory.getFeatureFactory().getMetricsFeatureProvider();
        String action = getIntent() != null ? getIntent().getAction() : "";
        if (Settings.ACTION_FIRST_DAY_OF_WEEK_SETTINGS.equals(action)) {
            metricsFeatureProvider.action(
                    context, SettingsEnums.ACTION_OPEN_FIRST_DAY_OF_WEEK_OUTSIDE_SETTINGS);
        }
    }

    @NonNull
    @Override
    public RecyclerView onCreateRecyclerView(
            @NonNull LayoutInflater inflater, @NonNull ViewGroup parent,
            @Nullable Bundle savedInstanceState) {

        // Talkback shouldn't announce in list numbers
        final RecyclerView recyclerView =
                super.onCreateRecyclerView(inflater, parent, savedInstanceState);
        recyclerView.setAccessibilityDelegateCompat(
            new PreferenceRecyclerViewAccessibilityDelegate(recyclerView) {
                    @Override
                    public void onInitializeAccessibilityNodeInfo(@NonNull View host,
                            @NonNull AccessibilityNodeInfoCompat info) {
                        super.onInitializeAccessibilityNodeInfo(host, info);
                        int availableCount = (int) getPreferenceControllers()
                                .stream()
                                .flatMap(Collection::stream)
                                .filter(AbstractPreferenceController::isAvailable)
                                .count();
                        info.setCollectionInfo(
                                CollectionInfoCompat.obtain(
                                        /*rowCount=*/availableCount,
                                        /*columnCount=*/1,
                                        /*hierarchical=*/false,
                                        CollectionInfoCompat.SELECTION_MODE_SINGLE)
                        );
                    }
            });
        return recyclerView;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.regional_preferences_first_day_of_week;
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.FIRST_DAY_OF_WEEK_PREFERENCE;
    }

    @Override
    protected String getLogTag() {
        return LOG_TAG;
    }

    @Override
    protected List<AbstractPreferenceController> createPreferenceControllers(Context context) {
        final List<AbstractPreferenceController> controllers = new ArrayList<>();
        controllers.add(new FirstDayOfWeekItemCategoryController(context,
                KEY_PREFERENCE_CATEGORY_FIRST_DAY_OF_WEEK_ITEM));
        return controllers;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.regional_preferences_first_day_of_week);
}
