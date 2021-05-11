package org.tdf.lotusvm.runtime;

import org.tdf.lotusvm.types.LimitType;

import java.io.Closeable;

public interface Memory extends Closeable {
    int PAGE_SIZE = 64 * (1 << 10); // 64 KB

    void setLimit(LimitType limit);

    void put(int offset, byte[] data);

    byte[] load(int offset, int length);

    int load32(int offset);

    long load64(int offset);

    byte load8(int offset);

    short load16(int offset);

    void storeI32(int offset, int val);

    void storeI64(int offset, long n);

    void storeI16(int offset, short num);

    void storeI8(int offset, byte n);

    int grow(int n);

    int getPages();

    default void close() {

    }
}
