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

package com.android.settings.accessibility;

import static com.android.internal.accessibility.common.ShortcutConstants.UserShortcutType.HARDWARE;
import static com.android.internal.accessibility.common.ShortcutConstants.UserShortcutType.QUICK_SETTINGS;
import static com.android.internal.accessibility.common.ShortcutConstants.UserShortcutType.SOFTWARE;
import static com.android.internal.accessibility.common.ShortcutConstants.UserShortcutType.TRIPLETAP;
import static com.android.internal.accessibility.common.ShortcutConstants.UserShortcutType.TWOFINGER_DOUBLETAP;
import static com.android.settings.accessibility.AccessibilityUtil.State.OFF;
import static com.android.settings.accessibility.AccessibilityUtil.State.ON;
import static com.android.settings.accessibility.MagnificationCapabilities.MagnificationMode;
import static com.android.settings.accessibility.ToggleScreenMagnificationPreferenceFragment.KEY_MAGNIFICATION_SHORTCUT_PREFERENCE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.settings.SettingsEnums;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.icu.text.CaseMap;
import android.net.Uri;
import android.os.Bundle;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.provider.DeviceConfig;
import android.provider.Settings;
import android.view.InputDevice;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.preference.Preference;
import androidx.preference.TwoStatePreference;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

import com.android.server.accessibility.Flags;
import com.android.settings.DialogCreatable;
import com.android.settings.R;
import com.android.settings.SettingsActivity;
import com.android.settings.accessibility.AccessibilityDialogUtils.DialogEnums;
import com.android.settings.accessibility.MagnificationCapabilities.MagnificationMode;
import com.android.settings.testutils.shadow.ShadowAccessibilityManager;
import com.android.settings.testutils.shadow.ShadowDeviceConfig;
import com.android.settings.testutils.shadow.ShadowInputDevice;
import com.android.settings.testutils.shadow.ShadowStorageManager;
import com.android.settings.testutils.shadow.ShadowUserManager;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.search.SearchIndexableRaw;

import com.google.common.truth.Correspondence;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowContentResolver;
import org.robolectric.shadows.ShadowPackageManager;
import org.robolectric.shadows.ShadowSettings;
import org.robolectric.shadows.androidx.fragment.FragmentController;
import org.robolectric.util.ReflectionHelpers;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** Tests for {@link ToggleScreenMagnificationPreferenceFragment}. */
@RunWith(RobolectricTestRunner.class)
@Config(shadows = {
        ShadowUserManager.class,
        ShadowStorageManager.class,
        ShadowSettings.ShadowSecure.class,
        ShadowDeviceConfig.class,
        ShadowAccessibilityManager.class,
})
public class ToggleScreenMagnificationPreferenceFragmentTest {

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    private static final String PLACEHOLDER_PACKAGE_NAME = "com.mock.example";
    private static final String PLACEHOLDER_CLASS_NAME =
            PLACEHOLDER_PACKAGE_NAME + ".mock_a11y_service";
    private static final ComponentName PLACEHOLDER_COMPONENT_NAME = new ComponentName(
            PLACEHOLDER_PACKAGE_NAME, PLACEHOLDER_CLASS_NAME);
    private static final String PLACEHOLDER_DIALOG_TITLE = "title";

    private static final String SOFTWARE_SHORTCUT_KEY =
            Settings.Secure.ACCESSIBILITY_BUTTON_TARGETS;
    private static final String HARDWARE_SHORTCUT_KEY =
            Settings.Secure.ACCESSIBILITY_SHORTCUT_TARGET_SERVICE;
    private static final String TRIPLETAP_SHORTCUT_KEY =
            Settings.Secure.ACCESSIBILITY_DISPLAY_MAGNIFICATION_ENABLED;
    private static final String TWO_FINGER_TRIPLE_TAP_SHORTCUT_KEY =
            Settings.Secure.ACCESSIBILITY_MAGNIFICATION_TWO_FINGER_TRIPLE_TAP_ENABLED;

    private static final String MAGNIFICATION_CONTROLLER_NAME =
            "com.android.server.accessibility.MagnificationController";

    private static final String KEY_FOLLOW_TYPING =
            Settings.Secure.ACCESSIBILITY_MAGNIFICATION_FOLLOW_TYPING_ENABLED;
    private static final String KEY_SINGLE_FINGER_PANNING =
            Settings.Secure.ACCESSIBILITY_SINGLE_FINGER_PANNING_ENABLED;
    private static final String KEY_ALWAYS_ON =
            Settings.Secure.ACCESSIBILITY_MAGNIFICATION_ALWAYS_ON_ENABLED;
    private static final String KEY_JOYSTICK =
            Settings.Secure.ACCESSIBILITY_MAGNIFICATION_JOYSTICK_ENABLED;

    private FragmentController<ToggleScreenMagnificationPreferenceFragment> mFragController;
    private Context mContext;
    private Resources mSpyResources;
    private ShadowPackageManager mShadowPackageManager;
    private ShadowAccessibilityManager mShadowAccessibilityManager;

    @Before
    public void setUpTestFragment() {
        mContext = spy(ApplicationProvider.getApplicationContext());
        mShadowAccessibilityManager = Shadow.extract(
                mContext.getSystemService(AccessibilityManager.class));

        // Set up the fragment that support window magnification feature
        mSpyResources = spy(mContext.getResources());
        mShadowPackageManager = Shadows.shadowOf(mContext.getPackageManager());
        Context spyContext = spy(mContext);
        when(spyContext.getResources()).thenReturn(mSpyResources);

        setWindowMagnificationSupported(
                /* magnificationAreaSupported= */ true,
                /* windowMagnificationSupported= */ true);

        TestToggleScreenMagnificationPreferenceFragment fragment =
                new TestToggleScreenMagnificationPreferenceFragment();
        fragment.setArguments(new Bundle());
        fragment.setContext(spyContext);

        mFragController = FragmentController.of(fragment, SettingsActivity.class);
    }

    @After
    public void tearDown() {
        ShadowDeviceConfig.reset();
        ShadowInputDevice.reset();
    }

