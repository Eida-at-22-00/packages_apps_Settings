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

package com.android.settings.connecteddevice.audiosharing.audiostreams;

import static com.android.settings.connecteddevice.audiosharing.audiostreams.AudioStreamMediaService.BROADCAST_ID;
import static com.android.settings.connecteddevice.audiosharing.audiostreams.AudioStreamMediaService.DEVICES;
import static com.android.settings.connecteddevice.audiosharing.audiostreams.AudioStreamMediaService.LEAVE_BROADCAST_ACTION;
import static com.android.settingslib.bluetooth.LocalBluetoothLeBroadcast.EXTRA_PRIVATE_BROADCAST_RECEIVE_DATA;
import static com.android.settingslib.bluetooth.LocalBluetoothLeBroadcastAssistant.LocalBluetoothLeBroadcastSourceState.DECRYPTION_FAILED;
import static com.android.settingslib.bluetooth.LocalBluetoothLeBroadcastAssistant.LocalBluetoothLeBroadcastSourceState.PAUSED;
import static com.android.settingslib.bluetooth.LocalBluetoothLeBroadcastAssistant.LocalBluetoothLeBroadcastSourceState.STREAMING;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.settings.SettingsEnums;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothLeBroadcastReceiveState;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.media.session.ISession;
import android.media.session.ISessionController;
import android.media.session.MediaSessionManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.platform.test.flag.junit.SetFlagsRule;
import android.util.DisplayMetrics;
import android.view.KeyEvent;

import com.android.settings.connecteddevice.audiosharing.audiostreams.testshadows.ShadowAudioStreamsHelper;
import com.android.settings.testutils.FakeFeatureFactory;
import com.android.settings.testutils.shadow.ShadowBluetoothAdapter;
import com.android.settings.testutils.shadow.ShadowBluetoothUtils;
import com.android.settings.testutils.shadow.ShadowThreadUtils;
import com.android.settingslib.bluetooth.BluetoothEventManager;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.settingslib.bluetooth.CachedBluetoothDeviceManager;
import com.android.settingslib.bluetooth.LocalBluetoothLeBroadcastAssistant;
import com.android.settingslib.bluetooth.LocalBluetoothManager;
import com.android.settingslib.bluetooth.LocalBluetoothProfileManager;
import com.android.settingslib.bluetooth.PrivateBroadcastReceiveData;
import com.android.settingslib.bluetooth.VolumeControlProfile;
import com.android.settingslib.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(
        shadows = {
            ShadowThreadUtils.class,
            ShadowBluetoothAdapter.class,
            ShadowBluetoothUtils.class,
            ShadowAudioStreamsHelper.class,
        })
public class AudioStreamMediaServiceTest {
    @Rule public final MockitoRule mMockitoRule = MockitoJUnit.rule();
    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();
    private static final String DEVICE_ADDRESS = "00:A1:A1:A1:A1:A1";
    private static final String CHANNEL_ID = "bluetooth_notification_channel";
    private static final String DEVICE_NAME = "name";
    @Mock private Resources mResources;
    @Mock private LocalBluetoothManager mLocalBtManager;
    @Mock private LocalBluetoothLeBroadcastAssistant mLeBroadcastAssistant;
    @Mock private BluetoothLeBroadcastReceiveState mBroadcastReceiveState;
    @Mock private AudioStreamsHelper mAudioStreamsHelper;
    @Mock private NotificationManager mNotificationManager;
    @Mock private MediaSessionManager mMediaSessionManager;
    @Mock private BluetoothEventManager mBluetoothEventManager;
    @Mock private LocalBluetoothProfileManager mLocalBluetoothProfileManager;
    @Mock private CachedBluetoothDeviceManager mCachedDeviceManager;
    @Mock private VolumeControlProfile mVolumeControlProfile;
    @Mock private CachedBluetoothDevice mCachedBluetoothDevice;
    @Mock private BluetoothDevice mDevice;
    @Mock
    private BluetoothDevice mDevice2;
    @Mock private ISession mISession;
    @Mock private ISessionController mISessionController;
    @Mock private PackageManager mPackageManager;
    @Mock private DisplayMetrics mDisplayMetrics;
    @Mock private Context mContext;
    @Mock private Handler mHandler;
    private FakeFeatureFactory mFeatureFactory;
    private AudioStreamMediaService mAudioStreamMediaService;

