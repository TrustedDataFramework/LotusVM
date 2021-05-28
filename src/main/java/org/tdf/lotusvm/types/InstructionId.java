package org.tdf.lotusvm.types;

import org.tdf.lotusvm.common.OpCode;

// allocation for instruction id
// operand (4byte) or zero | 0x00 | 0x01 | 0x00 | opcode for instruction with <= 1(i32, f32) operand, no branch
// operand position (4byte) | 0x00 | 0x01 | 0x00 | opcode for instruction with = 1(i64, f64) operand, no branch
// memory position | operand size (2byte) | result type (1byte) | opcode for instruction with branch and var length operands

// allocation for instruction branch and operands
// 8 byte  operands offset | branch1 size (2byte) | branch0 size (2byte)
// 8 byte  branch1 offset (4byte) | branch0 offset (4byte)
public final class InstructionId {
    private InstructionId() {
    }

    public static OpCode getOpCode(long insId) {
        return OpCode.fromCode((int) (insId & 0xffL));
    }

    // with opcode and null result type
    public static long withOpCode(OpCode op) {
        return (op.code & 0xFFL) | (0xFF00L);
    }

    public static long setLeft32(long insId, int value) {
        return ((value & 0xFFFFFFFFL) << 32) | (insId & 0xFFFFFFFFL);
    }

    public static int getLeft32(long insId) {
        return (int) ((insId & 0xFFFFFFFF00000000L) >>> 32);
    }

    public static int getOperandSize(long insId) {
        return (int) ((insId & 0xFFFF0000L) >>> 16);
    }

    public static long setOperandSize(long insId, int operandSize) {
        return (insId & ~(0xFFFF0000L)) | ((operandSize & 0xFFFFL) << 16);
    }

    public static ResultType getResultType(long insId) {
        int rt = (int) ((insId & 0xFF00L) >>> 8);
        if ((rt & 0x80) != 0)
            return null;
        return ResultType.VALUES[rt];
    }

    public static long setResultType(long insId, ResultType t) {
        return (insId & ~(0xFF00L)) | (t.ordinal() & 0xFFL) << 8;
    }
}
