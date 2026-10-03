/*
 * SPDX-FileCopyrightText: The SunOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.sun.os;

import static org.sun.provider.SettingsExt.System.VIBRATOR_EXT_ALARM_CALL_STRENGTH_LEVEL;
import static org.sun.provider.SettingsExt.System.VIBRATOR_EXT_HAPTIC_STRENGTH_LEVEL;
import static org.sun.provider.SettingsExt.System.VIBRATOR_EXT_NOTIFICAITON_STRENGTH_LEVEL;

import android.content.Context;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.util.ArrayMap;
import android.util.Slog;

import com.android.internal.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import vendor.sun.hardware.vibratorExt.IVibratorExt;
import vendor.sun.hardware.vibratorExt.LevelRange;
import vendor.sun.hardware.vibratorExt.Style;
import vendor.sun.hardware.vibratorExt.Type;

/** @hide */
public final class VibratorExtManager {

    private static final String TAG = "VibratorExtManager";

    private static final String SERVICE_NAME = "vendor.sun.hardware.vibratorExt.IVibratorExt/default";

    // Whether the device supports RichTap
    public static final boolean RICHTAP_SUPPORT =
            SystemProperties.getBoolean("vendor.sun.feature.vibrator.richtap", false);
    // Whether to use richtap for aosp effects and duration vibration
    public static final boolean RICHTAP_TAKEOVER_CTL = RICHTAP_SUPPORT &&
            SystemProperties.getBoolean("vendor.sun.feature.vibrator.richtap_effect", false);

    private IVibratorExt mService;

    private static final Set<Integer> sAllVibrationTypes = Set.of(
        Type.ALARM_CALL,
        Type.HAPTIC,
        Type.NOTIFICATION
    );

    private static final List<Integer> sAllHapticStyles = List.of(
        Style.CRISP,
        Style.GENTLE
    );

    private final ArrayList<Integer> mValidVibrationTypes;
    private final ArrayList<Integer> mValidHapticStyles;
    private final ArrayMap<Integer, LevelRange> mLevelRanges;
    private final ArrayMap<Integer, Integer> mHapticStyleSummary;

    private static class InstanceHolder {
        private static final VibratorExtManager INSTANCE = new VibratorExtManager();
    }

    public static VibratorExtManager getInstance() {
        return InstanceHolder.INSTANCE;
    }

    private VibratorExtManager() {
        mValidVibrationTypes = new ArrayList<>();
        mValidHapticStyles = new ArrayList<>();
        mLevelRanges = new ArrayMap<>();
        mHapticStyleSummary = new ArrayMap<>();

        mHapticStyleSummary.put(Style.CRISP, R.string.haptic_style_crisp);
        mHapticStyleSummary.put(Style.GENTLE, R.string.haptic_style_gentle);

    }

    private boolean connect() {
        if (mService != null && mService.asBinder().isBinderAlive()) return true;
        mService = IVibratorExt.Stub.asInterface(ServiceManager.checkService(SERVICE_NAME));
        mValidVibrationTypes.clear();
        mValidHapticStyles.clear();
        mLevelRanges.clear();
        if (mService != null) {
            for (int type : sAllVibrationTypes) {
                LevelRange range = null;
                try {
                    range = mService.getStrengthLevelRange(type);
                } catch (RemoteException e) {
                    Slog.e(TAG, "Failed to get vibrator strength level range", e);
                }
                if (isStrengthLevelRangeLegal(range)) {
                    mValidVibrationTypes.add(type);
                    mLevelRanges.put(type, range);
                }
            }

            for (int style : sAllHapticStyles) {
                boolean isSupported = false;
                try {
                    isSupported = mService.isHapticStyleSupported(style);
                } catch (RemoteException e) {
                    Slog.e(TAG, "Failed to check if haptic style is supported", e);
                }
                if (isSupported) {
                    mValidHapticStyles.add(style);
                }
            }
        }
        return mService != null;
    }

    public synchronized boolean isSupported() {
        return connect();
    }

    public synchronized void initVibrator() {
        if (!connect()) {
            return;
        }
        try {
            mService.initVibrator();
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to calibrate vibrator", e);
        }
    }

