/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.settings.biometrics.fingerprint;


import static android.app.admin.DevicePolicyResources.Strings.Settings.FINGERPRINT_UNLOCK_DISABLED_EXPLANATION;
import static android.app.admin.DevicePolicyResources.Strings.Settings.WORK_PROFILE_FINGERPRINT_LAST_DELETE_MESSAGE;
import static android.app.admin.DevicePolicyResources.UNDEFINED;
import static android.hardware.biometrics.Flags.screenOffUnlockUdfps;
import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;

import static com.android.settings.Utils.SETTINGS_PACKAGE_NAME;
import static com.android.settings.Utils.isPrivateProfile;
import static com.android.settings.biometrics.BiometricEnrollBase.BIOMETRIC_AUTH_REQUEST;
import static com.android.settings.biometrics.BiometricEnrollBase.EXTRA_FROM_SETTINGS_SUMMARY;
import static com.android.settings.biometrics.BiometricEnrollBase.EXTRA_KEY_CHALLENGE;
import static com.android.settings.core.BasePreferenceController.AVAILABLE;
import static com.android.settings.core.BasePreferenceController.CONDITIONALLY_UNAVAILABLE;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.app.admin.DevicePolicyManager;
import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ResourceId;
import android.graphics.drawable.Drawable;
import android.hardware.fingerprint.Fingerprint;
import android.hardware.fingerprint.FingerprintManager;
import android.hardware.fingerprint.FingerprintSensorPropertiesInternal;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.ImeAwareEditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceViewHolder;
import androidx.preference.TwoStatePreference;

import com.android.internal.widget.LockPatternUtils;
import com.android.settings.R;
import com.android.settings.SubSettings;
import com.android.settings.Utils;
import com.android.settings.biometrics.BiometricEnrollBase;
import com.android.settings.biometrics.BiometricUtils;
import com.android.settings.biometrics.GatekeeperPasswordProvider;
import com.android.settings.biometrics.IdentityCheckBiometricErrorDialog;
import com.android.settings.biometrics.fingerprint.feature.ChallengeGeneratedInvoker;
import com.android.settings.biometrics.fingerprint.feature.FingerprintExtPreferencesProvider;
import com.android.settings.biometrics.fingerprint.feature.PrimarySwitchIntentPreference;
import com.android.settings.core.SettingsBaseActivity;
import com.android.settings.core.instrumentation.InstrumentedDialogFragment;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.flags.Flags;
import com.android.settings.overlay.FeatureFactory;
import com.android.settings.password.ChooseLockGeneric;
import com.android.settings.password.ChooseLockSettingsHelper;
import com.android.settings.password.ConfirmDeviceCredentialActivity;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.HelpUtils;
import com.android.settingslib.RestrictedLockUtils;
import com.android.settingslib.RestrictedLockUtils.EnforcedAdmin;
import com.android.settingslib.RestrictedLockUtilsInternal;
import com.android.settingslib.RestrictedPreference;
import com.android.settingslib.RestrictedSwitchPreference;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settingslib.search.SearchIndexable;
import com.android.settingslib.transition.SettingsTransitionHelper;
import com.android.settingslib.widget.ButtonPreference;
import com.android.settingslib.widget.FooterPreference;
import com.android.settingslib.widget.SettingsThemeHelper;
import com.android.settingslib.widget.TwoTargetPreference;

import com.google.android.setupdesign.util.DeviceHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * Settings screen for fingerprints
 */
public class FingerprintSettings extends SubSettings {

    private static final String TAG = "FingerprintSettings";

    private static final long LOCKOUT_DURATION = 30000; // time we have to wait for fp to reset, ms

    public static final String ANNOTATION_URL = "url";
    public static final String ANNOTATION_ADMIN_DETAILS = "admin_details";

    private static final int RESULT_FINISHED = BiometricEnrollBase.RESULT_FINISHED;
    private static final int RESULT_SKIP = BiometricEnrollBase.RESULT_SKIP;
    private static final int RESULT_TIMEOUT = BiometricEnrollBase.RESULT_TIMEOUT;
    @VisibleForTesting
    static final VibrationEffect SUCCESS_VIBRATION_EFFECT =
            VibrationEffect.get(VibrationEffect.EFFECT_CLICK);

    @Override
    public Intent getIntent() {
        Intent modIntent = new Intent(super.getIntent());
        modIntent.putExtra(EXTRA_SHOW_FRAGMENT, FingerprintSettingsFragment.class.getName());
        return modIntent;
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        if (FingerprintSettingsFragment.class.getName().equals(fragmentName)) return true;
        return false;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final int userId = getIntent().getIntExtra(Intent.EXTRA_USER_ID, UserHandle.myUserId());
        CharSequence msg = getText(isPrivateProfile(userId, this)
                ? R.string.private_space_fingerprint_unlock_title
                : R.string.security_settings_fingerprint_preference_title);
        setTitle(msg);
    }

    /**
     * @param context
     * @return true if the Fingerprint hardware is detected.
     */
    public static boolean isFingerprintHardwareDetected(Context context) {
        FingerprintManager manager = Utils.getFingerprintManagerOrNull(context);
        boolean isHardwareDetected = false;
        if (manager == null) {
            Log.d(TAG, "FingerprintManager is null");
        } else {
            isHardwareDetected = manager.isHardwareDetected();
            Log.d(TAG, "FingerprintManager is not null. Hardware detected: " + isHardwareDetected);
        }
        return manager != null && isHardwareDetected;
    }


