package org.tdf.lotusvm;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.tdf.lotusvm.runtime.BaseMemory;
import org.tdf.lotusvm.runtime.StackAllocator;

import java.math.BigDecimal;
import java.util.Collection;

@RunWith(JUnit4.class)
public class RuntimeTest {
    public void testSpecFunctions(String filename, Collection<String> functions, int skip, int limit) throws Exception {
        TestModule module = Util.getTestModule("testdata/spec");
        module.testSpecFunctions(filename, functions, skip, limit);
    }

    public void testSpecFile(String filename) throws Exception {
        TestModule module = Util.getTestModule("testdata/spec");
        module.testSpecFunctions(filename, null, 0, -1);
    }


    @Test
    public void testAddWasm() throws Exception {
        Module md = Module.create(Util.readClassPathFile("expression-tests/add.wasm"));
        StackAllocator allocator = Util.getAllocator();
        ModuleInstance instance =
            ModuleInstance.builder()
                .memory(new BaseMemory())
                .module(md)
                .stackAllocator(allocator)
                .build();
        assert instance.execute(0, 1, 1)[0] == 2;
        assert instance.execute(0, 1, -1)[0] == 0;
        md.close();
        allocator.close();
    }

    @Test
    public void testAddress() throws Exception {
        testSpecFile("address.wasm");
    }

    @Test
    public void testBlock() throws Exception {
        testSpecFile("block.wasm");
    }

    @Test
    public void testBR() throws Exception {
        testSpecFile("br.wasm");
    }

    @Test
    public void testBrIf() throws Exception {
        testSpecFile("br_if.wasm");
    }

    @Test
    public void testBrTable() throws Exception {
//        testSpecFunctions("br_table.wasm", Collections.singleton("empty"), 0, 1);
        testSpecFile("br_table.wasm");

    }

    @Test
    public void testBreakDrop() throws Exception {
        testSpecFile("break-drop.wasm");
    }

    @Test
    public void testCallIndirect() throws Exception {
        testSpecFile("call_indirect.wasm");
    }

    @Test
    public void testEndianness() throws Exception {
//        testSpecFunctions("endianness.wasm", Collections.singleton("i64_store32"), 0, 1);
        testSpecFile("endianness.wasm");
    }

    @Test
    public void testFac() throws Exception {
        testSpecFile("fac.wasm");
    }

    @Test
    public void testForward() throws Exception {
        testSpecFile("forward.wasm");
    }

    @Test
    public void testGetLocal() throws Exception {
        testSpecFile("get_local.wasm");
    }

    @Test
    public void testGlobals() throws Exception {
        testSpecFile("globals.wasm");
    }

    @Test
    public void testIF() throws Exception {
        testSpecFile("if.wasm");
    }

    @Test
    public void testLoop() throws Exception {
        testSpecFile("loop.wasm");
    }

    @Test
    public void testMemoryRedundancy() throws Exception {
        testSpecFile("memory_redundancy.wasm");
    }

    @Test
    public void testNop() throws Exception {
        testSpecFile("nop.wasm");
    }

    @Test
    public void testResizing() throws Exception {
        testSpecFile("resizing.wasm");
    }

    @Test
    public void testReturn() throws Exception {
        testSpecFile("return.wasm");
    }

    @Test
    public void testSelect() throws Exception {
        testSpecFile("select.wasm");
    }

    @Test
    public void testSwitch() throws Exception {
//        testSpecFunctions("switch.wasm", Collections.singleton("stmt"), 8, 1);
        testSpecFile("switch.wasm");

    }

    @Test
    public void testTeeLocal() throws Exception {
        testSpecFile("tee_local.wasm");
    }

    @Test
    public void testTrapsIntDiv() throws Exception {
        testSpecFile("traps_int_div.wasm");
    }

    @Test
    public void testTrapsIntRem() throws Exception {
        testSpecFile("traps_int_rem.wasm");

    }

    @Test
    public void testUnreachable() throws Exception {
        testSpecFile("unreachable.wasm");

    }

    @Test
    public void testTrapsMen() throws Exception {
        testSpecFile("traps_mem.wasm");

    }

    @Test
    public void testUnwind() throws Exception {
        testSpecFile("unwind.wasm");
    }


    @Test
    public void testParseFloat() {
        assert Float.isNaN((float) (0.0 / 0.0));
    }

    @Test
    public void testDoubleToUnsignedLong() {
        double d = Long.MAX_VALUE;
        double d2 = d + 1;
        long l = new BigDecimal(d2).longValue();
        System.out.println(Long.toUnsignedString(Long.MAX_VALUE));
        System.out.println(Long.toUnsignedString(l));
    }
}
