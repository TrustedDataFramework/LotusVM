package org.tdf.lotusvm.runtime;

import org.tdf.lotusvm.types.Instruction;

// stack provider to avoid array create
public interface StackAllocator {
    // create a stack, return the stackId
    int create();

    // push local variable into stack
    void pushLocal(int stackId, long value);

    void setLocal(int stackId, int index, long value);

    // push a value into a stack
    void push(int stackId, long value);

    // pop a value from a stack
    long pop(int stackId);

    // get from stack by index, unchecked
    long getUnchecked(int index);

    // get local from stack by index
    long getLocal(int stackId, int index);

    // pop n value into target stack
    // the return value is the offset of stack
    int popN(int stackId, int length);

    // drop the stack
    void drop(int stackId);

    int getStackSize(int stackId);

    void setStackSize(int stackId, int size);

    void pushLabel(int stackId, boolean arity, Instruction[] body, boolean loop);

    void popLabel(int stackId);

    void popAndClearLabel(int stackId);

    void branch(int stackId, int l);

    int getLabelSize(int stackId);

    Instruction[] getInstructions(int stackId, int idx);

    int getPc(int stackId, int idx);

    void setPc(int stackId, int idx, int pc);

    void clear();
}
