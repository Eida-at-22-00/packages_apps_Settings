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

package com.android.settings.service

import android.content.Context
import android.os.Bundle
import android.service.settings.preferences.GetValueRequest
import android.service.settings.preferences.GetValueResult
import android.service.settings.preferences.MetadataResult
import android.service.settings.preferences.SetValueRequest
import android.service.settings.preferences.SetValueResult
import android.service.settings.preferences.SettingsPreferenceMetadata
import android.service.settings.preferences.SettingsPreferenceValue
import com.android.settingslib.graph.PreferenceGetterErrorCode
import com.android.settingslib.graph.PreferenceGetterFlags
import com.android.settingslib.graph.PreferenceGetterRequest
import com.android.settingslib.graph.PreferenceGetterResponse
import com.android.settingslib.graph.PreferenceSetterRequest
import com.android.settingslib.graph.PreferenceSetterResult
import com.android.settingslib.graph.getAllPermissions
import com.android.settingslib.graph.getText
import com.android.settingslib.graph.preferenceValueProto
import com.android.settingslib.graph.proto.PreferenceGraphProto
import com.android.settingslib.graph.proto.PreferenceOrGroupProto
import com.android.settingslib.graph.proto.PreferenceProto
import com.android.settingslib.graph.proto.PreferenceValueProto
import com.android.settingslib.graph.toBundle
import com.android.settingslib.graph.toIntent
import com.android.settingslib.metadata.PreferenceCoordinate
import com.android.settingslib.metadata.ReadWritePermit
import com.android.settingslib.metadata.SensitivityLevel

/** Transform Catalyst Graph result to Framework GET METADATA result */
fun transformCatalystGetMetadataResponse(
    context: Context,
    graph: PreferenceGraphProto
): MetadataResult {
    val preferences = mutableSetOf<PreferenceWithScreen>()
    // recursive function to visit all nodes in preference group
    fun traverseGroupOrPref(
        screenKey: String,
        groupOrPref: PreferenceOrGroupProto,
    ) {
        when (groupOrPref.kindCase) {
            PreferenceOrGroupProto.KindCase.PREFERENCE ->
                preferences.add(
                    PreferenceWithScreen(screenKey, groupOrPref.preference)
                )
            PreferenceOrGroupProto.KindCase.GROUP -> {
                for (child in groupOrPref.group.preferencesList) {
                    traverseGroupOrPref(screenKey, child)
                }
            }
            else -> {}
        }
    }
    // traverse all screens and all preferences on screen
    for ((screenKey, screen) in graph.screensMap) {
        for (groupOrPref in screen.root.preferencesList) {
            traverseGroupOrPref(screenKey, groupOrPref)
        }
        for (parameterizedScreen in screen.parameterizedScreensList) {
            val args = parameterizedScreen.args.toBundle()
            // TODO: support parameterized screen with non empty arguments
            if (!args.isEmpty) continue
            for (groupOrPref in parameterizedScreen.screen.root.preferencesList) {
                traverseGroupOrPref(screenKey, groupOrPref)
            }
        }
    }

    return if (preferences.isNotEmpty()) {
        MetadataResult.Builder(MetadataResult.RESULT_OK)
            .setMetadataList(
                preferences.map {
                    it.preference.toMetadata(context, it.screenKey)
                }
            )
            .build()
    } else {
        MetadataResult.Builder(MetadataResult.RESULT_UNSUPPORTED).build()
    }
}

/** Translate Framework GET VALUE request to Catalyst GET VALUE request */
fun transformFrameworkGetValueRequest(
    request: GetValueRequest,
    flags: Int = PreferenceGetterFlags.ALL
): PreferenceGetterRequest {
    val coord = PreferenceCoordinate(request.screenKey, request.preferenceKey)
    return PreferenceGetterRequest(
        arrayOf(coord),
        flags
    )
}

/** Translate Catalyst GET VALUE result to Framework GET VALUE result */
fun transformCatalystGetValueResponse(
    context: Context, request: GetValueRequest, response: PreferenceGetterResponse
): GetValueResult? {
    val coord = PreferenceCoordinate(request.screenKey, request.preferenceKey)
    val errorResponse = response.errors[coord]
    val valueResponse = response.preferences[coord]
    when {
        errorResponse != null -> {
            val errorCode = when (errorResponse) {
                PreferenceGetterErrorCode.NOT_FOUND -> GetValueResult.RESULT_UNSUPPORTED
                PreferenceGetterErrorCode.DISALLOW -> GetValueResult.RESULT_DISALLOW
                else -> GetValueResult.RESULT_INTERNAL_ERROR
            }
            return GetValueResult.Builder(errorCode).build()
        }
        valueResponse != null -> {
            val metadata = valueResponse.toMetadata(context, coord.screenKey)
            val value = valueResponse.value.toSettingsPreferenceValue()
            return when (value) {
                null -> GetValueResult.Builder(GetValueResult.RESULT_UNSUPPORTED)
                else -> GetValueResult.Builder(GetValueResult.RESULT_OK).setValue(value)
            }.setMetadata(metadata).build()
        }
        else -> return null
    }
}

