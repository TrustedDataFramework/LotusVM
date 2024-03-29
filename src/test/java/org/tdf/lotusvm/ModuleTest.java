package org.tdf.lotusvm;

import com.google.common.io.ByteStreams;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.tdf.lotusvm.common.BytesReader;
import org.tdf.lotusvm.types.ModuleImpl;

import java.io.File;
import java.io.FileInputStream;


@RunWith(JUnit4.class)
public class ModuleTest {

    @Test
    public void test1() throws Exception {
        for (File f : Util.readClassPathDir("testdata/spec")) {
            if(f.getName().equals("address.wasm")) continue;
            if (f.isFile() && f.getName().endsWith(".wasm")) {
                ModuleImpl module = new ModuleImpl(ByteStreams.toByteArray(new FileInputStream(f)));
            }
        }
    }

    private BytesReader getReader() {
        return new BytesReader(new byte[]{0x00, 0x01, 0x02, 0x03});
    }

    @Test
    public void testBytesReaderPeek() {
        BytesReader reader = getReader();
        assert reader.peek() == reader.read();
        assert reader.peek() == reader.read();
        assert reader.peek() == reader.read();
        assert reader.peek() == reader.read();
    }

    @Test
    public void testReadSome() throws Exception {
        BytesReader reader = getReader();
        byte[] data = reader.read(2);
        assert data[0] == 0x00;
        assert data[1] == 0x01;
        data = reader.read(2);
        assert data[0] == 0x02;
        assert data[1] == 0x03;
    }

    @Test
    public void testReadAll() throws Exception {
        BytesReader reader = getReader();
        reader.read(2);
        byte[] data = reader.readAll();
        assert data[0] == 0x02;
        assert data[1] == 0x03;
        assert reader.remaining() == 0;
    }

    @Test
    public void testAddWasm() throws Exception {
        ModuleImpl m = new ModuleImpl(Util.readClassPathFile("expression-tests/add.wasm"));
        assert m.getFunctionSection() != null;
    }

    @Test
    public void testExportAdd() throws Exception {
        ModuleImpl m = new ModuleImpl(Util.readClassPathFile("expression-tests/export-add.wasm"));
        assert m.getFunctionSection() != null;
        assert m.getExportSection() != null;
    }

    @Test
    public void testCall() throws Exception {
        ModuleImpl m = new ModuleImpl(Util.readClassPathFile("expression-tests/call-another.wasm"));
        assert m.getFunctionSection() != null;
        assert m.getExportSection() != null;
    }

    @Test
    public void testAddress() throws Exception {
        ModuleImpl m = new ModuleImpl(Util.readClassPathFile("testdata/spec/address.wasm"));
        assert m.getFunctionSection() != null;
        assert m.getExportSection() != null;
    }

    @Test
    public void testMemory() throws Exception{
        ModuleImpl m = new ModuleImpl(Util.readClassPathFile("expression-tests/memory.wasm"));
        assert m.getDataSection() != null;
    }

    @Test
    public void testTable() throws Exception{
        ModuleImpl m = new ModuleImpl(Util.readClassPathFile("expression-tests/table.wasm"));
        assert m.getTableSection() != null;
    }
}
