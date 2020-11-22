package org.tdf.lotusvm.runtime;

import lombok.AccessLevel;
import lombok.Setter;
import org.tdf.lotusvm.ModuleInstance;
import org.tdf.lotusvm.types.FunctionType;

import java.util.Objects;

public abstract class HostFunction implements FunctionInstance {
    @Setter
    private ModuleInstanceImpl instance;
    @Setter(AccessLevel.PROTECTED)
    private FunctionType type;
    @Setter(AccessLevel.PROTECTED)
    private String name;

    public HostFunction() {
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
    public abstract long[] execute(long... parameters);

    @Override
    public boolean isHost() {
        return true;
    }

    public String getName() {
        return name;
    }

    ;

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
        return instance.memory.loadN(offset, length);
    }

    protected void putStringIntoMemory(int offset, String data) {
        instance.memory.putString(offset, data);
    }

    protected String loadStringFromMemory(int offset, int length) {
        return instance.memory.loadString(offset, length);
    }
}
