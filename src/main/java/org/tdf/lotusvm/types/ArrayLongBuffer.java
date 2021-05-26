package org.tdf.lotusvm.types;

public class ArrayLongBuffer implements LongBuffer{
    private long[] data;
    private int size;

    public int size() {
        return size;
    }

    public ArrayLongBuffer(int initialSize) {
        this.data = new long[Math.max(initialSize, 8)];
    }

    public long get(int index) {
        return data[index];
    }

    public void set(int index, long val) {
        data[index] = val;
    }


    public void push(long v) {
        if (this.data.length == this.size) {
            long[] tmp = new long[this.data.length * 2];
            System.arraycopy(this.data, 0, tmp, 0, this.data.length);
            this.data = tmp;
        }
        set(this.size, v);
        this.size++;
    }

}
