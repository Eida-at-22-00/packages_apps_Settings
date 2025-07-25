/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.settings.biometrics;

import static android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG;
import static android.provider.Settings.ACTION_BIOMETRIC_ENROLL;

import static androidx.test.espresso.intent.Intents.intended;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra;

import static com.android.settings.biometrics.BiometricEnrollActivity.EXTRA_LAUNCH_FACE_ENROLL_FIRST;
import static com.android.settings.password.ChooseLockSettingsHelper.EXTRA_KEY_FOR_BIOMETRICS;
import static com.android.settings.password.ChooseLockSettingsHelper.EXTRA_KEY_FOR_FACE;
import static com.android.settings.password.ChooseLockSettingsHelper.EXTRA_KEY_FOR_FINGERPRINT;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.biometrics.SensorProperties;
import android.hardware.face.FaceManager;
import android.hardware.face.FaceSensorPropertiesInternal;
import android.hardware.fingerprint.FingerprintManager;
import android.hardware.fingerprint.FingerprintSensorPropertiesInternal;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.intent.Intents;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import com.android.internal.widget.LockPatternChecker;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.LockscreenCredential;
import com.android.settings.biometrics.face.FaceEnroll;
import com.android.settings.biometrics.fingerprint.FingerprintEnroll;
import com.android.settings.password.ChooseLockGeneric;
import com.android.settings.password.ChooseLockSettingsHelper;
import com.android.settings.password.ConfirmLockPassword;
import com.android.settings.testutils.AdbUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
@MediumTest
public class BiometricEnrollActivityTest {

    private static final String TAG = "BiometricEnrollActivityTest";
    private static final int ADB_TIMEOUT_MS = 5000;
    private static final String TEST_PIN = "1234";

    private final Context  mContext = ApplicationProvider.getApplicationContext();
    private boolean mHasFace;
    private boolean mHasFingerprint;
    private boolean mIsFaceStrong;
    private boolean mIsFingerprintStrong;

    @Before
    public void setup() {
        Intents.init();
        final PackageManager pm = mContext.getPackageManager();
        mHasFingerprint = pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT);
        mHasFace = pm.hasSystemFeature(PackageManager.FEATURE_FACE);

        if (mHasFace) {
            final FaceManager faceManager = mContext.getSystemService(FaceManager.class);
            final List<FaceSensorPropertiesInternal> faceProperties =
                    faceManager.getSensorPropertiesInternal();
            if (!faceProperties.isEmpty()) {
                final FaceSensorPropertiesInternal faceProp = faceProperties.get(0);
                mIsFaceStrong = faceProp.sensorStrength == SensorProperties.STRENGTH_STRONG;
            }
        }

