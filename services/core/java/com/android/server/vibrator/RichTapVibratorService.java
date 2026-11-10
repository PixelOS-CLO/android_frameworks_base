/*
 * SPDX-FileCopyrightText: The PixelOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server.vibrator;

import android.annotation.Nullable;
import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.RichTapVibrationEffect;
import android.os.ServiceManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;

import vendor.aac.hardware.richtap.vibrator.IRichtapCallback;
import vendor.aac.hardware.richtap.vibrator.IRichtapVibrator;

/**
 * Plays the effects of the RichTap SDK on the RichTap vibrator HAL.
 *
 * The HAL is either its own service, or an extension of the default vibrator HAL.
 */
final class RichTapVibratorService {
    private static final String TAG = "RichTapVibratorService";

    private static final String RICHTAP_INSTANCE = IRichtapVibrator.DESCRIPTOR + "/default";
    private static final String VIBRATOR_INSTANCE =
            "android.hardware.vibrator.IVibrator/default";

    // HE 2.0 patterns are streamed in packages, the later ones continue the first.
    private static final int HE_FORMAT_VERSION_2 = 0x02;
    private static final int HE_PACKAGE_START_INDEX = 5;

    private final Object mLock = new Object();
    private final boolean mSupported;

    @GuardedBy("mLock")
    private IRichtapVibrator mHal;
    @GuardedBy("mLock")
    private boolean mPlaying;
    @GuardedBy("mLock")
    private int mDynamicScale = -1;

    private final IRichtapCallback mCallback = new IRichtapCallback.Stub() {
        @Override
        public void onCallback(int status) {}

        @Override
        public int getInterfaceVersion() {
            return IRichtapCallback.VERSION;
        }

        @Override
        public String getInterfaceHash() {
            return IRichtapCallback.HASH;
        }
    };

    RichTapVibratorService(Context context) {
        mSupported = context.getResources().getBoolean(
                com.android.internal.R.bool.config_richtapSupported);
    }

    /** Whether the effect is one of the RichTap SDK, which this service plays. */
    boolean isRichTapEffect(@Nullable VibrationEffect effect) {
        return mSupported && (effect instanceof RichTapVibrationEffect.ExtPrebaked
                || effect instanceof RichTapVibrationEffect.Envelope
                || effect instanceof RichTapVibrationEffect.PatternHe
                || effect instanceof RichTapVibrationEffect.PatternHeParameter
                || effect instanceof RichTapVibrationEffect.HapticParameter);
    }

    /**
     * Whether the effect starts new playback, which supersedes other vibrations. Parameter
     * updates and later packages of a streamed pattern continue the current one.
     */
    boolean startsPlayback(VibrationEffect effect) {
        if (effect instanceof RichTapVibrationEffect.PatternHe patternHe) {
            int[] pattern = patternHe.getPatternInfo();
            return pattern.length <= HE_PACKAGE_START_INDEX
                    || pattern[0] != HE_FORMAT_VERSION_2
                    || pattern[HE_PACKAGE_START_INDEX] == 0;
        }
        return effect instanceof RichTapVibrationEffect.ExtPrebaked
                || effect instanceof RichTapVibrationEffect.Envelope;
    }

