package org.tdf.lotusvm.runtime;

import org.tdf.lotusvm.types.Instruction;

public interface Hook {
    void onInstruction(Instruction ins, ModuleInstanceImpl module);
    void onHostFunction(HostFunction function, ModuleInstanceImpl module);
}