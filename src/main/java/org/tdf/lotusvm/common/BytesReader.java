package org.tdf.lotusvm.common;

import lombok.Getter;

import java.io.InputStream;

@Getter
public class BytesReader extends InputStream {// io.reader
    private static final long FIRST_BIT = 0x80L;
    private static final long MASK = 0x7fL;

    private final byte[] buffer;
    private int offset;
    private final int limit;

    public BytesReader(byte[] buffer) {
        this.buffer = buffer;
        this.limit = buffer.length;
    }

    private BytesReader(byte[] buffer, int offset, int limit) {
        this.buffer = buffer;
        this.offset = offset;
        this.limit = limit;
    }

    public static void main(String[] args) {
        BytesReader reader = new BytesReader(new byte[]{(byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0xfd, 0x07});
        System.out.println(reader.readVarUint(64));
        BytesReader reader1 = new BytesReader(new byte[]{(byte) 0x80, 0x7f});
        System.out.println(reader1.readVarUint(64));
        BytesReader reader2 = new BytesReader(new byte[]{0x08});
        System.out.println(reader2.readVarUint(64));

    }

    public int peek() {
        return buffer[offset] & 0xff;
    }

    public int read() {
        return buffer[offset++] & 0xff;
    }

    public int remaining() {
        return limit - offset;
    }

    public byte[] read(int size) {
        byte[] buf = new byte[size];
        System.arraycopy(this.buffer, offset, buf, 0, size);
        this.offset += size;
        return buf;
    }

    public BytesReader readAsReader(int size) {
        BytesReader r = new BytesReader(buffer, offset, offset + size);
        this.offset += size;
        return r;
    }

    public byte[] readAll() {
        return read(remaining());
    }

    // u32
    public int readVarUint32() throws RuntimeException {
        return (int) readVarUint(32);
    }

    public long readVarUint64() throws RuntimeException {
        return readVarUint(64);
    }

    public long readVarUint(int n) throws RuntimeException {
        if (n > 64) {
            throw new RuntimeException("leb128: n must <= 64");
        }
//        UnsignedLong res = UnsignedLong.ZERO;
        long res = 0;
        int shift = 0;
        while (true) {
            // the b here is non-negative here
            // n is non-negative also
            // unsigned conversion safety
            long b = Integer.toUnsignedLong(read());
            // note: can not use b < 1<<n, when n == 64, 1<<n will overflow to 0

            long r = n == 64 ? 0xFFFFFFFFFFFFFFFFL : ((1L << n) - 1L);
            if ((b & FIRST_BIT) == 0 && Long.compareUnsigned(b, r) <= 0) {
                res |=  b << shift;
                return res;
            }

            if((b & FIRST_BIT) != 0 && n > 7) {
                res |=  (b & MASK) << shift;
                shift+= 7;
                n -= 7;
                continue;
            }
            throw new RuntimeException("leb128: invalid int");
        }
    }

    public int readVarInt32() throws RuntimeException {
        return (int) readVarInt(32);
    }

    public long readVarInt64() throws RuntimeException {
        return readVarInt(64);
    }

    public long readVarInt(int n) throws RuntimeException {
        if (n > 64) {
            throw new RuntimeException("leb128: n must <= 64");
        }
        long res = 0;
        int shift = 0;
        while (true) {
            long b = read() & 0xffffffffL;
            //  b < 1<<6 && uint64(b) < uint64(1<<(n-1))
            if (b < 1 << 6 &&
                Long.compareUnsigned(b, 1L << (n - 1)) < 0) {
                res += (1L << shift) * b;
                break;
            }
            // b >= 1<<6 && b < 1<<7 && uint64(b)+1<<(n-1) >= 1<<7
            if (b >= 1 << 6 &&
                b < 1 << 7 &&
                Long.compareUnsigned(b + (1L << (n - 1)), 1L << 7) >= 0
            ) {
                res += (1L << shift) * (b - (1 << 7));
                break;
            }
            // b >= 1<<7 && n > 7
            if (b >= 1 << 7 && n > 7) {
                long a = b - (1 << 7);
                res += (1L << shift) * a;
                shift += 7;
                n -= 7;
                continue;
            }
            throw new RuntimeException("leb128: invalid int");
        }
        return res;
    }

    public int readUint32() throws RuntimeException {
        int r = LittleEndian.decodeInt32(buffer, offset);
        this.offset += 4;
        return r;
    }

    public long readUint64() {
        long r = LittleEndian.decodeInt64(buffer, offset);
        this.offset += 8;
        return r;
    }

    public float readFloat() {
        return Float.intBitsToFloat(readUint32());
    }

    public double readDouble() {
        return Double.longBitsToDouble(readUint64());
    }

}
