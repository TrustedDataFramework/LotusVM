package org.tdf.lotusvm;

import lombok.Getter;
import org.tdf.lotusvm.runtime.Hook;
import org.tdf.lotusvm.runtime.HostFunction;
import org.tdf.lotusvm.runtime.ModuleInstanceImpl;

import java.util.Collections;
import java.util.Set;

@Getter
public class Builder {
    private boolean initGlobals = true;
    private boolean initMemory = true;
    private Set<HostFunction> hostFunctions = Collections.emptySet();
    private Set<Hook> hooks = Collections.emptySet();

    private byte[] binary;
    private long[] globals;
    private byte[] memory;

    private Builder(){}

    public static Builder builder(){
        return new Builder();
    }

    public Builder initGlobals(boolean initGlobals){
        this.initGlobals = initGlobals;
        return this;
    }

    public Builder initMemory(boolean initMemory){
        this.initMemory = initMemory;
        return this;
    }

    public Builder hostFunctions(Set<HostFunction> hostFunctions){
        this.hostFunctions = hostFunctions;
        return this;
    }

    public Builder hooks(Set<Hook> hooks){
        this.hooks = hooks;
        return this;
    }

    public Builder binary(byte[] binary){
        this.binary = binary;
        return this;
    }

    public Builder globals(long[] globals){
        this.globals = globals;
        return this;
    }

    public Builder memory(byte[] memory){
        this.memory = memory;
        return this;
    }

    public ModuleInstance build(){
        return new ModuleInstanceImpl(this);
    }
}
