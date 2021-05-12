package org.tdf.lotusvm.runtime;


import org.tdf.lotusvm.types.Instruction;
import org.tdf.lotusvm.types.ValueType;

public class LimitedStackAllocator extends AbstractStackAllocator {
    private static final int MAX_SIZE_PER_FRAME = 0xffff;

    private static final long ARITY_MASK = 0x000000000000ff00L;
    private static final int ARITY_OFFSET = 8;

    private static final long LOOP_MASK = 0x00000000000000ffL;
    private static final int LOOP_OFFSET = 0;

    private static final long STACK_PC_MASK = 0xffff000000000000L;
    private static final int STACK_PC_OFFSET = 48;

    private static final long LABELS_PC_MASK = 0x0000ffff00000000L;
    private static final int LABELS_PC_OFFSET = 32;

    private static final long STACK_BASE_MASK = 0xffffffff00000000L;
    public static final int STACK_BASE_OFFSET = 32;

    private static final long LABEL_BASE_MASK = 0x00000000ffffffffL;
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
    // 4. function index (two byte)
    private final long[] frameData;

    // offset = stack offset + label offset
    private final long[] offsets;

    // stack id -> position
    private final Instruction[][] labels;

    // frame count
    private int count;

    private ValueType resultType;
    private Instruction[] body;

    @Override
    protected Instruction[] getBody() {
        return body;
    }

    @Override
    protected ValueType getResultType() {
        return resultType;
    }

    public LimitedStackAllocator(int maxStackSize, int maxFrames, int maxLabelSize) {
        if (maxStackSize <= 0 || maxFrames <= 0 || maxLabelSize <= 0)
            throw new RuntimeException("invalid limits <= 0" + maxStackSize + " " + maxFrames + " " + maxLabelSize);
        this.offsets = new long[maxFrames];
        this.stackData = new long[maxStackSize];
        this.frameData = new long[maxFrames];
        this.labelData = new long[maxLabelSize];
        this.labels = new Instruction[maxLabelSize][];
    }


    @Override
    public void pushExpression(Instruction[] instructions, ValueType type) {
        if (count == offsets.length) {
            throw new RuntimeException("frame overflow");
        }
        int c = this.count;
        // new stack base and new label base
        int newStackBase = c == 0 ? 0 : (getStackBase(c - 1) + getLocalSize(c - 1) + getStackSize(c - 1));
        int newLabelBase = c == 0 ? 0 : (getLabelBase(c - 1) + getLabelSize(c - 1));

        offsets[c]
            = (Integer.toUnsignedLong(newStackBase) << STACK_BASE_OFFSET) |
            (Integer.toUnsignedLong(newLabelBase) << LABEL_BASE_OFFSET);

        this.resultType = type;
        this.body = instructions;

        this.count++;
    }

    // when args = null,
    public int pushFrame(int functionIndex, long[] args) {
        if (count == offsets.length) {
            throw new RuntimeException("frame overflow");
        }
        // push locals
        if (functionIndex > 0xffff)
            throw new RuntimeException("function index overflow");

        int c = this.count;
        // new stack base and new label base
        int newStackBase = c == 0 ? 0 : (getStackBase(c - 1) + getLocalSize(c - 1) + getStackSize(c - 1));
        int newLabelBase = c == 0 ? 0 : (getLabelBase(c - 1) + getLabelSize(c - 1));
        offsets[c]
            = (Integer.toUnsignedLong(newStackBase) << STACK_BASE_OFFSET) |
            (Integer.toUnsignedLong(newLabelBase) << LABEL_BASE_OFFSET);

        WASMFunction func = (WASMFunction) ((functionIndex & TABLE_MASK) != 0 ?
            getModule().table.getFunctions()[(int) (functionIndex & FUNCTION_INDEX_MASK)] :
            getModule().functions.get((int) (functionIndex & FUNCTION_INDEX_MASK)));

        // set function index
        frameData[c] = functionIndex;

        // set local size
        int localSize = func.parametersLength() + func.getLocals();
        frameData[c] &= ~LOCAL_SIZE_MASK;
        frameData[c] |= Integer.toUnsignedLong(localSize) << LOCAL_SIZE_OFFSET;
        // set body and value type

        body = func.getBody();
        // set value type
        resultType = func.getType().getResultTypes().isEmpty() ? null : func.getType().getResultTypes().get(0);

        if (args == null) {
            int start = popN(c - 1, func.parametersLength());

            for (int i = 0; i < localSize; i++) {
                setLocal(c, i, i < func.parametersLength() ? getUnchecked(start + i) : 0);
            }
        } else {
            for (int i = 0; i < localSize; i++) {
                setLocal(c, i, i < args.length ? args[i] : 0);
            }
        }
        this.count++;
        return current();
    }

