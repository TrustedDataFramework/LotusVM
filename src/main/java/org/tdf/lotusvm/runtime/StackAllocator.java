package org.tdf.lotusvm.runtime;

import org.tdf.lotusvm.types.ValueType;

import java.io.Closeable;

// stack provider to avoid array create
public interface StackAllocator extends Closeable {
    long MAXIMUM_UNSIGNED_I32 = 0xFFFFFFFFL;
    long UNSIGNED_MASK = 0x7fffffffffffffffL;
    long FUNCTION_INDEX_MASK = 0x0000000000007fffL;
    long TABLE_MASK = 0x0000000000008000L;

    boolean isEmpty();

    long execute();

    // create a frame, return the frame Id, the function referred by index must be wasm function
    void pushFrame(int functionIndex, long[] params);

    void pushExpression(long instructions, ValueType type);

    int getLabelSize();

    // get from stack by index, unchecked
    long getUnchecked(int index);

    int popN(int frameIndex, int n);

    int getPc();

    void setPc(int pc);

    long getInstructions();

    long pop();

    void push(long value);

    long getLocal(int idx);

    void setLocal(int idx, long value);

    // clear frames
    void clear();

    // get current module instance
    ModuleInstanceImpl getModule();

    void setModule(ModuleInstanceImpl module);

    default void close() {

    }
}
