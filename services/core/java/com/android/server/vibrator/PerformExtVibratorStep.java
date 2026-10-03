/*
 * SPDX-FileCopyrightText: The SunOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.server.vibrator;

import android.annotation.NonNull;
import android.os.VibrationEffect;

import java.util.List;

final class PerformExtVibratorStep extends AbstractVibratorStep {
    private final VibratorExtHalWrapper mExtensionVibrator;
    private final VibrationEffect.Composed mFallback;

    PerformExtVibratorStep(VibrationStepConductor conductor, long startTime,
            VibratorExtHalWrapper vibrator, VibrationEffect.Composed fallback) {
        super(conductor, startTime, vibrator, /* pendingVibratorOffDeadline= */ 0);
        mExtensionVibrator = vibrator;
        mFallback = fallback;
    }

    @NonNull
    @Override
    public List<Step> play() {
        int stepId = conductor.nextVibratorCallbackStepId(getVibratorId());
        long duration = mExtensionVibrator.onExtension(getVibration().id, stepId,
                getVibration().extensionEffectId, 0, getVibration().getExtensionAmplitude());
        if (duration <= 0) {
            // Use the segment overload so the fallback cannot select this step again.
            AbstractVibratorStep fallback = conductor.nextVibrateStep(startTime, vibrator,
                    mFallback, 0, mPendingVibratorOffDeadline);
            List<Step> next = fallback.play();
            handleVibratorOnResult(fallback.getVibratorOnDuration());
            return next;
        }
        handleVibratorOnResult(duration);
        getVibration().stats.reportVibratorOn(duration);
        return List.of(new CompleteEffectVibratorStep(conductor, startTime + duration,
                /* cancelled= */ false, vibrator, mPendingVibratorOffDeadline));
    }
}
