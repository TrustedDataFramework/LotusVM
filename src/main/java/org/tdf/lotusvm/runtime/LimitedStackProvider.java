package org.tdf.lotusvm.runtime;


public class LimitedStackProvider implements StackProvider{
    private final int maxStackSize;
    private final int maxFrames;
    private long[] stackData;
    private int[] stackLengths;
    private int[] localLengths;
    private int count;

    public LimitedStackProvider(int maxStackSize, int maxFrames) {
        this.maxStackSize = maxStackSize;
        this.maxFrames = maxFrames;
        this.stackData = new long[maxStackSize * maxFrames];
        this.stackLengths = new int[maxFrames];
        this.localLengths = new int[maxFrames];
    }


    @Override
    public int create() {
        return count++;
    }

    @Override
    public void pushLocal(int stackId, long value) {
        int stackLength = stackLengths[stackId];
        if(stackLength > 0)
            throw new RuntimeException("push local failed: stack is not empty");
        int base = stackId * maxStackSize;
        int localLength = localLengths[stackId];
        stackData[base + localLength] = value;
        localLengths[stackId]++;
    }

    @Override
    public void setLocal(int stackId, int index, long value) {
        if(index >= localLengths[stackId])
            throw new RuntimeException("local variable overflow");
        int base = stackId * maxStackSize;
        stackData[base + index] = value;
    }

    @Override
    public void push(int stackId, long value) {
        int base = stackId * maxStackSize + localLengths[stackId];
        if(stackLengths[stackId] == maxStackSize) {
            throw new RuntimeException("stack overflow");
        }
        stackData[base + stackLengths[stackId]] = value;
        stackLengths[stackId]++;
    }

    @Override
    public long pop(int stackId) {
        int base = stackId * maxStackSize + localLengths[stackId];
        if(stackLengths[stackId] == 0)
            throw new RuntimeException("stack underflow");
        long v = stackData[base + stackLengths[stackId] - 1];
        stackLengths[stackId]--;
        return v;
    }

    @Override
    public long getUnchecked(int stackId, int index) {
        int base = stackId * maxStackSize + localLengths[stackId];
        return stackData[base + index];
    }

    @Override
    public long getLocal(int stackId, int index) {
        int base = stackId * maxStackSize;
        return stackData[base + index];
    }

    @Override
    public int popN(int stackId, int length) {
        if(stackLengths[stackId] < length)
            throw new RuntimeException("stack overflow");
        int r = stackLengths[stackId] - length;
        stackLengths[stackId] -= length;
        return r;
    }

    @Override
    public void drop(int stackId) {
        if(stackId != count - 1)
            throw new RuntimeException("stack should be dropped sequentially");
        count--;
        // clear
        localLengths[count] = 0;
        stackLengths[count] = 0;
        int base = maxStackSize * count;
        for(int i = base; i < base + maxStackSize; i++) {
            stackData[i] = 0L;
        }
    }

    @Override
    public int getStackSize(int stackId) {
        return stackLengths[stackId];
    }

    @Override
    public void setStackSize(int stackId, int size) {
        stackLengths[stackId] = size;
    }
}
