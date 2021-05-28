package org.tdf.lotusvm.runtime;

import org.tdf.lotusvm.types.InstructionPool;
import org.tdf.lotusvm.types.LongBuffer;
import org.tdf.lotusvm.types.UnsafeLongBuffer;
import org.tdf.lotusvm.types.ValueType;

import static org.tdf.lotusvm.types.UnsafeUtil.MAX_UNSIGNED_SHORT;
import static org.tdf.lotusvm.types.UnsafeUtil.UNSAFE;

public class UnsafeStackAllocator extends AbstractStackAllocator {
    private static final long ARITY_OFFSET = 6L;
    private static final long LOOP_OFFSET = 7L;
    private static final long LABEL_PC_OFFSET = 2L;

    private final LongBuffer stackData;

    // label data = stack pc (2byte) | label pc (2byte) | 0x00  | 0x00  | arity (1byte) | loop (1byte)
    private final long labelDataPtr;

    // frame data = stack size (2byte) | local size (2byte) | label size (2byte) | function index (2byte)
    private final LongBuffer frameData;

    // offsets ptr = stack base (4byte) in stack data | label base (4byte) in labels
    private final LongBuffer offsets;

    // = label array id in instruction pool = (label size (4byte) | label offset (4byte))
    private final LongBuffer labels;

    // frame count
    private int count;

    private ValueType resultType;
    private long body;

    private int labelSize;
    private int localSize;
    private int stackSize;
    private int functionIndex;

    private int stackBase;
    private int labelBase;

    public UnsafeStackAllocator(int maxStackSize, int maxFrames, int maxLabelSize) {
        super(maxStackSize, maxFrames, maxLabelSize);

        this.labels = new UnsafeLongBuffer(maxLabelSize);
        this.labels.setSize(maxLabelSize);

        this.stackData = new UnsafeLongBuffer(maxStackSize);
        this.stackData.setSize(maxStackSize);

        this.frameData = new UnsafeLongBuffer(maxFrames);
        this.frameData.setSize(maxFrames);

        this.offsets = new UnsafeLongBuffer(maxFrames);
        this.offsets.setSize(maxFrames);

        this.labelDataPtr = UNSAFE.allocateMemory((maxLabelSize * 8L));
        UNSAFE.setMemory(labelDataPtr, (maxLabelSize * 8L), (byte) 0);
    }


    private void setLabels(int p, long instructions) {
        labels.set(p, instructions);
    }

    private long getLabels(int p) {
        return labels.get(p);
    }

    private void setArity(int p, boolean arity) {
        UNSAFE.putByte(labelDataPtr + ((p * 8L) | ARITY_OFFSET), (byte) (arity ? 1 : 0));
    }

    private boolean getArity(int p) {
        return UNSAFE.getByte(labelDataPtr + ((p * 8L) | ARITY_OFFSET)) != 0;
    }

    private void setLoop(int p, boolean loop) {
        UNSAFE.putByte(labelDataPtr + ((p * 8L) | LOOP_OFFSET), (byte) (loop ? 1 : 0));
    }

    private boolean getLoop(int p) {
        return UNSAFE.getByte(labelDataPtr + ((p * 8L) | LOOP_OFFSET)) != 0;
    }

    private void setLabelPc(int p, int pc) {
        UNSAFE.putShort(
            labelDataPtr + ((p * 8L) | LABEL_PC_OFFSET), (short) pc
        );
    }

    private int getLabelPc(int p) {
        return UNSAFE.getShort(
            labelDataPtr + ((p * 8L) | LABEL_PC_OFFSET)
        ) & MAX_UNSIGNED_SHORT;
    }

    private void setStackPc(int p, int pc) {
        UNSAFE.putShort(labelDataPtr + ((p * 8L)), (short) pc);
    }

    private int getStackPc(int p) {
        return UNSAFE.getShort(labelDataPtr + ((p * 8L))) & MAX_UNSIGNED_SHORT;
    }

    private long getStackData(int index) {
        return stackData.get(index);
    }

    private void setStackData(int index, long data) {
        stackData.set(index, data);
    }