        if (mHasFingerprint) {
            final FingerprintManager fingerprintManager = mContext.getSystemService(
                    FingerprintManager.class);
            final List<FingerprintSensorPropertiesInternal> fingerProperties =
                    fingerprintManager.getSensorPropertiesInternal();
            if (!fingerProperties.isEmpty()) {
                final FingerprintSensorPropertiesInternal fingerProp = fingerProperties.get(0);
                mIsFingerprintStrong =
                        fingerProp.sensorStrength == SensorProperties.STRENGTH_STRONG;
            }
        }
    }

    @After
    public void teardown() throws Exception {
        Intents.release();
        AdbUtils.checkStringInAdbCommandOutput(TAG, "locksettings clear --old " + TEST_PIN,
                "", "", ADB_TIMEOUT_MS);
    }

    @Test
    public void launchWithoutPin_setsPin() {
        try (ActivityScenario<BiometricEnrollActivity> scenario =
                     ActivityScenario.launch(getIntent())) {
            intended(hasComponent(ChooseLockGeneric.class.getName()));
            if (mHasFace && mHasFingerprint) {
                intended(hasExtra(EXTRA_KEY_FOR_BIOMETRICS, true));
            } else if (mHasFace) {
                intended(hasExtra(EXTRA_KEY_FOR_FACE, true));
            } else if (mHasFingerprint) {
                intended(hasExtra(EXTRA_KEY_FOR_FINGERPRINT, true));
            }
        }
    }

    @Test
    public void launchWithPin_confirmsPin() throws Exception  {
        setPin();
        try (ActivityScenario<BiometricEnrollActivity> scenario =
                     ActivityScenario.launch(getIntent())) {
            intended(hasComponent(ConfirmLockPassword.InternalActivity.class.getName()));
        }
    }

    @Test
    public void launchWithPinAndPwHandle_confirmsPin() throws Exception {
        assumeTrue(mHasFace || mHasFingerprint);

        setPin();
        final Intent intent = getIntent(true /* useInternal */, null);
        LockPatternChecker.verifyCredential(new LockPatternUtils(mContext),
                LockscreenCredential.createPin(TEST_PIN), UserHandle.myUserId(),
                LockPatternUtils.VERIFY_FLAG_REQUEST_GK_PW_HANDLE, (response, timeoutMs) -> {
                    assertThat(response.containsGatekeeperPasswordHandle()).isTrue();
                    intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_GK_PW_HANDLE,
                            response.getGatekeeperPasswordHandle());
                }).get();

        try (ActivityScenario<BiometricEnrollActivity> scenario =
                     ActivityScenario.launch(intent)) {
            intended(hasComponent(mHasFace && !mHasFingerprint
                    ? FaceEnroll.class.getName()
                    : FingerprintEnroll.class.getName()));
        }
    }

    @Test
    public void launchWithPinAndPwHandle_confirmsPin_firstEnrollmentIsFace() throws Exception {
        assumeTrue(mHasFace && mHasFingerprint);

        setPin();
        final Intent intent = getFaceEnrollFirstIntent();
        LockPatternChecker.verifyCredential(new LockPatternUtils(mContext),
                LockscreenCredential.createPin(TEST_PIN), UserHandle.myUserId(),
                LockPatternUtils.VERIFY_FLAG_REQUEST_GK_PW_HANDLE, (response, timeoutMs) -> {
                    assertThat(response.containsGatekeeperPasswordHandle()).isTrue();
                    intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_GK_PW_HANDLE,
                            response.getGatekeeperPasswordHandle());
                }).get();

        try (ActivityScenario<BiometricEnrollActivity> scenario =
                     ActivityScenario.launch(intent)) {
            intended(hasComponent(FaceEnroll.class.getName()));
        }
    }

    @Test
    public void launchWithStrongBiometricAllowed_doNotEnrollWeak() throws Exception {
        assumeTrue(mHasFace || mHasFingerprint);

        // Allow only strong biometrics
        Intent intent = getIntent();
        intent.putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, BIOMETRIC_STRONG);

        try (ActivityScenario<BiometricEnrollActivity> scenario =
                     ActivityScenario.launch(intent)) {
            intended(hasComponent(ChooseLockGeneric.class.getName()));
            if (mIsFaceStrong && mIsFingerprintStrong) {
                intended(hasExtra(EXTRA_KEY_FOR_BIOMETRICS, true));
            } else if (mIsFaceStrong) {
                intended(hasExtra(EXTRA_KEY_FOR_FACE, true));
            } else if (mIsFingerprintStrong) {
                intended(hasExtra(EXTRA_KEY_FOR_FINGERPRINT, true));
            }
        }
    }

    private Intent getIntent() {
        return getIntent(false /* useInternal */, null);
    }

    private Intent getFaceEnrollFirstIntent() {
        final Bundle bundle = new Bundle();
        bundle.putBoolean(EXTRA_LAUNCH_FACE_ENROLL_FIRST, true);
        return getIntent(true /* useInternal */, bundle);
    }

    private Intent getIntent(boolean useInternal, Bundle bundle) {
        final Intent intent = new Intent(mContext, useInternal
                ? BiometricEnrollActivity.InternalActivity.class : BiometricEnrollActivity.class);
        intent.setAction(ACTION_BIOMETRIC_ENROLL);
        if (bundle != null && !bundle.isEmpty()) {
            intent.putExtras(bundle);
        }
        return intent;
    }

    private static void setPin() throws Exception  {
        assertThat(AdbUtils.checkStringInAdbCommandOutput(TAG, "locksettings set-pin " + TEST_PIN,
                "Pin set to ", "'" + TEST_PIN + "'", ADB_TIMEOUT_MS)).isTrue();
    }
}
