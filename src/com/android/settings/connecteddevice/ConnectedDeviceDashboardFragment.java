/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.settings.connecteddevice;

import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.android.settings.R;
import com.android.settings.SettingsActivity;
import com.android.settings.Utils;
import com.android.settings.connecteddevice.audiosharing.AudioSharingDevicePreferenceController;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.overlay.FeatureFactory;
import com.android.settings.overlay.SurveyFeatureProvider;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.slices.SlicePreferenceController;
import com.android.settingslib.bluetooth.BluetoothUtils;
import com.android.settingslib.bluetooth.HearingAidStatsLogUtils;

import com.android.settingslib.core.instrumentation.MetricsFeatureProvider;
import com.android.settingslib.search.SearchIndexable;

@SearchIndexable(forTarget = SearchIndexable.ALL & ~SearchIndexable.ARC)
public class ConnectedDeviceDashboardFragment extends DashboardFragment {

    private static final String TAG = "ConnectedDeviceFrag";
    private static final String SETTINGS_SEARCH_ACTION =
            "com.android.settings.SEARCH_RESULT_TRAMPOLINE";
    @VisibleForTesting static final String KEY_CONNECTED_DEVICES = "connected_device_list";
    @VisibleForTesting static final String KEY_AVAILABLE_DEVICES = "available_device_list";

    private static final String ENTRYPOINT_SYSUI = "bt_settings_entrypoint_sysui";
    private static final String ENTRYPOINT_SETTINGS = "bt_settings_entrypoint_settings_click";
    private static final String ENTRYPOINT_SETTINGS_SEARCH =
            "bt_settings_entrypoint_settings_search";
    private static final String ENTRYPOINT_OTHER = "bt_settings_entrypoint_other";

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.SETTINGS_CONNECTED_DEVICE_CATEGORY;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    public int getHelpResource() {
        return R.string.help_url_connected_devices;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.connected_devices;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        String callingAppPackageName =
                ((SettingsActivity) getActivity()).getInitialCallingPackage();
        Intent intent = getIntent();
        String action = intent != null ? intent.getAction() : "";

        Log.d(
                TAG,
                "onAttach() calling package name is : "
                        + callingAppPackageName
                        + ", action : "
                        + action);

        if (BluetoothUtils.isAudioSharingUIAvailable(context)) {
            use(AudioSharingDevicePreferenceController.class).init(this);
        }
        use(AvailableMediaDeviceGroupController.class).init(this);
        use(ConnectedDeviceGroupController.class).init(this);
        use(PreviouslyConnectedDevicePreferenceController.class).init(this);
        use(SlicePreferenceController.class)
                .setSliceUri(Uri.parse(getString(R.string.config_nearby_devices_slice_uri)));
        use(DiscoverableFooterPreferenceController.class)
                .setAlwaysDiscoverable(isAlwaysDiscoverable(callingAppPackageName, action));

        // Show hearing devices survey if user is categorized as one of interested category
        final String category = HearingAidStatsLogUtils.getUserCategory(context);
        if (category != null && !category.isEmpty()) {
            SurveyFeatureProvider provider =
                    FeatureFactory.getFeatureFactory().getSurveyFeatureProvider(context);
            if (provider != null) {
                provider.sendActivityIfAvailable(category);
            }
        }

        logPageEntrypoint(context, callingAppPackageName, intent);
    }

    @VisibleForTesting
    boolean isAlwaysDiscoverable(String callingAppPackageName, String action) {
        return TextUtils.equals(SETTINGS_SEARCH_ACTION, action)
                ? false
                : TextUtils.equals(Utils.SETTINGS_PACKAGE_NAME, callingAppPackageName)
                        || TextUtils.equals(Utils.SYSTEMUI_PACKAGE_NAME, callingAppPackageName);
    }

    private void logPageEntrypoint(Context context, String callingAppPackageName, Intent intent) {
        String action = intent != null ? intent.getAction() : "";
        if (TextUtils.equals(Utils.SYSTEMUI_PACKAGE_NAME, callingAppPackageName)) {
            mMetricsFeatureProvider.action(
                    context, SettingsEnums.SETTINGS_CONNECTED_DEVICES_ENTRYPOINT, ENTRYPOINT_SYSUI);
        } else if (TextUtils.equals(Utils.SETTINGS_PACKAGE_NAME, callingAppPackageName)
                && TextUtils.equals(Intent.ACTION_MAIN, action)) {
            String sourceCategory =
                    intent != null
                            ? Integer.toString(
                                    getIntent()
                                            .getIntExtra(
                                                    MetricsFeatureProvider
                                                            .EXTRA_SOURCE_METRICS_CATEGORY,
                                                    SettingsEnums.PAGE_UNKNOWN))
                            : "";
            mMetricsFeatureProvider.action(
                    context,
                    SettingsEnums.SETTINGS_CONNECTED_DEVICES_ENTRYPOINT,
                    ENTRYPOINT_SETTINGS + "_" + sourceCategory);
        } else if (TextUtils.equals(Utils.SETTINGS_PACKAGE_NAME, callingAppPackageName)
                && TextUtils.equals(SETTINGS_SEARCH_ACTION, action)) {
            mMetricsFeatureProvider.action(
                    context,
                    SettingsEnums.SETTINGS_CONNECTED_DEVICES_ENTRYPOINT,
                    ENTRYPOINT_SETTINGS_SEARCH);

        } else {
            mMetricsFeatureProvider.action(
                    context, SettingsEnums.SETTINGS_CONNECTED_DEVICES_ENTRYPOINT, ENTRYPOINT_OTHER);
        }
    }

    /** For Search. */
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.connected_devices);
}
