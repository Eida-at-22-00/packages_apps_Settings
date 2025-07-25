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

package com.android.settings.testutils.shadow;

import static com.google.common.truth.Truth.assertThat;

import android.accessibilityservice.AccessibilityShortcutInfo;
import android.annotation.NonNull;
import android.annotation.UserIdInt;
import android.content.ComponentName;
import android.content.Context;
import android.util.ArrayMap;
import android.view.accessibility.AccessibilityManager;

import com.android.internal.accessibility.common.ShortcutConstants;
import com.android.internal.accessibility.common.ShortcutConstants.UserShortcutType;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shadow of {@link AccessibilityManager} with the hidden methods
 */
@Implements(AccessibilityManager.class)
public class ShadowAccessibilityManager extends org.robolectric.shadows.ShadowAccessibilityManager {
    private Map<ComponentName, ComponentName> mA11yFeatureToTileMap = new ArrayMap<>();
    private List<AccessibilityShortcutInfo> mInstalledAccessibilityShortcutList = List.of();
    private Map<Integer, List<String>> mShortcutTargets = new ArrayMap<>();

    /**
     * Implements a hidden method {@link AccessibilityManager#getA11yFeatureToTileMap}
     */
    @Implementation
    public Map<ComponentName, ComponentName> getA11yFeatureToTileMap(@UserIdInt int userId) {
        return mA11yFeatureToTileMap;
    }

    /**
     * Set fake a11y feature to tile mapping
     */
    public void setA11yFeatureToTileMap(
            @NonNull Map<ComponentName, ComponentName> a11yFeatureToTileMap) {
        mA11yFeatureToTileMap = a11yFeatureToTileMap;
    }

    /**
     * Implements the hidden method
     * {@link AccessibilityManager#getInstalledAccessibilityShortcutListAsUser}.
     */
    @Implementation
    public List<AccessibilityShortcutInfo> getInstalledAccessibilityShortcutListAsUser(
            @NonNull Context context, @UserIdInt int userId) {
        return mInstalledAccessibilityShortcutList;
    }

    /**
     * Sets the value to be returned by {@link #getInstalledAccessibilityShortcutListAsUser}.
     */
    public void setInstalledAccessibilityShortcutListAsUser(
            @NonNull List<AccessibilityShortcutInfo> installedAccessibilityShortcutList) {
        mInstalledAccessibilityShortcutList = installedAccessibilityShortcutList;
    }

    /**
     * Implements the hidden method
     * {@link AccessibilityManager#getAccessibilityShortcutTargets}.
     */
    @Implementation
    public List<String> getAccessibilityShortcutTargets(
            @UserShortcutType int shortcutType) {
        if (!mShortcutTargets.containsKey(shortcutType)) {
            mShortcutTargets.put(shortcutType, new ArrayList<>());
        }
        List<String> targets = mShortcutTargets.get(shortcutType);
        assertThat(targets).isNotNull();
        return targets;
    }

    /**
     * Used by tests to easily write directly to a shortcut targets value
     */
    public void setAccessibilityShortcutTargets(int shortcutTypes, List<String> targets) {
        for (int type : ShortcutConstants.USER_SHORTCUT_TYPES) {
            if ((type & shortcutTypes) == type) {
                mShortcutTargets.put(type, List.copyOf(targets));
            }
        }
    }
}