    @Before
    public void setUp() {
        mFeatureFactory = FakeFeatureFactory.setupForTest();
        ShadowAudioStreamsHelper.setUseMock(mAudioStreamsHelper);
        when(mAudioStreamsHelper.getLeBroadcastAssistant()).thenReturn(mLeBroadcastAssistant);
        ShadowBluetoothAdapter shadowBluetoothAdapter =
                Shadow.extract(BluetoothAdapter.getDefaultAdapter());
        shadowBluetoothAdapter.setEnabled(true);
        shadowBluetoothAdapter.setIsLeAudioBroadcastSourceSupported(
                BluetoothStatusCodes.FEATURE_SUPPORTED);
        shadowBluetoothAdapter.setIsLeAudioBroadcastAssistantSupported(
                BluetoothStatusCodes.FEATURE_SUPPORTED);
        ShadowBluetoothUtils.sLocalBluetoothManager = mLocalBtManager;
        when(mLocalBtManager.getEventManager()).thenReturn(mBluetoothEventManager);
        when(mLocalBtManager.getProfileManager()).thenReturn(mLocalBluetoothProfileManager);
        when(mLocalBtManager.getCachedDeviceManager()).thenReturn(mCachedDeviceManager);
        when(mCachedDeviceManager.findDevice(any())).thenReturn(mCachedBluetoothDevice);
        when(mCachedBluetoothDevice.getName()).thenReturn(DEVICE_NAME);
        when(mLocalBluetoothProfileManager.getVolumeControlProfile())
                .thenReturn(mVolumeControlProfile);
        when(mHandler.post(any(Runnable.class))).thenAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        });
        when(mHandler.getLooper()).thenReturn(Looper.getMainLooper());
        mAudioStreamMediaService = spy(new AudioStreamMediaService() {
            @Override
            Handler getHandler() {
                return mHandler;
            }
        });
        ReflectionHelpers.setField(mAudioStreamMediaService, "mBase", mContext);
        when(mAudioStreamMediaService.getSystemService(anyString()))
                .thenReturn(mMediaSessionManager);
        when(mMediaSessionManager.createSession(any(), anyString(), any())).thenReturn(mISession);
        try {
            when(mISession.getController()).thenReturn(mISessionController);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
        doReturn(mNotificationManager)
                .when(mAudioStreamMediaService)
                .getSystemService(NotificationManager.class);
        when(mAudioStreamMediaService.getApplicationInfo()).thenReturn(new ApplicationInfo());
        when(mAudioStreamMediaService.getResources()).thenReturn(mResources);
        when(mAudioStreamMediaService.getPackageManager()).thenReturn(mPackageManager);
        when(mResources.getDisplayMetrics()).thenReturn(mDisplayMetrics);
        mDisplayMetrics.density = 1.5f;
    }

    @After
    public void tearDown() {
        mAudioStreamMediaService.stopSelf();
        ShadowBluetoothUtils.reset();
        ShadowAudioStreamsHelper.reset();
    }

    @Test
    public void onCreate_flagOff_doNothing() {
        mSetFlagsRule.disableFlags(Flags.FLAG_ENABLE_LE_AUDIO_SHARING);

        mAudioStreamMediaService.onCreate();

        verify(mNotificationManager, never()).createNotificationChannel(any());
        verify(mBluetoothEventManager, never()).registerCallback(any());
        verify(mLeBroadcastAssistant, never()).registerServiceCallBack(any(), any());
        verify(mVolumeControlProfile, never()).registerCallback(any(), any());
    }

    @Test
    public void onCreate_flagOn_init() {
        mSetFlagsRule.enableFlags(Flags.FLAG_ENABLE_LE_AUDIO_SHARING);

        mAudioStreamMediaService.onCreate();

        verify(mNotificationManager).createNotificationChannel(any());
        verify(mBluetoothEventManager).registerCallback(any());
        verify(mLeBroadcastAssistant).registerServiceCallBack(any(), any());
        verify(mVolumeControlProfile).registerCallback(any(), any());
    }

    @Test
    public void onCreate_flagOn_createNewChannel() {
        mSetFlagsRule.enableFlags(Flags.FLAG_ENABLE_LE_AUDIO_SHARING);
        when(mNotificationManager.getNotificationChannel(anyString())).thenReturn(null);

        mAudioStreamMediaService.onCreate();

        ArgumentCaptor<NotificationChannel> notificationChannelCapture =
                ArgumentCaptor.forClass(NotificationChannel.class);
        verify(mNotificationManager)
                .createNotificationChannel(notificationChannelCapture.capture());
        NotificationChannel newChannel = notificationChannelCapture.getValue();
        assertThat(newChannel).isNotNull();
        assertThat(newChannel.getId()).isEqualTo(CHANNEL_ID);
        assertThat(newChannel.getName())
                .isEqualTo(mContext.getString(com.android.settings.R.string.bluetooth));
        assertThat(newChannel.getImportance()).isEqualTo(NotificationManager.IMPORTANCE_HIGH);
    }

    @Test
    public void onDestroy_flagOff_doNothing() {
        mSetFlagsRule.disableFlags(Flags.FLAG_ENABLE_LE_AUDIO_SHARING);

        mAudioStreamMediaService.onCreate();
        mAudioStreamMediaService.onDestroy();

        verify(mBluetoothEventManager, never()).unregisterCallback(any());
        verify(mLeBroadcastAssistant, never()).unregisterServiceCallBack(any());
        verify(mVolumeControlProfile, never()).unregisterCallback(any());
    }

    @Test
    public void onDestroy_flagOn_cleanup() {
        mSetFlagsRule.enableFlags(Flags.FLAG_ENABLE_LE_AUDIO_SHARING);
        mSetFlagsRule.disableFlags(Flags.FLAG_AUDIO_STREAM_MEDIA_SERVICE_BY_RECEIVE_STATE);
        var devices = new ArrayList<BluetoothDevice>();
        devices.add(mDevice);

        Intent intent = new Intent();
        intent.putExtra(BROADCAST_ID, 1);
        intent.putParcelableArrayListExtra(DEVICES, devices);

        mAudioStreamMediaService.onCreate();
        mAudioStreamMediaService.onStartCommand(intent, /* flags= */ 0, /* startId= */ 0);
        mAudioStreamMediaService.onDestroy();

        verify(mBluetoothEventManager).unregisterCallback(any());
        verify(mLeBroadcastAssistant).unregisterServiceCallBack(any());
        verify(mVolumeControlProfile).unregisterCallback(any());
    }

    @Test
    public void byReceiveStateFlagOn_onDestroy_flagOn_cleanup() {
        mSetFlagsRule.enableFlags(Flags.FLAG_ENABLE_LE_AUDIO_SHARING);
        mSetFlagsRule.enableFlags(Flags.FLAG_AUDIO_STREAM_MEDIA_SERVICE_BY_RECEIVE_STATE);

        Intent intent = setupReceiveDataIntent(1, mDevice, STREAMING);
        mAudioStreamMediaService.onCreate();
        mAudioStreamMediaService.onStartCommand(intent, /* flags= */ 0, /* startId= */ 0);
        mAudioStreamMediaService.onDestroy();

        verify(mBluetoothEventManager).unregisterCallback(any());
        verify(mLeBroadcastAssistant).unregisterServiceCallBack(any());
        verify(mVolumeControlProfile).unregisterCallback(any());
    }

    @Test
    public void byReceiveStateFlagOn_onStartCommand_invalidData_stopSelf() {
        mSetFlagsRule.enableFlags(Flags.FLAG_AUDIO_STREAM_MEDIA_SERVICE_BY_RECEIVE_STATE);
        Intent intent = setupReceiveDataIntent(-1, mDevice, STREAMING);
        mAudioStreamMediaService.onStartCommand(intent, /* flags= */ 0, /* startId= */ 0);

        verify(mAudioStreamMediaService).stopSelf();
    }

    @Test
    public void onStartCommand_noBroadcastId_stopSelf() {
        mSetFlagsRule.disableFlags(Flags.FLAG_AUDIO_STREAM_MEDIA_SERVICE_BY_RECEIVE_STATE);
        mAudioStreamMediaService.onStartCommand(new Intent(), /* flags= */ 0, /* startId= */ 0);

        verify(mAudioStreamMediaService).stopSelf();
    }

    @Test
    public void onStartCommand_noDevice_stopSelf() {
        mSetFlagsRule.disableFlags(Flags.FLAG_AUDIO_STREAM_MEDIA_SERVICE_BY_RECEIVE_STATE);
        Intent intent = new Intent();
        intent.putExtra(BROADCAST_ID, 1);

        mAudioStreamMediaService.onStartCommand(intent, /* flags= */ 0, /* startId= */ 0);

        verify(mAudioStreamMediaService).stopSelf();
    }

    @Test
    public void byReceiveStateFlagOn_onStartCommand_createSessionAndStartForeground() {
        mSetFlagsRule.enableFlags(Flags.FLAG_AUDIO_STREAM_MEDIA_SERVICE_BY_RECEIVE_STATE);
        Intent intent = setupReceiveDataIntent(1, mDevice, STREAMING);
        mAudioStreamMediaService.onStartCommand(intent, /* flags= */ 0, /* startId= */ 0);

        ArgumentCaptor<Notification> notificationCapture = ArgumentCaptor.forClass(
                Notification.class);
        verify(mAudioStreamMediaService).startForeground(anyInt(), notificationCapture.capture());
        var notification = notificationCapture.getValue();
        assertThat(notification.getSmallIcon()).isNotNull();
        assertThat(notification.isStyle(Notification.MediaStyle.class)).isTrue();

        verify(mAudioStreamMediaService, never()).stopSelf();
    }

    @Test
    public void byReceiveStateFlagOn_onStartCommand_decryptionFailed_stopSelf() {
        mSetFlagsRule.enableFlags(Flags.FLAG_AUDIO_STREAM_MEDIA_SERVICE_BY_RECEIVE_STATE);
        Intent intent = setupReceiveDataIntent(1, mDevice, DECRYPTION_FAILED);
        mAudioStreamMediaService.onStartCommand(intent, /* flags= */ 0, /* startId= */ 0);

        verify(mAudioStreamMediaService).stopSelf();
    }

    @Test
    public void byReceiveStateFlagOn_onStartCommand_addDevice() {
        mSetFlagsRule.enableFlags(Flags.FLAG_AUDIO_STREAM_MEDIA_SERVICE_BY_RECEIVE_STATE);
        mAudioStreamMediaService.onCreate();
        Intent intent1 = setupReceiveDataIntent(1, mDevice, STREAMING);
        mAudioStreamMediaService.onStartCommand(intent1, /* flags= */ 0, /* startId= */ 0);
        Intent intent2 = setupReceiveDataIntent(1, mDevice2, PAUSED);
        mAudioStreamMediaService.onStartCommand(intent2, /* flags= */ 0, /* startId= */ 0);

        ArgumentCaptor<Notification> notificationCapture = ArgumentCaptor.forClass(
                Notification.class);
        verify(mAudioStreamMediaService).startForeground(anyInt(), notificationCapture.capture());
        var notification = notificationCapture.getValue();
        assertThat(notification.getSmallIcon()).isNotNull();
        assertThat(notification.isStyle(Notification.MediaStyle.class)).isTrue();

        assertThat(mAudioStreamMediaService.mStateByDevice).isNotNull();
        var deviceState = mAudioStreamMediaService.mStateByDevice.get(mDevice);
        assertThat(deviceState).isNotNull();
        assertThat(deviceState).isEqualTo(STREAMING);
        var device2State = mAudioStreamMediaService.mStateByDevice.get(mDevice2);
        assertThat(device2State).isNotNull();
        assertThat(device2State).isEqualTo(PAUSED);
        verify(mAudioStreamMediaService, never()).stopSelf();
        verify(mNotificationManager).notify(anyInt(), any());
    }

    @Test
    public void byReceiveStateFlagOn_onStartCommand_updateState() {
        mSetFlagsRule.enableFlags(Flags.FLAG_AUDIO_STREAM_MEDIA_SERVICE_BY_RECEIVE_STATE);
        mAudioStreamMediaService.onCreate();
        Intent intent1 = setupReceiveDataIntent(1, mDevice, STREAMING);
        mAudioStreamMediaService.onStartCommand(intent1, /* flags= */ 0, /* startId= */ 0);
        Intent intent2 = setupReceiveDataIntent(1, mDevice, PAUSED);
        mAudioStreamMediaService.onStartCommand(intent2, /* flags= */ 0, /* startId= */ 0);

        ArgumentCaptor<Notification> notificationCapture = ArgumentCaptor.forClass(
                Notification.class);
        verify(mAudioStreamMediaService).startForeground(anyInt(), notificationCapture.capture());
        var notification = notificationCapture.getValue();
        assertThat(notification.getSmallIcon()).isNotNull();
        assertThat(notification.isStyle(Notification.MediaStyle.class)).isTrue();

        assertThat(mAudioStreamMediaService.mStateByDevice).isNotNull();
        var deviceState = mAudioStreamMediaService.mStateByDevice.get(mDevice);
        assertThat(deviceState).isNotNull();
        assertThat(deviceState).isEqualTo(PAUSED);
        verify(mAudioStreamMediaService, never()).stopSelf();
        verify(mNotificationManager).notify(anyInt(), any());
    }

    @Test
    public void byReceiveStateFlagOn_onStartCommand_newBroadcastId() {
        mSetFlagsRule.enableFlags(Flags.FLAG_AUDIO_STREAM_MEDIA_SERVICE_BY_RECEIVE_STATE);
        mAudioStreamMediaService.onCreate();
        Intent intent1 = setupReceiveDataIntent(1, mDevice, STREAMING);
        mAudioStreamMediaService.onStartCommand(intent1, /* flags= */ 0, /* startId= */ 0);
        Intent intent2 = setupReceiveDataIntent(2, mDevice2, PAUSED);
        mAudioStreamMediaService.onStartCommand(intent2, /* flags= */ 0, /* startId= */ 0);

        ArgumentCaptor<Notification> notificationCapture = ArgumentCaptor.forClass(
                Notification.class);
        verify(mAudioStreamMediaService).startForeground(anyInt(), notificationCapture.capture());
        var notification = notificationCapture.getValue();
        assertThat(notification.getSmallIcon()).isNotNull();
        assertThat(notification.isStyle(Notification.MediaStyle.class)).isTrue();

        assertThat(mAudioStreamMediaService.mStateByDevice).isNotNull();
        var oldDeviceState = mAudioStreamMediaService.mStateByDevice.get(mDevice);
        assertThat(oldDeviceState).isNull();
        var newDeviceState = mAudioStreamMediaService.mStateByDevice.get(mDevice2);
        assertThat(newDeviceState).isEqualTo(PAUSED);
        verify(mAudioStreamMediaService, never()).stopSelf();
        verify(mNotificationManager).notify(anyInt(), any());
    }

    @Test
    public void onStartCommand_createSessionAndStartForeground() {
        mSetFlagsRule.disableFlags(Flags.FLAG_AUDIO_STREAM_MEDIA_SERVICE_BY_RECEIVE_STATE);
        var devices = new ArrayList<BluetoothDevice>();
        devices.add(mDevice);

        Intent intent = new Intent();
        intent.putExtra(BROADCAST_ID, 1);
        intent.putParcelableArrayListExtra(DEVICES, devices);

        mAudioStreamMediaService.onStartCommand(intent, /* flags= */ 0, /* startId= */ 0);

        ArgumentCaptor<Notification> notificationCapture =
                ArgumentCaptor.forClass(Notification.class);
        verify(mAudioStreamMediaService).startForeground(anyInt(), notificationCapture.capture());
        var notification = notificationCapture.getValue();
        assertThat(notification.getSmallIcon()).isNotNull();
        assertThat(notification.isStyle(Notification.MediaStyle.class)).isTrue();

        verify(mAudioStreamMediaService, never()).stopSelf();
    }

    @Test
    public void assistantCallback_onSourceLost_stopSelf() {
        mAudioStreamMediaService.onCreate();

        assertThat(mAudioStreamMediaService.mBroadcastAssistantCallback).isNotNull();
        mAudioStreamMediaService.mBroadcastAssistantCallback.onSourceLost(/* broadcastId= */ 0);

        verify(mAudioStreamMediaService).stopSelf();
    }

    @Test
    public void assistantCallback_onSourceRemoved_stopSelf() {
        mAudioStreamMediaService.onCreate();

        assertThat(mAudioStreamMediaService.mBroadcastAssistantCallback).isNotNull();
        mAudioStreamMediaService.mBroadcastAssistantCallback.onSourceRemoved(
                mDevice, /* sourceId= */ 0, /* reason= */ 0);

        verify(mAudioStreamMediaService).stopSelf();
    }

    @Test
    public void assistantCallback_onReceiveStateChanged_connected_doNothing() {
        mSetFlagsRule.disableFlags(Flags.FLAG_AUDIO_STREAM_MEDIA_SERVICE_BY_RECEIVE_STATE);
        mSetFlagsRule.enableFlags(Flags.FLAG_AUDIO_SHARING_HYSTERESIS_MODE_FIX);
        mSetFlagsRule.enableFlags(Flags.FLAG_ENABLE_LE_AUDIO_SHARING);

        mAudioStreamMediaService.onCreate();
        mAudioStreamMediaService.onStartCommand(setupIntent(), /* flags= */ 0, /* startId= */ 0);

        assertThat(mAudioStreamMediaService.mBroadcastAssistantCallback).isNotNull();
        List<Long> bisSyncState = new ArrayList<>();
        bisSyncState.add(1L);
        when(mBroadcastReceiveState.getBisSyncState()).thenReturn(bisSyncState);
        when(mDevice.getAddress()).thenReturn(DEVICE_ADDRESS);
        when(mBroadcastReceiveState.getSourceDevice()).thenReturn(mDevice);

        mAudioStreamMediaService.mBroadcastAssistantCallback.onReceiveStateChanged(
                mDevice, /* sourceId= */ 0, /* state= */ mBroadcastReceiveState);

        verify(mNotificationManager, never()).notify(anyInt(), any());
    }

    @Test
    public void assistantCallback_onReceiveStateChanged_hysteresis_updateNotification() {
        mSetFlagsRule.disableFlags(Flags.FLAG_AUDIO_STREAM_MEDIA_SERVICE_BY_RECEIVE_STATE);
        mSetFlagsRule.enableFlags(Flags.FLAG_AUDIO_SHARING_HYSTERESIS_MODE_FIX);
        mSetFlagsRule.enableFlags(Flags.FLAG_ENABLE_LE_AUDIO_SHARING);

        mAudioStreamMediaService.onCreate();
        mAudioStreamMediaService.onStartCommand(setupIntent(), /* flags= */ 0, /* startId= */ 0);

        assertThat(mAudioStreamMediaService.mBroadcastAssistantCallback).isNotNull();
        when(mBroadcastReceiveState.getBisSyncState()).thenReturn(new ArrayList<>());
        when(mDevice.getAddress()).thenReturn(DEVICE_ADDRESS);
        when(mBroadcastReceiveState.getSourceDevice()).thenReturn(mDevice);

        mAudioStreamMediaService.mBroadcastAssistantCallback.onReceiveStateChanged(
                mDevice, /* sourceId= */ 0, /* state= */ mBroadcastReceiveState);

        verify(mNotificationManager).notify(anyInt(), any());
    }

    @Test
    public void assistantCallback_onReceiveStateChanged_hysteresis_flagOff_doNothing() {
        mSetFlagsRule.disableFlags(Flags.FLAG_AUDIO_STREAM_MEDIA_SERVICE_BY_RECEIVE_STATE);
        mSetFlagsRule.disableFlags(Flags.FLAG_AUDIO_SHARING_HYSTERESIS_MODE_FIX);
        mSetFlagsRule.enableFlags(Flags.FLAG_ENABLE_LE_AUDIO_SHARING);

        mAudioStreamMediaService.onCreate();
        mAudioStreamMediaService.onStartCommand(setupIntent(), /* flags= */ 0, /* startId= */ 0);

        assertThat(mAudioStreamMediaService.mBroadcastAssistantCallback).isNotNull();
        mAudioStreamMediaService.mBroadcastAssistantCallback.onReceiveStateChanged(
                mDevice, /* sourceId= */ 0, /* state= */ mBroadcastReceiveState);

        verify(mBroadcastReceiveState, never()).getBisSyncState();
        verify(mBroadcastReceiveState, never()).getSourceDevice();
        verify(mNotificationManager, never()).notify(anyInt(), any());
    }

    @Test
    public void bluetoothCallback_onBluetoothOff_stopSelf() {
        mAudioStreamMediaService.onCreate();

        assertThat(mAudioStreamMediaService.mBluetoothCallback).isNotNull();
        mAudioStreamMediaService.mBluetoothCallback.onBluetoothStateChanged(
                BluetoothAdapter.STATE_OFF);

        verify(mAudioStreamMediaService).stopSelf();
    }

    @Test
    public void bluetoothCallback_onDeviceDisconnect_stopSelf() {
        mSetFlagsRule.enableFlags(Flags.FLAG_ENABLE_LE_AUDIO_SHARING);
        mSetFlagsRule.disableFlags(Flags.FLAG_AUDIO_STREAM_MEDIA_SERVICE_BY_RECEIVE_STATE);
        mAudioStreamMediaService.onCreate();
        assertThat(mAudioStreamMediaService.mBluetoothCallback).isNotNull();
        mAudioStreamMediaService.onStartCommand(setupIntent(), /* flags= */ 0, /* startId= */ 0);

        mAudioStreamMediaService.mBluetoothCallback.onProfileConnectionStateChanged(
                mCachedBluetoothDevice,
                BluetoothAdapter.STATE_DISCONNECTED,
                BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT);

        verify(mAudioStreamMediaService).stopSelf();
    }

    @Test
    public void byReceiveStateFlagOn_bluetoothCallback_onDeviceDisconnect_stopSelf() {
        mSetFlagsRule.enableFlags(Flags.FLAG_ENABLE_LE_AUDIO_SHARING);
        mSetFlagsRule.enableFlags(Flags.FLAG_AUDIO_STREAM_MEDIA_SERVICE_BY_RECEIVE_STATE);
        mAudioStreamMediaService.onCreate();
        assertThat(mAudioStreamMediaService.mBluetoothCallback).isNotNull();
        Intent intent = setupReceiveDataIntent(1, mDevice, STREAMING);
        mAudioStreamMediaService.onStartCommand(intent, /* flags= */ 0, /* startId= */ 0);

        mAudioStreamMediaService.mBluetoothCallback.onProfileConnectionStateChanged(
                mCachedBluetoothDevice, BluetoothAdapter.STATE_DISCONNECTED,
                BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT);

        verify(mAudioStreamMediaService).stopSelf();
    }

    @Test
    public void mediaSessionCallback_onPause_setVolume() {
        mSetFlagsRule.enableFlags(Flags.FLAG_ENABLE_LE_AUDIO_SHARING);
        mSetFlagsRule.disableFlags(Flags.FLAG_AUDIO_STREAM_MEDIA_SERVICE_BY_RECEIVE_STATE);

        mAudioStreamMediaService.onCreate();
        mAudioStreamMediaService.onStartCommand(setupIntent(), /* flags= */ 0, /* startId= */ 0);
        assertThat(mAudioStreamMediaService.mMediaSessionCallback).isNotNull();
        mAudioStreamMediaService.mMediaSessionCallback.onPause();

        verify(mVolumeControlProfile).setDeviceVolume(any(), anyInt(), anyBoolean());
        verify(mFeatureFactory.metricsFeatureProvider)
                .action(
                        any(),
                        eq(SettingsEnums.ACTION_AUDIO_STREAM_NOTIFICATION_MUTE_BUTTON_CLICK),
                        eq(1));
    }

    @Test
    public void byReceiveStateFlagOn_mediaSessionCallback_onPause_setVolume() {
        mSetFlagsRule.enableFlags(Flags.FLAG_ENABLE_LE_AUDIO_SHARING);
        mSetFlagsRule.enableFlags(Flags.FLAG_AUDIO_STREAM_MEDIA_SERVICE_BY_RECEIVE_STATE);

        mAudioStreamMediaService.onCreate();
        Intent intent = setupReceiveDataIntent(1, mDevice, STREAMING);
        mAudioStreamMediaService.onStartCommand(intent, /* flags= */ 0, /* startId= */ 0);
        assertThat(mAudioStreamMediaService.mMediaSessionCallback).isNotNull();
        mAudioStreamMediaService.mMediaSessionCallback.onPause();

        verify(mVolumeControlProfile).setDeviceVolume(any(), anyInt(), anyBoolean());
        verify(mFeatureFactory.metricsFeatureProvider).action(any(),
                eq(SettingsEnums.ACTION_AUDIO_STREAM_NOTIFICATION_MUTE_BUTTON_CLICK), eq(1));
    }

    @Test
    public void mediaSessionCallback_onPlay_setVolume() {
        mSetFlagsRule.enableFlags(Flags.FLAG_ENABLE_LE_AUDIO_SHARING);
        mSetFlagsRule.disableFlags(Flags.FLAG_AUDIO_STREAM_MEDIA_SERVICE_BY_RECEIVE_STATE);

        mAudioStreamMediaService.onCreate();
        mAudioStreamMediaService.onStartCommand(setupIntent(), /* flags= */ 0, /* startId= */ 0);
        assertThat(mAudioStreamMediaService.mMediaSessionCallback).isNotNull();
        mAudioStreamMediaService.mMediaSessionCallback.onPlay();

        verify(mVolumeControlProfile).setDeviceVolume(any(), anyInt(), anyBoolean());
        verify(mFeatureFactory.metricsFeatureProvider)
                .action(
                        any(),
                        eq(SettingsEnums.ACTION_AUDIO_STREAM_NOTIFICATION_MUTE_BUTTON_CLICK),
                        eq(0));
    }

    @Test
    public void byReceiveStateFlagOn_mediaSessionCallback_onPlay_setVolume() {
        mSetFlagsRule.enableFlags(Flags.FLAG_ENABLE_LE_AUDIO_SHARING);
        mSetFlagsRule.enableFlags(Flags.FLAG_AUDIO_STREAM_MEDIA_SERVICE_BY_RECEIVE_STATE);

        mAudioStreamMediaService.onCreate();
        Intent intent = setupReceiveDataIntent(1, mDevice, STREAMING);
        mAudioStreamMediaService.onStartCommand(intent, /* flags= */ 0, /* startId= */ 0);
        assertThat(mAudioStreamMediaService.mMediaSessionCallback).isNotNull();
        mAudioStreamMediaService.mMediaSessionCallback.onPlay();

        verify(mVolumeControlProfile).setDeviceVolume(any(), anyInt(), anyBoolean());
        verify(mFeatureFactory.metricsFeatureProvider).action(any(),
                eq(SettingsEnums.ACTION_AUDIO_STREAM_NOTIFICATION_MUTE_BUTTON_CLICK), eq(0));
    }

    @Test
    public void byReceiveStateFlagOn_mediaSessionCallback_onButtonEventPlay_setVolume() {
        mSetFlagsRule.enableFlags(Flags.FLAG_ENABLE_LE_AUDIO_SHARING);
        mSetFlagsRule.enableFlags(Flags.FLAG_AUDIO_STREAM_MEDIA_SERVICE_BY_RECEIVE_STATE);

        mAudioStreamMediaService.onCreate();
        Intent intent = setupReceiveDataIntent(1, mDevice, STREAMING);
        mAudioStreamMediaService.onStartCommand(intent, /* flags= */ 0, /* startId= */ 0);
        assertThat(mAudioStreamMediaService.mMediaSessionCallback).isNotNull();

        Intent buttonEvent = new Intent();
        buttonEvent.putExtra(Intent.EXTRA_KEY_EVENT,
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY));
        mAudioStreamMediaService.mMediaSessionCallback.onMediaButtonEvent(buttonEvent);

        verify(mVolumeControlProfile).setDeviceVolume(any(), anyInt(), anyBoolean());
        verify(mFeatureFactory.metricsFeatureProvider).action(any(),
                eq(SettingsEnums.ACTION_AUDIO_STREAM_NOTIFICATION_MUTE_BUTTON_CLICK), eq(0));
    }

    @Test
    public void byReceiveStateFlagOn_mediaSessionCallback_onButtonEventPause_setVolume() {
        mSetFlagsRule.enableFlags(Flags.FLAG_ENABLE_LE_AUDIO_SHARING);
        mSetFlagsRule.enableFlags(Flags.FLAG_AUDIO_STREAM_MEDIA_SERVICE_BY_RECEIVE_STATE);

        mAudioStreamMediaService.onCreate();
        Intent intent = setupReceiveDataIntent(1, mDevice, STREAMING);
        mAudioStreamMediaService.onStartCommand(intent, /* flags= */ 0, /* startId= */ 0);
        assertThat(mAudioStreamMediaService.mMediaSessionCallback).isNotNull();

        Intent buttonEvent = new Intent();
        buttonEvent.putExtra(Intent.EXTRA_KEY_EVENT,
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE));
        mAudioStreamMediaService.mMediaSessionCallback.onMediaButtonEvent(buttonEvent);

        verify(mVolumeControlProfile).setDeviceVolume(any(), anyInt(), anyBoolean());
        verify(mFeatureFactory.metricsFeatureProvider).action(any(),
                eq(SettingsEnums.ACTION_AUDIO_STREAM_NOTIFICATION_MUTE_BUTTON_CLICK), eq(1));
    }

    @Test
    public void mediaSessionCallback_onCustomAction_leaveBroadcast() {
        mSetFlagsRule.enableFlags(Flags.FLAG_ENABLE_LE_AUDIO_SHARING);
        mSetFlagsRule.disableFlags(Flags.FLAG_AUDIO_STREAM_MEDIA_SERVICE_BY_RECEIVE_STATE);

        mAudioStreamMediaService.onCreate();
        mAudioStreamMediaService.onStartCommand(setupIntent(), /* flags= */ 0, /* startId= */ 0);
        assertThat(mAudioStreamMediaService.mMediaSessionCallback).isNotNull();
        mAudioStreamMediaService.mMediaSessionCallback.onCustomAction(
                LEAVE_BROADCAST_ACTION, Bundle.EMPTY);

        verify(mAudioStreamsHelper).removeSource(anyInt());
        verify(mFeatureFactory.metricsFeatureProvider)
                .action(
                        any(),
                        eq(SettingsEnums.ACTION_AUDIO_STREAM_NOTIFICATION_LEAVE_BUTTON_CLICK));
    }

    @Test
    public void byReceiveStateFlagOn_mediaSessionCallback_onCustomAction_leaveBroadcast() {
        mSetFlagsRule.enableFlags(Flags.FLAG_ENABLE_LE_AUDIO_SHARING);
        mSetFlagsRule.enableFlags(Flags.FLAG_AUDIO_STREAM_MEDIA_SERVICE_BY_RECEIVE_STATE);

        mAudioStreamMediaService.onCreate();
        Intent intent = setupReceiveDataIntent(1, mDevice, STREAMING);
        mAudioStreamMediaService.onStartCommand(intent, /* flags= */ 0, /* startId= */ 0);
        assertThat(mAudioStreamMediaService.mMediaSessionCallback).isNotNull();
        mAudioStreamMediaService.mMediaSessionCallback.onCustomAction(LEAVE_BROADCAST_ACTION,
                Bundle.EMPTY);

        verify(mAudioStreamsHelper).removeSource(anyInt());
        verify(mFeatureFactory.metricsFeatureProvider).action(any(),
                eq(SettingsEnums.ACTION_AUDIO_STREAM_NOTIFICATION_LEAVE_BUTTON_CLICK));
    }

    @Test
    public void onBind_returnNull() {
        IBinder binder = mAudioStreamMediaService.onBind(new Intent());

        assertThat(binder).isNull();
    }

    private Intent setupIntent() {
        when(mCachedBluetoothDevice.getDevice()).thenReturn(mDevice);
        var devices = new ArrayList<BluetoothDevice>();
        devices.add(mDevice);

        Intent intent = new Intent();
        intent.putExtra(BROADCAST_ID, 1);
        intent.putParcelableArrayListExtra(DEVICES, devices);
        return intent;
    }

    private Intent setupReceiveDataIntent(int broadcastId, BluetoothDevice device,
            LocalBluetoothLeBroadcastAssistant.LocalBluetoothLeBroadcastSourceState state) {
        when(mCachedBluetoothDevice.getDevice()).thenReturn(mDevice);
        Intent intent = new Intent();
        intent.putExtra(EXTRA_PRIVATE_BROADCAST_RECEIVE_DATA,
                new PrivateBroadcastReceiveData(device, 1, broadcastId, "programInfo", state));
        return intent;
    }
}
