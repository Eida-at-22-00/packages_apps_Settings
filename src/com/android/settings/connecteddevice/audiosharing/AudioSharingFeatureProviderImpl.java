/*
 * Copyright (C) 2025 The Android Open Source Project
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

import android.annotation.IdRes;
import androidx.annotation.NonNull;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;

import androidx.fragment.app.Fragment;

/** Default implementation for {@link AudioSharingFeatureProvider} */
public class AudioSharingFeatureProviderImpl implements AudioSharingFeatureProvider {
    public void setQrCode(
            @NonNull Fragment fragment,
            @NonNull View qrcodeContainer,
            @IdRes int qrCodeImageViewId,
            @NonNull Drawable drawable,
            @NonNull String qrCode) {
        ImageView imageView = ((ImageView) qrcodeContainer.requireViewById(qrCodeImageViewId));
        imageView.setImageDrawable(drawable);
        imageView.setVisibility(View.VISIBLE);
    }
}
