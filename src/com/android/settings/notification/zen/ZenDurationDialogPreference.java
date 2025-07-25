/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.settings.notification.zen;

import android.content.Context;
import android.content.DialogInterface;
import android.util.AttributeSet;

import androidx.appcompat.app.AlertDialog;

import com.android.settingslib.CustomDialogPreferenceCompat;
import com.android.settingslib.notification.modes.DndDurationDialogFactory;

public class ZenDurationDialogPreference extends CustomDialogPreferenceCompat {

    public ZenDurationDialogPreference(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setSingleLineTitle(false);
    }

    public ZenDurationDialogPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setSingleLineTitle(false);
    }

    public ZenDurationDialogPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setSingleLineTitle(false);
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder,
            DialogInterface.OnClickListener listener) {
        super.onPrepareDialogBuilder(builder, listener);

        DndDurationDialogFactory zenDialog = new DndDurationDialogFactory(getContext());
        zenDialog.setupDialog(builder);
    }
}
