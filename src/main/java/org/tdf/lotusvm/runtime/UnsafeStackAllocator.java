package org.tdf.lotusvm.runtime;

import org.tdf.lotusvm.types.InstructionPool;
import org.tdf.lotusvm.types.ValueType;

import static org.tdf.lotusvm.types.UnsafeUtil.UNSAFE;

public class UnsafeStackAllocator extends AbstractStackAllocator {
    private final long stackDataPtr;
    private final long labelDataPtr;
    private final long frameDataPtr;
    private final long offsetsPtr;

    private final long[] labels;

    // frame count
    private int count;

    private ValueType resultType;
    private long body;

    public UnsafeStackAllocator(int maxStackSize, int maxFrames, int maxLabelSize) {
        super(maxStackSize, maxFrames, maxLabelSize);

        this.labels = new long[maxLabelSize];

        this.stackDataPtr = UNSAFE.allocateMemory(fastMul8(maxStackSize));
        UNSAFE.setMemory(stackDataPtr, fastMul8(maxStackSize), (byte) 0);

        this.frameDataPtr = UNSAFE.allocateMemory(fastMul8(maxFrames));
        UNSAFE.setMemory(frameDataPtr, fastMul8(maxFrames), (byte) 0);

        this.offsetsPtr = UNSAFE.allocateMemory(fastMul8(maxFrames));
        UNSAFE.setMemory(offsetsPtr, fastMul8(maxFrames), (byte) 0);

        this.labelDataPtr = UNSAFE.allocateMemory(fastMul8(maxLabelSize));
        UNSAFE.setMemory(labelDataPtr, fastMul8(maxLabelSize), (byte) 0);
    }

    private static long fastMul8(int i) {
        if ((i & 0xe0000000) != 0)
            throw new RuntimeException("multiply overflow");
        return Integer.toUnsignedLong(i) << 3;
    }

    private void setLabels(int p, long instructions) {
        labels[p] = instructions;
    }

    private long getLabels(int p) {
        return labels[p];
    }

    private void setArity(int p, boolean arity) {
        UNSAFE.putByte(labelDataPtr + (fastMul8(p) | 6), (byte) (arity ? 1 : 0));
    }

    private boolean getArity(int p) {
        return UNSAFE.getByte(labelDataPtr + (fastMul8(p) | 6)) != 0;
    }

    private void setLoop(int p, boolean loop) {
        UNSAFE.putByte(labelDataPtr + (fastMul8(p) | 7), (byte) (loop ? 1 : 0));
    }

    private boolean getLoop(int p) {
        return UNSAFE.getByte(labelDataPtr + (fastMul8(p) | 7)) != 0;
    }

    private void setLabelPc(int p, int pc) {
        UNSAFE.putShort(
                labelDataPtr + (fastMul8(p) | 2), (short) pc
        );
    }

    private int getLabelPc(int p) {
        return UNSAFE.getShort(
                labelDataPtr + (fastMul8(p) | 2)
        );
    }

    private void setStackPc(int p, int pc) {
        UNSAFE.putShort(labelDataPtr + (fastMul8(p)), (short) pc);
    }

    private int getStackPc(int p) {
        return UNSAFE.getShort(labelDataPtr + (fastMul8(p)));
    }

    private void clearFrameData(int index) {
        UNSAFE.putLong(frameDataPtr + fastMul8(index), 0);
    }

    private long getStackData(int index) {
        return UNSAFE.getLong(stackDataPtr + fastMul8(index));
    }

    private void setStackData(int index, long data) {
        UNSAFE.putLong(stackDataPtr + fastMul8(index), data);
    }

    public int getLocalSize(int frameId) {
        return Short.toUnsignedInt(
                UNSAFE.getShort(frameDataPtr + (fastMul8(frameId) | 2))
        );
    }

    private void setLocalSize(int frameId, int functionIndex) {
        UNSAFE.putShort(frameDataPtr + (fastMul8(frameId) | 2), (short) functionIndex);
    }