    private void resetBody(int functionIndex) {
        WASMFunction func = (WASMFunction) ((functionIndex & TABLE_MASK) != 0 ?
            getModule().table.getFunctions()[(int) (functionIndex & FUNCTION_INDEX_MASK)] :
            getModule().functions.get((int) (functionIndex & FUNCTION_INDEX_MASK)));

        this.body = func.getBody();
        this.resultType = func.getType().getResultTypes().isEmpty() ? null : func.getType().getResultTypes().get(0);
    }

    private int getStackBase(int frameId) {
        return (int) ((offsets[frameId] & STACK_BASE_MASK) >>> STACK_BASE_OFFSET);
    }

    private int getLabelBase(int frameId) {
        return (int) ((offsets[frameId] & LABEL_BASE_MASK) >>> LABEL_BASE_OFFSET);
    }

    private int getLocalSize(int frameId) {
        return (int) ((frameData[frameId] & LOCAL_SIZE_MASK) >>> LOCAL_SIZE_OFFSET);
    }

    private void decreaseStackSize(int frameId) {
        int before = (int) ((frameData[frameId] & STACK_SIZE_MASK) >>> STACK_SIZE_OFFSET);
        frameData[frameId] &= ~STACK_SIZE_MASK;
        frameData[frameId] |= Integer.toUnsignedLong(before - 1) << STACK_SIZE_OFFSET;
    }

    private void increaseLocalSize(int frameId) {
        int before = (int) ((frameData[frameId] & LOCAL_SIZE_MASK) >>> LOCAL_SIZE_OFFSET);
        frameData[frameId] &= ~LOCAL_SIZE_MASK;
        frameData[frameId] |= (Integer.toUnsignedLong(before + 1) << LOCAL_SIZE_OFFSET);
    }

    private void increaseStackSize(int frameId) {
        int before = (int) ((frameData[frameId] & STACK_SIZE_MASK) >>> STACK_SIZE_OFFSET);
        frameData[frameId] &= ~STACK_SIZE_MASK;
        frameData[frameId] |= Integer.toUnsignedLong(before + 1) << STACK_SIZE_OFFSET;
    }

    private void increaseLabelSize(int frameId) {
        int before = (int) ((frameData[frameId] & LABEL_SIZE_MASK) >>> LABEL_SIZE_OFFSET);
        frameData[frameId] &= ~LABEL_SIZE_MASK;
        frameData[frameId] |= Integer.toUnsignedLong(before + 1) << LABEL_SIZE_OFFSET;
    }

    private void decreaseLabelSize(int frameId) {
        int before = (int) ((frameData[frameId] & LABEL_SIZE_MASK) >>> LABEL_SIZE_OFFSET);
        frameData[frameId] &= ~LABEL_SIZE_MASK;
        frameData[frameId] |= Integer.toUnsignedLong(before - 1) << LABEL_SIZE_OFFSET;
    }

    @Override
    public void setLocal(int frameId, int index, long value) {
        if (index >= getLocalSize(frameId))
            throw new RuntimeException("local variable overflow");
        int base = getStackBase(frameId);
        stackData[base + index] = value;
    }

    @Override
    public void push(int frameId, long value) {
        int base = getStackBase(frameId) + getLocalSize(frameId);
        int stackSize = getStackSize(frameId);
        if (stackSize == MAX_SIZE_PER_FRAME)
            throw new RuntimeException("frame's stack overflow");
        stackData[base + stackSize] = value;
        increaseStackSize(frameId);
    }

    @Override
    public long pop(int frameId) {
        int base = getStackBase(frameId) + getLocalSize(frameId);
        int size = getStackSize(frameId);
        if (size == 0)
            throw new RuntimeException("stack underflow");
        long v = stackData[base + size - 1];
        decreaseStackSize(frameId);
        return v;
    }

    @Override
    public long getUnchecked(int index) {
        return stackData[index];
    }

    @Override
    public long getLocal(int frameId, int index) {
        int base = getStackBase(frameId);
        return stackData[base + index];
    }

    @Override
    public int popN(int frameId, int length) {
        if (length == 0)
            return 0;

        int size = getStackSize(frameId);
        if (size < length)
            throw new RuntimeException("stack underflow");
        int r = getStackBase(frameId) + getLocalSize(frameId) + size - length;
        setStackSize(frameId, size - length);
        return r;
    }

    @Override
    public void drop(int frameId) {
        if (frameId != current())
            throw new RuntimeException("stack should be dropped sequentially");
        count--;
        // clear
        frameData[frameId] = 0;
        offsets[frameId] = 0;
        if (frameId > 0)
            resetBody((int) (frameData[current()] & 0xffff));
    }

    @Override
    public int getStackSize(int frameId) {
        return (int) ((frameData[frameId] & STACK_SIZE_MASK) >>> STACK_SIZE_OFFSET);
    }

