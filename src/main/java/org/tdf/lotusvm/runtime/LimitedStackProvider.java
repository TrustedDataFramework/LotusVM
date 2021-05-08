package org.tdf.lotusvm.runtime;


import org.tdf.lotusvm.types.Instruction;

public class LimitedStackProvider implements StackProvider{
    private static final long ARITY_MASK =     0x000000000000ff00L;
    private static final int ARITY_OFFSET =    8;

    private static final long LOOP_MASK =      0x00000000000000ffL;
    private static final int  LOOP_OFFSET = 0;

    private static final long STACK_PC_MASK =  0xffff000000000000L;
    private static final int STACK_PC_OFFSET = 48;

    private static final long LABELS_PC_MASK = 0x0000ffff00000000L;
    private static final int LABELS_PC_OFFSET = 32;


    private final int maxStackSize;
    private final int maxFrames;
    private final int maxLabelSize;

    private final long[] stackData;
    private final int[] stackLengths;
    private final int[] localLengths;
    private final long[] labelData;
    private final int[] labelLengths;

    // stack id -> position
    private final Instruction[][] labels;

    private int count;

    public LimitedStackProvider(int maxStackSize, int maxFrames, int maxLabelSize) {
        if(maxStackSize > (1 << 16))
            throw new RuntimeException("invalid max stack size, should less than or equals to " + (1 << 16));
        this.maxStackSize = maxStackSize;
        this.maxFrames = maxFrames;
        this.stackData = new long[maxStackSize * maxFrames];
        this.stackLengths = new int[maxFrames];
        this.localLengths = new int[maxFrames];
        this.labelData = new long[maxFrames * maxLabelSize];
        this.labelLengths = new int[maxFrames];
        this.labels = new Instruction[maxFrames * maxLabelSize][];
        this.maxLabelSize = maxLabelSize;
    }


    @Override
    public int create() {
        if(count == maxFrames) {
            throw new RuntimeException("frame overflow");
        }
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
        if(length == 0)
            return 0;
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
        labels[stackId] = null;
    }

    @Override
    public int getStackSize(int stackId) {
        return stackLengths[stackId];
    }

    @Override
    public void setStackSize(int stackId, int size) {
        stackLengths[stackId] = size;
    }

    @Override
    public void pushLabel(int stackId, boolean arity, Instruction[] body, boolean loop) {
        int size = labelLengths[stackId];
        int base = stackId * maxLabelSize;
        if (size == maxLabelSize)
            throw new RuntimeException("label overflow");

        int p = base + size;
        labels[p] = body;
        labelData[p] &= ~ARITY_MASK;
        labelData[p] |= (arity ? 1L : 0L) << ARITY_OFFSET;
        labelData[p] &= ~LOOP_MASK;
        labelData[p] |= (loop ? 1L : 0L) << LOOP_OFFSET;
        labelData[p] &= ~LABELS_PC_MASK;

        labelData[p] &= ~STACK_PC_MASK;
        int stackSize = getStackSize(stackId);
        labelData[p] |= Integer.toUnsignedLong(stackSize) << STACK_PC_OFFSET;
        localLengths[stackId]++;
    }

    @Override
    public void popLabel(int stackId) {
        this.labelLengths[stackId]--;
    }

    @Override
    public void popAndClearLabel(int stackId) {
        int size = labelLengths[stackId];
        setStackSize(
            stackId,
            (int) ((this.labelData[size - 1] & STACK_PC_MASK) >>> STACK_PC_OFFSET)
        );
        labelLengths[stackId]--;
    }

    @Override
    public void branch(int stackId, int l) {
        int idx = getLabelsLength(stackId) - 1 - l;
        int p = stackId * maxLabelSize + idx;

        boolean arity = (labelData[p] & ARITY_MASK) != 0;
        long val = arity? pop(stackId) : 0;
        // Repeat l+1 times
        for (int i = 0; i < l + 1; i++) {
            popAndClearLabel(stackId);
        }
        if (arity) {
            push(stackId, val);
        }
        boolean loop = (labelData[p] & LOOP_MASK) != 0;
        labelData[p] &= ~LABELS_PC_MASK;
        if (!loop) {
            long prev = Integer.toUnsignedLong(labels[p].length);
            if(prev > 0xffff)
                throw new RuntimeException("labels overflow");
            labelData[p] |= prev << LABELS_PC_OFFSET;
        }
        int prevPc = (int) ((labelData[p] & LABELS_PC_MASK) >>> LABELS_PC_OFFSET);
        pushLabel(
            stackId,
            arity,
            labels[p],
            loop
        );
        p = stackId * maxLabelSize + labelLengths[stackId] - 1;
        labelData[p] &= ~LABELS_PC_MASK;
        labelData[p] |= Integer.toUnsignedLong(prevPc) << LABELS_PC_OFFSET;
    }

    @Override
    public int getLabelsLength(int stackId) {
        return labelLengths[stackId];
    }

    @Override
    public Instruction[] getInstructions(int stackId, int idx) {
        int base = stackId * maxLabelSize;
        return labels[base + idx];
    }

    @Override
    public int getPc(int stackId, int idx) {
        int base = stackId * maxLabelSize;
        return (int) ((labelData[base + idx] & LABELS_PC_MASK) >>> LABELS_PC_OFFSET);
    }

    @Override
    public void setPc(int stackId, int idx, int pc) {
        int p = stackId * maxLabelSize + idx;
        labelData[p] &= ~LABELS_PC_MASK;
        labelData[p] |= Integer.toUnsignedLong(pc + 1) << LABELS_PC_OFFSET;
    }

}
