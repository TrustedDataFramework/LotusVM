package org.tdf.lotusvm.common;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * little endian encoding helpers
 */
public class LittleEndian {

    public static short decodeInt16(byte[] data) {
        return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                .getShort();
    }

    public static byte[] encodeInt16(short val) {
        return ByteBuffer.allocate(Short.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putShort(val).array();
    }

    public static int decodeInt32(byte[] data) {
        return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                .getInt();
    }

    public static byte[] encodeInt32(int val) {
        return ByteBuffer.allocate(Integer.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(val)
                .array();
    }

    // big-endian encoding
    public static byte[] encodeInt64(long value) {
        return ByteBuffer.allocate(Long.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putLong(value).array();
    }

    public static long decodeInt64(byte[] data) {
        return ByteBuffer.wrap(data)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getLong();
    }

    public static byte[] encodeIEEE754Float(float number) {
        return encodeInt32(Float.floatToIntBits(number));
    }

    public static byte[] encodeIEEE754Double(double number) {
        return encodeInt64(Double.doubleToLongBits(number));
    }

    public static float decodeIEEE754Float(byte[] data) {
        return Float.intBitsToFloat(decodeInt32(data));
    }

    public static double decodeIEEE754Double(byte[] data) {
        return Double.longBitsToDouble(decodeInt64(data));
    }


}