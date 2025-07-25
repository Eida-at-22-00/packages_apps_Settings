/*
 * Copyright (C) 2013 The Android Open Source Project
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

import static com.android.internal.accessibility.common.ShortcutConstants.UserShortcutType.DEFAULT;
import static com.android.internal.accessibility.common.ShortcutConstants.UserShortcutType.SOFTWARE;
import static com.android.settings.accessibility.AccessibilityDialogUtils.DialogEnums;
import static com.android.settings.accessibility.AccessibilityUtil.getShortcutSummaryList;

import android.app.Dialog;
import android.app.settings.SettingsEnums;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.service.quicksettings.TileService;
import android.text.Html;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityManager.TouchExplorationStateChangeListener;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceViewHolder;
import androidx.recyclerview.widget.RecyclerView;

import com.android.internal.accessibility.common.ShortcutConstants;
import com.android.internal.accessibility.util.ShortcutUtils;
import com.android.settings.R;
import com.android.settings.SettingsActivity;
import com.android.settings.accessibility.actionbar.FeedbackMenuController;
import com.android.settings.accessibility.shortcuts.EditShortcutsPreferenceFragment;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.flags.Flags;
import com.android.settings.widget.SettingsMainSwitchBar;
import com.android.settings.widget.SettingsMainSwitchPreference;
import com.android.settingslib.utils.ThreadUtils;
import com.android.settingslib.widget.IllustrationPreference;
import com.android.settingslib.widget.TopIntroPreference;

import com.google.android.setupcompat.util.WizardManagerHelper;
import com.google.android.setupdesign.util.ThemeHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Base class for accessibility fragments with toggle, shortcut, some helper functions
 * and dialog management.
 */
