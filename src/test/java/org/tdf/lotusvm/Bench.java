package org.tdf.lotusvm;

import org.tdf.lotusvm.runtime.Memory;
import org.tdf.lotusvm.runtime.UnsafeMemory;
import org.tdf.lotusvm.runtime.UnsafeStackAllocator;
import org.tdf.lotusvm.types.ModuleImpl;

import java.net.URL;

public class Bench {
    private static final String WBI_MALLOC = "__malloc";
    private static final String WBI_CHANGE_TYPE = "__change_t";
    public static final int MAX_FRAMES = 16384;
    public static final int MAX_STACK_SIZE = MAX_FRAMES * 64;
    public static final int MAX_LABELS = MAX_FRAMES * 64;
    public static final int MAX_CALL_DEPTH = 8;


    // ops = 0.58
    // rust wasmer ops = 8.06451
    public static void main(String... args) throws Exception {
        URL file = Bench.class
            .getClassLoader()
            .getResource("bench/main.wasm");
        int loop = 10;

        byte[] data = file.openStream().readAllBytes();

        long start = System.currentTimeMillis();
        UnsafeStackAllocator u = new UnsafeStackAllocator(MAX_STACK_SIZE, MAX_FRAMES, MAX_LABELS);
        ModuleImpl md = new ModuleImpl(data);

        for (int i = 0; i < loop; i++) {
            u.clear();
            Memory mem = new UnsafeMemory();
            ModuleInstance ins = ModuleInstance
                .builder()
                .module(md)
                .memory(mem)
                .stackAllocator(
                    u
                ).build();

            ins.execute("bench");
            mem.close();
        }
        md.close();
        u.close();

        long end = System.currentTimeMillis();
        System.out.println("ops = " + loop * 1.0 / (end - start) * 1000);
    }
}
