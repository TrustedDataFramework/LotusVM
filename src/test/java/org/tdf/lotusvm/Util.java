package org.tdf.lotusvm;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.SneakyThrows;
import org.tdf.lotusvm.runtime.LimitedStackAllocator;
import org.tdf.lotusvm.runtime.StackAllocator;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Util {
    public static ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .enable(JsonParser.Feature.ALLOW_COMMENTS);


    public static StackAllocator getAllocator() {
        return new LimitedStackAllocator(32768 * 128, 32768, 32768 * 128);
    }

    @SneakyThrows
    public static byte[] readClassPathFile(String name){
        String f = Util.class.getClassLoader().getResource(name).getFile();
        return Files.readAllBytes(Path.of(f));
    }

    public static File[] readClassPathDir(String name){
        String f = Util.class.getClassLoader().getResource(name).getFile();
        return new File(f).listFiles();
    }


    public static TestModule getTestModule(String directory) throws Exception {
        String file = Paths.get(directory, "modules.json").toString();
        TestConfig[] cfgs = MAPPER.readValue(
            readClassPathFile(file),
            TestConfig[].class);
        return new TestModule(cfgs, directory);
    }



}
