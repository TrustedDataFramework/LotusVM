package org.tdf.lotusvm.types;

public class ArrayLongBuffer implements LongBuffer {
    private long[] data;
    private int size;

    public ArrayLongBuffer(int initialCap) {
        this.data = new long[Math.max(initialCap, 8)];
    }

    public int size() {
        return size;
    }

    public long get(int index) {
        return data[index];
    }

    public void set(int index, long val) {
        data[index] = val;
    }


    public void push(long value) {
        if (this.data.length == this.size) {
            long[] tmp = new long[this.data.length * 2];
            System.arraycopy(this.data, 0, tmp, 0, this.data.length);
            this.data = tmp;
        }
        set(this.size, value);
        this.size++;
    }

    @Override
    public void setSize(int size) {
        this.size = size;
        if (this.data.length < this.size) {
            long[] tmp = new long[this.size];
            System.arraycopy(this.data, 0, tmp, 0, this.data.length);
            this.data = tmp;
        }
    }

    @Override
    public void close() {

    }
}
