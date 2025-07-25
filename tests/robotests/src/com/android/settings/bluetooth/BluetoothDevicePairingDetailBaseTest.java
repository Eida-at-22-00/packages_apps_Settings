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

import static com.android.settingslib.bluetooth.LocalBluetoothLeBroadcast.EXTRA_BT_DEVICE_TO_AUTO_ADD_SOURCE;
import static com.android.settingslib.bluetooth.LocalBluetoothLeBroadcast.EXTRA_PAIR_AND_JOIN_SHARING;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Looper;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ApplicationProvider;

import com.android.settings.R;
import com.android.settings.SettingsActivity;
import com.android.settings.testutils.shadow.ShadowAlertDialogCompat;
import com.android.settings.testutils.shadow.ShadowBluetoothAdapter;
import com.android.settings.testutils.shadow.ShadowFragment;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.settingslib.bluetooth.LocalBluetoothManager;
import com.android.settingslib.flags.Flags;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.annotation.Resetter;
import org.robolectric.shadow.api.Shadow;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/** Tests for {@link BluetoothDevicePairingDetailBase}. */
@RunWith(RobolectricTestRunner.class)
@Config(shadows = {
        ShadowBluetoothAdapter.class,
        ShadowAlertDialogCompat.class,
        ShadowFragment.class,
})
public class BluetoothDevicePairingDetailBaseTest {

    @Rule
    public final MockitoRule mMockitoRule = MockitoJUnit.rule();
    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    public static final String KEY_DEVICE_LIST_GROUP = "test_key";

    private static final String TEST_DEVICE_ADDRESS = "00:A1:A1:A1:A1:A1";
    private static final String TEST_DEVICE_ADDRESS_B = "00:B1:B1:B1:B1:B1";
    private final Context mContext = ApplicationProvider.getApplicationContext();

    @Mock
    private Resources mResource;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private LocalBluetoothManager mLocalManager;
    @Mock
    private CachedBluetoothDevice mCachedBluetoothDevice;
    @Mock
    private Drawable mDrawable;
    private BluetoothAdapter mBluetoothAdapter;
    private ShadowBluetoothAdapter mShadowBluetoothAdapter;
    private BluetoothProgressCategory mAvailableDevicesCategory;
    private BluetoothDevice mBluetoothDevice;
    private TestBluetoothDevicePairingDetailBase mFragment;

    @Before
    public void setUp() {
        mAvailableDevicesCategory = spy(new BluetoothProgressCategory(mContext));
        mBluetoothAdapter = spy(BluetoothAdapter.getDefaultAdapter());
        mShadowBluetoothAdapter = Shadow.extract(BluetoothAdapter.getDefaultAdapter());
        mShadowBluetoothAdapter.setIsLeAudioBroadcastSourceSupported(
                BluetoothStatusCodes.FEATURE_SUPPORTED);
        mShadowBluetoothAdapter.setIsLeAudioBroadcastAssistantSupported(
                BluetoothStatusCodes.FEATURE_SUPPORTED);
        when(mCachedBluetoothDevice.getAddress()).thenReturn(TEST_DEVICE_ADDRESS);
        final Pair<Drawable, String> pairs = new Pair<>(mDrawable, "fake_device");
        when(mCachedBluetoothDevice.getDrawableWithDescription()).thenReturn(pairs);
        mBluetoothDevice = mBluetoothAdapter.getRemoteDevice(TEST_DEVICE_ADDRESS);

        mFragment = spy(new TestBluetoothDevicePairingDetailBase());
        when(mFragment.findPreference(KEY_DEVICE_LIST_GROUP)).thenReturn(mAvailableDevicesCategory);
        doReturn(mContext).when(mFragment).getContext();
        doReturn(mResource).when(mFragment).getResources();
        mFragment.mDeviceListGroup = mAvailableDevicesCategory;
        mFragment.mLocalManager = mLocalManager;
        mFragment.mBluetoothAdapter = mBluetoothAdapter;
        mFragment.initPreferencesFromPreferenceScreen();
    }

    @Test
    public void startScanning_startScanAndRemoveDevices() {
        mFragment.enableScanning();

        verify(mFragment).startScanning();
        verify(mAvailableDevicesCategory).removeAll();
    }

