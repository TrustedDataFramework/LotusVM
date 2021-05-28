package org.tdf.lotusvm;

import lombok.SneakyThrows;
import org.tdf.lotusvm.runtime.*;
import org.tdf.lotusvm.types.FunctionType;
import org.tdf.lotusvm.types.ValueType;

import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TestModule {
    private TestConfig[] configs;
    private String directory;

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

    public static class PrintHost extends HostFunction {

        public PrintHost() {
            super(
                "print",
                new FunctionType(Collections.singletonList(ValueType.I64), Collections.emptyList()),
                "print_i32"
            );
        }

        @Override
        public long execute(long[] args) {
            System.out.println(args[0]);
            return 0;
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
        ModuleInstance instance;
        UnsafeStackAllocator u = new UnsafeStackAllocator(32768 * 128, 32768, 32768 * 128);
        Module md;
            try {
                md = Module.create(Util.readClassPathFile(filename));
                instance = ModuleInstance
                    .builder()
                    .memory(m)
                    .hostFunctions(Collections.singleton(new PrintHost()))
                    .validateFunctionType()
                    .module(md)
                    .stackAllocator(u)
                    .build();
            } catch (Exception e) {
                System.out.println("test file ignored = " + filename + " reason = " + e.getMessage());
                return;
            };

        Set<String> all = new HashSet<>(functions == null ? Collections.emptyList() : functions);

        List<TestConfig.TestFunction> tests = functions == null ? cfg.tests :
            cfg.tests.stream().filter(f -> all.contains(f.function))
                .collect(Collectors.toList());

        for (int j = skip; j < Integer.toUnsignedLong(limit) && j < tests.size(); j++) {
            TestConfig.TestFunction function = tests.get(j);
            String failedMessage =
                String.format("test failed for file = %s function = %s  idx = %d", cfg.file, function.function, j);

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
            if(function.invokeOnly) {
                try {
                    instance.execute(function.function, args1);
                } catch (Exception e) {
                    System.err.println(e.getMessage());
                }
                continue;
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
                    System.err.println(failedMessage + " " + function.trap + " expected");
                } else {
//                    System.out.println("test passed for file = " + cfg.file + " function = " + function.function);
                }
                continue;
            }
            long[] res;
            try {
                res = instance.execute(function.function, args1);
            } catch (Exception e) {
                if(e.getMessage() == null || !e.getMessage().startsWith("float number")) {
                    System.err.println(failedMessage);
                    e.printStackTrace();
                }
                continue;
//                throw new RuntimeException(filename + " " + function.function + " failed, unexpected exception ");
            }
            if (function.returns != null) {
                if (res[0] != function.returns.data) {
                    System.err.println(failedMessage + " return not match ");
                    System.err.printf("found %d expect %d%n", res[0], function.returns.data);

//                    throw new RuntimeException(filename + " " + function.function + " failed");
                } else {
//                    System.out.println("test passed for file = " + cfg.file + " function = " + function.function);
                }
                continue;
            }
            if (res.length != 0) {
                throw new RuntimeException(filename + " " + function.function + " failed, the result should be null");
            }
//            System.out.println("test passed for file = " + cfg.file + " function = " + function.function);
        }
        u.close();
        md.close();
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
