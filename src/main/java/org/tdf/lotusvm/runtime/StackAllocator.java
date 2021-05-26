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

    int current();

    // create a frame, return the frame Id, the function referred by index must be wasm function
    int pushFrame(int functionIndex, long[] params);

    void pushExpression(long instructions, ValueType type);

    // set frame local variable
    void setLocal(int frameId, int index, long value);

    // push a value into a stack
    void push(int frameId, long value);

    // pop a value from a stack
    long pop(int frameId);

    // get from stack by index, unchecked
    long getUnchecked(int index);

    // get local from stack by index
    long getLocal(int frameId, int index);

    // pop n value into target stack
    // the return value is the offset of stack
    int popN(int frameId, int length);

    // drop the frame
    void drop(int frameId);

    // get stack size of a frame
    int getStackSize(int frameId);

    // set stack of the frame
    void setStackSize(int frameId, int size);

    // push label of the frame
    void pushLabel(int frameId, boolean arity, long body, boolean loop);

    // pop label of the frame
    void popLabel(int frameId);

    // pop and clear label of the frame
    void popAndClearLabel(int frameId);

    // branch on frame
    void branch(int frameId, int l);

    // get label size of the frame
    int getLabelSize(int frameId);

    // get instruction by label index
    long getInstructions(int frameId, int idx);

    // get play count by label index
    int getPc(int frameId, int idx);

    // set play count by label index
    void setPc(int frameId, int idx, int pc);

    // clear frames
    void clear();

    // get current module instance
    ModuleInstanceImpl getModule();

    void setModule(ModuleInstanceImpl module);

    default void close() {

    }
}
