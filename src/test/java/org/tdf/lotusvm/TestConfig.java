package org.tdf.lotusvm;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public class TestConfig {
    public static long f2l(float f) {
        return Integer.toUnsignedLong(Float.floatToIntBits(f));
    }

    public static long d2l(double f) {
        return Double.doubleToLongBits(f);
    }

    public String file;
    public List<TestFunction> tests;

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
        public boolean invokeOnly;
    }

    @JsonDeserialize(using = ArgumentDeserializer.class)
    public static class Argument {
        public long data;

        public Argument(long data) {
            this.data = data;
        }
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
            String lower = val.trim().toLowerCase();

            switch (type.toLowerCase().trim()) {
                case "i32":
                    if (val.startsWith("0x"))
                        return new Argument(Integer.toUnsignedLong(Integer.parseUnsignedInt(val.substring(2), 16)));
                    if (val.startsWith("-")) {
                        return new Argument(Integer.toUnsignedLong(Integer.parseInt(val)));
                    }
                    return new Argument(Integer.toUnsignedLong(Integer.parseUnsignedInt(val)));
                case "i64":
                    if (val.startsWith("0x"))
                        return new Argument(Long.parseUnsignedLong(val.substring(2), 16));
                    if (val.startsWith("-")) {
                        return new Argument(Long.parseLong(val));
                    }
                    return new Argument(Long.parseUnsignedLong(val));
                case "f32":
                    switch (lower) {
                        case "nan":
                            return new Argument(f2l(Float.NaN));
                        case "inf":
                            return new Argument(f2l(Float.POSITIVE_INFINITY));
                        case "-inf":
                            return new Argument(f2l(Float.NEGATIVE_INFINITY));
                    }

                    if (val.startsWith("0x")) {
                        Optional<Long> o = tryParseHexLong(val.substring(2));

                        if (o.isPresent()) {
                            return new Argument(
                                o.map(x -> (float) x).map(Float::floatToIntBits).map(Integer::toUnsignedLong).get()
                            );
                        }
                    }

                    return new Argument(f2l(Float.parseFloat(val)));
                case "f64":
                    switch (lower) {
                        case "nan":
                            return new Argument(d2l(Double.NaN));
                        case "inf":
                            return new Argument(d2l(Double.POSITIVE_INFINITY));
                        case "-inf":
                            return new Argument(d2l(Double.NEGATIVE_INFINITY));
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
                        d2l(Double.parseDouble(val))
                    );
            }
            throw new RuntimeException("invalid type " + type);
        }
    }
}
