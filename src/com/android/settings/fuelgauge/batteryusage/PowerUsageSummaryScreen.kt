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
package com.android.settings.fuelgauge.batteryusage

import android.content.Context
import com.android.settings.R
import com.android.settings.display.BatteryPercentageSwitchPreference
import com.android.settings.flags.Flags
import com.android.settings.fuelgauge.BatteryHeaderPreference
import com.android.settingslib.metadata.PreferenceAvailabilityProvider
import com.android.settingslib.metadata.PreferenceIconProvider
import com.android.settingslib.metadata.ProvidePreferenceScreen
import com.android.settingslib.metadata.preferenceHierarchy
import com.android.settingslib.preference.PreferenceScreenCreator
import com.android.settingslib.widget.SettingsThemeHelper.isExpressiveTheme

@ProvidePreferenceScreen(PowerUsageSummaryScreen.KEY)
class PowerUsageSummaryScreen :
    PreferenceScreenCreator, PreferenceAvailabilityProvider, PreferenceIconProvider {
    override val key: String
        get() = KEY

    override val title: Int
        get() = R.string.power_usage_summary_title

    override val keywords: Int
        get() = R.string.keywords_battery

    override fun isFlagEnabled(context: Context) = Flags.catalystPowerUsageSummaryScreen()

    override fun hasCompleteHierarchy() = false

    override fun fragmentClass() = PowerUsageSummary::class.java

    override fun isAvailable(context: Context) =
        context.resources.getBoolean(R.bool.config_show_top_level_battery)

    override fun getIcon(context: Context) =
        when {
            isExpressiveTheme(context) -> R.drawable.ic_homepage_battery
            Flags.homepageRevamp() -> R.drawable.ic_settings_battery_filled
            else -> R.drawable.ic_settings_battery_white
        }

    override fun getPreferenceHierarchy(context: Context) =
        preferenceHierarchy(context, this) {
            +BatteryHeaderPreference()
            +BatteryPercentageSwitchPreference()
        }

    companion object {
        const val KEY = "power_usage_summary_screen"
    }
}
