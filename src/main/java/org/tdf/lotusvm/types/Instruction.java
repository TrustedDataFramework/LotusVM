package org.tdf.lotusvm.types;

import lombok.*;
import org.tdf.lotusvm.common.BytesReader;
import org.tdf.lotusvm.common.OpCode;
import org.tdf.lotusvm.common.Vector;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.tdf.lotusvm.common.OpCode.*;


// [null(32byte), ins0, ins1, operand]
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public class Instruction {
    public static final Instruction[] EMPTY_INSTRUCTIONS = new Instruction[0];
    static int max = 0;
    private OpCode code;

    private ResultType blockType;

    private Instruction[] branch0;

    private Instruction[] branch1;

    private long[] operands;

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
                operands[0] = reader.readUint32() & 0xffffffffL;
                return new Instruction(c, operands);
            }
            case F64_CONST: {
                long[] operands = new long[1];
                operands[0] = reader.readUint64();
                return new Instruction(c, operands);
            }
            default:
                return new Instruction(c);
        }
    }

    public static Instruction readFrom(BytesReader reader) {
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

    @SneakyThrows
    public static Instruction[] readExpressionFrom(BytesReader reader) {
        int cur = reader.getOffset();
        Instruction[] instructions = readInstructionsUntil(reader, END.code);
        reader.read();

        if (instructions.length > 190) {
            byte[] o = reader.slice(cur, reader.getLimit());
            Files.write(Paths.get("ins.data"), o);
        }
        return instructions;
    }

    public boolean equals(Instruction another) {
        if (code != another.code)
            return false;
        if (blockType != another.getBlockType())
            return false;
        if (!Arrays.equals(branch0, another.branch0))
            return false;
        if (!Arrays.equals(branch1, another.branch1))
            return false;
        return Arrays.equals(operands, another.operands);
    }

    public int getOperandInt(int idx) {
        return (int) operands[idx];
    }

    public long getOperandLong(int idx) {
        return operands[idx];
    }

    public int getOperandLen() {
        return operands.length;
    }

    @Override
    public String toString() {
        return "Instruction{" +
                "code=" + code.name +
                ", blockType=" + blockType +
                ", branch0=" + Arrays.toString(branch0) +
                ", branch1=" + Arrays.toString(branch1) +
                ", operands=" + Arrays.toString(operands) +
                '}';
    }
}
