package org.tdf.lotusvm.runtime;

import org.tdf.lotusvm.ModuleInstance;
import org.tdf.lotusvm.types.FunctionType;

import java.util.*;

public abstract class HostFunction implements FunctionInstance {
    private final FunctionType type;
    private final String name;
    ModuleInstanceImpl instance;
    private Set<String> alias;

    public HostFunction(String name, FunctionType type) {
        this.name = name;
        this.type = type;
    }

    public HostFunction(String name, FunctionType type, String... alias) {
        this.name = name;
        this.type = type;
        this.alias = new HashSet<>();
        this.alias.addAll(Arrays.asList(alias));
    }

    public Set<String> getAlias() {
        if (alias == null)
            return Collections.emptySet();
        return alias;
    }

    protected ModuleInstance getInstance() {
        return instance;
    }

    @Override
    public int parametersLength() {
        return getType().getParameterTypes().size();
    }

    @Override
    public int getArity() {
        return getType().getResultTypes().size();
    }

    @Override
    public FunctionType getType() {
        return type;
    }


    @Override
    public abstract long execute(long[] parameters);

    @Override
    public boolean isHost() {
        return true;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HostFunction that = (HostFunction) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    protected void putMemory(int offset, byte[] data) {
        instance.memory.put(offset, data);
    }

    protected byte[] loadMemory(int offset, int length) {
        return instance.memory.load(offset, length);
    }
}
