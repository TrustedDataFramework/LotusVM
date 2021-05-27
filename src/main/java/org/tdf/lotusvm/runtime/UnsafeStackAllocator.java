package org.tdf.lotusvm.runtime;

import org.tdf.lotusvm.types.*;

import static org.tdf.lotusvm.types.UnsafeUtil.MAX_UNSIGNED_SHORT;
import static org.tdf.lotusvm.types.UnsafeUtil.UNSAFE;

public class UnsafeStackAllocator extends AbstractStackAllocator {
    private static final long ARITY_OFFSET = 6L;
    private static final long LOOP_OFFSET = 7L;
    private static final long LABEL_PC_OFFSET = 2L;
    private static final long LABEL_BASE_OFFSET = 4L;


    private final LongBuffer stackData;

    // label data = stack pc (2byte) | label pc (2byte) | 0x00  | 0x00  | arity (1byte) | loop (1byte)
    private final long labelDataPtr;

    // frame data = stack size (2byte) | local size (2byte) | label size (2byte) | function index (2byte)
    private final LongBuffer frameData;

    // offsets ptr = stack base (4byte) in stack data | label base (4byte) in labels
    private final long offsetsPtr;

    // = label array id in instruction pool = (label size (4byte) | label offset (4byte))
    private final LongBuffer labels;

    // frame count
    private int count;

    private ValueType resultType;
    private long body;

    private long currentFrameData;


    public UnsafeStackAllocator(int maxStackSize, int maxFrames, int maxLabelSize) {
        super(maxStackSize, maxFrames, maxLabelSize);

        this.labels = new UnsafeLongBuffer(maxLabelSize);
        this.labels.setSize(maxLabelSize);

        this.stackData = new ArrayLongBuffer(maxStackSize);
        this.stackData.setSize(maxStackSize);

        this.frameData = new UnsafeLongBuffer(maxFrames);
        this.frameData.setSize(maxFrames);

        this.offsetsPtr = UNSAFE.allocateMemory((maxFrames * 8L));
        UNSAFE.setMemory(offsetsPtr, (maxFrames * 8L), (byte) 0);

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
        return FrameId.getLabelSize(currentFrameData);
    }

    private void clearOffsets(int frameId) {
        UNSAFE.putLong(offsetsPtr + (frameId * 8L), 0);
    }

    private void setStackBase(int frameId, int value) {
        UNSAFE.putInt(offsetsPtr + (frameId * 8L), value);
    }

    private void setLabelBase(int frameId, int value) {
        UNSAFE.putInt(offsetsPtr + ((frameId * 8L) | LABEL_BASE_OFFSET), value);
    }

    private int getStackBase(int frameId) {
        return UNSAFE.getInt(offsetsPtr + (frameId * 8L));
    }

    private int getLabelBase(int frameId) {
        return UNSAFE.getInt(offsetsPtr + ((frameId * 8L) | LABEL_BASE_OFFSET));
    }

    @Override
    public void pushExpression(long instructions, ValueType type) {
        if (count == maxFrames) {
            throw new RuntimeException("frame overflow");
        }
        int c = this.count;

        // clear
        long prevFrame = this.currentFrameData;

        if(c != 0){
            storeCurrentFrame();
        }

        clearOffsets(c);

        // new stack base and new label base
        int newStackBase = c == 0 ? 0 : (getStackBase(c - 1) + FrameId.getLocalSize(prevFrame) + FrameId.getStackSize(prevFrame));
        int newLabelBase = c == 0 ? 0 : (getLabelBase(c - 1) + FrameId.getLabelSize(prevFrame));

        setStackBase(c, newStackBase);
        setLabelBase(c, newLabelBase);

        this.resultType = type;
        this.body = instructions;

        this.count++;
    }

    // when args = null,

