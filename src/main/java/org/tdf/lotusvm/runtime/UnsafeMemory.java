package org.tdf.lotusvm.runtime;

import lombok.Getter;
import org.tdf.lotusvm.types.LimitType;
import sun.misc.Unsafe;

import java.io.Closeable;
import java.lang.reflect.Field;
import java.nio.ByteOrder;

public class UnsafeMemory implements Memory, Closeable {
    private LimitType limit = new LimitType();
    private final Unsafe unsafe = reflectGetUnsafe();
    private final int ARRAY_OFFSET = unsafe.arrayBaseOffset(byte[].class);

    private long pointer;
    private long overflow;

    public UnsafeMemory() {
        if (ByteOrder.nativeOrder() != ByteOrder.LITTLE_ENDIAN)
            throw new RuntimeException("create unsafe memory failed: native byte order is not le!");
    }

    @Getter
    private int pages;

    public static Unsafe reflectGetUnsafe() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe) field.get(null);
        } catch (Exception e) {
            throw new RuntimeException("access unsafe failed");
        }
    }

    @Override
    public void setLimit(LimitType limit) {
        this.pages = limit.getMinimum();
        pointer = unsafe.allocateMemory(pages * PAGE_SIZE);
        overflow = pointer + pages * PAGE_SIZE;
        unsafe.setMemory(pointer, pages * PAGE_SIZE, (byte) 0);
        this.limit = limit;

    }

    @Override
    public void put(int offset, byte[] data) {
        long o = Integer.toUnsignedLong(offset);
        if (pointer + o + data.length > overflow)
            throw new RuntimeException("memory access overflow");
        unsafe.copyMemory(data, ARRAY_OFFSET, null, pointer + offset, data.length);
    }

    @Override
    public byte[] load(int offset, int length) {
        long o = Integer.toUnsignedLong(offset);
        if (pointer + o + length > overflow)
            throw new RuntimeException("memory access overflow");
        byte[] r = new byte[length];
        unsafe.copyMemory(null, pointer + offset, r, ARRAY_OFFSET, length);
        return r;
    }

    @Override
    public int load32(int offset) {
        long o = Integer.toUnsignedLong(offset);
        if (pointer + o + 4 > overflow)
            throw new RuntimeException("memory access overflow");
        return unsafe.getInt(pointer + offset);
    }

    @Override
    public long load64(int offset) {
        long o = Integer.toUnsignedLong(offset);
        if (pointer + o + 8 > overflow)
            throw new RuntimeException("memory access overflow");
        return unsafe.getLong(pointer + offset);
    }

    @Override
    public byte load8(int offset) {
        long o = Integer.toUnsignedLong(offset);
        if (pointer + o >= overflow)
            throw new RuntimeException("memory access overflow");
        return unsafe.getByte(pointer + offset);
    }

    @Override
    public short load16(int offset) {
        long o = Integer.toUnsignedLong(offset);
        if (pointer + o + 2 > overflow)
            throw new RuntimeException("memory access overflow");
        return unsafe.getShort(pointer + offset);
    }

    @Override
    public void storeI32(int offset, int val) {
        long o = Integer.toUnsignedLong(offset);
        if (pointer + o + 4 > overflow)
            throw new RuntimeException("memory access overflow");
        unsafe.putInt(pointer + offset, val);
    }

    @Override
    public void storeI64(int offset, long n) {
        long o = Integer.toUnsignedLong(offset);
        if (pointer + o + 8 > overflow)
            throw new RuntimeException("memory access overflow");
        unsafe.putLong(pointer + offset, n);
    }

    @Override
    public void storeI16(int offset, short num) {
        long o = Integer.toUnsignedLong(offset);
        if (pointer + o + 2 > overflow)
            throw new RuntimeException("memory access overflow");
        unsafe.putShort(pointer + offset, num);
    }

    @Override
    public void storeI8(int offset, byte n) {
        long o = Integer.toUnsignedLong(offset);
        if (pointer + o >= overflow)
            throw new RuntimeException("memory access overflow");
        unsafe.putByte(pointer + offset, n);
    }

    @Override
    public int grow(int n) {
        if (limit.isBounded() && getPages() + n > limit.getMaximum()) {
            return -1;
        }
        long newPointer = unsafe.allocateMemory((pages + n) * PAGE_SIZE);
        unsafe.setMemory(newPointer, (pages + n) * PAGE_SIZE, (byte) 0);
        unsafe.copyMemory(this.pointer, newPointer, this.pages * PAGE_SIZE);
        int prev = this.pages;
        this.pages += n;
        unsafe.freeMemory(this.pointer);
        this.pointer = newPointer;
        this.overflow = this.pointer + this.pages * PAGE_SIZE;
        return prev;
    }


    @Override
    public void close() {
        if (pointer == 0)
            return;
        unsafe.freeMemory(pointer);
        pointer = 0;
    }

}