    @Test
    public void updateContent_stateOn() {
        mFragment.updateContent(BluetoothAdapter.STATE_ON);

        assertThat(mBluetoothAdapter.isEnabled()).isTrue();
        verify(mFragment).enableScanning();
    }

    @Test
    public void updateContent_stateOff_finish() {
        mFragment.updateContent(BluetoothAdapter.STATE_OFF);

        verify(mFragment).finish();
    }

    @Test
    public void updateBluetooth_bluetoothOff_turnOnBluetooth() {
        mShadowBluetoothAdapter.setEnabled(false);

        mFragment.updateBluetooth();

        assertThat(mBluetoothAdapter.isEnabled()).isTrue();
    }

    @Test
    public void updateBluetooth_bluetoothOn_updateState() {
        mShadowBluetoothAdapter.setEnabled(true);
        doNothing().when(mFragment).updateContent(anyInt());

        mFragment.updateBluetooth();

        verify(mFragment).updateContent(anyInt());
    }

    @Test
    public void onBluetoothStateChanged_whenTurnedOnBTShowToast() {
        doNothing().when(mFragment).updateContent(anyInt());

        mFragment.onBluetoothStateChanged(BluetoothAdapter.STATE_ON);

        verify(mFragment).showBluetoothTurnedOnToast();
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_LE_AUDIO_SHARING)
    @DisableFlags(Flags.FLAG_ENABLE_TEMPORARY_BOND_DEVICES_UI)
    public void onDeviceBondStateChanged_bonded_notPairInSharing_finish() {
        when(mCachedBluetoothDevice.getDevice()).thenReturn(mBluetoothDevice);
        mFragment.mSelectedList.add(mBluetoothDevice);
        setUpFragmentWithShareThenPairIntent(false);
        mFragment.onDeviceBondStateChanged(mCachedBluetoothDevice, BluetoothDevice.BOND_BONDED);

        verify(mFragment).finish();
    }

    @Test
    @Config(shadows = ShadowDialogFragment.class)
    @EnableFlags(Flags.FLAG_ENABLE_LE_AUDIO_SHARING)
    @DisableFlags(Flags.FLAG_ENABLE_TEMPORARY_BOND_DEVICES_UI)
    public void onDeviceBondStateChanged_bonded_shareThenPair_handle() {
        ShadowDialogFragment.reset();
        when(mCachedBluetoothDevice.getDevice()).thenReturn(mBluetoothDevice);
        mFragment.mSelectedList.add(mBluetoothDevice);
        setUpFragmentWithShareThenPairIntent(true);
        mFragment.onDeviceBondStateChanged(mCachedBluetoothDevice, BluetoothDevice.BOND_BONDED);
        shadowOf(Looper.getMainLooper()).idle();

        ProgressDialogFragment progressDialog = mFragment.mProgressDialog;
        assertThat(progressDialog).isNotNull();
        assertThat(progressDialog.getMessage()).isEqualTo(
                mContext.getString(R.string.progress_dialog_connect_device_content,
                        TEST_DEVICE_ADDRESS));
        assertThat(
                ShadowDialogFragment.isIsShowing(ProgressDialogFragment.class.getName())).isTrue();
        verify(mFragment, never()).finish();

        ShadowDialogFragment.reset();
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_LE_AUDIO_SHARING)
    @DisableFlags(Flags.FLAG_ENABLE_TEMPORARY_BOND_DEVICES_UI)
    public void onDeviceBondStateChanged_bonding_notPairInSharing_doNothing() {
        when(mCachedBluetoothDevice.getDevice()).thenReturn(mBluetoothDevice);
        mFragment.mSelectedList.add(mBluetoothDevice);
        setUpFragmentWithShareThenPairIntent(false);
        mFragment.onDeviceBondStateChanged(mCachedBluetoothDevice, BluetoothDevice.BOND_BONDING);

        verify(mBluetoothAdapter, never()).addOnMetadataChangedListener(any(BluetoothDevice.class),
                any(Executor.class), any(BluetoothAdapter.OnMetadataChangedListener.class));
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_LE_AUDIO_SHARING)
    @DisableFlags(Flags.FLAG_ENABLE_TEMPORARY_BOND_DEVICES_UI)
    public void onDeviceBondStateChanged_bonding_shareThenPair_addListener() {
        when(mCachedBluetoothDevice.getDevice()).thenReturn(mBluetoothDevice);
        mFragment.mSelectedList.add(mBluetoothDevice);
        setUpFragmentWithShareThenPairIntent(true);
        mFragment.onDeviceBondStateChanged(mCachedBluetoothDevice, BluetoothDevice.BOND_BONDING);

        verify(mBluetoothAdapter).addOnMetadataChangedListener(eq(mBluetoothDevice),
                any(Executor.class),
                any(BluetoothAdapter.OnMetadataChangedListener.class));
    }

