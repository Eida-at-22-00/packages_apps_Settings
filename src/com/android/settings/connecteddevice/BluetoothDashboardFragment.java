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
package com.android.settings.connecteddevice;

import android.app.Activity;
import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.settings.R;
import com.android.settings.SettingsActivity;
import com.android.settings.Utils;
import com.android.settings.bluetooth.AlwaysDiscoverable;
import com.android.settings.bluetooth.BluetoothDeviceRenamePreferenceController;
import com.android.settings.bluetooth.BluetoothSwitchPreferenceController;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.password.PasswordUtils;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.widget.MainSwitchBarController;
import com.android.settings.widget.SettingsMainSwitchBar;
import com.android.settingslib.core.lifecycle.Lifecycle;
import com.android.settingslib.search.SearchIndexable;
import com.android.settingslib.widget.FooterPreference;

/**
 * Dedicated screen for allowing the user to toggle bluetooth which displays relevant information to
 * the user based on related settings such as bluetooth scanning.
 */
@SearchIndexable(forTarget = SearchIndexable.ALL & ~SearchIndexable.ARC)
public class BluetoothDashboardFragment extends DashboardFragment {

    private static final String TAG = "BluetoothDashboardFrag";
    private static final String SLICE_ACTION = "com.android.settings.SEARCH_RESULT_TRAMPOLINE";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private FooterPreference mFooterPreference;
    private SettingsMainSwitchBar mSwitchBar;
    private BluetoothSwitchPreferenceController mController;

    private @Nullable AlwaysDiscoverable mAlwaysDiscoverable;

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.BLUETOOTH_FRAGMENT;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    public int getHelpResource() {
        return R.string.help_uri_bluetooth_screen;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.bluetooth_screen;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        if (!isCatalystEnabled()) {
            mFooterPreference = findPreference(BluetoothFooterPreference.KEY);
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        use(BluetoothDeviceRenamePreferenceController.class).setFragment(this);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (isCatalystEnabled()) {
            return;
        }
        String callingAppPackageName = PasswordUtils.getCallingAppPackageName(
                getActivity().getActivityToken());
        String action = getIntent() != null ? getIntent().getAction() : "";
        if (DEBUG) {
            Log.d(TAG, "onActivityCreated() calling package name is : " + callingAppPackageName
                    + ", action : " + action);
        }

        SettingsActivity activity = (SettingsActivity) getActivity();
        mSwitchBar = activity.getSwitchBar();
        mSwitchBar.setTitle(getContext().getString(R.string.bluetooth_main_switch_title));
        mSwitchBar.getRootView().setAccessibilityDelegate(new MainSwitchAccessibilityDelegate());
        mController = new BluetoothSwitchPreferenceController(activity,
                new MainSwitchBarController(mSwitchBar), mFooterPreference);
        mController.setAlwaysDiscoverable(isAlwaysDiscoverable(callingAppPackageName, action));
        Lifecycle lifecycle = getSettingsLifecycle();
        if (lifecycle != null) {
            lifecycle.addObserver(mController);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (isCatalystEnabled()) {
            Activity activity = requireActivity();
            String callingAppPackageName = PasswordUtils.getCallingAppPackageName(
                    activity.getActivityToken());
            Intent intent = activity.getIntent();
            String action = intent != null ? intent.getAction() : "";
            if (DEBUG) {
                Log.d(TAG, "onActivityCreated() calling package name is : " + callingAppPackageName
                        + ", action : " + action);
            }
            if (isAlwaysDiscoverable(callingAppPackageName, action)) {
                mAlwaysDiscoverable = new AlwaysDiscoverable(activity);
                mAlwaysDiscoverable.start();
            }
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mAlwaysDiscoverable != null) {
            mAlwaysDiscoverable.stop();
            mAlwaysDiscoverable = null;
        }
    }

    @VisibleForTesting
    boolean isAlwaysDiscoverable(@Nullable String callingAppPackageName, @Nullable String action) {
        return TextUtils.equals(SLICE_ACTION, action) ? false
            : TextUtils.equals(Utils.SETTINGS_PACKAGE_NAME, callingAppPackageName)
                || TextUtils.equals(Utils.SYSTEMUI_PACKAGE_NAME, callingAppPackageName);
    }

    /**
     * For Search.
     */
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.bluetooth_screen);

    @Override
    public @Nullable String getPreferenceScreenBindingKey(@NonNull Context context) {
        return BluetoothDashboardScreen.KEY;
    }

    private static final class MainSwitchAccessibilityDelegate extends View.AccessibilityDelegate {
        @Override
        public boolean onRequestSendAccessibilityEvent(
                @NonNull ViewGroup host, @NonNull View view, @NonNull AccessibilityEvent event) {
            if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                    && (event.getContentChangeTypes()
                    & AccessibilityEvent.CONTENT_CHANGE_TYPE_ENABLED)
                    != 0) {
                Log.d(TAG, "Skip accessibility event for CONTENT_CHANGE_TYPE_ENABLED");
                return false;
            }
            return super.onRequestSendAccessibilityEvent(host, view, event);
        }
    }
}
