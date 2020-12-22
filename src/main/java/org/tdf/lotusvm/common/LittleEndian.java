package org.tdf.lotusvm.common;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * little endian encoding helpers
 */
public class LittleEndian {

    public static short decodeInt16(byte[] data, int offset) {
        return (short) ((data[offset] & 0xff)| ((data[offset + 1] & 0xff) << 8));
    }

    public static void encodeInt16(short num, byte[] data, int offset) {
        data[offset] = (byte) (num & 0xff);
        data[offset + 1] = (byte) ((num >>> 8) & 0xff);
    }

    public static int decodeInt32(byte[] data, int offset) {
        return (data[offset] & 0xff)| ((data[offset + 1] & 0xff) << 8) | ((data[offset + 2] & 0xff )<< 16) | ((data[offset + 3] & 0xff)<< 24);
    }

    public static void encodeInt32(int val, byte[] data, int offset) {
        data[offset] = (byte) (val & 0xff);
        data[offset + 1] = (byte) ((val >>> 8) & 0xff);
        data[offset + 2] = (byte) ((val >>> 16) & 0xff);
        data[offset + 3] = (byte) ((val >>> 24) & 0xff);
    }

    // big-endian encoding
    public static void encodeInt64(long n, byte[] data, int offset) {
        data[offset] = (byte) (n & 0xff);
        data[offset + 1] = (byte) ((n >>> 8) & 0xff);
        data[offset + 2] = (byte) ((n >>> 16) & 0xff);
        data[offset + 3] = (byte) ((n >>> 24) & 0xff);
        data[offset + 4] = (byte) ((n >>> 32) & 0xff);
        data[offset + 5] = (byte) ((n >>> 40) & 0xff);
        data[offset + 6] = (byte) ((n >>> 48) & 0xff);
        data[offset + 7] = (byte) ((n >>> 56) & 0xff);
    }

    public static long decodeInt64(byte[] data, int offset) {
        return (((long) data[offset]) & 0xffL)|
                (((long) data[offset + 1]) & 0xffL) << 8 |
                (((long) data[offset + 2]) & 0xffL) << 16 |
                (((long) data[offset + 3]) & 0xffL) << 24 |
                (((long) data[offset + 4]) & 0xffL) << 32 |
                (((long) data[offset + 5]) & 0xffL) << 40 |
                (((long) data[offset + 6]) & 0xffL) << 48 |
                (((long) data[offset + 7]) & 0xffL) << 56
                ;
    }

    public static void encodeIEEE754Float(float number, byte[] data, int offset) {
        encodeInt32(Float.floatToIntBits(number), data, offset);
    }

    public static void encodeIEEE754Double(double number, byte[] data, int offset) {
        encodeInt64(Double.doubleToLongBits(number), data, offset);
    }

    public static float decodeIEEE754Float(byte[] data, int offset) {
        return Float.intBitsToFloat(decodeInt32(data, offset));
    }

    public static double decodeIEEE754Double(byte[] data, int offset) {
        return Double.longBitsToDouble(decodeInt64(data, offset));
    }


}