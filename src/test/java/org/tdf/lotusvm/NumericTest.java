package org.tdf.lotusvm;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.tdf.lotusvm.runtime.LimitedStackProvider;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RunWith(JUnit4.class)
public class NumericTest {
    public void testSpecFunctions(String filename, Collection<String> functions, int skip, int limit) throws Exception {
        ModuleInstance instance =
                ModuleInstance.Builder.builder()
                        .binary(Util.readClassPathFileAsByteArray("testdata/" + filename))
                    .stackProvider(new LimitedStackProvider(32768 * 128, 32768, 32768 * 128))
                .build()
        ;
        RuntimeTest.TestConfig config = RuntimeTest.getTestConfig("testdata/modules.json", filename);
        Set<String> all = new HashSet<>(functions);
        List<RuntimeTest.TestFunction> tests = config.tests.stream().filter(f -> all.contains(f.function)).skip(skip).limit(limit > 0 ? limit : Long.MAX_VALUE).collect(Collectors.toList());
        for (RuntimeTest.TestFunction function : tests) {
            Long[] args;
            if (function.args != null) {
                args = function.args.stream().map(x -> x.data).toArray(Long[]::new);
            } else {
                args = new Long[0];
            }
            long[] args1 = new long[args.length];
            for (int i = 0; i < args.length; i++) {
                args1[i] = args[i];
            }
            if (function.trap != null && !function.trap.equals("")) {
                Exception e = null;
                try {
                    instance.execute(function.function, args1);
                } catch (Exception e2) {
                    e = e2;
                }
                if (e == null) {
                    throw new RuntimeException(filename + " " + function.function + " failed" + " " + function.trap + " expected");
                }
                continue;
            }
            long[] res;
            try {
                res = instance.execute(function.function, args1);
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(filename + " " + function.function + " failed, unexpected exception ");
            }
            if (function.returns != null) {
                if (res[0] != function.returns.data) {
                    throw new RuntimeException(filename + " " + function.function + " failed");
                }
                continue;
            }
            if (res.length != 0) {
                throw new RuntimeException(filename + " " + function.function + " failed, the result should be null");
            }
        }
    }

    public void testSpecFile(String filename) throws Exception {
        RuntimeTest.TestConfig config = RuntimeTest.getTestConfig("testdata/modules.json", filename);
        testSpecFunctions(filename, config.tests.stream().map(x -> x.function).collect(Collectors.toList()), 0, -1);
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
