package org.tdf.lotusvm;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.tdf.lotusvm.common.BytesReader;

@RunWith(JUnit4.class)
public class LebTests {

    @Test
    public void testReadVarUint32(){
        BytesReader reader = new BytesReader(new byte[]{0x08});
        assert reader.readVarUint32() == 8;
        reader = new BytesReader(new byte[]{(byte)0x80, 0x7f});
        assert reader.readVarUint32() == 16256;
        reader = new BytesReader(new byte[]{(byte)0x80, (byte)0x80, (byte)0x80, (byte)0xfd, 0x07});
        assert reader.readVarUint32() == 2141192192;
    }

    @Test
    public void testReadVarUint64(){
        BytesReader reader = new BytesReader(new byte[]{0x08});
        assert reader.readVarUint64() == 8;
        reader = new BytesReader(new byte[]{(byte)0x80, 0x7f});
        assert reader.readVarUint64() == 16256;
        reader = new BytesReader(new byte[]{(byte)0x80, (byte)0x80, (byte)0x80, (byte)0xfd, 0x07});
        assert reader.readVarUint64() == 2141192192;
    }

    @Test
    public void testReadVarInt32(){
        BytesReader reader = new BytesReader(new byte[]{(byte)0x80, (byte)0x80, (byte)0x80, (byte)0x80, 0x78});
        assert reader.readVarInt32() == -2147483648;
        reader = new BytesReader(new byte[]{(byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, 0x07});
        assert reader.readVarInt32() == 2147483647;
        reader = new BytesReader(new byte[]{(byte)0x80, 0x40});
        assert reader.readVarInt32() == -8192;
        assert new BytesReader(new byte[]{(byte)0x80, (byte)0xc0, 0x00}).readVarInt32() == 8192;
        assert new BytesReader(new byte[]{(byte)135, 0x01}).readVarInt32() == 135;
    }

    @Test
    public void testReadVarInt64(){
        BytesReader reader = new BytesReader(new byte[]{(byte)0x80, (byte)0x80, (byte)0x80, (byte)0x80, 0x78});
        assert reader.readVarInt64() == -2147483648;
        reader = new BytesReader(new byte[]{(byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, 0x07});
        assert reader.readVarInt64() == 2147483647;
        reader = new BytesReader(new byte[]{(byte)0x80, 0x40});
        assert reader.readVarInt64() == -8192;
        assert new BytesReader(new byte[]{(byte)0x80, (byte)0xc0, 0x00}).readVarInt64() == 8192;
        assert new BytesReader(new byte[]{(byte)135, 0x01}).readVarInt64() == 135;
    }

    @Test
    public void test0(){
        System.out.println(0x8000000000000000L);
        System.out.println((int) (0xa000000000000000L));
    }
}
