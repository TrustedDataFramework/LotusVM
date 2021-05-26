package org.tdf.lotusvm.types;

import org.tdf.lotusvm.common.BytesReader;
import org.tdf.lotusvm.common.OpCode;
import org.tdf.lotusvm.common.Vector;

import java.util.ArrayList;
import java.util.List;

import static org.tdf.lotusvm.common.OpCode.*;

// instruction = 0x00 + 0x00 + 1byte + 1byte + 8byte(size << 32 | offset) +8byte(size << 32 | offset) + 8byte = 32byte = long + long + long + long
public class InstructionPool {
    private static final int INSTRUCTION_SIZE = 8;
    private static final long OPERAND_SIZE_MASK = 0x7FFFFFFF00000000L;
    private static final long OPERAND_OFFSET_MASK = 0x7FFFFFFFL;

    private long[] data;

    private int size;

    private int operandSize = 0;
    private int operandOffset = 0;

    public InstructionPool() {
        this.size = 1;
        this.data = new long[8];
    }


    // push instruction return the position
    public int push(OpCode op, ResultType type) {
        tryExtend(8);
        long ins = ((op.code & 0xffL) << 8) | (type.ordinal() & 0xffL);
        data[size] = ins;
        int r = this.size;
        this.size += INSTRUCTION_SIZE;
        return r;
    }

    // operands is allocated as array
    public void beginOperands() {
        this.operandSize = 0;
        this.operandOffset = size;
    }

    public void pushOperands(long operand) {
        tryExtend(1);
        data[size++] = operand;
        this.operandSize++;
    }

    public long endOperands() {
        long ret = (Integer.toUnsignedLong(operandSize) << 32) | (Integer.toUnsignedLong(operandOffset));
        this.operandSize = 0;
        this.operandOffset = 0;
        return ret;
    }

    public int getBranchSize(int insId, int branch) {
        long l = this.data[insId + 1 + branch];
        return getInstructionsSize(l);
    }

    public int getBranchInstruction(int insId, int branch, int index) {
        long l = this.data[insId + 1 + branch];
        return getInstructionInArray(l, index);
    }

    public void setBranchBits(int insId, int branch, long instructions) {
        this.data[insId + 1 + branch] = instructions;
    }

    public void setOperandsBits(int insId, long operands) {
        this.data[insId + 3] = operands;
    }

    public long getOperandsBits(int insId) {
        return this.data[insId + 3];
    }

    public int getOperandsSize(int insId) {
        return (int) ((getOperandsBits(insId) & OPERAND_SIZE_MASK) >> 32);
    }

    private int getOperandsOffset(int insId) {
        return (int) (getOperandsBits(insId) & OPERAND_OFFSET_MASK);
    }

    public long getOperand(int insId, int index) {
        if (index >= getOperandsSize(insId))
            throw new RuntimeException("operand access overflow");
        return data[getOperandsOffset(insId) + index];
    }

    private void tryExtend(int size) {
        while (data.length <= this.size + size) {
            long[] data = new long[this.data.length * 2];
            System.arraycopy(this.data, 0, data, 0, this.data.length);
            this.data = data;
        }
    }

    public OpCode getOpCode(int insId) {
        long begin = data[insId];
        long code = (begin & 0xFF00L) >> 8;
        return OpCode.fromCode((int) code);
    }

    public ResultType getResultType(int insId) {
        long begin = data[insId];
        long c = begin & 0xFFL;
        return ResultType.VALUES[(int) c];
    }

    private int readControlInstruction(BytesReader reader) {
        OpCode c = OpCode.fromCode(reader.read());
        switch (c) {
            // 0x00 0x01 0x0f
            case UNREACHABLE:
            case NOP:
            case RETURN: {
                return push(c, ResultType.EMPTY);
            }

            // 0x0c 0x0d 0x10
            // a u32 operand
            case BR:
            case BR_IF:
            case CALL: {
                int r = push(c, ResultType.EMPTY);
                long op = reader.readVarUint32() & 0xffffffffL;
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
                long branch1 = 0L;
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
                long[] labels = Vector.readUint32VectorAsLongFrom(reader);
                long[] operands = new long[labels.length + 1];
                System.arraycopy(labels, 0, operands, 0, labels.length);
                operands[operands.length - 1] = reader.readVarUint32() & 0xffffffffL;
                int r = push(c, ResultType.EMPTY);
                beginOperands();
                for (int i = 0; i < operands.length; i++)
                    pushOperands(operands[i]);
                setOperandsBits(r, endOperands());
                return r;
            }

            case CALL_INDIRECT: {
                // 0x11  x:typeidx u32  0x00
                long typeIndex = reader.readVarUint32() & 0xffffffffL;
                if (reader.read() != 0) throw new RuntimeException("invalid operand of call indirect");
                int r = push(c, ResultType.EMPTY);
                beginOperands();
                pushOperands(typeIndex);
                setOperandsBits(r, endOperands());
                return r;
            }

        }
        throw new RuntimeException("unknown control opcode " + c);
    }

