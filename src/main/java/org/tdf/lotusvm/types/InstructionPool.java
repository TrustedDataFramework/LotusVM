package org.tdf.lotusvm.types;

import org.tdf.lotusvm.common.BytesReader;
import org.tdf.lotusvm.common.OpCode;

import java.io.Closeable;

import static org.tdf.lotusvm.common.OpCode.*;
import static org.tdf.lotusvm.types.UnsafeUtil.MAX_UNSIGNED_SHORT;


// allocation for instruction id
// operand (4byte) or zero | 0x00 | 0x01 | 0x00 | opcode for instruction with <= 1(i32, f32) operand, no branch
// operand position (4byte) | 0x00 | 0x01 | 0x00 | opcode for instruction with = 1(i64, f64) operand, no branch

// operands offset | operand size (2byte) | result type (1byte) | opcode for instruction with branch and var length operands

// branch offset | operand size (2byte) | result type (1byte) | opcode for instruction with branch and var length operands
//
public final class InstructionPool implements Closeable {
    private static final int MAX_INITIAL_CAP = 128;

    private static final long LINKED_LIST_NEXT_MASK = 0x7FFFFFFF00000000L;
    private static final int LINKED_LIST_NEXT_SHIFTS = 32;
    private static final long LINKED_LIST_VALUE_MASK = 0x000000007FFFFFFFL;

    private static final long INSTRUCTIONS_SIZE_MASK = 0x7FFFFFFF00000000L;
    private static final int INSTRUCTIONS_SIZE_SHIFTS = 32;
    private static final long INSTRUCTIONS_OFFSET_MASK = 0x7FFFFFFFL;


    private static final long NULL = 0xFFFFFFFFFFFFFFFFL;

    private final LongBuffer data;

    public InstructionPool() {
        this(MAX_INITIAL_CAP);
    }

