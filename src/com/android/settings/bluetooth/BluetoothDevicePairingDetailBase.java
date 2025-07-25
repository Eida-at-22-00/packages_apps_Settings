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

package com.android.settings.bluetooth;

import static android.os.UserManager.DISALLOW_CONFIG_BLUETOOTH;

import static com.android.settingslib.bluetooth.LocalBluetoothLeBroadcast.EXTRA_BT_DEVICE_TO_AUTO_ADD_SOURCE;
import static com.android.settingslib.bluetooth.LocalBluetoothLeBroadcast.EXTRA_PAIR_AND_JOIN_SHARING;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.settings.R;
import com.android.settings.SettingsActivity;
import com.android.settings.accessibility.AccessibilityStatsLogUtils;
import com.android.settings.connecteddevice.audiosharing.AudioSharingIncompatibleDialogFragment;
import com.android.settings.overlay.FeatureFactory;
import com.android.settingslib.bluetooth.BluetoothUtils;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.settingslib.bluetooth.HearingAidStatsLogUtils;
import com.android.settingslib.utils.ThreadUtils;

import com.google.common.collect.ImmutableList;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Abstract class for providing basic interaction for a list of Bluetooth devices in bluetooth
 * device pairing detail page.
 */
public abstract class BluetoothDevicePairingDetailBase extends DeviceListPreferenceFragment {
    private static final long AUTO_DISMISS_TIME_THRESHOLD_MS = TimeUnit.SECONDS.toMillis(15);
    private static final int AUTO_DISMISS_MESSAGE_ID = 1001;
    private static final ImmutableList<Integer> AUDIO_SHARING_PROFILES = ImmutableList.of(
            BluetoothProfile.LE_AUDIO,
            BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT, BluetoothProfile.VOLUME_CONTROL);

    protected boolean mInitialScanStarted;
    @VisibleForTesting
    protected BluetoothProgressCategory mAvailableDevicesCategory;
    @Nullable
    private volatile BluetoothDevice mJustBonded = null;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    @VisibleForTesting
    @Nullable
    ProgressDialogFragment mProgressDialog = null;
    @VisibleForTesting
    boolean mShouldTriggerShareThenPairFlow = false;
    private CopyOnWriteArrayList<BluetoothDevice> mDevicesWithMetadataChangedListener =
            new CopyOnWriteArrayList<>();

    // BluetoothDevicePreference updates the summary based on several callbacks, including
    // BluetoothAdapter.OnMetadataChangedListener and BluetoothCallback. In most cases,
    // metadata changes callback will be triggered before onDeviceBondStateChanged(BOND_BONDED).
    // And before we hear onDeviceBondStateChanged(BOND_BONDED), the BluetoothDevice.getState() has
    // already been BOND_BONDED. These event sequence will lead to: before we hear
    // onDeviceBondStateChanged(BOND_BONDED), BluetoothDevicePreference's summary has already
    // change from "Pairing..." to empty since it listens to metadata changes happens earlier.
    //
    // In share then pair flow, we have to wait on this page till the device is connected to check
    // the device type and handle extra logic for audio sharing.
    // The BluetoothDevicePreference summary will be blank for seconds between "Pairing..." and
    // "Connecting..." To help users better understand the process, we listen to metadata change
    // as well and show a progress dialog with "Connecting to ...." once BluetoothDevice.getState()
    // gets to BOND_BONDED.
    final BluetoothAdapter.OnMetadataChangedListener mMetadataListener =
            new BluetoothAdapter.OnMetadataChangedListener() {
                @Override
                public void onMetadataChanged(@NonNull BluetoothDevice device, int key,
                        @Nullable byte[] value) {
                    Log.d(getLogTag(), "onMetadataChanged device = " + device + ", key  = " + key);
                    if (mShouldTriggerShareThenPairFlow && mProgressDialog == null
                            && device.getBondState() == BluetoothDevice.BOND_BONDED
                            && mSelectedList.contains(device)) {
                        handleShareThenPair(device);
                        // Once device is bonded, remove the listener
                        removeOnMetadataChangedListener(device);
                    }
                }
            };

