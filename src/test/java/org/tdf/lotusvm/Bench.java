package org.tdf.lotusvm;

import org.tdf.lotusvm.runtime.HostFunction;
import org.tdf.lotusvm.runtime.Memory;
import org.tdf.lotusvm.runtime.UnsafeMemory;
import org.tdf.lotusvm.runtime.UnsafeStackAllocator;
import org.tdf.lotusvm.types.FunctionType;
import org.tdf.lotusvm.types.Module;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

public class Bench {
    private static final String WBI_MALLOC = "__malloc";
    private static final String WBI_CHANGE_TYPE = "__change_t";
    public static final int MAX_FRAMES = 16384;
    public static final int MAX_STACK_SIZE = MAX_FRAMES * 64;
    public static final int MAX_LABELS = MAX_FRAMES * 64;
    public static final int MAX_CALL_DEPTH = 8;

    private static final long UINT_256 = 0xec13d6d1L; // keccak(uint256)
    private static final long ADDRESS = 0x421683f8L; // keccak(address)
    private static final long STRING = 0x97fc4627L; // keccak(string)
    private static final long BYTES = 0xb963e9b4L; // keccak(bytes)

    public static int mallocInternal(ModuleInstance ins, long type, byte[] bin) {
        long ptr = ins.execute(WBI_MALLOC, bin.length)[0];
        ins.getMemory().put((int) ptr, bin);
        long p = ins.execute(WBI_CHANGE_TYPE, type, ptr, bin.length)[0];
        int r = (int) p;
        if (r < 0) throw new RuntimeException("malloc failed: pointer is negative");
        return r;
    }


    private static int mallocBytes(ModuleInstance ins, byte[] bin) {
        return mallocInternal(ins, BYTES, bin);
    }

    public static class EmptyHost extends HostFunction {

        public EmptyHost(String name) {
            super(name, new FunctionType(Collections.emptyList(), Collections.emptyList()));
        }

        @Override
        public long execute(long[] parameters) {
            return 0;
        }
    }

    // ops = 0.58
    // rust wasmer ops = 8.06451
    public static void main(String... args) throws Exception {
        String file = Bench.class
            .getClassLoader()
            .getResource("bench/main.wasm")
            .getFile();
        int loop = 10;

        byte[] data = Files.readAllBytes(Path.of(file));
        Module m = new Module(data);


        long start = System.currentTimeMillis();
        UnsafeStackAllocator u = new UnsafeStackAllocator(MAX_STACK_SIZE, MAX_FRAMES, MAX_LABELS);

        for (int i = 0; i < loop; i++) {
            u.clear();
            Memory mem = new UnsafeMemory();
            ModuleInstance ins = ModuleInstance
                .builder()
                .module(m)
                .memory(mem)
                .stackAllocator(
                    u
                ).build();

            ins.execute("bench");
            mem.close();
        }
        u.close();

        long end = System.currentTimeMillis();
        System.out.println("ops = " + loop * 1.0 / (end - start) * 1000);
    }
}
