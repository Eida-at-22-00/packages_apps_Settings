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

package com.android.settings.sound;

import static com.android.settings.core.BasePreferenceController.AVAILABLE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.spy;

import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

// LINT.IfChange
@RunWith(AndroidJUnit4.class)
public class MediaControlsPreferenceControllerTest {

    private static final String KEY = "media_controls_resume_switch";

    private Context mContext;
    private int mOriginalResume;
    private ContentResolver mContentResolver;
    private MediaControlsPreferenceController mController;

    @Before
    public void setUp() {
        mContext = spy(ApplicationProvider.getApplicationContext());
        mContentResolver = mContext.getContentResolver();
        mOriginalResume = Settings.Secure.getInt(mContentResolver,
                Settings.Secure.MEDIA_CONTROLS_RESUME, 1);
        mController = new MediaControlsPreferenceController(mContext, KEY);
    }

    @After
    public void tearDown() {
        Settings.Secure.putInt(mContentResolver, Settings.Secure.MEDIA_CONTROLS_RESUME,
                mOriginalResume);
    }

    @Test
    public void getAvailability_returnAvailable() {
        assertThat(mController.getAvailabilityStatus()).isEqualTo(AVAILABLE);
    }

    @Test
    public void setChecked_enable_shouldTurnOn() {
        Settings.Secure.putInt(mContentResolver, Settings.Secure.MEDIA_CONTROLS_RESUME, 1);

        assertThat(mController.isChecked()).isTrue();

        mController.setChecked(false);

        assertThat(Settings.Secure.getInt(mContentResolver,
                Settings.Secure.MEDIA_CONTROLS_RESUME, -1)).isEqualTo(0);
    }

    @Test
    public void setChecked_disable_shouldTurnOff() {
        Settings.Secure.putInt(mContentResolver, Settings.Secure.MEDIA_CONTROLS_RESUME, 0);

        assertThat(mController.isChecked()).isFalse();

        mController.setChecked(true);

        assertThat(Settings.Secure.getInt(mContentResolver,
                Settings.Secure.MEDIA_CONTROLS_RESUME, -1)).isEqualTo(1);
    }
}
// LINT.ThenChange(MediaControlsSwitchPreferenceTest.kt)
