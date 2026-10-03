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

package com.android.systemui.keyguard.ui.viewmodel

//noinspection CleanArchitectureDependencyViolation

import android.os.SystemClock
import android.os.VibrationExtInfo
import org.sun.os.CustomVibrationAttributes.VIBRATION_ATTRIBUTES_MISC_SCENES
import vendor.sun.hardware.vibratorExt.Effect.LOCKSCREEN_SHORTCUT
import vendor.sun.hardware.vibratorExt.Effect.UNIFIED_ERROR
import vendor.sun.hardware.vibratorExt.Effect.CLICK
import vendor.sun.hardware.vibratorExt.Effect.DOUBLE_CLICK
import com.android.systemui.Flags
import com.android.systemui.keyguard.ui.binder.KeyguardBottomAreaVibrations
import com.android.systemui.statusbar.VibratorHelper
import com.google.android.msdl.data.model.MSDLToken
import com.google.android.msdl.domain.MSDLPlayer
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

class KeyguardQuickAffordanceHapticViewModel
@AssistedInject
constructor(private val msdlPlayer: MSDLPlayer, private val vibratorHelper: VibratorHelper) {
    var longPressed = false
        private set

    private var activated = false
    private var lastActivationTime = Long.MIN_VALUE

    fun updateActivatedHistory(isActivated: Boolean) {
        if (activated != isActivated) longPressed = false
        activated = isActivated
    }

    fun onQuickAffordanceLongPress(isActivated: Boolean) {
        longPressed = true
        val now = SystemClock.uptimeMillis()
        if (lastActivationTime != Long.MIN_VALUE && now - lastActivationTime < 100L) return
        lastActivationTime = now
        vibratorHelper.vibrateExt(VibrationExtInfo.Builder()
            .setEffectId(LOCKSCREEN_SHORTCUT)
            .setFallbackEffectId(CLICK)
            .setVibrationAttributes(VIBRATION_ATTRIBUTES_MISC_SCENES)
            .build())
    }

    fun onQuickAffordanceClick() {
        vibratorHelper.vibrateExt(VibrationExtInfo.Builder()
            .setEffectId(UNIFIED_ERROR)
            .setFallbackEffectId(DOUBLE_CLICK)
            .setVibrationAttributes(VIBRATION_ATTRIBUTES_MISC_SCENES)
            .build())
    }

    @AssistedFactory
    interface Factory {
        fun create(): KeyguardQuickAffordanceHapticViewModel
    }
}
