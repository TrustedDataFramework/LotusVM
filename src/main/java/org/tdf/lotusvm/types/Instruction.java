package org.tdf.lotusvm.types;

import lombok.*;
import org.tdf.lotusvm.common.BytesReader;
import org.tdf.lotusvm.common.OpCode;
import org.tdf.lotusvm.common.Vector;

import java.util.ArrayList;
import java.util.List;

import static org.tdf.lotusvm.common.OpCode.*;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public class Instruction {
    public static final Instruction[] EMPTY_INSTRUCTIONS = new Instruction[0];

    private OpCode code;

    private ResultType blockType;

    private Instruction[] branch0;

    private Instruction[] branch1;

    private long[] operands;

    public int getOperandInt(int idx) {
        return (int) operands[idx];
    }

    public long getOperandLong(int idx) {
        return operands[idx];
    }


    public int getOperandLen() {
        return operands.length;
    }

    public Instruction(OpCode code) {
        this.code = code;
    }

    public Instruction(OpCode code, long[] operands) {
        this.code = code;
        this.operands = operands;
    }

    private static Instruction readControlInstruction(BytesReader reader) {
        OpCode c = OpCode.fromCode(reader.read());
        switch (c) {
            // 0x00 0x01 0x0f
            case UNREACHABLE:
            case NOP:
            case RETURN:
                return new Instruction(c);
            // 0x0c 0x0d 0x10
            // a u32 operand
            case BR:
            case BR_IF:
            case CALL:
                return new Instruction(c, new long[]{reader.readVarUint32() & 0xffffffffL});
            // 0x02 0x03 0x04
            case BLOCK:
            case LOOP:
            case IF:
                ResultType type = ResultType.readFrom(reader);
                Instruction[] branch0 = readInstructionsUntil(reader, END.code, ELSE.code);
                Instruction[] branch1 = null;
                if (reader.peek() == ELSE.code) {
                    // skip 0x05
                    reader.read();
                    branch1 = readInstructionsUntil(reader, END.code);
                }
                // skip 0x05 or 0x0b
                reader.read();
                return new Instruction(c, type, branch0, branch1, null);
            case BR_TABLE:
                long[] labels = Vector.readUint32VectorAsLongFrom(reader);
                long[] operands = new long[labels.length + 1];
                System.arraycopy(labels, 0, operands, 0, labels.length);
                operands[operands.length - 1] = reader.readVarUint32() & 0xffffffffL;
                return new Instruction(c, operands);
            case CALL_INDIRECT:
                // 0x11  x:typeidx u32  0x00
                long typeIndex = reader.readVarUint32() & 0xffffffffL;
                if (reader.read() != 0) throw new RuntimeException("invalid operand of call indirect");
                return new Instruction(c, new long[]{typeIndex});
        }
        throw new RuntimeException("unknown control opcode " + c);
    }

    // instr
    //::=
    //0x1A
    //0x1B
    private static Instruction readParametricInstruction(BytesReader reader) {
        return new Instruction(OpCode.fromCode(reader.read()));
    }

    //0x20  x:localidx
    //0x21  x:localidx
    //0x22  x:localidx
    //0x23  x:globalidx
    //0x24  x:globalidx
    private static Instruction readVariableInstruction(BytesReader reader) {
        OpCode c = OpCode.fromCode(reader.read());
        return new Instruction(c, new long[]{reader.readVarUint32() & 0xffffffffL});
    }

    private static Instruction readMemoryInstruction(BytesReader reader) {
        OpCode c = OpCode.fromCode(reader.read());
        if (c == CURRENT_MEMORY /*0x3f 0x00*/ || c == GROW_MEMORY /*0x40 0x00*/) {
            // skip 0x00
            if (reader.read() != 0) {
                throw new RuntimeException("invalid terminator of opcode " + c);
            }
            return new Instruction(c);
        }
        return new Instruction(c, new long[]{reader.readVarUint32() & 0xffffffffL, reader.readVarUint32() & 0xffffffffL});
    }

    private static Instruction readNumericInstruction(BytesReader reader) {
        OpCode c = OpCode.fromCode(reader.read());
        switch (c) {
            case I32_CONST: {
                long[] operands = new long[1];
                operands[0] = reader.readVarInt32() & 0xffffffffL;
                return new Instruction(c, operands);
            }
            case I64_CONST: {
                long[] operands = new long[1];
                operands[0] = reader.readVarInt64();
                return new Instruction(c, operands);
            }
            case F32_CONST: {
                long[] operands = new long[1];
                operands[0] = Float.floatToIntBits(reader.readFloat()) & 0xffffffffL;
                return new Instruction(c, operands);
            }
            case F64_CONST: {
                long[] operands = new long[1];
                operands[0] = Double.doubleToLongBits(reader.readDouble());
                return new Instruction(c, operands);
            }
            default:
                return new Instruction(c);
        }
    }

    public static Instruction readFrom(BytesReader reader) {
        OpCode c = OpCode.fromCode(reader.peek());
        if (CONTROL_INSTRUCTIONS.contains(c)) {
            return readControlInstruction(reader);
        }
        if (PARAMETRIC_INSTRUCTIONS.contains(c)) {
            return readParametricInstruction(reader);
        }
        if (VARIABLE_INSTRUCTIONS.contains(c)) {
            return readVariableInstruction(reader);
        }
        if (MEMORY_INSTRUCTIONS.contains(c)) {
            return readMemoryInstruction(reader);
        }
        if (NUMERIC_INSTRUCTIONS.contains(c)) {
            return readNumericInstruction(reader);
        }
        throw new RuntimeException("unknown opcode " + c);
    }

    private static Instruction[] readInstructionsUntil(BytesReader reader, int... ends) {
        List<Instruction> instructions = new ArrayList<>();
        while (true) {
            int ins = reader.peek();
            for (int e : ends) {
                if (e == ins) return instructions.toArray(EMPTY_INSTRUCTIONS);
            }
            instructions.add(readFrom(reader));
        }
    }

    public static Instruction[] readExpressionFrom(BytesReader reader) {
        Instruction[] instructions = readInstructionsUntil(reader, END.code);
        reader.read();
        return instructions;
    }
}