public abstract class ToggleFeaturePreferenceFragment extends DashboardFragment
        implements ShortcutPreference.OnClickCallback, OnCheckedChangeListener {

    public static final String KEY_GENERAL_CATEGORY = "general_categories";
    public static final String KEY_SHORTCUT_PREFERENCE = "shortcut_preference";
    protected static final String KEY_TOP_INTRO_PREFERENCE = "top_intro";
    protected static final String KEY_HTML_DESCRIPTION_PREFERENCE = "html_description";
    protected static final String KEY_ANIMATED_IMAGE = "animated_image";
    // For html description of accessibility service, must follow the rule, such as
    // <img src="R.drawable.fileName"/>, a11y settings will get the resources successfully.
    private static final String IMG_PREFIX = "R.drawable.";
    private static final String DRAWABLE_FOLDER = "drawable";

    protected TopIntroPreference mTopIntroPreference;
    protected SettingsMainSwitchPreference mToggleServiceSwitchPreference;
    protected ShortcutPreference mShortcutPreference;
    protected Preference mSettingsPreference;
    @Nullable protected AccessibilityFooterPreference mHtmlFooterPreference;
    protected AccessibilityFooterPreferenceController mFooterPreferenceController;
    protected String mPreferenceKey;
    protected Dialog mDialog;
    protected CharSequence mSettingsTitle;
    protected Intent mSettingsIntent;
    // The mComponentName maybe null, such as Magnify
    protected ComponentName mComponentName;
    protected CharSequence mFeatureName;
    protected Uri mImageUri;
    protected CharSequence mHtmlDescription;
    protected CharSequence mTopIntroTitle;
    private CharSequence mDescription;
    private TouchExplorationStateChangeListener mTouchExplorationStateChangeListener;
    private AccessibilitySettingsContentObserver mSettingsContentObserver;
    private ImageView mImageGetterCacheView;
    protected final Html.ImageGetter mImageGetter = (String str) -> {
        if (str != null && str.startsWith(IMG_PREFIX)) {
            final String fileName = str.substring(IMG_PREFIX.length());
            return getDrawableFromUri(Uri.parse(
                    ContentResolver.SCHEME_ANDROID_RESOURCE + "://"
                            + mComponentName.getPackageName() + "/" + DRAWABLE_FOLDER + "/"
                            + fileName));
        }
        return null;
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        onProcessArguments(getArguments());
        final int resId = getPreferenceScreenResId();
        if (resId <= 0) {
            final PreferenceScreen preferenceScreen = getPreferenceManager().createPreferenceScreen(
                    getPrefContext());
            setPreferenceScreen(preferenceScreen);
        }

        mSettingsContentObserver = new AccessibilitySettingsContentObserver(new Handler());
        registerKeysToObserverCallback(mSettingsContentObserver);

        FeedbackMenuController.init(this, getFeedbackCategory());
    }

    protected void registerKeysToObserverCallback(
            AccessibilitySettingsContentObserver contentObserver) {
        final List<String> shortcutFeatureKeys = getShortcutFeatureSettingsKeys();

        contentObserver.registerKeysToObserverCallback(shortcutFeatureKeys, key -> {
            updateShortcutPreferenceData();
            updateShortcutPreference();
        });
    }

    protected List<String> getShortcutFeatureSettingsKeys() {
        final List<String> shortcutFeatureKeys = new ArrayList<>();
        shortcutFeatureKeys.add(Settings.Secure.ACCESSIBILITY_BUTTON_TARGETS);
        shortcutFeatureKeys.add(Settings.Secure.ACCESSIBILITY_SHORTCUT_TARGET_SERVICE);
        shortcutFeatureKeys.add(Settings.Secure.ACCESSIBILITY_QS_TARGETS);
        return shortcutFeatureKeys;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        initTopIntroPreference();
        initAnimatedImagePreference();
        initToggleServiceSwitchPreference();
        initGeneralCategory();
        initShortcutPreference();
        initSettingsPreference();
        initAppInfoPreference();
        initHtmlTextPreference();
        initFooterPreference();

        installActionBarToggleSwitch();

        updateToggleServiceTitle(mToggleServiceSwitchPreference);

        mTouchExplorationStateChangeListener = isTouchExplorationEnabled -> {
            mShortcutPreference.setSummary(getShortcutTypeSummary(getPrefContext()));
        };

        updatePreferenceOrder();
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public Dialog onCreateDialog(int dialogId) {
        switch (dialogId) {
            case DialogEnums.LAUNCH_ACCESSIBILITY_TUTORIAL:
                if (isAnySetupWizard()) {
                    mDialog = AccessibilityShortcutsTutorial
                            .createAccessibilityTutorialDialogForSetupWizard(
                                    getPrefContext(), getUserPreferredShortcutTypes(),
                                    this::callOnTutorialDialogButtonClicked, mFeatureName);
                } else {
                    mDialog = AccessibilityShortcutsTutorial
                            .createAccessibilityTutorialDialog(
                                    getPrefContext(), getUserPreferredShortcutTypes(),
                                    this::callOnTutorialDialogButtonClicked, mFeatureName);
                }
                mDialog.setCanceledOnTouchOutside(false);
                return mDialog;
            default:
                throw new IllegalArgumentException("Unsupported dialogId " + dialogId);
        }
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final SettingsActivity settingsActivity = (SettingsActivity) getActivity();
        final SettingsMainSwitchBar switchBar = settingsActivity.getSwitchBar();
        switchBar.hide();

        writeConfigDefaultAccessibilityServiceIntoShortcutTargetServiceIfNeeded(getContext());
    }

    @Override
    public void onResume() {
        super.onResume();

        final AccessibilityManager am = getPrefContext().getSystemService(
                AccessibilityManager.class);
        am.addTouchExplorationStateChangeListener(mTouchExplorationStateChangeListener);
        mSettingsContentObserver.register(getContentResolver());
        updateShortcutPreferenceData();
        updateShortcutPreference();
    }

    @Override
    public void onPause() {
        final AccessibilityManager am = getPrefContext().getSystemService(
                AccessibilityManager.class);
        am.removeTouchExplorationStateChangeListener(mTouchExplorationStateChangeListener);
        mSettingsContentObserver.unregister(getContentResolver());
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        removeActionBarToggleSwitch();
    }

    @Override
    public int getDialogMetricsCategory(int dialogId) {
        switch (dialogId) {
            case DialogEnums.LAUNCH_ACCESSIBILITY_TUTORIAL:
                return SettingsEnums.DIALOG_ACCESSIBILITY_TUTORIAL;
            default:
                return SettingsEnums.ACTION_UNKNOWN;
        }
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.ACCESSIBILITY_SERVICE;
    }

    /**
     * Returns the category of the feedback page.
     *
     * <p>By default, this method returns {@link SettingsEnums#PAGE_UNKNOWN}. This indicates that
     * the feedback category is unknown, and the absence of a feedback menu.
     *
     * @return The feedback category, which is {@link SettingsEnums#PAGE_UNKNOWN} by default.
     */
    protected int getFeedbackCategory() {
        return SettingsEnums.PAGE_UNKNOWN;
    }

    @Override
    public int getHelpResource() {
        return 0;
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        onPreferenceToggled(mPreferenceKey, isChecked);
    }

    /**
     * Returns the shortcut type list which has been checked by user.
     */
    abstract int getUserShortcutTypes();

    /** Returns the accessibility tile component name. */
    abstract ComponentName getTileComponentName();

    protected void updateToggleServiceTitle(SettingsMainSwitchPreference switchPreference) {
        final CharSequence title =
                getString(R.string.accessibility_service_primary_switch_title, mFeatureName);
        switchPreference.setTitle(title);
    }

    protected String getUseServicePreferenceKey() {
        return "use_service";
    }

    protected CharSequence getShortcutTitle() {
        return getString(R.string.accessibility_shortcut_title, mFeatureName);
    }

    @VisibleForTesting
    CharSequence getContentDescriptionForAnimatedIllustration() {
        return getString(R.string.accessibility_illustration_content_description, mFeatureName);
    }

    protected void onPreferenceToggled(String preferenceKey, boolean enabled) {
    }

    protected void onInstallSwitchPreferenceToggleSwitch() {
        // Implement this to set a checked listener.
        updateSwitchBarToggleSwitch();
        mToggleServiceSwitchPreference.addOnSwitchChangeListener(this);
    }

    protected void onRemoveSwitchPreferenceToggleSwitch() {
        // Implement this to reset a checked listener.
    }

    protected void updateSwitchBarToggleSwitch() {
        // Implement this to update the state of switch.
    }

    public void setTitle(String title) {
        getActivity().setTitle(title);
    }

    protected void onProcessArguments(@Nullable Bundle arguments) {
        if (arguments == null) {
            return;
        }
        // Key.
        mPreferenceKey = arguments.getString(AccessibilitySettings.EXTRA_PREFERENCE_KEY);

        // Title.
        if (arguments.containsKey(AccessibilitySettings.EXTRA_RESOLVE_INFO)) {
            ResolveInfo info = arguments.getParcelable(AccessibilitySettings.EXTRA_RESOLVE_INFO);
            getActivity().setTitle(info.loadLabel(getPackageManager()).toString());
        } else if (arguments.containsKey(AccessibilitySettings.EXTRA_TITLE)) {
            setTitle(arguments.getString(AccessibilitySettings.EXTRA_TITLE));
        }

        // Summary.
        if (arguments.containsKey(AccessibilitySettings.EXTRA_SUMMARY)) {
            mDescription = arguments.getCharSequence(AccessibilitySettings.EXTRA_SUMMARY);
        }

        // Settings html description.
        if (arguments.containsKey(AccessibilitySettings.EXTRA_HTML_DESCRIPTION)) {
            mHtmlDescription = arguments.getCharSequence(
                    AccessibilitySettings.EXTRA_HTML_DESCRIPTION);
        }

        // Intro.
        if (arguments.containsKey(AccessibilitySettings.EXTRA_INTRO)) {
            mTopIntroTitle = arguments.getCharSequence(AccessibilitySettings.EXTRA_INTRO);
        }
    }

    private void installActionBarToggleSwitch() {
        onInstallSwitchPreferenceToggleSwitch();
    }

    private void removeActionBarToggleSwitch() {
        mToggleServiceSwitchPreference.setOnPreferenceClickListener(null);
        onRemoveSwitchPreferenceToggleSwitch();
    }

    private void updatePreferenceOrder() {
        final List<String> lists = getPreferenceOrderList();

        final PreferenceScreen preferenceScreen = getPreferenceScreen();
        preferenceScreen.setOrderingAsAdded(false);

        final int size = lists.size();
        for (int i = 0; i < size; i++) {
            final Preference preference = preferenceScreen.findPreference(lists.get(i));
            if (preference != null) {
                preference.setOrder(i);
            }
        }
    }

    /** Customizes the order by preference key. */
    protected List<String> getPreferenceOrderList() {
        final List<String> lists = new ArrayList<>();
        lists.add(KEY_TOP_INTRO_PREFERENCE);
        lists.add(KEY_ANIMATED_IMAGE);
        lists.add(getUseServicePreferenceKey());
        lists.add(KEY_GENERAL_CATEGORY);
        lists.add(KEY_HTML_DESCRIPTION_PREFERENCE);
        return lists;
    }

    private Drawable getDrawableFromUri(Uri imageUri) {
        if (mImageGetterCacheView == null) {
            mImageGetterCacheView = new ImageView(getPrefContext());
        }

        mImageGetterCacheView.setAdjustViewBounds(true);
        mImageGetterCacheView.setImageURI(imageUri);

        if (mImageGetterCacheView.getDrawable() == null) {
            return null;
        }

        final Drawable drawable =
                mImageGetterCacheView.getDrawable().mutate().getConstantState().newDrawable();
        mImageGetterCacheView.setImageURI(null);
        final int imageWidth = drawable.getIntrinsicWidth();
        final int imageHeight = drawable.getIntrinsicHeight();
        final int screenHalfHeight = AccessibilityUtil.getScreenHeightPixels(getPrefContext()) / 2;
        if ((imageWidth > AccessibilityUtil.getScreenWidthPixels(getPrefContext()))
                || (imageHeight > screenHalfHeight)) {
            return null;
        }

        drawable.setBounds(/* left= */0, /* top= */0, drawable.getIntrinsicWidth(),
                drawable.getIntrinsicHeight());

        return drawable;
    }
    private void initAnimatedImagePreference() {
        initAnimatedImagePreference(mImageUri, new IllustrationPreference(getPrefContext()) {
            @Override
            public void onBindViewHolder(PreferenceViewHolder holder) {
                super.onBindViewHolder(holder);
                if (ThemeHelper.shouldApplyGlifExpressiveStyle(getContext())
                        && isAnySetupWizard()) {
                    View illustrationFrame = holder.findViewById(R.id.illustration_frame);
                    final ViewGroup.LayoutParams lp = illustrationFrame.getLayoutParams();
                    lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                    illustrationFrame.setLayoutParams(lp);
                }
            }
        });
    }

    @VisibleForTesting
    void initAnimatedImagePreference(
            @Nullable Uri imageUri,
            @NonNull IllustrationPreference preference) {
        if (imageUri == null) {
            return;
        }

        final int displayHalfHeight =
                AccessibilityUtil.getDisplayBounds(getPrefContext()).height() / 2;
        preference.setImageUri(imageUri);
        preference.setSelectable(false);
        preference.setMaxHeight(displayHalfHeight);
        preference.setKey(KEY_ANIMATED_IMAGE);
        preference.setOnBindListener(view -> {
            // isAnimatable is decided in
            // {@link IllustrationPreference#onBindViewHolder(PreferenceViewHolder)}. Therefore, we
            // wait until the view is bond to set the content description for it.
            // The content description is added for an animation illustration only. Since the static
            // images are decorative.
            ThreadUtils.getUiThreadHandler().post(() -> {
                if (preference.isAnimatable()) {
                    preference.setContentDescription(
                            getContentDescriptionForAnimatedIllustration());
                }
            });
        });
        getPreferenceScreen().addPreference(preference);
    }

    @VisibleForTesting
    void initTopIntroPreference() {
        if (TextUtils.isEmpty(mTopIntroTitle)) {
            return;
        }
        mTopIntroPreference = new TopIntroPreference(getPrefContext());
        mTopIntroPreference.setKey(KEY_TOP_INTRO_PREFERENCE);
        mTopIntroPreference.setTitle(mTopIntroTitle);
        getPreferenceScreen().addPreference(mTopIntroPreference);
    }

    private void initToggleServiceSwitchPreference() {
        mToggleServiceSwitchPreference = new SettingsMainSwitchPreference(getPrefContext());
        mToggleServiceSwitchPreference.setKey(getUseServicePreferenceKey());
        if (getArguments() != null
                && getArguments().containsKey(AccessibilitySettings.EXTRA_CHECKED)) {
            final boolean enabled = getArguments().getBoolean(AccessibilitySettings.EXTRA_CHECKED);
            mToggleServiceSwitchPreference.setChecked(enabled);
        }

        getPreferenceScreen().addPreference(mToggleServiceSwitchPreference);
    }

    private void initGeneralCategory() {
        final PreferenceCategory generalCategory = new PreferenceCategory(getPrefContext());
        generalCategory.setKey(KEY_GENERAL_CATEGORY);
        generalCategory.setTitle(R.string.accessibility_screen_option);

        getPreferenceScreen().addPreference(generalCategory);
    }

    protected void initShortcutPreference() {
        // Initial the shortcut preference.
        mShortcutPreference = new ShortcutPreference(getPrefContext(), /* attrs= */ null);
        mShortcutPreference.setPersistent(false);
        mShortcutPreference.setKey(getShortcutPreferenceKey());
        mShortcutPreference.setOnClickCallback(this);
        mShortcutPreference.setTitle(getShortcutTitle());

        final PreferenceCategory generalCategory = findPreference(KEY_GENERAL_CATEGORY);
        generalCategory.addPreference(mShortcutPreference);
    }

    protected void initSettingsPreference() {
        if (mSettingsTitle == null || mSettingsIntent == null) {
            return;
        }

        // Show the "Settings" menu as if it were a preference screen.
        mSettingsPreference = new Preference(getPrefContext());
        mSettingsPreference.setTitle(mSettingsTitle);
        mSettingsPreference.setIconSpaceReserved(false);
        mSettingsPreference.setIntent(mSettingsIntent);

        final PreferenceCategory generalCategory = findPreference(KEY_GENERAL_CATEGORY);
        generalCategory.addPreference(mSettingsPreference);
    }

    @VisibleForTesting
    @Nullable
    Preference createAppInfoPreference() {
        if (!Flags.accessibilityShowAppInfoButton()) {
            return null;
        }
        // App Info is not available in Setup Wizard.
        if (isAnySetupWizard()) {
            return null;
        }
        // Only show the button for pages with valid component package names.
        if (mComponentName == null) {
            return null;
        }
        final String packageName = mComponentName.getPackageName();
        final PackageManager packageManager = getPrefContext().getPackageManager();
        if (!packageManager.isPackageAvailable(packageName)) {
            return null;
        }

        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setPackage(getContext().getPackageName());
        intent.setData(Uri.parse("package:" + packageName));

        final Preference appInfoPreference = new Preference(getPrefContext());
        appInfoPreference.setTitle(getString(R.string.application_info_label));
        appInfoPreference.setIconSpaceReserved(false);
        appInfoPreference.setIntent(intent);
        return appInfoPreference;
    }

    private void initAppInfoPreference() {
        final Preference appInfoPreference = createAppInfoPreference();
        if (appInfoPreference != null) {
            final PreferenceCategory generalCategory = findPreference(KEY_GENERAL_CATEGORY);
            generalCategory.addPreference(appInfoPreference);
        }
    }

    private void initHtmlTextPreference() {
        if (TextUtils.isEmpty(getCurrentHtmlDescription())) {
            return;
        }
        final PreferenceScreen screen = getPreferenceScreen();

        mHtmlFooterPreference =
                new AccessibilityFooterPreference(screen.getContext());
        mHtmlFooterPreference.setKey(KEY_HTML_DESCRIPTION_PREFERENCE);
        updateHtmlTextPreference();
        screen.addPreference(mHtmlFooterPreference);

        // TODO(b/171272809): Migrate to DashboardFragment.
        final String title = getString(R.string.accessibility_introduction_title, mFeatureName);
        mFooterPreferenceController = new AccessibilityFooterPreferenceController(
                screen.getContext(), mHtmlFooterPreference.getKey());
        mFooterPreferenceController.setIntroductionTitle(title);
        mFooterPreferenceController.displayPreference(screen);
    }

    protected void updateHtmlTextPreference() {
        if (mHtmlFooterPreference == null) {
            return;
        }

        String description = getCurrentHtmlDescription().toString();
        final CharSequence htmlDescription = Html.fromHtml(description,
                Html.FROM_HTML_MODE_COMPACT, mImageGetter, /* tagHandler= */ null);
        mHtmlFooterPreference.setSummary(htmlDescription);
    }

    CharSequence getCurrentHtmlDescription() {
        return mHtmlDescription;
    }

    private void initFooterPreference() {
        if (!TextUtils.isEmpty(mDescription)) {
            createFooterPreference(getPreferenceScreen(), mDescription,
                    getString(R.string.accessibility_introduction_title, mFeatureName));
        }
    }

    /**
     * Creates {@link AccessibilityFooterPreference} and append into {@link PreferenceScreen}
     *
     * @param screen The preference screen to add the footer preference
     * @param summary The summary of the preference summary
     * @param introductionTitle The title of introduction in the footer
     */
    @VisibleForTesting
    void createFooterPreference(PreferenceScreen screen, CharSequence summary,
            String introductionTitle) {
        final AccessibilityFooterPreference footerPreference =
                new AccessibilityFooterPreference(screen.getContext());
        footerPreference.setSummary(summary);
        screen.addPreference(footerPreference);

        mFooterPreferenceController = new AccessibilityFooterPreferenceController(
                screen.getContext(), footerPreference.getKey());
        mFooterPreferenceController.setIntroductionTitle(introductionTitle);
        mFooterPreferenceController.displayPreference(screen);
    }

    protected CharSequence getShortcutTypeSummary(Context context) {
        if (!mShortcutPreference.isSettingsEditable()) {
            return context.getText(R.string.accessibility_shortcut_edit_dialog_title_hardware);
        }

        if (!mShortcutPreference.isChecked()) {
            return context.getText(R.string.accessibility_shortcut_state_off);
        }

        final int shortcutTypes = PreferredShortcuts.retrieveUserShortcutType(
                context, mComponentName.flattenToString(), getDefaultShortcutTypes());
        return getShortcutSummaryList(context, shortcutTypes);
    }

    /**
     * This method will be invoked when a button in the tutorial dialog is clicked.
     *
     * @param dialog The dialog that received the click
     * @param which  The button that was clicked
     */
    private void callOnTutorialDialogButtonClicked(DialogInterface dialog, int which) {
        dialog.dismiss();
    }

    protected void updateShortcutPreferenceData() {
        if (mComponentName == null) {
            return;
        }

        final int shortcutTypes = AccessibilityUtil.getUserShortcutTypesFromSettings(
                getPrefContext(), mComponentName);
        if (shortcutTypes != DEFAULT) {
            final PreferredShortcut shortcut = new PreferredShortcut(
                    mComponentName.flattenToString(), shortcutTypes);
            PreferredShortcuts.saveUserShortcutType(getPrefContext(), shortcut);
        }
    }

    protected void updateShortcutPreference() {
        if (mComponentName == null) {
            return;
        }

        final int shortcutTypes = getUserPreferredShortcutTypes();
        mShortcutPreference.setChecked(
                ShortcutUtils.isShortcutContained(
                        getPrefContext(), shortcutTypes, mComponentName.flattenToString()));
        mShortcutPreference.setSummary(getShortcutTypeSummary(getPrefContext()));
    }

    protected String getShortcutPreferenceKey() {
        return KEY_SHORTCUT_PREFERENCE;
    }

    @Override
    public void onToggleClicked(ShortcutPreference preference) {
        if (mComponentName == null) {
            return;
        }

        final int shortcutTypes = getUserPreferredShortcutTypes();
        final boolean isChecked = preference.isChecked();
        getPrefContext().getSystemService(AccessibilityManager.class).enableShortcutsForTargets(
                isChecked, shortcutTypes,
                Set.of(mComponentName.flattenToString()), getPrefContext().getUserId());
        if (isChecked) {
            showDialog(DialogEnums.LAUNCH_ACCESSIBILITY_TUTORIAL);
        }
        mShortcutPreference.setSummary(getShortcutTypeSummary(getPrefContext()));
    }

    @Override
    public void onSettingsClicked(ShortcutPreference preference) {
        EditShortcutsPreferenceFragment.showEditShortcutScreen(
                requireContext(), getMetricsCategory(), getShortcutTitle(),
                mComponentName, getIntent());
    }

    /**
     * Setups {@link com.android.internal.R.string#config_defaultAccessibilityService} into
     * {@link Settings.Secure#ACCESSIBILITY_SHORTCUT_TARGET_SERVICE} if that settings key has never
     * been set and only write the key when user enter into corresponding page.
     */
    @VisibleForTesting
    void writeConfigDefaultAccessibilityServiceIntoShortcutTargetServiceIfNeeded(Context context) {
        if (mComponentName == null) {
            return;
        }

        // It might be shortened form (with a leading '.'). Need to unflatten back to ComponentName
        // first, or it will encounter errors when getting service from
        // `ACCESSIBILITY_SHORTCUT_TARGET_SERVICE`.
        final ComponentName configDefaultService = ComponentName.unflattenFromString(
                getString(com.android.internal.R.string.config_defaultAccessibilityService));

        if (!mComponentName.equals(configDefaultService)) {
            return;
        }

        final String targetKey = Settings.Secure.ACCESSIBILITY_SHORTCUT_TARGET_SERVICE;
        final String targetString = Settings.Secure.getString(context.getContentResolver(),
                targetKey);

        // By intentional, we only need to write the config string when the Settings key has never
        // been set (== null). Empty string also means someone already wrote it before, so we need
        // to respect the value.
        if (targetString == null) {
            Settings.Secure.putString(context.getContentResolver(), targetKey,
                    configDefaultService.flattenToString());
        }
    }

    /** Returns user visible name of the tile by given {@link ComponentName}. */
    protected CharSequence loadTileLabel(Context context, ComponentName componentName) {
        final PackageManager packageManager = context.getPackageManager();
        final Intent queryIntent = new Intent(TileService.ACTION_QS_TILE);
        final List<ResolveInfo> resolveInfos =
                packageManager.queryIntentServices(queryIntent, PackageManager.GET_META_DATA);
        for (ResolveInfo info : resolveInfos) {
            final ServiceInfo serviceInfo = info.serviceInfo;
            if (TextUtils.equals(componentName.getPackageName(), serviceInfo.packageName)
                    && TextUtils.equals(componentName.getClassName(), serviceInfo.name)) {
                return serviceInfo.loadLabel(packageManager);
            }
        }
        return null;
    }

    @VisibleForTesting
    boolean isAnySetupWizard() {
        return WizardManagerHelper.isAnySetupWizard(getIntent());
    }

    /**
     * Returns the default preferred shortcut types when the user doesn't have a preferred shortcut
     * types
     */
    @ShortcutConstants.UserShortcutType
    protected int getDefaultShortcutTypes() {
        return SOFTWARE;
    }

    /**
     * Returns the user preferred shortcut types or the default shortcut types if not set
     */
    @ShortcutConstants.UserShortcutType
    protected int getUserPreferredShortcutTypes() {
        return PreferredShortcuts.retrieveUserShortcutType(
                getPrefContext(), mComponentName.flattenToString(), getDefaultShortcutTypes());
    }

    @Override
    public RecyclerView onCreateRecyclerView(LayoutInflater inflater, ViewGroup parent,
            Bundle savedInstanceState) {
        RecyclerView recyclerView =
                super.onCreateRecyclerView(inflater, parent, savedInstanceState);
        return AccessibilityFragmentUtils.addCollectionInfoToAccessibilityDelegate(recyclerView);
    }
}