    public InstructionPool(int initialCap) {
        this.data = new UnsafeLongBuffer(
            Math.min(initialCap & Integer.MAX_VALUE, MAX_INITIAL_CAP)
        );
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


    // push instruction into pool with prefixed operands/body offset
    public long pushWithBodyOffset(long ins) {
        int size = data.size();
        ins = InstructionId.setLeft32(ins, size + 1);
        data.push(ins);
        return ins;
    }


    public void pushValue(long value) {
        data.push(value);
    }

    public int getBranchSize(long insId, int branch) {
        int branchOffset = InstructionId.getLeft32(insId) + branch;
        long l = this.data.get(branchOffset);
        if (isNull(l))
            throw new RuntimeException("branch is null");
        return getInstructionsSize(l);
    }

    public boolean isNullBranchDebug(long insId, int branch) {
        OpCode c = InstructionId.getOpCode(insId);
        switch (c) {
            case BLOCK:
            case LOOP:
            case IF: {
                int branchOffset = InstructionId.getLeft32(insId) + branch;
                return data.get(branchOffset) < 0;
            }
            default:
                return true;
        }
    }

    public boolean isNullBranch(long value) {
        return value < 0;
    }

    public long getBranchInstruction(long insId, int branch, int index) {
        int branchOffset = InstructionId.getLeft32(insId) + branch;
        long l = this.data.get(branchOffset);
        return getInstructionInArray(l, index);
    }

    public long getBranch0(long insId) {
        return getBranchInstructions(insId, 0);
    }

    public long getBranch1(long insId) {
        return getBranchInstructions(insId, 1);
    }

    public long getBranchInstructions(long insId, int branch) {
        int branchOffset = InstructionId.getLeft32(insId) + branch;
        return this.data.get(branchOffset);
    }

    public long getOperand(long insId, int index) {
        int operandSize = InstructionId.getOperandSize(insId);
        if (index >= operandSize)
            throw new RuntimeException("operands access overflow");
        int operandOffset = InstructionId.getLeft32(insId);
        return data.get(operandOffset + index);
    }

    public long getOperandDebug(long insId, int index) {
        OpCode c = InstructionId.getOpCode(insId);

        switch (c) {
            case I32_CONST:
            case F32_CONST:
            case BR:
            case BR_IF:
            case CALL:
            case CALL_INDIRECT:
                return Integer.toUnsignedLong(InstructionId.getLeft32(insId));
        }

        if (c.code >= GET_LOCAL.code && c.code < I32_LOAD.code) {
            return Integer.toUnsignedLong(InstructionId.getLeft32(insId));
        }
        if (c.code >= I32_LOAD.code && c.code < I32_CONST.code) {
            return Integer.toUnsignedLong(InstructionId.getLeft32(insId));
        }
        return getOperand(insId, index);
    }

    public int getOperandAsInt(long insId, int index) {
        return (int) getOperand(insId, index);
    }

    private long readControlInstruction(BytesReader reader) {
        OpCode c = OpCode.fromCode(reader.read());
        long ins = InstructionId.withOpCode(c);
        switch (c) {
            // 0x00 0x01 0x0f
            case UNREACHABLE:
            case NOP:
            case RETURN: {
                return ins;
            }

            // 0x0c 0x0d 0x10
            // a u32 operand
            case BR:
            case BR_IF:
            case CALL: {
                ins = InstructionId.setLeft32(ins, reader.readVarUint32());
                ins = InstructionId.setOperandSize(ins, 1);
                return ins;
            }
            // 0x02 0x03 0x04
            case BLOCK:
            case LOOP:
            case IF: {
                ResultType type = ResultType.readFrom(reader);
                ins = InstructionId.setResultType(ins, type);
                long branch0 = readInstructionsUntil(reader, END.code, ELSE.code);
                long branch1 = NULL;
                if (reader.peek() == ELSE.code) {
                    // skip 0x05
                    reader.read();
                    branch1 = readInstructionsUntil(reader, END.code);
                }
                // skip 0x05 or 0x0b
                reader.read();
                ins = pushWithBodyOffset(ins);
                pushValue(branch0);
                pushValue(branch1);
                return ins;
            }

            case BR_TABLE: {
                ins = pushWithBodyOffset(ins);
                int operandSize = pushLabelsFrom(reader);
                pushValue(reader.readVarUint32AsLong());
                ins = InstructionId.setOperandSize(ins, operandSize + 1);
                return ins;
            }

            case CALL_INDIRECT: {
                // 0x11  x:typeidx u32  0x00
                int typeIndex = reader.readVarUint32();
                if (reader.read() != 0) throw new RuntimeException("invalid operand of call indirect");
                ins = InstructionId.setOperandSize(ins, 1);
                return InstructionId.setLeft32(ins, typeIndex);
            }

        }
        throw new RuntimeException("unknown control opcode " + c);
    }

    private int pushLabelsFrom(BytesReader reader) {
        int length = reader.readVarUint32();
        for (int i = 0; i < length; i++) {
            pushValue(reader.readVarUint32AsLong());
        }
        return length;
    }

    // instr
    //::=
    //0x1A
    //0x1B
    private long readParametricInstruction(BytesReader reader) {
        return InstructionId.withOpCode(OpCode.fromCode(reader.read()));
    }

    //0x20  x:localidx
    //0x21  x:localidx
    //0x22  x:localidx
    //0x23  x:globalidx
    //0x24  x:globalidx
    private long readVariableInstruction(BytesReader reader) {
        OpCode c = OpCode.fromCode(reader.read());
        long insId = InstructionId.withOpCode(c);
        insId = InstructionId.setLeft32(insId, reader.readVarUint32());
        return InstructionId.setOperandSize(insId, 1);
    }

    private long readMemoryInstruction(BytesReader reader) {
        OpCode c = OpCode.fromCode(reader.read());
        long ins = InstructionId.withOpCode(c);
        if (c == CURRENT_MEMORY /*0x3f 0x00*/ || c == GROW_MEMORY /*0x40 0x00*/) {
            // skip 0x00
            if (reader.read() != 0) {
                throw new RuntimeException("invalid terminator of opcode " + c);
            }
            return ins;
        }
        // align is intend to optimize memory access, currently unused
        int align = reader.readVarUint32();
        int m = reader.readVarUint32();
        ins = InstructionId.setLeft32(ins, m);
        return InstructionId.setOperandSize(ins, 1);
    }

    private long readNumericInstruction(BytesReader reader) {
        OpCode c = OpCode.fromCode(reader.read());
        long ins = InstructionId.withOpCode(c);
        long op;
        switch (c) {
            case I32_CONST: {
                ins = InstructionId.setOperandSize(ins, 1);
                return InstructionId.setLeft32(ins, reader.readVarInt32());
            }
            case I64_CONST: {
                ins = InstructionId.setOperandSize(ins, 1);
                op = reader.readVarInt64();
                break;
            }
            case F32_CONST: {
                ins = InstructionId.setOperandSize(ins, 1);
                return InstructionId.setLeft32(ins, reader.readUint32());
            }
            case F64_CONST: {
                ins = InstructionId.setOperandSize(ins, 1);
                op = reader.readUint64();
                break;
            }
            default:
                return ins;
        }
        ins = pushWithBodyOffset(ins);
        pushValue(op);
        return ins;
    }

    public long readFrom(BytesReader reader) {
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

    public int allocIns(long ins) {
        int r = this.size();
        this.data.push(ins);
        return r;
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
            this.data.push(this.data.get(val));
            cnt++;
            if (next == 0)
                break;
            cur = this.data.get(next);
        }
        if (cnt > MAX_UNSIGNED_SHORT) {
            throw new RuntimeException("label size overflow");
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
            long read = readFrom(reader);
            if (head == null) {
                head = allocateLinkedList(allocIns(read));
                cur = head;
            } else {
                cur = pushIntoLinkedList(cur, allocIns(read));
            }
        }
    }

    public long readExpressionFrom(BytesReader reader) {
        long instructions = readInstructionsUntil(reader, END.code);
        reader.read();
        return instructions;
    }

    public Instruction toInstruction(long insId) {
        OpCode o = InstructionId.getOpCode(insId);
        ResultType type = InstructionId.getResultType(insId);

        Instruction[] branch0 = null;
        int branch = 0;

        if (!isNullBranchDebug(insId, branch)) {
            branch0 = new Instruction[getBranchSize(insId, branch)];
            for (int i = 0; i < getBranchSize(insId, branch); i++) {
                branch0[i] = toInstruction(getBranchInstruction(insId, branch, i));
            }
        }

        branch = 1;
        Instruction[] branch1 = null;
        if (!isNullBranchDebug(insId, branch)) {
            branch1 = new Instruction[getBranchSize(insId, branch)];
            for (int i = 0; i < getBranchSize(insId, branch); i++) {
                branch1[i] = toInstruction(getBranchInstruction(insId, branch, i));
            }
        }


        long[] operands = null;
        int operandSize = InstructionId.getOperandSize(insId);

        if (operandSize > 0) {
            operands = new long[operandSize];
            for (int i = 0; i < operands.length; i++) {
                operands[i] = getOperandDebug(insId, i);
            }
        }

        return new Instruction(o, type, branch0, branch1, operands);
    }

    public long getInstructionInArray(long id, int index) {
        int offset = getInstructionsOffset(id);
        int size = getInstructionsSize(id);
        if (index >= size)
            throw new RuntimeException("index overflow");
        return this.data.get(offset + index);
    }

    @Override
    public void close() {
        this.data.close();
    }
}