    public BluetoothDevicePairingDetailBase() {
        super(DISALLOW_CONFIG_BLUETOOTH);
    }

    @Override
    public void initPreferencesFromPreferenceScreen() {
        mAvailableDevicesCategory = findPreference(getDeviceListKey());
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mInitialScanStarted = false;
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mLocalManager == null) {
            Log.e(getLogTag(), "Bluetooth is not supported on this device");
            return;
        }
        updateBluetooth();
        mShouldTriggerShareThenPairFlow = shouldTriggerShareThenPairFlow();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mLocalManager == null) {
            Log.e(getLogTag(), "Bluetooth is not supported on this device");
            return;
        }
        disableScanning();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        var unused = ThreadUtils.postOnBackgroundThread(() -> {
            mDevicesWithMetadataChangedListener.forEach(
                    device -> {
                        try {
                            if (mBluetoothAdapter != null) {
                                mBluetoothAdapter.removeOnMetadataChangedListener(device,
                                        mMetadataListener);
                                mDevicesWithMetadataChangedListener.remove(device);
                            }
                        } catch (IllegalArgumentException e) {
                            Log.d(getLogTag(), "Fail to remove listener: " + e);
                        }
                    });
            mDevicesWithMetadataChangedListener.clear();
        });
    }

    @Override
    public void onBluetoothStateChanged(int bluetoothState) {
        super.onBluetoothStateChanged(bluetoothState);
        updateContent(bluetoothState);
        if (bluetoothState == BluetoothAdapter.STATE_ON) {
            showBluetoothTurnedOnToast();
        }
    }

    @Override
    public void onDeviceBondStateChanged(CachedBluetoothDevice cachedDevice, int bondState) {
        if (bondState == BluetoothDevice.BOND_BONDED) {
            if (cachedDevice != null && mShouldTriggerShareThenPairFlow) {
                BluetoothDevice device = cachedDevice.getDevice();
                if (device != null && mSelectedList.contains(device)) {
                    handleShareThenPair(device);
                    removeOnMetadataChangedListener(device);
                    return;
                }
            }
            // If one device is connected(bonded), then close this fragment.
            finish();
            return;
        } else if (bondState == BluetoothDevice.BOND_BONDING) {
            if (mShouldTriggerShareThenPairFlow && cachedDevice != null) {
                BluetoothDevice device = cachedDevice.getDevice();
                if (device != null && mSelectedList.contains(device)) {
                    addOnMetadataChangedListener(device);
                }
            }
            // Set the bond entry where binding process starts for logging hearing aid device info
            final int pageId = FeatureFactory.getFeatureFactory().getMetricsFeatureProvider()
                    .getAttribution(getActivity());
            final int bondEntry = AccessibilityStatsLogUtils.convertToHearingAidInfoBondEntry(
                    pageId);
            HearingAidStatsLogUtils.setBondEntryForDevice(bondEntry, cachedDevice);
        } else if (bondState == BluetoothDevice.BOND_NONE) {
            if (mShouldTriggerShareThenPairFlow && cachedDevice != null) {
                BluetoothDevice device = cachedDevice.getDevice();
                if (device != null && mSelectedList.contains(device)) {
                    removeOnMetadataChangedListener(device);
                }
            }
        }
        if (mSelectedDevice != null && cachedDevice != null) {
            BluetoothDevice device = cachedDevice.getDevice();
            if (device != null && mSelectedDevice.equals(device)
                    && bondState == BluetoothDevice.BOND_NONE) {
                // If currently selected device failed to bond, restart scanning
                enableScanning();
            }
        }
    }

    @Override
    public void onProfileConnectionStateChanged(
            @NonNull CachedBluetoothDevice cachedDevice, @ConnectionState int state,
            int bluetoothProfile) {
        // This callback is used to handle the case that bonded device is connected in pairing list.
        // 1. If user selected multiple bonded devices in pairing list, after connected
        // finish this page.
        // 2. If the bonded devices auto connected in paring list, after connected it will be
        // removed from paring list.
        if (cachedDevice != null && cachedDevice.isConnected()) {
            final BluetoothDevice device = cachedDevice.getDevice();
            if (device != null
                    && mSelectedList.contains(device)) {
                var unused = ThreadUtils.postOnBackgroundThread(() -> {
                    if (BluetoothUtils.isAudioSharingUIAvailable(getContext())) {
                        if (mShouldTriggerShareThenPairFlow
                                && state == BluetoothAdapter.STATE_CONNECTED
                                && device.equals(mJustBonded)
                                && AUDIO_SHARING_PROFILES.contains(bluetoothProfile)
                                && isReadyForAudioSharing(cachedDevice, bluetoothProfile)) {
                            Log.d(getLogTag(), "onProfileConnectionStateChanged, lea eligible");
                            dismissConnectingDialog();
                            mHandler.removeMessages(AUTO_DISMISS_MESSAGE_ID);
                            postOnMainThread(() -> finishFragmentWithResultForAudioSharing(device));
                        }
                    } else {
                        postOnMainThread(() -> finish());
                    }
                });
            } else {
                onDeviceDeleted(cachedDevice);
            }
        }
    }

    @Override
    public void enableScanning() {
        // Clear all device states before first scan
        if (!mInitialScanStarted) {
            if (mAvailableDevicesCategory != null) {
                removeAllDevices();
            }
            mLocalManager.getCachedDeviceManager().clearNonBondedDevices();
            mInitialScanStarted = true;
        }
        super.enableScanning();
    }

    @Override
    public void onDevicePreferenceClick(BluetoothDevicePreference btPreference) {
        disableScanning();
        super.onDevicePreferenceClick(btPreference);
        // Clean up the previous bond value
        mJustBonded = null;
    }

    @VisibleForTesting
    void updateBluetooth() {
        if (mBluetoothAdapter.isEnabled()) {
            updateContent(mBluetoothAdapter.getState());
        } else {
            // Turn on bluetooth if it is disabled
            mBluetoothAdapter.enable();
        }
    }

    /**
     * Enables the scanning when {@code bluetoothState} is on, or finish the page when
     * {@code bluetoothState} is off.
     *
     * @param bluetoothState the current Bluetooth state, the possible values that will handle here:
     *                       {@link android.bluetooth.BluetoothAdapter#STATE_OFF},
     *                       {@link android.bluetooth.BluetoothAdapter#STATE_ON},
     */
    @VisibleForTesting
    public void updateContent(int bluetoothState) {
        switch (bluetoothState) {
            case BluetoothAdapter.STATE_ON:
                mBluetoothAdapter.enable();
                enableScanning();
                break;

            case BluetoothAdapter.STATE_OFF:
                finish();
                break;
        }
    }

    @VisibleForTesting
    void showBluetoothTurnedOnToast() {
        Toast.makeText(getContext(), R.string.connected_device_bluetooth_turned_on_toast,
                Toast.LENGTH_SHORT).show();
    }

    @VisibleForTesting
    boolean shouldTriggerShareThenPairFlow() {
        if (BluetoothUtils.isAudioSharingUIAvailable(getContext())) {
            Activity activity = getActivity();
            Intent intent = activity == null ? null : activity.getIntent();
            Bundle args =
                    intent == null ? null :
                            intent.getBundleExtra(
                                    SettingsActivity.EXTRA_SHOW_FRAGMENT_ARGUMENTS);
            return args != null
                    && args.getBoolean(EXTRA_PAIR_AND_JOIN_SHARING, false);
        }
        return false;
    }

    private boolean isReadyForAudioSharing(@NonNull CachedBluetoothDevice cachedDevice,
            int justConnectedProfile) {
        for (int profile : AUDIO_SHARING_PROFILES) {
            // Skip checking connection state for just connected profile
            if (profile == justConnectedProfile) continue;
            switch (profile) {
                case BluetoothProfile.LE_AUDIO -> {
                    if (!cachedDevice.isConnectedLeAudioDevice()) {
                        Log.d(getLogTag(), "isReadyForAudioSharing, LE_AUDIO not ready");
                        return false;
                    }
                }
                case BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT -> {
                    if (!cachedDevice.isConnectedLeAudioBroadcastAssistantDevice()) {
                        Log.d(getLogTag(), "isReadyForAudioSharing, ASSISTANT not ready");
                        return false;
                    }
                }
                case BluetoothProfile.VOLUME_CONTROL -> {
                    if (!cachedDevice.isConnectedVolumeControlDevice()) {
                        Log.d(getLogTag(), "isReadyForAudioSharing, VC not ready");
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private void addOnMetadataChangedListener(@Nullable BluetoothDevice device) {
        var unused = ThreadUtils.postOnBackgroundThread(() -> {
            if (mBluetoothAdapter != null && device != null
                    && !mDevicesWithMetadataChangedListener.contains(device)) {
                mBluetoothAdapter.addOnMetadataChangedListener(device, mExecutor,
                        mMetadataListener);
                mDevicesWithMetadataChangedListener.add(device);
            }
        });
    }

    private void removeOnMetadataChangedListener(@Nullable BluetoothDevice device) {
        var unused = ThreadUtils.postOnBackgroundThread(() -> {
            if (mBluetoothAdapter != null && device != null
                    && mDevicesWithMetadataChangedListener.contains(device)) {
                try {
                    mBluetoothAdapter.removeOnMetadataChangedListener(device, mMetadataListener);
                    mDevicesWithMetadataChangedListener.remove(device);
                } catch (IllegalArgumentException e) {
                    Log.d(getLogTag(), "Fail to remove listener: " + e);
                }
            }
        });
    }

    private void handleShareThenPair(@Nullable BluetoothDevice device) {
        var unused = ThreadUtils.postOnBackgroundThread(() -> {
            if (mJustBonded != null) {
                Log.d(getLogTag(), "Skip handleShareThenPair, already done");
                return;
            }
            mJustBonded = device;
            // Show connecting device progress
            String aliasName = device.getAlias();
            String deviceName = TextUtils.isEmpty(aliasName) ? device.getAddress()
                    : aliasName;
            showConnectingDialog(deviceName);
            // For share then pair flow, we have strong signal that users wish to pair new
            // device to join sharing.
            // So we wait for AUTO_DISMISS_TIME_THRESHOLD_MS, if we find that the bonded device
            // is lea in onProfileConnectionStateChanged, we finish the activity, set the device
            // as temp bond and auto add source; otherwise, show dialog to notify that the
            // device is incompatible for audio sharing.
            if (!mHandler.hasMessages(AUTO_DISMISS_MESSAGE_ID)) {
                mHandler.postDelayed(() ->
                        postOnMainThread(
                                () -> {
                                    Log.d(getLogTag(), "Show incompatible dialog when timeout");
                                    dismissConnectingDialog();
                                    AudioSharingIncompatibleDialogFragment.show(this, deviceName,
                                            () -> finish());
                                }), AUTO_DISMISS_MESSAGE_ID, AUTO_DISMISS_TIME_THRESHOLD_MS);
            }
        });
    }

    private void finishFragmentWithResultForAudioSharing(@Nullable BluetoothDevice device) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra(EXTRA_BT_DEVICE_TO_AUTO_ADD_SOURCE, device);
        if (getActivity() != null) {
            getActivity().setResult(Activity.RESULT_OK, resultIntent);
        }
        finish();
    }

    private void showConnectingDialog(@NonNull String deviceName) {
        postOnMainThread(() -> {
            String message = getContext().getString(R.string.progress_dialog_connect_device_content,
                    deviceName);
            if (mProgressDialog == null) {
                mProgressDialog = ProgressDialogFragment.newInstance(this);
            }
            if (mProgressDialog != null) {
                mProgressDialog.show(message);
            }
        });
    }

    private void dismissConnectingDialog() {
        postOnMainThread(() -> {
            if (mProgressDialog != null) {
                Log.d(getLogTag(), "Dismiss connecting dialog.");
                mProgressDialog.dismissAllowingStateLoss();
            }
        });
    }

    private void postOnMainThread(@NonNull Runnable runnable) {
        getContext().getMainExecutor().execute(runnable);
    }
}
