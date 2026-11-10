/*
 * SPDX-FileCopyrightText: The PixelOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package android.os;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.res.Resources;

import java.util.Arrays;
import java.util.Objects;

/**
 * Vibration effects of the RichTap SDK.
 *
 * Apps using the SDK find this class by reflection and play its effects through
 * {@link Vibrator}. They are performed by the RichTap vibrator HAL.
 *
 * @hide
 */
public final class RichTapVibrationEffect {
    private static final int PARCEL_TOKEN_EXT_PREBAKED = 501;
    private static final int PARCEL_TOKEN_ENVELOPE = 502;
    private static final int PARCEL_TOKEN_PATTERN_HE = 503;
    private static final int PARCEL_TOKEN_PATTERN_HE_LOOP_PARAMETER = 504;
    private static final int PARCEL_TOKEN_HAPTIC_PARAMETER = 505;

    // Reported like Oplus does, so the SDK takes the same paths as on stock.
    private static final int OPLUS_CLIENT = 0x01 << 16;
    private static final int MAJOR_RICHTAP_VERSION = 0x20 << 8;
    private static final int MINOR_RICHTAP_VERSION = 0x10;
    private static final int VIBRATION_EFFECT_SUPPORT_NO = 2;

    private RichTapVibrationEffect() {}

    /** Returns the client and RichTap version, or 2 when RichTap is not supported. */
    public static int checkIfRichTapSupport() {
        if (!Resources.getSystem().getBoolean(com.android.internal.R.bool.config_richtapSupported)) {
            return VIBRATION_EFFECT_SUPPORT_NO;
        }
        return OPLUS_CLIENT | MAJOR_RICHTAP_VERSION | MINOR_RICHTAP_VERSION;
    }

    @NonNull
    public static VibrationEffect createExtPreBaked(int effectId, int strength) {
        VibrationEffect effect = new ExtPrebaked(effectId, strength);
        effect.validate();
        return effect;
    }

    @NonNull
    public static VibrationEffect createEnvelope(@NonNull int[] relativeTimeArr,
            @NonNull int[] scaleArr, @NonNull int[] freqArr, boolean steepMode, int amplitude) {
        VibrationEffect effect = new Envelope(relativeTimeArr, scaleArr, freqArr, steepMode,
                amplitude);
        effect.validate();
        return effect;
    }

    @NonNull
    public static VibrationEffect createPatternHeParameter(int interval, int amplitude,
            int freq) {
        VibrationEffect effect = new PatternHeParameter(interval, amplitude, freq);
        effect.validate();
        return effect;
    }

    @NonNull
    public static VibrationEffect createHapticParameter(@NonNull int[] param, int length) {
        VibrationEffect effect = new HapticParameter(param, length);
        effect.validate();
        return effect;
    }

    @NonNull
    public static VibrationEffect createPatternHeWithParam(@NonNull int[] patternInfo, int looper,
            int interval, int amplitude, int freq) {
        VibrationEffect effect = new PatternHe(patternInfo, looper, interval, amplitude, freq);
        effect.validate();
        return effect;
    }

    /** Whether the parcel token is one of a RichTap effect. */
    public static boolean isExtendedEffect(int token) {
        return token >= PARCEL_TOKEN_EXT_PREBAKED && token <= PARCEL_TOKEN_HAPTIC_PARAMETER;
    }

    /** Reads a RichTap effect, after its parcel token. */
    @NonNull
    public static VibrationEffect createFromParcel(int token, @NonNull Parcel in) {
        switch (token) {
            case PARCEL_TOKEN_EXT_PREBAKED:
                return new ExtPrebaked(in);
            case PARCEL_TOKEN_ENVELOPE:
                return new Envelope(in);
            case PARCEL_TOKEN_PATTERN_HE:
                return new PatternHe(in);
            case PARCEL_TOKEN_PATTERN_HE_LOOP_PARAMETER:
                return new PatternHeParameter(in);
            case PARCEL_TOKEN_HAPTIC_PARAMETER:
                return new HapticParameter(in);
            default:
                throw new IllegalStateException("Unexpected RichTap effect token " + token);
        }
    }

