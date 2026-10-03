/*
 * SPDX-FileCopyrightText: The SunOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.server.vibrator;

import android.os.CombinedVibration;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.vibrator.PrimitiveSegment;
import android.view.HapticFeedbackConstants;

import vendor.sun.hardware.vibratorExt.Effect;

final class VibratorExtEffectMapper {
    static final int NONE = -1;

    private VibratorExtEffectMapper() {}

    static int fromLauncherScroll(String packageName, VibrationAttributes attributes,
            CombinedVibration effect) {
        if (!"com.google.android.apps.nexuslauncher".equals(packageName)
                || attributes.getUsage() != VibrationAttributes.USAGE_TOUCH
                || !(effect instanceof CombinedVibration.Mono mono)
                || !(mono.getEffect() instanceof VibrationEffect.Composed composed)
                || composed.getRepeatIndex() >= 0
                || composed.getSegments().size() != 1
                || !(composed.getSegments().get(0) instanceof PrimitiveSegment primitive)
                || primitive.getPrimitiveId() != VibrationEffect.Composition.PRIMITIVE_LOW_TICK
                || primitive.getDelay() != 0
                || Float.compare(primitive.getScale(), 0.6f) != 0) {
            return NONE;
        }
        return Effect.TICK;
    }

    static boolean isHapticEffect(int effect) {
        return (effect >= Effect.CLICK && effect <= Effect.HEAVY_CLICK)
                || (effect >= Effect.DURATION_STRENGTH_LEVEL1
                        && effect <= Effect.DURATION_STRENGTH_LEVEL10)
                || (effect > Effect.CUSTOM_EFFECT_START && effect < Effect.CUSTOM_EFFECT_END);
    }

    static int fromHapticFeedback(int constant) {
        return switch (constant) {
            case HapticFeedbackConstants.LONG_PRESS -> Effect.HEAVY_CLICK;
            case HapticFeedbackConstants.VIRTUAL_KEY_RELEASE,
                    HapticFeedbackConstants.KEYBOARD_RELEASE,
                    HapticFeedbackConstants.TEXT_HANDLE_MOVE -> Effect.TICK;
            case HapticFeedbackConstants.VIRTUAL_KEY,
                    HapticFeedbackConstants.CONTEXT_CLICK -> Effect.BUTTON_CLICK;
            case HapticFeedbackConstants.KEYBOARD_PRESS -> Effect.KEYBOARD_PRESS;
            case HapticFeedbackConstants.TOGGLE_ON,
                    HapticFeedbackConstants.TOGGLE_OFF -> Effect.SWITCH_TOGGLE;
            case HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE -> Effect.BACK_GESTURE;
            case HapticFeedbackConstants.SEGMENT_TICK,
                    HapticFeedbackConstants.SEGMENT_FREQUENT_TICK -> Effect.SLIDER_STEP;
            case HapticFeedbackConstants.SCROLL_LIMIT -> Effect.SLIDER_EDGE;
            case HapticFeedbackConstants.CLOCK_TICK,
                    HapticFeedbackConstants.SCROLL_ITEM_FOCUS -> Effect.INDEXABLE_WIDGET;
            case HapticFeedbackConstants.CONFIRM,
                    HapticFeedbackConstants.BIOMETRIC_CONFIRM -> Effect.UNIFIED_SUCCESS;
            case HapticFeedbackConstants.REJECT,
                    HapticFeedbackConstants.BIOMETRIC_REJECT -> Effect.UNIFIED_ERROR;
            default -> NONE;
        };
    }
}
