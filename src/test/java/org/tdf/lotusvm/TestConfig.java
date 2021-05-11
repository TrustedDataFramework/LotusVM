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
