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

package com.android.settings.display.darkmode

import android.Manifest
import android.app.settings.SettingsEnums.ACTION_DARK_THEME
import android.content.Context
import android.os.PowerManager
import androidx.preference.Preference
import com.android.settings.R
import com.android.settings.contract.KEY_DARK_THEME
import com.android.settings.flags.Flags
import com.android.settings.metrics.PreferenceActionMetricsProvider
import com.android.settingslib.PrimarySwitchPreferenceBinding
import com.android.settingslib.datastore.KeyValueStore
import com.android.settingslib.datastore.Permissions
import com.android.settingslib.metadata.BooleanValuePreference
import com.android.settingslib.metadata.PreferenceMetadata
import com.android.settingslib.metadata.PreferenceSummaryProvider
import com.android.settingslib.metadata.ProvidePreferenceScreen
import com.android.settingslib.metadata.ReadWritePermit
import com.android.settingslib.metadata.SensitivityLevel
import com.android.settingslib.metadata.preferenceHierarchy
import com.android.settingslib.preference.PreferenceScreenCreator

// LINT.IfChange
@ProvidePreferenceScreen(DarkModeScreen.KEY)
class DarkModeScreen(context: Context) :
    PreferenceScreenCreator,
    PrimarySwitchPreferenceBinding,
    PreferenceActionMetricsProvider,
    BooleanValuePreference,
    PreferenceSummaryProvider {

    private val darkModeStorage = DarkModeStorage(context)

    override val key: String
        get() = KEY

    override val title: Int
        get() = R.string.dark_ui_mode

    override val keywords: Int
        get() = R.string.keywords_dark_ui_mode

    override val preferenceActionMetrics: Int
        get() = ACTION_DARK_THEME

    override fun tags(context: Context) = arrayOf(KEY_DARK_THEME)

    override fun getReadPermissions(context: Context) = Permissions.EMPTY

    override fun getWritePermissions(context: Context) =
        Permissions.allOf(Manifest.permission.MODIFY_DAY_NIGHT_MODE)

    override fun getReadPermit(context: Context, callingPid: Int, callingUid: Int) =
        ReadWritePermit.ALLOW

    override fun getWritePermit(
        context: Context,
        value: Boolean?,
        callingPid: Int,
        callingUid: Int,
    ) = ReadWritePermit.ALLOW

    override val sensitivityLevel
        get() = SensitivityLevel.NO_SENSITIVITY

    override fun isFlagEnabled(context: Context) = Flags.catalystDarkUiMode()

    override fun fragmentClass() = DarkModeSettingsFragment::class.java

    override fun hasCompleteHierarchy() = false

    override fun getPreferenceHierarchy(context: Context) = preferenceHierarchy(context, this) {}

    override fun storage(context: Context): KeyValueStore = darkModeStorage

    override fun bind(preference: Preference, metadata: PreferenceMetadata) {
        super.bind(preference, metadata)
        if (preference is DarkModePreference) preference.setCatalystEnabled(true)
    }

    override fun isEnabled(context: Context) = !context.isPowerSaveMode()

    override fun getSummary(context: Context): CharSequence? {
        val active = darkModeStorage.getBoolean(KEY) == true
        return when {
            !context.isPowerSaveMode() -> AutoDarkTheme.getStatus(context, active)
            active -> context.getString(R.string.dark_ui_mode_disabled_summary_dark_theme_on)
            else -> context.getString(R.string.dark_ui_mode_disabled_summary_dark_theme_off)
        }
    }

    companion object {
        const val KEY = "dark_ui_mode"

        private fun Context.isPowerSaveMode() =
            getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
    }
}
// LINT.ThenChange(../DarkUIPreferenceController.java)