    /**
     * Base of the RichTap effects. They are performed as is by the RichTap HAL, so
     * the generic transformations of vibration effects leave them unchanged.
     */
    private abstract static class RichTapEffect extends VibrationEffect {
        @Override
        public long[] computeCreateWaveformOffOnTimingsOrNull() {
            return null;
        }

        @Override
        public VibrationEffect cropToLengthOrNull(int length) {
            return null;
        }

        @Override
        public long getDuration() {
            return -1;
        }

        @Override
        public boolean areVibrationFeaturesSupported(@NonNull VibratorInfo vibratorInfo) {
            return true;
        }

        @NonNull
        @Override
        public VibrationEffect resolve(int defaultAmplitude) {
            return this;
        }

        @NonNull
        @Override
        public VibrationEffect applyEffectStrength(int effectStrength) {
            return this;
        }

        @NonNull
        @Override
        public VibrationEffect scale(float scaleFactor) {
            return this;
        }

        @NonNull
        @Override
        public VibrationEffect applyAdaptiveScale(float scaleFactor) {
            return this;
        }

        @NonNull
        @Override
        public VibrationEffect applyRepeatingIndefinitely(boolean wantRepeating,
                int loopDelayMs) {
            return this;
        }

        @Override
        public String toDebugString() {
            return toString();
        }

        @Override
        public int describeContents() {
            return 0;
        }
    }

    /** A prebaked effect of the RichTap effect library. */
    public static final class ExtPrebaked extends RichTapEffect {
        private final int mEffectId;
        private final int mStrength;

        ExtPrebaked(@NonNull Parcel in) {
            this(in.readInt(), in.readInt());
        }

        ExtPrebaked(int effectId, int strength) {
            mEffectId = effectId;
            mStrength = strength;
        }

        public int getId() {
            return mEffectId;
        }

        public int getScale() {
            return mStrength;
        }

        @Override
        public void validate() {
            if (mEffectId < 0) {
                throw new IllegalArgumentException("Unknown effect id " + mEffectId);
            }
            if (mStrength < 1 || mStrength > 100) {
                throw new IllegalArgumentException(
                        "Strength must be between 1 and 100 inclusive (strength=" + mStrength
                                + ")");
            }
        }

        @Override
        public boolean equals(@Nullable Object o) {
            return o instanceof ExtPrebaked other
                    && mEffectId == other.mEffectId && mStrength == other.mStrength;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mEffectId, mStrength);
        }

        @Override
        public String toString() {
            return "ExtPrebaked{id=" + mEffectId + ", strength=" + mStrength + "}";
        }

