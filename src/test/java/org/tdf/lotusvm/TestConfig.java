package org.tdf.lotusvm;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.tdf.lotusvm.runtime.LimitedStackAllocator;
import org.tdf.lotusvm.runtime.Memory;
import org.tdf.lotusvm.runtime.StackAllocator;
import org.tdf.lotusvm.runtime.UnsafeMemory;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class TestConfig {
    public String file;
    public List<TestFunction> tests;
    @JsonIgnore
    public String directory;

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
    public static class Argument {
        public long data;

        public Argument(long data) {
            this.data = data;
        }
    }

    public void testSpecFunctions(String filename, Collection<String> functions, int skip, int limit) throws Exception {
        Memory m = new UnsafeMemory();
        StackAllocator provider = new LimitedStackAllocator(32768 * 128, 32768, 32768 * 128);

        ModuleInstance instance =
            ModuleInstance.Builder

                .builder()
                .memory(m)
                .validateFunctionType()
                .binary(Util.readClassPathFile(Paths.get(directory, filename).toString()))
                .stackProvider(provider)
                .build()
            ;
        Set<String> all = new HashSet<>(functions);
        List<TestConfig.TestFunction> tests = this.tests.stream().filter(f -> all.contains(f.function)).skip(skip).limit(limit > 0 ? limit : Long.MAX_VALUE).collect(Collectors.toList());
        for (TestConfig.TestFunction function : tests) {
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
        m.close();
    }

    public static class ArgumentDeserializer extends JsonDeserializer<Argument> {
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
}