    public int getStackSize(int frameId) {
        return Short.toUnsignedInt(
                UNSAFE.getShort(frameDataPtr + fastMul8(frameId))
        );
    }

    public void setStackSize(int frameId, int size) {
        UNSAFE.putShort(frameDataPtr + fastMul8(frameId), (short) size);
    }

    public int getLabelSize(int frameId) {
        return Short.toUnsignedInt(
                UNSAFE.getShort(
                        frameDataPtr + (fastMul8(frameId) | 4)
                )
        );
    }

    public void setLabelSize(int frameId, int size) {
        UNSAFE.putShort(frameDataPtr + (fastMul8(frameId) | 4), (short) size);
    }

    private int getFunctionIndex(int frameId) {
        return Short.toUnsignedInt(UNSAFE.getShort(frameDataPtr + (fastMul8(frameId) | 6)));
    }

    private void setFunctionIndex(int frameId, int functionIndex) {
        UNSAFE.putShort(frameDataPtr + (fastMul8(frameId) | 6), (short) functionIndex);
    }

    private void clearOffsets(int frameId) {
        UNSAFE.putLong(offsetsPtr + fastMul8(frameId), 0);
    }

    private void setStackBase(int frameId, int value) {
        UNSAFE.putInt(offsetsPtr + fastMul8(frameId), value);
    }

    private void setLabelBase(int frameId, int value) {
        UNSAFE.putInt(offsetsPtr + (fastMul8(frameId) | 4), value);
    }

    private int getStackBase(int frameId) {
        return UNSAFE.getInt(offsetsPtr + fastMul8(frameId));
    }

    private int getLabelBase(int frameId) {
        return UNSAFE.getInt(offsetsPtr + (fastMul8(frameId) | 4));
    }

    @Override
    public void pushExpression(long instructions, ValueType type) {
        if (count == maxFrames) {
            throw new RuntimeException("frame overflow");
        }
        int c = this.count;

        // clear
        clearFrameData(c);
        clearOffsets(c);

        // new stack base and new label base
        int newStackBase = c == 0 ? 0 : (getStackBase(c - 1) + getLocalSize(c - 1) + getStackSize(c - 1));
        int newLabelBase = c == 0 ? 0 : (getLabelBase(c - 1) + getLabelSize(c - 1));

        setStackBase(c, newStackBase);
        setLabelBase(c, newLabelBase);

        this.resultType = type;
        this.body = instructions;

        this.count++;
    }

    // when args = null,
    public int pushFrame(int functionIndex, long[] args) {
        if (count == maxFrames) {
            throw new RuntimeException("frame overflow");
        }


        // push locals
        if (functionIndex > 0xffff)
            throw new RuntimeException("function index overflow");

        int c = this.count;

        clearFrameData(c);
        clearOffsets(c);

        // new stack base and new label base
        int newStackBase = c == 0 ? 0 : (getStackBase(c - 1) + getLocalSize(c - 1) + getStackSize(c - 1));
        int newLabelBase = c == 0 ? 0 : (getLabelBase(c - 1) + getLabelSize(c - 1));

        setStackBase(c, newStackBase);
        setLabelBase(c, newLabelBase);

        WASMFunction func = (WASMFunction) ((functionIndex & TABLE_MASK) != 0 ?
                getModule().table.getFunctions()[(int) (functionIndex & FUNCTION_INDEX_MASK)] :
                getModule().functions.get((int) (functionIndex & FUNCTION_INDEX_MASK)));

        // set function index
        setFunctionIndex(c, functionIndex);

        // set local size
        int localSize = func.parametersLength() + func.getLocals();
        setLocalSize(c, localSize);

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

    private void decreaseStackSize(int frameId) {
        setStackSize(frameId, getStackSize(frameId) - 1);
    }

    private void increaseStackSize(int frameId) {
        setStackSize(frameId, getStackSize(frameId) + 1);
    }

    private void increaseLabelSize(int frameId) {
        setLabelSize(frameId, getLabelSize(frameId) + 1);
    }

    private void decreaseLabelSize(int frameId) {
        setLabelSize(frameId, getLabelSize(frameId) - 1);
    }

    @Override
    public void setLocal(int frameId, int index, long value) {
        if (index >= getLocalSize(frameId))
            throw new RuntimeException("local variable overflow");
        int base = getStackBase(frameId);
        setStackData(base + index, value);
    }

    @Override
    public void push(int frameId, long value) {
        int base = getStackBase(frameId) + getLocalSize(frameId);
        int stackSize = getStackSize(frameId);
        setStackData(base + stackSize, value);
        increaseStackSize(frameId);
    }

    @Override
    public long pop(int frameId) {
        int base = getStackBase(frameId) + getLocalSize(frameId);
        int size = getStackSize(frameId);
        if (size == 0)
            throw new RuntimeException("stack underflow");
        long v = getStackData(base + size - 1);
        decreaseStackSize(frameId);
        return v;
    }

    @Override
    public long getUnchecked(int index) {
        return getStackData(index);
    }

    @Override
    public long getLocal(int frameId, int index) {
        int base = getStackBase(frameId);
        return getStackData(base + index);
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
        if (frameId > 0)
            resetBody(getFunctionIndex(frameId - 1));
    }


    @Override
    public void pushLabel(int frameId, boolean arity, long body, boolean loop) {
        int size = getLabelSize(frameId);
        int base = getLabelBase(frameId);

        int p = base + size;
        setLabels(p, body);
        setArity(p, arity);
        setLoop(p, loop);
        setLabelPc(p, 0);

        int stackSize = getStackSize(frameId);
        setStackPc(p, stackSize);

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
                getStackPc(base + size - 1)
        );
        decreaseLabelSize(frameId);
    }