    @Test
    public void onResume_defaultStateForMagnificationMode_preferenceShouldReturnFullScreen() {
        mFragController.create(R.id.main_content, /* bundle= */ null).start().resume();

        // Default is MagnificationMode.FULLSCREEN
        final String expected =
                MagnificationCapabilities.getSummary(mContext, MagnificationMode.FULLSCREEN);

        final Preference preference = mFragController.get().findPreference(
                MagnificationModePreferenceController.PREF_KEY);
        assertThat(preference).isNotNull();
        assertThat(preference.getSummary()).isEqualTo(expected);
    }

    @Test
    public void onResume_setMagnificationModeToAll_preferenceShouldReturnAll() {
        setKeyMagnificationMode(MagnificationMode.ALL);

        mFragController.create(R.id.main_content, /* bundle= */ null).start().resume();

        final String expected =
                MagnificationCapabilities.getSummary(mContext, MagnificationMode.ALL);

        final Preference preference = mFragController.get().findPreference(
                MagnificationModePreferenceController.PREF_KEY);
        assertThat(preference).isNotNull();
        assertThat(preference.getSummary()).isEqualTo(expected);
    }

    @Test
    public void onResume_defaultStateForFollowingTyping_switchPreferenceShouldReturnTrue() {
        setKeyFollowTypingEnabled(true);

        mFragController.create(R.id.main_content, /* bundle= */ null).start().resume();

        final TwoStatePreference switchPreference = mFragController.get().findPreference(
                MagnificationFollowTypingPreferenceController.PREF_KEY);
        assertThat(switchPreference).isNotNull();
        assertThat(switchPreference.isChecked()).isTrue();
    }

    @Test
    public void onResume_disableFollowingTyping_switchPreferenceShouldReturnFalse() {
        setKeyFollowTypingEnabled(false);

        mFragController.create(R.id.main_content, /* bundle= */ null).start().resume();

        final TwoStatePreference switchPreference = mFragController.get().findPreference(
                MagnificationFollowTypingPreferenceController.PREF_KEY);
        assertThat(switchPreference).isNotNull();
        assertThat(switchPreference.isChecked()).isFalse();
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MAGNIFICATION_ONE_FINGER_PANNING_GESTURE)
    public void onResume_defaultStateForOneFingerPan_switchPreferenceShouldReturnFalse() {
        mFragController.create(R.id.main_content, /* bundle= */ null).start().resume();

        final TwoStatePreference switchPreference = mFragController.get().findPreference(
                MagnificationOneFingerPanningPreferenceController.PREF_KEY);
        assertThat(switchPreference).isNotNull();
        assertThat(switchPreference.isChecked()).isFalse();
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MAGNIFICATION_ONE_FINGER_PANNING_GESTURE)
    public void onResume_enableOneFingerPan_switchPreferenceShouldReturnTrue() {
        setKeyOneFingerPanEnabled(true);

        mFragController.create(R.id.main_content, /* bundle= */ null).start().resume();

        final TwoStatePreference switchPreference = mFragController.get().findPreference(
                MagnificationOneFingerPanningPreferenceController.PREF_KEY);
        assertThat(switchPreference).isNotNull();
        assertThat(switchPreference.isChecked()).isTrue();
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MAGNIFICATION_ONE_FINGER_PANNING_GESTURE)
    public void onResume_disableOneFingerPan_switchPreferenceShouldReturnFalse() {
        setKeyOneFingerPanEnabled(false);

        mFragController.create(R.id.main_content, /* bundle= */ null).start().resume();

        final TwoStatePreference switchPreference = mFragController.get().findPreference(
                MagnificationOneFingerPanningPreferenceController.PREF_KEY);
        assertThat(switchPreference).isNotNull();
        assertThat(switchPreference.isChecked()).isFalse();
    }

    @Test
    public void onResume_defaultStateForAlwaysOn_switchPreferenceShouldReturnTrue() {
        setAlwaysOnSupported(true);

        mFragController.create(R.id.main_content, /* bundle= */ null).start().resume();

        final TwoStatePreference switchPreference = mFragController.get().findPreference(
                MagnificationAlwaysOnPreferenceController.PREF_KEY);
        assertThat(switchPreference).isNotNull();
        assertThat(switchPreference.isChecked()).isTrue();
    }

    @Test
    public void onResume_enableAlwaysOn_switchPreferenceShouldReturnTrue() {
        setAlwaysOnSupported(true);
        setKeyAlwaysOnEnabled(true);

        mFragController.create(R.id.main_content, /* bundle= */ null).start().resume();

        final TwoStatePreference switchPreference = mFragController.get().findPreference(
                MagnificationAlwaysOnPreferenceController.PREF_KEY);
        assertThat(switchPreference).isNotNull();
        assertThat(switchPreference.isChecked()).isTrue();
    }

    @Test
    public void onResume_disableAlwaysOn_switchPreferenceShouldReturnFalse() {
        setAlwaysOnSupported(true);
        setKeyAlwaysOnEnabled(false);

        mFragController.create(R.id.main_content, /* bundle= */ null).start().resume();

        final TwoStatePreference switchPreference = mFragController.get().findPreference(
                MagnificationAlwaysOnPreferenceController.PREF_KEY);
        assertThat(switchPreference).isNotNull();
        assertThat(switchPreference.isChecked()).isFalse();
    }

    @Test
    public void onResume_defaultStateForJoystick_switchPreferenceShouldReturnFalse() {
        setJoystickSupported(true);

        mFragController.create(R.id.main_content, /* bundle= */ null).start().resume();

        final TwoStatePreference switchPreference = mFragController.get().findPreference(
                MagnificationJoystickPreferenceController.PREF_KEY);
        assertThat(switchPreference).isNotNull();
        assertThat(switchPreference.isChecked()).isFalse();
    }

    @Test
    public void onResume_enableJoystick_switchPreferenceShouldReturnTrue() {
        setJoystickSupported(true);
        setKeyJoystickEnabled(true);

        mFragController.create(R.id.main_content, /* bundle= */ null).start().resume();

        final TwoStatePreference switchPreference = mFragController.get().findPreference(
                MagnificationJoystickPreferenceController.PREF_KEY);
        assertThat(switchPreference).isNotNull();
        assertThat(switchPreference.isChecked()).isTrue();
    }

