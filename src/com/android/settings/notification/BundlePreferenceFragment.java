/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.settings.notification;

import static android.service.notification.Adjustment.KEY_TYPE;

import android.app.Activity;
import android.app.Application;
import android.app.Flags;
import android.app.settings.SettingsEnums;
import android.content.Context;

import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.applications.ApplicationsState;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settingslib.search.SearchIndexable;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment for bundled notifications.
 */
@SearchIndexable(forTarget = SearchIndexable.ALL & ~SearchIndexable.ARC)
public class BundlePreferenceFragment extends DashboardFragment {

    private static final String BUNDLE_CATEGORY_KEY = "enabled_settings";

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.BUNDLED_NOTIFICATIONS;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.bundle_notifications_settings;
    }

    @Override
    protected List<AbstractPreferenceController> createPreferenceControllers(Context context) {
        final List<AbstractPreferenceController> controllers = new ArrayList<>();
        controllers.add(new BundleCombinedPreferenceController(context, BUNDLE_CATEGORY_KEY,
                new NotificationBackend()));
        return controllers;
    }

    @Override
    protected String getLogTag() {
        return "BundlePreferenceFragment";
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (use(AdjustmentExcludedAppsPreferenceController.class) != null) {
            final Activity activity = getActivity();
            Application app = null;
            ApplicationsState appState = null;
            if (activity != null) {
                app = activity.getApplication();
            } else {
                app = null;
            }
            if (app != null) {
                appState = ApplicationsState.getInstance(app);
            }
            use(AdjustmentExcludedAppsPreferenceController.class).onAttach(
                    appState, this, new NotificationBackend(), KEY_TYPE);
        }
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.bundle_notifications_settings) {

                @Override
                protected boolean isPageSearchEnabled(Context context) {
                    return Flags.notificationClassificationUi();
                }
            };
}