    @Override
    public void branch(int frameId, int l) {
        int idx = getLabelSize(frameId) - 1 - l;
        int p = getLabelBase(frameId) + idx;

        boolean arity = getArity(p);
        long val = arity ? pop(frameId) : 0;
        // Repeat l+1 times
        for (int i = 0; i < l + 1; i++) {
            popAndClearLabel(frameId);
        }
        if (arity) {
            push(frameId, val);
        }
        boolean loop = getLoop(p);
        setLabelPc(p, 0);
        if (!loop) {
            setLabelPc(p, InstructionPool.getInstructionsSize(getLabels(p)));
        }
        int prevPc = getLabelPc(p);
        pushLabel(
                frameId,
                arity,
                getLabels(p),
                loop
        );
        p = getLabelBase(frameId) + getLabelSize(frameId) - 1;
        setLabelPc(p, prevPc);
    }

    @Override
    public long getInstructions(int frameId, int idx) {
        int size = getLabelSize(frameId);
        if (idx < 0 || idx >= size)
            throw new RuntimeException("label index overflow");
        int base = getLabelBase(frameId);
        return getLabels(base + idx);
    }

    @Override
    public int getPc(int frameId, int idx) {
        int size = getLabelSize(frameId);
        if (idx < 0 || idx >= size)
            throw new RuntimeException("label index overflow");
        int p = getLabelBase(frameId) + idx;
        return getLabelPc(p);
    }

    @Override
    public void setPc(int frameId, int idx, int pc) {
        int size = getLabelSize(frameId);
        if (idx < 0 || idx >= size)
            throw new RuntimeException("label index overflow");
        int p = getLabelBase(frameId) + idx;
        setLabelPc(p, pc);
    }

    @Override
    public void clear() {
        this.count = 0;
    }

    @Override
    public void close() {
        UNSAFE.freeMemory(stackDataPtr);
        UNSAFE.freeMemory(labelDataPtr);
        UNSAFE.freeMemory(frameDataPtr);
        UNSAFE.freeMemory(offsetsPtr);
    }

    @Override
    public boolean isEmpty() {
        return count == 0;
    }

    @Override
    public int current() {
        if (count == 0)
            throw new RuntimeException("frame underflow");
        return count - 1;
    }

    @Override
    protected long getBody() {
        return body;
    }

    @Override
    protected ValueType getResultType() {
        return resultType;
    }
}
