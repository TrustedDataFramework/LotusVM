package org.tdf.lotusvm.common;

import java.nio.charset.StandardCharsets;

import static org.tdf.lotusvm.common.Constants.EMPTY_LONGS;

public class Vector {

    // vec(byte)
    public static byte[] readBytesFrom(BytesReader reader) {
        int length = reader.readVarUint32();
        return reader.read(length);
    }


    // name <- vec(byte)
    public static String readStringFrom(BytesReader reader) {
        return new String(readBytesFrom(reader), StandardCharsets.UTF_8);
    }

    // vec(u32)
    public static int[] readUint32VectorFrom(BytesReader reader) {
        int length = reader.readVarUint32();
        int[] res = new int[length];
        for (int i = 0; i < res.length; i++) {
            res[i] = reader.readVarUint32();
        }
        return res;
    }

    public static long[] readUint32VectorAsLongFrom(BytesReader reader) {
        int length = reader.readVarUint32();
        if (length == 0)
            return EMPTY_LONGS;
        long[] res = new long[length];
        for (int i = 0; i < res.length; i++) {
            res[i] = Integer.toUnsignedLong(reader.readVarUint32());
        }
        return res;
    }
}

