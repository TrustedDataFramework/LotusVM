package org.tdf.lotusvm.types;

import org.tdf.lotusvm.common.BytesReader;
import org.tdf.lotusvm.common.OpCode;

import java.io.Closeable;

import static org.tdf.lotusvm.common.OpCode.*;

// allocation instruction(32byte) in long[]
// = 0x00000000(4byte) + 0x0000 + 1byte(opcode) + 1byte(result type) 8byte
// + branch0 8byte(size << 32 | offset)
// + branch1 8byte(size << 32 | offset)
// + operands (size <<32 | offset)
public final class InstructionPool implements Closeable {
    // instruction = 32byte = 8 * 8(size of long)
    private static final int INSTRUCTION_SIZE = 8;
    private static final long OPERANDS_SIZE_MASK = 0x7FFFFFFF00000000L;
    private static final int OPERANDS_SIZE_SHIFTS = 32;
    private static final long OPERANDS_OFFSET_MASK = 0x7FFFFFFFL;

    private static final long LINKED_LIST_NEXT_MASK = 0x7FFFFFFF00000000L;
    private static final int LINKED_LIST_NEXT_SHIFTS = 32;
    private static final long LINKED_LIST_VALUE_MASK = 0x000000007FFFFFFFL;

    private static final long INSTRUCTIONS_SIZE_MASK = 0x7FFFFFFF00000000L;
    private static final int INSTRUCTIONS_SIZE_SHIFTS = 32;
    private static final long INSTRUCTIONS_OFFSET_MASK = 0x7FFFFFFFL;

    private static final long OPCODE_MASK = 0xFFL;
    private static final int RESULT_TYPE_SHIFTS = 8;
    private static final long RESULT_TYPE_MASK = 0xFF00L;

    private static final int BRANCH_OFFSET = 1;
    private static final int OPERANDS_OFFSET = 3;

    private static final long NULL = 0xFFFFFFFFFFFFFFFFL;

    private final LongBuffer data;

    private int operandSize = 0;
    private int operandOffset = 0;

    public InstructionPool() {
        this.data = new UnsafeLongBuffer(INSTRUCTION_SIZE * 8);
        // push null pointer at offset 0;
        this.data.push(0L);
    }

    private static boolean isNull(long instructions) {
        return instructions < 0;
    }

    public static int getInstructionsSize(long id) {
        if (isNull(id))
            return 0;
        return (int) ((id & INSTRUCTIONS_SIZE_MASK) >> INSTRUCTIONS_SIZE_SHIFTS);
    }

    public static int getInstructionsOffset(long id) {
        return (int) (id & INSTRUCTIONS_OFFSET_MASK);
    }

    public int size() {
        return data.size();
    }


    // push instruction return the position
    public int push(OpCode op, int type) {
        long ins = ((op.code & 0xffL)) | ((type & 0xFFL) << RESULT_TYPE_SHIFTS);
        int size = data.size();
        data.push(ins);
        data.push(NULL);
        data.push(NULL);
        data.push(NULL); // operands
        return size;
    }

    public int push(OpCode op) {
        return push(op, 0xFF);
    }

    public int push(OpCode op, ResultType type) {
        return push(op, type.ordinal());
    }

    // operands is allocated as array
    public void beginOperands() {
        this.operandSize = 0;
        this.operandOffset = data.size();
    }

    public void pushOperands(long operand) {
        data.push(operand);
        this.operandSize++;
    }

    public long endOperands() {
        long ret = (Integer.toUnsignedLong(operandSize) << OPERANDS_SIZE_SHIFTS) | (Integer.toUnsignedLong(operandOffset));
        this.operandSize = 0;
        this.operandOffset = 0;
        return ret;
    }

    public int getBranchSize(int insId, int branch) {
        long l = this.data.get(insId + BRANCH_OFFSET + branch);
        if (isNull(l))
            throw new RuntimeException("branch is null");
        return getInstructionsSize(l);
    }

    public boolean isNullBranch(int insId, int branch) {
        long l = this.data.get(insId + BRANCH_OFFSET + branch);
        return isNull(l);
    }

    public int getBranchInstruction(int insId, int branch, int index) {
        long l = this.data.get(insId + BRANCH_OFFSET + branch);
        return getInstructionInArray(l, index);
    }

    public long getBranch0(int insId) {
        return getBranchInstructions(insId, 0);
    }

    public long getBranch1(int insId) {
        return getBranchInstructions(insId, 1);
    }

    public long getBranchInstructions(int insId, int branch) {
        return this.data.get(insId + BRANCH_OFFSET + branch);
    }

    public void setBranchBits(int insId, int branch, long instructions) {
        this.data.set(insId + 1 + branch, instructions);
    }

    public void setOperandsBits(int insId, long operands) {
        this.data.set(insId + OPERANDS_OFFSET, operands);
    }