    /**
     *
     */
    @SearchIndexable
    public static class FingerprintSettingsFragment extends DashboardFragment
            implements OnPreferenceChangeListener, FingerprintPreference.OnDeleteClickListener {

        public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
                new BaseSearchIndexProvider(R.xml.security_settings_fingerprint) {

                    @Override
                    protected boolean isPageSearchEnabled(Context context) {
                        return super.isPageSearchEnabled(context) &&
                                hasEnrolledFingerprints(context);
                    }

                    @Override
                    public List<AbstractPreferenceController>
                            createPreferenceControllers(Context context) {
                        return createThePreferenceControllers(context);
                    }

                    private boolean hasEnrolledFingerprints(Context context) {
                        final FingerprintManager fingerprintManager =
                                Utils.getFingerprintManagerOrNull(context);
                        if (fingerprintManager != null) {
                            return fingerprintManager.hasEnrolledTemplates(UserHandle.myUserId());
                        }
                        return false;
                    }
                };

        private static List<AbstractPreferenceController> createThePreferenceControllers(Context
                context) {
            final List<AbstractPreferenceController> controllers = new ArrayList<>();
            FingerprintManager manager = Utils.getFingerprintManagerOrNull(context);
            if (manager == null || !manager.isHardwareDetected()) {
                return null;
            }
            if (manager.isPowerbuttonFps()) {
                controllers.add(
                        new FingerprintUnlockCategoryController(
                                context,
                                KEY_FINGERPRINT_UNLOCK_CATEGORY
                        ));
                controllers.add(
                        new FingerprintSettingsRequireScreenOnToAuthPreferenceController(
                                context,
                                KEY_REQUIRE_SCREEN_ON_TO_AUTH
                        ));
            } else if (screenOffUnlockUdfps()) {
                controllers.add(
                        new FingerprintUnlockCategoryController(
                                context,
                                KEY_FINGERPRINT_UNLOCK_CATEGORY
                        ));
                controllers.add(
                        new FingerprintSettingsScreenOffUnlockUdfpsPreferenceController(
                                context,
                                KEY_SCREEN_OFF_FINGERPRINT_UNLOCK
                        ));
            }
            controllers.add(new FingerprintsEnrolledCategoryPreferenceController(context,
                    KEY_FINGERPRINTS_ENROLLED_CATEGORY));
            return controllers;
        }

        private static class FooterColumn {
            CharSequence mTitle = null;
            CharSequence mLearnMoreOverrideText = null;
            View.OnClickListener mLearnMoreClickListener = null;
        }

        private static final int RESET_HIGHLIGHT_DELAY_MS = 500;

        private static final String TAG = "FingerprintSettings";
        private static final String KEY_FINGERPRINT_ITEM_PREFIX = "key_fingerprint_item";

        private static final String KEY_FINGERPRINT_CHECK_ENROLLED =
                "key_fingerprint_check_enrolled";
        @VisibleForTesting
        static final String KEY_FINGERPRINT_ADD = "key_fingerprint_add";
        @VisibleForTesting
        static final String KEY_FINGERPRINT_ADD_EXPRESSIVE = "key_fingerprint_add_expressive";
        private static final String KEY_FINGERPRINT_ENABLE_KEYGUARD_TOGGLE =
                "fingerprint_enable_keyguard_toggle";
        private static final String KEY_LAUNCHED_CONFIRM = "launched_confirm";
        private static final String KEY_HAS_FIRST_ENROLLED = "has_first_enrolled";
        private static final String KEY_IS_ENROLLING = "is_enrolled";
        private static final String KEY_IS_LAUNCHING_EXT_PREF = "is_launching_ext_pref";
        private static final String KEY_HAS_RUN_CHALLENGE_INVOKER = "has_run_challenge_invoker";
        @VisibleForTesting
        static final String KEY_REQUIRE_SCREEN_ON_TO_AUTH =
                "security_settings_require_screen_on_to_auth";
        @VisibleForTesting
        static final String KEY_SCREEN_OFF_FINGERPRINT_UNLOCK =
                "security_settings_screen_off_unlock_udfps";
        private static final String KEY_FINGERPRINTS_ENROLLED_CATEGORY =
                "security_settings_fingerprints_enrolled";
        private static final String KEY_FINGERPRINT_UNLOCK_CATEGORY =
                "security_settings_fingerprint_unlock_category";
        private static final String KEY_FINGERPRINT_UNLOCK_FOOTER =
                "security_settings_fingerprint_footer";
        private static final String KEY_FINGERPRINT_DESCRIPTION =
                "security_settings_fingerprint_description";
        private static final String KEY_BIOMETRICS_AUTHENTICATION_REQUESTED =
                "biometrics_authentication_requested";
        private static final String KEY_BIOMETRICS_USE_FINGERPRINT_TO_CATEGORY =
                "biometric_settings_use_fingerprint_to";

        private static final int MSG_REFRESH_FINGERPRINT_TEMPLATES = 1000;
        private static final int MSG_FINGER_AUTH_SUCCESS = 1001;
        private static final int MSG_FINGER_AUTH_FAIL = 1002;
        private static final int MSG_FINGER_AUTH_ERROR = 1003;
        private static final int MSG_FINGER_AUTH_HELP = 1004;
        private static final int MSG_RELOAD_FINGERPRINT_TEMPLATES = 1005;

        private static final int CONFIRM_REQUEST = 101;
        @VisibleForTesting
        static final int CHOOSE_LOCK_GENERIC_REQUEST = 102;
        @VisibleForTesting
        static final int ADD_FINGERPRINT_REQUEST = 10;
        private static final int AUTO_ADD_FIRST_FINGERPRINT_REQUEST = 11;
        /** For launching extern preferences coming from FingerprintExtPreferencesProvider */
        private static final int LAUNCH_EXT_PREF_REQUEST = 12;

        protected static final boolean DEBUG = false;

        private List<AbstractPreferenceController> mControllers;
        private FingerprintUnlockCategoryController
                mFingerprintUnlockCategoryPreferenceController;
        private FingerprintSettingsRequireScreenOnToAuthPreferenceController
                mRequireScreenOnToAuthPreferenceController;
        private FingerprintSettingsScreenOffUnlockUdfpsPreferenceController
                mScreenOffUnlockUdfpsPreferenceController;
        private Preference mAddFingerprintPreference;
        private RestrictedSwitchPreference mRequireScreenOnToAuthPreference;
        private RestrictedSwitchPreference mScreenOffUnlockUdfpsPreference;
        private PreferenceCategory mFingerprintsEnrolledCategory;
        private PreferenceCategory mFingerprintUnlockCategory;
        private PreferenceCategory mFingerprintUnlockFooter;

        private FingerprintManager mFingerprintManager;
        private FingerprintUpdater mFingerprintUpdater;
        private List<FingerprintSensorPropertiesInternal> mSensorProperties;
        private boolean mInFingerprintLockout;
        private byte[] mToken;
        private boolean mLaunchedConfirm;
        private boolean mBiometricsAuthenticationRequested;
        private boolean mHasFirstEnrolled = true;
        private Drawable mHighlightDrawable;
        private int mUserId;
        private final List<FooterColumn> mFooterColumns = new ArrayList<>();
        private boolean mIsEnrolling;
        /** SaveInstance key if we are waiting activity result from a extension preference */
        @NonNull private String mLaunchedExtPrefKey = "";
        /** key list for changing visibility */
        @NonNull private final ArrayList<String> mExtPrefKeys = new ArrayList<>();
        /** Use to make sure ChallengeGeneratedInvokers have been run */
        private boolean mHasRunChallengeInvoker = false;

        private long mChallenge;

        private static final String TAG_AUTHENTICATE_SIDECAR = "authenticate_sidecar";
        private static final String TAG_REMOVAL_SIDECAR = "removal_sidecar";
        private FingerprintAuthenticateSidecar mAuthenticateSidecar;
        private FingerprintRemoveSidecar mRemovalSidecar;
        private HashMap<Integer, String> mFingerprintsRenaming;
        private Vibrator mVibrator;

        @Nullable
        private UdfpsEnrollCalibrator mCalibrator;

        private boolean mIsExpressiveThemeStyle;

        FingerprintAuthenticateSidecar.Listener mAuthenticateListener =
                new FingerprintAuthenticateSidecar.Listener() {
                    @Override
                    public void onAuthenticationSucceeded(
                            FingerprintManager.AuthenticationResult result) {
                        int fingerId = result.getFingerprint().getBiometricId();
                        mHandler.obtainMessage(MSG_FINGER_AUTH_SUCCESS, fingerId, 0).sendToTarget();
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        mHandler.obtainMessage(MSG_FINGER_AUTH_FAIL).sendToTarget();
                    }

                    @Override
                    public void onAuthenticationError(int errMsgId, CharSequence errString) {
                        mHandler.obtainMessage(MSG_FINGER_AUTH_ERROR, errMsgId, 0, errString)
                                .sendToTarget();
                    }

                    @Override
                    public void onAuthenticationHelp(int helpMsgId, CharSequence helpString) {
                        mHandler.obtainMessage(MSG_FINGER_AUTH_HELP, helpMsgId, 0, helpString)
                                .sendToTarget();
                    }
                };

        FingerprintRemoveSidecar.Listener mRemovalListener =
                new FingerprintRemoveSidecar.Listener() {
                    public void onRemovalSucceeded(Fingerprint fingerprint) {
                        mHandler.obtainMessage(MSG_REFRESH_FINGERPRINT_TEMPLATES,
                                fingerprint.getBiometricId(), 0).sendToTarget();
                        updateDialog();
                    }

                    public void onRemovalError(Fingerprint fp, int errMsgId,
                            CharSequence errString) {
                        final Activity activity = getActivity();
                        if (activity != null) {
                            Toast.makeText(activity, errString, Toast.LENGTH_SHORT);
                        }
                        mHandler.obtainMessage(MSG_RELOAD_FINGERPRINT_TEMPLATES)
                                .sendToTarget();
                        updateDialog();
                    }

                    private void updateDialog() {
                        RenameDialog renameDialog = (RenameDialog) getFragmentManager().
                                findFragmentByTag(RenameDialog.class.getName());
                        if (renameDialog != null) {
                            renameDialog.enableDelete();
                        }
                    }
                };

        private final Handler mHandler = new Handler() {
            @Override
            public void handleMessage(android.os.Message msg) {
                switch (msg.what) {
                    case MSG_REFRESH_FINGERPRINT_TEMPLATES:
                        removeFingerprintPreference(msg.arg1);
                        updatePreferencesAfterFingerprintRemoved();
                        break;
                    case MSG_FINGER_AUTH_SUCCESS:
                        highlightFingerprintItem(msg.arg1);
                        retryFingerprint();
                        break;
                    case MSG_FINGER_AUTH_FAIL:
                        // No action required... fingerprint will allow up to 5 of these
                        break;
                    case MSG_FINGER_AUTH_ERROR:
                        handleError(msg.arg1 /* errMsgId */, (CharSequence) msg.obj /* errStr */);
                        break;
                    case MSG_RELOAD_FINGERPRINT_TEMPLATES:
                        updatePreferencesAfterFingerprintRemoved();
                        break;
                    case MSG_FINGER_AUTH_HELP: {
                        // Not used
                        break;
                    }
                }
            }
        };

        /** Lambda function for creating a FingerprintExtPreferencesProvider::PreferenceInflater */
        @SuppressLint("RestrictedApi")
        private PreferenceScreen inflateFromResource(int resId) {
            return getPreferenceManager().inflateFromResource(
                    requireContext(),
                    resId,
                    /* rootPreferences */ null
            );
        }

        /**
         * Lambda function for handling click event in preferences from
         * FingerprintExtPreferencesProvider
         */
        private boolean onExtIntentPreferenceClick(@NonNull Preference preference) {
            if (!(preference instanceof PrimarySwitchIntentPreference)) {
                return false;
            }

            mLaunchedExtPrefKey = preference.getKey();
            startActivityForResult(
                    ((PrimarySwitchIntentPreference) preference).getLaunchedIntent(mToken),
                    LAUNCH_EXT_PREF_REQUEST
            );
            return true;
        }

        /**
         * Add new preferences from FingerprintExtPreferencesProvider
         */
        public void setupExtFingerprintPreferences() {
            FingerprintExtPreferencesProvider preferencesProvider = getExtPreferenceProvider();
            for (int index = 0; index < preferencesProvider.getSize(); ++index) {
                final RestrictedPreference preference = preferencesProvider.newPreference(
                        index, this::inflateFromResource);
                if (preference == null || findPreference(preference.getKey()) != null) {
                    continue;
                }
                if (preference instanceof PrimarySwitchIntentPreference) {
                    preference.setOnPreferenceClickListener(this::onExtIntentPreferenceClick);
                }
                mExtPrefKeys.add(preference.getKey());
                mFingerprintUnlockCategory.addPreference(preference);
            }
        }

        @NonNull
        private FingerprintExtPreferencesProvider getExtPreferenceProvider() {
            return FeatureFactory.getFeatureFactory().getFingerprintFeatureProvider()
                    .getExtPreferenceProvider(requireContext());
        }

        /**
         *
         */
        protected void handleError(int errMsgId, CharSequence msg) {
            switch (errMsgId) {
                case FingerprintManager.FINGERPRINT_ERROR_CANCELED:
                case FingerprintManager.FINGERPRINT_ERROR_USER_CANCELED:
                    // Only happens if we get preempted by another activity, or canceled by the
                    // user (e.g. swipe up to home). Ignored.
                    return;
                case FingerprintManager.FINGERPRINT_ERROR_LOCKOUT:
                    mInFingerprintLockout = true;
                    // We've been locked out.  Reset after 30s.
                    if (!mHandler.hasCallbacks(mFingerprintLockoutReset)) {
                        mHandler.postDelayed(mFingerprintLockoutReset,
                                LOCKOUT_DURATION);
                    }
                    break;
                case FingerprintManager.FINGERPRINT_ERROR_LOCKOUT_PERMANENT:
                    mInFingerprintLockout = true;
                    break;
            }

            if (mInFingerprintLockout) {
                // Activity can be null on a screen rotation.
                final Activity activity = getActivity();
                if (activity != null) {
                    Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
                }
            }
            retryFingerprint(); // start again
        }

        private void retryFingerprint() {
            if (isUdfps()) {
                // Do not authenticate for UDFPS devices.
                return;
            }

            if (mRemovalSidecar.inProgress()
                    || 0 == mFingerprintManager.getEnrolledFingerprints(mUserId).size()) {
                return;
            }
            // Don't start authentication if ChooseLockGeneric is showing, otherwise if the user
            // is in FP lockout, a toast will show on top
            if (mLaunchedConfirm) {
                return;
            }
            if (!mInFingerprintLockout) {
                mAuthenticateSidecar.startAuthentication(mUserId);
                mAuthenticateSidecar.setListener(mAuthenticateListener);
            }
        }

        @Override
        public int getMetricsCategory() {
            return SettingsEnums.FINGERPRINT;
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mIsExpressiveThemeStyle = SettingsThemeHelper.isExpressiveTheme(getPrefContext());

            Activity activity = getActivity();
            mFingerprintManager = Utils.getFingerprintManagerOrNull(activity);
            mFingerprintUpdater = new FingerprintUpdater(activity, mFingerprintManager);
            mSensorProperties = mFingerprintManager.getSensorPropertiesInternal();

            mToken = getIntent().getByteArrayExtra(
                    ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE_TOKEN);
            mChallenge = activity.getIntent()
                    .getLongExtra(BiometricEnrollBase.EXTRA_KEY_CHALLENGE, -1L);

            mAuthenticateSidecar = (FingerprintAuthenticateSidecar)
                    getFragmentManager().findFragmentByTag(TAG_AUTHENTICATE_SIDECAR);
            if (mAuthenticateSidecar == null) {
                mAuthenticateSidecar = new FingerprintAuthenticateSidecar();
                getFragmentManager().beginTransaction()
                        .add(mAuthenticateSidecar, TAG_AUTHENTICATE_SIDECAR).commit();
            }
            mAuthenticateSidecar.setFingerprintManager(mFingerprintManager);

            mRemovalSidecar = (FingerprintRemoveSidecar)
                    getFragmentManager().findFragmentByTag(TAG_REMOVAL_SIDECAR);
            if (mRemovalSidecar == null) {
                mRemovalSidecar = new FingerprintRemoveSidecar();
                getFragmentManager().beginTransaction()
                        .add(mRemovalSidecar, TAG_REMOVAL_SIDECAR).commit();
            }
            mRemovalSidecar.setFingerprintUpdater(mFingerprintUpdater);
            mRemovalSidecar.setListener(mRemovalListener);

            RenameDialog renameDialog = (RenameDialog) getFragmentManager().
                    findFragmentByTag(RenameDialog.class.getName());
            if (renameDialog != null) {
                renameDialog.setDeleteInProgress(mRemovalSidecar.inProgress());
            }

            mFingerprintsRenaming = new HashMap<Integer, String>();
            mUserId = getActivity().getIntent().getIntExtra(
                    Intent.EXTRA_USER_ID, UserHandle.myUserId());
            mHasFirstEnrolled = mFingerprintManager.hasEnrolledFingerprints(mUserId);

            if (savedInstanceState != null) {
                mFingerprintsRenaming = (HashMap<Integer, String>)
                        savedInstanceState.getSerializable("mFingerprintsRenaming");
                mToken = savedInstanceState.getByteArray(
                        ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE_TOKEN);
                mLaunchedConfirm = savedInstanceState.getBoolean(
                        KEY_LAUNCHED_CONFIRM, false);
                mIsEnrolling = savedInstanceState.getBoolean(KEY_IS_ENROLLING, mIsEnrolling);
                mLaunchedExtPrefKey = savedInstanceState.getString(
                        KEY_IS_LAUNCHING_EXT_PREF, mLaunchedExtPrefKey);
                mHasRunChallengeInvoker = savedInstanceState.getBoolean(
                        KEY_HAS_RUN_CHALLENGE_INVOKER, /* defaultValue= */ false);
                mHasFirstEnrolled = savedInstanceState.getBoolean(KEY_HAS_FIRST_ENROLLED,
                        mHasFirstEnrolled);
                mBiometricsAuthenticationRequested = savedInstanceState.getBoolean(
                        KEY_BIOMETRICS_AUTHENTICATION_REQUESTED);
            }

            // (mLaunchedConfirm or mIsEnrolling) means that we are waiting an activity result.
            if (!mLaunchedConfirm && !mIsEnrolling) {
                // Need to authenticate a session token if none
                if (mToken == null) {
                    mLaunchedConfirm = true;
                    launchChooseOrConfirmLock();
                } else if (!mHasFirstEnrolled) {
                    mIsEnrolling = true;
                    addFirstFingerprint(null);
                }
            }
            mVibrator = getContext().getSystemService(Vibrator.class);
            final PreferenceScreen root = getPreferenceScreen();
            root.removeAll();
            addPreferencesFromResource(getPreferenceScreenResId());
            updateFooterColumns(activity);
        }

        private void updateFooterColumns(@NonNull Activity activity) {
            final EnforcedAdmin admin = RestrictedLockUtilsInternal.checkIfKeyguardFeaturesDisabled(
                    activity, DevicePolicyManager.KEYGUARD_DISABLE_FINGERPRINT, mUserId);
            final Intent helpIntent = HelpUtils.getHelpIntent(
                    activity, getString(getHelpResource()), activity.getClass().getName());
            final View.OnClickListener learnMoreClickListener = (v) -> {
                activity.startActivityForResult(helpIntent, 0);
            };

            mFooterColumns.clear();
            if (admin != null) {
                final DevicePolicyManager devicePolicyManager =
                        getSystemService(DevicePolicyManager.class);
                final FooterColumn column1 = new FooterColumn();
                column1.mTitle = devicePolicyManager.getResources().getString(
                        FINGERPRINT_UNLOCK_DISABLED_EXPLANATION,
                        () -> getString(
                                R.string.security_fingerprint_disclaimer_lockscreen_disabled_1
                        )
                );
                column1.mLearnMoreClickListener = (v) -> RestrictedLockUtils
                        .sendShowAdminSupportDetailsIntent(activity, admin);
                column1.mLearnMoreOverrideText = getText(R.string.admin_support_more_info);
                mFooterColumns.add(column1);

                final FooterColumn column2 = new FooterColumn();
                column2.mTitle = getText(
                        R.string.security_fingerprint_disclaimer_lockscreen_disabled_2
                );
                if (isSfps()) {
                    column2.mLearnMoreOverrideText = getText(
                            R.string.security_settings_fingerprint_settings_footer_learn_more);
                }
                column2.mLearnMoreClickListener = learnMoreClickListener;
                mFooterColumns.add(column2);
            } else {
                final FooterColumn column = new FooterColumn();
                final FingerprintSettingsFeatureProvider featureProvider =
                        FeatureFactory.getFeatureFactory().getFingerprintFeatureProvider()
                                .getFingerprintSettingsFeatureProvider();
                column.mTitle = getString(isPrivateProfile()
                        ? R.string.private_space_fingerprint_enroll_introduction_message
                        : R.string.security_settings_fingerprint_enroll_introduction_v3_message,
                        DeviceHelper.getDeviceName(getActivity()));
                column.mLearnMoreClickListener = learnMoreClickListener;
                column.mLearnMoreOverrideText = getText(
                        featureProvider.getSettingPageFooterLearnMoreDescription());
                mFooterColumns.add(column);
            }
        }

        private boolean isUdfps() {
            for (FingerprintSensorPropertiesInternal prop : mSensorProperties) {
                if (prop.isAnyUdfpsType()) {
                    return true;
                }
            }
            return false;
        }

        private boolean isSfps() {
            mFingerprintManager = Utils.getFingerprintManagerOrNull(getActivity());
            if (mFingerprintManager != null) {
                mSensorProperties = mFingerprintManager.getSensorPropertiesInternal();
                for (FingerprintSensorPropertiesInternal prop : mSensorProperties) {
                    if (prop.isAnySidefpsType()) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean isScreenOffUnlcokSupported() {
            if (isUdfps()) {
                return getContext().getResources().getBoolean(
                        com.android.internal.R.bool.config_screen_off_udfps_enabled);
            }
            return false;
        }

        protected void removeFingerprintPreference(int fingerprintId) {
            String name = genKey(fingerprintId);
            Log.e(TAG, "removeFingerprintPreference : " + fingerprintId);
            Preference prefToRemove = findPreference(name);
            if (prefToRemove != null) {
                if (!getPreferenceScreen().removePreference(prefToRemove)) {
                    Log.w(TAG, "Failed to remove preference with key " + name);
                }
            } else {
                Log.w(TAG, "Can't find preference to remove: " + name);
            }
        }

        /**
         * Important!
         *
         * Don't forget to update the SecuritySearchIndexProvider if you are doing any change in the
         * logic or adding/removing preferences here.
         */
        private PreferenceScreen createPreferenceHierarchy() {
            PreferenceScreen root = getPreferenceScreen();
            addFingerprintPreferences(root);
            if (Flags.biometricsOnboardingEducation()) {
                setupUseFingerprintToPreferences();
            }
            setPreferenceScreen(root);
            return root;
        }

        private void addFingerprintPreferences(PreferenceGroup root) {
            final String fpPrefKey = addFingerprintItemPreferences(root);
            for (AbstractPreferenceController controller : mControllers) {
                if (controller instanceof FingerprintSettingsPreferenceController) {
                    ((FingerprintSettingsPreferenceController) controller).setUserId(mUserId);
                } else if (controller instanceof FingerprintUnlockCategoryController) {
                    ((FingerprintUnlockCategoryController) controller).setUserId(mUserId);
                }
            }

            // This needs to be after setting ids, otherwise
            // |mRequireScreenOnToAuthPreferenceController.isChecked| is always checking the primary
            // user instead of the user with |mUserId|.
            if (isSfps() || (screenOffUnlockUdfps() && isScreenOffUnlcokSupported())
                    || getExtPreferenceProvider().getSize() > 0) {
                scrollToPreference(fpPrefKey);
                addFingerprintUnlockCategory();
            }
            final int descriptionRes = FeatureFactory.getFeatureFactory()
                    .getFingerprintFeatureProvider().getFingerprintSettingsFeatureProvider()
                    .getSettingPageDescription();
            if (ResourceId.isValid(descriptionRes)) {
                final Preference preference = findPreference(KEY_FINGERPRINT_DESCRIPTION);
                preference.setTitle(descriptionRes);
                preference.setVisible(true);
            }
            createFooterPreference(root);
        }

        private String addFingerprintItemPreferences(PreferenceGroup root) {
            mFingerprintsEnrolledCategory = findPreference(KEY_FINGERPRINTS_ENROLLED_CATEGORY);
            if (mFingerprintsEnrolledCategory != null) {
                mFingerprintsEnrolledCategory.removeAll();
            }
            if (Flags.biometricsOnboardingEducation()) {
                mFingerprintsEnrolledCategory.setTitle(root.getContext().getString(
                        R.string.security_settings_fingerprint_title));
            }

            String keyToReturn = getAddFingerprintPreferenceKey();
            final List<Fingerprint> items = mFingerprintManager.getEnrolledFingerprints(mUserId);
            final int fingerprintCount = items.size();
            for (int i = 0; i < fingerprintCount; i++) {
                final Fingerprint item = items.get(i);
                FingerprintPreference pref = new FingerprintPreference(root.getContext(),
                        this /* onDeleteClickListener */);
                String key = genKey(item.getBiometricId());
                if (i == 0) {
                    keyToReturn = key;
                }
                pref.setKey(key);
                pref.setTitle(item.getName());
                pref.setFingerprint(item);
                pref.setPersistent(false);
                pref.setIcon(R.drawable.ic_fingerprint_24dp);
                if (mRemovalSidecar.isRemovingFingerprint(item.getBiometricId())) {
                    pref.setEnabled(false);
                }
                if (mFingerprintsRenaming.containsKey(item.getBiometricId())) {
                    pref.setTitle(mFingerprintsRenaming.get(item.getBiometricId()));
                }
                mFingerprintsEnrolledCategory.addPreference(pref);
                pref.setOnPreferenceChangeListener(this);
            }
            if (Flags.biometricsOnboardingEducation() && isUdfps() && fingerprintCount > 0) {
                // Setup check enrolled fingerprints preference
                Preference pref = new Preference(root.getContext());
                pref.setKey(KEY_FINGERPRINT_CHECK_ENROLLED);
                pref.setTitle(root.getContext().getString(
                        R.string.fingerprint_check_enrolled_title));
                pref.setIcon(R.drawable.ic_check_list_24dp);
                pref.setVisible(true);
                mFingerprintsEnrolledCategory.addPreference(pref);
                pref.setOnPreferenceChangeListener(this);
            }
            mAddFingerprintPreference = findPreference(getAddFingerprintPreferenceKey());
            setupAddFingerprintPreference();
            return keyToReturn;
        }

        private void setupAddFingerprintPreference() {
            mAddFingerprintPreference.setOnPreferenceChangeListener(this);
            if (mIsExpressiveThemeStyle
                    && (mAddFingerprintPreference instanceof ButtonPreference)) {
                ((ButtonPreference) mAddFingerprintPreference).setOnClickListener(view -> {
                    mIsEnrolling = true;
                    Intent intent = new Intent();
                    intent.setClassName(SETTINGS_PACKAGE_NAME,
                            FingerprintEnroll.AddAdditionalFingerprint.class.getName());
                    intent.putExtra(Intent.EXTRA_USER_ID, mUserId);
                    intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE_TOKEN, mToken);
                    if (mCalibrator != null) {
                        intent.putExtras(mCalibrator.getExtrasForNextIntent());
                    }
                    startActivityForResult(intent, ADD_FINGERPRINT_REQUEST);
                });
            }

            updateAddPreference();
        }

        /**
         * Lambda function for setCategoryHasChildrenSupplier
         */
        private boolean fingerprintUnlockCategoryHasChild() {
            return mFingerprintUnlockCategory.getPreferenceCount() > 0;
        }

        private void addFingerprintUnlockCategory() {
            mFingerprintUnlockCategory = findPreference(KEY_FINGERPRINT_UNLOCK_CATEGORY);
            mFingerprintUnlockCategoryPreferenceController.setCategoryHasChildrenSupplier(
                    this::fingerprintUnlockCategoryHasChild);
            if (isSfps()) {
                // For both SFPS "screen on to auth" and "rest to unlock"
                final Preference restToUnlockPreference = FeatureFactory.getFeatureFactory()
                        .getFingerprintFeatureProvider()
                        .getSfpsRestToUnlockFeature(getContext())
                        .getRestToUnlockPreference(getContext());
                if (restToUnlockPreference != null) {
                    // Use custom featured preference if any.
                    mRequireScreenOnToAuthPreference.setTitle(restToUnlockPreference.getTitle());
                    mRequireScreenOnToAuthPreference.setSummary(
                            restToUnlockPreference.getSummary());
                    mRequireScreenOnToAuthPreference.setChecked(
                            ((TwoStatePreference) restToUnlockPreference).isChecked());
                    mRequireScreenOnToAuthPreference.setOnPreferenceChangeListener(
                            restToUnlockPreference.getOnPreferenceChangeListener());
                }
                setupFingerprintUnlockCategoryPreferencesForScreenOnToAuth();
            } else if (screenOffUnlockUdfps() && isScreenOffUnlcokSupported()) {
                setupFingerprintUnlockCategoryPreferencesForScreenOffUnlock();
            }
            setupExtFingerprintPreferences();
            updateFingerprintUnlockCategoryVisibility();
        }

        private void updateFingerprintUnlockCategoryVisibility() {
            final int categoryStatus =
                    mFingerprintUnlockCategoryPreferenceController.getAvailabilityStatus();
            updatePreferenceVisibility(categoryStatus, mFingerprintUnlockCategory);

            if (mRequireScreenOnToAuthPreferenceController != null) {
                final int status =
                        mRequireScreenOnToAuthPreferenceController.getAvailabilityStatus();
                updatePreferenceVisibility(status, mRequireScreenOnToAuthPreference);
            }
            if (mScreenOffUnlockUdfpsPreferenceController != null) {
                final int status =
                        mScreenOffUnlockUdfpsPreferenceController.getAvailabilityStatus();
                updatePreferenceVisibility(status, mScreenOffUnlockUdfpsPreference);
            }
            if (!mExtPrefKeys.isEmpty()) {
                for (String key: mExtPrefKeys) {
                    Preference preference = mFingerprintUnlockCategory.findPreference(key);
                    if (preference != null) {
                        updatePreferenceVisibility(categoryStatus, preference);
                    }
                }
            }
        }

        private void updatePreferenceVisibility(int availabilityStatus, Preference preference) {
            if (availabilityStatus == AVAILABLE) {
                preference.setVisible(true);
                preference.setEnabled(true);
            } else if (availabilityStatus == CONDITIONALLY_UNAVAILABLE) {
                preference.setVisible(true);
                preference.setEnabled(false);
            } else {
                preference.setVisible(false);
            }
        }

        private void setupFingerprintUnlockCategoryPreferencesForScreenOnToAuth() {
            mRequireScreenOnToAuthPreference = findPreference(KEY_REQUIRE_SCREEN_ON_TO_AUTH);
            mRequireScreenOnToAuthPreference.setChecked(
                    mRequireScreenOnToAuthPreferenceController.isChecked());
            mRequireScreenOnToAuthPreference.setOnPreferenceChangeListener(
                    (preference, newValue) -> {
                        final boolean isChecked = ((TwoStatePreference) preference).isChecked();
                        mRequireScreenOnToAuthPreferenceController.setChecked(!isChecked);
                        return true;
                    });
        }

        private void setupFingerprintUnlockCategoryPreferencesForScreenOffUnlock() {
            mScreenOffUnlockUdfpsPreference = findPreference(KEY_SCREEN_OFF_FINGERPRINT_UNLOCK);
            mScreenOffUnlockUdfpsPreference.setChecked(
                    mScreenOffUnlockUdfpsPreferenceController.isChecked());
            mScreenOffUnlockUdfpsPreference.setOnPreferenceChangeListener(
                    (preference, newValue) -> {
                        final boolean isChecked = ((TwoStatePreference) preference).isChecked();
                        mScreenOffUnlockUdfpsPreferenceController.setChecked(!isChecked);
                        return true;
                    });
        }

        private void setupUseFingerprintToPreferences() {
            final PreferenceCategory category =
                    findPreference(KEY_BIOMETRICS_USE_FINGERPRINT_TO_CATEGORY);
            category.setVisible(true);

            // Setup use fingerprint to unlock preference
            final FingerprintSettingsKeyguardUnlockPreferenceController fpUnlockController =
                    use(FingerprintSettingsKeyguardUnlockPreferenceController.class);
            fpUnlockController.setUserId(mUserId);
            findPreference(fpUnlockController.getPreferenceKey())
                    .setOnPreferenceChangeListener(fpUnlockController);

            // Setup use fingerprint to verify it's you in apps preference
            final FingerprintSettingsAppsPreferenceController fingerprintAppController =
                    use(FingerprintSettingsAppsPreferenceController.class);
            fingerprintAppController.setUserId(mUserId);
            findPreference(fingerprintAppController.getPreferenceKey())
                    .setOnPreferenceChangeListener(fingerprintAppController);
        }

        private void updateUseFingerprintToEnableStatus() {
            final PreferenceCategory category =
                    findPreference(KEY_BIOMETRICS_USE_FINGERPRINT_TO_CATEGORY);
            if (!category.isVisible()) {
                return;
            }
            final boolean hasFingerprintEnrolled =
                    mFingerprintManager.getEnrolledFingerprints(mUserId).size() > 0;

            final FingerprintSettingsKeyguardUnlockPreferenceController fpUnlockController =
                    use(FingerprintSettingsKeyguardUnlockPreferenceController.class);
            findPreference(fpUnlockController.getPreferenceKey())
                    .setEnabled(hasFingerprintEnrolled);

            final FingerprintSettingsAppsPreferenceController fingerprintAppController =
                    use(FingerprintSettingsAppsPreferenceController.class);
            findPreference(fingerprintAppController.getPreferenceKey())
                    .setEnabled(hasFingerprintEnrolled);
        }

        private void updatePreferencesAfterFingerprintRemoved() {
            updateAddPreference();
            updateUseFingerprintToEnableStatus();
            if (isSfps() || (screenOffUnlockUdfps() && isScreenOffUnlcokSupported())) {
                updateFingerprintUnlockCategoryVisibility();
            }
            updatePreferences();
        }

        private void updateAddPreference() {
            if (getActivity() == null) {
                return; // Activity went away
            }

            mAddFingerprintPreference = findPreference(getAddFingerprintPreferenceKey());

            if (mAddFingerprintPreference == null) {
                return; // b/275519315 Skip if updateAddPreference() invoke before addPreference()
            }

            updateAddingButtonStyle();

            /* Disable preference if too many fingerprints added */
            final int max = getContext().getResources().getInteger(
                    com.android.internal.R.integer.config_fingerprintMaxTemplatesPerUser);
            boolean tooMany = mFingerprintManager.getEnrolledFingerprints(mUserId).size() >= max;
            // retryFingerprint() will be called when remove finishes
            // need to disable enroll or have a way to determine if enroll is in progress
            final boolean removalInProgress = mRemovalSidecar.inProgress();
            final boolean isDeviceOwnerBlockingAuth =
                    RestrictedLockUtilsInternal.checkIfKeyguardFeaturesDisabled(
                            getContext(), DevicePolicyManager.KEYGUARD_DISABLE_FINGERPRINT,
                            mUserId) != null;

            CharSequence maxSummary = tooMany ?
                    getContext().getString(R.string.fingerprint_add_max, max) : "";
            mAddFingerprintPreference.setSummary(maxSummary);
            mAddFingerprintPreference.setEnabled(!isDeviceOwnerBlockingAuth
                    && !tooMany && !removalInProgress && mToken != null);
        }

        private void updateAddingButtonStyle() {
            final Preference nonExpressiveBtnPreference = findPreference(KEY_FINGERPRINT_ADD);
            final ButtonPreference expressiveBtnPreference =
                    findPreference(KEY_FINGERPRINT_ADD_EXPRESSIVE);

            if (nonExpressiveBtnPreference != null) {
                nonExpressiveBtnPreference.setVisible(!shouldShowExpressiveAddFingerprintPref());
            }

            if (expressiveBtnPreference != null) {
                expressiveBtnPreference.setVisible(shouldShowExpressiveAddFingerprintPref());
            }
        }

        private void createFooterPreference(PreferenceGroup root) {
            final Context context = getActivity();
            if (context == null) {
                return;
            }
            mFingerprintUnlockFooter = findPreference(KEY_FINGERPRINT_UNLOCK_FOOTER);
            if (mFingerprintUnlockFooter != null) {
                mFingerprintUnlockFooter.removeAll();
            }
            for (int i = 0; i < mFooterColumns.size(); ++i) {
                final FooterColumn column = mFooterColumns.get(i);
                final FooterPreference footer = new FooterPreference.Builder(context)
                        .setTitle(column.mTitle).build();
                if (i > 0) {
                    footer.setIconVisibility(View.GONE);
                }
                if (column.mLearnMoreClickListener != null) {
                    footer.setLearnMoreAction(column.mLearnMoreClickListener);
                    if (!TextUtils.isEmpty(column.mLearnMoreOverrideText)) {
                        footer.setLearnMoreText(column.mLearnMoreOverrideText);
                    }
                }
                mFingerprintUnlockFooter.addPreference(footer);
            }
        }

        private static String genKey(int id) {
            return KEY_FINGERPRINT_ITEM_PREFIX + "_" + id;
        }

        @Override
        public void onResume() {
            super.onResume();
            mInFingerprintLockout = false;
            // Make sure we reload the preference hierarchy since fingerprints may be added,
            // deleted or renamed.
            updatePreferences();
            if (mRemovalSidecar != null) {
                mRemovalSidecar.setListener(mRemovalListener);
            }

            if (!mLaunchedConfirm && !mIsEnrolling) {
                mCalibrator = FeatureFactory.getFeatureFactory().getFingerprintFeatureProvider()
                        .getUdfpsEnrollCalibrator(getActivity().getApplicationContext(), null,
                                null);
            }
        }

        private void updatePreferences() {
            createPreferenceHierarchy();
            retryFingerprint();
        }

        @Override
        public void onPause() {
            super.onPause();
            if (mRemovalSidecar != null) {
                mRemovalSidecar.setListener(null);
            }
            if (mAuthenticateSidecar != null) {
                mAuthenticateSidecar.setListener(null);
                mAuthenticateSidecar.stopAuthentication();
                mHandler.removeCallbacks(mFingerprintLockoutReset);
            }
        }

        @Override
        public void onStop() {
            super.onStop();
            if (!getActivity().isChangingConfigurations() && !mLaunchedConfirm && !mIsEnrolling
                    && mLaunchedExtPrefKey.isEmpty()) {
                Log.d(TAG, "Finish activity for unknown stop");
                setResult(RESULT_TIMEOUT);
                getActivity().finish();
            }
        }

        @Override
        protected int getPreferenceScreenResId() {
            return R.xml.security_settings_fingerprint;
        }

        @Override
        protected String getLogTag() {
            return TAG;
        }

        @Override
        public void onSaveInstanceState(final Bundle outState) {
            outState.putByteArray(ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE_TOKEN,
                    mToken);
            outState.putBoolean(KEY_LAUNCHED_CONFIRM, mLaunchedConfirm);
            outState.putSerializable("mFingerprintsRenaming", mFingerprintsRenaming);
            outState.putBoolean(KEY_IS_ENROLLING, mIsEnrolling);
            outState.putString(KEY_IS_LAUNCHING_EXT_PREF, mLaunchedExtPrefKey);
            outState.putBoolean(KEY_HAS_FIRST_ENROLLED, mHasFirstEnrolled);
            outState.putBoolean(KEY_HAS_RUN_CHALLENGE_INVOKER, mHasRunChallengeInvoker);
            outState.putBoolean(KEY_BIOMETRICS_AUTHENTICATION_REQUESTED,
                    mBiometricsAuthenticationRequested);
        }

        @Override
        public boolean onPreferenceTreeClick(Preference pref) {
            final String key = pref.getKey();
            if (KEY_FINGERPRINT_ADD.equals(key)) {
                mIsEnrolling = true;
                Intent intent = new Intent();
                intent.setClassName(SETTINGS_PACKAGE_NAME,
                        FingerprintEnroll.AddAdditionalFingerprint.class.getName());
                intent.putExtra(Intent.EXTRA_USER_ID, mUserId);
                intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE_TOKEN, mToken);
                if (mCalibrator != null) {
                    intent.putExtras(mCalibrator.getExtrasForNextIntent());
                }
                startActivityForResult(intent, ADD_FINGERPRINT_REQUEST);
            } else if (pref instanceof FingerprintPreference) {
                FingerprintPreference fpref = (FingerprintPreference) pref;
                final Fingerprint fp = fpref.getFingerprint();
                showRenameDialog(fp);
            } else if (KEY_FINGERPRINT_CHECK_ENROLLED.equals(key)) {
                showCheckEnrolledDialog();
            }
            return super.onPreferenceTreeClick(pref);
        }

        @Override
        public void onDeleteClick(FingerprintPreference p) {
            final boolean hasMultipleFingerprint =
                    mFingerprintManager.getEnrolledFingerprints(mUserId).size() > 1;
            final Fingerprint fp = p.getFingerprint();

            if (hasMultipleFingerprint) {
                if (mRemovalSidecar.inProgress()) {
                    Log.d(TAG, "Fingerprint delete in progress, skipping");
                    return;
                }
                DeleteFingerprintDialog.newInstance(fp, this /* target */)
                        .show(getFragmentManager(), DeleteFingerprintDialog.class.getName());
            } else {
                ConfirmLastDeleteDialog lastDeleteDialog = new ConfirmLastDeleteDialog();
                final boolean isProfileChallengeUser =
                        UserManager.get(getContext()).isManagedProfile(mUserId);
                final Bundle args = new Bundle();
                args.putParcelable("fingerprint", fp);
                args.putBoolean("isProfileChallengeUser", isProfileChallengeUser);
                lastDeleteDialog.setArguments(args);
                lastDeleteDialog.setTargetFragment(this, 0);
                lastDeleteDialog.show(getFragmentManager(),
                        ConfirmLastDeleteDialog.class.getName());
            }
        }

        private void showRenameDialog(final Fingerprint fp) {
            RenameDialog renameDialog = new RenameDialog();
            Bundle args = new Bundle();
            if (mFingerprintsRenaming.containsKey(fp.getBiometricId())) {
                final Fingerprint f = new Fingerprint(
                        mFingerprintsRenaming.get(fp.getBiometricId()),
                        fp.getGroupId(), fp.getBiometricId(), fp.getDeviceId());
                args.putParcelable("fingerprint", f);
            } else {
                args.putParcelable("fingerprint", fp);
            }
            renameDialog.setOnDismissListener((dialogInterface) -> {
                retryFingerprint();
            });
            renameDialog.setDeleteInProgress(mRemovalSidecar.inProgress());
            renameDialog.setArguments(args);
            renameDialog.setTargetFragment(this, 0);
            renameDialog.show(getFragmentManager(), RenameDialog.class.getName());
            mAuthenticateSidecar.stopAuthentication();
        }

        private void showCheckEnrolledDialog() {
            final CheckEnrolledDialog checkEnrolledDialog = new CheckEnrolledDialog();
            final Bundle args = new Bundle();
            args.putInt(CheckEnrolledDialog.KEY_USER_ID, mUserId);
            args.putParcelable(CheckEnrolledDialog.KEY_SENSOR_PROPERTIES, mSensorProperties.get(0));
            checkEnrolledDialog.setArguments(args);
            checkEnrolledDialog.setTargetFragment(this, 0);
            checkEnrolledDialog.show(getFragmentManager(), CheckEnrolledDialog.class.getName());
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            boolean result = true;
            final String key = preference.getKey();
            if (KEY_FINGERPRINT_ENABLE_KEYGUARD_TOGGLE.equals(key)) {
                // TODO
            } else {
                Log.v(TAG, "Unknown key:" + key);
            }
            return result;
        }

        @Override
        public int getHelpResource() {
            return R.string.help_url_fingerprint;
        }

        @Override
        protected List<AbstractPreferenceController> createPreferenceControllers(Context context) {
            if (!isFingerprintHardwareDetected(context)) {
                Log.e(TAG, "Fingerprint hardware is not detected");
                mControllers = Collections.emptyList();
                return null;
            }

            mControllers = buildPreferenceControllers(context);
            return mControllers;
        }

        private List<AbstractPreferenceController> buildPreferenceControllers(Context context) {
            final List<AbstractPreferenceController> controllers =
                    createThePreferenceControllers(context);
            if (isSfps()) {
                for (AbstractPreferenceController controller : controllers) {
                    if (controller.getPreferenceKey() == KEY_FINGERPRINT_UNLOCK_CATEGORY) {
                        mFingerprintUnlockCategoryPreferenceController =
                                (FingerprintUnlockCategoryController) controller;
                    } else if (controller.getPreferenceKey() == KEY_REQUIRE_SCREEN_ON_TO_AUTH) {
                        mRequireScreenOnToAuthPreferenceController =
                                (FingerprintSettingsRequireScreenOnToAuthPreferenceController)
                                        controller;
                    }

                }
            } else if (screenOffUnlockUdfps() && isScreenOffUnlcokSupported()) {
                for (AbstractPreferenceController controller : controllers) {
                    if (controller.getPreferenceKey() == KEY_FINGERPRINT_UNLOCK_CATEGORY) {
                        mFingerprintUnlockCategoryPreferenceController =
                                (FingerprintUnlockCategoryController) controller;
                    } else if (controller.getPreferenceKey() == KEY_SCREEN_OFF_FINGERPRINT_UNLOCK) {
                        mScreenOffUnlockUdfpsPreferenceController =
                                (FingerprintSettingsScreenOffUnlockUdfpsPreferenceController)
                                        controller;
                    }

                }
            }

            if (mFingerprintUnlockCategoryPreferenceController == null
                    && getExtPreferenceProvider().getSize() > 0 && controllers != null) {
                for (AbstractPreferenceController controller : controllers) {
                    if (KEY_FINGERPRINT_UNLOCK_CATEGORY.equals(controller.getPreferenceKey())) {
                        mFingerprintUnlockCategoryPreferenceController =
                                (FingerprintUnlockCategoryController) controller;
                    }
                }
            }
            return controllers;
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (requestCode == CONFIRM_REQUEST || requestCode == CHOOSE_LOCK_GENERIC_REQUEST) {
                mLaunchedConfirm = false;
                if (resultCode == RESULT_FINISHED || resultCode == RESULT_OK) {
                    runChallengeGeneratedInvokers();
                    if (BiometricUtils.containsGatekeeperPasswordHandle(data)) {
                        final Utils.BiometricStatus biometricAuthStatus =
                                Utils.requestBiometricAuthenticationForMandatoryBiometrics(
                                        getActivity(),
                                        mBiometricsAuthenticationRequested,
                                        mUserId);
                        if (biometricAuthStatus == Utils.BiometricStatus.OK) {
                            Utils.launchBiometricPromptForMandatoryBiometrics(this,
                                    BIOMETRIC_AUTH_REQUEST,
                                    mUserId, true /* hideBackground */, data);
                        } else if (biometricAuthStatus != Utils.BiometricStatus.NOT_ACTIVE) {
                            IdentityCheckBiometricErrorDialog
                                    .showBiometricErrorDialogAndFinishActivityOnDismiss(
                                            getActivity(),
                                            biometricAuthStatus);
                        } else {
                            handleAuthenticationSuccessful(data);
                        }
                    } else {
                        Log.d(TAG, "Data null or GK PW missing");
                        finish();
                    }
                } else {
                    Log.d(TAG, "Password not confirmed");
                    finish();
                }
            } else if (requestCode == ADD_FINGERPRINT_REQUEST) {
                mIsEnrolling = false;
                if (resultCode == RESULT_TIMEOUT) {
                    Activity activity = getActivity();
                    activity.setResult(resultCode);
                    activity.finish();
                }
            } else if (requestCode == AUTO_ADD_FIRST_FINGERPRINT_REQUEST) {
                if (resultCode != RESULT_FINISHED) {
                    Log.d(TAG, "Add first fingerprint, fail or null data, result:" + resultCode);
                    if (resultCode == BiometricEnrollBase.RESULT_TIMEOUT) {
                        // If "Fingerprint Unlock" is closed because of timeout, notify result code
                        // back because "Face & Fingerprint Unlock" has to close itself for timeout
                        // case.
                        setResult(resultCode);
                    }
                    finish();
                    return;
                }

                if (mToken == null && data != null) {
                    mToken = data.getByteArrayExtra(
                            ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE_TOKEN);
                }
                if (mToken == null) {
                    Log.w(TAG, "Add first fingerprint, null token");
                    finish();
                    return;
                }

                if (mChallenge == -1L && data != null) {
                    mChallenge = data.getLongExtra(EXTRA_KEY_CHALLENGE, -1L);
                }
                if (mChallenge == -1L) {
                    Log.w(TAG, "Add first fingerprint, invalid challenge");
                    finish();
                    return;
                }

                mIsEnrolling = false;
                mHasFirstEnrolled = true;
                updateAddPreference();
            } else if (requestCode == BIOMETRIC_AUTH_REQUEST) {
                mBiometricsAuthenticationRequested = false;
                if (resultCode == RESULT_OK) {
                    handleAuthenticationSuccessful(data);
                } else {
                    if (resultCode
                            == ConfirmDeviceCredentialActivity.BIOMETRIC_LOCKOUT_ERROR_RESULT) {
                        IdentityCheckBiometricErrorDialog
                                .showBiometricErrorDialogAndFinishActivityOnDismiss(getActivity(),
                                        Utils.BiometricStatus.LOCKOUT);
                    } else {
                        finish();
                    }
                }
            } else if (requestCode == LAUNCH_EXT_PREF_REQUEST) {
                if (resultCode == RESULT_TIMEOUT) {
                    Activity activity = getActivity();
                    if (activity != null) {
                        activity.setResult(resultCode);
                        activity.finish();
                        return;
                    }
                }

                if (!mLaunchedExtPrefKey.isEmpty()) {
                    final Preference preference = findPreference(mLaunchedExtPrefKey);
                    if (preference instanceof PrimarySwitchIntentPreference) {
                        ((PrimarySwitchIntentPreference) preference).forceUpdate();
                    }
                }
                mLaunchedExtPrefKey = "";
            }
        }

        private void runChallengeGeneratedInvokers() {
            if (mHasRunChallengeInvoker) {
                return;
            }
            mHasRunChallengeInvoker = true;

            List<ChallengeGeneratedInvoker> invokers = FeatureFactory.getFeatureFactory()
                    .getFingerprintFeatureProvider().getChallengeGeneratedInvokers();
            Log.d(TAG, "Num of ChallengeGeneratedInvoker: " + invokers.size());
            for (ChallengeGeneratedInvoker invoker: invokers) {
                Bundle bundle = getIntent().getBundleExtra(invoker.getIntentKeyForBundle());
                if (bundle == null) {
                    continue;
                }

                long startTime = System.currentTimeMillis();
                boolean result = invoker.invoke(this, bundle);
                Log.d(TAG, "Invoker for " + invoker.getIntentKeyForBundle() + " run "
                        + (System.currentTimeMillis() - startTime) + "ms, result: " + result);

                // We shall only have at most one invoker for each launching
                return;
            }
        }

        private void handleAuthenticationSuccessful(Intent data) {
            if (!mHasFirstEnrolled && !mIsEnrolling) {
                final Activity activity = getActivity();
                if (activity != null) {
                    // Apply pending transition for auto adding first fingerprint case
                    activity.overridePendingTransition(
                            com.google.android.setupdesign.R.anim.sud_slide_next_in,
                            com.google.android.setupdesign.R.anim.sud_slide_next_out);
                }

                // To have smoother animation, change flow to let next visible activity
                // to generateChallenge, then pass it back through activity result.
                // Token and challenge will be updated later through the activity result
                // of AUTO_ADD_FIRST_FINGERPRINT_REQUEST.
                mIsEnrolling = true;
                addFirstFingerprint(
                        BiometricUtils.getGatekeeperPasswordHandle(data));
            } else {
                mFingerprintManager.generateChallenge(mUserId,
                        (sensorId, userId, challenge) -> {
                            final Activity activity = getActivity();
                            if (activity == null || activity.isFinishing()) {
                                // Stop everything
                                Log.w(TAG, "activity detach or finishing");
                                return;
                            }

                            final GatekeeperPasswordProvider provider =
                                    new GatekeeperPasswordProvider(
                                            new LockPatternUtils(activity));
                            mToken = provider.requestGatekeeperHat(data, challenge,
                                    mUserId);
                            mChallenge = challenge;
                            provider.removeGatekeeperPasswordHandle(data, false);
                            updateAddPreference();
                        });
            }
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            if (getActivity().isFinishing()) {
                mFingerprintManager.revokeChallenge(mUserId, mChallenge);
            }
        }

        private Drawable getHighlightDrawable() {
            if (mHighlightDrawable == null) {
                final Activity activity = getActivity();
                if (activity != null) {
                    mHighlightDrawable = activity.getDrawable(R.drawable.preference_highlight);
                }
            }
            return mHighlightDrawable;
        }

        private void highlightFingerprintItem(int fpId) {
            mVibrator.vibrate(SUCCESS_VIBRATION_EFFECT);
            String prefName = genKey(fpId);
            FingerprintPreference fpref = (FingerprintPreference) findPreference(prefName);
            final Drawable highlight = getHighlightDrawable();
            if (highlight != null && fpref != null) {
                final View view = fpref.getView();
                if (view == null) {
                    // FingerprintPreference is not bound to UI yet, so view is null.
                    return;
                }
                final int centerX = view.getWidth() / 2;
                final int centerY = view.getHeight() / 2;
                highlight.setHotspot(centerX, centerY);
                view.setBackground(highlight);
                view.setPressed(true);
                view.setPressed(false);
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        view.setBackground(null);
                    }
                }, RESET_HIGHLIGHT_DELAY_MS);
            }
        }

        private void launchChooseOrConfirmLock() {
            final Intent intent = new Intent();
            final ChooseLockSettingsHelper.Builder builder =
                    new ChooseLockSettingsHelper.Builder(getActivity(), this);
            final boolean launched = builder.setRequestCode(CONFIRM_REQUEST)
                    .setTitle(getString(R.string.security_settings_fingerprint_preference_title))
                    .setRequestGatekeeperPasswordHandle(true)
                    .setUserId(mUserId)
                    .setForegroundOnly(true)
                    .setReturnCredentials(true)
                    .show();

            if (!launched) {
                // TODO: This should be cleaned up. ChooseLockGeneric should provide a way of
                //  specifying arguments/requests, instead of relying on callers setting extras.
                intent.setClassName(SETTINGS_PACKAGE_NAME, ChooseLockGeneric.class.getName());
                intent.putExtra(ChooseLockGeneric.ChooseLockGenericFragment.HIDE_INSECURE_OPTIONS,
                        true);
                intent.putExtra(Intent.EXTRA_USER_ID, mUserId);
                intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_REQUEST_GK_PW_HANDLE, true);
                intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_FOR_FINGERPRINT, true);
                startActivityForResult(intent, CHOOSE_LOCK_GENERIC_REQUEST);
            }
        }

        private void addFirstFingerprint(@Nullable Long gkPwHandle) {
            Intent intent = new Intent();
            intent.setClassName(SETTINGS_PACKAGE_NAME,
                    FingerprintEnroll.InternalActivity.class.getName());
            intent.putExtra(EXTRA_FROM_SETTINGS_SUMMARY, true);
            intent.putExtra(SettingsBaseActivity.EXTRA_PAGE_TRANSITION_TYPE,
                    SettingsTransitionHelper.TransitionType.TRANSITION_SLIDE);

            intent.putExtra(Intent.EXTRA_USER_ID, mUserId);
            if (gkPwHandle != null) {
                intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_GK_PW_HANDLE,
                        gkPwHandle.longValue());
            } else {
                intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE_TOKEN, mToken);
                intent.putExtra(BiometricEnrollBase.EXTRA_KEY_CHALLENGE, mChallenge);
            }

            if (mCalibrator != null) {
                intent.putExtras(mCalibrator.getExtrasForNextIntent());
            }

            startActivityForResult(intent, AUTO_ADD_FIRST_FINGERPRINT_REQUEST);
        }

        @VisibleForTesting
        void deleteFingerPrint(Fingerprint fingerPrint) {
            mRemovalSidecar.startRemove(fingerPrint, mUserId);
            String name = genKey(fingerPrint.getBiometricId());
            Preference prefToRemove = findPreference(name);
            if (prefToRemove != null) {
                prefToRemove.setEnabled(false);
            }
            updateAddPreference();
        }

        private void renameFingerPrint(int fingerId, String newName) {
            mFingerprintManager.rename(fingerId, mUserId, newName);
            if (!TextUtils.isEmpty(newName)) {
                mFingerprintsRenaming.put(fingerId, newName);
            }
            updatePreferences();
        }

        private final Runnable mFingerprintLockoutReset = new Runnable() {
            @Override
            public void run() {
                mInFingerprintLockout = false;
                retryFingerprint();
            }
        };

        private boolean isPrivateProfile() {
            return Utils.isPrivateProfile(mUserId, getContext());
        }

        private String getAddFingerprintPreferenceKey() {
            return shouldShowExpressiveAddFingerprintPref()
                    ? KEY_FINGERPRINT_ADD_EXPRESSIVE : KEY_FINGERPRINT_ADD;
        }

        private boolean shouldShowExpressiveAddFingerprintPref() {
            return Flags.biometricsOnboardingEducation() && mIsExpressiveThemeStyle
                    && mFingerprintManager.hasEnrolledTemplates(mUserId);
        }

        public static class DeleteFingerprintDialog extends InstrumentedDialogFragment
                implements DialogInterface.OnClickListener {

            private static final String KEY_FINGERPRINT = "fingerprint";
            private Fingerprint mFp;
            private AlertDialog mAlertDialog;

            public static DeleteFingerprintDialog newInstance(Fingerprint fp,
                    FingerprintSettingsFragment target) {
                final DeleteFingerprintDialog dialog = new DeleteFingerprintDialog();
                final Bundle bundle = new Bundle();
                bundle.putParcelable(KEY_FINGERPRINT, fp);
                dialog.setArguments(bundle);
                dialog.setTargetFragment(target, 0 /* requestCode */);
                return dialog;
            }

            @Override
            public int getMetricsCategory() {
                return SettingsEnums.DIALOG_FINGERPRINT_DELETE;
            }

            @Override
            public Dialog onCreateDialog(Bundle savedInstanceState) {
                mFp = getArguments().getParcelable(KEY_FINGERPRINT);
                final String title = getString(R.string.fingerprint_delete_title, mFp.getName());
                final String message =
                        getString(R.string.fingerprint_v2_delete_message, mFp.getName());

                mAlertDialog = new AlertDialog.Builder(getActivity())
                        .setTitle(title)
                        .setMessage(message)
                        .setPositiveButton(
                                R.string.security_settings_fingerprint_enroll_dialog_delete,
                                this /* onClickListener */)
                        .setNegativeButton(R.string.cancel, null /* onClickListener */)
                        .create();
                return mAlertDialog;
            }

            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    final int fingerprintId = mFp.getBiometricId();
                    Log.v(TAG, "Removing fpId=" + fingerprintId);
                    mMetricsFeatureProvider.action(getContext(),
                            SettingsEnums.ACTION_FINGERPRINT_DELETE,
                            fingerprintId);
                    FingerprintSettingsFragment parent
                            = (FingerprintSettingsFragment) getTargetFragment();
                    parent.deleteFingerPrint(mFp);
                }
            }
        }

        private static InputFilter[] getFilters() {
            InputFilter filter = new InputFilter() {
                @Override
                public CharSequence filter(CharSequence source, int start, int end,
                        Spanned dest, int dstart, int dend) {
                    for (int index = start; index < end; index++) {
                        final char c = source.charAt(index);
                        // KXMLSerializer does not allow these characters,
                        // see KXmlSerializer.java:162.
                        if (c < 0x20) {
                            return "";
                        }
                    }
                    return null;
                }
            };
            return new InputFilter[]{filter};
        }

        public static class CheckEnrolledDialog extends InstrumentedDialogFragment {

            private static final String KEY_USER_ID = "user_id";
            private static final String KEY_SENSOR_PROPERTIES = "sensor_properties";
            private static final String EXTRA_FAILURE_COUNT = "failure_count";
            private static final int MAX_FAILURE_COUNT = 3;
            private int mUserId;
            private int mFailureCount;
            private @Nullable CancellationSignal mCancellationSignal;
            private @Nullable FingerprintSensorPropertiesInternal mSensorPropertiesInternal;

            @Override
            public @NonNull View onCreateView(
                    @NonNull LayoutInflater inflater,
                    @Nullable ViewGroup container,
                    @Nullable Bundle savedInstanceState) {
                if (savedInstanceState != null) {
                    mFailureCount = savedInstanceState.getInt(EXTRA_FAILURE_COUNT, 0);
                }
                return inflater.inflate(
                        R.layout.fingerprint_check_enrolled_dialog, container, false);
            }

            @Override
            public @NonNull Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
                final Dialog dialog = super.onCreateDialog(savedInstanceState);
                if (dialog != null) {
                    mUserId = getArguments().getInt(KEY_USER_ID);
                    mSensorPropertiesInternal =
                            getArguments().getParcelable(KEY_SENSOR_PROPERTIES);

                    // Remove the default dialog title bar
                    dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);

                    dialog.setOnShowListener(dialogInterface -> {
                        final UdfpsCheckEnrolledView v =
                                dialog.findViewById(R.id.udfps_check_enrolled_view);
                        v.setSensorProperties(mSensorPropertiesInternal);
                        v.setOnTouchListener((view, event) -> {
                            Log.d(TAG, "CheckEnrollDialog dismissed: touch outside");
                            dialog.dismiss();
                            return false;
                        });
                    });
                }
                return dialog;
            }

            @Override
            public void onSaveInstanceState(@NonNull Bundle outState) {
                super.onSaveInstanceState(outState);
                outState.putInt(EXTRA_FAILURE_COUNT, mFailureCount);
            }

            @Override
            public void onStart() {
                super.onStart();
                if (getDialog() == null) {
                    return;
                }

                final Dialog dialog = getDialog();
                Window window = dialog.getWindow();
                WindowManager.LayoutParams params = window.getAttributes();

                // Make the dialog fullscreen
                params.width = WindowManager.LayoutParams.MATCH_PARENT;
                params.height = WindowManager.LayoutParams.MATCH_PARENT;
                params.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
                params.setFitInsetsTypes(0);
                window.setAttributes(params);
                window.getDecorView().getWindowInsetsController().hide(
                        WindowInsets.Type.statusBars());
                window.getDecorView().getWindowInsetsController().setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                window.setBackgroundDrawableResource(android.R.color.black);

                final TextView message =
                        dialog.findViewById(R.id.udfps_fingerprint_sensor_message);
                final Vibrator vibrator = getContext().getSystemService(Vibrator.class);
                final FingerprintManager fpm = Utils.getFingerprintManagerOrNull(getContext());
                mCancellationSignal = new CancellationSignal();
                fpm.authenticate(
                        null /* crypto */,
                        mCancellationSignal,
                        new FingerprintManager.AuthenticationCallback() {
                            @Override
                            public void onAuthenticationError(
                                    int errorCode, @NonNull CharSequence errString) {
                                mMetricsFeatureProvider.action(
                                        getContext(),
                                        SettingsEnums.ACTION_CHECK_FINGERPRINT_SETTINGS,
                                        false);
                                dialog.dismiss();
                            }

                            @Override
                            public void onAuthenticationSucceeded(
                                    @NonNull FingerprintManager.AuthenticationResult result) {
                                int fingerId = result.getFingerprint().getBiometricId();
                                FingerprintSettingsFragment parent =
                                        (FingerprintSettingsFragment) getTargetFragment();
                                parent.highlightFingerprintItem(fingerId);
                                mMetricsFeatureProvider.action(
                                        getContext(),
                                        SettingsEnums.ACTION_CHECK_FINGERPRINT_SETTINGS,
                                        true);
                                dialog.dismiss();
                            }

                            @Override
                            public void onAuthenticationFailed() {
                                vibrator.vibrate(
                                        VibrationEffect.get(VibrationEffect.EFFECT_DOUBLE_CLICK));
                                message.setText(R.string.fingerprint_check_enroll_not_recognized);
                                message.postDelayed(() -> {
                                    message.setText(R.string.fingerprint_check_enroll_touch_sensor);
                                }, 2000);
                                mFailureCount++;
                                if (mFailureCount >= MAX_FAILURE_COUNT) {
                                    Log.d(TAG, "CheckEnrollDialog dismissed: failed 3 times");
                                    dialog.dismiss();
                                }
                            }
                        },
                        null /* handler */,
                        mUserId);
            }

            @Override
            public void onDismiss(@NonNull DialogInterface dialog) {
                super.onDismiss(dialog);
                if (mCancellationSignal != null) {
                    mCancellationSignal.cancel();
                    mCancellationSignal = null;
                }
            }

            @Override
            public int getMetricsCategory() {
                return 0;
            }
        }

        public static class RenameDialog extends InstrumentedDialogFragment {

            private Fingerprint mFp;
            private ImeAwareEditText mDialogTextField;
            private AlertDialog mAlertDialog;
            private @Nullable DialogInterface.OnDismissListener mDismissListener;
            private boolean mDeleteInProgress;

            public void setDeleteInProgress(boolean deleteInProgress) {
                mDeleteInProgress = deleteInProgress;
            }

            @Override
            public void onCancel(DialogInterface dialog) {
                super.onCancel(dialog);
                if (mDismissListener != null) {
                    mDismissListener.onDismiss(dialog);
                }
            }

            @Override
            public Dialog onCreateDialog(Bundle savedInstanceState) {
                mFp = getArguments().getParcelable("fingerprint");
                final String fingerName;
                final int textSelectionStart;
                final int textSelectionEnd;
                if (savedInstanceState != null) {
                    fingerName = savedInstanceState.getString("fingerName");
                    textSelectionStart = savedInstanceState.getInt("startSelection", -1);
                    textSelectionEnd = savedInstanceState.getInt("endSelection", -1);
                } else {
                    fingerName = null;
                    textSelectionStart = -1;
                    textSelectionEnd = -1;
                }
                mAlertDialog = new AlertDialog.Builder(getActivity())
                        .setView(R.layout.fingerprint_rename_dialog)
                        .setPositiveButton(R.string.security_settings_fingerprint_enroll_dialog_ok,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        final String newName =
                                                mDialogTextField.getText().toString();
                                        final CharSequence name = mFp.getName();
                                        if (!TextUtils.equals(newName, name)) {
                                            Log.d(TAG, "rename " + name + " to " + newName);
                                            mMetricsFeatureProvider.action(getContext(),
                                                    SettingsEnums.ACTION_FINGERPRINT_RENAME,
                                                    mFp.getBiometricId());
                                            FingerprintSettingsFragment parent
                                                    = (FingerprintSettingsFragment)
                                                    getTargetFragment();
                                            parent.renameFingerPrint(mFp.getBiometricId(),
                                                    newName);
                                        }
                                        if (mDismissListener != null) {
                                            mDismissListener.onDismiss(dialog);
                                        }
                                        dialog.dismiss();
                                    }
                                })
                        .create();
                mAlertDialog.setOnShowListener(new DialogInterface.OnShowListener() {
                    @Override
                    public void onShow(DialogInterface dialog) {
                        mDialogTextField = mAlertDialog.findViewById(R.id.fingerprint_rename_field);
                        CharSequence name = fingerName == null ? mFp.getName() : fingerName;
                        mDialogTextField.setText(name);
                        mDialogTextField.setFilters(getFilters());
                        if (textSelectionStart != -1 && textSelectionEnd != -1) {
                            mDialogTextField.setSelection(textSelectionStart, textSelectionEnd);
                        } else {
                            mDialogTextField.selectAll();
                        }
                        if (mDeleteInProgress) {
                            mAlertDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
                        }
                        mDialogTextField.requestFocus();
                        mDialogTextField.scheduleShowSoftInput();
                    }
                });
                return mAlertDialog;
            }

            public void setOnDismissListener(@NonNull DialogInterface.OnDismissListener listener) {
                mDismissListener = listener;
            }

            public void enableDelete() {
                mDeleteInProgress = false;
                if (mAlertDialog != null) {
                    mAlertDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);
                }
            }

            @Override
            public void onSaveInstanceState(Bundle outState) {
                super.onSaveInstanceState(outState);
                if (mDialogTextField != null) {
                    outState.putString("fingerName", mDialogTextField.getText().toString());
                    outState.putInt("startSelection", mDialogTextField.getSelectionStart());
                    outState.putInt("endSelection", mDialogTextField.getSelectionEnd());
                }
            }

            @Override
            public int getMetricsCategory() {
                return SettingsEnums.DIALOG_FINGERPRINT_RENAME;
            }
        }

        public static class ConfirmLastDeleteDialog extends InstrumentedDialogFragment {

            private Fingerprint mFp;

            @Override
            public int getMetricsCategory() {
                return SettingsEnums.DIALOG_FINGERPINT_DELETE_LAST;
            }

            @Override
            public Dialog onCreateDialog(Bundle savedInstanceState) {
                mFp = getArguments().getParcelable("fingerprint");
                final boolean isProfileChallengeUser =
                        getArguments().getBoolean("isProfileChallengeUser");

                final String title = getString(R.string.fingerprint_delete_title, mFp.getName());
                final String message =
                        getString(R.string.fingerprint_v2_delete_message, mFp.getName());

                DevicePolicyManager devicePolicyManager =
                        getContext().getSystemService(DevicePolicyManager.class);
                String messageId =
                        isProfileChallengeUser ? WORK_PROFILE_FINGERPRINT_LAST_DELETE_MESSAGE
                                : UNDEFINED;
                int defaultMessageId = isProfileChallengeUser
                        ? R.string.fingerprint_last_delete_message_profile_challenge
                        : R.string.fingerprint_last_delete_message;

                final AlertDialog alertDialog = new AlertDialog.Builder(getActivity())
                        .setTitle(title)
                        .setMessage(devicePolicyManager.getResources().getString(
                                messageId,
                                () -> message + "\n\n" + getContext().getString(defaultMessageId)))
                        .setPositiveButton(
                                R.string.security_settings_fingerprint_enroll_dialog_delete,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        FingerprintSettingsFragment parent
                                                = (FingerprintSettingsFragment) getTargetFragment();
                                        parent.deleteFingerPrint(mFp);
                                        dialog.dismiss();
                                    }
                                })
                        .setNegativeButton(
                                R.string.cancel,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                    }
                                })
                        .create();
                return alertDialog;
            }
        }
    }

    public static class FingerprintPreference extends TwoTargetPreference {

        private final OnDeleteClickListener mOnDeleteClickListener;

        private Fingerprint mFingerprint;
        private View mView;
        private View mDeleteView;

        public interface OnDeleteClickListener {
            void onDeleteClick(FingerprintPreference p);
        }

        public FingerprintPreference(Context context, OnDeleteClickListener onDeleteClickListener) {
            super(context);
            mOnDeleteClickListener = onDeleteClickListener;
        }

        public View getView() {
            return mView;
        }

        public void setFingerprint(Fingerprint item) {
            mFingerprint = item;
        }

        public Fingerprint getFingerprint() {
            return mFingerprint;
        }

        @Override
        protected int getSecondTargetResId() {
            return R.layout.preference_widget_delete;
        }

        @Override
        public void onBindViewHolder(PreferenceViewHolder view) {
            super.onBindViewHolder(view);
            mView = view.itemView;
            mDeleteView = view.itemView.findViewById(R.id.delete_button);
            if (mFingerprint != null) {
                mDeleteView.setContentDescription(
                        mDeleteView.getContentDescription()
                                + " " + mFingerprint.getName().toString());
            }
            mDeleteView.setOnClickListener(v -> {
                if (mOnDeleteClickListener != null) {
                    mOnDeleteClickListener.onDeleteClick(FingerprintPreference.this);
                }
            });
        }
    }
}
