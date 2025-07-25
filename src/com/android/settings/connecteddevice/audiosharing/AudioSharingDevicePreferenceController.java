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

import static com.android.settingslib.Utils.isAudioModeOngoingCall;
import static com.android.settingslib.bluetooth.LocalBluetoothLeBroadcast.EXTRA_BLUETOOTH_DEVICE;

import android.app.settings.SettingsEnums;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothLeBroadcastAssistant;
import android.bluetooth.BluetoothLeBroadcastMetadata;
import android.bluetooth.BluetoothLeBroadcastReceiveState;
import android.bluetooth.BluetoothProfile;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.SettingsActivity;
import com.android.settings.bluetooth.BluetoothDevicePreference;
import com.android.settings.bluetooth.BluetoothDeviceUpdater;
import com.android.settings.bluetooth.Utils;
import com.android.settings.connecteddevice.DevicePreferenceCallback;
import com.android.settings.core.BasePreferenceController;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.overlay.FeatureFactory;
import com.android.settingslib.bluetooth.A2dpProfile;
import com.android.settingslib.bluetooth.BluetoothCallback;
import com.android.settingslib.bluetooth.BluetoothEventManager;
import com.android.settingslib.bluetooth.BluetoothUtils;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.settingslib.bluetooth.CachedBluetoothDeviceManager;
import com.android.settingslib.bluetooth.HeadsetProfile;
import com.android.settingslib.bluetooth.HearingAidProfile;
import com.android.settingslib.bluetooth.LeAudioProfile;
import com.android.settingslib.bluetooth.LocalBluetoothLeBroadcast;
import com.android.settingslib.bluetooth.LocalBluetoothLeBroadcastAssistant;
import com.android.settingslib.bluetooth.LocalBluetoothManager;
import com.android.settingslib.bluetooth.LocalBluetoothProfileManager;
import com.android.settingslib.core.instrumentation.MetricsFeatureProvider;
import com.android.settingslib.flags.Flags;
import com.android.settingslib.utils.ThreadUtils;

