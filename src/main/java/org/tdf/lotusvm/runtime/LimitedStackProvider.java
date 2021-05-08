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

    private static final long STACK_BASE_MASK =  0xffffffff00000000L;
    public static final int STACK_BASE_OFFSET = 32;

    private static final long LABEL_BASE_MASK =  0x00000000ffffffffL;
    public static final int LABEL_BASE_OFFSET = 0;

    private static final long STACK_SIZE_MASK = 0xffff000000000000L;
    private static final long STACK_SIZE_OFFSET = 48;

    private static final long LOCAL_SIZE_MASK = 0x0000ffff00000000L;
    private static final long LOCAL_SIZE_OFFSET = 32;

    private static final long LABEL_SIZE_MASK = 0x00000000ffff0000L;
    private static final long LABEL_SIZE_OFFSET = 16;



    // offset = stack offset + label offset

    private final long[] stackData;

    private final long[] labelData;


    // frame data =
    // 1. stack size (two byte)
    // 2. local length (two byte)
    // 3. label length (two byte)
    private final long[] frameData;

    // offset = stack offset + label offset
    private final long[] offsets;

    // stack id -> position
    private final Instruction[][] labels;

    private int count;

    public LimitedStackProvider(int maxStackSize, int maxFrames, int maxLabelSize) {
        this.offsets = new long[maxFrames];
        this.stackData = new long[maxStackSize];

        this.frameData = new long[maxFrames];
        this.labelData = new long[maxLabelSize];

        this.labels = new Instruction[maxLabelSize][];
    }


    @Override
    public int create() {
        if(count == offsets.length) {
            throw new RuntimeException("frame overflow");
        }
        int c = this.count;
        // new stack base and new label base
        int newStackBase = c == 0 ? 0 : (getStackBase(c - 1) + getLocalSize(c - 1) + getStackSize(c - 1));
        int newLabelBase = c == 0 ? 0 : (getLabelBase(c - 1) + getLabelSize(c - 1));
        offsets[c]
            = (Integer.toUnsignedLong(newStackBase) << STACK_SIZE_OFFSET) |
            (Integer.toUnsignedLong(newLabelBase) << LABEL_BASE_OFFSET);
        count++;
        return c;
    }


    private int getStackBase(int stackId) {
        return (int) ((offsets[stackId] & STACK_BASE_MASK) >>> STACK_BASE_OFFSET);
    }

    private int getLabelBase(int stackId) {
        return (int) ((offsets[stackId] & LABEL_BASE_MASK) >>> LABEL_BASE_OFFSET);
    }

    private int getLocalSize(int stackId) {
        return (int) ((frameData[stackId] & LOCAL_SIZE_MASK) >>> LOCAL_SIZE_OFFSET);
    }

    private void decreaseStackSize(int stackId) {
        int before = (int) ((frameData[stackId] & STACK_SIZE_MASK) >>> STACK_SIZE_OFFSET);
        frameData[stackId] &= ~STACK_SIZE_MASK;
        frameData[stackId] |= Integer.toUnsignedLong(before - 1) << STACK_SIZE_OFFSET;
    }

    private void increaseLocalSize(int stackId) {
        int before = (int) ((frameData[stackId] & LOCAL_SIZE_MASK) >>> LOCAL_SIZE_OFFSET);
        frameData[stackId] &= ~LOCAL_SIZE_OFFSET;
        frameData[stackId] |= Integer.toUnsignedLong(before + 1) << LOCAL_SIZE_OFFSET;
    }

    private void increaseStackSize(int stackId) {
        int before = (int) ((frameData[stackId] & STACK_SIZE_MASK) >>> STACK_SIZE_OFFSET);
        frameData[stackId] &= ~STACK_SIZE_MASK;
        frameData[stackId] |= Integer.toUnsignedLong(before + 1) << STACK_SIZE_OFFSET;
    }

    private void increaseLabelSize(int stackId) {
        int before = (int) ((frameData[stackId] & LABEL_SIZE_MASK) >>> LABEL_SIZE_OFFSET);
        frameData[stackId] &= ~LABEL_SIZE_MASK;
        frameData[stackId] |= Integer.toUnsignedLong(before + 1) << LABEL_SIZE_OFFSET;
    }

    private void decreaseLabelSize(int stackId) {
        int before = (int) ((frameData[stackId] & LABEL_SIZE_MASK) >>> LABEL_SIZE_OFFSET);
        frameData[stackId] &= ~LABEL_SIZE_MASK;
        frameData[stackId] |= Integer.toUnsignedLong(before - 1) << LABEL_SIZE_OFFSET;
    }

    @Override
    public void pushLocal(int stackId, long value) {
        int stackSize = getStackSize(stackId);
        if(stackSize > 0)
            throw new RuntimeException("push local failed: stack is not empty");
        int base = getStackBase(stackId);
        int localSize = getLocalSize(stackId);
        stackData[base + localSize] = value;
        increaseLocalSize(stackId);
    }

    @Override
    public void setLocal(int stackId, int index, long value) {
        if(index >= getLocalSize(stackId))
            throw new RuntimeException("local variable overflow");
        int base = getStackBase(stackId);
        stackData[base + index] = value;
    }

    @Override
    public void push(int stackId, long value) {
        int base = getStackBase(stackId) + getLocalSize(stackId) ;
        stackData[base + getStackSize(stackId)] = value;
        increaseStackSize(stackId);
    }

    @Override
    public long pop(int stackId) {
        int base = getStackBase(stackId) + getLocalSize(stackId);
        int size = getStackSize(stackId);
        if(size == 0)
            throw new RuntimeException("stack underflow");
        long v = stackData[base + size - 1];
        decreaseStackSize(stackId);
        return v;
    }

    @Override
    public long getUnchecked(int index) {
        return stackData[index];
    }

    @Override
    public long getLocal(int stackId, int index) {
        int base = getStackBase(stackId);
        return stackData[base + index];
    }

    @Override
    public int popN(int stackId, int length) {
        if(length == 0)
            return 0;

        int size = getStackSize(stackId);
        if(size < length)
            throw new RuntimeException("stack overflow");
        int r = getStackBase(stackId) + size - length;
        setStackSize(stackId, size - length);
        return r;
    }

    @Override
    public void drop(int stackId) {
        if(stackId != count - 1)
            throw new RuntimeException("stack should be dropped sequentially");
        count--;
        // clear
        frameData[count] = 0;
        offsets[count] = 0;
    }

    @Override
    public int getStackSize(int stackId) {
        return (int) ((frameData[stackId] & STACK_SIZE_MASK) >>> STACK_SIZE_OFFSET);
    }

    @Override
    public void setStackSize(int stackId, int size) {
        frameData[stackId] &= ~STACK_SIZE_MASK;
        frameData[stackId] |= Integer.toUnsignedLong(size) << STACK_SIZE_OFFSET;
    }

    @Override
    public void pushLabel(int stackId, boolean arity, Instruction[] body, boolean loop) {
        int size = getLabelSize(stackId);
        int base = getLabelBase(stackId);

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

        increaseLabelSize(stackId);
    }

    @Override
    public void popLabel(int stackId) {
        if(getLabelSize(stackId) == 0)
            throw new RuntimeException("label underflow");
        decreaseLabelSize(stackId);
    }

    @Override
    public void popAndClearLabel(int stackId) {
        int size = getLabelSize(stackId);
        if(size == 0)
            throw new RuntimeException("label underflow");
        int base = getLabelBase(stackId);
        setStackSize(
            stackId,
            (int) ((this.labelData[base + size - 1] & STACK_PC_MASK) >>> STACK_PC_OFFSET)
        );
        decreaseLabelSize(stackId);
    }

    @Override
    public void branch(int stackId, int l) {
        int idx = getLabelSize(stackId) - 1 - l;
        int p = getLabelBase(stackId) + idx;

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
            labelData[p] |= prev << LABELS_PC_OFFSET;
        }
        int prevPc = (int) ((labelData[p] & LABELS_PC_MASK) >>> LABELS_PC_OFFSET);
        pushLabel(
            stackId,
            arity,
            labels[p],
            loop
        );
        p = getLabelBase(stackId) + getLabelSize(stackId) - 1;
        labelData[p] &= ~LABELS_PC_MASK;
        labelData[p] |= Integer.toUnsignedLong(prevPc) << LABELS_PC_OFFSET;
    }

    @Override
    public int getLabelSize(int stackId) {
        return (int) ((frameData[stackId] & LABEL_SIZE_MASK) >> LABEL_SIZE_OFFSET);
    }

    @Override
    public Instruction[] getInstructions(int stackId, int idx) {
        int base = getLabelBase(stackId);
        return labels[base + idx];
    }

    @Override
    public int getPc(int stackId, int idx) {
        int p = getLabelBase(stackId) + idx;
        return (int) ((labelData[p] & LABELS_PC_MASK) >>> LABELS_PC_OFFSET);
    }

    @Override
    public void setPc(int stackId, int idx, int pc) {
        int p = getLabelBase(stackId) + idx;
        labelData[p] &= ~LABELS_PC_MASK;
        labelData[p] |= (Integer.toUnsignedLong(pc) << LABELS_PC_OFFSET);
    }

}