    public int getLabelSize() {
        return labelSize;
    }


    private void clearFrameData() {
        this.labelSize = 0;
        this.stackSize = 0;
        this.functionIndex = 0;
        this.localSize = 0;
    }

    @Override
    public void pushExpression(long instructions, ValueType type) {
        if (count == maxFrames) {
            throw new RuntimeException("frame overflow");
        }
        int c = this.count;

        // clear
        if (c != 0) {
            storeCurrentFrame();
        }

        int newStackBase = 0;
        int newLabelBase = 0;

        if (c != 0) {
            newStackBase = this.stackBase + this.localSize + this.stackSize;
            newLabelBase = this.labelBase + this.labelSize;
        }


        // new stack base and new label base
        clearFrameData();

        this.stackBase = newStackBase;
        this.labelBase = newLabelBase;

        this.resultType = type;
        this.body = instructions;

        this.count++;
    }

    // when args = null,

    private void storeCurrentFrame() {
        long frameId = FrameId.setFunctionIndex(0L, functionIndex);
        frameId = FrameId.setStackSize(frameId, stackSize);
        frameId = FrameId.setLabelSize(frameId, labelSize);
        frameId = FrameId.setLocalSize(frameId, localSize);
        long offset = FrameDataOffset.setLabelBase(0L, labelBase);
        offset = FrameDataOffset.setStackBase(offset, stackBase);
        frameData.set(currentFrameIndex(), frameId);
        offsets.set(currentFrameIndex(), offset);
    }

    public int currentFrameIndex() {
        return this.count - 1;
    }

    public void pushFrame(int functionIndex, long[] args) {
        if (count == maxFrames) {
            throw new RuntimeException("frame overflow");
        }


        // push locals
        if (functionIndex > MAX_UNSIGNED_SHORT)
            throw new RuntimeException("function index overflow");

        int c = this.count;

        if (c != 0) {
            storeCurrentFrame();
        }

        this.functionIndex = functionIndex;

        // new stack base and new label base
        int newStackBase = 0;
        int newLabelBase = 0;


        if (c != 0) {
            newStackBase = this.stackBase + this.localSize + this.stackSize;
            newLabelBase = this.labelBase + this.labelSize;
        }

        this.labelSize = 0;
        this.stackSize = 0;

        this.stackBase = newStackBase;
        this.labelBase = newLabelBase;


        WASMFunction func = (WASMFunction) ((functionIndex & TABLE_MASK) != 0 ?
            getModule().table.getFunctions()[(int) (functionIndex & FUNCTION_INDEX_MASK)] :
            getModule().functions.get((int) (functionIndex & FUNCTION_INDEX_MASK)));

        // set local size
        this.localSize = func.parametersLength() + func.getLocals();

        // set body and value type
        body = func.getBody();
        // set value type
        resultType = func.getType().getResultTypes().isEmpty() ? null : func.getType().getResultTypes().get(0);

        this.count++;
        if (args == null) {
            int start = popN(currentFrameIndex() - 1, func.parametersLength());
            for (int i = 0; i < localSize; i++) {
                setLocal(i, i < func.parametersLength() ? getUnchecked(start + i) : 0);
            }
        } else {
            for (int i = 0; i < localSize; i++) {
                setLocal(i, i < args.length ? args[i] : 0);
            }
        }
    }

    private void resetBody(int functionIndex) {
        WASMFunction func = (WASMFunction) ((functionIndex & TABLE_MASK) != 0 ?
            getModule().table.getFunctions()[(int) (functionIndex & FUNCTION_INDEX_MASK)] :
            getModule().functions.get((int) (functionIndex & FUNCTION_INDEX_MASK)));

        this.body = func.getBody();
        this.resultType = func.getType().getResultTypes().isEmpty() ? null : func.getType().getResultTypes().get(0);
    }


    @Override
    public void setLocal(int index, long value) {
        if (index >= this.localSize)
            throw new RuntimeException("local variable overflow");
        setStackData(stackBase + index, value);
    }

