package org.tdf.lotusvm;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.stream.Collectors;

@RunWith(JUnit4.class)
public class NumericTest {
    public void testSpecFile(String filename) throws Exception {
        TestConfig config = Util.getTestConfig("testdata", filename);
        config.testSpecFunctions(filename, config.tests.stream().map(x -> x.function).collect(Collectors.toList()), 0, -1);
    }

    @Test
    public void testBasic() throws Exception {
        testSpecFile("basic.wasm");
    }

    @Test
    public void testBinary() throws Exception {
        testSpecFile("binary.wasm");
    }

    @Test
    public void testBr() throws Exception {
        testSpecFile("br.wasm");
    }

    @Test
    public void testBrIF() throws Exception {
        testSpecFile("brif.wasm");
    }

    @Test
    public void testBrIFLoop() throws Exception {
        testSpecFile("brif-loop.wasm");
    }

    @Test
    public void testBug49() throws Exception {
        testSpecFile("bug-49.wasm");
    }

    @Test
    public void testCall() throws Exception {
        testSpecFile("call.wasm");
    }

    @Test
    public void testCallZeroArgs() throws Exception {
        testSpecFile("call-zero-args.wasm");
    }

    @Test
    public void testCallIndirect() throws Exception {
        testSpecFile("callindirect.wasm");
    }

    @Test
    public void testCast() throws Exception {
        testSpecFile("cast.wasm");
    }

    @Test
    public void testCompare() throws Exception {
        testSpecFile("compare.wasm");
    }

    @Test
    public void testConvert() throws Exception {
        testSpecFile("convert.wasm");
    }

    @Test
    public void testExprBlock() throws Exception {
        testSpecFile("expr-block.wasm");
    }

    @Test
    public void testExprBr() throws Exception {
        testSpecFile("expr-br.wasm");
    }

    @Test
    public void testExprBrIF() throws Exception {
        testSpecFile("expr-brif.wasm");
    }

    @Test
    public void testExprIF() throws Exception {
        testSpecFile("expr-if.wasm");
    }

    @Test
    public void testIF() throws Exception {
        testSpecFile("if.wasm");
    }

    @Test
    public void testLoad() throws Exception {
        testSpecFile("load.wasm");
    }

    @Test
    public void testNestedIF() throws Exception {
        testSpecFile("nested-if.wasm");
    }

    @Test
    public void testReturn() throws Exception {
        testSpecFile("return.wasm");
    }

    @Test
    public void testRustBasic() throws Exception {
        testSpecFile("rust-basic.wasm");
    }

    @Test
    public void testSelect() throws Exception {
        testSpecFile("select.wasm");
    }

    @Test
    public void testStart() throws Exception {
        testSpecFile("start.wasm");
    }

    @Test
    public void testUnary() throws Exception {
        testSpecFile("unary.wasm");
    }
}
