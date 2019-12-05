package org.tdf.lotusvm;

import org.tdf.lotusvm.runtime.ModuleInstanceImpl;
import org.tdf.lotusvm.types.GlobalType;

import java.util.List;

public interface ModuleInstance {
    long[] getGlobals();

    List<GlobalType> getGlobalTypes();

    byte[] getMemory();

    long[] execute(int functionIndex, long... parameters);

    long[] execute(String funcName, long... parameters) throws RuntimeException;

    static ModuleInstance newInstance(Config config) {
        return new ModuleInstanceImpl(config);
    }
}
