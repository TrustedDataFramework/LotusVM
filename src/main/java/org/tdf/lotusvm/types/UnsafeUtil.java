package org.tdf.lotusvm.types;

import sun.misc.Unsafe;

import java.lang.reflect.Field;

public class UnsafeUtil {
    public static final Unsafe UNSAFE = reflectGetUnsafe();
    public static final int MAX_UNSIGNED_SHORT = 0xFFFF;

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
        return i * 8L;
    }
}
