package org.tdf.lotusvm.runtime;

import lombok.Getter;
import org.tdf.lotusvm.types.LimitType;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

// TODO: limit memory size in block chain
@Getter
public class Memory {
    private static final int PAGE_SIZE = 64 * (1 << 10); // 64 KB
    private byte[] data;
    private LimitType limit;
    private int pages;

    Memory() {
        this.data = new byte[0];
        this.limit = new LimitType();
    }

    Memory(LimitType limit) {
        this.limit = limit;
        this.pages = limit.getMinimum();
        this.data = new byte[limit.getMinimum() * PAGE_SIZE];
    }

    /**
     * Returns the values from each provided array combined into a single array. For example, {@code
     * concat(new byte[] {a, b}, new byte[] {}, new byte[] {c}} returns the array {@code {a, b, c}}.
     *
     * @param arrays zero or more {@code byte} arrays
     * @return a single array containing all the values from the source arrays, in order
     */
    private static byte[] concat(byte[]... arrays) {
        int length = 0;
        for (byte[] array : arrays) {
            length += array.length;
        }
        byte[] result = new byte[length];
        int pos = 0;
        for (byte[] array : arrays) {
            System.arraycopy(array, 0, result, pos, array.length);
            pos += array.length;
        }
        return result;
    }

    void copyFrom(byte[] data) {
        if (limit.isBounded() && data.length > limit.getMaximum() * PAGE_SIZE) {
            throw new RuntimeException("exec: out of bounds memory access");
        }
        this.data = data;
    }

    public void putString(int offset, String s) {
        byte[] data = s.getBytes(StandardCharsets.UTF_8);
        put(offset, data);
    }

    public void put(int offset, byte[] data) {
        if(offset + data.length >= this.data.length)
            throw new RuntimeException("exec: out of bounds memory access");
        System.arraycopy(data, 0, this.data, offset, data.length);
    }

    public void putLong(int offset, long data) {
        storeI64(offset, data);
    }

    public String loadString(int offset, int n) {
        return new String(loadN(offset, n), StandardCharsets.UTF_8);
    }

    public byte[] loadN(int offset, int n) {
        if (offset < 0 || n < 0) {
            throw new RuntimeException("exec: out of bounds memory access");
        }
        if (offset + n > data.length) {
            throw new RuntimeException("exec: out of bounds memory access");
        }
        return Arrays.copyOfRange(data, offset, offset + n);
    }

    public int get(int offset) {
        if (offset >= data.length) {
            throw new RuntimeException("exec: out of bounds memory access");
        }
        return data[offset] & 0xff;
    }

    public long getLong(int offset) {
        if (offset >= data.length) {
            throw new RuntimeException("exec: out of bounds memory access");
        }
        return (data[offset] & 0xff) & 0x00000000ffffffffL;
    }

    public int load32(int offset) {
        return get(offset) | (get(offset + 1) << 8) | (get(offset + 2) << 16) | (get(offset + 3) << 24);
    }

    public long load64(int offset) {
        return getLong(offset) |
                (getLong(offset + 1) << 8) |
                (getLong(offset + 2) << 16) |
                (getLong(offset + 3) << 24) |
                (getLong(offset + 4) << 32) |
                (getLong(offset + 5) << 40) |
                (getLong(offset + 6) << 48) |
                (getLong(offset + 7) << 56)
                ;

    }

    public byte load8(int offset) {
        return (byte) get(offset);
    }

    public short load16(int offset) {
        return (short) (get(offset) | ((get(offset + 1) & 0xff) << 8));
    }

    public void storeI32(int offset, int n) {
        storeI8(offset, (byte) (n & 0xff));
        storeI8(offset + 1, (byte) ((n >>> 8) & 0xff));
        storeI8(offset + 2, (byte) ((n >>> 16) & 0xff));
        storeI8(offset + 3, (byte) ((n >>> 24) & 0xff));
    }

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

    public void storeI16(int offset, short n) {
        storeI8(offset, (byte) (n & 0xff));
        storeI8(offset + 1, (byte) ((n >>> 8) & 0xff));
    }

    public void storeI8(int offset, byte n) {
        if(offset >= this.data.length)
            throw new RuntimeException("exec: out of bounds memory access");
        this.data[offset] = n;
    }

    int getPageSize() {
        return pages;
    }

    // The memory.grow instruction is non-deterministic.
    // It may either succeed, returning the old memory size sz, or fail, returning -1
    // Failure must occur if the referenced memory instance has a maximum size defined that would be exceeded.
    // However, failure can occur in other cases as well.
    // In practice, the choice depends on the resources available to the embedder.
    public int grow(int n) {
        if (limit.isBounded() && getPageSize() + n > limit.getMaximum()) {
            return -1;
        }
        int prev = this.pages;
        this.pages += n;
        byte[] tmp = new byte[PAGE_SIZE * this.pages];
        System.arraycopy(this.data, 0, tmp, 0, this.data.length);
        this.data = tmp;
        return prev;
    }


    public int getActualSize() {
        return data.length;
    }

}
