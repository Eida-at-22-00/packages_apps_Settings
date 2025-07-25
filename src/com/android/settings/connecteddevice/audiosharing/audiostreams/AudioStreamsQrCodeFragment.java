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

package com.android.settings.connecteddevice.audiosharing.audiostreams;

import android.app.settings.SettingsEnums;
import android.bluetooth.BluetoothLeBroadcastMetadata;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import com.android.settings.R;
import com.android.settings.bluetooth.Utils;
import com.android.settings.connecteddevice.audiosharing.AudioSharingFeatureProvider;
import com.android.settings.core.InstrumentedFragment;
import com.android.settingslib.bluetooth.BluetoothLeBroadcastMetadataExt;
import com.android.settingslib.bluetooth.LocalBluetoothLeBroadcast;
import com.android.settingslib.qrcode.QrCodeGenerator;
import com.android.settingslib.utils.ThreadUtils;
import com.android.settings.overlay.FeatureFactory;

import com.google.zxing.WriterException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

public class AudioStreamsQrCodeFragment extends InstrumentedFragment {
    private static final String TAG = "AudioStreamsQrCodeFragment";

    AudioSharingFeatureProvider audioSharingFeatureProvider =
            FeatureFactory.getFeatureFactory().getAudioSharingFeatureProvider();

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.AUDIO_STREAM_QR_CODE;
    }

    @Override
    public final View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bluetooth_audio_streams_qr_code, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Collapse or expand the app bar based on orientation for better display the qr code image.
        AudioStreamsHelper.configureAppBarByOrientation(getActivity());
        var unused =
                ThreadUtils.postOnBackgroundThread(
                        () -> {
                            BluetoothLeBroadcastMetadata broadcastMetadata = getBroadcastMetadata();
                            if (broadcastMetadata == null) {
                                return;
                            }
                            Drawable drawable =
                                    getQrCodeDrawable(broadcastMetadata, getActivity())
                                            .orElse(null);
                            if (drawable == null) {
                                return;
                            }

                            ThreadUtils.postOnMainThread(
                                    () -> {
                                        audioSharingFeatureProvider.setQrCode(
                                                this,
                                                view,
                                                R.id.qrcode_view,
                                                drawable,
                                                BluetoothLeBroadcastMetadataExt.INSTANCE
                                                        .toQrCodeString(broadcastMetadata));
                                        if (broadcastMetadata.getBroadcastCode() != null) {
                                            String password =
                                                    new String(
                                                            broadcastMetadata.getBroadcastCode(),
                                                            StandardCharsets.UTF_8);
                                            String passwordText =
                                                    getString(
                                                            R.string
                                                                    .audio_streams_qr_code_page_password,
                                                            password);
                                            ((TextView) view.requireViewById(R.id.password))
                                                    .setText(passwordText);
                                        }
                                        TextView summaryView =
                                                view.requireViewById(android.R.id.summary);
                                        String summary =
                                                getString(
                                                        R.string
                                                                .audio_streams_qr_code_page_description,
                                                        broadcastMetadata.getBroadcastName());
                                        summaryView.setText(summary);
                                    });
                        });
    }

    /** Gets an optional drawable from metadata. */
    public static Optional<Drawable> getQrCodeDrawable(
            @Nullable BluetoothLeBroadcastMetadata metadata,
            Context context) {
        if (metadata == null) {
            Log.d(TAG, "getQrCodeDrawable: broadcastMetadata is empty!");
            return Optional.empty();
        }
        String metadataStr = BluetoothLeBroadcastMetadataExt.INSTANCE.toQrCodeString(metadata);
        if (metadataStr.isEmpty()) {
            Log.d(TAG, "getQrCodeDrawable: metadataStr is empty!");
            return Optional.empty();
        }
        Log.d(TAG, "getQrCodeDrawable: metadata : " + metadata);
        try {
            Resources resources = context.getResources();
            int qrcodeSize = resources.getDimensionPixelSize(R.dimen.audio_streams_qrcode_size);
            int margin = resources.getDimensionPixelSize(R.dimen.audio_streams_qrcode_margin);
            Bitmap bitmap = QrCodeGenerator.encodeQrCode(metadataStr, qrcodeSize, margin);
            RoundedBitmapDrawable drawable = RoundedBitmapDrawableFactory.create(resources, bitmap);
            drawable.setCornerRadius(resources.getDimensionPixelSize(
                    R.dimen.audio_streams_qrcode_preview_radius));
            return Optional.of(drawable);
        } catch (WriterException e) {
            Log.d(
                    TAG,
                    "getQrCodeDrawable: broadcastMetadata "
                            + metadata
                            + " qrCode generation exception "
                            + e);
        }

        return Optional.empty();
    }

    @Nullable
    private BluetoothLeBroadcastMetadata getBroadcastMetadata() {
        LocalBluetoothLeBroadcast localBluetoothLeBroadcast =
                Utils.getLocalBtManager(getActivity())
                        .getProfileManager()
                        .getLeAudioBroadcastProfile();
        if (localBluetoothLeBroadcast == null) {
            Log.d(TAG, "getBroadcastMetadataQrCode: localBluetoothLeBroadcast is null!");
            return null;
        }

        List<BluetoothLeBroadcastMetadata> metadata =
                localBluetoothLeBroadcast.getAllBroadcastMetadata();
        if (metadata.isEmpty()) {
            Log.d(TAG, "getBroadcastMetadataQrCode: metadata is null!");
            return null;
        }

        return metadata.get(0);
    }
}