    @Test
    @DisableFlags({Flags.FLAG_ENABLE_LE_AUDIO_SHARING, Flags.FLAG_ENABLE_TEMPORARY_BOND_DEVICES_UI})
    public void onDeviceBondStateChanged_unbonded_notPairInSharing_doNothing() {
        when(mCachedBluetoothDevice.getDevice()).thenReturn(mBluetoothDevice);
        mFragment.mSelectedList.add(mBluetoothDevice);
        mFragment.onDeviceBondStateChanged(mCachedBluetoothDevice, BluetoothDevice.BOND_NONE);

        verify(mBluetoothAdapter, never()).removeOnMetadataChangedListener(
                any(BluetoothDevice.class), any(BluetoothAdapter.OnMetadataChangedListener.class));
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_LE_AUDIO_SHARING)
    @DisableFlags(Flags.FLAG_ENABLE_TEMPORARY_BOND_DEVICES_UI)
    public void onDeviceBondStateChanged_unbonded_shareThenPair_removeListener() {
        when(mCachedBluetoothDevice.getDevice()).thenReturn(mBluetoothDevice);
        mFragment.mSelectedList.add(mBluetoothDevice);
        setUpFragmentWithShareThenPairIntent(true);
        mFragment.onDeviceBondStateChanged(mCachedBluetoothDevice, BluetoothDevice.BOND_BONDING);
        mFragment.onDeviceBondStateChanged(mCachedBluetoothDevice, BluetoothDevice.BOND_NONE);

        verify(mBluetoothAdapter).removeOnMetadataChangedListener(eq(mBluetoothDevice),
                any(BluetoothAdapter.OnMetadataChangedListener.class));
    }

    @Test
    @DisableFlags({Flags.FLAG_ENABLE_LE_AUDIO_SHARING, Flags.FLAG_ENABLE_TEMPORARY_BOND_DEVICES_UI})
    public void
            onProfileConnectionStateChanged_deviceInSelectedListAndConnected_notInSharing_finish() {
        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(TEST_DEVICE_ADDRESS_B);
        mFragment.mSelectedList.add(mBluetoothDevice);
        mFragment.mSelectedList.add(device);

        when(mCachedBluetoothDevice.isConnected()).thenReturn(true);
        when(mCachedBluetoothDevice.getDevice()).thenReturn(device);

        mFragment.onProfileConnectionStateChanged(mCachedBluetoothDevice,
                BluetoothAdapter.STATE_CONNECTED, BluetoothProfile.A2DP);
        shadowOf(Looper.getMainLooper()).idle();

        verify(mFragment).finish();
    }

