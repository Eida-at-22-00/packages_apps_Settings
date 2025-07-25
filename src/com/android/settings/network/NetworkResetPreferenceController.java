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

package com.android.settings.network;

import android.content.Context;

import com.android.settings.core.PreferenceControllerMixin;
import com.android.settings.network.SubscriptionUtil;
import com.android.settingslib.Utils;
import com.android.settingslib.core.AbstractPreferenceController;

public class NetworkResetPreferenceController extends AbstractPreferenceController
        implements PreferenceControllerMixin {

    private final NetworkResetRestrictionChecker mRestrictionChecker;

    public NetworkResetPreferenceController(Context context) {
        super(context);
        mRestrictionChecker = new NetworkResetRestrictionChecker(context);
    }

    @Override
    public boolean isAvailable() {
        return (SubscriptionUtil.isSimHardwareVisible(mContext)
                && !Utils.isWifiOnly(mContext)
                && !mRestrictionChecker.hasUserRestriction());
    }

    @Override
    public String getPreferenceKey() {
        return "network_reset_mobile_network_settings_pref";
    }
}
