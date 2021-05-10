package org.tdf.lotusvm.runtime;

import lombok.Getter;
import org.tdf.lotusvm.types.LimitType;
import sun.misc.Unsafe;

import java.io.Closeable;
import java.lang.reflect.Field;

public class UnsafeMemory implements Memory, Closeable {
    private LimitType limit = new LimitType();
    private Unsafe unsafe = reflectGetUnsafe();
    private long pointer;
    private long overflow;

    @Getter
    private int pages;

    private static Unsafe reflectGetUnsafe() {
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
        for (int i = 0; i < data.length; i++) {
            storeI8(offset + i, data[i]);
        }
    }

    @Override
    public byte[] load(int offset, int length) {
        byte[] r = new byte[length];
        for (int i = 0; i < length; i++) {
            r[i] = load8(offset + i);
        }
        return r;
    }

    @Override
    public int load32(int offset) {
        if (pointer + offset + 4 > overflow)
            throw new RuntimeException("memory access overflow");
        return (load8(offset) & 0xff) | ((load8(offset + 1) & 0xff) << 8) | ((load8(offset + 2) & 0xff) << 16) | ((load8(offset + 3) & 0xff) << 24);
    }

    @Override
    public long load64(int offset) {
        if (pointer + offset + 8 > overflow)
            throw new RuntimeException("memory access overflow");
        return (((long) load8(offset)) & 0xffL) |
            (((long) load8(offset + 1)) & 0xffL) << 8 |
            (((long) load8(offset + 2)) & 0xffL) << 16 |
            (((long) load8(offset + 3)) & 0xffL) << 24 |
            (((long) load8(offset + 4)) & 0xffL) << 32 |
            (((long) load8(offset + 5)) & 0xffL) << 40 |
            (((long) load8(offset + 6)) & 0xffL) << 48 |
            (((long) load8(offset + 7)) & 0xffL) << 56
            ;
    }

    @Override
    public byte load8(int offset) {
        if (offset < 0 || pointer + offset >= overflow)
            throw new RuntimeException("memory access overflow");
        return unsafe.getByte(pointer + offset);
    }

    @Override
    public short load16(int offset) {
        return (short) ((load8(offset) & 0xff) | ((load8(offset + 1) & 0xff) << 8));
    }

    @Override
    public void storeI32(int offset, int val) {
        storeI8(offset, (byte) (val & 0xff));
        storeI8(offset + 1, (byte) ((val >>> 8) & 0xff));
        storeI8(offset + 2, (byte) ((val >>> 16) & 0xff));
        storeI8(offset + 3, (byte) ((val >>> 24) & 0xff));
    }

    @Override
    public void storeI64(int offset, long n) {
        storeI8(offset, (byte) (n & 0xff));
        storeI8(offset + 1, (byte) ((n >>> 8) & 0xff));
        storeI8(offset + 2, (byte) ((n >>> 16) & 0xff));
        storeI8(offset + 3, (byte) ((n >>> 24) & 0xff));
        storeI8(offset + 4, (byte) ((n >>> 32) & 0xff));
        storeI8(offset + 5, (byte) ((n >>> 40) & 0xff));
        storeI8(offset + 6, (byte) ((n >>> 48) & 0xff));
        storeI8(offset + 7, (byte) ((n >>> 56) & 0xff));
    }

    @Override
    public void storeI16(int offset, short num) {
        storeI8(offset, (byte) (num & 0xff));
        storeI8(offset + 1, (byte) ((num >>> 8) & 0xff));
    }

    @Override
    public void storeI8(int offset, byte n) {
        if (offset < 0 || pointer + offset >= overflow)
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
