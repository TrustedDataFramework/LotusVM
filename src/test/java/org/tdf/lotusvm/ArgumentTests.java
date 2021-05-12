package org.tdf.lotusvm;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.tdf.lotusvm.TestConfig.f2l;

@RunWith(JUnit4.class)
public class ArgumentTests {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @SneakyThrows
    @Test
    public void test() {
        String[] args = new String[] {
            "\"f32:0xf32\"", "\"f32:3.4\"", "\"f32:0.0\"",
            "\"f32:-123.0\"", "\"f32:0x1.fffffep+127\"", "\"f32:nan\"",
            "\"f32:inf\"", "\"f32:-inf\"",
            "\"i64:45\"","\"i64:0\"","\"i64:0xABADCAFEDEAD1DEA\"",
            "\"i64:-1\""
        };

        long[] expected = new long[] {
            f2l(3890.0f), f2l(3.4f), f2l(0.0f),
            f2l(-123.0f), f2l(340282346638528859811704183484516925440.0f),
            f2l(Float.NaN), f2l(Float.POSITIVE_INFINITY), f2l(Float.NEGATIVE_INFINITY),
            45L, 0L, 0xABADCAFEDEAD1DEAL,
            Long.parseUnsignedLong("18446744073709551615")
        };

        for(int i = 0; i < args.length; i++) {
            TestConfig.Argument arg = OBJECT_MAPPER.readValue(args[i], TestConfig.Argument.class);
            assert arg.data == expected[i];
        }
    }
}
