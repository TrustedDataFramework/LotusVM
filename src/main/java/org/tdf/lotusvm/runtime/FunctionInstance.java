package org.tdf.lotusvm.runtime;

import org.tdf.lotusvm.types.FunctionType;

public interface FunctionInstance {
    int parametersLength();
    int getArity();
    FunctionType getType();
    long[] execute(long... parameters);
    boolean isHost();
}
