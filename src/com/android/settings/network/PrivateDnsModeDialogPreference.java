/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.settings.network;

import static android.net.ConnectivitySettingsManager.PRIVATE_DNS_MODE_OFF;
import static android.net.ConnectivitySettingsManager.PRIVATE_DNS_MODE_OPPORTUNISTIC;
import static android.net.ConnectivitySettingsManager.PRIVATE_DNS_MODE_PROVIDER_HOSTNAME;

import static com.android.settingslib.RestrictedLockUtils.EnforcedAdmin;

import android.app.settings.SettingsEnums;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.ConnectivitySettingsManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceViewHolder;

import com.android.settings.R;
import com.android.settings.overlay.FeatureFactory;
import com.android.settings.utils.AnnotationSpan;
import com.android.settingslib.CustomDialogPreferenceCompat;
import com.android.settingslib.HelpUtils;
import com.android.settingslib.RestrictedLockUtils;
import com.android.settingslib.RestrictedLockUtilsInternal;

import com.google.android.material.textfield.TextInputLayout;
import com.google.common.net.InternetDomainName;

import java.util.HashMap;
import java.util.Map;

/**
 * Dialog to set the Private DNS
 */
public class PrivateDnsModeDialogPreference extends CustomDialogPreferenceCompat implements
        RadioGroup.OnCheckedChangeListener, TextWatcher {

    public static final String ANNOTATION_URL = "url";

    private static final String TAG = "PrivateDnsModeDialog";
    // DNS_MODE -> RadioButton id
    private static final Map<Integer, Integer> PRIVATE_DNS_MAP;

    static {
        PRIVATE_DNS_MAP = new HashMap<>();
        PRIVATE_DNS_MAP.put(PRIVATE_DNS_MODE_OFF, R.id.private_dns_mode_off);
        PRIVATE_DNS_MAP.put(PRIVATE_DNS_MODE_OPPORTUNISTIC, R.id.private_dns_mode_opportunistic);
        PRIVATE_DNS_MAP.put(PRIVATE_DNS_MODE_PROVIDER_HOSTNAME, R.id.private_dns_mode_provider);
    }

    @VisibleForTesting
    TextInputLayout mHostnameLayout;
    @VisibleForTesting
    EditText mHostnameText;
    @VisibleForTesting
    RadioGroup mRadioGroup;
    @VisibleForTesting
    int mMode;

    public PrivateDnsModeDialogPreference(Context context) {
        super(context);
    }

    public PrivateDnsModeDialogPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PrivateDnsModeDialogPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public PrivateDnsModeDialogPreference(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    private final AnnotationSpan.LinkInfo mUrlLinkInfo = new AnnotationSpan.LinkInfo(
            ANNOTATION_URL, (widget) -> {
        final Context context = widget.getContext();
        final Intent intent = HelpUtils.getHelpIntent(context,
                context.getString(R.string.help_uri_private_dns),
                context.getClass().getName());
        if (intent != null) {
            try {
                widget.startActivityForResult(intent, 0);
            } catch (ActivityNotFoundException e) {
                Log.w(TAG, "Activity was not found for intent, " + intent.toString());
            }
        }
    });

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        if (isDisabledByAdmin()) {
            // If the preference is disabled by the admin, set the inner item as enabled so
            // it could act as a click target. The preference itself will have been disabled
            // by the controller.
            holder.itemView.setEnabled(true);
        }

        setSaveButtonListener();
    }

    @Override
    protected void onBindDialogView(View view) {
        final Context context = getContext();
        mMode = ConnectivitySettingsManager.getPrivateDnsMode(context);
        mRadioGroup = view.findViewById(R.id.private_dns_radio_group);
        mRadioGroup.check(PRIVATE_DNS_MAP.getOrDefault(mMode, R.id.private_dns_mode_opportunistic));
        mRadioGroup.setOnCheckedChangeListener(this);

        // Initial radio button text
        final RadioButton offRadioButton = view.findViewById(R.id.private_dns_mode_off);
        offRadioButton.setText(com.android.settingslib.R.string.private_dns_mode_off);
        final RadioButton opportunisticRadioButton =
                view.findViewById(R.id.private_dns_mode_opportunistic);
        opportunisticRadioButton.setText(
                com.android.settingslib.R.string.private_dns_mode_opportunistic);
        final RadioButton providerRadioButton = view.findViewById(R.id.private_dns_mode_provider);
        providerRadioButton.setText(com.android.settingslib.R.string.private_dns_mode_provider);

        mHostnameLayout = view.findViewById(R.id.private_dns_mode_provider_hostname_layout);
        mHostnameText = view.findViewById(R.id.private_dns_mode_provider_hostname);
        if (mHostnameText != null) {
            mHostnameText.setText(ConnectivitySettingsManager.getPrivateDnsHostname(context));
            mHostnameText.addTextChangedListener(this);
        }

        final TextView helpTextView = view.findViewById(R.id.private_dns_help_info);
        helpTextView.setMovementMethod(LinkMovementMethod.getInstance());
        final Intent helpIntent = HelpUtils.getHelpIntent(context,
                context.getString(R.string.help_uri_private_dns),
                context.getClass().getName());
        final AnnotationSpan.LinkInfo linkInfo = new AnnotationSpan.LinkInfo(context,
                ANNOTATION_URL, helpIntent);
        if (linkInfo.isActionable()) {
            helpTextView.setText(AnnotationSpan.linkify(
                    context.getText(R.string.private_dns_help_message), linkInfo));
        } else {
            helpTextView.setText("");
        }

        updateDialogInfo();
    }

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {
        if (checkedId == R.id.private_dns_mode_off) {
            mMode = PRIVATE_DNS_MODE_OFF;
        } else if (checkedId == R.id.private_dns_mode_opportunistic) {
            mMode = PRIVATE_DNS_MODE_OPPORTUNISTIC;
        } else if (checkedId == R.id.private_dns_mode_provider) {
            mMode = PRIVATE_DNS_MODE_PROVIDER_HOSTNAME;
        }
        updateDialogInfo();
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    @Override
    public void afterTextChanged(Editable s) {
        updateDialogInfo();
    }

    @Override
    public void performClick() {
        EnforcedAdmin enforcedAdmin = getEnforcedAdmin();

        if (enforcedAdmin == null) {
            // If the restriction is not restricted by admin, continue as usual.
            super.performClick();
        } else {
            // Show a dialog explaining to the user why they cannot change the preference.
            RestrictedLockUtils.sendShowAdminSupportDetailsIntent(getContext(), enforcedAdmin);
        }
    }

    private EnforcedAdmin getEnforcedAdmin() {
        return RestrictedLockUtilsInternal.checkIfRestrictionEnforced(
                getContext(), UserManager.DISALLOW_CONFIG_PRIVATE_DNS, UserHandle.myUserId());
    }

    private boolean isDisabledByAdmin() {
        return getEnforcedAdmin() != null;
    }

    private void updateDialogInfo() {
        final boolean modeProvider = PRIVATE_DNS_MODE_PROVIDER_HOSTNAME == mMode;
        if (mHostnameLayout != null) {
            mHostnameLayout.setEnabled(modeProvider);
            mHostnameLayout.setErrorEnabled(false);
        }
    }

    private void setSaveButtonListener() {
        View.OnClickListener onClickListener = v -> doSaveButton();
        DialogInterface.OnShowListener onShowListener = dialog -> {
            if (dialog == null) {
                Log.e(TAG, "The DialogInterface is null!");
                return;
            }
            Button saveButton = ((AlertDialog) dialog).getButton(DialogInterface.BUTTON_POSITIVE);
            if (saveButton == null) {
                Log.e(TAG, "Can't get the save button!");
                return;
            }
            saveButton.setOnClickListener(onClickListener);
        };
        setOnShowListener(onShowListener);
    }

    @VisibleForTesting
    void doSaveButton() {
        Context context = getContext();
        if (mMode == PRIVATE_DNS_MODE_PROVIDER_HOSTNAME) {
            if (mHostnameLayout == null || mHostnameText == null) {
                Log.e(TAG, "Can't find hostname resources!");
                return;
            }
            if (mHostnameText.getText().isEmpty()) {
                mHostnameLayout.setError(context.getString(R.string.private_dns_field_require));
                Log.w(TAG, "The hostname is empty!");
                return;
            }
            if (!InternetDomainName.isValid(mHostnameText.getText().toString())) {
                mHostnameLayout.setError(context.getString(R.string.private_dns_hostname_invalid));
                Log.w(TAG, "The hostname is invalid!");
                return;
            }

            ConnectivitySettingsManager.setPrivateDnsHostname(context,
                    mHostnameText.getText().toString());
        }

        ConnectivitySettingsManager.setPrivateDnsMode(context, mMode);

        FeatureFactory.getFeatureFactory().getMetricsFeatureProvider()
                .action(context, SettingsEnums.ACTION_PRIVATE_DNS_MODE, mMode);
        getDialog().dismiss();
    }
}
