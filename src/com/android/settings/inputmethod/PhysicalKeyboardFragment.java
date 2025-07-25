/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.settings.inputmethod;

import android.app.Activity;
import android.app.settings.SettingsEnums;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.hardware.input.InputDeviceIdentifier;
import android.hardware.input.InputManager;
import android.hardware.input.InputSettings;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.SearchIndexableResource;
import android.provider.Settings.Secure;
import android.text.TextUtils;
import android.util.FeatureFlagUtils;
import android.view.InputDevice;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.TwoStatePreference;

import com.android.internal.util.Preconditions;
import com.android.settings.R;
import com.android.settings.core.SubSettingLauncher;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.keyboard.Flags;
import com.android.settings.overlay.FeatureFactory;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;
import com.android.settingslib.utils.ThreadUtils;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

// TODO(b/327638540): Update implementation of preference here and reuse key preferences and
//  controllers between here and A11y Setting page.
@SearchIndexable
public final class PhysicalKeyboardFragment extends DashboardFragment
        implements InputManager.InputDeviceListener {

    private static final String KEYBOARD_OPTIONS_CATEGORY = "keyboard_options_category";
    private static final String KEYBOARD_A11Y_CATEGORY = "keyboard_a11y_category";
    private static final String ACCESSIBILITY_BOUNCE_KEYS = "accessibility_bounce_keys";
    private static final String ACCESSIBILITY_SLOW_KEYS = "accessibility_slow_keys";
    private static final String ACCESSIBILITY_STICKY_KEYS = "accessibility_sticky_keys";
    private static final String ACCESSIBILITY_MOUSE_KEYS = "accessibility_mouse_keys";
    private static final String ACCESSIBILITY_PHYSICAL_KEYBOARD_A11Y = "physical_keyboard_a11y";
    private static final String KEYBOARD_SHORTCUTS_HELPER = "keyboard_shortcuts_helper";
    private static final String MODIFIER_KEYS_SETTINGS = "modifier_keys_settings";
    private static final String EXTRA_AUTO_SELECTION = "auto_selection";
    public static final String EXTRA_INPUT_DEVICE_IDENTIFIER = "input_device_identifier";
    private static final String TAG = "KeyboardAndTouchA11yFragment";
    private static final Uri sVirtualKeyboardSettingsUri = Secure.getUriFor(
            Secure.SHOW_IME_WITH_HARD_KEYBOARD);
    private static final Uri sAccessibilityBounceKeysUri = Secure.getUriFor(
            Secure.ACCESSIBILITY_BOUNCE_KEYS);
    private static final Uri sAccessibilitySlowKeysUri = Secure.getUriFor(
            Secure.ACCESSIBILITY_SLOW_KEYS);
    private static final Uri sAccessibilityStickyKeysUri = Secure.getUriFor(
            Secure.ACCESSIBILITY_STICKY_KEYS);
    private static final Uri sAccessibilityMouseKeysUri = Secure.getUriFor(
            Secure.ACCESSIBILITY_MOUSE_KEYS_ENABLED);
    public static final int BOUNCE_KEYS_THRESHOLD = 500;
    public static final int SLOW_KEYS_THRESHOLD = 500;

    @NonNull
    private final ArrayList<HardKeyboardDeviceInfo> mLastHardKeyboards = new ArrayList<>();

    private InputManager mIm;
    private InputMethodManager mImm;
    private InputDeviceIdentifier mAutoInputDeviceIdentifier;
    private KeyboardSettingsFeatureProvider mFeatureProvider;
    @NonNull
    private PreferenceCategory mKeyboardAssistanceCategory;
    @Nullable
    private PreferenceCategory mKeyboardA11yCategory = null;
    @Nullable
    private TwoStatePreference mAccessibilityBounceKeys = null;
    @Nullable
    private TwoStatePreference mAccessibilitySlowKeys = null;
    @Nullable
    private TwoStatePreference mAccessibilityStickyKeys = null;
    @Nullable
    private TwoStatePreference mAccessibilityMouseKeys = null;
    private boolean mSupportsFirmwareUpdate;

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.physical_keyboard_settings;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putParcelable(EXTRA_AUTO_SELECTION, mAutoInputDeviceIdentifier);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        super.onCreatePreferences(bundle, s);
        Activity activity = Preconditions.checkNotNull(getActivity());
        addPreferencesFromResource(R.xml.physical_keyboard_settings);
        mIm = Preconditions.checkNotNull(activity.getSystemService(InputManager.class));
        mImm = Preconditions.checkNotNull(activity.getSystemService(InputMethodManager.class));
        mKeyboardAssistanceCategory = Preconditions.checkNotNull(
                findPreference(KEYBOARD_OPTIONS_CATEGORY));

        mKeyboardA11yCategory = Objects.requireNonNull(findPreference(KEYBOARD_A11Y_CATEGORY));
        mAccessibilityBounceKeys = Objects.requireNonNull(
                mKeyboardA11yCategory.findPreference(ACCESSIBILITY_BOUNCE_KEYS));
        mAccessibilityBounceKeys.setSummary(
                getContext().getString(R.string.bounce_keys_summary, BOUNCE_KEYS_THRESHOLD));
        mAccessibilitySlowKeys = Objects.requireNonNull(
                mKeyboardA11yCategory.findPreference(ACCESSIBILITY_SLOW_KEYS));
        mAccessibilitySlowKeys.setSummary(
                getContext().getString(R.string.slow_keys_summary, SLOW_KEYS_THRESHOLD));
        mAccessibilityStickyKeys = Objects.requireNonNull(
                mKeyboardA11yCategory.findPreference(ACCESSIBILITY_STICKY_KEYS));
        mAccessibilityMouseKeys = Objects.requireNonNull(
                mKeyboardA11yCategory.findPreference(ACCESSIBILITY_MOUSE_KEYS));

        FeatureFactory featureFactory = FeatureFactory.getFeatureFactory();
        mMetricsFeatureProvider = featureFactory.getMetricsFeatureProvider();
        mFeatureProvider = featureFactory.getKeyboardSettingsFeatureProvider();
        mSupportsFirmwareUpdate = mFeatureProvider.supportsFirmwareUpdate();
        if (mSupportsFirmwareUpdate) {
            mFeatureProvider.registerKeyboardInformationCategory(getPreferenceScreen());
        }
        boolean isModifierKeySettingsEnabled = FeatureFlagUtils
                .isEnabled(getContext(), FeatureFlagUtils.SETTINGS_NEW_KEYBOARD_MODIFIER_KEY);
        boolean isKeyboardAndTouchpadA11yNewPageEnabled =
                Flags.keyboardAndTouchpadA11yNewPageEnabled();
        if (!isModifierKeySettingsEnabled) {
            mKeyboardAssistanceCategory.removePreference(findPreference(MODIFIER_KEYS_SETTINGS));
        }
        if (!isKeyboardAndTouchpadA11yNewPageEnabled) {
            mKeyboardAssistanceCategory.removePreference(
                    findPreference(ACCESSIBILITY_PHYSICAL_KEYBOARD_A11Y));
        }
        if (isKeyboardAndTouchpadA11yNewPageEnabled) {
            mKeyboardA11yCategory.removePreference(mAccessibilityBounceKeys);
        }
        if (isKeyboardAndTouchpadA11yNewPageEnabled) {
            mKeyboardA11yCategory.removePreference(mAccessibilitySlowKeys);
        }
        if (isKeyboardAndTouchpadA11yNewPageEnabled) {
            mKeyboardA11yCategory.removePreference(mAccessibilityStickyKeys);
        }
        if (!InputSettings.isAccessibilityMouseKeysFeatureFlagEnabled()
                || isKeyboardAndTouchpadA11yNewPageEnabled) {
            mKeyboardA11yCategory.removePreference(mAccessibilityMouseKeys);
        }
        if (isKeyboardAndTouchpadA11yNewPageEnabled) {
            mKeyboardA11yCategory.setVisible(false);
        }
        InputDeviceIdentifier inputDeviceIdentifier = activity.getIntent().getParcelableExtra(
                EXTRA_INPUT_DEVICE_IDENTIFIER, InputDeviceIdentifier.class);
        int intentFromWhere =
                activity.getIntent().getIntExtra(android.provider.Settings.EXTRA_ENTRYPOINT, -1);
        if (intentFromWhere != -1) {
            mMetricsFeatureProvider.action(
                    getContext(), SettingsEnums.ACTION_OPEN_PK_SETTINGS_FROM, intentFromWhere);
        }
        if (inputDeviceIdentifier != null) {
            mAutoInputDeviceIdentifier = inputDeviceIdentifier;
        }
        // Don't repeat the autoselection.
        if (isAutoSelection(bundle, inputDeviceIdentifier)) {
            showEnabledLocalesKeyboardLayoutList(inputDeviceIdentifier);
        }
    }

    private static boolean isAutoSelection(Bundle bundle, InputDeviceIdentifier identifier) {
        if (bundle != null && bundle.getParcelable(EXTRA_AUTO_SELECTION,
                InputDeviceIdentifier.class) != null) {
            return false;
        }
        return identifier != null;
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (KEYBOARD_SHORTCUTS_HELPER.equals(preference.getKey())) {
            writePreferenceClickMetric(preference);
            toggleKeyboardShortcutsMenu();
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public void onResume() {
        super.onResume();
        mLastHardKeyboards.clear();
        scheduleUpdateHardKeyboards();
        mIm.registerInputDeviceListener(this, null);
        Objects.requireNonNull(mAccessibilityBounceKeys).setOnPreferenceChangeListener(
                mAccessibilityBounceKeysSwitchPreferenceChangeListener);
        Objects.requireNonNull(mAccessibilitySlowKeys).setOnPreferenceChangeListener(
                mAccessibilitySlowKeysSwitchPreferenceChangeListener);
        Objects.requireNonNull(mAccessibilityStickyKeys).setOnPreferenceChangeListener(
                mAccessibilityStickyKeysSwitchPreferenceChangeListener);
        Objects.requireNonNull(mAccessibilityMouseKeys).setOnPreferenceChangeListener(
                mAccessibilityMouseKeysSwitchPreferenceChangeListener);
        registerSettingsObserver();
    }

    @Override
    public void onPause() {
        super.onPause();
        mLastHardKeyboards.clear();
        mIm.unregisterInputDeviceListener(this);
        Objects.requireNonNull(mAccessibilityBounceKeys).setOnPreferenceChangeListener(null);
        Objects.requireNonNull(mAccessibilitySlowKeys).setOnPreferenceChangeListener(null);
        Objects.requireNonNull(mAccessibilityStickyKeys).setOnPreferenceChangeListener(null);
        Objects.requireNonNull(mAccessibilityMouseKeys).setOnPreferenceChangeListener(null);
        unregisterSettingsObserver();
    }

    @Override
    public void onInputDeviceAdded(int deviceId) {
        scheduleUpdateHardKeyboards();
    }

    @Override
    public void onInputDeviceRemoved(int deviceId) {
        scheduleUpdateHardKeyboards();
    }

    @Override
    public void onInputDeviceChanged(int deviceId) {
        scheduleUpdateHardKeyboards();
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.PHYSICAL_KEYBOARDS;
    }

    private void scheduleUpdateHardKeyboards() {
        final Context context = getContext();
        ThreadUtils.postOnBackgroundThread(() -> {
            final List<HardKeyboardDeviceInfo> newHardKeyboards = getHardKeyboards(context);
            if (newHardKeyboards.isEmpty()) {
                getActivity().finish();
                return;
            }
            ThreadUtils.postOnMainThread(() -> updateHardKeyboards(context, newHardKeyboards));
        });
    }

    private void updateHardKeyboards(@NonNull Context context,
                                     @NonNull List<HardKeyboardDeviceInfo> newHardKeyboards) {
        if (Objects.equals(mLastHardKeyboards, newHardKeyboards)) {
            // Nothing has changed.  Ignore.
            return;
        }

        // TODO(yukawa): Maybe we should follow the style used in ConnectedDeviceDashboardFragment.

        mLastHardKeyboards.clear();
        mLastHardKeyboards.addAll(newHardKeyboards);

        final PreferenceScreen preferenceScreen = getPreferenceScreen();
        preferenceScreen.removeAll();
        final PreferenceCategory category = new PreferenceCategory(getPrefContext());
        category.setTitle(R.string.builtin_keyboard_settings_title);
        category.setOrder(0);
        preferenceScreen.addPreference(category);

        for (HardKeyboardDeviceInfo hardKeyboardDeviceInfo : newHardKeyboards) {
            // TODO(yukawa): Consider using com.android.settings.widget.GearPreference
            final Preference pref = new Preference(getPrefContext());
            pref.setTitle(hardKeyboardDeviceInfo.mDeviceName);
            String currentLayout =
                    InputPeripheralsSettingsUtils.getSelectedKeyboardLayoutLabelForUser(context,
                            UserHandle.myUserId(), hardKeyboardDeviceInfo.mDeviceIdentifier);
            if (currentLayout != null) {
                pref.setSummary(currentLayout);
            }
            pref.setOnPreferenceClickListener(
                    preference -> {
                        showEnabledLocalesKeyboardLayoutList(
                                hardKeyboardDeviceInfo.mDeviceIdentifier);
                        return true;
                    });

            category.addPreference(pref);
            StringBuilder vendorAndProductId = new StringBuilder();
            String vendorId = String.valueOf(hardKeyboardDeviceInfo.mVendorId);
            String productId = String.valueOf(hardKeyboardDeviceInfo.mProductId);
            vendorAndProductId.append(vendorId);
            vendorAndProductId.append("-");
            vendorAndProductId.append(productId);
            mMetricsFeatureProvider.action(
                    context,
                    SettingsEnums.ACTION_USE_SPECIFIC_KEYBOARD,
                    vendorAndProductId.toString());
        }
        mKeyboardAssistanceCategory.setOrder(1);
        preferenceScreen.addPreference(mKeyboardAssistanceCategory);
        if (mSupportsFirmwareUpdate) {
            mFeatureProvider.registerKeyboardInformationCategory(preferenceScreen);
        }

        Objects.requireNonNull(mKeyboardA11yCategory).setOrder(2);
        preferenceScreen.addPreference(mKeyboardA11yCategory);
        updateAccessibilityStickyKeysSwitch(context);
        updateAccessibilityBounceKeysSwitch(context);
        updateAccessibilitySlowKeysSwitch(context);
        if (InputSettings.isAccessibilityMouseKeysFeatureFlagEnabled()) {
            updateAccessibilityMouseKeysSwitch(context);
        }
    }

    private void showEnabledLocalesKeyboardLayoutList(InputDeviceIdentifier inputDeviceIdentifier) {
        Bundle arguments = new Bundle();
        arguments.putParcelable(InputPeripheralsSettingsUtils.EXTRA_INPUT_DEVICE_IDENTIFIER,
                inputDeviceIdentifier);
        new SubSettingLauncher(getContext())
                .setSourceMetricsCategory(getMetricsCategory())
                .setDestination(NewKeyboardLayoutEnabledLocalesFragment.class.getName())
                .setArguments(arguments)
                .launch();
    }

    private void registerSettingsObserver() {
        unregisterSettingsObserver();
        ContentResolver contentResolver = getActivity().getContentResolver();
        contentResolver.registerContentObserver(
                sVirtualKeyboardSettingsUri,
                false,
                mContentObserver,
                UserHandle.myUserId());
        contentResolver.registerContentObserver(
                sAccessibilityBounceKeysUri,
                false,
                mContentObserver,
                UserHandle.myUserId());
        contentResolver.registerContentObserver(
                sAccessibilitySlowKeysUri,
                false,
                mContentObserver,
                UserHandle.myUserId());
        contentResolver.registerContentObserver(
                sAccessibilityStickyKeysUri,
                false,
                mContentObserver,
                UserHandle.myUserId());
        if (InputSettings.isAccessibilityMouseKeysFeatureFlagEnabled()) {
            contentResolver.registerContentObserver(
                    sAccessibilityMouseKeysUri,
                    false,
                    mContentObserver,
                    UserHandle.myUserId());
        }
        final Context context = getContext();
        updateAccessibilityBounceKeysSwitch(context);
        updateAccessibilitySlowKeysSwitch(context);
        updateAccessibilityStickyKeysSwitch(context);
        updateAccessibilityMouseKeysSwitch(context);
    }

    private void unregisterSettingsObserver() {
        getActivity().getContentResolver().unregisterContentObserver(mContentObserver);
        if (mSupportsFirmwareUpdate) {
            mFeatureProvider.unregisterKeyboardInformationCategory();
        }
    }

    private void updateAccessibilityBounceKeysSwitch(@NonNull Context context) {
        Objects.requireNonNull(mAccessibilityBounceKeys).setChecked(
                InputSettings.isAccessibilityBounceKeysEnabled(context));
    }

    private void updateAccessibilitySlowKeysSwitch(@NonNull Context context) {
        Objects.requireNonNull(mAccessibilitySlowKeys).setChecked(
                InputSettings.isAccessibilitySlowKeysEnabled(context));
    }

    private void updateAccessibilityStickyKeysSwitch(@NonNull Context context) {
        Objects.requireNonNull(mAccessibilityStickyKeys).setChecked(
                InputSettings.isAccessibilityStickyKeysEnabled(context));
    }

    private void updateAccessibilityMouseKeysSwitch(@NonNull Context context) {
        if (!InputSettings.isAccessibilityMouseKeysFeatureFlagEnabled()) {
            return;
        }
        Objects.requireNonNull(mAccessibilityMouseKeys).setChecked(
                InputSettings.isAccessibilityMouseKeysEnabled(context));
    }

    private void toggleKeyboardShortcutsMenu() {
        getActivity().requestShowKeyboardShortcuts();
    }

    private final OnPreferenceChangeListener
            mAccessibilityBounceKeysSwitchPreferenceChangeListener = (preference, newValue) -> {
                InputSettings.setAccessibilityBounceKeysThreshold(getContext(),
                        ((Boolean) newValue) ? 500 : 0);
                return true;
            };

    private final OnPreferenceChangeListener
            mAccessibilitySlowKeysSwitchPreferenceChangeListener = (preference, newValue) -> {
                InputSettings.setAccessibilitySlowKeysThreshold(getContext(),
                        ((Boolean) newValue) ? 500 : 0);
                return true;
            };

    private final OnPreferenceChangeListener
            mAccessibilityStickyKeysSwitchPreferenceChangeListener = (preference, newValue) -> {
                InputSettings.setAccessibilityStickyKeysEnabled(getContext(), (Boolean) newValue);
                return true;
            };

    private final OnPreferenceChangeListener
            mAccessibilityMouseKeysSwitchPreferenceChangeListener = (preference, newValue) -> {
                InputSettings.setAccessibilityMouseKeysEnabled(getContext(), (Boolean) newValue);
                return true;
            };

    private final ContentObserver mContentObserver = new ContentObserver(new Handler(true)) {
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if (sAccessibilityBounceKeysUri.equals(uri)) {
                updateAccessibilityBounceKeysSwitch(getContext());
            } else if (sAccessibilitySlowKeysUri.equals(uri)) {
                updateAccessibilitySlowKeysSwitch(getContext());
            } else if (sAccessibilityStickyKeysUri.equals(uri)) {
                updateAccessibilityStickyKeysSwitch(getContext());
            } else if (sAccessibilityMouseKeysUri.equals(uri)) {
                updateAccessibilityMouseKeysSwitch(getContext());
            }
        }
    };

    @NonNull
    static List<HardKeyboardDeviceInfo> getHardKeyboards(@NonNull Context context) {
        final List<HardKeyboardDeviceInfo> keyboards = new ArrayList<>();
        final InputManager im = context.getSystemService(InputManager.class);
        if (im == null) {
            return new ArrayList<>();
        }
        for (int deviceId : InputDevice.getDeviceIds()) {
            final InputDevice device = InputDevice.getDevice(deviceId);
            if (device == null || device.isVirtual() || !device.isFullKeyboard()) {
                continue;
            }
            keyboards.add(new HardKeyboardDeviceInfo(
                    device.getName(),
                    device.getIdentifier(),
                    device.getBluetoothAddress(),
                    device.getVendorId(),
                    device.getProductId()));
        }

        // We intentionally don't reuse Comparator because Collator may not be thread-safe.
        final Collator collator = Collator.getInstance();
        keyboards.sort((a, b) -> {
            int result = collator.compare(a.mDeviceName, b.mDeviceName);
            if (result != 0) {
                return result;
            }
            return a.mDeviceIdentifier.getDescriptor().compareTo(
                    b.mDeviceIdentifier.getDescriptor());
        });
        return keyboards;
    }

    public static final class HardKeyboardDeviceInfo {
        @NonNull
        public final String mDeviceName;
        @NonNull
        public final InputDeviceIdentifier mDeviceIdentifier;
        @Nullable
        public final String mBluetoothAddress;
        @NonNull
        public final int mVendorId;
        @NonNull
        public final int mProductId;

        public HardKeyboardDeviceInfo(
                @Nullable String deviceName,
                @NonNull InputDeviceIdentifier deviceIdentifier,
                @Nullable String bluetoothAddress,
                @NonNull int vendorId,
                @NonNull int productId) {
            mDeviceName = TextUtils.emptyIfNull(deviceName);
            mDeviceIdentifier = deviceIdentifier;
            mBluetoothAddress = bluetoothAddress;
            mVendorId = vendorId;
            mProductId = productId;
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if (o == null) return false;

            if (!(o instanceof HardKeyboardDeviceInfo)) return false;

            final HardKeyboardDeviceInfo that = (HardKeyboardDeviceInfo) o;
            if (!TextUtils.equals(mDeviceName, that.mDeviceName)) {
                return false;
            }
            if (!Objects.equals(mDeviceIdentifier, that.mDeviceIdentifier)) {
                return false;
            }
            if (!TextUtils.equals(mBluetoothAddress, that.mBluetoothAddress)) {
                return false;
            }

            return true;
        }
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider() {
                @Override
                public List<SearchIndexableResource> getXmlResourcesToIndex(
                        Context context, boolean enabled) {
                    final SearchIndexableResource sir = new SearchIndexableResource(context);
                    sir.xmlResId = R.xml.physical_keyboard_settings;
                    return Arrays.asList(sir);
                }

                @Override
                protected boolean isPageSearchEnabled(Context context) {
                    return !getHardKeyboards(context).isEmpty();
                }
            };
}