    @Test
    public void onResume_disableJoystick_switchPreferenceShouldReturnFalse() {
        setJoystickSupported(true);
        setKeyJoystickEnabled(false);

        mFragController.create(R.id.main_content, /* bundle= */ null).start().resume();

        final TwoStatePreference switchPreference = mFragController.get().findPreference(
                MagnificationJoystickPreferenceController.PREF_KEY);
        assertThat(switchPreference).isNotNull();
        assertThat(switchPreference.isChecked()).isFalse();
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_LOW_VISION_HATS)
    public void onResume_enableLowVisionHaTS_feedbackPreferenceShouldReturnNotNull() {
        mFragController.create(R.id.main_content, /* bundle= */ null).start().resume();

        final Preference feedbackPreference = mFragController.get().findPreference(
                MagnificationFeedbackPreferenceController.PREF_KEY);
        assertThat(feedbackPreference).isNotNull();
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_LOW_VISION_HATS)
    public void onResume_disableLowVisionHaTS_feedbackPreferenceShouldReturnNull() {
        mFragController.create(R.id.main_content, /* bundle= */ null).start().resume();

        final Preference feedbackPreference = mFragController.get().findPreference(
                MagnificationFeedbackPreferenceController.PREF_KEY);
        assertThat(feedbackPreference).isNull();
    }