import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class AudioSharingDevicePreferenceController extends BasePreferenceController
        implements DefaultLifecycleObserver,
                DevicePreferenceCallback,
                BluetoothCallback,
                LocalBluetoothProfileManager.ServiceListener {
    private static final boolean DEBUG = BluetoothUtils.D;

    private static final String TAG = "AudioSharingDevicePrefController";
    private static final String KEY = "audio_sharing_device_list";

    @Nullable private final LocalBluetoothManager mBtManager;
    @Nullable private final CachedBluetoothDeviceManager mDeviceManager;
    @Nullable private final BluetoothEventManager mEventManager;
    @Nullable private final LocalBluetoothProfileManager mProfileManager;
    @Nullable private final LocalBluetoothLeBroadcast mBroadcast;
    @Nullable private final LocalBluetoothLeBroadcastAssistant mAssistant;
    @Nullable private final ContentResolver mContentResolver;
    private final Executor mExecutor;
    private final MetricsFeatureProvider mMetricsFeatureProvider;
    @Nullable private PreferenceGroup mPreferenceGroup;
    @Nullable private BluetoothDeviceUpdater mBluetoothDeviceUpdater;
    @Nullable private DashboardFragment mFragment;
    @Nullable private AudioSharingDialogHandler mDialogHandler;
    private AtomicBoolean mIntentHandled = new AtomicBoolean(false);
    private AtomicBoolean mIsAudioModeOngoingCall = new AtomicBoolean(false);

    @VisibleForTesting
    BluetoothLeBroadcastAssistant.Callback mBroadcastAssistantCallback =
            new BluetoothLeBroadcastAssistant.Callback() {
                @Override
                public void onSearchStarted(int reason) {}

                @Override
                public void onSearchStartFailed(int reason) {}

                @Override
                public void onSearchStopped(int reason) {}

                @Override
                public void onSearchStopFailed(int reason) {}

                @Override
                public void onSourceFound(@NonNull BluetoothLeBroadcastMetadata source) {}

                @Override
                public void onSourceAdded(
                        @NonNull BluetoothDevice sink, int sourceId, int reason) {
                    Log.d(TAG, "onSourceAdded: update sharing device list.");
                    if (mBluetoothDeviceUpdater != null) {
                        mBluetoothDeviceUpdater.forceUpdate();
                    }
                    if (mDeviceManager != null && mDialogHandler != null) {
                        CachedBluetoothDevice cachedDevice = mDeviceManager.findDevice(sink);
                        if (cachedDevice != null) {
                            mDialogHandler.closeOpeningDialogsForLeaDevice(cachedDevice);
                        }
                    }
                }

                @Override
                public void onSourceAddFailed(
                        @NonNull BluetoothDevice sink,
                        @NonNull BluetoothLeBroadcastMetadata source,
                        int reason) {
                    mMetricsFeatureProvider.action(
                            mContext,
                            SettingsEnums.ACTION_AUDIO_SHARING_JOIN_FAILED,
                            SettingsEnums.SETTINGS_CONNECTED_DEVICE_CATEGORY);
                    AudioSharingUtils.toastMessage(
                            mContext,
                            String.format(
                                    Locale.US,
                                    "Fail to add source to %s reason %d",
                                    sink.getAddress(),
                                    reason));
                }

                @Override
                public void onSourceModified(
                        @NonNull BluetoothDevice sink, int sourceId, int reason) {}

                @Override
                public void onSourceModifyFailed(
                        @NonNull BluetoothDevice sink, int sourceId, int reason) {}

                @Override
                public void onSourceRemoved(
                        @NonNull BluetoothDevice sink, int sourceId, int reason) {
                    Log.d(TAG, "onSourceRemoved: update sharing device list.");
                    if (mBluetoothDeviceUpdater != null) {
                        mBluetoothDeviceUpdater.forceUpdate();
                    }
                }

                @Override
                public void onSourceRemoveFailed(
                        @NonNull BluetoothDevice sink, int sourceId, int reason) {
                    mMetricsFeatureProvider.action(
                            mContext,
                            SettingsEnums.ACTION_AUDIO_SHARING_LEAVE_FAILED,
                            SettingsEnums.SETTINGS_CONNECTED_DEVICE_CATEGORY);
                    AudioSharingUtils.toastMessage(
                            mContext,
                            String.format(
                                    Locale.US,
                                    "Fail to remove source from %s reason %d",
                                    sink.getAddress(),
                                    reason));
                }

                @Override
                public void onReceiveStateChanged(
                        @NonNull BluetoothDevice sink,
                        int sourceId,
                        @NonNull BluetoothLeBroadcastReceiveState state) {}
            };

    @VisibleForTesting
    ContentObserver mSettingsObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override
        public void onChange(boolean selfChange) {
            Log.d(TAG, "onChange, primary group id has been changed, refresh list");
            if (mBluetoothDeviceUpdater != null) {
                mBluetoothDeviceUpdater.refreshPreference();
            }
        }
    };

    public AudioSharingDevicePreferenceController(Context context) {
        super(context, KEY);
        mBtManager = Utils.getLocalBtManager(mContext);
        mEventManager = mBtManager == null ? null : mBtManager.getEventManager();
        mDeviceManager = mBtManager == null ? null : mBtManager.getCachedDeviceManager();
        mProfileManager = mBtManager == null ? null : mBtManager.getProfileManager();
        mBroadcast = mProfileManager == null ? null : mProfileManager.getLeAudioBroadcastProfile();
        mAssistant =
                mProfileManager == null
                        ? null
                        : mProfileManager.getLeAudioBroadcastAssistantProfile();
        mContentResolver = context.getContentResolver();
        mExecutor = Executors.newSingleThreadExecutor();
        mMetricsFeatureProvider = FeatureFactory.getFeatureFactory().getMetricsFeatureProvider();
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        var unused = ThreadUtils.postOnBackgroundThread(() -> {
            if (!isAvailable()) {
                Log.d(TAG, "Skip onStart(), feature is not supported.");
                return;
            }
            if (!AudioSharingUtils.isAudioSharingProfileReady(mProfileManager)
                    && mProfileManager != null) {
                Log.d(TAG, "Register profile service listener");
                mProfileManager.addServiceListener(this);
            }
            if (mEventManager == null
                    || mAssistant == null
                    || mDialogHandler == null
                    || mContentResolver == null
                    || mBluetoothDeviceUpdater == null) {
                Log.d(TAG, "Skip onStart(), profile is not ready.");
                return;
            }
            Log.d(TAG, "onStart() Register callbacks.");
            mEventManager.registerCallback(this);
            mAssistant.registerServiceCallBack(mExecutor, mBroadcastAssistantCallback);
            mDialogHandler.registerCallbacks(mExecutor);
            mContentResolver.registerContentObserver(
                    Settings.Secure.getUriFor(BluetoothUtils.getPrimaryGroupIdUriForBroadcast()),
                    false,
                    mSettingsObserver);
            mBluetoothDeviceUpdater.registerCallback();
            mBluetoothDeviceUpdater.refreshPreference();
            mIsAudioModeOngoingCall.set(isAudioModeOngoingCall(mContext));
            updateTitle();
        });
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        var unused = ThreadUtils.postOnBackgroundThread(() -> {
            if (!isAvailable()) {
                Log.d(TAG, "Skip onStop(), feature is not supported.");
                return;
            }
            if (mProfileManager != null) {
                mProfileManager.removeServiceListener(this);
            }
            if (mEventManager == null
                    || mAssistant == null
                    || mDialogHandler == null
                    || mContentResolver == null
                    || mBluetoothDeviceUpdater == null) {
                Log.d(TAG, "Skip onStop(), profile is not ready.");
                return;
            }
            Log.d(TAG, "onStop() Unregister callbacks.");
            mEventManager.unregisterCallback(this);
            mAssistant.unregisterServiceCallBack(mBroadcastAssistantCallback);
            mDialogHandler.unregisterCallbacks();
            mContentResolver.unregisterContentObserver(mSettingsObserver);
            mBluetoothDeviceUpdater.unregisterCallback();
        });
    }

    @Override
    public void onServiceConnected() {
        if (AudioSharingUtils.isAudioSharingProfileReady(mProfileManager)) {
            if (mProfileManager != null) {
                mProfileManager.removeServiceListener(this);
            }
            if (!mIntentHandled.get()) {
                Log.d(TAG, "onServiceConnected: handleDeviceClickFromIntent");
                handleDeviceClickFromIntent();
                mIntentHandled.set(true);
            }
        }
    }

    @Override
    public void onServiceDisconnected() {
        // Do nothing
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreferenceGroup = screen.findPreference(KEY);
        if (mPreferenceGroup != null) {
            mPreferenceGroup.setVisible(false);
        }

        if (isAvailable()) {
            if (mBluetoothDeviceUpdater != null) {
                mBluetoothDeviceUpdater.setPrefContext(screen.getContext());
                mBluetoothDeviceUpdater.forceUpdate();
            }
            if (AudioSharingUtils.isAudioSharingProfileReady(mProfileManager)) {
                if (!mIntentHandled.get()) {
                    Log.d(TAG, "displayPreference: profile ready, handleDeviceClickFromIntent");
                    var unused =
                            ThreadUtils.postOnBackgroundThread(() -> handleDeviceClickFromIntent());
                    mIntentHandled.set(true);
                }
            }
        }
    }

    @Override
    public int getAvailabilityStatus() {
        return (BluetoothUtils.isAudioSharingUIAvailable(mContext)
                && mBluetoothDeviceUpdater != null)
                ? AVAILABLE_UNSEARCHABLE
                : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public String getPreferenceKey() {
        return KEY;
    }

    @Override
    public void onDeviceAdded(Preference preference) {
        if (mPreferenceGroup != null) {
            if (mPreferenceGroup.getPreferenceCount() == 0) {
                mPreferenceGroup.setVisible(true);
            }
            mPreferenceGroup.addPreference(preference);
        }
    }

    @Override
    public void onDeviceRemoved(Preference preference) {
        if (mPreferenceGroup != null) {
            mPreferenceGroup.removePreference(preference);
            if (mPreferenceGroup.getPreferenceCount() == 0) {
                mPreferenceGroup.setVisible(false);
            }
        }
    }

    @Override
    public void onProfileConnectionStateChanged(
            @NonNull CachedBluetoothDevice cachedDevice,
            @ConnectionState int state,
            int bluetoothProfile) {
        if (mDialogHandler == null || mBroadcast == null || mAssistant == null
                || mFragment == null) {
            Log.d(TAG, "Ignore onProfileConnectionStateChanged, not init correctly");
            return;
        }
        if (Flags.promoteAudioSharingForSecondAutoConnectedLeaDevice() && mBroadcast.isEnabled(
                null)) {
            Log.d(TAG, "Ignore onProfileConnectionStateChanged, in broadcast");
            // Device connected in broadcast will be handled in sysui via settingslib
            return;
        }
        if (!isMediaDevice(cachedDevice)) {
            Log.d(TAG, "Ignore onProfileConnectionStateChanged, not a media device");
            return;
        }
        // Close related dialogs if the BT remote device is disconnected.
        if (state == BluetoothAdapter.STATE_DISCONNECTED) {
            boolean isLeAudioSupported = BluetoothUtils.isLeAudioSupported(cachedDevice);
            if (isLeAudioSupported
                    && bluetoothProfile == BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT) {
                Log.d(TAG, "closeOpeningDialogsForLeaDevice");
                mDialogHandler.closeOpeningDialogsForLeaDevice(cachedDevice);
                return;
            }
            if (!isLeAudioSupported && !cachedDevice.isConnected()) {
                Log.d(TAG, "closeOpeningDialogsForNonLeaDevice");
                mDialogHandler.closeOpeningDialogsForNonLeaDevice(cachedDevice);
                return;
            }
        }
        if (state != BluetoothAdapter.STATE_CONNECTED || !cachedDevice.getDevice().isConnected()) {
            Log.d(TAG, "Ignore onProfileConnectionStateChanged, not connected state");
            return;
        }
        handleOnProfileStateChanged(cachedDevice, bluetoothProfile);
    }

    @Override
    public void onBluetoothStateChanged(@AdapterState int bluetoothState) {
        if (bluetoothState == BluetoothAdapter.STATE_OFF && mDialogHandler != null) {
            mDialogHandler.closeOpeningDialogsOtherThan("");
        }
    }

    @Override
    public void onAudioModeChanged() {
        mIsAudioModeOngoingCall.set(isAudioModeOngoingCall(mContext));
        updateTitle();
    }

    @Override
    public void onDeviceClick(@NonNull Preference preference) {
        boolean isCallMode = mIsAudioModeOngoingCall.get();
        if (isCallMode) {
            Log.d(TAG, "onDeviceClick, set active in call mode");
            CachedBluetoothDevice cachedDevice =
                    ((BluetoothDevicePreference) preference).getBluetoothDevice();
            cachedDevice.setActive();
            AudioSharingUtils.setUserPreferredPrimary(mContext, cachedDevice);
        }
        mMetricsFeatureProvider.action(mContext, SettingsEnums.ACTION_AUDIO_SHARING_DEVICE_CLICK,
                isCallMode);
    }

    /**
     * Initialize the controller.
     *
     * @param fragment The fragment to provide the context and metrics category for {@link
     *     AudioSharingBluetoothDeviceUpdater} and provide the host for dialogs.
     */
    public void init(DashboardFragment fragment) {
        mFragment = fragment;
        mBluetoothDeviceUpdater =
                new AudioSharingBluetoothDeviceUpdater(
                        fragment.getContext(),
                        AudioSharingDevicePreferenceController.this,
                        fragment.getMetricsCategory());
        mDialogHandler = new AudioSharingDialogHandler(mContext, fragment);
    }

    @VisibleForTesting
    void setBluetoothDeviceUpdater(@Nullable BluetoothDeviceUpdater bluetoothDeviceUpdater) {
        mBluetoothDeviceUpdater = bluetoothDeviceUpdater;
    }

    @VisibleForTesting
    void setDialogHandler(@Nullable AudioSharingDialogHandler dialogHandler) {
        mDialogHandler = dialogHandler;
    }

    @VisibleForTesting
    void setHostFragment(@Nullable DashboardFragment fragment) {
        mFragment = fragment;
    }

    /** Test only: set intent handle state for test. */
    @VisibleForTesting
    void setIntentHandled(boolean handled) {
        mIntentHandled.set(handled);
    }

    private void handleOnProfileStateChanged(
            @NonNull CachedBluetoothDevice cachedDevice, int bluetoothProfile) {
        boolean isLeAudioSupported = BluetoothUtils.isLeAudioSupported(cachedDevice);
        // For eligible (LE audio) remote device, we only check its connected LE audio assistant
        // profile.
        if (isLeAudioSupported
                && bluetoothProfile != BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT) {
            Log.d(
                    TAG,
                    "Ignore onProfileConnectionStateChanged, not the le assistant profile for"
                            + " le audio device");
            return;
        }
        boolean isFirstConnectedProfile = isFirstConnectedProfile(cachedDevice, bluetoothProfile);
        // For ineligible (non LE audio) remote device, we only check its first connected profile.
        if (!isLeAudioSupported && !isFirstConnectedProfile) {
            Log.d(
                    TAG,
                    "Ignore onProfileConnectionStateChanged, not the first connected profile for"
                            + " non le audio device");
            return;
        }
        Log.d(TAG, "Start handling onProfileConnectionStateChanged for "
                + cachedDevice.getDevice().getAnonymizedAddress());
        // Check nullability to pass NullAway check
        if (mDialogHandler != null) {
            mDialogHandler.handleDeviceConnected(cachedDevice, /* userTriggered= */ false);
        }
    }

    private boolean isMediaDevice(CachedBluetoothDevice cachedDevice) {
        return cachedDevice.getUiAccessibleProfiles().stream()
                .anyMatch(
                        profile ->
                                profile instanceof A2dpProfile
                                        || profile instanceof HearingAidProfile
                                        || profile instanceof LeAudioProfile
                                        || profile instanceof HeadsetProfile);
    }

    private boolean isFirstConnectedProfile(
            CachedBluetoothDevice cachedDevice, int bluetoothProfile) {
        return cachedDevice.getProfiles().stream()
                .noneMatch(
                        profile ->
                                profile.getProfileId() != bluetoothProfile
                                        && profile.getConnectionStatus(cachedDevice.getDevice())
                                                == BluetoothProfile.STATE_CONNECTED);
    }

    /**
     * Handle device click triggered by intent.
     *
     * <p>When user click device from BT QS dialog, BT QS will send intent to open {@link
     * com.android.settings.connecteddevice.ConnectedDeviceDashboardFragment} and handle device
     * click event under some conditions.
     *
     * <p>This method will be called when displayPreference if the audio sharing profiles are ready.
     * If the profiles are not ready when the preference display, this method will be called when
     * onServiceConnected.
     */
    private void handleDeviceClickFromIntent() {
        if (mFragment == null
                || mFragment.getActivity() == null
                || mFragment.getActivity().getIntent() == null) {
            Log.d(TAG, "Skip handleDeviceClickFromIntent, fragment intent is null");
            return;
        }
        Intent intent = mFragment.getActivity().getIntent();
        Bundle args = intent.getBundleExtra(SettingsActivity.EXTRA_SHOW_FRAGMENT_ARGUMENTS);
        BluetoothDevice device =
                args == null
                        ? null
                        : args.getParcelable(EXTRA_BLUETOOTH_DEVICE, BluetoothDevice.class);
        CachedBluetoothDevice cachedDevice =
                (device == null || mDeviceManager == null)
                        ? null
                        : mDeviceManager.findDevice(device);
        if (cachedDevice == null) {
            Log.d(TAG, "Skip handleDeviceClickFromIntent, device is null");
            return;
        }
        // Check nullability to pass NullAway check
        if (device != null && !device.isConnected()) {
            Log.d(TAG, "handleDeviceClickFromIntent: connect");
            cachedDevice.connect();
        } else if (mDialogHandler != null) {
            Log.d(TAG, "handleDeviceClickFromIntent: trigger dialog handler");
            mDialogHandler.handleDeviceConnected(cachedDevice, /* userTriggered= */ true);
        }
    }

    private void updateTitle() {
        if (mPreferenceGroup == null) return;
        int titleResId;
        if (mIsAudioModeOngoingCall.get()) {
            // in phone call
            titleResId = R.string.connected_device_call_device_title;
        } else {
            // without phone call
            titleResId = R.string.audio_sharing_device_group_title;
        }
        AudioSharingUtils.postOnMainThread(mContext,
                () -> {
                    if (mPreferenceGroup != null) {
                        mPreferenceGroup.setTitle(titleResId);
                    }
                });
    }
}
