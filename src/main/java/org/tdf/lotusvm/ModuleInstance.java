package org.tdf.lotusvm;

import lombok.Getter;
import org.tdf.lotusvm.runtime.*;
import org.tdf.lotusvm.types.GlobalType;
import org.tdf.lotusvm.types.Module;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Not support:
 * 1. sign-extension operators
 * 2. return multi-value
 * 3. per frame stack size < 65536
 * 4. per frame locals size < 65536
 * 5. per frame labels size < 65536
 * 6. float number, i.e. f32_nearest, f64_round (platform undefined behavior)
 */
public interface ModuleInstance {
    static Builder builder() {
        return Builder.builder();
    }

    long[] getGlobals();

    void setGlobals(long[] globals);

    List<GlobalType> getGlobalTypes();

    Memory getMemory();

    Set<Hook> getHooks();

    void setHooks(Set<Hook> hooks);

    boolean containsExport(String funcName);

    long[] execute(int functionIndex, long... parameters);

    long[] execute(String funcName, long... parameters) throws RuntimeException;

    @Getter
    class Builder {
        boolean validateFunctionType;
        private Set<HostFunction> hostFunctions = Collections.emptySet();
        private Set<Hook> hooks = Collections.emptySet();
        private byte[] binary;
        private long[] globals;
        private Memory memory;
        private Module module;
        private StackAllocator stackAllocator;

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

        public Builder stackAllocator(StackAllocator allocator) {
            this.stackAllocator = allocator;
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

        public Builder memory(Memory memory) {
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
