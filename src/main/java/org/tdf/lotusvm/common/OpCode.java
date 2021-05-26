package org.tdf.lotusvm.common;

public enum OpCode {
    // control Instructions
    UNREACHABLE(0x00, "unreachable", "", 0),
    NOP(0x01, "nop", "", 0),
    BLOCK(0x02, "block", "u8", 0),
    LOOP(0x03, "loop", "u8", 0),
    IF(0x04, "if", "u8", 0),
    ELSE(0x05, "else", "", 0),
    END(0x0b, "end", "", 0),
    BR(0x0c, "br", "u32", 0),
    BR_IF(0x0d, "br_if", "u32", 0),
    BR_TABLE(0x0e, "br_table", "complex", 0),
    RETURN(0x0f, "return", "", 0),
    CALL(0x10, "call", "u32", 0),
    CALL_INDIRECT(0x11, "call_indirect", "u32,u8", 0),
    // parametric Instructions
    DROP(0x1a, "drop", "", 0),
    SELECT(0x1b, "select", "", 0),
    // vaiable instructions
    GET_LOCAL(0x20, "local.get", "u32", 0),
    SET_LOCAL(0x21, "local.set", "u32", 0),
    TEE_LOCAL(0x22, "local.tee", "u32", 0),
    GET_GLOBAL(0x23, "global.get", "u32", 0),
    SET_GLOBAL(0x24, "global.set", "u32", 0),
    // memory instructions
    I32_LOAD(0x28, "i32.load", "u32,u32", 4),
    I64_LOAD(0x29, "i64.load", "u32,u32", 8),
    F32_LOAD(0x2a, "f32.load", "u32,u32", 4),
    F64_LOAD(0x2b, "f64.load", "u32,u32", 8),
    I32_LOAD8_S(0x2c, "i32.load8_s", "u32,u32", 1),
    I32_LOAD8_U(0x2d, "i32.load8_u", "u32,u32", 1),
    I32_LOAD16_S(0x2e, "i32.load16_s", "u32,u32", 2),
    I32_LOAD16_U(0x2f, "i32.load16_u", "u32,u32", 2),
    I64_LOAD8_S(0x30, "i64.load8_s", "u32,u32", 1),
    I64_LOAD8_U(0x31, "i64.load8_u", "u32,u32", 1),
    I64_LOAD16_S(0x32, "i64.load16_s", "u32,u32", 2),
    I64_LOAD16_U(0x33, "i64.load16_u", "u32,u32", 2),
    I64_LOAD32_S(0x34, "i64.load32_s", "u32,u32", 4),
    I64_LOAD32_U(0x35, "i64.load32_u", "u32,u32", 4),
    I32_STORE(0x36, "i32.store", "u32,u32", 4),
    I64_STORE(0x37, "i64.store", "u32,u32", 8),
    F32_STORE(0x38, "f32.store", "u32,u32", 4),
    F64_STORE(0x39, "f64.store", "u32,u32", 8),
    I32_STORE8(0x3a, "i32.store8", "u32,u32", 1),
    I32_STORE16(0x3b, "i32.store16", "u32,u32", 2),
    I64_STORE8(0x3c, "i64.store8", "u32,u32", 1),
    I64_STORE16(0x3d, "i64.store16", "u32,u32", 2),
    I64_STORE32(0x3e, "i64.store32", "u32,u32", 4),
    CURRENT_MEMORY(0x3f, "memory.size", "u8", 0),
    GROW_MEMORY(0x40, "memory.grow", "u8", 1),
    // numeric instructions
    // constants
    I32_CONST(0x41, "i32.const", "i32", 2),
    I64_CONST(0x42, "i64.const", "i64", 1),
    F32_CONST(0x43, "f32.const", "f32", 2),
    F64_CONST(0x44, "f64.const", "f64", 4),
    I32_EQZ(0x45, "i32.eqz", "", 0),
    I32_EQ(0x46, "i32.eq", "", 0),
    I32_NE(0x47, "i32.ne", "", 0),
    I32_LTS(0x48, "i32.lt_s", "", 0),
    I32_LTU(0x49, "i32.lt_u", "", 0),
    I32_GTS(0x4a, "i32.gt_s", "", 0),
    I32_GTU(0x4b, "i32.gt_u", "", 0),
    I32_LES(0x4c, "i32.le_s", "", 0),
    I32_LEU(0x4d, "i32.le_u", "", 0),
    I32_GES(0x4e, "i32.ge_s", "", 0),
    I32_GEU(0x4f, "i32.ge_u", "", 0),
    I64_EQZ(0x50, "i64.eqz", "", 0),
    I64_EQ(0x51, "i64.eq", "", 0),
    I64_NE(0x52, "i64.ne", "", 0),
    I64_LTS(0x53, "i64.lt_s", "", 0),
    I64_LTU(0x54, "i64.lt_u", "", 0),
    I64_GTS(0x55, "i64.gt_s", "", 0),
    I64_GTU(0x56, "i64.gt_u", "", 0),
    I64_LES(0x57, "i64.le_s", "", 0),
    I64_LEU(0x58, "i64.le_u", "", 0),
    I64_GES(0x59, "i64.ge_s", "", 0),
    I64_GEU(0x5a, "i64.ge_u", "", 0),
    F32_EQ(0x5b, "f32.eq", "", 0),
    F32_NE(0x5c, "f32.ne", "", 0),
    F32_LT(0x5d, "f32.lt", "", 0),
    F32_GT(0x5e, "f32.gt", "", 0),
    F32_LE(0x5f, "f32.le", "", 0),
    F32_GE(0x60, "f32.ge", "", 0),
    F64_EQ(0x61, "f64.eq", "", 0),
    F64_NE(0x62, "f64.ne", "", 0),
    F64_LT(0x63, "f64.lt", "", 0),
    F64_GT(0x64, "f64.gt", "", 0),
    F64_LE(0x65, "f64.le", "", 0),
    F64_GE(0x66, "f64.ge", "", 0),
    I32_CLZ(0x67, "i32.clz", "", 0),
    I32_CTZ(0x68, "i32.ctz", "", 0),
    I32_POPCNT(0x69, "i32.popcnt", "", 0),
    I32_ADD(0x6a, "i32.add", "", 0),
    I32_SUB(0x6b, "i32.sub", "", 0),
    I32_MUL(0x6c, "i32.mul", "", 0),
    I32_DIVS(0x6d, "i32.div_s", "", 0),
    I32_DIVU(0x6e, "i32.div_u", "", 0),
    I32_REMS(0x6f, "i32.rem_s", "", 0),
    I32_REMU(0x70, "i32.rem_u", "", 0),
    I32_AND(0x71, "i32.and", "", 0),
    I32_OR(0x72, "i32.or", "", 0),
    I32_XOR(0x73, "i32.xor", "", 0),
    I32_SHL(0x74, "i32.shl", "", 0),
    I32_SHRS(0x75, "i32.shr_s", "", 0),
    I32_SHRU(0x76, "i32.shr_u", "", 0),
    I32_ROTL(0x77, "i32.rotl", "", 0),
    I32_ROTR(0x78, "i32.rotr", "", 0),
    I64_CLZ(0x79, "i64.clz", "", 0),
    I64_CTZ(0x7a, "i64.ctz", "", 0),
    I64_POPCNT(0x7b, "i64.popcnt", "", 0),
    I64_ADD(0x7c, "i64.add", "", 0),
    I64_SUB(0x7d, "i64.sub", "", 0),
    I64_MUL(0x7e, "i64.mul", "", 0),
    I64_DIVS(0x7f, "i64.div_s", "", 0),
    I64_DIVU(0x80, "i64.div_u", "", 0),
    I64_REMS(0x81, "i64.rem_s", "", 0),
    I64_REMU(0x82, "i64.rem_u", "", 0),
    I64_AND(0x83, "i64.and", "", 0),
    I64_OR(0x84, "i64.or", "", 0),
    I64_XOR(0x85, "i64.xor", "", 0),
    I64_SHL(0x86, "i64.shl", "", 0),
    I64_SHRS(0x87, "i64.shr_s", "", 0),
    I64_SHRU(0x88, "i64.shr_u", "", 0),
    I64_ROTL(0x89, "i64.rotl", "", 0),
    I64_ROTR(0x8a, "i64.rotr", "", 0),
    F32_ABS(0x8b, "f32.abs", "", 0),
    F32_NEG(0x8c, "f32.neg", "", 0),
    F32_CEIL(0x8d, "f32.ceil", "", 0),
    F32_FLOOR(0x8e, "f32.floor", "", 0),
    F32_TRUNC(0x8f, "f32.trunc", "", 0),
    F32_NEAREST(0x90, "f32.nearest", "", 0),
    F32_SQRT(0x91, "f32.sqrt", "", 0),
    F32_ADD(0x92, "f32.add", "", 0),
    F32_SUB(0x93, "f32.sub", "", 0),
    F32_MUL(0x94, "f32.mul", "", 0),
    F32_DIV(0x95, "f32.div", "", 0),
    F32_MIN(0x96, "f32.min", "", 0),
    F32_MAX(0x97, "f32.max", "", 0),
    F32_COPYSIGN(0x98, "f32.copysign", "", 0),
    F64_ABS(0x99, "f64.abs", "", 0),
    F64_NEG(0x9a, "f64.neg", "", 0),
    F64_CEIL(0x9b, "f64.ceil", "", 0),
    F64_FLOOR(0x9c, "f64.floor", "", 0),
    F64_TRUNC(0x9d, "f64.trunc", "", 0),
    F64_NEAREST(0x9e, "f64.nearest", "", 0),
    F64_SQRT(0x9f, "f64.sqrt", "", 0),
    F64_ADD(0xa0, "f64.add", "", 0),
    F64_SUB(0xa1, "f64.sub", "", 0),
    F64_MUL(0xa2, "f64.mul", "", 0),
    F64_DIV(0xa3, "f64.div", "", 0),
    F64_MIN(0xa4, "f64.min", "", 0),
    F64_MAX(0xa5, "f64.max", "", 0),
    F64_COPYSIGN(0xa6, "f64.copysign", "", 0),
    I32_WRAP_I64(0xa7, "i32.wrap_i64", "", 0),
    I32_TRUNC_SF32(0xa8, "i32.trunc_f32_s", "", 0),
    I32_TRUNC_UF32(0xa9, "i32.trunc_f32_u", "", 0),
    I32_TRUNC_SF64(0xaa, "i32.trunc_f64_s", "", 0),
    I32_TRUNC_UF64(0xab, "i32.trunc_f64_u", "", 0),
    I64_EXTEND_SI32(0xac, "i64.extend_i32_s", "", 0),
    I64_EXTEND_UI32(0xad, "i64.extend_i32_u", "", 0),
    I64_TRUNC_SF32(0xae, "i64.trunc_f32_s", "", 0),
    I64_TRUNC_UF32(0xaf, "i64.trunc_f32_u", "", 0),
    I64_TRUNC_SF64(0xb0, "i64.trunc_f64_s", "", 0),
    I64_TRUNC_UF64(0xb1, "i64.trunc_f64_u", "", 0),
    F32_CONVERT_SI32(0xb2, "f32.convert_i32_s", "", 0),
    F32_CONVERT_UI32(0xb3, "f32.convert_i32_u", "", 0),
    F32_CONVERT_SI64(0xb4, "f32.convert_i64_s", "", 0),
    F32_CONVERT_UI64(0xb5, "f32.convert_i64_u", "", 0),
    F32_DEMOTE_F64(0xb6, "f32.demote_f64", "", 0),
    F64_CONVERT_SI32(0xb7, "f64.convert_i32_s", "", 0),
    F64_CONVERT_UI32(0xb8, "f64.convert_i32_u", "", 0),
    F64_CONVERT_SI64(0xb9, "f64.convert_i64_s", "", 0),
    F64_CONVERT_UI64(0xba, "f64.convert_i64_u", "", 0),
    F64_PROMOTE_F32(0xbb, "f64.promote_f32", "", 0),
    I32_REINTERPRET_F32(0xbc, "i32.reinterpret_f32", "", 0),
    I64_REINTERPRET_F64(0xbd, "i64.reinterpret_f64", "", 0),
    F32_REINTERPRET_I32(0xbe, "f32.reinterpret_i32", "", 0),
    F64_REINTERPRET_I64(0xbf, "f64.reinterpret_i64", "", 0);

    static OpCode[] CODES = new OpCode[0xbf + 1];

    static {
        for (int i = 0; i < OpCode.values().length; i++) {
            CODES[OpCode.values()[i].code] = OpCode.values()[i];
        }
    }

    public final int code;
    public final String name;
    public final String codeSize;
    public final int loadSize;


    OpCode(int code, String name, String codeSize, int loadSize) {
        this.code = code;
        this.name = name;
        this.codeSize = codeSize;
        this.loadSize = loadSize;
    }

    public static OpCode fromCode(int b) {
        OpCode c = CODES[b];
        if (c == null) throw new RuntimeException(String.format("unknown opcode %x", b));
        return c;
    }

    public boolean isFloatOp(OpCode c) {
        return c.code >= 0xae && c.code < 0xc0 ||
                c.code >= 0xa8 && c.code < 0xac ||
                c.code >= 0x8b && c.code < 0xa7 ||
                c.code >= 0x5b && c.code < 0x67 ||
                c == F32_CONST ||
                c == F64_CONST ||
                c == F32_LOAD ||
                c == F64_LOAD ||
                c == F32_STORE ||
                c == F64_STORE;
    }
}
