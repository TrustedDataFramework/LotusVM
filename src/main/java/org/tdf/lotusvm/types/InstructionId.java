package org.tdf.lotusvm.types;

// allocation for instruction id
// operand (4byte) or zero | 0x00 | 0x01 | 0x00 | opcode for instruction with <= 1(i32, f32) operand, no branch
// operand position (4byte) | 0x00 | 0x01 | 0x00 | opcode for instruction with = 1(i64, f64) operand, no branch
// memory position | operand size (2byte) | result type (1byte) | opcode for instruction with branch and var length operands

import org.tdf.lotusvm.common.OpCode;

// allocation for instruction branch and operands
// 8 byte  operands offset | branch1 size (2byte) | branch0 size (2byte)
// 8 byte  branch1 offset (4byte) | branch0 offset (4byte)
final class InstructionId {
    private InstructionId() {}

    static OpCode getOpCode(long insId) {
        return OpCode.fromCode((int) (insId & 0xffL));
    }

    static long setOpCode(long insId, OpCode op) {
        return (insId & (~(0xFFL))) | (op.code & 0xFFL);
    }

    static long setLeft32(long insId, int value) {
        return ((value & 0xFFFFFFFFL) << 32) | (insId & 0xFFFFFFFFL);
    }

    static int getLeft32(long insId) {
        return (int) ((insId & 0xFFFFFFFF00000000L) >>> 32);
    }

    static int getOperandSize(long insId) {
        return (int) ((insId & 0xFFFF0000L) >>> 16);
    }

    static long setOperandSize(long insId, int operandSize) {
        return (insId & ~(0xFFFF0000L)) | ((operandSize & 0xFFFFL) << 16);
    }

    static ResultType getResultType(long insId) {
        int rt = (int) ((insId & 0xFF00L) >>> 8);
        return ResultType.VALUES[rt];
    }

    static long setResultType(long insId, ResultType t) {
        return (insId & ~(0xFF00L)) | (t.ordinal() & 0xFFL) << 8;
    }
}