    @Test
    public void onResume_haveRegisterToSpecificUris() {
        ShadowContentResolver shadowContentResolver = Shadows.shadowOf(
                mContext.getContentResolver());
        Uri[] observedUri = new Uri[]{
                Settings.Secure.getUriFor(Settings.Secure.ACCESSIBILITY_BUTTON_TARGETS),
                Settings.Secure.getUriFor(
                        Settings.Secure.ACCESSIBILITY_SHORTCUT_TARGET_SERVICE),
                Settings.Secure.getUriFor(
                        Settings.Secure.ACCESSIBILITY_QS_TARGETS),
                Settings.Secure.getUriFor(
                        Settings.Secure.ACCESSIBILITY_MAGNIFICATION_FOLLOW_TYPING_ENABLED),
                Settings.Secure.getUriFor(
                        Settings.Secure.ACCESSIBILITY_MAGNIFICATION_ALWAYS_ON_ENABLED)
        };
        for (Uri uri : observedUri) {
            // verify no observer registered before launching the fragment
            assertThat(shadowContentResolver.getContentObservers(uri)).isEmpty();
        }

        mFragController.create(R.id.main_content, /* bundle= */ null).start().resume();

        for (Uri uri : observedUri) {
            Collection<ContentObserver> observers = shadowContentResolver.getContentObservers(uri);
            assertThat(observers.size()).isEqualTo(1);
            assertThat(observers.stream().findFirst().get()).isInstanceOf(
                    AccessibilitySettingsContentObserver.class);
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MAGNIFICATION_ONE_FINGER_PANNING_GESTURE)
    public void onResume_oneFingerPanningFlagOn_registerToSpecificUri() {
        ShadowContentResolver shadowContentResolver = Shadows.shadowOf(
                mContext.getContentResolver());
        Uri observedUri = Settings.Secure.getUriFor(
                Settings.Secure.ACCESSIBILITY_SINGLE_FINGER_PANNING_ENABLED);
        // verify no one finger panning settings observer registered before launching the fragment
        assertThat(shadowContentResolver.getContentObservers(observedUri)).isEmpty();

        mFragController.create(R.id.main_content, /* bundle= */ null).start().resume();

        Collection<ContentObserver> observers =
                shadowContentResolver.getContentObservers(observedUri);
        assertThat(observers.size()).isEqualTo(1);
        assertThat(observers.stream().findFirst().get()).isInstanceOf(
                AccessibilitySettingsContentObserver.class);
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_MAGNIFICATION_ONE_FINGER_PANNING_GESTURE)
    public void onResume_oneFingerPanningFlagOff_notRegisterToSpecificUri() {
        ShadowContentResolver shadowContentResolver = Shadows.shadowOf(
                mContext.getContentResolver());
        Uri observedUri = Settings.Secure.getUriFor(
                Settings.Secure.ACCESSIBILITY_SINGLE_FINGER_PANNING_ENABLED);
        // verify no one finger panning settings observer registered before launching the fragment
        assertThat(shadowContentResolver.getContentObservers(observedUri)).isEmpty();

        mFragController.create(R.id.main_content, /* bundle= */ null).start().resume();
        // verify no one finger panning settings observer registered after launching the fragment
        assertThat(shadowContentResolver.getContentObservers(observedUri)).isEmpty();
    }

    @Test
    public void updateShortcutPreferenceData_assignDefaultValueToVariable() {
        mFragController.create(R.id.main_content, /* bundle= */ null).start().resume();

        mFragController.get().updateShortcutPreferenceData();

        final int expectedType = PreferredShortcuts.retrieveUserShortcutType(mContext,
                MAGNIFICATION_CONTROLLER_NAME);
        // Compare to default UserShortcutType
        assertThat(expectedType).isEqualTo(SOFTWARE);
    }

    @Test
    public void updateShortcutPreferenceData_hasValueInSettings_assignToVariable() {
        mShadowAccessibilityManager.setAccessibilityShortcutTargets(
                TRIPLETAP | SOFTWARE, List.of(MAGNIFICATION_CONTROLLER_NAME));
        mFragController.create(R.id.main_content, /* bundle= */ null).start().resume();

        mFragController.get().updateShortcutPreferenceData();

        final int expectedType = PreferredShortcuts.retrieveUserShortcutType(mContext,
                MAGNIFICATION_CONTROLLER_NAME);
        assertThat(expectedType).isEqualTo(SOFTWARE | TRIPLETAP);
    }

    @Test
    public void updateShortcutPreferenceData_hasValueInSharedPreference_assignToVariable() {
        final PreferredShortcut tripleTapShortcut = new PreferredShortcut(
                MAGNIFICATION_CONTROLLER_NAME, TRIPLETAP);
        putUserShortcutTypeIntoSharedPreference(mContext, tripleTapShortcut);
        mFragController.create(R.id.main_content, /* bundle= */ null).start().resume();

        mFragController.get().updateShortcutPreferenceData();

        final int expectedType = PreferredShortcuts.retrieveUserShortcutType(mContext,
                MAGNIFICATION_CONTROLLER_NAME);
        assertThat(expectedType).isEqualTo(TRIPLETAP);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MAGNIFICATION_MULTIPLE_FINGER_MULTIPLE_TAP_GESTURE)
    public void updateShortcutPreferenceData_hasTwoFingerTripleTapInSettings_assignToVariable() {
        mShadowAccessibilityManager.setAccessibilityShortcutTargets(
                TWOFINGER_DOUBLETAP, List.of(MAGNIFICATION_CONTROLLER_NAME));
        mFragController.create(R.id.main_content, /* bundle= */ null).start().resume();

        mFragController.get().updateShortcutPreferenceData();

        final int expectedType = PreferredShortcuts.retrieveUserShortcutType(mContext,
                MAGNIFICATION_CONTROLLER_NAME);
        assertThat(expectedType).isEqualTo(TWOFINGER_DOUBLETAP);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MAGNIFICATION_MULTIPLE_FINGER_MULTIPLE_TAP_GESTURE)
    public void updateShortcutPreferenceData_hasTwoFingerTripleTapInSharedPref_assignToVariable() {
        final PreferredShortcut tripleTapShortcut = new PreferredShortcut(
                MAGNIFICATION_CONTROLLER_NAME, TWOFINGER_DOUBLETAP);
        putUserShortcutTypeIntoSharedPreference(mContext, tripleTapShortcut);
        mFragController.create(R.id.main_content, /* bundle= */ null).start().resume();

        mFragController.get().updateShortcutPreferenceData();

        final int expectedType = PreferredShortcuts.retrieveUserShortcutType(mContext,
                MAGNIFICATION_CONTROLLER_NAME);
        assertThat(expectedType).isEqualTo(TWOFINGER_DOUBLETAP);
    }

    @Test
    public void onCreateView_magnificationAreaNotSupported_settingsPreferenceIsNull() {
        setWindowMagnificationSupported(
                /* magnificationAreaSupported= */ false,
                /* windowMagnificationSupported= */ true);

        mFragController.create(R.id.main_content, /* bundle= */ null).start().resume();

        assertThat(mFragController.get().mSettingsPreference).isNull();
    }

    @Test
    public void onCreateView_windowMagnificationNotSupported_settingsPreferenceIsNull() {
        setWindowMagnificationSupported(
                /* magnificationAreaSupported= */ true,
                /* windowMagnificationSupported= */ false);

        mFragController.create(R.id.main_content, /* bundle= */ null).start().resume();

        assertThat(mFragController.get().mSettingsPreference).isNull();
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_MAGNIFICATION_ONE_FINGER_PANNING_GESTURE)
    public void onCreateView_oneFingerPanNotSupported_settingsPreferenceIsNull() {
        mFragController.create(R.id.main_content, /* bundle= */ null).start().resume();

        final TwoStatePreference switchPreference = mFragController.get().findPreference(
                MagnificationOneFingerPanningPreferenceController.PREF_KEY);
        assertThat(switchPreference).isNull();
    }

    @Test
    public void onCreateView_alwaysOnNotSupported_settingsPreferenceIsNull() {
        setAlwaysOnSupported(false);

        mFragController.create(R.id.main_content, /* bundle= */ null).start().resume();

        final TwoStatePreference switchPreference = mFragController.get().findPreference(
                MagnificationAlwaysOnPreferenceController.PREF_KEY);
        assertThat(switchPreference).isNull();
    }

    @Test
    public void onCreateView_joystickNotSupported_settingsPreferenceIsNull() {
        setJoystickSupported(false);

        mFragController.create(R.id.main_content, /* bundle= */ null).start().resume();

        final TwoStatePreference switchPreference = mFragController.get().findPreference(
                MagnificationJoystickPreferenceController.PREF_KEY);
        assertThat(switchPreference).isNull();
    }

    @Test
    @DisableFlags(com.android.settings.accessibility.Flags
                .FLAG_ENABLE_MAGNIFICATION_CURSOR_FOLLOWING_DIALOG)
    @Config(shadows = ShadowInputDevice.class)
    public void onCreateView_cursorFollowingModeDisabled_settingsPreferenceIsNull() {
        addMouseDevice();

        mFragController.create(R.id.main_content, /* bundle= */null).start().resume().get();

        final Preference preference = mFragController.get().findPreference(
                MagnificationCursorFollowingModePreferenceController.PREF_KEY);
        assertThat(preference).isNull();
    }

    @Test
    @EnableFlags(com.android.settings.accessibility.Flags
                .FLAG_ENABLE_MAGNIFICATION_CURSOR_FOLLOWING_DIALOG)
    public void onCreateView_cursorFollowingModeEnabled_settingsPreferenceIsNullWithoutMouse() {
        mFragController.create(R.id.main_content, /* bundle= */null).start().resume().get();

        final Preference preference = mFragController.get().findPreference(
                MagnificationCursorFollowingModePreferenceController.PREF_KEY);
        assertThat(preference).isNull();
    }

    @Test
    @EnableFlags(com.android.settings.accessibility.Flags
                .FLAG_ENABLE_MAGNIFICATION_CURSOR_FOLLOWING_DIALOG)
    @Config(shadows = ShadowInputDevice.class)
    public void onCreateView_cursorFollowingModeEnabled_settingsPreferenceIsNotNullWithMouse() {
        addMouseDevice();

        mFragController.create(R.id.main_content, /* bundle= */null).start().resume().get();

        final Preference preference = mFragController.get().findPreference(
                MagnificationCursorFollowingModePreferenceController.PREF_KEY);
        assertThat(preference).isNotNull();
    }

    @Test
    public void onCreateView_setDialogDelegateAndAddTheControllerToLifeCycleObserver() {
        Correspondence instanceOf = Correspondence.transforming(
                observer -> (observer instanceof MagnificationModePreferenceController),
                "contains MagnificationModePreferenceController");

        ToggleScreenMagnificationPreferenceFragment fragment = mFragController.create(
                R.id.main_content, /* bundle= */ null).start().resume().get();

        DialogCreatable dialogDelegate = ReflectionHelpers.getField(fragment,
                "mMagnificationModeDialogDelegate");
        List<LifecycleObserver> lifecycleObservers = ReflectionHelpers.getField(
                fragment.getSettingsLifecycle(), "mObservers");
        assertThat(dialogDelegate).isInstanceOf(MagnificationModePreferenceController.class);
        assertThat(lifecycleObservers).isNotNull();
        assertThat(lifecycleObservers).comparingElementsUsing(instanceOf).contains(true);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MAGNIFICATION_ONE_FINGER_PANNING_GESTURE)
    public void onCreateView_oneFingerPanSupported_addControllerToLifeCycleObserver() {
        Correspondence instanceOf = Correspondence.transforming(
                observer -> (observer instanceof MagnificationOneFingerPanningPreferenceController),
                "contains MagnificationOneFingerPanningPreferenceController");

        ToggleScreenMagnificationPreferenceFragment fragment = mFragController.create(
                R.id.main_content, /* bundle= */ null).start().resume().get();

        List<LifecycleObserver> lifecycleObservers = ReflectionHelpers.getField(
                fragment.getSettingsLifecycle(), "mObservers");
        assertThat(lifecycleObservers).isNotNull();
        assertThat(lifecycleObservers).comparingElementsUsing(instanceOf).contains(true);
    }

    @Test
    public void onCreateView_alwaysOnSupported_addControllerToLifeCycleObserver() {
        setAlwaysOnSupported(true);

        Correspondence instanceOf = Correspondence.transforming(
                observer -> (observer instanceof MagnificationAlwaysOnPreferenceController),
                "contains MagnificationAlwaysOnPreferenceController");

        ToggleScreenMagnificationPreferenceFragment fragment = mFragController.create(
                R.id.main_content, /* bundle= */ null).start().resume().get();

        List<LifecycleObserver> lifecycleObservers = ReflectionHelpers.getField(
                fragment.getSettingsLifecycle(), "mObservers");
        assertThat(lifecycleObservers).isNotNull();
        assertThat(lifecycleObservers).comparingElementsUsing(instanceOf).contains(true);
    }

    @Test
    public void onCreateDialog_setMagnificationModeDialogDelegate_invokeDialogDelegate() {
        ToggleScreenMagnificationPreferenceFragment fragment =
                mFragController.create(
                        R.id.main_content, /* bundle= */ null).start().resume().get();
        final DialogCreatable dialogDelegate = mock(DialogCreatable.class, RETURNS_DEEP_STUBS);
        final int dialogId = DialogEnums.DIALOG_MAGNIFICATION_MODE;
        when(dialogDelegate.getDialogMetricsCategory(anyInt())).thenReturn(dialogId);
        fragment.setMagnificationModeDialogDelegate(dialogDelegate);

        fragment.onCreateDialog(dialogId);
        fragment.getDialogMetricsCategory(dialogId);
        verify(dialogDelegate).onCreateDialog(dialogId);
        verify(dialogDelegate).getDialogMetricsCategory(dialogId);
    }

    @Test
    @EnableFlags(com.android.settings.accessibility.Flags
                .FLAG_ENABLE_MAGNIFICATION_CURSOR_FOLLOWING_DIALOG)
    public void onCreateDialog_setCursorFollowingModeDialogDelegate_invokeDialogDelegate() {
        ToggleScreenMagnificationPreferenceFragment fragment =
                mFragController.create(
                        R.id.main_content, /* bundle= */ null).start().resume().get();
        final DialogCreatable dialogDelegate = mock(DialogCreatable.class, RETURNS_DEEP_STUBS);
        final int dialogId = DialogEnums.DIALOG_MAGNIFICATION_CURSOR_FOLLOWING_MODE;
        when(dialogDelegate.getDialogMetricsCategory(anyInt())).thenReturn(dialogId);
        fragment.setMagnificationCursorFollowingModeDialogDelegate(dialogDelegate);

        fragment.onCreateDialog(dialogId);
        fragment.getDialogMetricsCategory(dialogId);
        verify(dialogDelegate).onCreateDialog(dialogId);
        verify(dialogDelegate).getDialogMetricsCategory(dialogId);
    }

    @Test
    public void getMetricsCategory_returnsCorrectCategory() {
        ToggleScreenMagnificationPreferenceFragment fragment =
                mFragController.create(
                        R.id.main_content, /* bundle= */ null).start().resume().get();

        assertThat(fragment.getMetricsCategory()).isEqualTo(
                SettingsEnums.ACCESSIBILITY_TOGGLE_SCREEN_MAGNIFICATION);
    }

    @Test
    public void getHelpResource_returnsCorrectHelpResource() {
        ToggleScreenMagnificationPreferenceFragment fragment =
                mFragController.create(
                        R.id.main_content, /* bundle= */ null).start().resume().get();

        assertThat(fragment.getHelpResource()).isEqualTo(R.string.help_url_magnification);
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_MAGNIFICATION_ONE_FINGER_PANNING_GESTURE)
    public void
            onProcessArguments_defaultArgumentUnavailableAndFlagOff_shouldSetDefaultArguments() {
        ToggleScreenMagnificationPreferenceFragment fragment =
                mFragController.create(
                        R.id.main_content, /* bundle= */ null).start().resume().get();
        Bundle arguments = new Bundle();

        fragment.onProcessArguments(arguments);

        assertTrue(arguments.containsKey(AccessibilitySettings.EXTRA_PREFERENCE_KEY));
        assertTrue(arguments.containsKey(AccessibilitySettings.EXTRA_INTRO));
        assertTrue(arguments.containsKey(AccessibilitySettings.EXTRA_HTML_DESCRIPTION));
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MAGNIFICATION_ONE_FINGER_PANNING_GESTURE)
    public void
            onProcessArguments_defaultArgumentUnavailableAndFlagOn_shouldSetDefaultArguments() {
        ToggleScreenMagnificationPreferenceFragment fragment =
                mFragController.create(
                        R.id.main_content, /* bundle= */ null).start().resume().get();
        Bundle arguments = new Bundle();

        fragment.onProcessArguments(arguments);

        assertTrue(arguments.containsKey(AccessibilitySettings.EXTRA_PREFERENCE_KEY));
        assertTrue(arguments.containsKey(AccessibilitySettings.EXTRA_INTRO));
        // If OneFingerPanning flag is on, the EXTRA_HTML_DESCRIPTION should not be set. The html
        // description would be decided dynamically based on the OneFingerPanning preference state.
        assertFalse(arguments.containsKey(AccessibilitySettings.EXTRA_HTML_DESCRIPTION));
    }

    @Test
    public void getCurrentHtmlDescription_shouldNotBeEmpty() {
        ToggleScreenMagnificationPreferenceFragment fragment =
                mFragController.create(
                        R.id.main_content, /* bundle= */ null).start().resume().get();
        assertThat(fragment.getCurrentHtmlDescription().toString()).isNotEmpty();
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MAGNIFICATION_KEYBOARD_CONTROL)
    public void getCurrentHtmlDescription_doesNotIncludeKeyboardInfoIfNoKeyboardAttached() {
        ToggleScreenMagnificationPreferenceFragment fragment =
                mFragController.create(
                        R.id.main_content, /* bundle= */ null).start().resume().get();

        String htmlDescription = fragment.getCurrentHtmlDescription().toString();
        assertThat(htmlDescription).isNotEmpty();
        assertThat(htmlDescription).doesNotContain("keyboard");
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MAGNIFICATION_KEYBOARD_CONTROL)
    @Config(shadows = ShadowInputDevice.class)
    public void getCurrentHtmlDescription_includesKeyboardInfoIfKeyboardAttached() {
        int deviceId = 1;
        ShadowInputDevice.sDeviceIds = new int[]{deviceId};
        InputDevice device = ShadowInputDevice.makeFullKeyboardInputDevicebyId(deviceId);
        ShadowInputDevice.addDevice(deviceId, device);

        ToggleScreenMagnificationPreferenceFragment fragment =
                mFragController.create(
                        R.id.main_content, /* bundle= */ null).start().resume().get();

        String htmlDescription = fragment.getCurrentHtmlDescription().toString();
        assertThat(htmlDescription).isNotEmpty();
        assertThat(htmlDescription).contains("keyboard");
    }

    @Test
    public void getSummary_magnificationEnabled_returnShortcutOnWithSummary() {
        mShadowAccessibilityManager.setAccessibilityShortcutTargets(
                TRIPLETAP, List.of(MAGNIFICATION_CONTROLLER_NAME));

        assertThat(
                ToggleScreenMagnificationPreferenceFragment.getServiceSummary(mContext).toString())
                .isEqualTo(
                        mContext.getString(
                                com.android.settingslib.R.string
                                        .preference_summary_default_combination,
                                mContext.getText(R.string.accessibility_summary_shortcut_enabled),
                                mContext.getText(R.string.magnification_feature_summary)));
    }

    @Test
    public void getSummary_magnificationDisabled_returnShortcutOffWithSummary() {
        mShadowAccessibilityManager.setAccessibilityShortcutTargets(
                TRIPLETAP, List.of());

        assertThat(
                ToggleScreenMagnificationPreferenceFragment.getServiceSummary(mContext).toString())
                .isEqualTo(
                        mContext.getString(
                                com.android.settingslib.R.string
                                        .preference_summary_default_combination,
                                mContext.getText(
                                        R.string.generic_accessibility_feature_shortcut_off),
                                mContext.getText(R.string.magnification_feature_summary)));
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MAGNIFICATION_MULTIPLE_FINGER_MULTIPLE_TAP_GESTURE)
    public void getSummary_magnificationGestureEnabled_returnShortcutOnWithSummary() {
        mShadowAccessibilityManager.setAccessibilityShortcutTargets(
                TWOFINGER_DOUBLETAP, List.of(MAGNIFICATION_CONTROLLER_NAME));

        assertThat(
                ToggleScreenMagnificationPreferenceFragment.getServiceSummary(mContext).toString())
                .isEqualTo(
                        mContext.getString(
                                com.android.settingslib.R.string
                                        .preference_summary_default_combination,
                                mContext.getText(R.string.accessibility_summary_shortcut_enabled),
                                mContext.getText(R.string.magnification_feature_summary)));
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MAGNIFICATION_MULTIPLE_FINGER_MULTIPLE_TAP_GESTURE)
    public void getSummary_magnificationGestureDisabled_returnShortcutOffWithSummary() {
        mShadowAccessibilityManager.setAccessibilityShortcutTargets(
                TRIPLETAP | TWOFINGER_DOUBLETAP, List.of());

        assertThat(
                ToggleScreenMagnificationPreferenceFragment.getServiceSummary(mContext).toString())
                .isEqualTo(
                        mContext.getString(
                                com.android.settingslib.R.string
                                        .preference_summary_default_combination,
                                mContext.getText(
                                        R.string.generic_accessibility_feature_shortcut_off),
                                mContext.getText(R.string.magnification_feature_summary)));
    }

    @Test
    public void getShortcutTypeSummary_shortcutSummaryIsCorrectlySet() {
        final PreferredShortcut userPreferredShortcut = new PreferredShortcut(
                MAGNIFICATION_CONTROLLER_NAME,
                HARDWARE | QUICK_SETTINGS);
        putUserShortcutTypeIntoSharedPreference(mContext, userPreferredShortcut);
        final ShortcutPreference shortcutPreference =
                new ShortcutPreference(mContext, /* attrs= */ null);
        shortcutPreference.setChecked(true);
        shortcutPreference.setSettingsEditable(true);
        ToggleScreenMagnificationPreferenceFragment fragment =
                mFragController.create(R.id.main_content, /* bundle= */
                        null).start().resume().get();
        fragment.mShortcutPreference = shortcutPreference;
        String expected = CaseMap.toTitle().wholeString().noLowercase().apply(Locale.getDefault(),
                /* iter= */ null,
                mContext.getString(
                        R.string.accessibility_feature_shortcut_setting_summary_quick_settings)
                        + ", "
                        + mContext.getString(R.string.accessibility_shortcut_hardware_keyword));

        String summary = fragment.getShortcutTypeSummary(mContext).toString();

        assertThat(summary).isEqualTo(expected);
    }

    @Test
    @EnableFlags(
            com.android.settings.accessibility.Flags.FLAG_TOGGLE_FEATURE_FRAGMENT_COLLECTION_INFO)
    public void fragmentRecyclerView_getCollectionInfo_hasCorrectCounts() {
        ToggleScreenMagnificationPreferenceFragment fragment =
                mFragController.create(R.id.main_content, /* bundle= */
                        null).start().resume().get();
        RecyclerView rv = fragment.getListView();

        AccessibilityNodeInfoCompat node = AccessibilityNodeInfoCompat.obtain();
        rv.getCompatAccessibilityDelegate().onInitializeAccessibilityNodeInfo(rv, node);
        AccessibilityNodeInfo.CollectionInfo collectionInfo = node.unwrap().getCollectionInfo();

        // Asserting against specific item counts will be brittle to changes to the preferences
        // included on this page, so instead just check some properties of these counts.
        assertThat(collectionInfo.getColumnCount()).isEqualTo(1);
        assertThat(collectionInfo.getRowCount()).isEqualTo(collectionInfo.getItemCount());
        assertThat(collectionInfo.getItemCount())
                // One unimportant item: the illustration preference
                .isEqualTo(collectionInfo.getImportantForAccessibilityItemCount() + 1);
    }

    @Test
    @EnableFlags(com.android.settings.accessibility.Flags.FLAG_FIX_A11Y_SETTINGS_SEARCH)
    public void getRawDataToIndex_returnsAllPreferenceKeys() {
        final List<String> expectedSearchKeys = List.of(
                KEY_MAGNIFICATION_SHORTCUT_PREFERENCE,
                MagnificationModePreferenceController.PREF_KEY,
                MagnificationFollowTypingPreferenceController.PREF_KEY,
                MagnificationOneFingerPanningPreferenceController.PREF_KEY,
                MagnificationAlwaysOnPreferenceController.PREF_KEY,
                MagnificationJoystickPreferenceController.PREF_KEY,
                MagnificationCursorFollowingModePreferenceController.PREF_KEY,
                MagnificationFeedbackPreferenceController.PREF_KEY);

        final List<SearchIndexableRaw> rawData = ToggleScreenMagnificationPreferenceFragment
                .SEARCH_INDEX_DATA_PROVIDER.getRawDataToIndex(mContext, true);
        final List<String> actualSearchKeys = rawData.stream().map(raw -> raw.key).toList();

        assertThat(actualSearchKeys).containsExactlyElementsIn(expectedSearchKeys);
    }

    @Test
    @EnableFlags(com.android.settings.accessibility.Flags.FLAG_FIX_A11Y_SETTINGS_SEARCH)
    public void getNonIndexableKeys_windowMagnificationNotSupported_onlyShortcutSearchable() {
        setWindowMagnificationSupported(false, false);

        final List<String> niks = ToggleScreenMagnificationPreferenceFragment
                .SEARCH_INDEX_DATA_PROVIDER.getNonIndexableKeys(mContext);
        final List<SearchIndexableRaw> rawData = ToggleScreenMagnificationPreferenceFragment
                .SEARCH_INDEX_DATA_PROVIDER.getRawDataToIndex(mContext, true);
        // Expect all search data, except the shortcut preference, to be in NIKs.
        final List<String> expectedNiks = rawData.stream().map(raw -> raw.key)
                .filter(key -> !key.equals(KEY_MAGNIFICATION_SHORTCUT_PREFERENCE))
                .toList();

        // In NonIndexableKeys == not searchable
        assertThat(niks).containsExactlyElementsIn(expectedNiks);
    }

    @Test
    @EnableFlags({
            com.android.settings.accessibility.Flags.FLAG_FIX_A11Y_SETTINGS_SEARCH,
            Flags.FLAG_ENABLE_LOW_VISION_HATS})
    public void
            getNonIndexableKeys_windowMagnificationNotSupportedHatsOn_shortcutFeedbackSearchable() {
        setWindowMagnificationSupported(false, false);

        final List<String> niks = ToggleScreenMagnificationPreferenceFragment
                .SEARCH_INDEX_DATA_PROVIDER.getNonIndexableKeys(mContext);
        final List<SearchIndexableRaw> rawData = ToggleScreenMagnificationPreferenceFragment
                .SEARCH_INDEX_DATA_PROVIDER.getRawDataToIndex(mContext, true);
        // Expect all search data, except the shortcut preference and feedback preference, to be in
        // NIKs.
        final List<String> expectedNiks = rawData.stream().map(raw -> raw.key)
                .filter(key ->
                        !key.equals(KEY_MAGNIFICATION_SHORTCUT_PREFERENCE)
                        && !key.equals(MagnificationFeedbackPreferenceController.PREF_KEY))
                .toList();

        // In NonIndexableKeys == not searchable
        assertThat(niks).containsExactlyElementsIn(expectedNiks);
    }

    @Test
    @EnableFlags({
            com.android.settings.accessibility.Flags.FLAG_FIX_A11Y_SETTINGS_SEARCH,
            Flags.FLAG_ENABLE_MAGNIFICATION_ONE_FINGER_PANNING_GESTURE,
            Flags.FLAG_ENABLE_LOW_VISION_HATS,
            com.android.settings.accessibility.Flags
                    .FLAG_ENABLE_MAGNIFICATION_CURSOR_FOLLOWING_DIALOG})
    @Config(shadows = ShadowInputDevice.class)
    public void getNonIndexableKeys_hasShortcutAndAllFeaturesEnabled_allItemsSearchable() {
        mShadowAccessibilityManager.setAccessibilityShortcutTargets(
                TRIPLETAP, List.of(MAGNIFICATION_CONTROLLER_NAME));
        setAlwaysOnSupported(true);
        setJoystickSupported(true);
        addMouseDevice();

        final List<String> niks = ToggleScreenMagnificationPreferenceFragment
                .SEARCH_INDEX_DATA_PROVIDER.getNonIndexableKeys(mContext);

        // Empty NonIndexableKeys == all indexed items are searchable
        assertThat(niks).isEmpty();
    }

    @Test
    @EnableFlags(com.android.settings.accessibility.Flags.FLAG_FIX_A11Y_SETTINGS_SEARCH)
    public void getNonIndexableKeys_noShortcut_alwaysOnSupported_notSearchable() {
        mShadowAccessibilityManager.setAccessibilityShortcutTargets(
                TRIPLETAP, List.of());
        setAlwaysOnSupported(true);

        final List<String> niks = ToggleScreenMagnificationPreferenceFragment
                .SEARCH_INDEX_DATA_PROVIDER.getNonIndexableKeys(mContext);

        // In NonIndexableKeys == not searchable
        assertThat(niks).contains(MagnificationAlwaysOnPreferenceController.PREF_KEY);
    }

    @Test
    @EnableFlags(com.android.settings.accessibility.Flags.FLAG_FIX_A11Y_SETTINGS_SEARCH)
    public void getNonIndexableKeys_hasShortcut_alwaysOnNotSupported_notSearchable() {
        mShadowAccessibilityManager.setAccessibilityShortcutTargets(
                TRIPLETAP, List.of(MAGNIFICATION_CONTROLLER_NAME));
        setAlwaysOnSupported(false);

        final List<String> niks = ToggleScreenMagnificationPreferenceFragment
                .SEARCH_INDEX_DATA_PROVIDER.getNonIndexableKeys(mContext);

        // In NonIndexableKeys == not searchable
        assertThat(niks).contains(MagnificationAlwaysOnPreferenceController.PREF_KEY);
    }

    @Test
    @EnableFlags(com.android.settings.accessibility.Flags.FLAG_FIX_A11Y_SETTINGS_SEARCH)
    @DisableFlags(Flags.FLAG_ENABLE_MAGNIFICATION_ONE_FINGER_PANNING_GESTURE)
    public void getNonIndexableKeys_oneFingerPanningNotSupported_notSearchable() {
        final List<String> niks = ToggleScreenMagnificationPreferenceFragment
                .SEARCH_INDEX_DATA_PROVIDER.getNonIndexableKeys(mContext);

        // In NonIndexableKeys == not searchable
        assertThat(niks).contains(MagnificationOneFingerPanningPreferenceController.PREF_KEY);
    }

    @Test
    @EnableFlags(com.android.settings.accessibility.Flags.FLAG_FIX_A11Y_SETTINGS_SEARCH)
    public void getNonIndexableKeys_joystickNotSupported_notSearchable() {
        setJoystickSupported(false);

        final List<String> niks = ToggleScreenMagnificationPreferenceFragment
                .SEARCH_INDEX_DATA_PROVIDER.getNonIndexableKeys(mContext);

        // In NonIndexableKeys == not searchable
        assertThat(niks).contains(MagnificationJoystickPreferenceController.PREF_KEY);
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_LOW_VISION_HATS)
    public void getNonIndexableKeys_hatsNotSupported_notSearchable() {
        final List<String> niks = ToggleScreenMagnificationPreferenceFragment
                .SEARCH_INDEX_DATA_PROVIDER.getNonIndexableKeys(mContext);

        // In NonIndexableKeys == not searchable
        assertThat(niks).contains(MagnificationFeedbackPreferenceController.PREF_KEY);
    }

    private void putStringIntoSettings(String key, String componentName) {
        Settings.Secure.putString(mContext.getContentResolver(), key, componentName);
    }

    private void putUserShortcutTypeIntoSharedPreference(Context context,
            PreferredShortcut shortcut) {
        PreferredShortcuts.saveUserShortcutType(context, shortcut);
    }

    private void setKeyMagnificationMode(@MagnificationMode int mode) {
        MagnificationCapabilities.setCapabilities(mContext, mode);
    }

    private void setKeyFollowTypingEnabled(boolean enabled) {
        Settings.Secure.putInt(mContext.getContentResolver(), KEY_FOLLOW_TYPING,
                enabled ? ON : OFF);
    }

    private void setKeyOneFingerPanEnabled(boolean enabled) {
        Settings.Secure.putInt(mContext.getContentResolver(), KEY_SINGLE_FINGER_PANNING,
                enabled ? ON : OFF);
    }

    private void setAlwaysOnSupported(boolean supported) {
        ShadowDeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_WINDOW_MANAGER,
                "AlwaysOnMagnifier__enable_always_on_magnifier",
                supported ? "true" : "false",
                /* makeDefault= */ false);
    }

