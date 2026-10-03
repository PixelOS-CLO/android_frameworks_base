/*
 * SPDX-FileCopyrightText: The SunOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.server.vibrator;

import static org.sun.provider.SettingsExt.System.*;

import android.content.Context;
import android.database.ContentObserver;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.VibrationAttributes;
import android.provider.Settings;
import android.util.Slog;
import android.util.SparseBooleanArray;

import com.android.internal.annotations.VisibleForTesting;

import java.util.NoSuchElementException;

import vendor.sun.hardware.vibratorExt.Effect;
import vendor.sun.hardware.vibratorExt.IVibratorExt;
import vendor.sun.hardware.vibratorExt.LevelRange;
import vendor.sun.hardware.vibratorExt.Style;
import vendor.sun.hardware.vibratorExt.Type;

class VibratorExtController {
    private static final String TAG = "VibratorExtController";
    static final String SERVICE = IVibratorExt.DESCRIPTOR + "/default";
    private static final long RETRY_INTERVAL_MS = 1_000;
    private static final long MAX_HAPTIC_DURATION_MS = 60_000;
    private static final String[] SETTINGS = {
            VIBRATOR_EXT_HAPTIC_STYLE,
            VIBRATOR_EXT_HAPTIC_STRENGTH_LEVEL,
            VIBRATOR_EXT_NOTIFICAITON_STRENGTH_LEVEL,
            VIBRATOR_EXT_ALARM_CALL_STRENGTH_LEVEL
    };

    private final Context mContext;
    private final Handler mHandler;
    private final boolean mEnabled;
    private IVibratorExt mService;
    private IBinder.DeathRecipient mDeathRecipient;
    private final SparseBooleanArray mSupportedEffects = new SparseBooleanArray();
    private boolean mReady;
    private long mNextLookupUptime;
    private volatile EffectCooldown mEffectCooldown;

    private record EffectCooldown(int effectId, long untilUptimeMillis) {}

    VibratorExtController(Context context, Handler handler) {
        this(context, handler, context.getResources().getBoolean(
                com.android.internal.R.bool.config_enableVibratorExt));
    }

    @VisibleForTesting
    VibratorExtController(Context context, Handler handler, boolean enabled) {
        mContext = context;
        mHandler = handler;
        mEnabled = enabled;
    }

    boolean shouldVibrate(VibrationAttributes attributes) {
        if (!mEnabled) return true;
        String setting = switch (attributes.getUsage()) {
            case VibrationAttributes.USAGE_CUSTOM_BACK_GESTURE_DRAG -> CUSTOM_HAPTIC_ON_BACK_GESTURE;
            case VibrationAttributes.USAGE_CUSTOM_FACE_UNLOCK -> CUSTOM_HAPTIC_ON_FACE;
            case VibrationAttributes.USAGE_CUSTOM_FINGERPRINT_UNLOCK -> CUSTOM_HAPTIC_ON_FINGERPRINT;
            case VibrationAttributes.USAGE_CUSTOM_MISC_SCENES -> CUSTOM_HAPTIC_ON_MISC_SCENES;
            case VibrationAttributes.USAGE_CUSTOM_OFF_SCREEN_GESTURE -> CUSTOM_HAPTIC_ON_GESTURE_OFF_SCREEN;
            case VibrationAttributes.USAGE_CUSTOM_QS_TILE -> CUSTOM_HAPTIC_ON_QS_TILE;
            case VibrationAttributes.USAGE_CUSTOM_SLIDER -> CUSTOM_HAPTIC_ON_SLIDER;
            case VibrationAttributes.USAGE_CUSTOM_SWITCH -> CUSTOM_HAPTIC_ON_SWITCH;
            default -> null;
        };
        if (setting == null) return true;
        long identity = Binder.clearCallingIdentity();
        try {
            return readSetting(setting, 1) != 0;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    static VibrationAttributes normalizeAttributes(VibrationAttributes attributes) {
        int usage = attributes.getUsage();
        if (attributes.getUsageClass()
                != VibrationAttributes.USAGE_CLASS_CUSTOM) return attributes;
        int normalized = switch (usage) {
            case VibrationAttributes.USAGE_CUSTOM_FACE_UNLOCK,
                    VibrationAttributes.USAGE_CUSTOM_FINGERPRINT_UNLOCK ->
                    VibrationAttributes.USAGE_COMMUNICATION_REQUEST;
            case VibrationAttributes.USAGE_CUSTOM_OFF_SCREEN_GESTURE ->
                    VibrationAttributes.USAGE_HARDWARE_FEEDBACK;
            case VibrationAttributes.USAGE_CUSTOM_PREVIEW_ALARM_CALL,
                    VibrationAttributes.USAGE_CUSTOM_PREVIEW_NOTIFICATION ->
                    VibrationAttributes.USAGE_ACCESSIBILITY;
            default -> VibrationAttributes.USAGE_TOUCH;
        };
        return new VibrationAttributes.Builder(attributes).setUsage(normalized).build();
    }

    boolean isEnabled() {
        return mEnabled;
    }

    void onSystemReady() {
        if (!mEnabled) return;
        ContentObserver observer = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange) {
                reloadSettings();
            }
        };
        for (String key : SETTINGS) {
            mContext.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(key), false, observer, UserHandle.USER_ALL);
        }
        synchronized (this) {
            mReady = true;
        }
        reloadSettings();
    }

    synchronized void reloadSettings() {
        if (!mEnabled || !mReady) return;
        long identity = Binder.clearCallingIdentity();
        try {
            if (mService == null) {
                getServiceLocked();
            } else {
                applySettingsLocked(mService);
            }
        } catch (RemoteException | RuntimeException e) {
            disconnectLocked(e);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    boolean isInCooldown(int effectId) {
        EffectCooldown cooldown = mEffectCooldown;
        return cooldown != null && cooldown.effectId() == effectId
                && uptimeMillis() < cooldown.untilUptimeMillis();
    }

    synchronized long on(int effectId, long duration, float amplitude) {
        if (!Float.isFinite(amplitude) || amplitude <= 0f
                || amplitude > 1f || duration < 0) {
            return -1;
        }
        IVibratorExt service = getServiceLocked();
        if (service == null) return -1;
        try {
            // DURATION_DEFAULT is resolved by the HAL before capability checking.
            if (effectId != Effect.DURATION_DEFAULT) {
                int index = mSupportedEffects.indexOfKey(effectId);
                boolean supported = index >= 0 ? mSupportedEffects.valueAt(index)
                        : service.isEffectSupported(effectId);
                mSupportedEffects.put(effectId, supported);
                if (!supported) return -1;
            }
            service.setAmplitude(amplitude);
            long result = service.vibratorOn(effectId, duration);
            // Duration effects may legitimately run longer; haptic effects must be bounded.
            if (result <= 0 || result > Integer.MAX_VALUE
                    || (duration == 0 && result > MAX_HAPTIC_DURATION_MS)) {
                service.vibratorOff();
                return -1;
            }
            mEffectCooldown = new EffectCooldown(effectId, uptimeMillis() + result);
            return result;
        } catch (RemoteException | RuntimeException e) {
            try {
                service.vibratorOff();
            } catch (RemoteException | RuntimeException ignored) {
            }
            disconnectLocked(e);
            return -1;
        }
    }

    synchronized void off() {
        if (mService == null) return;
        try {
            mService.vibratorOff();
        } catch (RemoteException | RuntimeException e) {
            disconnectLocked(e);
        }
    }

    synchronized void setAmplitude(float amplitude) {
        if (mService == null || !Float.isFinite(amplitude) || amplitude <= 0f
                || amplitude > 1f) return;
        try {
            mService.setAmplitude(amplitude);
        } catch (RemoteException | RuntimeException e) {
            disconnectLocked(e);
        }
    }

    private IVibratorExt getServiceLocked() {
        if (!mEnabled || !mReady) return null;
        if (mService != null) return mService;
        long now = uptimeMillis();
        if (now < mNextLookupUptime) return null;
        mNextLookupUptime = now + RETRY_INTERVAL_MS;
        IBinder binder = findService();
        if (binder == null) return null;
        IVibratorExt service = IVibratorExt.Stub.asInterface(binder);
        IBinder.DeathRecipient recipient = () -> {
            synchronized (VibratorExtController.this) {
                if (mService != null && mService.asBinder() == binder) {
                    disconnectLocked(null);
                }
            }
        };
        try {
            binder.linkToDeath(recipient, 0);
            mService = service;
            mDeathRecipient = recipient;
            service.initVibrator();
            applySettingsLocked(service);
            return service;
        } catch (RemoteException | RuntimeException e) {
            disconnectLocked(e);
            return null;
        }
    }

    private void applySettingsLocked(IVibratorExt service) throws RemoteException {
        int style = readSetting(VIBRATOR_EXT_HAPTIC_STYLE, Style.CRISP);
        if (style != Style.CRISP && style != Style.GENTLE) style = Style.CRISP;
        if (!service.isHapticStyleSupported(style)) {
            style = style == Style.CRISP ? Style.GENTLE : Style.CRISP;
        }
        if (service.isHapticStyleSupported(style)) service.setHapticStyle(style);
        applyStrengthLocked(service, Type.HAPTIC, VIBRATOR_EXT_HAPTIC_STRENGTH_LEVEL);
        applyStrengthLocked(service, Type.NOTIFICATION,
                VIBRATOR_EXT_NOTIFICAITON_STRENGTH_LEVEL);
        applyStrengthLocked(service, Type.ALARM_CALL, VIBRATOR_EXT_ALARM_CALL_STRENGTH_LEVEL);
    }

    private void applyStrengthLocked(IVibratorExt service, int type, String key)
            throws RemoteException {
        LevelRange range = service.getStrengthLevelRange(type);
        if (range == null || range.maxLevel < 1 || range.defaultLevel < 1
                || range.defaultLevel > range.maxLevel) return;
        int level = Math.max(1, Math.min(range.maxLevel, readSetting(key, range.defaultLevel)));
        service.setStrengthLevel(type, level);
    }

    @VisibleForTesting
    int readSetting(String key, int fallback) {
        return Settings.System.getIntForUser(mContext.getContentResolver(), key, fallback,
                UserHandle.USER_CURRENT);
    }

    private void disconnectLocked(Exception cause) {
        if (mService != null && mDeathRecipient != null) {
            try {
                mService.asBinder().unlinkToDeath(mDeathRecipient, 0);
            } catch (NoSuchElementException ignored) {
            }
        }
        mService = null;
        mEffectCooldown = null;
        mDeathRecipient = null;
        mSupportedEffects.clear();
        mNextLookupUptime = uptimeMillis() + RETRY_INTERVAL_MS;
        if (cause != null) Slog.w(TAG, "Extension unavailable; using platform fallback", cause);
    }

    @VisibleForTesting
    IBinder findService() {
        return ServiceManager.checkService(SERVICE);
    }

    @VisibleForTesting
    long uptimeMillis() {
        return SystemClock.uptimeMillis();
    }
}