    @Override
    public void push(long value) {
        int base = stackBase + localSize;
        setStackData(base + stackSize, value);
        stackSize++;
    }


    @Override
    public long pop() {
        int base = stackBase + localSize;
        if (stackSize == 0)
            throw new RuntimeException("stack underflow");
        long v = getStackData(base + stackSize - 1);
        this.stackSize--;
        return v;
    }

    @Override
    public long getUnchecked(int index) {
        return getStackData(index);
    }

    @Override
    public long getLocal(int index) {
        return getStackData(stackBase + index);
    }

    @Override
    public int popN(int frameIndex, int length) {
        if (length == 0)
            return 0;

        if (frameIndex == currentFrameIndex()) {
            if (stackSize < length)
                throw new RuntimeException("stack underflow");
            int r = this.stackBase + this.localSize + stackSize - length;
            this.stackSize -= length;
            return r;
        } else {
            long frameId = frameData.get(frameIndex);
            long offset = offsets.get(frameIndex);
            int size = FrameId.getStackSize(frameId);
            if (size < length)
                throw new RuntimeException("stack underflow");
            frameData.set(frameIndex, FrameId.setStackSize(frameId, size - length));
            return FrameDataOffset.getStackBase(offset) + FrameId.getLocalSize(frameId) + size - length;
        }
    }


    @Override
    public void popFrame() {
        count--;
        // clear
        if (count == 0)
            return;

        long prev = this.frameData.get(currentFrameIndex());
        this.stackSize = FrameId.getStackSize(prev);
        this.localSize = FrameId.getLocalSize(prev);
        this.labelSize = FrameId.getLabelSize(prev);
        this.functionIndex = FrameId.getFunctionIndex(prev);

        long prevOffset = this.offsets.get(currentFrameIndex());
        this.stackBase = FrameDataOffset.getStackBase(prevOffset);
        this.labelBase = FrameDataOffset.getLabelBase(prevOffset);

        resetBody(functionIndex);
    }


    @Override
    public void pushLabel(boolean arity, long body, boolean loop) {
        int p = labelBase + labelSize;
        setLabels(p, body);
        setArity(p, arity);
        setLoop(p, loop);
        setLabelPc(p, 0);

        setStackPc(p, stackSize);
        this.labelSize++;
    }

    @Override
    public void popLabel() {
        if (labelSize == 0)
            throw new RuntimeException("label underflow");
        labelSize--;
    }


    public void popAndClearLabel() {
        if (labelSize == 0)
            throw new RuntimeException("label underflow");
        this.stackSize = getStackPc(labelBase + labelSize - 1);
        this.labelSize--;
    }

    @Override
    public void branch(int l) {
        int idx = labelSize - 1 - l;
        int p = labelBase + idx;

        boolean arity = getArity(p);
        long val = arity ? pop() : 0;
        // Repeat l+1 times
        for (int i = 0; i < l + 1; i++) {
            popAndClearLabel();
        }
        if (arity) {
            push(val);
        }
        boolean loop = getLoop(p);
        setLabelPc(p, 0);
        if (!loop) {
            setLabelPc(p, InstructionPool.getInstructionsSize(getLabels(p)));
        }
        int prevPc = getLabelPc(p);
        pushLabel(
            arity,
            getLabels(p),
            loop
        );
        p = labelBase + labelSize - 1;
        setLabelPc(p, prevPc);
    }

    @Override
    boolean labelIsEmpty() {
        return labelSize == 0;
    }


    public long getInstructions() {
        return getLabels(labelBase + labelSize - 1);
    }

    @Override
    public int getPc() {
        int p = labelBase + labelSize - 1;
        return getLabelPc(p);
    }

    @Override
    public void setPc(int pc) {
        int p = labelBase + labelSize - 1;
        setLabelPc(p, pc);
    }

    @Override
    public void clear() {
        this.count = 0;
    }

    @Override
    public void close() {
        stackData.close();
        UNSAFE.freeMemory(labelDataPtr);
        frameData.close();
        offsets.close();
        labels.close();
    }

    @Override
    public boolean isEmpty() {
        return count == 0;
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