    @Test
    @Config(shadows = ShadowDialogFragment.class)
    @EnableFlags({Flags.FLAG_ENABLE_LE_AUDIO_SHARING, Flags.FLAG_ENABLE_TEMPORARY_BOND_DEVICES_UI})
    public void
            onProfileConnectionStateChanged_inSelectedListAndConnected_shareThenPair_handle() {
        ShadowDialogFragment.reset();
        BluetoothDevice device = spy(mBluetoothDevice);
        when(mCachedBluetoothDevice.getDevice()).thenReturn(device);
        mFragment.mSelectedList.add(device);
        setUpFragmentWithShareThenPairIntent(true);
        mFragment.onDeviceBondStateChanged(mCachedBluetoothDevice, BluetoothDevice.BOND_BONDED);
        shadowOf(Looper.getMainLooper()).idle();

        when(mCachedBluetoothDevice.isConnected()).thenReturn(true);
        when(mCachedBluetoothDevice.isConnectedLeAudioDevice()).thenReturn(true);
        when(mCachedBluetoothDevice.isConnectedLeAudioBroadcastAssistantDevice()).thenReturn(true);
        when(mCachedBluetoothDevice.isConnectedVolumeControlDevice()).thenReturn(true);

        mFragment.onProfileConnectionStateChanged(mCachedBluetoothDevice,
                BluetoothAdapter.STATE_CONNECTED, BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT);
        shadowOf(Looper.getMainLooper()).idle();

        ArgumentCaptor<Intent> captor = ArgumentCaptor.forClass(Intent.class);
        verify(mFragment.getActivity()).setResult(eq(Activity.RESULT_OK), captor.capture());
        Intent intent = captor.getValue();
        BluetoothDevice btDevice =
                intent != null
                        ? intent.getParcelableExtra(EXTRA_BT_DEVICE_TO_AUTO_ADD_SOURCE,
                        BluetoothDevice.class)
                        : null;
        assertThat(btDevice).isNotNull();
        assertThat(btDevice).isEqualTo(device);
        verify(mFragment).finish();

        ShadowDialogFragment.reset();
    }

    @Test
    @DisableFlags({Flags.FLAG_ENABLE_LE_AUDIO_SHARING, Flags.FLAG_ENABLE_TEMPORARY_BOND_DEVICES_UI})
    public void onProfileConnectionStateChanged_deviceNotInSelectedList_doNothing() {
        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(TEST_DEVICE_ADDRESS_B);
        mFragment.mSelectedList.add(device);

        when(mCachedBluetoothDevice.isConnected()).thenReturn(true);
        when(mCachedBluetoothDevice.getDevice()).thenReturn(mBluetoothDevice);

        mFragment.onProfileConnectionStateChanged(mCachedBluetoothDevice,
                BluetoothAdapter.STATE_CONNECTED, BluetoothProfile.A2DP);

        // not crash
    }

    @Test
    @DisableFlags({Flags.FLAG_ENABLE_LE_AUDIO_SHARING, Flags.FLAG_ENABLE_TEMPORARY_BOND_DEVICES_UI})
    public void onProfileConnectionStateChanged_deviceDisconnected_doNothing() {
        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(TEST_DEVICE_ADDRESS_B);
        mFragment.mSelectedList.add(mBluetoothDevice);
        mFragment.mSelectedList.add(device);

        when(mCachedBluetoothDevice.isConnected()).thenReturn(false);
        when(mCachedBluetoothDevice.getDevice()).thenReturn(device);

        mFragment.onProfileConnectionStateChanged(mCachedBluetoothDevice,
                BluetoothAdapter.STATE_DISCONNECTED, BluetoothProfile.A2DP);

        // not crash
    }

    @Test
    @DisableFlags({Flags.FLAG_ENABLE_LE_AUDIO_SHARING, Flags.FLAG_ENABLE_TEMPORARY_BOND_DEVICES_UI})
    public void onProfileConnectionStateChanged_deviceInPreferenceMapAndConnected_removed() {
        final BluetoothDevicePreference preference =
                new BluetoothDevicePreference(mContext, mCachedBluetoothDevice,
                        true, BluetoothDevicePreference.SortType.TYPE_FIFO);
        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(TEST_DEVICE_ADDRESS);
        mFragment.getDevicePreferenceMap().put(mCachedBluetoothDevice, preference);

        when(mCachedBluetoothDevice.isConnected()).thenReturn(true);
        when(mCachedBluetoothDevice.getDevice()).thenReturn(device);

        mFragment.onProfileConnectionStateChanged(mCachedBluetoothDevice,
                BluetoothAdapter.STATE_CONNECTED, BluetoothProfile.A2DP);

        assertThat(mFragment.getDevicePreferenceMap().size()).isEqualTo(0);
    }

