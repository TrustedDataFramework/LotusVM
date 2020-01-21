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
    static Builder builder() {
        return Builder.builder();
    }

    long[] getGlobals();

    void setGlobals(long[] globals);

    List<GlobalType> getGlobalTypes();

    byte[] getMemory();

    void setMemory(byte[] memory);

    boolean containsExport(String funcName);

    long[] execute(int functionIndex, long... parameters);

    long[] execute(String funcName, long... parameters) throws RuntimeException;

    @Getter
    class Builder {
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
