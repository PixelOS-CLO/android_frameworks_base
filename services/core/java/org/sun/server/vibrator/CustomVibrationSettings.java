/*
 * SPDX-FileCopyrightText: The SunOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.sun.server.vibrator;

import static org.sun.provider.SettingsExt.System.*;

import android.content.Context;
import android.database.ContentObserver;
import android.os.CombinedVibration;
import android.os.Handler;
import android.os.UserHandle;
import android.os.VibrationAttributes;
import android.provider.Settings;

import org.sun.os.VibrationPatternManager;

public final class CustomVibrationSettings {
    private static final CustomVibrationSettings INSTANCE = new CustomVibrationSettings();
    private Context mContext;
    private Handler mHandler;
    private volatile boolean mKeyboardEffect;
    private volatile boolean mForceIme;
    private volatile boolean mDisableInLowPower = true;

    private CustomVibrationSettings() {}

    public static CustomVibrationSettings getInstance() {
        return INSTANCE;
    }

    public void init(Context context, Handler handler) {
        mContext = context;
        mHandler = handler;
    }

    public void onSystemReady() {
        ContentObserver observer = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange) {
                onUserSwitched();
            }
        };
        for (String key : new String[] {IME_KEYBOARD_PRESS_EFFECT, FORCE_ENABLE_IME_HAPTIC,
                LOW_POWER_DISABLE_VIBRATION}) {
            mContext.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(key), false, observer, UserHandle.USER_ALL);
        }
        VibrationEffectAdapter.initEffectMap(VibrationEffectAdapter.SYSTEM_CONFIG_FILE);
        onUserSwitched();
    }

    public void onUserSwitched() {
        mKeyboardEffect = read(IME_KEYBOARD_PRESS_EFFECT, 0);
        mForceIme = read(FORCE_ENABLE_IME_HAPTIC, 0);
        mDisableInLowPower = read(LOW_POWER_DISABLE_VIBRATION, 1);
    }

    private boolean read(String key, int fallback) {
        return Settings.System.getIntForUser(mContext.getContentResolver(), key, fallback,
                UserHandle.USER_CURRENT) != 0;
    }

    public CombinedVibration applyPattern(CombinedVibration effect,
            VibrationAttributes attributes) {
        int type;
        String key;
        switch (attributes.getUsage()) {
            case VibrationAttributes.USAGE_RINGTONE:
                type = VibrationPatternManager.Type.RINGTONE;
                key = VIBRATION_PATTERN_RINGTONE;
                break;
            case VibrationAttributes.USAGE_NOTIFICATION:
                type = VibrationPatternManager.Type.NOTIFICATION;
                key = VIBRATION_PATTERN_NOTIFICATION;
                break;
            default:
                return effect;
        }
        int pattern = Settings.System.getIntForUser(mContext.getContentResolver(), key, -1,
                UserHandle.USER_CURRENT);
        if (pattern < 0) return effect;
        return CombinedVibration.createParallel(
                VibrationPatternManager.getVibrationFromNumber(pattern, type,
                        effect.getDuration() == Long.MAX_VALUE));
    }

    public boolean isKeyboardEffectEnabled() { return mKeyboardEffect; }
    public boolean shouldForceEnableIMEHaptic() { return mForceIme; }
    public boolean isLowPowerDisableVibration() { return mDisableInLowPower; }
}
