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

package com.android.settings.accessibility

import android.content.Context
import android.hardware.display.ColorDisplayManager
import com.android.settings.R
import com.android.settings.Settings.ColorAndMotionActivity
import com.android.settings.display.darkmode.DarkModeScreen
import com.android.settings.flags.Flags
import com.android.settings.utils.makeLaunchIntent
import com.android.settingslib.metadata.PreferenceCategory
import com.android.settingslib.metadata.PreferenceGroup
import com.android.settingslib.metadata.PreferenceHierarchy
import com.android.settingslib.metadata.PreferenceMetadata
import com.android.settingslib.metadata.ProvidePreferenceScreen
import com.android.settingslib.metadata.preferenceHierarchy
import com.android.settingslib.preference.PreferenceScreenCreator

@ProvidePreferenceScreen(ColorAndMotionScreen.KEY)
class ColorAndMotionScreen : PreferenceScreenCreator {
    override val key: String
        get() = KEY

    override val title: Int
        get() = R.string.accessibility_color_and_motion_title

    override val icon: Int
        get() = R.drawable.ic_color_and_motion

    override fun isFlagEnabled(context: Context) = Flags.catalystAccessibilityColorAndMotion()

    override fun hasCompleteHierarchy(): Boolean = true

    override fun fragmentClass() = ColorAndMotionFragment::class.java

    override fun getPreferenceHierarchy(context: Context): PreferenceHierarchy {
        // LINT.IfChange(ui_hierarchy)
        if (ColorDisplayManager.isColorTransformAccelerated(context)) {
            return preferenceHierarchy(context, this) {
                +DaltonizerPreference()
                +ColorInversionPreference()
                +DarkModeScreen.KEY
                +RemoveAnimationsPreference()
            }
        } else {
            return preferenceHierarchy(context, this) {
                +ColorInversionPreference()
                +DarkModeScreen.KEY
                +PreferenceCategory(
                    "experimental_category",
                    R.string.experimental_category_title
                ) += {
                    +DaltonizerPreference()
                    +RemoveAnimationsPreference()
                }
            }
        }
        // LINT.ThenChange(/res/xml/accessibility_color_and_motion.xml, /src/com/android/settings/accessibility/ColorAndMotionFragment.java:ui_hierarchy)
    }

    override fun getLaunchIntent(context: Context, metadata: PreferenceMetadata?) =
        makeLaunchIntent(context, ColorAndMotionActivity::class.java, metadata?.key)

    companion object {
        const val KEY = "accessibility_color_and_motion"
    }
}
