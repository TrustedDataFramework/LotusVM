package org.tdf.lotusvm;

import org.tdf.lotusvm.types.GlobalType;

import java.util.List;

public interface ModuleInstance {
    long[] getGlobals();

    List<GlobalType> getGlobalTypes();

    byte[] getMemory();

    boolean hasExport(String funcName);

    long[] execute(int functionIndex, long... parameters);

    long[] execute(String funcName, long... parameters) throws RuntimeException;
}
