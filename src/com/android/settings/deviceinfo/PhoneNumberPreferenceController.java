/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.settings.deviceinfo;

import android.content.Context;
import android.os.UserManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;
import com.android.settings.network.SubscriptionUtil;
import com.android.settingslib.Utils;

import java.util.ArrayList;
import java.util.List;

public class PhoneNumberPreferenceController extends BasePreferenceController {

    private static final String KEY_PHONE_NUMBER = "phone_number";
    private static final String KEY_PREFERENCE_CATEGORY = "basic_info_category";

    private final TelephonyManager mTelephonyManager;
    private final SubscriptionManager mSubscriptionManager;
    private final List<Preference> mPreferenceList = new ArrayList<>();
    private final List<Boolean> mTappedList = new ArrayList<>();

    public PhoneNumberPreferenceController(Context context, String key) {
        super(context, key);
        mTelephonyManager = mContext.getSystemService(TelephonyManager.class);
        mSubscriptionManager = mContext.getSystemService(SubscriptionManager.class);
        mTappedList.add(false);
    }

    @Override
    public int getAvailabilityStatus() {
        if (!SubscriptionUtil.isSimHardwareVisible(mContext) || Utils.isWifiOnly(mContext)) {
            return UNSUPPORTED_ON_DEVICE;
        }
        if (!mContext.getSystemService(UserManager.class).isAdminUser()) {
            return DISABLED_FOR_USER;
        }
        return AVAILABLE;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        if (!isAvailable()) {
            return;
        }

        final Preference preference = screen.findPreference(getPreferenceKey());
        final PreferenceCategory category = screen.findPreference(KEY_PREFERENCE_CATEGORY);
        preference.setCopyingEnabled(false);
        mPreferenceList.add(preference);

        final int phonePreferenceOrder = preference.getOrder();
        // Add additional preferences for each sim in the device
        for (int simSlotNumber = 1; simSlotNumber < mTelephonyManager.getPhoneCount();
                simSlotNumber++) {
            final Preference multiSimPreference = createNewPreference(screen.getContext());
            multiSimPreference.setSelectable(false);
            multiSimPreference.setCopyingEnabled(true);
            multiSimPreference.setOrder(phonePreferenceOrder + simSlotNumber);
            multiSimPreference.setKey(KEY_PHONE_NUMBER + simSlotNumber);
            multiSimPreference.setCopyingEnabled(false);
            category.addPreference(multiSimPreference);
            mPreferenceList.add(multiSimPreference);
            mTappedList.add(simSlotNumber, false);
        }
    }

    @Override
    public void updateState(Preference preference) {
        for (int simSlotNumber = 0; simSlotNumber < mPreferenceList.size(); simSlotNumber++) {
            final Preference simStatusPreference = mPreferenceList.get(simSlotNumber);
            final boolean tapped = mTappedList.get(simSlotNumber);
            simStatusPreference.setTitle(getPreferenceTitle(simSlotNumber));
            setPhoneNumber(simSlotNumber, tapped);
        }
    }

    @Override
    public boolean isSliceable() {
        return mTappedList.get(0);
    }

    @Override
    public boolean useDynamicSliceSummary() {
        return mTappedList.get(0);
    }

    @Override
    public boolean handlePreferenceTreeClick(Preference preference) {
        final int simSlotNumber = mPreferenceList.indexOf(preference);
        if (simSlotNumber == -1) {
            return false;
        }
        final boolean tapped = !mTappedList.get(simSlotNumber);
        mTappedList.set(simSlotNumber, tapped);
        setPhoneNumber(simSlotNumber, tapped);
        return true;
    }

    private void setPhoneNumber(int simSlot, boolean tapped) {
        final Preference simStatusPreference = mPreferenceList.get(simSlot);
        final SubscriptionInfo subscriptionInfo = getSubscriptionInfo(simSlot);
        simStatusPreference.setEnabled(subscriptionInfo != null);
        if (subscriptionInfo == null) {
            simStatusPreference.setSummary(mContext.getString(R.string.device_info_not_available));
        } else if (!tapped) {
            simStatusPreference.setSummary(mContext.getString(R.string.device_info_protected_single_press));
        } else {
            simStatusPreference.setSummary(getFormattedPhoneNumber(subscriptionInfo));
        }
        simStatusPreference.setCopyingEnabled(subscriptionInfo != null && tapped);
    }

    private CharSequence getPreferenceTitle(int simSlot) {
        return mTelephonyManager.getPhoneCount() > 1 ? mContext.getString(
                R.string.status_number_sim_slot, simSlot + 1) : mContext.getString(
                R.string.status_number);
    }

    @VisibleForTesting
    protected SubscriptionInfo getSubscriptionInfo(int simSlot) {
        final List<SubscriptionInfo> subscriptionInfoList =
                mSubscriptionManager.getActiveSubscriptionInfoList();
        if (subscriptionInfoList != null) {
            for (SubscriptionInfo info : subscriptionInfoList) {
                if (info.getSimSlotIndex() == simSlot) {
                    return info;
                }
            }
        }
        return null;
    }

    @VisibleForTesting
    protected String getFormattedPhoneNumber(SubscriptionInfo subscriptionInfo) {
        final String phoneNumber = SubscriptionUtil.getBidiFormattedPhoneNumber(mContext,
                subscriptionInfo);
        return TextUtils.isEmpty(phoneNumber) ? mContext.getString(R.string.device_info_default)
                : phoneNumber;
    }

    @VisibleForTesting
    protected Preference createNewPreference(Context context) {
        return new Preference(context);
    }
}
