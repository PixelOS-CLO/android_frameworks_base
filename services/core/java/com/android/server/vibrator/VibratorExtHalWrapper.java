/*
 * SPDX-FileCopyrightText: The SunOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.server.vibrator;

import android.os.Handler;
import android.os.IVibratorStateListener;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.VibratorInfo;
import android.os.vibrator.PrebakedSegment;
import android.os.vibrator.PrimitiveSegment;
import android.os.vibrator.PwlePoint;
import android.util.IndentingPrintWriter;

import org.sun.os.VibrationPatternManager;

import vendor.sun.hardware.vibratorExt.Effect;

final class VibratorExtHalWrapper implements HalVibrator {
    private final HalVibrator mDelegate;
    private final VibratorExtController mExtension;
    private boolean mExtensionActive;
    private float mExtensionAmplitude;
    private boolean mExtensionUsesDuration;
    private final Handler mHandler;
    private final Callbacks mCallbacks;
    private final RemoteCallbackList<IVibratorStateListener> mListeners =
            new RemoteCallbackList<>();
    private Runnable mCompletion;
    private boolean mLastReportedState;


    VibratorExtHalWrapper(HalVibrator delegate, VibratorExtController extension,
            Handler handler, Callbacks callbacks) {
        mDelegate = delegate;
        mExtension = extension;
        mHandler = handler;
        mCallbacks = callbacks;
        mDelegate.registerVibratorStateListener(new IVibratorStateListener.Stub() {
            @Override
            public void onVibrating(boolean vibrating) {
                mHandler.post(() -> notifyState());
            }
        });
    }

    HalVibrator forAttributes(VibrationAttributes attributes) {
        return attributes.isFlagSet(VibrationAttributes.FLAG_USE_PLATFORM_EFFECT)
                ? mDelegate : this;
    }

    synchronized long onExtension(long vibrationId, long stepId, int effectId,
            long requestedDuration, float amplitude) {
        cancelCompletion();
        mDelegate.off();
        mDelegate.setAmplitude(1f);
        long duration = mExtension.on(effectId, requestedDuration,
                requestedDuration > 0 ? 1f : amplitude);
        if (duration <= 0) {
            if (mExtensionActive) mExtension.off();
            mExtensionActive = false;
            return duration;
        }
        mExtensionActive = true;
        mExtensionUsesDuration = requestedDuration > 0;
        mExtensionAmplitude = mExtensionUsesDuration ? amplitude : -1f;
        if (mExtensionUsesDuration) mDelegate.setAmplitude(amplitude);
        notifyState();
        mCompletion = () -> mCallbacks.onVibrationStepComplete(
                getInfo().getId(), vibrationId, stepId);
        if (!mHandler.postDelayed(mCompletion, duration)) {
            off();
            return -1;
        }
        return duration;
    }

    private void cancelCompletion() {
        if (mCompletion != null) {
            mHandler.removeCallbacks(mCompletion);
            mCompletion = null;
        }
    }

    synchronized long onDuration(long vibrationId, long stepId, long milliseconds,
            float amplitude, int usage, int requestedEffect) {
        int effectId = switch (usage) {
            case VibrationAttributes.USAGE_ALARM, VibrationAttributes.USAGE_RINGTONE ->
                    Effect.DURATION_ALARM_CALL;
            case VibrationAttributes.USAGE_NOTIFICATION -> Effect.DURATION_NOTIFICATION;
            default -> Effect.DURATION_DEFAULT;
        };
        if (requestedEffect >= Effect.DURATION_DEFAULT) effectId = requestedEffect;
        if (VibrationPatternManager.isRampDownEffect(effectId, milliseconds)) {
            effectId = Effect.RAMP_DOWN;
        }
        long duration = onExtension(vibrationId, stepId, effectId, milliseconds, amplitude);
        return duration > 0 ? duration : mDelegate.on(vibrationId, stepId, milliseconds);
    }

    @Override
    public long on(long vibrationId, long stepId, long milliseconds) {
        return mDelegate.on(vibrationId, stepId, milliseconds);
    }

    @Override
    public synchronized long on(long vibrationId, long stepId, PrebakedSegment effect) {
        float amplitude = switch (effect.getEffectStrength()) {
            case VibrationEffect.EFFECT_STRENGTH_LIGHT -> 0.5f;
            case VibrationEffect.EFFECT_STRENGTH_STRONG -> 1f;
            default -> 0.75f;
        };
        long duration = onExtension(vibrationId, stepId, effect.getEffectId(), 0, amplitude);
        return duration > 0 ? duration : mDelegate.on(vibrationId, stepId, effect);
    }

    @Override
    public long on(long vibrationId, long stepId, VibrationEffect.VendorEffect effect) {
        return mDelegate.on(vibrationId, stepId, effect);
    }

    @Override
    public long on(long vibrationId, long stepId, PrimitiveSegment[] primitives) {
        return mDelegate.on(vibrationId, stepId, primitives);
    }

    @Override
    public long on(long vibrationId, long stepId, PwlePoint[] points) {
        return mDelegate.on(vibrationId, stepId, points);
    }

    @Override
    public synchronized boolean off() {
        cancelCompletion();
        if (mExtensionActive) mExtension.off();
        mExtensionActive = false;
        mExtensionAmplitude = 0f;
        boolean result = mDelegate.off();
        notifyState();
        return result;
    }

    @Override
    public synchronized boolean setAmplitude(float amplitude) {
        if (mExtensionActive) {
            if (mExtensionUsesDuration) {
                mExtensionAmplitude = amplitude;
                return mDelegate.setAmplitude(amplitude);
            }
            return false;
        }
        return mDelegate.setAmplitude(amplitude);
    }

    @Override
    public synchronized boolean setExternalControl(boolean enabled) {
        if (enabled && mExtensionActive) {
            off();
        }
        return mDelegate.setExternalControl(enabled);
    }

    @Override
    public void init(Callbacks callbacks) {
        mDelegate.init(callbacks);
    }

    @Override
    public void onSystemReady() {
        mDelegate.onSystemReady();
    }

    @Override
    public VibratorInfo getInfo() {
        return mDelegate.getInfo();
    }

    @Override
    public synchronized boolean isVibrating() {
        return mExtensionActive || mDelegate.isVibrating();
    }

    @Override
    public synchronized boolean registerVibratorStateListener(IVibratorStateListener listener) {
        if (!mListeners.register(listener)) return false;
        try {
            listener.onVibrating(isVibrating());
        } catch (RemoteException e) {
            mListeners.unregister(listener);
            return false;
        }
        return true;
    }

    @Override
    public boolean unregisterVibratorStateListener(IVibratorStateListener listener) {
        return mListeners.unregister(listener);
    }

    @Override
    public synchronized float getCurrentAmplitude() {
        return mExtensionActive ? mExtensionAmplitude : mDelegate.getCurrentAmplitude();
    }

    private synchronized void notifyState() {
        boolean vibrating = isVibrating();
        if (mLastReportedState == vibrating) return;
        mLastReportedState = vibrating;
        int count = mListeners.beginBroadcast();
        try {
            for (int i = 0; i < count; i++) {
                try {
                    mListeners.getBroadcastItem(i).onVibrating(vibrating);
                } catch (RemoteException ignored) {
                }
            }
        } finally {
            mListeners.finishBroadcast();
        }
    }

    @Override
    public boolean setAlwaysOn(int id, PrebakedSegment effect) {
        return mDelegate.setAlwaysOn(id, effect);
    }

    @Override
    public void dump(IndentingPrintWriter pw) {
        pw.println("vibratorExt wrapper enabled");
        mDelegate.dump(pw);
    }
}
