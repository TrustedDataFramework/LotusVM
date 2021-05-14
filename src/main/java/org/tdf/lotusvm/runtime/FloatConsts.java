package org.tdf.lotusvm.runtime;

public class FloatConsts {
    public static final int NAN = Float.floatToIntBits(Float.NaN);

    /**
     * Bias used in representing a {@code float} exponent.
     */
    public static final int     EXP_BIAS        = 127;

    /**
     * Bit mask to isolate the sign bit of a {@code float}.
     */
    public static final int     SIGN_BIT_MASK   = 0x80000000;

    /**
     * Bit mask to isolate the exponent field of a
     * {@code float}.
     */
    public static final int     EXP_BIT_MASK    = 0x7F800000;

    /**
     * Bit mask to isolate the significand field of a
     * {@code float}.
     */
    public static final int   SIGNIF_BIT_MASK = 0x007FFFFF;

    public static String ieee754(int i) {
        String s = Integer.toBinaryString(i);
        while (s.length() < 32) {
            s = "0" + s;
        }
        return s;
    }

    public static int fadd(int bits) {
        return Float.floatToRawIntBits(
            Float.intBitsToFloat(bits) + 1f
        );
    }

    public static int fsub(int bits) {
        return Float.floatToRawIntBits(
            Float.intBitsToFloat(bits) - 1f
        );
    }
}
