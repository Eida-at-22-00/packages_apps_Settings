/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.settings.notification.app;

import android.app.Flags;
import android.app.settings.SettingsEnums;
import android.content.Context;
import android.os.Bundle;
import android.provider.Settings;
import android.service.notification.Adjustment;
import android.text.TextUtils;
import android.util.Log;

import androidx.preference.PreferenceScreen;
import androidx.recyclerview.widget.RecyclerView;

import com.android.internal.widget.LockPatternUtils;
import com.android.settings.R;
import com.android.settings.SettingsActivity;
import com.android.settingslib.core.AbstractPreferenceController;

import java.util.ArrayList;
import java.util.List;

/** These settings are per app, so should not be returned in global search results. */
public class AppNotificationSettings extends NotificationSettings {
    private static final String TAG = "AppNotificationSettings";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    boolean mShowAll = false;

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.NOTIFICATION_APP_NOTIFICATION;
    }

    @Override
    protected RecyclerView.Adapter onCreateAdapter(PreferenceScreen preferenceScreen) {
        if (Flags.uiRichOngoing()) {
            // If we got here via APP_NOTIFICATION_PROMOTION_SETTINGS intent, add the relevant
            // preference key to navigate to & highlight. This argument is passed into
            // HighlightablePreferenceGroupAdapter in the SettingsPreferenceFragment onCreateAdapter
            // call.
            if (mIntent != null && TextUtils.equals(mIntent.getAction(),
                    Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)) {
                Bundle args = getArguments();
                if (args == null) {
                    args = new Bundle();
                }
                args.putString(SettingsActivity.EXTRA_FRAGMENT_ARG_KEY,
                        PromotedNotificationsPreferenceController.KEY_PROMOTED_SWITCH);
                setArguments(args);
            }
        }
        return super.onCreateAdapter(preferenceScreen);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mUid < 0 || TextUtils.isEmpty(mPkg) || mPkgInfo == null) {
            Log.w(TAG, "Missing package or uid or packageinfo");
            finish();
            return;
        }

        getActivity().setTitle(mAppRow.label);

        for (NotificationPreferenceController controller : mControllers) {
            controller.onResume(mAppRow, mChannel, mChannelGroup, null, null, mSuspendedAppsAdmin,
                    null);
            controller.displayPreference(getPreferenceScreen());
        }
        updatePreferenceStates();
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.app_notification_settings;
    }

    @Override
    protected List<AbstractPreferenceController> createPreferenceControllers(Context context) {
        mControllers = new ArrayList<>();
        mControllers.add(new HeaderPreferenceController(context, this));
        mControllers.add(new BlockPreferenceController(context, mDependentFieldListener, mBackend));
        mControllers.add(new FullScreenIntentPermissionPreferenceController(context, mBackend));
        mControllers.add(new BadgePreferenceController(context, mBackend));
        mControllers.add(new AllowSoundPreferenceController(
                context, mDependentFieldListener, mBackend));
        mControllers.add(new ImportancePreferenceController(
                context, mDependentFieldListener, mBackend));
        mControllers.add(new MinImportancePreferenceController(
                context, mDependentFieldListener, mBackend));
        mControllers.add(new HighImportancePreferenceController(
                context, mDependentFieldListener, mBackend));
        mControllers.add(new SoundPreferenceController(context, this,
                mDependentFieldListener, mBackend));
        mControllers.add(new LightsPreferenceController(context, mBackend));
        mControllers.add(new VibrationPreferenceController(context, mBackend, mDependentFieldListener));
        mControllers.add(new VisibilityPreferenceController(context, new LockPatternUtils(context),
                mBackend));
        mControllers.add(new DndPreferenceController(context, mBackend));
        mControllers.add(new AppLinkPreferenceController(context));
        mControllers.add(new ChannelListPreferenceController(context, mBackend));
        mControllers.add(new AppConversationListPreferenceController(context, mBackend));
        mControllers.add(new InvalidConversationInfoPreferenceController(context, mBackend));
        mControllers.add(new InvalidConversationPreferenceController(context, mBackend));
        mControllers.add(new BubbleSummaryPreferenceController(context, mBackend));
        mControllers.add(new NotificationsOffPreferenceController(context));
        mControllers.add(new DeletedChannelsPreferenceController(context, mBackend));
        mControllers.add(new ShowMorePreferenceController(
                context, mDependentFieldListener, mBackend));
        mControllers.add(new BundleListPreferenceController(context, mBackend));
        mControllers.add(new PromotedNotificationsPreferenceController(context, mBackend));
        mControllers.add(new AdjustmentKeyPreferenceController(context, mBackend,
                Adjustment.KEY_SUMMARIZATION));
        mControllers.add(new AdjustmentKeyPreferenceController(context, mBackend,
                Adjustment.KEY_TYPE));
        return new ArrayList<>(mControllers);
    }
}
