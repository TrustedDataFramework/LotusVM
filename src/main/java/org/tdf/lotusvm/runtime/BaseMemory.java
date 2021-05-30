package org.tdf.lotusvm.runtime;

import lombok.Getter;
import org.tdf.lotusvm.types.LimitType;

// TODO: limit memory size in block chain
@Getter
class BaseMemory implements Memory {
    private LimitType limit;
    private byte[] data;
    private int pages;

    public BaseMemory() {
        this.data = new byte[0];
        this.limit = new LimitType();
    }

    public void setLimit(LimitType limit) {
        this.limit = limit;
        this.pages = limit.getMinimum();
        this.data = new byte[limit.getMinimum() * PAGE_SIZE];
    }

    public void put(int offset, byte[] data) {
        System.arraycopy(data, 0, this.data, offset, data.length);
    }


    public byte[] load(int offset, int n) {
        byte[] ret = new byte[n];
        System.arraycopy(data, offset, ret, 0, n);
        return ret;
    }

    public int load32(int offset) {
        return (data[offset] & 0xff) | ((data[offset + 1] & 0xff) << 8) | ((data[offset + 2] & 0xff) << 16) | ((data[offset + 3] & 0xff) << 24);
    }

    public long load64(int offset) {
        return (((long) data[offset]) & 0xffL) |
            (((long) data[offset + 1]) & 0xffL) << 8 |
            (((long) data[offset + 2]) & 0xffL) << 16 |
            (((long) data[offset + 3]) & 0xffL) << 24 |
            (((long) data[offset + 4]) & 0xffL) << 32 |
            (((long) data[offset + 5]) & 0xffL) << 40 |
            (((long) data[offset + 6]) & 0xffL) << 48 |
            (((long) data[offset + 7]) & 0xffL) << 56
            ;
    }

    public byte load8(int offset) {
        return data[offset];
    }

    public short load16(int offset) {
        return (short) ((data[offset] & 0xff) | ((data[offset + 1] & 0xff) << 8));
    }

    public void storeI32(int offset, int val) {
        data[offset] = (byte) (val & 0xff);
        data[offset + 1] = (byte) ((val >>> 8) & 0xff);
        data[offset + 2] = (byte) ((val >>> 16) & 0xff);
        data[offset + 3] = (byte) ((val >>> 24) & 0xff);
    }

    public void storeI64(int offset, long n) {
        data[offset] = (byte) (n & 0xff);
        data[offset + 1] = (byte) ((n >>> 8) & 0xff);
        data[offset + 2] = (byte) ((n >>> 16) & 0xff);
        data[offset + 3] = (byte) ((n >>> 24) & 0xff);
        data[offset + 4] = (byte) ((n >>> 32) & 0xff);
        data[offset + 5] = (byte) ((n >>> 40) & 0xff);
        data[offset + 6] = (byte) ((n >>> 48) & 0xff);
        data[offset + 7] = (byte) ((n >>> 56) & 0xff);
    }

    public void storeI16(int offset, short num) {
        data[offset] = (byte) (num & 0xff);
        data[offset + 1] = (byte) ((num >>> 8) & 0xff);
    }

    public void storeI8(int offset, byte n) {
        this.data[offset] = n;
    }


    // The memory.grow instruction is non-deterministic.
    // It may either succeed, returning the old memory size sz, or fail, returning -1
    // Failure must occur if the referenced memory instance has a maximum size defined that would be exceeded.
    // However, failure can occur in other cases as well.
    // In practice, the choice depends on the resources available to the embedder.
    public int grow(int n) {
        if (limit.getBounded() && getPages() + n > limit.getMaximum()) {
            return -1;
        }
        int prev = this.pages;
        this.pages += n;
        byte[] tmp = new byte[PAGE_SIZE * this.pages];
        System.arraycopy(this.data, 0, tmp, 0, this.data.length);
        this.data = tmp;
        return prev;
    }

    @Override
    public void close() {

    }
}