    @Test
    @DisableFlags({Flags.FLAG_ENABLE_LE_AUDIO_SHARING, Flags.FLAG_ENABLE_TEMPORARY_BOND_DEVICES_UI})
    public void onProfileConnectionStateChanged_deviceNotInPreferenceMap_doNothing() {
        final CachedBluetoothDevice cachedDevice = mock(CachedBluetoothDevice.class);
        final BluetoothDevicePreference preference =
                new BluetoothDevicePreference(mContext, mCachedBluetoothDevice,
                        true, BluetoothDevicePreference.SortType.TYPE_FIFO);
        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(TEST_DEVICE_ADDRESS);
        final BluetoothDevice device2 = mBluetoothAdapter.getRemoteDevice(TEST_DEVICE_ADDRESS_B);
        mFragment.getDevicePreferenceMap().put(mCachedBluetoothDevice, preference);

        when(mCachedBluetoothDevice.isConnected()).thenReturn(true);
        when(mCachedBluetoothDevice.getDevice()).thenReturn(device);
        when(cachedDevice.isConnected()).thenReturn(true);
        when(cachedDevice.getDevice()).thenReturn(device2);
        when(cachedDevice.getAddress()).thenReturn(TEST_DEVICE_ADDRESS_B);
        when(cachedDevice.getIdentityAddress()).thenReturn(TEST_DEVICE_ADDRESS_B);

        mFragment.onProfileConnectionStateChanged(cachedDevice, BluetoothAdapter.STATE_CONNECTED,
                BluetoothProfile.A2DP);

        // not crash
    }

    private void setUpFragmentWithShareThenPairIntent(boolean enableShareThenPair) {
        Bundle args = new Bundle();
        args.putBoolean(EXTRA_PAIR_AND_JOIN_SHARING, enableShareThenPair);
        Intent intent = new Intent();
        intent.putExtra(SettingsActivity.EXTRA_SHOW_FRAGMENT_ARGUMENTS, args);
        FragmentActivity activity = spy(Robolectric.setupActivity(FragmentActivity.class));
        doReturn(intent).when(activity).getIntent();
        doReturn(activity).when(mFragment).getActivity();
        FragmentManager fragmentManager = mock(FragmentManager.class);
        FragmentTransaction fragmentTransaction = mock(FragmentTransaction.class);
        doReturn(fragmentTransaction).when(fragmentManager).beginTransaction();
        doReturn(fragmentManager).when(mFragment).getFragmentManager();
        doReturn(fragmentManager).when(mFragment).getChildFragmentManager();
        Lifecycle lifecycle = mock(Lifecycle.class);
        when(lifecycle.getCurrentState()).thenReturn(Lifecycle.State.RESUMED);
        doReturn(lifecycle).when(mFragment).getLifecycle();
        mFragment.mShouldTriggerShareThenPairFlow = mFragment.shouldTriggerShareThenPairFlow();
    }

    private static class TestBluetoothDevicePairingDetailBase extends
            BluetoothDevicePairingDetailBase {

        TestBluetoothDevicePairingDetailBase() {
            super();
        }

        @Override
        public int getMetricsCategory() {
            return 0;
        }

        @Override
        public String getDeviceListKey() {
            return KEY_DEVICE_LIST_GROUP;
        }

        @Override
        protected int getPreferenceScreenResId() {
            return 0;
        }

        @Override
        protected String getLogTag() {
            return "test_tag";
        }
    }

    /** Shadow of DialogFragment. */
    @Implements(value = DialogFragment.class)
    public static class ShadowDialogFragment {
        @RealObject
        private DialogFragment mDialogFragment;
        private static Map<String, Boolean> sDialogStatus = new HashMap<>();

        /** Resetter of the shadow. */
        @Resetter
        public static void reset() {
            sDialogStatus.clear();
        }

        /** Implementation for DialogFragment#show. */
        @Implementation
        public void show(@NonNull FragmentManager manager, @Nullable String tag) {
            sDialogStatus.put(mDialogFragment.getClass().getName(), true);
        }

        /** Implementation for DialogFragment#dismissAllowingStateLoss. */
        @Implementation
        public void dismissAllowingStateLoss() {
            sDialogStatus.put(mDialogFragment.getClass().getName(), false);
        }

        /** Implementation for DialogFragment#dismiss. */
        @Implementation
        public void dismiss() {
            sDialogStatus.put(mDialogFragment.getClass().getName(), false);
        }

        /** Check if DialogFragment is showing. */
        public static boolean isIsShowing(String clazzName) {
            return sDialogStatus.getOrDefault(clazzName, false);
        }
    }
}
