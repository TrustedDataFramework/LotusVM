package org.tdf.lotusvm.runtime;

import lombok.Getter;
import org.tdf.lotusvm.types.LimitType;

import java.io.Closeable;
import java.nio.ByteOrder;

import static org.tdf.lotusvm.types.UnsafeUtil.UNSAFE;

public class UnsafeMemory implements Memory, Closeable {
    private static final int MAX_PAGES = 0xFFFF;
    private final int ARRAY_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);
    private LimitType limit = new LimitType();
    private long pointer;
    private long overflow;
    @Getter
    private int pages;

    public UnsafeMemory() {
        if (ByteOrder.nativeOrder() != ByteOrder.LITTLE_ENDIAN)
            throw new RuntimeException("create unsafe memory failed: native byte order is not little endian!");
    }

    public void setRawSize(int rawSize) {
        pointer = UNSAFE.allocateMemory(rawSize);
        overflow = pointer + rawSize;
        UNSAFE.setMemory(pointer, rawSize, (byte) 0);
    }

    @Override
    public void setLimit(LimitType limit) {
        this.pages = limit.getMinimum();
        int rawSize = pages * PAGE_SIZE;
        if (rawSize < 0)
            throw new RuntimeException("memory overflow");
        setRawSize(rawSize);
        this.limit = limit;
    }

    @Override
    public void put(int offset, byte[] data) {
        long o = Integer.toUnsignedLong(offset);
        if (pointer + o + data.length > overflow)
            throw new RuntimeException("memory access overflow");
        UNSAFE.copyMemory(data, ARRAY_OFFSET, null, pointer + offset, data.length);
    }

    @Override
    public byte[] load(int offset, int length) {
        long o = Integer.toUnsignedLong(offset);
        if (pointer + o + length > overflow)
            throw new RuntimeException("memory access overflow");
        byte[] r = new byte[length];
        UNSAFE.copyMemory(null, pointer + offset, r, ARRAY_OFFSET, length);
        return r;
    }

    @Override
    public int load32(int offset) {
        long o = Integer.toUnsignedLong(offset);
        if (pointer + o + 4 > overflow)
            throw new RuntimeException("memory access overflow");
        return UNSAFE.getInt(pointer + offset);
    }

    @Override
    public long load64(int offset) {
        long o = Integer.toUnsignedLong(offset);
        if (pointer + o + 8 > overflow)
            throw new RuntimeException("memory access overflow");
        return UNSAFE.getLong(pointer + offset);
    }

    @Override
    public byte load8(int offset) {
        long o = Integer.toUnsignedLong(offset);
        if (pointer + o >= overflow)
            throw new RuntimeException("memory access overflow");
        return UNSAFE.getByte(pointer + offset);
    }

    @Override
    public short load16(int offset) {
        long o = Integer.toUnsignedLong(offset);
        if (pointer + o + 2 > overflow)
            throw new RuntimeException("memory access overflow");
        return UNSAFE.getShort(pointer + offset);
    }

    @Override
    public void storeI32(int offset, int val) {
        long o = Integer.toUnsignedLong(offset);
        if (pointer + o + 4 > overflow)
            throw new RuntimeException("memory access overflow");
        UNSAFE.putInt(pointer + offset, val);
    }

    @Override
    public void storeI64(int offset, long n) {
        long o = Integer.toUnsignedLong(offset);
        if (pointer + o + 8 > overflow)
            throw new RuntimeException("memory access overflow");
        UNSAFE.putLong(pointer + offset, n);
    }

    @Override
    public void storeI16(int offset, short num) {
        long o = Integer.toUnsignedLong(offset);
        if (pointer + o + 2 > overflow)
            throw new RuntimeException("memory access overflow");
        UNSAFE.putShort(pointer + offset, num);
    }

    @Override
    public void storeI8(int offset, byte n) {
        long o = Integer.toUnsignedLong(offset);
        if (pointer + o >= overflow)
            throw new RuntimeException("memory access overflow");
        UNSAFE.putByte(pointer + offset, n);
    }

    @Override
    public int grow(int n) {
        if (limit.isBounded() && getPages() + n > limit.getMaximum()) {
            return -1;
        }
        if (n + this.pages > MAX_PAGES)
            return -1;
        int prevRawSize = (int) (overflow - pointer);
        int newRawSize = (pages + n) * PAGE_SIZE;
        if (newRawSize < 0)
            throw new RuntimeException("memory overflow");
        long newPointer = UNSAFE.reallocateMemory(pointer, newRawSize);
        UNSAFE.setMemory(newPointer + prevRawSize, newRawSize - prevRawSize, (byte) 0);
        int prev = this.pages;
        this.pages += n;
        this.pointer = newPointer;
        this.overflow = this.pointer + newRawSize;
        return prev;
    }


    @Override
    public void close() {
        if (pointer == 0)
            return;
        UNSAFE.freeMemory(pointer);
        pointer = 0;
    }
}