    public long getOperandsBits(int insId) {
        return this.data.get(insId + OPERANDS_OFFSET);
    }

    public int getOperandsSize(int insId) {
        long bits = getOperandsBits(insId);
        if (bits < 0)
            throw new RuntimeException("null operands");
        return (int) ((getOperandsBits(insId) & OPERANDS_SIZE_MASK) >> OPERANDS_SIZE_SHIFTS);
    }

    private int getOperandsOffset(int insId) {
        return (int) (getOperandsBits(insId) & OPERANDS_OFFSET_MASK);
    }

    public long getOperand(int insId, int index) {
        if (index >= getOperandsSize(insId))
            throw new RuntimeException("operand access overflow");
        return data.get(getOperandsOffset(insId) + index);
    }

    public int getStackBase(int insId) {
        return getOperandAsInt(insId, 0);
    }

    public int getMemoryBase(int insId) {
        return getOperandAsInt(insId, 1);
    }

    public int getOperandAsInt(int insId, int index) {
        return (int) (getOperand(insId, index) & 0xFFFFFFFFL);
    }


    public OpCode getOpCode(int insId) {
        long begin = data.get(insId);
        long code = (begin & OPCODE_MASK);
        return OpCode.fromCode((int) code);
    }

    public ResultType getResultType(int insId) {
        long begin = data.get(insId);
        long c = begin & RESULT_TYPE_MASK;
        if (c == RESULT_TYPE_MASK)
            return null;
        return ResultType.VALUES[(int) (c >> RESULT_TYPE_SHIFTS)];
    }

    private int readControlInstruction(BytesReader reader) {
        OpCode c = OpCode.fromCode(reader.read());
        switch (c) {
            // 0x00 0x01 0x0f
            case UNREACHABLE:
            case NOP:
            case RETURN: {
                return push(c);
            }

            // 0x0c 0x0d 0x10
            // a u32 operand
            case BR:
            case BR_IF:
            case CALL: {
                int r = push(c);
                long op = reader.readVarUint32AsLong();
                beginOperands();
                pushOperands(op);
                setOperandsBits(r, endOperands());
                return r;
            }
            // 0x02 0x03 0x04
            case BLOCK:
            case LOOP:
            case IF: {
                ResultType type = ResultType.readFrom(reader);
                long branch0 = readInstructionsUntil(reader, END.code, ELSE.code);
                long branch1 = NULL;
                if (reader.peek() == ELSE.code) {
                    // skip 0x05
                    reader.read();
                    branch1 = readInstructionsUntil(reader, END.code);
                }
                // skip 0x05 or 0x0b
                reader.read();
                int r = push(c, type);
                setBranchBits(r, 0, branch0);
                setBranchBits(r, 1, branch1);
                return r;
            }

            case BR_TABLE: {
                int r = push(c);
                beginOperands();
                pushLabelsFrom(reader);
                pushOperands(reader.readVarUint32AsLong());
                setOperandsBits(r, endOperands());
                return r;
            }

            case CALL_INDIRECT: {
                // 0x11  x:typeidx u32  0x00
                long typeIndex = reader.readVarUint32AsLong();
                if (reader.read() != 0) throw new RuntimeException("invalid operand of call indirect");
                int r = push(c);
                beginOperands();
                pushOperands(typeIndex);
                setOperandsBits(r, endOperands());
                return r;
            }

        }
        throw new RuntimeException("unknown control opcode " + c);
    }

    private void pushLabelsFrom(BytesReader reader) {
        int length = reader.readVarUint32();
        for (int i = 0; i < length; i++) {
            pushOperands(reader.readVarUint32AsLong());
        }
    }

    // instr
    //::=
    //0x1A
    //0x1B
    private int readParametricInstruction(BytesReader reader) {
        return push(OpCode.fromCode(reader.read()));
    }

    //0x20  x:localidx
    //0x21  x:localidx
    //0x22  x:localidx
    //0x23  x:globalidx
    //0x24  x:globalidx
    private int readVariableInstruction(BytesReader reader) {
        OpCode c = OpCode.fromCode(reader.read());
        int r = push(c);
        beginOperands();
        pushOperands(reader.readVarUint32AsLong());
        setOperandsBits(r, endOperands());
        return r;
    }

    private int readMemoryInstruction(BytesReader reader) {
        OpCode c = OpCode.fromCode(reader.read());
        if (c == CURRENT_MEMORY /*0x3f 0x00*/ || c == GROW_MEMORY /*0x40 0x00*/) {
            // skip 0x00
            if (reader.read() != 0) {
                throw new RuntimeException("invalid terminator of opcode " + c);
            }
            return push(c);
        }
        int r = push(c);
        beginOperands();
        pushOperands(reader.readVarUint32AsLong());
        pushOperands(reader.readVarUint32AsLong());
        setOperandsBits(r, endOperands());
        return r;
    }