        @Override
        public void writeToParcel(@NonNull Parcel out, int flags) {
            out.writeInt(PARCEL_TOKEN_EXT_PREBAKED);
            out.writeInt(mEffectId);
            out.writeInt(mStrength);
        }
    }

    /** An effect following up to four points of time, intensity and frequency. */
    public static final class Envelope extends RichTapEffect {
        private final int[] mRelativeTimeArr;
        private final int[] mScaleArr;
        private final int[] mFreqArr;
        private final boolean mSteepMode;
        private final int mAmplitude;

        Envelope(@NonNull Parcel in) {
            this(in.createIntArray(), in.createIntArray(), in.createIntArray(),
                    in.readInt() == 1, in.readInt());
        }

        Envelope(@NonNull int[] relativeTimeArr, @NonNull int[] scaleArr,
                @NonNull int[] freqArr, boolean steepMode, int amplitude) {
            mRelativeTimeArr = Arrays.copyOf(relativeTimeArr, 4);
            mScaleArr = Arrays.copyOf(scaleArr, 4);
            mFreqArr = Arrays.copyOf(freqArr, 4);
            mSteepMode = steepMode;
            mAmplitude = amplitude;
        }

        @NonNull
        public int[] getRelativeTimeArr() {
            return mRelativeTimeArr;
        }

        @NonNull
        public int[] getScaleArr() {
            return mScaleArr;
        }

        @NonNull
        public int[] getFreqArr() {
            return mFreqArr;
        }

        public boolean isSteepMode() {
            return mSteepMode;
        }

        public int getAmplitude() {
            return mAmplitude;
        }

        @Override
        public void validate() {
            for (int i = 0; i < 4; i++) {
                if (mRelativeTimeArr[i] < 0 || mScaleArr[i] < 0 || mFreqArr[i] < 0) {
                    throw new IllegalArgumentException("Envelope values can not be negative");
                }
            }
            if (mAmplitude < -1 || mAmplitude == 0 || mAmplitude > 255) {
                throw new IllegalArgumentException(
                        "Amplitude must either be DEFAULT_AMPLITUDE, or between 1 and 255"
                                + " inclusive (amplitude=" + mAmplitude + ")");
            }
        }

        @Override
        public boolean equals(@Nullable Object o) {
            return o instanceof Envelope other
                    && mAmplitude == other.mAmplitude && mSteepMode == other.mSteepMode
                    && Arrays.equals(mRelativeTimeArr, other.mRelativeTimeArr)
                    && Arrays.equals(mScaleArr, other.mScaleArr)
                    && Arrays.equals(mFreqArr, other.mFreqArr);
        }

        @Override
        public int hashCode() {
            return Objects.hash(Arrays.hashCode(mRelativeTimeArr), Arrays.hashCode(mScaleArr),
                    Arrays.hashCode(mFreqArr), mSteepMode, mAmplitude);
        }

        @Override
        public String toString() {
            return "Envelope{time=" + Arrays.toString(mRelativeTimeArr)
                    + ", scale=" + Arrays.toString(mScaleArr)
                    + ", freq=" + Arrays.toString(mFreqArr)
                    + ", steep=" + mSteepMode + ", amplitude=" + mAmplitude + "}";
        }

        @Override
        public void writeToParcel(@NonNull Parcel out, int flags) {
            out.writeInt(PARCEL_TOKEN_ENVELOPE);
            out.writeIntArray(mRelativeTimeArr);
            out.writeIntArray(mScaleArr);
            out.writeIntArray(mFreqArr);
            out.writeInt(mSteepMode ? 1 : 0);
            out.writeInt(mAmplitude);
        }
    }

    /** An HE pattern, as encoded by the RichTap SDK. */
    public static final class PatternHe extends RichTapEffect {
        private final int[] mPatternInfo;
        private final int mLooper;
        private final int mInterval;
        private final int mAmplitude;
        private final int mFreq;

        PatternHe(@NonNull Parcel in) {
            this(in.createIntArray(), in.readInt(), in.readInt(), in.readInt(), in.readInt());
        }

        PatternHe(@NonNull int[] patternInfo, int looper, int interval, int amplitude,
                int freq) {
            mPatternInfo = patternInfo;
            mLooper = looper;
            mInterval = interval;
            mAmplitude = amplitude;
            mFreq = freq;
        }

        @NonNull
        public int[] getPatternInfo() {
            return mPatternInfo;
        }

        public int getLooper() {
            return mLooper;
        }

        public int getInterval() {
            return mInterval;
        }

        public int getAmplitude() {
            return mAmplitude;
        }

        public int getFreq() {
            return mFreq;
        }

        @Override
        public void validate() {
            if (mPatternInfo == null || mPatternInfo.length == 0) {
                throw new IllegalArgumentException("Pattern can not be empty");
            }
        }

        @Override
        public boolean equals(@Nullable Object o) {
            return o instanceof PatternHe other
                    && mLooper == other.mLooper && mInterval == other.mInterval
                    && mAmplitude == other.mAmplitude && mFreq == other.mFreq
                    && Arrays.equals(mPatternInfo, other.mPatternInfo);
        }

        @Override
        public int hashCode() {
            return Objects.hash(Arrays.hashCode(mPatternInfo), mLooper, mInterval, mAmplitude,
                    mFreq);
        }

        @Override
        public String toString() {
            return "PatternHe{length=" + mPatternInfo.length + ", looper=" + mLooper
                    + ", interval=" + mInterval + ", amplitude=" + mAmplitude
                    + ", freq=" + mFreq + "}";
        }

        @Override
        public void writeToParcel(@NonNull Parcel out, int flags) {
            out.writeInt(PARCEL_TOKEN_PATTERN_HE);
            out.writeIntArray(mPatternInfo);
            out.writeInt(mLooper);
            out.writeInt(mInterval);
            out.writeInt(mAmplitude);
            out.writeInt(mFreq);
        }
    }

    /** Updates the interval, amplitude and frequency of the HE pattern being played. */
    public static final class PatternHeParameter extends RichTapEffect {
        private final int mInterval;
        private final int mAmplitude;
        private final int mFreq;

        PatternHeParameter(@NonNull Parcel in) {
            this(in.readInt(), in.readInt(), in.readInt());
        }

        PatternHeParameter(int interval, int amplitude, int freq) {
            mInterval = interval;
            mAmplitude = amplitude;
            mFreq = freq;
        }

        public int getInterval() {
            return mInterval;
        }

        public int getAmplitude() {
            return mAmplitude;
        }

        public int getFreq() {
            return mFreq;
        }

        @Override
        public void validate() {
            if (mAmplitude < -1 || mAmplitude > 255 || mInterval < -1 || mFreq < -1) {
                throw new IllegalArgumentException("Invalid pattern parameters " + this);
            }
        }

        @Override
        public boolean equals(@Nullable Object o) {
            return o instanceof PatternHeParameter other
                    && mInterval == other.mInterval && mAmplitude == other.mAmplitude
                    && mFreq == other.mFreq;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mInterval, mAmplitude, mFreq);
        }

        @Override
        public String toString() {
            return "PatternHeParameter{interval=" + mInterval + ", amplitude=" + mAmplitude
                    + ", freq=" + mFreq + "}";
        }

        @Override
        public void writeToParcel(@NonNull Parcel out, int flags) {
            out.writeInt(PARCEL_TOKEN_PATTERN_HE_LOOP_PARAMETER);
            out.writeInt(mInterval);
            out.writeInt(mAmplitude);
            out.writeInt(mFreq);
        }
    }

    /** Haptic parameters for the RichTap HAL, such as the DRC mode. */
    public static final class HapticParameter extends RichTapEffect {
        private final int[] mParam;
        private final int mLength;

        HapticParameter(@NonNull Parcel in) {
            this(in.createIntArray(), in.readInt());
        }

        HapticParameter(@NonNull int[] param, int length) {
            mParam = param;
            mLength = length;
        }

        @NonNull
        public int[] getParam() {
            return mParam;
        }

        public int getLength() {
            return mLength;
        }

        @Override
        public void validate() {
            if (mParam == null || mParam.length == 0 || mLength != mParam.length) {
                throw new IllegalArgumentException("Empty or mismatched haptic parameters");
            }
        }

        @Override
        public boolean equals(@Nullable Object o) {
            return o instanceof HapticParameter other
                    && mLength == other.mLength && Arrays.equals(mParam, other.mParam);
        }

        @Override
        public int hashCode() {
            return Objects.hash(Arrays.hashCode(mParam), mLength);
        }

        @Override
        public String toString() {
            return "HapticParameter{param=" + Arrays.toString(mParam) + "}";
        }

        @Override
        public void writeToParcel(@NonNull Parcel out, int flags) {
            out.writeInt(PARCEL_TOKEN_HAPTIC_PARAMETER);
            out.writeIntArray(mParam);
            out.writeInt(mLength);
        }
    }
}
