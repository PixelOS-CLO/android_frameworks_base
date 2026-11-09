/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.settingslib.spa.widget.ui

import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import com.android.settingslib.spa.framework.theme.surfaceTone
import kotlin.math.roundToInt

@Composable
fun SettingsSlider(
    initValue: Int,
    modifier: Modifier = Modifier,
    valueRange: IntRange = 0..100,
    onValueChange: ((value: Int) -> Unit)? = null,
    onValueChangeFinished: (() -> Unit)? = null,
    showSteps: Boolean = false,
) {
    var sliderPosition by rememberSaveable { mutableStateOf(initValue.toFloat()) }
    var lastHapticTime by remember { mutableLongStateOf(0L) }
    val view = LocalView.current
    Slider(
        value = sliderPosition,
        onValueChange = {
            val previous = sliderPosition.roundToInt()
            sliderPosition = it
            val value = it.roundToInt()
            onValueChange?.invoke(value)
            if (value != previous) {
                val atEdge = value == valueRange.first || value == valueRange.last
                val now = SystemClock.uptimeMillis()
                if (atEdge || now - lastHapticTime >= 25) {
                    if (view.performHapticFeedback(
                        if (atEdge) HapticFeedbackConstants.SEGMENT_TICK
                        else HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
                    )) {
                        lastHapticTime = now
                    }
                }
            }
        },
        modifier = modifier,
        valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
        steps = if (showSteps) (valueRange.count() - 2) else 0,
        onValueChangeFinished = onValueChangeFinished,
        colors = SliderDefaults.colors(
            inactiveTrackColor = MaterialTheme.colorScheme.surfaceTone
        )
    )
}
