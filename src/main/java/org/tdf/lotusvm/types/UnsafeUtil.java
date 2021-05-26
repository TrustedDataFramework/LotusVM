package org.tdf.lotusvm.types;

import sun.misc.Unsafe;

import java.lang.reflect.Field;

public class UnsafeUtil {
    public static final Unsafe UNSAFE = reflectGetUnsafe();

    private static Unsafe reflectGetUnsafe() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe) field.get(null);
        } catch (Exception e) {
            throw new RuntimeException("access unsafe failed");
        }
    }

    public static long fastMul8(int i) {
        if ((i & 0xe0000000) != 0)
            throw new RuntimeException("multiply overflow");
        return Integer.toUnsignedLong(i) << 3;
    }
}