    private void setKeyAlwaysOnEnabled(boolean enabled) {
        Settings.Secure.putInt(mContext.getContentResolver(), KEY_ALWAYS_ON,
                enabled ? ON : OFF);
    }

    private void setJoystickSupported(boolean supported) {
        ShadowDeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_WINDOW_MANAGER,
                "MagnificationJoystick__enable_magnification_joystick",
                supported ? "true" : "false",
                /* makeDefault= */ false);
    }

    private void setKeyJoystickEnabled(boolean enabled) {
        Settings.Secure.putInt(mContext.getContentResolver(), KEY_JOYSTICK,
                enabled ? ON : OFF);
    }

    private void addMouseDevice() {
        int deviceId = 1;
        ShadowInputDevice.sDeviceIds = new int[]{deviceId};
        InputDevice device = ShadowInputDevice.makeInputDevicebyIdWithSources(deviceId,
                InputDevice.SOURCE_MOUSE);
        ShadowInputDevice.addDevice(deviceId, device);
    }

    private String getStringFromSettings(String key) {
        return Settings.Secure.getString(mContext.getContentResolver(), key);
    }

    private boolean getMagnificationTripleTapStatus() {
        return Settings.Secure.getInt(mContext.getContentResolver(), TRIPLETAP_SHORTCUT_KEY, OFF)
                == ON;
    }

    private void setWindowMagnificationSupported(boolean magnificationAreaSupported,
            boolean windowMagnificationSupported) {
        when(mSpyResources.getBoolean(
                com.android.internal.R.bool.config_magnification_area))
                .thenReturn(magnificationAreaSupported);
        mShadowPackageManager.setSystemFeature(PackageManager.FEATURE_WINDOW_MAGNIFICATION,
                windowMagnificationSupported);
    }

    /**
     * A test fragment that provides a way to change the context
     */
    public static class TestToggleScreenMagnificationPreferenceFragment
            extends ToggleScreenMagnificationPreferenceFragment {
        private Context mContext;

        @Override
        public Context getContext() {
            return this.mContext != null ? this.mContext : super.getContext();
        }

        /**
         * Sets the spy context used for RoboTest in order to change the value of
         * com.android.internal.R.bool.config_magnification_area
         */
        public void setContext(Context context) {
            this.mContext = context;
        }
    }
}
