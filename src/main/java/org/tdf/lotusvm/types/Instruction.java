package org.tdf.lotusvm.types;

import lombok.*;
import org.tdf.lotusvm.common.BytesReader;
import org.tdf.lotusvm.common.OpCode;
import org.tdf.lotusvm.common.Register;
import org.tdf.lotusvm.common.Vector;

import java.util.ArrayList;
import java.util.List;

import static org.tdf.lotusvm.common.OpCode.*;

@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public class Instruction {
    private OpCode code;

    private ResultType blockType;

    private List<Instruction> branch0;

    private List<Instruction> branch1;

    private Register operands;

    private static Instruction readControlInstruction(BytesReader reader) {
        OpCode c = OpCode.fromCode(reader.read());
        switch (c) {
            // 0x00 0x01 0x0f
            case UNREACHABLE:
            case NOP:
            case RETURN:
                return builder().code(c).build();
            // 0x0c 0x0d 0x10
            // a u32 operand
            case BR:
            case BR_IF:
            case CALL:
                Register s = new Register(1);
                s.pushI32(reader.readVarUint32());
                return builder().code(c).operands(s).build();
            // 0x02 0x03 0x04
            case BLOCK:
            case LOOP:
            case IF:
                ResultType type = ResultType.readFrom(reader);
                List<Instruction> branch0 = readInstructionsUntil(reader, END.code, ELSE.code);
                List<Instruction> branch1 = null;
                if (reader.peek() == ELSE.code) {
                    // skip 0x05
                    reader.read();
                    branch1 = readInstructionsUntil(reader, END.code);
                }
                // skip 0x05 or 0x0b
                reader.read();
                return builder().code(c).blockType(type)
                        .branch0(branch0).branch1(branch1)
                        .build();
            case BR_TABLE:
                long[] labels = Vector.readUint32VectorAsLongFrom(reader);
                long[] operands = new long[labels.length + 1];
                System.arraycopy(labels, 0, operands, 0, labels.length);
                operands[operands.length - 1] = Integer.toUnsignedLong(reader.readVarUint32());
                return builder().code(c).operands(new Register(operands)).build();
            case CALL_INDIRECT:
                // 0x11  x:typeidx u32  0x00
                long typeIndex = Integer.toUnsignedLong(reader.readVarUint32());
                if (reader.read() != 0) throw new RuntimeException("invalid operand of call indirect");
                return builder().code(c).operands(new Register(new long[]{typeIndex})).build();
        }
        throw new RuntimeException("unknown control opcode " + c);
    }

    // instr
    //::=
    //0x1A
    //0x1B
    private static Instruction readParametricInstruction(BytesReader reader) {
        return builder().code(OpCode.fromCode(reader.read())).build();
    }

    //0x20  x:localidx
    //0x21  x:localidx
    //0x22  x:localidx
    //0x23  x:globalidx
    //0x24  x:globalidx
    private static Instruction readVariableInstruction(BytesReader reader) {
        OpCode c = OpCode.fromCode(reader.read());
        Register s = new Register(1);
        s.pushI32(reader.readVarUint32());
        return builder().code(c).operands(s).build();
    }

    private static Instruction readMemoryInstruction(BytesReader reader) {
        OpCode c = OpCode.fromCode(reader.read());
        if (c == CURRENT_MEMORY /*0x3f 0x00*/ || c == GROW_MEMORY /*0x40 0x00*/) {
            // skip 0x00
            if (reader.read() != 0) {
                throw new RuntimeException("invalid terminator of opcode " + c);
            }
            return builder().code(c).build();
        }
        Register s = new Register(2);
        s.pushI32(reader.readVarUint32()); // align
        s.pushI32(reader.readVarUint32()); // offset
        return builder()
                .code(c).operands(s).build();
    }

    private static Instruction readNumericInstruction(BytesReader reader) {
        OpCode c = OpCode.fromCode(reader.read());
        Register s = new Register(1);
        switch (c) {
            case I32_CONST:
                s.pushI32(reader.readVarInt32());
                return builder().code(c).operands(s).build();
            case I64_CONST:
                s.pushI64(reader.readVarInt64());
                return builder().code(c).operands(s).build();
            case F32_CONST:
                s.pushF32(reader.readFloat());
                return builder().code(c).operands(s).build();
            case F64_CONST:
                s.pushF64(reader.readDouble());
                return builder().code(c).operands(s).build();
            default:
                return builder().code(c).build();
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

    private static List<Instruction> readInstructionsUntil(BytesReader reader, int... ends) {
        List<Instruction> instructions = new ArrayList<>();
        while (true) {
            int ins = reader.peek();
            for (int e : ends) {
                if (e == ins) return instructions;
            }
            instructions.add(readFrom(reader));
        }
    }

    public static List<Instruction> readExpressionFrom(BytesReader reader) {
        List<Instruction> instructions = readInstructionsUntil(reader, END.code);
        reader.read();
        return instructions;
    }
}
