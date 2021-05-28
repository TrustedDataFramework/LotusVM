package org.tdf.lotusvm.types;

public class UnsafeLongBuffer implements LongBuffer {
    private long pointer;
    private long limit;
    private int cap;
    private int size;

    public UnsafeLongBuffer(int initialCap) {
        int cap = Math.max(initialCap, 8);
        long bytes = (cap * 8L);
        this.pointer = UnsafeUtil.UNSAFE.allocateMemory(bytes);
        this.limit = pointer + bytes;
        this.cap = cap;
        UnsafeUtil.UNSAFE.setMemory(this.pointer, limit - pointer, (byte) 0);
    }

    public int size() {
        return size;
    }

    public long get(int index) {
        if (index >= size)
            throw new RuntimeException("access overflow");
        long ptr = pointer + (index * 8L);
        return UnsafeUtil.UNSAFE.getLong(ptr);
    }

    public void set(int index, long val) {
        if (index >= size)
            throw new RuntimeException("access overflow");
        long ptr = pointer + (index * 8L);
        UnsafeUtil.UNSAFE.putLong(ptr, val);
    }

    private void setInternal(int index, long val) {
        long ptr = pointer + (index * 8L);
        UnsafeUtil.UNSAFE.putLong(ptr, val);
    }

    public void push(long value) {
        if (this.size == cap) {
            long prevBytes = limit - pointer;
            long afterBytes = prevBytes * 2;
            this.pointer = UnsafeUtil.UNSAFE.reallocateMemory(this.pointer, afterBytes);
            this.limit = this.pointer + afterBytes;
            this.cap *= 2;
            UnsafeUtil.UNSAFE.setMemory(this.pointer + prevBytes, afterBytes - prevBytes, (byte) 0);
        }
        setInternal(this.size, value);
        this.size++;
    }

    @Override
    public void setSize(int size) {
        this.size = size;
        if (this.cap < this.size) {
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
        if (pointer != 0) {
            UnsafeUtil.UNSAFE.freeMemory(pointer);
            this.pointer = 0;
        }
    }
}