    @Override
    public void setStackSize(int frameId, int size) {
        frameData[frameId] &= ~STACK_SIZE_MASK;
        frameData[frameId] |= Integer.toUnsignedLong(size) << STACK_SIZE_OFFSET;
    }

    @Override
    public void pushLabel(int frameId, boolean arity, Instruction[] body, boolean loop) {
        int size = getLabelSize(frameId);
        int base = getLabelBase(frameId);

        if (size == MAX_SIZE_PER_FRAME)
            throw new RuntimeException("frame's label overflow");

        int p = base + size;
        labels[p] = body;
        labelData[p] &= ~ARITY_MASK;
        labelData[p] |= (arity ? 1L : 0L) << ARITY_OFFSET;
        labelData[p] &= ~LOOP_MASK;
        labelData[p] |= (loop ? 1L : 0L) << LOOP_OFFSET;
        labelData[p] &= ~LABELS_PC_MASK;

        int stackSize = getStackSize(frameId);
        labelData[p] &= ~STACK_PC_MASK;

        labelData[p] |= Integer.toUnsignedLong(stackSize) << STACK_PC_OFFSET;

        increaseLabelSize(frameId);
    }

    @Override
    public void popLabel(int frameId) {
        if (getLabelSize(frameId) == 0)
            throw new RuntimeException("label underflow");
        decreaseLabelSize(frameId);
    }

    @Override
    public void popAndClearLabel(int frameId) {
        int size = getLabelSize(frameId);
        if (size == 0)
            throw new RuntimeException("label underflow");
        int base = getLabelBase(frameId);
        setStackSize(
            frameId,
            (int) ((this.labelData[base + size - 1] & STACK_PC_MASK) >>> STACK_PC_OFFSET)
        );
        decreaseLabelSize(frameId);
    }

    @Override
    public void branch(int frameId, int l) {
        int idx = getLabelSize(frameId) - 1 - l;
        int p = getLabelBase(frameId) + idx;

        boolean arity = (labelData[p] & ARITY_MASK) != 0;
        long val = arity ? pop(frameId) : 0;
        // Repeat l+1 times
        for (int i = 0; i < l + 1; i++) {
            popAndClearLabel(frameId);
        }
        if (arity) {
            push(frameId, val);
        }
        boolean loop = (labelData[p] & LOOP_MASK) != 0;
        labelData[p] &= ~LABELS_PC_MASK;
        if (!loop) {
            long prev = Integer.toUnsignedLong(labels[p].length);
            labelData[p] |= prev << LABELS_PC_OFFSET;
        }
        int prevPc = (int) ((labelData[p] & LABELS_PC_MASK) >>> LABELS_PC_OFFSET);
        pushLabel(
            frameId,
            arity,
            labels[p],
            loop
        );
        p = getLabelBase(frameId) + getLabelSize(frameId) - 1;
        labelData[p] &= ~LABELS_PC_MASK;
        labelData[p] |= Integer.toUnsignedLong(prevPc) << LABELS_PC_OFFSET;
    }

    @Override
    public int getLabelSize(int frameId) {
        return (int) ((frameData[frameId] & LABEL_SIZE_MASK) >>> LABEL_SIZE_OFFSET);
    }

    @Override
    public Instruction[] getInstructions(int frameId, int idx) {
        int size = getLabelSize(frameId);
        if (idx < 0 || idx >= size)
            throw new RuntimeException("label index overflow");
        int base = getLabelBase(frameId);
        return labels[base + idx];
    }

    @Override
    public int getPc(int frameId, int idx) {
        int size = getLabelSize(frameId);
        if (idx < 0 || idx >= size)
            throw new RuntimeException("label index overflow");
        int p = getLabelBase(frameId) + idx;
        return (int) ((labelData[p] & LABELS_PC_MASK) >>> LABELS_PC_OFFSET);
    }

    @Override
    public void setPc(int frameId, int idx, int pc) {
        int size = getLabelSize(frameId);
        if (idx < 0 || idx >= size)
            throw new RuntimeException("label index overflow");
        int p = getLabelBase(frameId) + idx;
        labelData[p] &= ~LABELS_PC_MASK;
        labelData[p] |= (Integer.toUnsignedLong(pc) << LABELS_PC_OFFSET);
    }

    @Override
    public void clear() {
        this.count = 0;
        for(int i = 0; i < frameData.length; i++) {
            frameData[i] = 0;
            offsets[i] = 0;
            labels[i] = null;
        }

        for(int i = 0; i < stackData.length; i++){
            stackData[i] = 0;
        }

        for(int i = 0; i < labelData.length; i++){
            labelData[i] = 0;
        }
    }

    @Override
    public int current() {
        if (count == 0)
            throw new RuntimeException("frame underflow");
        return count - 1;
    }
}
