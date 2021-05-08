package org.tdf.lotusvm.runtime;

import java.util.ArrayList;
import java.util.List;

public class BaseStackProvider implements StackProvider{
    public static int DEFAULT_INITIAL_STACK_CAP = 8;
    private List<Integer> stackLengths = new ArrayList<>();
    private List<Integer> localLengths = new ArrayList<>();

    private List<long[]> stackData = new ArrayList<>();
    private List<long[]> locals = new ArrayList<>();
    private int count = 0;

    @Override
    public int create() {
        int id = count++;
        stackData.add(new long[DEFAULT_INITIAL_STACK_CAP]);
        locals.add(new long[DEFAULT_INITIAL_STACK_CAP]);
        this.stackLengths.add(0);
        this.localLengths.add(0);
        return id;
    }

    private void tryGrowStack(int stackId) {
        int size = stackLengths.get(stackId);
        long[] stackData = this.stackData.get(stackId);
        if (size >= stackData.length) {
            long[] tmp = new long[stackData.length * 2 + 1];
            System.arraycopy(stackData, 0, tmp, 0, stackData.length);
            this.stackData.set(stackId, tmp);
        }
    }

    private void tryGrowLocal(int stackId) {
        int size = this.localLengths.get(stackId);
        long[] data = this.locals.get(stackId);

        if (size >= data.length) {
            long[] tmp = new long[data.length * 2 + 1];
            System.arraycopy(data, 0, tmp, 0, data.length);
            this.locals.set(stackId, tmp);
        }
    }

    @Override
    public void setLocal(int stackId, int index, long value) {
        int size = this.localLengths.get(stackId);
        long[] data = this.locals.get(stackId);
        if(index >= size)
            throw new RuntimeException("local variable overflow");
        data[index] = value;
    }

    @Override
    public void pushLocal(int stackId, long value) {
        tryGrowLocal(stackId);
        int size = this.localLengths.get(stackId);
        long[] data = this.locals.get(stackId);
        data[size] = value;
        this.localLengths.set(stackId, size + 1);
    }

    @Override
    public void push(int stackId, long value) {
        tryGrowStack(stackId);
        int size = this.stackLengths.get(stackId);
        long[] data = this.stackData.get(stackId);
        data[size] = value;
        this.stackLengths.set(stackId, size + 1);
    }

    @Override
    public long pop(int stackId) {
        int size = this.stackLengths.get(stackId);
        long[] data = this.stackData.get(stackId);
        this.stackLengths.set(stackId, size - 1);
        if(size - 1 < 0)
            throw new RuntimeException("stack underflow");
        return data[size - 1];
    }

    @Override
    public long getUnchecked(int stackId, int index) {
        long[] data = this.stackData.get(stackId);
        return data[index];
    }

    @Override
    public long getLocal(int stackId, int index) {
        int size = this.localLengths.get(stackId);
        if(index < 0)
            throw new RuntimeException("stack underflow");
        if(index >= size)
            throw new RuntimeException("stack overflow");
        long[] data = this.locals.get(stackId);
        return data[index];
    }

    @Override
    public int popN(int stackId, int length) {
        if(length == 0)
            return 0;
        int size = this.stackLengths.get(stackId);
        if(size < length)
            throw new RuntimeException("stack overflow");
        this.stackLengths.set(stackId, size - length);
        return size - length;
    }

    @Override
    public void drop(int stackId) {
        if(stackId != count - 1)
            throw new RuntimeException("stack should be dropped sequentially");
        count--;
        stackLengths.remove(count);
        localLengths.remove(count);
        stackData.remove(count);
        locals.remove(count);
    }

    @Override
    public int getStackSize(int stackId) {
        return stackLengths.get(stackId);
    }

    public void setStackSize(int stackId, int size) {
        stackLengths.set(stackId, size);
    }
}
