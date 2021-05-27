package org.tdf.lotusvm.types;

public class UnsafeLongBuffer implements LongBuffer{
    private long pointer;
    private long limit;
    private int cap;
    private int size;

    public int size() {
        return size;
    }

    public UnsafeLongBuffer(int initialSize) {
        int cap = Math.max(initialSize, 8);
        long bytes = UnsafeUtil.fastMul8(cap);
        this.pointer = UnsafeUtil.UNSAFE.allocateMemory(
                bytes
        );
        this.limit = pointer + bytes;
        this.cap = cap;
        UnsafeUtil.UNSAFE.setMemory(this.pointer, limit - pointer, (byte) 0);
    }

    public long get(int index) {
        long ptr = pointer + UnsafeUtil.fastMul8(index);
        if (ptr + 8 > limit)
            throw new RuntimeException("memory access overflow");
        return UnsafeUtil.UNSAFE.getLong(ptr);
    }

    public void set(int index, long val) {
        long ptr = pointer + UnsafeUtil.fastMul8(index);
        if (ptr + 8 > limit)
            throw new RuntimeException("memory access overflow");
        UnsafeUtil.UNSAFE.putLong(ptr, val);
    }


    public void push(long v) {
        if(this.size == cap) {
            long prevBytes = limit - pointer;
            long afterBytes = prevBytes * 2;
            this.pointer = UnsafeUtil.UNSAFE.reallocateMemory(this.pointer, afterBytes);
            this.limit = this.pointer + afterBytes;
            this.cap *= 2;
            UnsafeUtil.UNSAFE.setMemory(this.pointer + prevBytes, afterBytes - prevBytes, (byte) 0);
        }
        set(this.size, v);
        this.size++;
    }

    @Override
    public void setSize(int size) {
        this.size = size;
        if(this.cap < this.size) {
            long prevBytes = limit - pointer;
            long afterBytes = this.size * 8L;
            this.pointer = UnsafeUtil.UNSAFE.reallocateMemory(this.pointer, afterBytes);
            this.limit = this.pointer + afterBytes;
            this.cap = this.size;
            UnsafeUtil.UNSAFE.setMemory(this.pointer + prevBytes, afterBytes - prevBytes, (byte) 0);
        }
    }

    @Override
    public void close() {
        if(pointer != 0) {
            UnsafeUtil.UNSAFE.freeMemory(pointer);
            this.pointer = 0;
        }
    }

}
