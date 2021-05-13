package org.tdf.lotusvm;

import lombok.SneakyThrows;
import org.tdf.lotusvm.runtime.*;

import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TestModule {
    private TestConfig[] configs;
    private String directory;

    private StackAllocator stackAllocator = Util.getAllocator();

    public TestModule(TestConfig[] configs, String directory) {
        this.configs = configs;
        this.directory = directory;
    }

    @SneakyThrows
    public void testAll() {
        for(int i = 0; i < configs.length; i++) {
            testSpecFunctions(i, null, 0, -1);
        }
    }

    public TestConfig getConfig(String filename) {
        List<TestConfig> cfgs =
            Stream.of(
                configs).filter(x -> x.file.equals(filename)
            ).findFirst().stream().collect(Collectors.toList());

        if (cfgs.size() == 0)
            throw new RuntimeException("config not found");

        if (cfgs.size() > 1)
            throw new RuntimeException("two much configs");

        return cfgs.get(0);
    }

    public void testSpecFunctions(String filename, Collection<String> functions, int skip, int limit) throws Exception {
        int idx = getIndex(filename);
        testSpecFunctions(idx, functions, skip, limit);
    }

    public synchronized void testSpecFunctions(int index, Collection<String> functions, int skip, int limit) throws Exception {
        TestConfig cfg = this.configs[index];
        String filename = Paths.get(directory, cfg.file).toString();

        Memory m = new UnsafeMemory();
        UnsafeStackAllocator u = new UnsafeStackAllocator(32768 * 128, 32768, 32768 * 128);
        this.stackAllocator.clear();
        ModuleInstance instance =
            ModuleInstance.Builder

                .builder()
                .memory(m)
                .validateFunctionType()
                .binary(Util.readClassPathFile(filename))
                .stackAllocator(u)
                .build();
        Set<String> all = new HashSet<>(functions == null ? Collections.emptyList() : functions);
        List<TestConfig.TestFunction> tests = functions == null ? cfg.tests :
            cfg.tests.stream().filter(f -> all.contains(f.function))
                .skip(skip)
                .limit(limit > 0 ? limit : Long.MAX_VALUE)
                .collect(Collectors.toList());

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
                long[] r = null;
                try {
                    r = instance.execute(function.function, args1);
                } catch (Exception e2) {
                    e = e2;
                }
                if (e == null) {
                    System.out.println("test failed for file = " + cfg.file + " function = " + function.function);
//                    throw new RuntimeException(filename + " " + function.function + " failed" + " " + function.trap + " expected");
                }
                continue;
            }
            long[] res;
            try {
                res = instance.execute(function.function, args1);
            } catch (Exception e) {
                System.out.println("test failed for file = " + cfg.file + " function = " + function.function);

                e.printStackTrace();
                continue;
//                throw new RuntimeException(filename + " " + function.function + " failed, unexpected exception ");
            }
            if (function.returns != null) {
                if (res[0] != function.returns.data) {
                    System.out.println("test failed for file = " + cfg.file + " function = " + function.function);

//                    throw new RuntimeException(filename + " " + function.function + " failed");
                }
                continue;
            }
            if (res.length != 0) {
                throw new RuntimeException(filename + " " + function.function + " failed, the result should be null");
            }
        }
        m.close();
    }

    public int getIndex(String filename) {
        int index = -1;
        for (int i = 0; i < this.configs.length; i++) {
            if (this.configs[i].file.equals(filename)) {
                index = i;
                break;
            }
        }
        if (index < 0)
            throw new RuntimeException("file not found in module");
        return index;
    }
}
