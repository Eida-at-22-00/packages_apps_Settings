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

package com.android.settings.connecteddevice.audiosharing.audiostreams.testshadows;

import static com.android.settingslib.bluetooth.LocalBluetoothLeBroadcastAssistant.LocalBluetoothLeBroadcastSourceState;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothLeBroadcastMetadata;
import android.bluetooth.BluetoothLeBroadcastReceiveState;
import android.content.ComponentName;
import android.content.Context;

import androidx.annotation.Nullable;

import com.android.settings.connecteddevice.audiosharing.audiostreams.AudioStreamsHelper;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.settingslib.bluetooth.LocalBluetoothLeBroadcastAssistant;
import com.android.settingslib.bluetooth.LocalBluetoothManager;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Resetter;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Implements(value = AudioStreamsHelper.class, callThroughByDefault = true)
public class ShadowAudioStreamsHelper {
    private static AudioStreamsHelper sMockHelper;
    @Nullable private static CachedBluetoothDevice sCachedBluetoothDevice;
    @Nullable private static ComponentName sEnabledScreenReaderService;

    public static void setUseMock(AudioStreamsHelper mockAudioStreamsHelper) {
        sMockHelper = mockAudioStreamsHelper;
    }

    /** Reset static fields */
    @Resetter
    public static void reset() {
        sMockHelper = null;
        sCachedBluetoothDevice = null;
        sEnabledScreenReaderService = null;
    }

    public static void setCachedBluetoothDeviceInSharingOrLeConnected(
            CachedBluetoothDevice cachedBluetoothDevice) {
        sCachedBluetoothDevice = cachedBluetoothDevice;
    }

    public static void setEnabledScreenReaderService(ComponentName componentName) {
        sEnabledScreenReaderService = componentName;
    }

    @Implementation
    public Map<Integer, LocalBluetoothLeBroadcastSourceState> getConnectedBroadcastIdAndState(
            boolean hysteresisModeFixAvailable) {
        return sMockHelper.getConnectedBroadcastIdAndState(hysteresisModeFixAvailable);
    }

    @Implementation
    public Map<BluetoothDevice, List<BluetoothLeBroadcastReceiveState>> getAllSourcesByDevice() {
        return sMockHelper.getAllSourcesByDevice();
    }

    /** Gets {@link CachedBluetoothDevice} in sharing or le connected */
    @Implementation
    public static Optional<CachedBluetoothDevice> getCachedBluetoothDeviceInSharingOrLeConnected(
            LocalBluetoothManager manager) {
        return Optional.ofNullable(sCachedBluetoothDevice);
    }

    /** Retrieves a set of enabled screen reader services that are pre-installed. */
    @Implementation
    public static Set<ComponentName> getEnabledScreenReaderServices(Context context) {
        if (sEnabledScreenReaderService != null) {
            return Set.of(sEnabledScreenReaderService);
        }
        return Collections.emptySet();
    }

    @Implementation
    public LocalBluetoothLeBroadcastAssistant getLeBroadcastAssistant() {
        return sMockHelper.getLeBroadcastAssistant();
    }

    /** Removes sources from LE broadcasts associated for all active sinks based on broadcast Id. */
    @Implementation
    public void removeSource(int broadcastId) {
        sMockHelper.removeSource(broadcastId);
    }

    /** Adds the specified LE broadcast source to all active sinks. */
    @Implementation
    public void addSource(BluetoothLeBroadcastMetadata source) {
        sMockHelper.addSource(source);
    }
}
