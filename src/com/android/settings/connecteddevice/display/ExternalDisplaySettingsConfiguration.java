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
package com.android.settings.connecteddevice.display;

import static android.content.Context.DISPLAY_SERVICE;
import static android.hardware.display.DisplayManager.DISPLAY_CATEGORY_ALL_INCLUDING_DISABLED;
import static android.hardware.display.DisplayManager.EVENT_TYPE_DISPLAY_ADDED;
import static android.hardware.display.DisplayManager.EVENT_TYPE_DISPLAY_CHANGED;
import static android.hardware.display.DisplayManager.EVENT_TYPE_DISPLAY_REMOVED;
import static android.hardware.display.DisplayManager.PRIVATE_EVENT_TYPE_DISPLAY_CONNECTION_CHANGED;
import static android.view.Display.INVALID_DISPLAY;

import static com.android.server.display.feature.flags.Flags.enableModeLimitForExternalDisplay;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManagerGlobal;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.view.Display;
import android.view.Display.Mode;
import android.view.IWindowManager;
import android.view.WindowManagerGlobal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.settings.R;
import com.android.settings.flags.FeatureFlags;
import com.android.settings.flags.FeatureFlagsImpl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class ExternalDisplaySettingsConfiguration {
    static final String VIRTUAL_DISPLAY_PACKAGE_NAME_SYSTEM_PROPERTY =
            "persist.demo.userrotation.package_name";
    static final String DISPLAY_ID_ARG = "display_id";
    static final int EXTERNAL_DISPLAY_NOT_FOUND_RESOURCE = R.string.external_display_not_found;
    static final int EXTERNAL_DISPLAY_HELP_URL = R.string.help_url_external_display;

    public static class SystemServicesProvider {
        @Nullable
        private IWindowManager mWindowManager;
        @Nullable
        private DisplayManager mDisplayManager;
        @Nullable
        protected Context mContext;
        /**
         * @param name of a system property.
         * @return the value of the system property.
         */
        @NonNull
        public String getSystemProperty(@NonNull String name) {
            return SystemProperties.get(name);
        }

        /**
         * @return return public Display manager.
         */
        @Nullable
        public DisplayManager getDisplayManager() {
            if (mDisplayManager == null && getContext() != null) {
                mDisplayManager = (DisplayManager) getContext().getSystemService(DISPLAY_SERVICE);
            }
            return mDisplayManager;
        }

        /**
         * @return internal IWindowManager
         */
        @Nullable
        public IWindowManager getWindowManager() {
            if (mWindowManager == null) {
                mWindowManager = WindowManagerGlobal.getWindowManagerService();
            }
            return mWindowManager;
        }

        /**
         * @return context.
         */
        @Nullable
        public Context getContext() {
            return mContext;
        }
    }

    public static class Injector extends SystemServicesProvider {
        @NonNull
        private final FeatureFlags mFlags;
        @NonNull
        private final Handler mHandler;

        Injector(@Nullable Context context) {
            this(context, new DesktopExperienceFlags(new FeatureFlagsImpl()),
                    new Handler(Looper.getMainLooper()));
        }

        Injector(@Nullable Context context, @NonNull FeatureFlags flags, @NonNull Handler handler) {
            mContext = context;
            mFlags = flags;
            mHandler = handler;
        }

        private static DisplayDevice wrapDmDisplay(Display display, DisplayIsEnabled isEnabled) {
            return new DisplayDevice(display.getDisplayId(), display.getName(),
                        display.getMode(), List.<Mode>of(display.getSupportedModes()), isEnabled);
        }

        /**
         * @return all displays including disabled.
         */
        @NonNull
        public List<DisplayDevice> getConnectedDisplays() {
            var dm = getDisplayManager();
            if (dm == null) {
                return List.of();
            }

            var enabledIds = new HashSet<Integer>();
            for (Display d : dm.getDisplays()) {
                enabledIds.add(d.getDisplayId());
            }

            var displays = new ArrayList<DisplayDevice>();
            for (Display d : dm.getDisplays(DISPLAY_CATEGORY_ALL_INCLUDING_DISABLED)) {
                if (!isDisplayAllowed(d, this)) {
                    continue;
                }
                var isEnabled = enabledIds.contains(d.getDisplayId())
                        ? DisplayIsEnabled.YES : DisplayIsEnabled.NO;
                displays.add(wrapDmDisplay(d, isEnabled));
            }
            return displays;
        }

        /**
         * @param displayId which must be returned
         * @return display object for the displayId, or null if display is not a connected display,
         *         the ID was not found, or the ID was invalid
         */
        @Nullable
        public DisplayDevice getDisplay(int displayId) {
            if (displayId == INVALID_DISPLAY) {
                return null;
            }
            var dm = getDisplayManager();
            if (dm == null) {
                return null;
            }
            var display = dm.getDisplay(displayId);
            if (display == null || !isDisplayAllowed(display, this)) {
                return null;
            }
            return wrapDmDisplay(display, DisplayIsEnabled.UNKNOWN);
        }

        /**
         * Register display listener.
         */
        public void registerDisplayListener(@NonNull DisplayManager.DisplayListener listener) {
            var dm = getDisplayManager();
            if (dm == null) {
                return;
            }
            dm.registerDisplayListener(listener, mHandler, EVENT_TYPE_DISPLAY_ADDED
                    | EVENT_TYPE_DISPLAY_CHANGED | EVENT_TYPE_DISPLAY_REMOVED,
                    PRIVATE_EVENT_TYPE_DISPLAY_CONNECTION_CHANGED);
        }

        /**
         * Unregister display listener.
         */
        public void unregisterDisplayListener(@NonNull DisplayManager.DisplayListener listener) {
            var dm = getDisplayManager();
            if (dm == null) {
                return;
            }
            dm.unregisterDisplayListener(listener);
        }

        /**
         * @return feature flags.
         */
        @NonNull
        public FeatureFlags getFlags() {
            return mFlags;
        }

        /**
         * Enable connected display.
         */
        public boolean enableConnectedDisplay(int displayId) {
            var dm = getDisplayManager();
            if (dm == null) {
                return false;
            }
            dm.enableConnectedDisplay(displayId);
            return true;
        }

        /**
         * Disable connected display.
         */
        public boolean disableConnectedDisplay(int displayId) {
            var dm = getDisplayManager();
            if (dm == null) {
                return false;
            }
            dm.disableConnectedDisplay(displayId);
            return true;
        }

        /**
         * @return handler
         */
        @NonNull
        public Handler getHandler() {
            return mHandler;
        }

        /**
         * Get display rotation
         * @param displayId display identifier
         * @return rotation
         */
        public int getDisplayUserRotation(int displayId) {
            var wm = getWindowManager();
            if (wm == null) {
                return 0;
            }
            try {
                return wm.getDisplayUserRotation(displayId);
            } catch (RemoteException e) {
                return 0;
            }
        }

        /**
         * Freeze rotation of the display in the specified rotation.
         * @param displayId display identifier
         * @param rotation [0, 1, 2, 3]
         * @return true if successful
         */
        public boolean freezeDisplayRotation(int displayId, int rotation) {
            var wm = getWindowManager();
            if (wm == null) {
                return false;
            }
            try {
                wm.freezeDisplayRotation(displayId, rotation,
                        "ExternalDisplayPreferenceFragment");
                return true;
            } catch (RemoteException e) {
                return false;
            }
        }

        /**
         * Enforce display mode on the given display.
         */
        public void setUserPreferredDisplayMode(int displayId, @NonNull Mode mode) {
            DisplayManagerGlobal.getInstance().setUserPreferredDisplayMode(displayId, mode);
        }

        /**
         * @return true if the display mode limit flag enabled.
         */
        public boolean isModeLimitForExternalDisplayEnabled() {
            return enableModeLimitForExternalDisplay();
        }
    }

    public abstract static class DisplayListener implements DisplayManager.DisplayListener {
        @Override
        public void onDisplayAdded(int displayId) {
            update(displayId);
        }

        @Override
        public void onDisplayRemoved(int displayId) {
            update(displayId);
        }

        @Override
        public void onDisplayChanged(int displayId) {
            update(displayId);
        }

        @Override
        public void onDisplayConnected(int displayId) {
            update(displayId);
        }

        @Override
        public void onDisplayDisconnected(int displayId) {
            update(displayId);
        }

        /**
         * Called from other listener methods to trigger update of the settings page.
         */
        public abstract void update(int displayId);
    }

    /**
     * @return whether the settings page is enabled or not.
     */
    public static boolean isExternalDisplaySettingsPageEnabled(@NonNull FeatureFlags flags) {
        return flags.rotationConnectedDisplaySetting()
                || flags.resolutionAndEnableConnectedDisplaySetting()
                || flags.displayTopologyPaneInDisplayList();
    }

    private static boolean isDisplayAllowed(Display display, SystemServicesProvider props) {
        return display.getType() == Display.TYPE_EXTERNAL
                || display.getType() == Display.TYPE_OVERLAY
                || isVirtualDisplayAllowed(display, props);
    }

    static boolean isTopologyPaneEnabled(@Nullable Injector injector) {
        return injector != null && injector.getFlags().displayTopologyPaneInDisplayList();
    }

    private static boolean isVirtualDisplayAllowed(@NonNull Display display,
            @NonNull SystemServicesProvider properties) {
        var sysProp = properties.getSystemProperty(VIRTUAL_DISPLAY_PACKAGE_NAME_SYSTEM_PROPERTY);
        return !sysProp.isEmpty() && display.getType() == Display.TYPE_VIRTUAL
                       && sysProp.equals(display.getOwnerPackageName());
    }

    static boolean isUseDisplaySettingEnabled(@Nullable Injector injector) {
        return injector != null && injector.getFlags().resolutionAndEnableConnectedDisplaySetting()
                && !injector.getFlags().displayTopologyPaneInDisplayList();
    }

    static boolean isResolutionSettingEnabled(@Nullable Injector injector) {
        return injector != null && injector.getFlags().resolutionAndEnableConnectedDisplaySetting();
    }

    static boolean isRotationSettingEnabled(@Nullable Injector injector) {
        return injector != null && injector.getFlags().rotationConnectedDisplaySetting();
    }

    static boolean isDisplaySizeSettingEnabled(@Nullable Injector injector) {
        return injector != null && injector.getFlags().displaySizeConnectedDisplaySetting();
    }
}