    // instr
    //::=
    //0x1A
    //0x1B
    private int readParametricInstruction(BytesReader reader) {
        return push(OpCode.fromCode(reader.read()), ResultType.EMPTY);
    }

    //0x20  x:localidx
    //0x21  x:localidx
    //0x22  x:localidx
    //0x23  x:globalidx
    //0x24  x:globalidx
    private int readVariableInstruction(BytesReader reader) {
        OpCode c = OpCode.fromCode(reader.read());
        int r = push(c, ResultType.EMPTY);
        beginOperands();
        pushOperands(reader.readVarUint32() & 0xffffffffL);
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
            return push(c, ResultType.EMPTY);
        }
        int r = push(c, ResultType.EMPTY);
        beginOperands();
        pushOperands(reader.readVarUint32() & 0xffffffffL);
        pushOperands(reader.readVarUint32() & 0xffffffffL);
        setOperandsBits(r, endOperands());
        return r;
    }

    private int readNumericInstruction(BytesReader reader) {
        OpCode c = OpCode.fromCode(reader.read());
        long op;
        switch (c) {
            case I32_CONST: {
                op = reader.readVarInt32() & 0xffffffffL;
                break;
            }
            case I64_CONST: {
                op = reader.readVarInt64();
                break;
            }
            case F32_CONST: {
                op = reader.readUint32() & 0xffffffffL;
                break;
            }
            case F64_CONST: {
                op = reader.readUint64();
                break;
            }
            default:
                return push(c, ResultType.EMPTY);
        }
        int r = push(c, ResultType.EMPTY);
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
        tryExtend(1);
        int r = this.size;
        this.data[size++] = Integer.toUnsignedLong(value);
        return r;
    }

    public int pushIntoLinkedList(int prev, int value) {
        tryExtend(1);
        int sz = this.size;
        this.data[size++] = Integer.toUnsignedLong(value);
        this.data[prev] |= (Integer.toUnsignedLong(sz)) << 32;
        return sz;
    }

    private static final long LINKED_LIST_NEXT_MASK = 0x7FFFFFFF00000000L;
    private static final long LINKED_LIST_VALUE_MASK = 0x000000007FFFFFFFL;

    private long spanLinkedList(int head) {
        int cnt = 0;
        long cur = this.data[head];
        int start = this.size;
        while (true) {
            int next = (int) ((cur & LINKED_LIST_NEXT_MASK) >> 32);
            int val = (int) (cur & LINKED_LIST_VALUE_MASK);
            tryExtend(1);
            this.data[size++] = Integer.toUnsignedLong(val);
            cnt++;
            if (next == 0)
                break;
            cur = this.data[next];
        }
        return (Integer.toUnsignedLong(cnt) << 32) | (Integer.toUnsignedLong(start));
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
            if(head == null) {
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
        Instruction[] branch0 = new Instruction[getBranchSize(insId, 0)];
        for(int i = 0; i < getBranchSize(insId, 0); i++) {
            branch0[i] = toInstruction(getBranchInstruction(insId, 0, i));
        }
        Instruction[] branch1 = new Instruction[getBranchSize(insId, 1)];
        for(int i = 0; i < getBranchSize(insId, 1); i++) {
            branch1[i] = toInstruction(getBranchInstruction(insId, 1, i));
        }
        long[] operands = new long[getOperandsSize(insId)];

        for(int i = 0; i < operands.length; i++) {
            operands[i] = getOperand(insId, i);
        }

        return new Instruction(o, type, branch0, branch1, operands);
    }

    public static int getInstructionsSize(long id) {
        return (int) ((id & 0x7fffffff00000000L) >> 32);
    }

    public int getInstructionInArray(long id, int index) {
        int offset = (int) (id & 0x7fffffffL);
        return (int) this.data[offset + index];
    }
}