    private void storeCurrentFrame() {
        frameData.set(currentFrameIndex(), currentFrameData);
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
        long prevFrame = this.currentFrameData;

        if(c != 0){
            storeCurrentFrame();
        }

        this.currentFrameData = FrameId.setFunctionIndex(0, functionIndex);
        clearOffsets(c);

        // new stack base and new label base
        int newStackBase = c == 0 ? 0 : (getStackBase(c - 1) + FrameId.getLocalSize(prevFrame) + FrameId.getStackSize(prevFrame));
        int newLabelBase = c == 0 ? 0 : (getLabelBase(c - 1) + FrameId.getLabelSize(prevFrame));

        setStackBase(c, newStackBase);
        setLabelBase(c, newLabelBase);

        WASMFunction func = (WASMFunction) ((functionIndex & TABLE_MASK) != 0 ?
            getModule().table.getFunctions()[(int) (functionIndex & FUNCTION_INDEX_MASK)] :
            getModule().functions.get((int) (functionIndex & FUNCTION_INDEX_MASK)));

        // set local size
        int localSize = func.parametersLength() + func.getLocals();
        this.currentFrameData = FrameId.setLocalSize(currentFrameData, localSize);

        // set body and value type
        body = func.getBody();
        // set value type
        resultType = func.getType().getResultTypes().isEmpty() ? null : func.getType().getResultTypes().get(0);

        this.count++;
        if (args == null) {
            int start = getStackData(currentFrameIndex() - 1, func.parametersLength());
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
        if (index >= FrameId.getLocalSize(currentFrameData))
            throw new RuntimeException("local variable overflow");
        int base = getStackBase(currentFrameIndex());
        setStackData(base + index, value);
    }

    @Override
    public void push(long value) {
        int base = getStackBase(currentFrameIndex()) + FrameId.getLocalSize(currentFrameData);
        int stackSize = FrameId.getStackSize(currentFrameData);
        setStackData(base + stackSize, value);
        currentFrameData = FrameId.setStackSize(currentFrameData, stackSize + 1);
    }


    @Override
    public long pop() {
        int base = getStackBase(currentFrameIndex()) + FrameId.getLocalSize(currentFrameData);
        int size = FrameId.getStackSize(currentFrameData);
        if (size == 0)
            throw new RuntimeException("stack underflow");
        long v = getStackData(base + size - 1);
        currentFrameData = FrameId.setStackSize(currentFrameData, size - 1);
        return v;
    }

    @Override
    public long getUnchecked(int index) {
        return getStackData(index);
    }

    @Override
    public long getLocal(int index) {
        int base = getStackBase(currentFrameIndex());
        return getStackData(base + index);
    }

    @Override
    public int getStackData(int frameIndex, int length) {
        if (length == 0)
            return 0;
        long frameId = frameData.get(frameIndex);
        int size = FrameId.getStackSize(frameId);
        if (size < length)
            throw new RuntimeException("stack underflow");
        return getStackBase(frameIndex) + FrameId.getLocalSize(frameId) + size - length;
    }


    @Override
    public void popFrame() {
        count--;
        // clear
        if(count == 0)
            return;

        long prev = this.frameData.get(currentFrameIndex());
        resetBody(FrameId.getFunctionIndex(prev));
        this.currentFrameData = prev;
    }


    @Override
    public void pushLabel( boolean arity, long body, boolean loop) {
        int size = FrameId.getLabelSize(currentFrameData);
        int base = getLabelBase(currentFrameIndex());

        int p = base + size;
        setLabels(p, body);
        setArity(p, arity);
        setLoop(p, loop);
        setLabelPc(p, 0);

        int stackSize = FrameId.getStackSize(currentFrameData);
        setStackPc(p, stackSize);

        currentFrameData = FrameId.setLabelSize(currentFrameData, size + 1);
    }

    @Override
    public void popLabel() {
        int labelSize = FrameId.getLabelSize(currentFrameData);
        if (labelSize == 0)
            throw new RuntimeException("label underflow");
        currentFrameData = FrameId.setLabelSize(currentFrameData, labelSize - 1);
    }


    public void popAndClearLabel() {
        int size = FrameId.getLabelSize(currentFrameData);
        if (size == 0)
            throw new RuntimeException("label underflow");
        int base = getLabelBase(currentFrameIndex());
        currentFrameData = FrameId.setStackSize(currentFrameData, getStackPc(base + size - 1));
        currentFrameData = FrameId.setLabelSize(currentFrameData, size - 1);
    }

    @Override
    public void branch(int l) {
        int idx = FrameId.getLabelSize(currentFrameData) - 1 - l;
        int p = getLabelBase(currentFrameIndex()) + idx;

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
        p = getLabelBase(currentFrameIndex()) + FrameId.getLabelSize(currentFrameData) - 1;
        setLabelPc(p, prevPc);
    }

    @Override
    boolean labelIsEmpty() {
        return FrameId.getLabelSize(currentFrameData) == 0;
    }


    public long getInstructions(int idx) {
        int size = FrameId.getLabelSize(currentFrameData);
        if (idx < 0 || idx >= size)
            throw new RuntimeException("label index overflow");
        int base = getLabelBase(currentFrameIndex());
        return getLabels(base + idx);
    }

    @Override
    public int getPc(int idx) {
        int size = FrameId.getLabelSize(currentFrameData);
        if (idx < 0 || idx >= size)
            throw new RuntimeException("label index overflow");
        int p = getLabelBase(currentFrameIndex()) + idx;
        return getLabelPc(p);
    }

    @Override
    public void setPc(int idx, int pc) {
        int size = FrameId.getLabelSize(currentFrameData);
        if (idx < 0 || idx >= size)
            throw new RuntimeException("label index overflow");
        int p = getLabelBase(currentFrameIndex()) + idx;
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
        UNSAFE.freeMemory(offsetsPtr);
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

    public long currentFrame() {
        return currentFrameData;
    }
}
