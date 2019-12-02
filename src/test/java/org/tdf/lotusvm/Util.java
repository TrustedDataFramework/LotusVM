package org.tdf.lotusvm;

import com.google.common.io.ByteStreams;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class Util {
    public static File readClassPathFile(String name){
        return new File(Util.class.getClassLoader().getResource(name).getFile());
    }

    public static byte[] readClassPathFileAsByteArray(String name) throws IOException {
        return ByteStreams.toByteArray(new FileInputStream(readClassPathFile(name)));
    }
}