    public synchronized long vibratorOn(int effectId, long duration) {
        if (!connect()) {
            return -1;
        }
        try {
            return mService.vibratorOn(effectId, duration);
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to turn on vibrator for effectId: " + effectId +
                    ", duration: " + duration, e);
        }
        return -1;
    }

    public synchronized void vibratorOff() {
        if (!connect()) {
            return;
        }
        try {
            mService.vibratorOff();
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to turn off vibrator", e);
        }
    }

    public synchronized String vibrationTypeToSettings(int type) {
        switch (type) {
            case Type.ALARM_CALL:
                return VIBRATOR_EXT_ALARM_CALL_STRENGTH_LEVEL;
            case Type.HAPTIC:
                return VIBRATOR_EXT_HAPTIC_STRENGTH_LEVEL;
            case Type.NOTIFICATION:
                return VIBRATOR_EXT_NOTIFICAITON_STRENGTH_LEVEL;
            default:
                break;
        }
        return "";
    }

    public synchronized ArrayList<Integer> getValidVibrationTypes() {
        connect();
        return new ArrayList<>(mValidVibrationTypes);
    }

    public synchronized ArrayList<Integer> getValidHapticStyles() {
        connect();
        return new ArrayList<>(mValidHapticStyles);
    }

    public synchronized String getHapticStyleSummary(Context context, int style) {
        connect();
        if (!mValidHapticStyles.contains(style)) {
            Slog.e(TAG, "getHapticStyleSummary, invalid haptic style: " + style);
            return "";
        }
        return context.getString(mHapticStyleSummary.get(style));
    }

    public synchronized void setAmplitude(float amplitude) {
        if (!connect()) {
            return;
        }
        if (!Float.isFinite(amplitude) || amplitude <= 0f || amplitude > 1f) {
            Slog.w(TAG, "Invalid amplitude: " + amplitude + ", fallback to default amplitude");
            amplitude = 1f;
        }
        try {
            mService.setAmplitude(amplitude);
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to set amplitude", e);
        }
    }

    public synchronized void setHapticStyle(int style) {
        setHapticStyle(style, null);
    }

    public synchronized void setHapticStyle(int style, HapticStyleChangedCallback callback) {
        if (!connect()) {
            return;
        }
        if (!mValidHapticStyles.contains(style)) {
            Slog.e(TAG, "setHapticStyle, invalid haptic style: " + style);
            return;
        }
        try {
            mService.setHapticStyle(style);
            if (callback != null) {
                callback.onHapticStyleChanged();
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to set haptic style", e);
        }
    }

    public synchronized LevelRange getStrengthLevelRange(int type) {
        connect();
        return mLevelRanges.getOrDefault(type, null);
    }

    public synchronized void setStrengthLevel(int type, int level) {
        setStrengthLevel(type, level, null);
    }

    public synchronized void setStrengthLevel(int type, int level, StrengthLevelChangedCallback callback) {
        if (!connect()) {
            return;
        }
        if (!mValidVibrationTypes.contains(type)) {
            return;
        }
        if (!isStrengthLevelLegal(type, level)) {
            Slog.e(TAG, "Unable to set strength level: Strength level is illegal");
            return;
        }
        try {
            mService.setStrengthLevel(type, level);
            if (callback != null) {
                callback.onStrengthLevelChanged();
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to set vibrator strength level to " + level, e);
        }
    }

    public synchronized boolean isEffectSupported(int effectId) {
        if (!connect()) {
            return false;
        }
        try {
            return mService.isEffectSupported(effectId);
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to get effect supported status", e);
        }
        return false;
    }

    private boolean isStrengthLevelRangeLegal(LevelRange range) {
        if (range == null) {
            return false;
        }
        return range.maxLevel >= 1 && range.defaultLevel >= 1
                && range.defaultLevel <= range.maxLevel;
    }

    private boolean isStrengthLevelLegal(int type, int level) {
        final LevelRange range = getStrengthLevelRange(type);
        if (range == null) {
            return false;
        }
        return level >= 1 && level <= range.maxLevel;
    }

    public interface StrengthLevelChangedCallback {
        void onStrengthLevelChanged();
    }

    public interface HapticStyleChangedCallback {
        void onHapticStyleChanged();
    }
}
