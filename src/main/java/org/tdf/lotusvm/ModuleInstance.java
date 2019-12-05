package org.tdf.lotusvm;

import lombok.Getter;
import org.tdf.lotusvm.runtime.Hook;
import org.tdf.lotusvm.runtime.HostFunction;
import org.tdf.lotusvm.runtime.ModuleInstanceImpl;
import org.tdf.lotusvm.types.GlobalType;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public interface ModuleInstance {
    long[] getGlobals();

    List<GlobalType> getGlobalTypes();

    byte[] getMemory();

    boolean hasExport(String funcName);

    long[] execute(int functionIndex, long... parameters);

    long[] execute(String funcName, long... parameters) throws RuntimeException;

    static Builder builder() {
        return Builder.builder();
    }

    @Getter
    class Builder {
        private boolean initGlobals = true;
        private boolean initMemory = true;
        private Set<HostFunction> hostFunctions = Collections.emptySet();
        private Set<Hook> hooks = Collections.emptySet();

        private byte[] binary;
        private long[] globals;
        private byte[] memory;

        private Builder() {
        }

        public static Builder builder() {
            return new Builder();
        }

        public Builder initGlobals(boolean initGlobals) {
            this.initGlobals = initGlobals;
            return this;
        }

        public Builder initMemory(boolean initMemory) {
            this.initMemory = initMemory;
            return this;
        }

        public Builder hostFunctions(Set<HostFunction> hostFunctions) {
            this.hostFunctions = hostFunctions;
            return this;
        }

        public Builder hooks(Set<Hook> hooks) {
            this.hooks = hooks;
            return this;
        }

        public Builder binary(byte[] binary) {
            this.binary = binary;
            return this;
        }

        public Builder globals(long[] globals) {
            this.globals = globals;
            return this;
        }

        public Builder memory(byte[] memory) {
            this.memory = memory;
            return this;
        }

        public ModuleInstance build() {
            return new ModuleInstanceImpl(this);
        }
    }
}