    /** Plays the effect, at the user intensity for its usage. */
    void perform(VibrationEffect effect, int intensity) {
        synchronized (mLock) {
            IRichtapVibrator hal = getHalLocked();
            if (hal == null) {
                return;
            }
            try {
                applyIntensityLocked(hal, intensity);
                if (effect instanceof RichTapVibrationEffect.ExtPrebaked prebaked) {
                    hal.perform(prebaked.getId(), (byte) prebaked.getScale(), mCallback);
                } else if (effect instanceof RichTapVibrationEffect.Envelope envelope) {
                    performEnvelope(hal, envelope);
                } else if (effect instanceof RichTapVibrationEffect.PatternHe patternHe) {
                    hal.performHe(patternHe.getLooper(), patternHe.getInterval(),
                            patternHe.getAmplitude(), patternHe.getFreq(),
                            patternHe.getPatternInfo(), mCallback);
                } else if (effect instanceof RichTapVibrationEffect.PatternHeParameter param) {
                    hal.performHeParam(param.getInterval(), param.getAmplitude(),
                            param.getFreq(), mCallback);
                } else if (effect instanceof RichTapVibrationEffect.HapticParameter param) {
                    hal.setHapticParam(param.getParam(), param.getLength(), mCallback);
                }
                if (startsPlayback(effect)) {
                    mPlaying = true;
                }
            } catch (RemoteException | RuntimeException e) {
                Slog.e(TAG, "Failed to perform " + effect, e);
            }
        }
    }

    /** Stops the RichTap playback, if any. */
    void stop() {
        synchronized (mLock) {
            if (!mPlaying) {
                return;
            }
            mPlaying = false;
            IRichtapVibrator hal = getHalLocked();
            if (hal == null) {
                return;
            }
            try {
                hal.stop(mCallback);
                hal.off(mCallback);
            } catch (RemoteException | RuntimeException e) {
                Slog.e(TAG, "Failed to stop", e);
            }
        }
    }

    @GuardedBy("mLock")
    private void performEnvelope(IRichtapVibrator hal, RichTapVibrationEffect.Envelope envelope)
            throws RemoteException {
        // Points of time, scale and frequency, interleaved.
        int[] time = envelope.getRelativeTimeArr();
        int[] scale = envelope.getScaleArr();
        int[] freq = envelope.getFreqArr();
        int[] params = new int[time.length * 3];
        for (int i = 0; i < time.length; i++) {
            params[i * 3] = time[i];
            params[i * 3 + 1] = scale[i];
            params[i * 3 + 2] = freq[i];
        }
        int amplitude = envelope.getAmplitude();
        hal.setAmplitude(amplitude == VibrationEffect.DEFAULT_AMPLITUDE ? 255 : amplitude,
                mCallback);
        hal.performEnvelope(params, envelope.isSteepMode(), mCallback);
    }

    @GuardedBy("mLock")
    private void applyIntensityLocked(IRichtapVibrator hal, int intensity)
            throws RemoteException {
        int scale = switch (intensity) {
            case Vibrator.VIBRATION_INTENSITY_LOW -> 60;
            case Vibrator.VIBRATION_INTENSITY_MEDIUM -> 80;
            default -> 100;
        };
        if (scale != mDynamicScale) {
            hal.setDynamicScale(scale, mCallback);
            mDynamicScale = scale;
        }
    }

    @GuardedBy("mLock")
    @Nullable
    private IRichtapVibrator getHalLocked() {
        if (mHal != null) {
            return mHal;
        }
        IBinder binder = null;
        if (ServiceManager.isDeclared(RICHTAP_INSTANCE)) {
            binder = ServiceManager.waitForDeclaredService(RICHTAP_INSTANCE);
        } else {
            IBinder vibrator = ServiceManager.getService(VIBRATOR_INSTANCE);
            try {
                binder = vibrator != null ? vibrator.getExtension() : null;
            } catch (RemoteException e) {
                Slog.e(TAG, "Failed to get the vibrator HAL extension", e);
            }
        }
        if (binder == null) {
            Slog.w(TAG, "RichTap vibrator HAL not found");
            return null;
        }
        try {
            binder.linkToDeath(() -> {
                synchronized (mLock) {
                    mHal = null;
                    mPlaying = false;
                    mDynamicScale = -1;
                }
            }, 0);
        } catch (RemoteException e) {
            return null;
        }
        mHal = IRichtapVibrator.Stub.asInterface(Binder.allowBlocking(binder));
        try {
            mHal.init(mCallback);
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to init the RichTap vibrator HAL", e);
        }
        return mHal;
    }
}
