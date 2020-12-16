package org.tdf.lotusvm.runtime;

import lombok.Getter;
import org.tdf.lotusvm.common.LittleEndian;
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
        this.data = new byte[0];
        this.pages = limit.getMinimum();
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
        this.pages = data.length / PAGE_SIZE + (data.length % PAGE_SIZE == 0 ? 0 : 1);
    }

    public void putString(int offset, String s) {
        byte[] data = s.getBytes(StandardCharsets.UTF_8);
        put(offset, data);
    }

    public void put(int offset, byte[] data) {
        spaceCheck(offset + data.length);
        System.arraycopy(data, 0, this.data, offset, data.length);
    }

    public void putLong(int offset, long data) {
        put(offset, LittleEndian.encodeInt64(data));
    }

    public String loadString(int offset, int n) {
        return new String(loadN(offset, n), StandardCharsets.UTF_8);
    }

    public byte[] loadN(int offset, int n) {
        if (offset < 0 || n < 0) {
            throw new RuntimeException("exec: out of bounds memory access");
        }
        if (offset + n > pages * PAGE_SIZE) {
            throw new RuntimeException("exec: out of bounds memory access");
        }
        if (offset + n <= data.length) {
            return Arrays.copyOfRange(data, offset, offset + n);
        }
        if (offset >= data.length) {
            return new byte[n];
        }
        byte[] bytes0 = Arrays.copyOfRange(data, offset, data.length);
        return concat(bytes0, new byte[n - bytes0.length]);
    }

    public byte get(int offset){
        if (offset < 0 || offset > pages * PAGE_SIZE) {
            throw new RuntimeException("exec: out of bounds memory access");
        }
        if(offset < data.length)
            return data[offset];
        return 0;
    }

    public int load32(int offset) {
        return LittleEndian.decodeInt32(loadN(offset, Integer.BYTES));
    }

    public long load64(int offset) {
        return LittleEndian.decodeInt64(loadN(offset, Long.BYTES));
    }

    public byte load8(int offset) {
        return loadN(offset, 1)[0];
    }

    public short load16(int offset) {
        return LittleEndian.decodeInt16(loadN(offset, Short.BYTES));
    }

    public void storeI32(int offset, int n) {
        put(offset, LittleEndian.encodeInt32(n));
    }

    public void storeI64(int offset, long n) {
        put(offset, LittleEndian.encodeInt64(n));
    }

    public void storeI16(int offset, short n) {
        put(offset, LittleEndian.encodeInt16(n));
    }

    public void storeI8(int offset, byte n) {
        put(offset, new byte[]{n});
    }

    private void spaceCheck(int n) {
        if (n <= data.length) return;
        if (n > pages * PAGE_SIZE) throw new RuntimeException("exec: out of bounds memory access");
        byte[] tmp = data;
        data = new byte[n];
        System.arraycopy(tmp, 0, data, 0, tmp.length);
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
        int tmp = this.pages;
        this.pages += n;
        return tmp;
    }


    public int getActualSize() {
        return data.length;
    }
}
