package org.tdf.lotusvm;

import org.apache.commons.codec.binary.Hex;
import org.junit.Test;
import org.tdf.lotusvm.runtime.HostFunction;
import org.tdf.lotusvm.runtime.LimitedStackAllocator;
import org.tdf.lotusvm.runtime.Memory;
import org.tdf.lotusvm.runtime.UnsafeMemory;
import org.tdf.lotusvm.types.FunctionType;
import org.tdf.lotusvm.types.Module;

import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class Bench {
    private static final String WBI_MALLOC = "__malloc";
    private static final String WBI_CHANGE_TYPE = "__change_t";
    private static final String POOL_DATA = "f90237f84f3894eb6c145066d39593aa432a9eda28c6279db1a01e038a08c45a4b0419db4400008a06a0729b1ad7e75c01e78a06f6f870e54fc5a800008a06f6fd369ed995e608c08089024a3e494ea7d0fa6f12f851389441c32b9fcf59f408909be04aaf2b58c7bae9b3400189013f306a2409fc00008901158e460913d0000088a688906bd8b0000088a688906bd8b00000884fcc1a89027f0000891e33535a0a37c2e5f312f85838947d5509050a47c716c4c3c61f051f1a9879b84c9c018a02d267667bd3777800008a02d267667bd3777800008a051d7fd7a006dff000008a051d84487e54d810000089b499d99ef4ddde000089937a549644d60952fb12f8593894e4cd658d9743cb7fb5d9cb0585662ce1f126fa9a018a02d22202ea51328400008a02d22202ea51328400008a0739eb824a004d1400008a0703b5b89c3a6e740000893d14a380aaf5b32c008a0bd027f7101f31fd2c9912f84e389408a11d706a65639672fa2fbe2affa849f7b2d701808a152d02c7e14af68000008a152d02c7e14af68000008a152d02c7e14af68000008a152d02c7e14af680000080880de0b6b3a764000012f84e389457e3e62c4c1bcd29c3089a8eee74a7a62477e739808a152d02c7e14af68000008a152d02c7e14af68000008a152d02c7e14af68000008a152d02c7e14af680000080880de0b6b3a764000012f83c3894036045e8b0f931b026c611de79786a51353ffdd8028a0259c9528c619b5400008a0259c9528c619b5400008a0259c9528c619b54000080808012";
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

    // LimitedStackAllocator: 1934.4 ms
    // BaseStackAllocator:

    public static void main(String... args) throws Exception {
        String file = Bench.class
            .getClassLoader()
            .getResource("bench/main.wasm")
            .getFile();
        int loop = 10;

        byte[] data = Files.readAllBytes(Path.of(file));
        byte[] input = Hex.decodeHex(POOL_DATA);
        Module m = new Module(data);
        Set<HostFunction> hosts = new HashSet<>();
        hosts.add(new EmptyHost("_u256"));
        hosts.add(new EmptyHost("_context"));
        hosts.add(new EmptyHost("_reflect"));
        hosts.add(new EmptyHost("_db"));
        hosts.add(new EmptyHost("_log"));
        LimitedStackAllocator allocator = new LimitedStackAllocator(MAX_STACK_SIZE, MAX_FRAMES, MAX_LABELS);


        long start = System.currentTimeMillis();

        allocator.clear();
        for (int i = 0; i < loop; i++) {
            Memory mem = new UnsafeMemory();
            ModuleInstance ins = ModuleInstance
                .builder()
                .module(m)
                .memory(mem)
                .hostFunctions(hosts)
                .stackProvider(
                    allocator
                ).build();

            int ptr = mallocBytes(ins, input);
            ins.execute("estimateShare", ptr, 0);
            mem.close();
        }

        long end = System.currentTimeMillis();
        System.out.println((end - start) * 1.0 / loop);
    }

    @Test
    public void unsafeTest() {
        System.out.println(ByteOrder.nativeOrder());
    }
}
