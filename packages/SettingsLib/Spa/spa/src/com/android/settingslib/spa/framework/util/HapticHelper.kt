/*
 * SPDX-FileCopyrightText: The PixelOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settingslib.spa.framework.util

import android.view.HapticFeedbackConstants
import android.view.View

/** Wraps [onSwitch] to play toggle haptic feedback when the user flips a switch. */
fun wrapOnSwitchWithHaptic(
    view: View,
    onSwitch: ((checked: Boolean) -> Unit)?,
): ((checked: Boolean) -> Unit)? {
    if (onSwitch == null) return null
    return { checked ->
        view.performHapticFeedback(
            if (checked) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF
        )
        onSwitch(checked)
    }
}
