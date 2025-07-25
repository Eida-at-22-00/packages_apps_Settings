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

package com.android.settings.network.ims;

import android.telephony.ims.ImsMmTelManager;
import android.util.Log;

/**
 * An {@link ImsQuery} for accessing IMS user setting for enhanced 4G LTE
 */
public class ImsQueryEnhanced4gLteModeUserSetting implements ImsQuery {

    private static final String LOG_TAG = "QueryEnhanced4gLteModeUserSetting";
    /**
     * Constructor
     * @param subId subscription id
     */
    public ImsQueryEnhanced4gLteModeUserSetting(int subId) {
        mSubId = subId;
    }

    private volatile int mSubId;

    /**
     * Implementation of interface {@link ImsQuery#query()}
     *
     * @return result of query
     */
    public boolean query() {
        try {
            final ImsMmTelManager imsMmTelManager =
                    ImsMmTelManager.createForSubscriptionId(mSubId);
            return imsMmTelManager.isAdvancedCallingSettingEnabled();
        } catch (IllegalArgumentException exception) {
            Log.w(LOG_TAG, "fail to get VoLte settings. subId=" + mSubId, exception);
        } catch (UnsupportedOperationException ex) {
            // expected on devices without IMS
        }
        return false;
    }
}
