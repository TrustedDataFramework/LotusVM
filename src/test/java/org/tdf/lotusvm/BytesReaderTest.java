package org.tdf.lotusvm;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.tdf.lotusvm.common.BytesReader;

import java.util.Arrays;

@RunWith(JUnit4.class)
public class BytesReaderTest {

    @Test
    public void test0(){
        BytesReader reader = new BytesReader(new byte[]{(byte)0x80, (byte)0x80, (byte)0x80, (byte)0x80, 0x78});
        BytesReader read = reader.readAsReader(2);
        assert reader.remaining() == 3;
        assert read.remaining() == 2;
        assert Arrays.equals(reader.readAll(), new byte[]{(byte)0x80, (byte)0x80, 0x78});
        assert Arrays.equals(read.readAll(), new byte[]{(byte)0x80, (byte)0x80});
    }
}
