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

package com.android.settings.deviceinfo.simstatus;

import static com.android.settings.deviceinfo.simstatus.SimStatusDialogController.CELL_DATA_NETWORK_TYPE_VALUE_ID;
import static com.android.settings.deviceinfo.simstatus.SimStatusDialogController.CELLULAR_NETWORK_STATE;
import static com.android.settings.deviceinfo.simstatus.SimStatusDialogController.CELL_VOICE_NETWORK_TYPE_VALUE_ID;
import static com.android.settings.deviceinfo.simstatus.SimStatusDialogController.ICCID_INFO_LABEL_ID;
import static com.android.settings.deviceinfo.simstatus.SimStatusDialogController.ICCID_INFO_VALUE_ID;
import static com.android.settings.deviceinfo.simstatus.SimStatusDialogController.IMS_REGISTRATION_STATE_LABEL_ID;
import static com.android.settings.deviceinfo.simstatus.SimStatusDialogController.IMS_REGISTRATION_STATE_VALUE_ID;
import static com.android.settings.deviceinfo.simstatus.SimStatusDialogController.MAX_PHONE_COUNT_SINGLE_SIM;
import static com.android.settings.deviceinfo.simstatus.SimStatusDialogController.NETWORK_PROVIDER_VALUE_ID;
import static com.android.settings.deviceinfo.simstatus.SimStatusDialogController.OPERATOR_INFO_LABEL_ID;
import static com.android.settings.deviceinfo.simstatus.SimStatusDialogController.OPERATOR_INFO_VALUE_ID;
import static com.android.settings.deviceinfo.simstatus.SimStatusDialogController.ROAMING_INFO_VALUE_ID;
import static com.android.settings.deviceinfo.simstatus.SimStatusDialogController.SERVICE_STATE_VALUE_ID;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.telephony.UiccCardInfo;
import android.telephony.euicc.EuiccManager;

import androidx.lifecycle.LifecycleOwner;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.settings.testutils.ResourcesUtils;
import com.android.settingslib.core.lifecycle.Lifecycle;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(AndroidJUnit4.class)
public class SimStatusDialogControllerTest {

    @Mock
    private SimStatusDialogFragment mDialog;
    @Mock
    private TelephonyManager mTelephonyManager;
    @Mock
    private TelephonyManager mTelephonyManagerForSub1;
    @Mock
    private TelephonyManager mTelephonyManagerForSub2;
    @Mock
    private SubscriptionInfo mSubscriptionInfo;
    @Mock
    private ServiceState mServiceState;
    @Mock
    private CarrierConfigManager mCarrierConfigManager;
    private PersistableBundle mPersistableBundle;
    @Mock
    private EuiccManager mEuiccManager;
    private SubscriptionManager mSubscriptionManager;

    private SimStatusDialogController mController;
    private Context mContext;
    private PackageManager mPackageManager;
    @Mock
    private LifecycleOwner mLifecycleOwner;
    private Lifecycle mLifecycle;
    private AtomicBoolean mEuiccEnabled;
    private AtomicInteger mUpdatePhoneNumberCount;

    private static final int MAX_PHONE_COUNT_DUAL_SIM = 2;

    private static final int TEST_SUB_ID_1 = 1;
    private static final int TEST_SUB_ID_2 = 2;

