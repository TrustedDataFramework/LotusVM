package org.tdf.lotusvm;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.tdf.lotusvm.runtime.LimitedStackAllocator;
import org.tdf.lotusvm.runtime.StackAllocator;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RunWith(JUnit4.class)
public class RuntimeTest {
    private static final StackAllocator provider = new LimitedStackAllocator(32768 * 128, 32768, 32768 * 128);

    public static ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(JsonParser.Feature.ALLOW_COMMENTS);

    public static class TestConfig {
        public String file;
        public List<TestFunction> tests;
    }

    public static class TestFunction {
        public String function;
        public List<Argument> args;
        @JsonProperty("return")
        public Argument returns;
        public String trap;
        @JsonProperty("recoverpanic")
        public boolean recoverPanic;

        @JsonProperty("_comment")
        public String comment;

        public int[] must_native_compile;
    }

    @JsonDeserialize(using = ArgumentDeserializer.class)
    static class Argument {
        public long data;

        public Argument(long data) {
            this.data = data;
        }
    }

    private static class ArgumentDeserializer extends JsonDeserializer<Argument> {
        private Optional<Long> tryParseHexLong(String s) {
            try {
                return Optional.of(Long.parseUnsignedLong(s, 16));
            } catch (Exception e) {
                return Optional.empty();
            }
        }

        @Override
        public Argument deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
            JsonNode node = p.getCodec().readTree(p);
            String encoded = node.asText();
            String[] typeValue = encoded.split(":");
            String type = typeValue[0];
            String val = typeValue[1];
            switch (type.toLowerCase().trim()) {
                case "i32":
                    if (val.startsWith("0x"))
                        return new Argument(Integer.toUnsignedLong(Integer.parseUnsignedInt(val.substring(2), 16)));
                    if(val.startsWith("-")){
                        return new Argument(Integer.toUnsignedLong(Integer.parseInt(val)));
                    }
                    return new Argument(Integer.toUnsignedLong(Integer.parseUnsignedInt(val)));
                case "i64":
                    if (val.startsWith("0x"))
                        return new Argument(Long.parseUnsignedLong(val.substring(2), 16));
                    if(val.startsWith("-")){
                        return new Argument(Long.parseLong(val));
                    }
                    return new Argument(Long.parseUnsignedLong(val));
                case "f32":
                    if (val.toLowerCase().trim().equals("nan")) {
                        return new Argument(Integer.toUnsignedLong(Float.floatToRawIntBits(Float.NaN)));
                    }
                    if (val.startsWith("0x")) {
                        Optional<Long> o = tryParseHexLong(val.substring(2));

                        if (o.isPresent()) {
                            return new Argument(
                                    o.map(x -> (float) x).map(Float::floatToIntBits).map(Integer::toUnsignedLong).get()
                            );
                        }
                    }
                    return new Argument(
                            Integer.toUnsignedLong(
                                    Float.floatToIntBits(Float.parseFloat(val))
                            ));
                case "f64":
                    if (val.toLowerCase().trim().equals("nan")) {
                        return new Argument(Double.doubleToLongBits(Double.NaN));
                    }
                    if (val.startsWith("0x")) {
                        Optional<Long> o = tryParseHexLong(val.substring(2));

                        if (o.isPresent()) {
                            return new Argument(
                                    o.map(x -> (double) x).map(Double::doubleToLongBits).get()
                            );
                        }
                    }
                    return new Argument(
                            Double.doubleToLongBits(Double.parseDouble(val))
                    );
            }
            throw new RuntimeException("invalid type " + type);
        }
    }

    public static TestConfig[] getTestConfig(String filepath) throws Exception {
        return MAPPER.readValue(
                Util.readClassPathFile(filepath),
                TestConfig[].class);
    }

    public static TestConfig getTestConfig(String filepath, String filename) throws Exception {
        return Stream.of(getTestConfig(filepath)).filter(x -> x.file.equals(filename)).findFirst().get();
    }

    public void testSpecFunctions(String filename, Collection<String> functions, int skip, int limit) throws Exception {
        ModuleInstance instance =
                ModuleInstance.Builder
                        .builder()
                        .validateFunctionType()
                .binary(Util.readClassPathFileAsByteArray("testdata/spec/" + filename))
                    .stackProvider(provider)
                .build()
        ;
        TestConfig config = getTestConfig("testdata/spec/modules.json", filename);
        Set<String> all = new HashSet<>(functions);
        List<TestFunction> tests = config.tests.stream().filter(f -> all.contains(f.function)).skip(skip).limit(limit > 0 ? limit : Long.MAX_VALUE).collect(Collectors.toList());
        for (TestFunction function : tests) {
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
        TestConfig config = getTestConfig("testdata/spec/modules.json", filename);
        testSpecFunctions(filename, config.tests.stream().map(x -> x.function).collect(Collectors.toList()), 0, -1);
    }

    @Before
    public void beforeTest(){
        provider.clear();
    }

    @Test
    public void testAddWasm() throws Exception {
        ModuleInstance instance =
                ModuleInstance.Builder.builder()
                .binary(Util.readClassPathFileAsByteArray("expression-tests/add.wasm"))
                    .stackProvider(provider)
                .build()
        ;
        assert instance.execute(0, 1, 1)[0] == 2;
        assert instance.execute(0, 1, -1)[0] == 0;
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
        assert Float.isNaN((float)( 0.0 / 0.0));
    }

    @Test
    public void testDoubleToUnsignedLong(){
        double d = Long.MAX_VALUE;
        double d2 = d + 1;
        long l = new BigDecimal(d2).longValue();
        System.out.println(Long.toUnsignedString(Long.MAX_VALUE));
        System.out.println(Long.toUnsignedString(l));
    }
}