private fun PreferenceValueProto.toSettingsPreferenceValue(): SettingsPreferenceValue? =
    when (valueCase.number) {
        PreferenceValueProto.BOOLEAN_VALUE_FIELD_NUMBER -> {
            SettingsPreferenceValue.Builder(
                SettingsPreferenceValue.TYPE_BOOLEAN
            ).setBooleanValue(booleanValue)
        }
        PreferenceValueProto.INT_VALUE_FIELD_NUMBER -> {
            SettingsPreferenceValue.Builder(
                SettingsPreferenceValue.TYPE_INT
            ).setIntValue(intValue)
        }
        else -> null
    }?.build()

/** Translate Framework SET VALUE request to Catalyst SET VALUE request */
fun transformFrameworkSetValueRequest(request: SetValueRequest): PreferenceSetterRequest? {
    val valueProto = when (request.preferenceValue.type) {
        SettingsPreferenceValue.TYPE_BOOLEAN -> preferenceValueProto {
            booleanValue = request.preferenceValue.booleanValue
        }
        SettingsPreferenceValue.TYPE_INT -> preferenceValueProto {
            intValue = request.preferenceValue.intValue
        }
        else -> return null
    }
    // TODO: support parameterized screen
    return PreferenceSetterRequest(request.screenKey, null, request.preferenceKey, valueProto)
}

/** Translate Catalyst SET VALUE result to Framework SET VALUE result */
fun transformCatalystSetValueResponse(@PreferenceSetterResult response: Int): SetValueResult {
   val resultCode = when (response) {
        PreferenceSetterResult.OK -> SetValueResult.RESULT_OK
        PreferenceSetterResult.UNAVAILABLE -> SetValueResult.RESULT_UNAVAILABLE
        PreferenceSetterResult.DISABLED -> SetValueResult.RESULT_DISABLED
        PreferenceSetterResult.UNSUPPORTED -> SetValueResult.RESULT_UNSUPPORTED
        PreferenceSetterResult.DISALLOW -> SetValueResult.RESULT_DISALLOW
        PreferenceSetterResult.REQUIRE_APP_PERMISSION ->
            SetValueResult.RESULT_REQUIRE_APP_PERMISSION
        PreferenceSetterResult.REQUIRE_USER_AGREEMENT -> SetValueResult.RESULT_REQUIRE_USER_CONSENT
        PreferenceSetterResult.RESTRICTED -> SetValueResult.RESULT_RESTRICTED
       PreferenceSetterResult.INVALID_REQUEST -> SetValueResult.RESULT_INVALID_REQUEST
        else -> SetValueResult.RESULT_INTERNAL_ERROR
    }
    return SetValueResult.Builder(resultCode).build()
}

private data class PreferenceWithScreen(
    val screenKey: String,
    val preference: PreferenceProto,
)

private const val KEY_INT_RANGE = "key_int_range"
private const val KEY_MIN = "key_min"
private const val KEY_MAX = "key_max"
private const val KEY_STEP = "key_step"
private const val KEY_TAGS = "tags"

private fun PreferenceProto.toMetadata(
    context: Context,
    screenKey: String
): SettingsPreferenceMetadata {
    val sensitivity = when (sensitivityLevel) {
        SensitivityLevel.NO_SENSITIVITY -> SettingsPreferenceMetadata.NO_SENSITIVITY
        SensitivityLevel.LOW_SENSITIVITY -> SettingsPreferenceMetadata.EXPECT_POST_CONFIRMATION
        SensitivityLevel.MEDIUM_SENSITIVITY -> SettingsPreferenceMetadata.DEEPLINK_ONLY
        SensitivityLevel.HIGH_SENSITIVITY -> SettingsPreferenceMetadata.DEEPLINK_ONLY
        else -> SettingsPreferenceMetadata.NO_DIRECT_ACCESS
    }
    val extras = Bundle()
    if (valueDescriptor.hasRangeValue()
        && valueDescriptor.rangeValue.hasMin()
        && valueDescriptor.rangeValue.hasMax()) {
        val intRange = Bundle()
        intRange.putInt(KEY_MIN, valueDescriptor.rangeValue.min)
        intRange.putInt(KEY_MAX, valueDescriptor.rangeValue.max)
        if (valueDescriptor.rangeValue.hasStep()) {
            intRange.putInt(KEY_STEP, valueDescriptor.rangeValue.step)
        }
        extras.putBundle(KEY_INT_RANGE, intRange)
    }
    if (tagsCount > 0) extras.putStringArray(KEY_TAGS, tagsList.toTypedArray())
    val writePermit = ReadWritePermit.getWritePermit(readWritePermit)
    return SettingsPreferenceMetadata.Builder(screenKey, key)
        .setTitle(title.getText(context))
        .setSummary(summary.getText(context))
        .setEnabled(enabled)
        .setAvailable(available)
        .setRestricted(restricted)
        .setWritable(persistent && writePermit == ReadWritePermit.ALLOW)
        .setLaunchIntent(launchIntent.toIntent())
        .setWriteSensitivity(sensitivity)
        // Returns all the permissions that are used, some of which are exclusive (e.g. p1 or p2)
        .setReadPermissions(readPermissions.getAllPermissions())
        .setWritePermissions(writePermissions.getAllPermissions())
        .setExtras(extras)
        .build()
}
