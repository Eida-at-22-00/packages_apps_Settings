/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.settings.connecteddevice.audiosharing;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;

import com.android.settings.bluetooth.BluetoothDevicePreference;
import com.android.settings.bluetooth.BluetoothDeviceUpdater;
import com.android.settings.bluetooth.Utils;
import com.android.settings.connecteddevice.DevicePreferenceCallback;
import com.android.settingslib.bluetooth.BluetoothUtils;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.settingslib.bluetooth.LocalBluetoothManager;

public class AudioSharingDeviceVolumeControlUpdater extends BluetoothDeviceUpdater
        implements Preference.OnPreferenceClickListener {

    private static final String TAG = "AudioSharingVolUpdater";

    @VisibleForTesting
    static final String PREF_KEY_PREFIX = "audio_sharing_volume_control_";

    @Nullable private final LocalBluetoothManager mBtManager;

    public AudioSharingDeviceVolumeControlUpdater(
            Context context,
            DevicePreferenceCallback devicePreferenceCallback,
            int metricsCategory) {
        super(context, devicePreferenceCallback, metricsCategory);
        mBtManager = Utils.getLocalBluetoothManager(context);
    }

    @Override
    public boolean isFilterMatched(CachedBluetoothDevice cachedDevice) {
        boolean isFilterMatched = false;
        if (isDeviceConnected(cachedDevice) && isDeviceInCachedDevicesList(cachedDevice)) {
            // If device is LE audio device and in a sharing session on current sharing device,
            // it would show in volume control group.
            if ((cachedDevice.isConnectedLeAudioDevice()
                    || cachedDevice.hasConnectedLeAudioMemberDevice())
                    && BluetoothUtils.isBroadcasting(mBtManager)
                    && BluetoothUtils.hasConnectedBroadcastSource(cachedDevice, mBtManager)) {
                isFilterMatched = true;
            }
        }
        Log.d(
                TAG,
                "isFilterMatched() device : "
                        + cachedDevice.getName()
                        + ", isFilterMatched : "
                        + isFilterMatched);
        return isFilterMatched;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        return true;
    }

    @Override
    protected void addPreference(CachedBluetoothDevice cachedDevice) {
        if (cachedDevice == null) return;
        final BluetoothDevice device = cachedDevice.getDevice();
        if (!mPreferenceMap.containsKey(device)) {
            AudioSharingDeviceVolumePreference vPreference =
                    new AudioSharingDeviceVolumePreference(mPrefContext, cachedDevice);
            vPreference.initialize();
            vPreference.setKey(getPreferenceKeyPrefix() + cachedDevice.hashCode());
            vPreference.setIcon(com.android.settingslib.R.drawable.ic_bt_untethered_earbuds);
            vPreference.setTitle(cachedDevice.getName());
            mPreferenceMap.put(device, vPreference);
            mDevicePreferenceCallback.onDeviceAdded(vPreference);
        }
    }

    @Override
    public void refreshPreference() {
        mPreferenceMap.forEach((key, preference) -> {
            if (isDeviceOfMapInCachedDevicesList(key)) {
                ((AudioSharingDeviceVolumePreference) preference).onPreferenceAttributesChanged();
            } else {
                // Remove staled preference.
                Log.d(TAG, "removePreference key: " + key.getAnonymizedAddress());
                removePreference(key);
            }
        });
    }

    @Override
    protected void removePreference(BluetoothDevice device) {
        if (mPreferenceMap.containsKey(device)) {
            if (mPreferenceMap.get(device) instanceof AudioSharingDeviceVolumePreference pref) {
                BluetoothDevice prefDevice = pref.getCachedDevice().getDevice();
                // For CSIP device, when it {@link CachedBluetoothDevice}#switchMemberDeviceContent,
                // it will change its mDevice and lead to the hashcode change for this preference.
                // This will cause unintended remove preference, see b/394765052
                if (device.equals(prefDevice) || !mPreferenceMap.containsKey(prefDevice)) {
                    mDevicePreferenceCallback.onDeviceRemoved(pref);
                } else {
                    Log.w(TAG, "Inconsistent key and preference when removePreference");
                }
                mPreferenceMap.remove(device);
            } else {
                mDevicePreferenceCallback.onDeviceRemoved(mPreferenceMap.get(device));
                mPreferenceMap.remove(device);
            }
        }
    }

    @Override
    protected String getPreferenceKeyPrefix() {
        return PREF_KEY_PREFIX;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    protected void update(CachedBluetoothDevice cachedBluetoothDevice) {
        super.update(cachedBluetoothDevice);
        Log.d(TAG, "Map : " + mPreferenceMap);
    }

    @Override
    protected void addPreference(
            CachedBluetoothDevice cachedDevice, @BluetoothDevicePreference.SortType int type) {}

    @Override
    protected void launchDeviceDetails(Preference preference) {}
}
