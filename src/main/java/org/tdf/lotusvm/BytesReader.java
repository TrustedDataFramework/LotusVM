package org.tdf.lotusvm;

import com.google.common.io.ByteStreams;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;


public class BytesReader extends InputStream{// io.reader
    private ByteBuffer buffer;

    public static BytesReader fromInputStream(InputStream inputStream) throws IOException {
        return new BytesReader(ByteStreams.toByteArray(inputStream));
    }

    private BytesReader (ByteBuffer buffer){
        this.buffer = buffer;
    }

    public BytesReader(byte[] data) {
        this.buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
    }

    public int peek() {
        return Byte.toUnsignedInt(buffer.get(buffer.position()));
    }

    public int read() {
        return Byte.toUnsignedInt(buffer.get());
    }

    public int remaining() {
        return buffer.remaining();
    }

    public byte[] read(int size) {
        byte[] buf = new byte[size];
        buffer.get(buf);
        return buf;
    }

    public BytesReader readAsReader(int size){
        ByteBuffer buf = buffer.slice();
        buf.limit(buffer.position() + size);
        buffer.position(buffer.position() + size);
        return new BytesReader(buf);
    }

    public byte[] readAll() {
        byte[] buf = new byte[buffer.remaining()];
        buffer.get(buf);
        return buf;
    }


    // u32
    public int readVarUint32() throws RuntimeException {
        int res = (int) readVarUint(32);
        if (res < 0)
            throw new RuntimeException("integer overflow");
        return res;
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
            int b = read();
//            UnsignedLong unsignedB = UnsignedLong.fromLongBits(b);
            // note: can not use b < 1<<n, when n == 64, 1<<n will overflow to 0
            long r = (n == 64 ? 0L : 1L << n) - 1;

            if (b < 1 << 7 && Long.compareUnsigned(b, r) <= 0) {
//                res = res.plus(UnsignedLong.valueOf(1L << shift).times(unsignedB));
                res = res + (1L << shift) * b;
                break;
            }
            if (b >= 1 << 7 && n > 7) {
//                res = res.plus(UnsignedLong.valueOf(1L << shift).times(unsignedB.minus(UnsignedLong.valueOf(1 << 7))));
                res = res + (1L << shift) * (b - (1<<7));
                shift += 7;
                n -= 7;
                continue;
            }
            throw new RuntimeException("leb128: invalid int");
        }
        if (res < 0) {
            throw new RuntimeException("integer overflow");
        }
        return res;
    }

    public int readVarInt32() throws RuntimeException {
        long res = readVarInt(32);
        if (res > Integer.MAX_VALUE) throw new RuntimeException("integer overflow");
        return (int) res;
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
            int b = read();
            //  b < 1<<6 && uint64(b) < uint64(1<<(n-1))
            if (b < 1 << 6 &&
                    Long.compareUnsigned(b, 1L << (n-1)) < 0) {
                res += (1L << shift) * b;
                break;
            }
            // b >= 1<<6 && b < 1<<7 && uint64(b)+1<<(n-1) >= 1<<7
            if (b >= 1 << 6 &&
                    b < 1 << 7 &&
                    Long.compareUnsigned(b + (1L << (n-1)), 1L << 7 ) >= 0
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

    public static void main(String[] args) {
        BytesReader reader = new BytesReader(new byte[]{(byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0xfd, 0x07});
        System.out.println(reader.readVarUint(64));
        BytesReader reader1 = new BytesReader(new byte[]{(byte) 0x80, 0x7f});
        System.out.println(reader1.readVarUint(64));
        BytesReader reader2 = new BytesReader(new byte[]{0x08});
        System.out.println(reader2.readVarUint(64));

    }


    public int readUint32() throws RuntimeException {
        int res = buffer.getInt();
        if (res < 0) throw new RuntimeException("integer overflow");
        return res;
    }

    public long readUint64() {
        long res = buffer.getLong();
        if (res < 0) throw new RuntimeException("long overflow");
        return res;
    }

    public float readFloat() {
        return buffer.getFloat();
    }

    public double readDouble() {
        return buffer.getDouble();
    }

}
