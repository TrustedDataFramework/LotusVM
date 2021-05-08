package org.tdf.lotusvm;

import lombok.Getter;
import org.tdf.lotusvm.runtime.*;
import org.tdf.lotusvm.types.GlobalType;
import org.tdf.lotusvm.types.Module;

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

    Memory getMemory();

    void setMemory(byte[] memory);

    Set<Hook> getHooks();

    void setHooks(Set<Hook> hooks);

    boolean containsExport(String funcName);

    long[] execute(int functionIndex, long... parameters);

    long[] execute(String funcName, long... parameters) throws RuntimeException;

    /**
     * create a module instance with empty hooks, the cloned module instance has its own globals and memory
     *
     * @return cloned module instance
     */
    ModuleInstance clone();

    @Getter
    class Builder {
        boolean validateFunctionType;
        private Set<HostFunction> hostFunctions = Collections.emptySet();
        private Set<Hook> hooks = Collections.emptySet();
        private byte[] binary;
        private long[] globals;
        private byte[] memory;
        private Module module;
        private StackProvider stackProvider;

        private Builder() {
        }

        public static Builder builder() {
            return new Builder();
        }

        public Builder module(Module module) {
            this.module = module;
            this.binary = null;
            return this;
        }

        public Builder stackProvider(StackProvider provider) {
            this.stackProvider = provider;
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
            this.module = null;
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

        public Builder validateFunctionType() {
            this.validateFunctionType = true;
            return this;
        }

        public ModuleInstance build() {
            return new ModuleInstanceImpl(this);
        }
    }
}