    private int readNumericInstruction(BytesReader reader) {
        OpCode c = OpCode.fromCode(reader.read());
        long op;
        switch (c) {
            case I32_CONST: {
                op = reader.readVarInt32AsLong();
                break;
            }
            case I64_CONST: {
                op = reader.readVarInt64();
                break;
            }
            case F32_CONST: {
                op = reader.readUint32AsLong();
                break;
            }
            case F64_CONST: {
                op = reader.readUint64();
                break;
            }
            default:
                return push(c);
        }
        int r = push(c);
        beginOperands();
        pushOperands(op);
        setOperandsBits(r, endOperands());
        return r;
    }

    public int readFrom(BytesReader reader) {
        OpCode c = OpCode.fromCode(reader.peek());
        if (c.code >= UNREACHABLE.code && c.code < DROP.code) {
            return readControlInstruction(reader);
        }
        if (c.code == DROP.code || c.code == SELECT.code) {
            return readParametricInstruction(reader);
        }
        if (c.code >= GET_LOCAL.code && c.code < I32_LOAD.code) {
            return readVariableInstruction(reader);
        }
        if (c.code >= I32_LOAD.code && c.code < I32_CONST.code) {
            return readMemoryInstruction(reader);
        }
        if (c.code >= I32_CONST.code) {
            return readNumericInstruction(reader);
        }
        throw new RuntimeException("unknown opcode " + c);
    }

    // push linked list node
    public int allocateLinkedList(int value) {
        int r = this.size();
        this.data.push(Integer.toUnsignedLong(value));
        return r;
    }

    public int pushIntoLinkedList(int prev, int value) {
        int sz = this.size();
        this.data.push(Integer.toUnsignedLong(value));
        this.data.set(prev, this.data.get(prev) | ((Integer.toUnsignedLong(sz)) << LINKED_LIST_NEXT_SHIFTS));
        return sz;
    }

    private long spanLinkedList(int head) {
        int cnt = 0;
        long cur = this.data.get(head);
        int start = this.size();
        while (true) {
            int next = (int) ((cur & LINKED_LIST_NEXT_MASK) >> LINKED_LIST_NEXT_SHIFTS);
            int val = (int) (cur & LINKED_LIST_VALUE_MASK);
            this.data.push(Integer.toUnsignedLong(val));
            cnt++;
            if (next == 0)
                break;
            cur = this.data.get(next);
        }
        return (Integer.toUnsignedLong(cnt) << INSTRUCTIONS_SIZE_SHIFTS) | (Integer.toUnsignedLong(start));
    }

    private long readInstructionsUntil(BytesReader reader, int... ends) {
        Integer head = null;
        int cur = 0;
        while (true) {
            int ins = reader.peek();
            for (int e : ends) {
                if (e == ins) return head == null ? 0 : spanLinkedList(head);
            }
            int read = readFrom(reader);
            if (head == null) {
                head = allocateLinkedList(read);
                cur = head;
            } else {
                cur = pushIntoLinkedList(cur, read);
            }
        }
    }

    public long readExpressionFrom(BytesReader reader) {
        long instructions = readInstructionsUntil(reader, END.code);
        reader.read();
        return instructions;
    }

    public Instruction toInstruction(int insId) {
        OpCode o = getOpCode(insId);
        ResultType type = getResultType(insId);

        Instruction[] branch0 = null;
        int branch = 0;
        if (!isNullBranch(insId, branch)) {
            branch0 = new Instruction[getBranchSize(insId, branch)];
            for (int i = 0; i < getBranchSize(insId, branch); i++) {
                branch0[i] = toInstruction(getBranchInstruction(insId, branch, i));
            }
        }

        branch = 1;
        Instruction[] branch1 = null;
        if (!isNullBranch(insId, branch)) {
            branch1 = new Instruction[getBranchSize(insId, branch)];
            for (int i = 0; i < getBranchSize(insId, branch); i++) {
                branch1[i] = toInstruction(getBranchInstruction(insId, branch, i));
            }
        }


        long[] operands = null;

        long bits = getOperandsBits(insId);

        if (!isNull(bits)) {
            operands = new long[getOperandsSize(insId)];
            for (int i = 0; i < operands.length; i++) {
                operands[i] = getOperand(insId, i);
            }
        }

        return new Instruction(o, type, branch0, branch1, operands);
    }

    public int getInstructionInArray(long id, int index) {
        int offset = getInstructionsOffset(id);
        int size = getInstructionsSize(id);
        if (index >= size)
            throw new RuntimeException("index overflow");
        return (int) this.data.get(offset + index);
    }

    @Override
    public void close() {
        this.data.close();
    }
}