    @Before
    @UiThreadTest
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mContext = spy(ApplicationProvider.getApplicationContext());
        when(mDialog.getContext()).thenReturn(mContext);
        mPackageManager = spy(mContext.getPackageManager());
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS))
                .thenReturn(true);
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_CALLING))
                .thenReturn(true);
        mLifecycle = new Lifecycle(mLifecycleOwner);

        mTelephonyManager = spy(mContext.getSystemService(TelephonyManager.class));
        mSubscriptionManager = spy(mContext.getSystemService(SubscriptionManager.class));
        doReturn(mSubscriptionInfo).when(mSubscriptionManager)
                .getActiveSubscriptionInfoForSimSlotIndex(anyInt());
        doReturn(TEST_SUB_ID_1).when(mSubscriptionInfo).getSubscriptionId();

        when(mContext.getSystemService(TelephonyManager.class)).thenReturn(mTelephonyManager);
        when(mContext.getSystemService(CarrierConfigManager.class)).thenReturn(
                    mCarrierConfigManager);
        when(mContext.getSystemService(EuiccManager.class)).thenReturn(mEuiccManager);
        when(mContext.getSystemService(SubscriptionManager.class)).thenReturn(mSubscriptionManager);

        doReturn(mTelephonyManagerForSub1).when(mTelephonyManager).createForSubscriptionId(
                TEST_SUB_ID_1);
        doReturn(mTelephonyManagerForSub2).when(mTelephonyManager).createForSubscriptionId(
                TEST_SUB_ID_2);
        doReturn(2).when(mTelephonyManager).getCardIdForDefaultEuicc();
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mTelephonyManager).getVoiceNetworkType();
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mTelephonyManager).getDataNetworkType();

        mUpdatePhoneNumberCount = new AtomicInteger();
        mEuiccEnabled = new AtomicBoolean(false);
        mController = new SimStatusDialogController(mDialog, mLifecycle, 0 /* phone id */) {
            @Override
            public TelephonyManager getTelephonyManager() {
                return mTelephonyManager;
            }

            @Override
            public void updatePhoneNumber() {
                super.updatePhoneNumber();
                mUpdatePhoneNumberCount.incrementAndGet();
            }
        };
        doReturn(mSubscriptionInfo).when(mSubscriptionManager).getActiveSubscriptionInfo(anyInt());

        when(mTelephonyManager.getActiveModemCount()).thenReturn(MAX_PHONE_COUNT_SINGLE_SIM);
        doReturn(new ArrayList<UiccCardInfo>()).when(mTelephonyManager).getUiccCardsInfo();
        doReturn(new HashMap<Integer, Integer>()).when(mTelephonyManager)
                .getLogicalToPhysicalSlotMapping();

        when(mEuiccManager.isEnabled()).thenReturn(false);
        mEuiccEnabled.set(false);
        when(mEuiccManager.createForCardId(anyInt())).thenReturn(mEuiccManager);

        mPersistableBundle = new PersistableBundle();
        when(mCarrierConfigManager.getConfigForSubId(anyInt())).thenReturn(mPersistableBundle);

        doReturn(mServiceState).when(mTelephonyManager).getServiceState();
    }

    @Test
    public void initialize_updateNetworkProviderWithFoobarCarrier_shouldUpdateCarrierWithFoobar() {
        final CharSequence carrierName = "foobar";
        doReturn(carrierName).when(mSubscriptionInfo).getCarrierName();

        mController.initialize();

        verify(mDialog).setText(NETWORK_PROVIDER_VALUE_ID, carrierName);
    }

    @Test
    public void initialize_shouldUpdatePhoneNumber() {
        mController.initialize();

        assertTrue(mUpdatePhoneNumberCount.get() > 0);
    }

    @Test
    public void initialize_updateLatestAreaInfoWithCdmaPhone_shouldRemoveOperatorInfoSetting() {
        when(mTelephonyManager.getPhoneType()).thenReturn(TelephonyManager.PHONE_TYPE_CDMA);

        mController.initialize();

        verify(mDialog).removeSettingFromScreen(OPERATOR_INFO_LABEL_ID);
        verify(mDialog).removeSettingFromScreen(OPERATOR_INFO_VALUE_ID);
    }

    @Test
    public void initialize_updateServiceStateWithInService_shouldUpdateTextToBeCInService() {
        when(mServiceState.getState()).thenReturn(ServiceState.STATE_IN_SERVICE);

        mController.initialize();

        final String inServiceText = ResourcesUtils.getResourcesString(
                mContext, "radioInfo_service_in");
        verify(mDialog).setText(SERVICE_STATE_VALUE_ID, inServiceText);
    }

    @Test
    @Ignore("b/337417520")
    public void initialize_updateServiceStateWithPowerOff_shouldUpdateText() {
        when(mServiceState.getState()).thenReturn(ServiceState.STATE_POWER_OFF);

        mController.initialize();

        final String offServiceText = ResourcesUtils.getResourcesString(
                mContext, "radioInfo_service_off");
        verify(mDialog).setText(SERVICE_STATE_VALUE_ID, offServiceText);
    }

    @Test
    @Ignore("b/337417520")
    public void initialize_updateVoiceDataOutOfService_shouldUpdateSetting() {
        when(mServiceState.getState()).thenReturn(ServiceState.STATE_OUT_OF_SERVICE);
        when(mServiceState.getDataRegistrationState()).thenReturn(
                ServiceState.STATE_OUT_OF_SERVICE);

        mController.initialize();

        final String offServiceText = ResourcesUtils.getResourcesString(
                mContext, "radioInfo_service_out");
        verify(mDialog).setText(SERVICE_STATE_VALUE_ID, offServiceText);
    }

    @Test
    public void initialize_updateVoiceOutOfServiceDataInService_shouldUpdateTextToBeInService() {
        when(mServiceState.getState()).thenReturn(ServiceState.STATE_OUT_OF_SERVICE);
        when(mServiceState.getDataRegistrationState()).thenReturn(ServiceState.STATE_IN_SERVICE);

        mController.initialize();

        final String inServiceText = ResourcesUtils.getResourcesString(
                mContext, "radioInfo_service_in");
        verify(mDialog).setText(SERVICE_STATE_VALUE_ID, inServiceText);
    }

    @Test
    public void initialize_updateVoiceNetworkTypeWithEdge_shouldUpdateSettingToEdge() {
        when(mTelephonyManager.getVoiceNetworkType()).thenReturn(
                TelephonyManager.NETWORK_TYPE_EDGE);

        mController.initialize();

        verify(mDialog).setText(CELL_VOICE_NETWORK_TYPE_VALUE_ID,
                SimStatusDialogController.getNetworkTypeName(TelephonyManager.NETWORK_TYPE_EDGE));
    }

    @Test
    public void initialize_updateDataNetworkTypeWithEdge_shouldUpdateSettingToEdge() {
        when(mTelephonyManager.getDataNetworkType()).thenReturn(
                TelephonyManager.NETWORK_TYPE_EDGE);

        mController.initialize();

        verify(mDialog).setText(CELL_DATA_NETWORK_TYPE_VALUE_ID,
                SimStatusDialogController.getNetworkTypeName(TelephonyManager.NETWORK_TYPE_EDGE));
    }

    @Test
    public void initialize_updateRoamingStatusIsRoaming_shouldSetSettingToRoaming() {
        when(mServiceState.getRoaming()).thenReturn(true);

        mController.initialize();

        final String roamingOnString = ResourcesUtils.getResourcesString(
                mContext, "radioInfo_roaming_in");
        verify(mDialog).setText(ROAMING_INFO_VALUE_ID, roamingOnString);
    }

    @Test
    public void initialize_updateRoamingStatusNotRoaming_shouldSetSettingToRoamingOff() {
        when(mServiceState.getRoaming()).thenReturn(false);

        mController.initialize();

        final String roamingOffString = ResourcesUtils.getResourcesString(
                mContext, "radioInfo_roaming_not");
        verify(mDialog).setText(ROAMING_INFO_VALUE_ID, roamingOffString);
    }

    @Test
    public void initialize_doNotShowIccid_shouldRemoveIccidSetting() {
        mPersistableBundle.putBoolean(
                CarrierConfigManager.KEY_SHOW_ICCID_IN_SIM_STATUS_BOOL, false);

        mController.initialize();

        verify(mDialog).removeSettingFromScreen(ICCID_INFO_LABEL_ID);
        verify(mDialog).removeSettingFromScreen(ICCID_INFO_VALUE_ID);
    }

    @Test
    public void initialize_showSignalStrengthAndIccId_shouldShowSignalStrengthAndIccIdSetting() {
        // getConfigForSubId is nullable, so make sure the default behavior is correct
        when(mCarrierConfigManager.getConfigForSubId(anyInt())).thenReturn(null);

        mController.initialize();

        verify(mDialog).removeSettingFromScreen(ICCID_INFO_LABEL_ID);
        verify(mDialog).removeSettingFromScreen(ICCID_INFO_VALUE_ID);
    }

    @Test
    public void initialize_showIccid_shouldSetIccidToSetting() {
        final String iccid = "12351351231241";
        mPersistableBundle.putBoolean(CarrierConfigManager.KEY_SHOW_ICCID_IN_SIM_STATUS_BOOL, true);
        doReturn(iccid).when(mTelephonyManager).getSimSerialNumber();

        mController.initialize();

        verify(mDialog).setText(ICCID_INFO_VALUE_ID, iccid);
    }

    @Test
    @Ignore
    public void initialize_imsRegistered_shouldSetImsRegistrationStateSummaryToRegisterd() {
        when(mTelephonyManager.isImsRegistered(anyInt())).thenReturn(true);

        mController.initialize();

        verify(mDialog).setText(IMS_REGISTRATION_STATE_VALUE_ID,
                mContext.getString(com.android.settingslib.R.string.ims_reg_status_registered));
    }

    @Test
    @Ignore
    public void initialize_imsNotRegistered_shouldSetImsRegistrationStateSummaryToNotRegisterd() {
        when(mTelephonyManager.isImsRegistered(anyInt())).thenReturn(false);

        mController.initialize();

        verify(mDialog).setText(IMS_REGISTRATION_STATE_VALUE_ID,
                mContext.getString(com.android.settingslib.R.string.ims_reg_status_not_registered));
    }

    @Test
    @Ignore("b/337417520")
    public void initialize_showImsRegistration_shouldShowImsRegistrationStateSetting() {
        mController.initialize();

        verify(mDialog).setSettingVisibility(IMS_REGISTRATION_STATE_VALUE_ID, true);
    }

    @Test
    @Ignore("b/337417520")
    public void initialize_doNotShowImsRegistration_shouldHideImsRegistrationStateSetting() {
        mController.initialize();

        verify(mDialog).setSettingVisibility(IMS_REGISTRATION_STATE_LABEL_ID, false);
        verify(mDialog).setSettingVisibility(IMS_REGISTRATION_STATE_VALUE_ID, false);
    }

    @Test
    public void onSubscriptionsChanged_updateSubInfoToNewSub_testTelephonyCallbackUnregRereg() {
        // sub id changed from 1 to 2
        SubscriptionInfo subInfo2 = mock(SubscriptionInfo.class);
        doReturn(TEST_SUB_ID_2).when(subInfo2).getSubscriptionId();
        doReturn(subInfo2).when(mSubscriptionManager)
                .getActiveSubscriptionInfoForSimSlotIndex(anyInt());
        mController.mOnSubscriptionsChangedListener.onSubscriptionsChanged();
        verify(mTelephonyManagerForSub2).registerTelephonyCallback(any(Executor.class),
                any(TelephonyCallback.class));

        // sub id changed from 2 to 1
        SubscriptionInfo subInfo1 = mock(SubscriptionInfo.class);
        doReturn(TEST_SUB_ID_1).when(subInfo1).getSubscriptionId();
        doReturn(subInfo1).when(mSubscriptionManager)
                .getActiveSubscriptionInfoForSimSlotIndex(anyInt());
        mController.mOnSubscriptionsChangedListener.onSubscriptionsChanged();
        verify(mTelephonyManagerForSub2).unregisterTelephonyCallback(
                mController.mTelephonyCallback);
        verify(mTelephonyManagerForSub1).registerTelephonyCallback(any(Executor.class),
                any(TelephonyCallback.class));
    }

    @Test
    public void onSubscriptionsChanged_updateSubInfoToNull_testTelephonyCallbackUnreg() {
        doReturn(null).when(mSubscriptionManager).getActiveSubscriptionInfoForSimSlotIndex(
                anyInt());
        mController.mOnSubscriptionsChangedListener.onSubscriptionsChanged();
        verify(mTelephonyManagerForSub1).unregisterTelephonyCallback(
                mController.mTelephonyCallback);
    }

    @Test
    public void onSubscriptionsChanged_updateSubInfoToNull_shouldUpdateDataStatusToUnknown() {
        doReturn(null).when(mSubscriptionManager).getActiveSubscriptionInfoForSimSlotIndex(
                anyInt());
        mController.mOnSubscriptionsChangedListener.onSubscriptionsChanged();
        final String unknownText = ResourcesUtils.getResourcesString(mContext, "radioInfo_unknown");
        verify(mDialog).setText(CELLULAR_NETWORK_STATE, unknownText);
    }
}
