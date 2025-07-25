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

package com.android.settings.vpn2;

import android.app.Dialog;
import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.DialogInterface;
import android.net.VpnManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.UserHandle;
import android.security.Credentials;
import android.security.LegacyVpnProfileStore;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.android.internal.net.LegacyVpnInfo;
import com.android.internal.net.VpnProfile;
import com.android.settings.R;
import com.android.settings.core.instrumentation.InstrumentedDialogFragment;

/**
 * Fragment wrapper around a {@link ConfigDialog}.
 */
public class ConfigDialogFragment extends InstrumentedDialogFragment implements
        DialogInterface.OnClickListener, DialogInterface.OnShowListener, View.OnClickListener,
        ConfirmLockdownFragment.ConfirmLockdownListener {
    private static final String TAG_CONFIG_DIALOG = "vpnconfigdialog";
    private static final String TAG = "ConfigDialogFragment";

    private static final String ARG_PROFILE = "profile";
    private static final String ARG_EDITING = "editing";
    private static final String ARG_EXISTS = "exists";

    private Context mContext;
    private VpnManager mService;


    @Override
    public int getMetricsCategory() {
        return SettingsEnums.DIALOG_LEGACY_VPN_CONFIG;
    }

    public static void show(VpnSettings parent, VpnProfile profile, boolean edit, boolean exists) {
        if (!parent.isAdded()) return;

        Bundle args = new Bundle();
        args.putParcelable(ARG_PROFILE, profile);
        args.putBoolean(ARG_EDITING, edit);
        args.putBoolean(ARG_EXISTS, exists);

        final ConfigDialogFragment frag = new ConfigDialogFragment();
        frag.setArguments(args);
        frag.setTargetFragment(parent, 0);
        frag.show(parent.getFragmentManager(), TAG_CONFIG_DIALOG);
    }

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        mContext = context;
        mService = context.getSystemService(VpnManager.class);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle args = getArguments();
        VpnProfile profile = (VpnProfile) args.getParcelable(ARG_PROFILE);
        boolean editing = args.getBoolean(ARG_EDITING);
        boolean exists = args.getBoolean(ARG_EXISTS);

        final Dialog dialog = new ConfigDialog(getActivity(), this, profile, editing, exists);
        dialog.setOnShowListener(this);
        return dialog;
    }

    /**
     * Override for the default onClick handler which also calls dismiss().
     *
     * @see DialogInterface.OnClickListener#onClick(DialogInterface, int)
     */
    @Override
    public void onShow(DialogInterface dialogInterface) {
        ((AlertDialog) getDialog()).getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(this);
    }

    @Override
    public void onClick(View positiveButton) {
        onClick(getDialog(), AlertDialog.BUTTON_POSITIVE);
    }

    @Override
    public void onConfirmLockdown(Bundle options, boolean isAlwaysOn, boolean isLockdown) {
        VpnProfile profile = (VpnProfile) options.getParcelable(ARG_PROFILE);
        connect(profile, isAlwaysOn);
        dismiss();
    }

    @Override
    public void onClick(DialogInterface dialogInterface, int button) {
        ConfigDialog dialog = (ConfigDialog) getDialog();
        if (dialog == null) {
            Log.e(TAG, "ConfigDialog object is null");
            return;
        }
        VpnProfile profile = dialog.getProfile();

        if (button == DialogInterface.BUTTON_POSITIVE) {
            if (!dialog.validate()) return;
            // Possibly throw up a dialog to explain lockdown VPN.
            final boolean shouldLockdown = dialog.isVpnAlwaysOn();
            final boolean shouldConnect = shouldLockdown || !dialog.isEditing();
            final boolean wasLockdown = VpnUtils.isAnyLockdownActive(mContext);
            try {
                final boolean replace = VpnUtils.isVpnActive(mContext);
                if (shouldConnect && !isConnected(profile) &&
                        ConfirmLockdownFragment.shouldShow(replace, wasLockdown, shouldLockdown)) {
                    final Bundle opts = new Bundle();
                    opts.putParcelable(ARG_PROFILE, profile);
                    ConfirmLockdownFragment.show(this, replace, /* alwaysOn */ shouldLockdown,
                           /* from */  wasLockdown, /* to */ shouldLockdown, opts);
                } else if (shouldConnect) {
                    connect(profile, shouldLockdown);
                } else {
                    save(profile, false);
                }
            } catch (RemoteException e) {
                Log.w(TAG, "Failed to check active VPN state. Skipping.", e);
            }
        } else if (button == DialogInterface.BUTTON_NEUTRAL) {
            // Disable profile if connected
            if (!disconnect(profile)) {
                Log.e(TAG, "Failed to disconnect VPN. Leaving profile in keystore.");
                return;
            }

            // Delete from profile store.
            LegacyVpnProfileStore.remove(Credentials.VPN + profile.key);

            updateLockdownVpn(false, profile);
        }
        dismiss();
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        dismiss();
        super.onCancel(dialog);
    }

    private void updateLockdownVpn(boolean isVpnAlwaysOn, VpnProfile profile) {
        // Save lockdown vpn
        if (isVpnAlwaysOn) {
            // Show toast if vpn profile is not valid
            if (!profile.isValidLockdownProfile()) {
                Toast.makeText(mContext, R.string.vpn_lockdown_config_error,
                        Toast.LENGTH_LONG).show();
                return;
            }

            mService.setAlwaysOnVpnPackageForUser(UserHandle.myUserId(), null,
                    /* lockdownEnabled */ false, /* lockdownAllowlist */ null);
            VpnUtils.setLockdownVpn(mContext, profile.key);
        } else {
            // update only if lockdown vpn has been changed
            if (VpnUtils.isVpnLockdown(profile.key)) {
                VpnUtils.clearLockdownVpn(mContext);
            }
        }
    }

    private void save(VpnProfile profile, boolean lockdown) {
        LegacyVpnProfileStore.put(Credentials.VPN + profile.key, profile.encode());

        // Flush out old version of profile
        disconnect(profile);

        // Notify lockdown VPN that the profile has changed.
        updateLockdownVpn(lockdown, profile);
    }

    private void connect(VpnProfile profile, boolean lockdown) {
        save(profile, lockdown);

        // Now try to start the VPN - this is not necessary if the profile is set as lockdown,
        // because just saving the profile in this mode will start a connection.
        if (!VpnUtils.isVpnLockdown(profile.key)) {
            VpnUtils.clearLockdownVpn(mContext);
            try {
                mService.startLegacyVpn(profile);
            } catch (IllegalStateException e) {
                Toast.makeText(mContext, R.string.vpn_no_network, Toast.LENGTH_LONG).show();
            } catch (UnsupportedOperationException e) {
                Log.e(TAG, "Attempted to start an unsupported VPN type.");
                final AlertDialog unusedDialog = new AlertDialog.Builder(mContext)
                        .setMessage(R.string.vpn_start_unsupported)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            }
        }
    }

    /**
     * Ensure that the VPN profile pointed at by {@param profile} is disconnected.
     *
     * @return {@code true} iff this VPN profile is no longer connected. Note that another profile
     *         may still be active - this function will then do nothing but still return success.
     */
    private boolean disconnect(VpnProfile profile) {
        try {
            if (!isConnected(profile)) {
                return true;
            }
            return VpnUtils.disconnectLegacyVpn(getContext());
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to disconnect", e);
            return false;
        }
    }

    private boolean isConnected(VpnProfile profile) throws RemoteException {
        LegacyVpnInfo connected = mService.getLegacyVpnInfo(UserHandle.myUserId());
        return connected != null && profile.key.equals(connected.key);
    }
}
