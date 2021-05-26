package org.tdf.lotusvm.runtime;

import org.tdf.lotusvm.common.OpCode;

public interface Hook {
    void onInstruction(OpCode ins, ModuleInstanceImpl module);

    void onHostFunction(HostFunction function, ModuleInstanceImpl module);

    void onNewFrame();

    void onFrameExit();

    default void onMemoryGrow(int beforeGrow, int afterGrow) {

    }
}
