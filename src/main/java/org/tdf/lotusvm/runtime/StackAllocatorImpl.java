package org.tdf.lotusvm.runtime;

import org.tdf.lotusvm.types.*;

import static org.tdf.lotusvm.runtime.ResourceFactory.createBuffer;
import static org.tdf.lotusvm.types.UnsafeUtil.MAX_UNSIGNED_SHORT;

class StackAllocatorImpl extends AbstractStackAllocator {
    private final LongBuffer stackData;

    // label data = stack pc (2byte) | label pc (2byte) | 0x00  | 0x00  | arity (1byte) | loop (1byte)
    private final LongBuffer labelData;

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

    private long labelBody;
    private int labelPc;
    private boolean arity;
    private boolean loop;
    private int stackPc;

    public StackAllocatorImpl(int maxStackSize, int maxFrames, int maxLabelSize) {
        super(maxStackSize, maxFrames, maxLabelSize);

        this.labels = createBuffer(maxLabelSize);
        this.labels.setSize(maxLabelSize);

        this.stackData = createBuffer(maxStackSize);
        this.stackData.setSize(maxStackSize);

        this.frameData = createBuffer(maxFrames);
        this.frameData.setSize(maxFrames);

        this.offsets = createBuffer(maxFrames);
        this.offsets.setSize(maxFrames);

        this.labelData = createBuffer(maxLabelSize);
        this.labelData.setSize(maxLabelSize);
    }

    private void loadLabel() {
        int p = this.labelBase + labelSize - 1;
        this.labelBody = labels.get(p);
        long bits = labelData.get(p);
        this.labelPc = LabelData.getLabelPc(bits);
        this.stackPc = LabelData.getStackPc(bits);
        this.arity = LabelData.getArity(bits);
        this.loop = LabelData.getLoop(bits);
    }

    private void saveLabel() {
        int p = this.labelBase + labelSize - 1;
        labels.set(p, this.labelBody);

        long data = LabelData.withAll(this.stackPc, this.labelPc, this.arity, this.loop);
        labelData.set(p, data);
    }

    private void setLabels(int p, long instructions) {
        labels.set(p, instructions);
    }

    private long getLabels(int p) {
        return labels.get(p);
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

        if (labelSize != 0)
            saveLabel();

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
        long frameId = FrameId.withAll(labelSize, localSize, stackSize, functionIndex);
        long offset = FrameDataOffset.withAll(labelBase, stackBase);
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

        if (labelSize != 0) {
            saveLabel();
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

        WASMFunction func = getFuncByBits(functionIndex);

        // set local size
        this.localSize = func.getParamSize() + func.getLocals();

        // set body and value type
        body = func.getBody();
        // set value type
        resultType = func.getType().getResultTypes().isEmpty() ? null : func.getType().getResultTypes().get(0);

        this.count++;
        if (args == null) {
            int start = popN(currentFrameIndex() - 1, func.getParamSize());
            for (int i = 0; i < localSize; i++) {
                setLocal(i, i < func.getParamSize() ? getUnchecked(start + i) : 0);
            }
        } else {
            for (int i = 0; i < localSize; i++) {
                setLocal(i, i < args.length ? args[i] : 0);
            }
        }
    }

    private WASMFunction getFuncByBits(int bits) {
        boolean inTable = (bits & TABLE_MASK) != 0;
        int funcIndex = (int) (bits & FUNCTION_INDEX_MASK);

        return (WASMFunction) (
            inTable ?
                module.getFuncInTable(funcIndex)
                : module.getFunc(funcIndex)
        );
    }

    private void resetBody(int functionIndex) {
        WASMFunction func = getFuncByBits(functionIndex);
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
        if (this.labelSize != 0)
            loadLabel();
    }


    @Override
    public void pushLabel(boolean arity, long body, boolean loop) {
        if (this.labelSize != 0)
            saveLabel();

        this.arity = arity;
        this.loop = loop;
        this.stackPc = stackSize;
        this.labelBody = body;
        this.labelPc = 0;
        this.labelSize++;
    }

    @Override
    public void popLabel() {
        if (labelSize == 0)
            throw new RuntimeException("label underflow");
        labelSize--;
        if (labelSize != 0)
            loadLabel();
    }

    // 1. pop l + 1 labels
    // 2. restore stack size
    // 3. push the last pop label and set pc = 0 if loop else label size
    @Override
    public void branch(int l) {
        int idx = labelSize - 1 - l;
        if (idx < 0)
            throw new RuntimeException("label underflow");

        this.labelSize = idx;

        // p refers to last pop label
        int p = labelBase + labelSize;


        // when l != 0, load label data from memory
        if (l != 0) {
            long inMem = labelData.get(p);
            this.loop = LabelData.getLoop(inMem);
            this.labelBody = getLabels(p);
            this.arity = LabelData.getArity(inMem);
            this.stackPc = LabelData.getStackPc(inMem);
        }

        long val = arity ? pop() : 0;

        // restore stack size after pop
        this.stackSize = this.stackPc;

        if (arity) {
            push(val);
        }

        this.labelSize++;
        this.labelPc = loop ? 0 : InstructionPool.getInstructionsSize(labelBody);
        ;
    }

    @Override
    boolean labelIsEmpty() {
        return labelSize == 0;
    }


    public long getInstructions() {
        return labelBody;
    }

    @Override
    public int getPc() {
        return labelPc;
    }

    @Override
    public void setPc(int pc) {
        this.labelPc = pc;
    }

    @Override
    public void clear() {
        this.count = 0;
    }

    @Override
    public void close() {
        stackData.close();
        labelData.close();
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
