package org.tdf.lotusvm.runtime;

import org.tdf.lotusvm.types.*;

import static org.tdf.lotusvm.types.UnsafeUtil.MAX_UNSIGNED_SHORT;
import static org.tdf.lotusvm.types.UnsafeUtil.UNSAFE;

public class UnsafeStackAllocator extends AbstractStackAllocator {
    private static final long ARITY_OFFSET = 6L;
    private static final long LOOP_OFFSET = 7L;
    private static final long LABEL_PC_OFFSET = 2L;
    private static final long LOCAL_SIZE_OFFSET = 2L;
    private static final long LABEL_SIZE_OFFSET = 4L;
    private static final long FUNCTION_INDEX_OFFSET = 6L;
    private static final long LABEL_BASE_OFFSET = 4L;


    private final LongBuffer stackData;

    // label data = stack pc (2byte) | label pc (2byte) | 0x00  | 0x00  | arity (1byte) | loop (1byte)
    private final long labelDataPtr;

    // frame data = stack size (2byte) | local size (2byte) | label size (2byte) | function index (2byte)
    private final long frameDataPtr;

    // offsets ptr = stack base (4byte) in stack data | label base (4byte) in labels
    private final long offsetsPtr;

    // = label array id in instruction pool = (label size (4byte) | label offset (4byte))
    private final LongBuffer labels;

    // frame count
    private int count;

    private ValueType resultType;
    private long body;

    public UnsafeStackAllocator(int maxStackSize, int maxFrames, int maxLabelSize) {
        super(maxStackSize, maxFrames, maxLabelSize);

        this.labels = new UnsafeLongBuffer(maxLabelSize);
        this.labels.setSize(maxLabelSize);

        this.stackData = new ArrayLongBuffer(maxStackSize);
        this.stackData.setSize(maxStackSize);

        this.frameDataPtr = UNSAFE.allocateMemory((maxFrames * 8L));
        UNSAFE.setMemory(frameDataPtr, (maxFrames * 8L), (byte) 0);

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

    private void clearFrameData(int index) {
        UNSAFE.putLong(frameDataPtr + (index * 8L), 0);
    }

    private long getStackData(int index) {
        return stackData.get(index);
    }

    private void setStackData(int index, long data) {
        stackData.set(index, data);
    }

    public int getLocalSize(int frameId) {
        return UNSAFE.getShort(frameDataPtr + ((frameId * 8L) | LOCAL_SIZE_OFFSET)) & MAX_UNSIGNED_SHORT;
    }

    private void setLocalSize(int frameId, int functionIndex) {
        if (functionIndex > MAX_UNSIGNED_SHORT)
            throw new RuntimeException("function index overflow");
        UNSAFE.putShort(frameDataPtr + ((frameId * 8L) | LOCAL_SIZE_OFFSET), (short) functionIndex);
    }

    public int getStackSize(int frameId) {
        return UNSAFE.getShort(frameDataPtr + (frameId * 8L)) & MAX_UNSIGNED_SHORT;
    }

    public void setStackSize(int frameId, int size) {
        if (size > MAX_UNSIGNED_SHORT)
            throw new RuntimeException("stack size overflow");
        UNSAFE.putShort(frameDataPtr + (frameId * 8L), (short) size);
    }

    public int getLabelSize(int frameId) {
        return UNSAFE.getShort(
            frameDataPtr + ((frameId * 8L) | LABEL_SIZE_OFFSET)
        ) & MAX_UNSIGNED_SHORT;
    }

    public void setLabelSize(int frameId, int size) {
        if (size > MAX_UNSIGNED_SHORT)
            throw new RuntimeException("label size overflow");
        UNSAFE.putShort(frameDataPtr + ((frameId * 8L) | LABEL_SIZE_OFFSET), (short) size);
    }

    private int getFunctionIndex(int frameId) {
        return UNSAFE.getShort(frameDataPtr + ((frameId * 8L) | FUNCTION_INDEX_OFFSET)) & MAX_UNSIGNED_SHORT;
    }

    private void setFunctionIndex(int frameId, int functionIndex) {
        UNSAFE.putShort(frameDataPtr + ((frameId * 8L) | FUNCTION_INDEX_OFFSET), (short) functionIndex);
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
        if (functionIndex > MAX_UNSIGNED_SHORT)
            throw new RuntimeException("function index overflow");

        int c = this.count;

        clearFrameData(c);

        clearOffsets(c);

        if(c != 0){
            System.out.println(getStackSize(c-1));
        }

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
        int labelSize = getLabelSize(frameId);
        if (labelSize == 0)
            throw new RuntimeException("label underflow");
        setLabelSize(frameId, labelSize - 1);
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
        setLabelSize(frameId, size - 1);
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
        stackData.close();
        UNSAFE.freeMemory(labelDataPtr);
        UNSAFE.freeMemory(frameDataPtr);
        UNSAFE.freeMemory(offsetsPtr);
        labels.close();
    }

    @Override
    public boolean isEmpty() {
        return count == 0;
    }

    // current() is always < maxFrameSize
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
