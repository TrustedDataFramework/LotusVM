package org.tdf.lotusvm;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.SneakyThrows;
import org.tdf.lotusvm.runtime.StackAllocator;
import org.tdf.lotusvm.runtime.UnsafeStackAllocator;

import java.io.File;
import java.io.InputStream;

import java.nio.file.Paths;

public class Util {
    public static ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .enable(JsonParser.Feature.ALLOW_COMMENTS);


    public static StackAllocator getAllocator() {
        return new UnsafeStackAllocator(32768 * 128, 32768, 32768 * 128);
    }

    @SneakyThrows
    public static byte[] readClassPathFile(String name){
        InputStream stream = Util.class.getClassLoader().getResource(name).openStream();
        byte[] all = new byte[stream.available()];
        if(stream.read(all) != all.length)
            throw new RuntimeException("read bytes from stream failed");
        return all;
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
