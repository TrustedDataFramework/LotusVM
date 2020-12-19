package org.tdf.lotusvm.runtime;

import lombok.Getter;
import org.tdf.lotusvm.common.OpCode;
import org.tdf.lotusvm.common.Register;
import org.tdf.lotusvm.types.FunctionType;
import org.tdf.lotusvm.types.Instruction;
import org.tdf.lotusvm.types.ResultType;



@Getter
public class Frame {
    public static int DEFAULT_INITIAL_STACK_CAP = 8;
    private static final long MAXIMUM_UNSIGNED_I32 = 0xFFFFFFFFL;

    private static final long UNSIGNED_MASK = 0x7fffffffffffffffL;

    private Instruction[] body;

    private FunctionType type;

    private ModuleInstanceImpl module;

    private long[] localVariables;

    private Register stack;

    private int labelPos;

    private int[] labelsPC;
    private Instruction[][] labelsBody;
    private boolean[] labelsArity;
    private boolean[] labelsLoop;

    private boolean labelIsEmpty() {
        return labelPos <= 0;
    }


    private void growLabel1() {
        if (this.labelsPC == null) {
            this.labelsPC = new int[DEFAULT_INITIAL_STACK_CAP];
            this.labelsBody = new Instruction[DEFAULT_INITIAL_STACK_CAP][];
            this.labelsArity = new boolean[DEFAULT_INITIAL_STACK_CAP];
            this.labelsLoop = new boolean[DEFAULT_INITIAL_STACK_CAP];
        }

        if (labelPos >= this.labelsPC.length) {
            int[] tmp0 = this.labelsPC;
            Instruction[][] tmp1 = this.labelsBody;
            boolean[] tmp2 = this.labelsArity;
            boolean[] tmp3 = this.labelsLoop;
            int l = tmp0.length * 2 + 1;
            this.labelsPC = new int[l];
            this.labelsBody = new Instruction[l][];
            this.labelsArity = new boolean[l];
            this.labelsLoop = new boolean[l];
            System.arraycopy(tmp0, 0, this.labelsPC, 0, tmp0.length);
            System.arraycopy(tmp1, 0, this.labelsBody, 0, tmp0.length);
            System.arraycopy(tmp2, 0, this.labelsArity, 0, tmp0.length);
            System.arraycopy(tmp3, 0, this.labelsLoop, 0, tmp0.length);
        }
    }

    Frame(Instruction[] body, FunctionType type, ModuleInstanceImpl module, long[] localVariables, Register stack) {
        this.body = body;
        this.type = type;
        this.module = module;
        this.localVariables = localVariables;
        this.stack = stack;
        for (int i = 0; i < module.hooks.length; i++) {
            module.hooks[i].onNewFrame(this);
        }
    }

    // math trunc
    private static double truncDouble(double d) {
        if (d > 0) {
            return Math.floor(d);
        } else {
            return Math.ceil(d);
        }
    }

    private static float truncFloat(float f) {
        return (float) truncDouble(f);
    }

    // A result is the outcome of a computation. It is either a sequence of values or a trap.
    // In the current version of WebAssembly, a result can consist of at most one value.
    long execute() throws RuntimeException {
        pushLabel(type.getResultTypes().size() != 0, body, false);
        while (!labelIsEmpty()) {
            int idx = this.labelPos - 1;
            int pc = this.labelsPC[idx];
            Instruction[] body = this.labelsBody[idx];
            if (pc >= body.length) {
                popLabel();
                continue;
            }
            Instruction ins = body[pc];
            if (ins.getCode().equals(OpCode.RETURN)) {
                return returns();
            }
            labelsPC[idx]++;
            invoke(ins);
        }
        for (int i = 0; i < module.hooks.length; i++) {
            module.hooks[i].onFrameExit(this);
        }
        return returns();
        // clear stack and local variables
    }

    private long returns() {
        if (type.getResultTypes().size() == 0)
            return 0;
        long res = stack.pop();
        switch (type.getResultTypes().get(0)) {
            case F32:
            case I32:
                // shadow bits
                res = res & 0xffffffffL;
        }
        return res;
    }

    private void pushLabel(boolean arity, Instruction[] body, boolean loop) {
        this.growLabel1();
        this.labelsBody[labelPos] = body;
        this.labelsArity[labelPos] = arity;
        this.labelsLoop[labelPos] = loop;
        this.labelsPC[labelPos] = 0;
        this.labelPos++;
        stack.pushLabel();
    }

    private void popLabel() {
        stack.popLabel();
        this.labelPos--;
    }

    private void popAndClearLabel() {
        stack.popAndClearLabel();
        this.labelPos--;
    }

    private void invoke(Instruction ins) throws RuntimeException {
        for (int i = 0; i < module.hooks.length; i++) {
            module.hooks[i].onInstruction(ins, module);
        }
        switch (ins.getCode()) {
            // these operations are essentially no-ops.
            case NOP:
            case I32_REINTERPRET_F32:
            case I64_REINTERPRET_F64:
            case F32_REINTERPRET_I32:
            case F64_REINTERPRET_I64:
            case I64_EXTEND_UI32:
                break;
            case UNREACHABLE:
                throw new RuntimeException("exec: reached unreachable");
                // parametric instructions
            case BLOCK:
                pushLabel(ins.getBlockType() != ResultType.EMPTY, ins.getBranch0(), false);
                break;
            case LOOP:
                pushLabel(false, ins.getBranch0(), true);
                break;
            case IF: {
                long c = stack.pop();
                if (c != 0) {
                    pushLabel(ins.getBlockType() != ResultType.EMPTY, ins.getBranch0(), false);
                    break;
                }
                if (ins.getBranch1() != null) {
                    pushLabel(ins.getBlockType() != ResultType.EMPTY, ins.getBranch1(), false);
                }
                break;
            }
            case BR: {
                // the l is non-negative here
                int l = ins.getOperandInt(0);
                branch(l);
                break;
            }
            case BR_IF: {
                long c = stack.pop();
                if (c == 0) {
                    break;
                }
                // the l is non-negative here
                int l = ins.getOperandInt(0);
                branch(l);
                break;
            }
            case BR_TABLE: {
                // n is non-negative
                int n = ins.getOperandInt(ins.getOperandLen() - 1);
                // cannot determine sign of i
                int i = stack.popI32();
                // length of l* is operands.cap() - 1
                if (Integer.compareUnsigned(i, ins.getOperandLen() - 1) < 0) {
                    n = ins.getOperandInt(i);
                }
                branch(n);
                break;
            }
            case DROP:
                // Pop the value val from the stack.
                stack.pop();
                break;
//            case RETURN:
//                break;
            case CALL: {
                FunctionInstance function = module.functions.get(ins.getOperandInt(0));
                if (function.isHost()) {
                    for (int i = 0; i < module.hooks.length; i++) {
                        module.hooks[i].onHostFunction((HostFunction) function, module);
                    }
                }
                long res = function.execute(
                        stack.popN(function.parametersLength())
                );
                int resLength = function.getArity();
                if (module.validateFunctionType && resLength != function.getArity()) {
                    throw new RuntimeException("the result of function " + function + " is not equals to its arity");
                }
                if (function.getArity() > 0) {
                    stack.push(
                            res
                    );
                }
                break;
            }
            case CALL_INDIRECT: {
                int elementIndex = stack.popI32();
                if (elementIndex < 0 || elementIndex >= module.table.getFunctions().length) {
                    throw new RuntimeException("undefined element index");
                }
                FunctionInstance function = module.table.getFunctions()[elementIndex];
                if (function.isHost()) {
                    for (int i = 0; i < module.hooks.length; i++) {
                        module.hooks[i].onHostFunction((HostFunction) function, module);
                    }
                }
                if (module.validateFunctionType && !function.getType().equals(module.types.get(ins.getOperandInt(0)))) {
                    throw new RuntimeException("failed exec: signature mismatch in call_indirect expected");
                }
                long r = function.execute(
                        stack.popN(function.parametersLength())
                );
                if (function.getArity() > 0) {
                    stack.push(r);
                }
                break;
            }
            case SELECT: {
                int c = stack.popI32();
                long val2 = stack.pop();
                long val1 = stack.pop();
                if (c != 0) {
                    stack.push(val1);
                    break;
                }
                stack.push(val2);
                break;
            }
            // variable instructions
            case GET_LOCAL:
                stack.push(localVariables[ins.getOperandInt(0)]);
                break;
            case SET_LOCAL:
                localVariables[ins.getOperandInt(0)] = stack.pop();
                break;
            case TEE_LOCAL: {
                long val = stack.pop();
                stack.push(val);
                stack.push(val);
                localVariables[ins.getOperandInt(0)] = stack.pop();
                break;
            }
            case GET_GLOBAL:
                stack.push(module.globals.get(ins.getOperandInt(0)));
                break;
            case SET_GLOBAL:
                if (!module.globalTypes.get(
                        ins.getOperandInt(0)
                ).isMutable()) throw new RuntimeException("modify a immutable global");
                module.globals.set(ins.getOperandInt(0), stack.pop());
                break;
            // memory instructions
            case I32_LOAD:
            case F32_LOAD:
            case I64_LOAD32_U:
                stack.pushI32(
                        module.memory.load32(stack.popU32() + ins.getOperandInt(1))
                );
                break;
            case I64_LOAD:
            case F64_LOAD:
                stack.push(
                        module.memory.load64(stack.popU32() + ins.getOperandInt(1))
                );
                break;
            case I32_LOAD8_S:
                stack.pushI32(module.memory.load8(stack.popU32() + ins.getOperandInt(1)));
                break;
            case I64_LOAD8_S: {
                stack.push(module.memory.load8(stack.popU32() + ins.getOperandInt(1)));
                break;
            }
            case I32_LOAD8_U:
            case I64_LOAD8_U:
                stack.pushI8(module.memory.load8(stack.popU32() + ins.getOperandInt(1)));
                break;
            case I32_LOAD16_S: {
                stack.pushI32(module.memory.load16(stack.popU32() + ins.getOperandInt(1)));
                break;
            }
            case I64_LOAD16_S:
                stack.push(module.memory.load16(stack.popU32() + ins.getOperandInt(1)));
                break;
            case I32_LOAD16_U:
            case I64_LOAD16_U:
                stack.pushI16(module.memory.load16(stack.popU32() + ins.getOperandInt(1)));
                break;
            case I64_LOAD32_S:
                stack.push(module.memory.load32(stack.popU32() + ins.getOperandInt(1)));
                break;
            case I32_STORE8:
            case I64_STORE8: {
                byte c = stack.popI8();
                module.memory.storeI8(stack.popU32() + ins.getOperandInt(1), c);
                break;
            }
            case I32_STORE16:
            case I64_STORE16: {
                short c = stack.popI16();
                module.memory.storeI16(stack.popU32() + ins.getOperandInt(1), c);
                break;
            }
            case I32_STORE:
            case F32_STORE:
            case I64_STORE32: {
                int c = stack.popI32();
                module.memory.storeI32(stack.popU32() + ins.getOperandInt(1), c);
                break;
            }
            case I64_STORE:
            case F64_STORE: {
                long c = stack.pop();
                module.memory.storeI64(stack.popU32() + ins.getOperandInt(1), c);
                break;
            }
            case CURRENT_MEMORY:
                stack.pushI32(module.memory.getPageSize());
                break;
            case GROW_MEMORY: {
                int n = stack.popU32();
                stack.pushI32(module.memory.grow(n));
                break;
            }
            case I32_CONST:
            case I64_CONST:
            case F32_CONST:
            case F64_CONST:
                stack.push(ins.getOperandLong(0));
                break;
            case I32_CLZ:
                stack.pushI32(Integer.numberOfLeadingZeros(stack.popI32()));
                break;
            case I32_CTZ:
                stack.pushI32(Integer.numberOfTrailingZeros(stack.popI32()));
                break;
            case I32_POPCNT:
                stack.pushI32(Integer.bitCount(stack.popI32()));
                break;
            case I32_ADD:
                stack.pushI32(stack.popI32() + stack.popI32());
                break;
            case I32_MUL:
                stack.pushI32(stack.popI32() * stack.popI32());
                break;
            case I32_DIVS: {
                int v2 = stack.popI32();
                int v1 = stack.popI32();
                stack.pushI32(v1 / v2);
                break;
            }
            case I32_DIVU: {
                int v2 = stack.popI32();
                int v1 = stack.popI32();
                stack.pushI32(Integer.divideUnsigned(v1, v2));
                break;
            }
            case I32_REMS: {
                int v2 = stack.popI32();
                int v1 = stack.popI32();
                stack.pushI32(v1 % v2);
                break;
            }
            case I32_REMU: {
                int v2 = stack.popI32();
                int v1 = stack.popI32();
                stack.pushI32(Integer.remainderUnsigned(v1, v2));
                break;
            }
            case I32_SUB: {
                int v2 = stack.popI32();
                int v1 = stack.popI32();
                stack.pushI32(v1 - v2);
                break;
            }
            case I32_AND:
                stack.pushI32(stack.popI32() & stack.popI32());
                break;
            case I32_OR:
                stack.pushI32(stack.popI32() | stack.popI32());
                break;
            case I32_XOR:
                stack.pushI32(stack.popI32() ^ stack.popI32());
                break;
            case I32_SHL: {
                int v2 = stack.popI32();
                int v1 = stack.popI32();
                stack.pushI32(v1 << v2);
                break;
            }
            case I32_SHRU: {
                int v2 = stack.popI32();
                int v1 = stack.popI32();
                stack.pushI32(v1 >>> v2);
                break;
            }
            case I32_SHRS: {
                int v2 = stack.popI32();
                int v1 = stack.popI32();
                stack.pushI32(v1 >> v2);
                break;
            }
            case I32_ROTL: {
                int v2 = stack.popI32();
                int v1 = stack.popI32();
                stack.pushI32(Integer.rotateLeft(v1, v2));
                break;
            }

            case I32_ROTR: {
                int v2 = stack.popI32();
                int v1 = stack.popI32();
                stack.pushI32(Integer.rotateLeft(v1, -v2));
                break;
            }

            case I32_LES: {
                int v2 = stack.popI32();
                int v1 = stack.popI32();
                stack.pushBoolean(v1 <= v2);
                break;
            }
            case I32_LEU: {
                int v2 = stack.popI32();
                int v1 = stack.popI32();
                stack.pushBoolean(Integer.compareUnsigned(v1, v2) <= 0);
                break;
            }

            case I32_LTS: {
                int v2 = stack.popI32();
                int v1 = stack.popI32();
                stack.pushBoolean(v1 < v2);
                break;
            }
            case I32_LTU: {
                int v2 = stack.popI32();
                int v1 = stack.popI32();
                stack.pushBoolean(Integer.compareUnsigned(v1, v2) < 0);
                break;
            }
            case I32_GTS: {
                int v2 = stack.popI32();
                int v1 = stack.popI32();
                stack.pushBoolean(v1 > v2);
                break;
            }
            case I32_GTU: {
                int v2 = stack.popI32();
                int v1 = stack.popI32();
                stack.pushBoolean(Integer.compareUnsigned(v1, v2) > 0);
                break;
            }
            case I32_GES: {
                int v2 = stack.popI32();
                int v1 = stack.popI32();
                stack.pushBoolean(v1 >= v2);
                break;
            }
            case I32_GEU: {
                int v2 = stack.popI32();
                int v1 = stack.popI32();
                stack.pushBoolean(Integer.compareUnsigned(v1, v2) >= 0);
                break;
            }
            case I32_EQZ:
                stack.pushBoolean(stack.popI32() == 0);
                break;
            case I32_EQ:
                stack.pushBoolean(stack.popI32() == stack.popI32());
                break;
            case I32_NE:
                stack.pushBoolean(stack.popI32() != stack.popI32());
                break;
            case I64_CLZ:
                stack.pushI64(Long.numberOfLeadingZeros(stack.popI64()));
                break;
            case I64_CTZ:
                stack.pushI64(Long.numberOfTrailingZeros(stack.popI64()));
                break;
            case I64_POPCNT:
                stack.pushI64(Long.bitCount(stack.popI64()));
                break;
            case I64_ADD:
                stack.pushI64(stack.popI64() + stack.popI64());
                break;
            case I64_SUB: {
                long v2 = stack.popI64();
                long v1 = stack.popI64();
                stack.pushI64(v1 - v2);
                break;
            }
            case I64_MUL:
                stack.pushI64(stack.popI64() * stack.popI64());
                break;
            case I64_DIVS: {
                long v2 = stack.popI64();
                long v1 = stack.popI64();
                stack.pushI64(v1 / v2);
                break;
            }
            case I64_DIVU: {
                long v2 = stack.popI64();
                long v1 = stack.popI64();
                stack.pushI64(Long.divideUnsigned(v1, v2));
                break;
            }
            case I64_REMS: {
                long v2 = stack.popI64();
                long v1 = stack.popI64();
                stack.pushI64(v1 % v2);
                break;
            }
            case I64_REMU: {
                long v2 = stack.popI64();
                long v1 = stack.popI64();
                stack.pushI64(Long.remainderUnsigned(v1, v2));
                break;
            }
            case I64_AND:
                stack.pushI64(stack.popI64() & stack.popI64());
                break;
            case I64_OR:
                stack.pushI64(stack.popI64() | stack.popI64());
                break;
            case I64_XOR:
                stack.pushI64(stack.popI64() ^ stack.popI64());
                break;
            case I64_SHL: {
                long v2 = stack.popI64();
                long v1 = stack.popI64();
                stack.pushI64(v1 << v2);
                break;
            }
            case I64_SHRS: {
                long v2 = stack.popI64();
                long v1 = stack.popI64();
                stack.pushI64(v1 >> v2);
                break;
            }
            case I64_SHRU: {
                long v2 = stack.popI64();
                long v1 = stack.popI64();
                stack.pushI64(v1 >>> v2);
                break;
            }
            case I64_ROTL: {
                long v2 = stack.popI64();
                long v1 = stack.popI64();
                stack.pushI64(Long.rotateLeft(v1, (int) v2));
                break;
            }
            case I64_ROTR: {
                long v2 = stack.popI64();
                long v1 = stack.popI64();
                stack.pushI64(Long.rotateLeft(v1, -(int) v2));
                break;
            }
            case I64_EQ:
                stack.pushBoolean(stack.popI64() == stack.popI64());
                break;
            case I64_EQZ:
                stack.pushBoolean(stack.popI64() == 0);
                break;
            case I64_NE:
                stack.pushBoolean(stack.popI64() != stack.popI64());
                break;
            case I64_LTS: {
                long v2 = stack.popI64();
                long v1 = stack.popI64();
                stack.pushBoolean(v1 < v2);
                break;
            }
            case I64_LTU: {
                long v2 = stack.popI64();
                long v1 = stack.popI64();
                stack.pushBoolean(Long.compareUnsigned(v1, v2) < 0);
                break;
            }

            case I64_GTS: {
                long v2 = stack.popI64();
                long v1 = stack.popI64();
                stack.pushBoolean(v1 > v2);
                break;
            }
            case I64_GTU: {
                long v2 = stack.popI64();
                long v1 = stack.popI64();
                stack.pushBoolean(Long.compareUnsigned(v1, v2) > 0);
                break;
            }
            case I64_LEU: {
                long v2 = stack.popI64();
                long v1 = stack.popI64();
                stack.pushBoolean(Long.compareUnsigned(v1, v2) <= 0);
                break;
            }
            case I64_LES: {
                long v2 = stack.popI64();
                long v1 = stack.popI64();
                stack.pushBoolean(v1 <= v2);
                break;
            }
            case I64_GES: {
                long v2 = stack.popI64();
                long v1 = stack.popI64();
                stack.pushBoolean(v1 >= v2);
                break;
            }
            case I64_GEU: {
                long v2 = stack.popI64();
                long v1 = stack.popI64();
                stack.pushBoolean(Long.compareUnsigned(v1, v2) >= 0);
                break;
            }
            case F32_ABS:
                stack.pushF32(Math.abs(stack.popF32()));
                break;
            case F32_NEG:
                stack.pushF32(-stack.popF32());
                break;
            case F32_CEIL:
                stack.pushF32((float) Math.ceil(stack.popF32()));
                break;
            case F32_FLOOR:
                stack.pushF32((float) Math.floor(stack.popF32()));
                break;
            case F32_TRUNC: {
                stack.pushF32(truncFloat(stack.popF32()));
                break;
            }
            case F32_NEAREST: {
                float f = stack.popF32();
                stack.pushF32((int) (f + (float) (Math.copySign(0.5, f))));
                break;
            }
            case F32_SQRT: {
                stack.pushF32((float) Math.sqrt(stack.popF32()));
                break;
            }
            case F32_ADD:
                stack.pushF32(stack.popF32() + stack.popF32());
                break;
            case F32_SUB: {
                float v2 = stack.popF32();
                float v1 = stack.popF32();
                stack.pushF32(v1 - v2);
                break;
            }
            case F32_MUL:
                stack.pushF32(stack.popF32() * stack.popF32());
                break;
            case F32_DIV: {
                float v2 = stack.popF32();
                float v1 = stack.popF32();
                stack.pushF32(v1 / v2);
                break;
            }
            case F32_MIN:
                stack.pushF32(Math.min(stack.popF32(), stack.popF32()));
                break;
            case F32_MAX:
                stack.pushF32(Math.max(stack.popF32(), stack.popF32()));
                break;
            case F32_COPYSIGN:
                stack.pushF32(Math.copySign(stack.popF32(), stack.popF32()));
                break;
            case F32_EQ:
                stack.pushBoolean(stack.popF32() == stack.popF32());
                break;
            case F32_NE:
                stack.pushBoolean(stack.popF32() != stack.popF32());
                break;
            case F32_LT: {
                float v2 = stack.popF32();
                float v1 = stack.popF32();
                stack.pushBoolean(v1 < v2);
                break;
            }
            case F32_GT: {
                float v2 = stack.popF32();
                float v1 = stack.popF32();
                stack.pushBoolean(v1 > v2);
                break;
            }
            case F32_LE: {
                float v2 = stack.popF32();
                float v1 = stack.popF32();
                stack.pushBoolean(v1 <= v2);
                break;
            }
            case F32_GE: {
                float v2 = stack.popF32();
                float v1 = stack.popF32();
                stack.pushBoolean(v1 >= v2);
                break;
            }
            case F64_ABS:
                stack.pushF64(Math.abs(stack.popF64()));
                break;
            case F64_NEG:
                stack.pushF64(-stack.popF64());
                break;
            case F64_CEIL:
                stack.pushF64(Math.ceil(stack.popF64()));
                break;
            case F64_FLOOR:
                stack.pushF64(Math.floor(stack.popF64()));
                break;
            case F64_TRUNC: {
                double d = stack.popF64();
                stack.pushF64(truncDouble(d));
                break;
            }
            case F64_NEAREST: {
                double d = stack.popF64();
                stack.pushF64((long) (d + Math.copySign(0.5, d)));
                break;
            }
            case F64_SQRT:
                stack.pushF64(Math.sqrt(stack.popF64()));
                break;
            case F64_ADD:
                stack.pushF64(stack.popF64() + stack.popF64());
                break;
            case F64_SUB: {
                double v2 = stack.popF64();
                double v1 = stack.popF64();
                stack.pushF64(v1 - v2);
                break;
            }
            case F64_MUL:
                stack.pushF64(stack.popF64() * stack.popF64());
                break;
            case F64_DIV: {
                double v2 = stack.popF64();
                double v1 = stack.popF64();
                stack.pushF64(v1 / v2);
                break;
            }
            case F64_MIN:
                stack.pushF64(Math.min(stack.popF64(), stack.popF64()));
                break;
            case F64_MAX:
                stack.pushF64(Math.max(stack.popF64(), stack.popF64()));
                break;
            case F64_COPYSIGN:
                stack.pushF64(Math.copySign(stack.popF64(), stack.popF64()));
                break;
            case F64_EQ:
                stack.pushBoolean(stack.popF64() == stack.popF64());
                break;
            case F64_NE:
                stack.pushBoolean(stack.popF64() != stack.popF64());
                break;
            case F64_LT: {
                double v2 = stack.popF64();
                double v1 = stack.popF64();
                stack.pushBoolean(v1 < v2);
                break;
            }
            case F64_GT: {
                double v2 = stack.popF64();
                double v1 = stack.popF64();
                stack.pushBoolean(v1 > v2);
                break;
            }
            case F64_LE: {
                double v2 = stack.popF64();
                double v1 = stack.popF64();
                stack.pushBoolean(v1 <= v2);
                break;
            }
            case F64_GE: {
                double v2 = stack.popF64();
                double v1 = stack.popF64();
                stack.pushBoolean(v1 >= v2);
                break;
            }
            case I32_WRAP_I64:
                // drop leading bits
                stack.pushI32((int) stack.popI64());
                break;
            case I32_TRUNC_SF32:
            case I32_TRUNC_SF64: {
                double src = ins.getCode().equals(OpCode.I32_TRUNC_SF32) ? stack.popF32() : stack.popF64();
                double f = truncDouble(src);
                if (f > Integer.MAX_VALUE || f < Integer.MIN_VALUE)
                    throw new RuntimeException("trunc" + src + " to i32 failed, math overflow");
                stack.pushI32((int) f);
                break;
            }
            case I32_TRUNC_UF32:
            case I32_TRUNC_UF64: {
                double src = ins.getCode().equals(OpCode.I32_TRUNC_UF32) ? stack.popF32() : stack.popF64();
                double f = truncDouble(src);
                if (f < 0 || f > MAXIMUM_UNSIGNED_I32)
                    throw new RuntimeException("trunc " + src + " to u32 failed, math overflow");
                stack.push((long) f);
                break;
            }
            case I64_EXTEND_SI32:
                stack.pushI64(stack.popI32());
                break;
            case I64_TRUNC_SF32:
            case I64_TRUNC_SF64: {
                double src = ins.getCode().equals(OpCode.I64_TRUNC_SF32) ? stack.popF32() : stack.popF64();
                double f = truncDouble(src);
                if (f > Long.MAX_VALUE || f < Long.MIN_VALUE)
                    throw new RuntimeException("trunc" + src + " to i64 failed, math overflow");
                stack.push((long) f);
                break;
            }
            case I64_TRUNC_UF32:
            case I64_TRUNC_UF64: {
                double src = ins.getCode().equals(OpCode.I64_TRUNC_UF32) ? stack.popF32() : stack.popF64();
                double f = truncDouble(src);
                long l = (long) (f);
                if (f < 0 || l != f)
                    throw new RuntimeException("trunc " + src + " to u64 failed, math overflow");
                stack.push(
                        l
                );
                break;
            }
            case F32_CONVERT_SI32:
                stack.pushF32(stack.popI32());
                break;
            case F32_CONVERT_UI32:
                stack.pushF32(Integer.toUnsignedLong(stack.popI32()));
                break;
            case F32_CONVERT_SI64:
                stack.pushF32(stack.popI64());
                break;
            case F32_CONVERT_UI64: {
                long value = stack.popI64();
                float fValue = (float) (value & UNSIGNED_MASK);
                if (value < 0) {
                    fValue += 0x1.0p63f;
                }
                stack.pushF32(fValue);
                break;
            }
            case F32_DEMOTE_F64:
                stack.pushF32((float) stack.popF64());
                break;
            // force number conversion
            case F64_CONVERT_SI32:
                stack.pushF64(stack.popI32());
                break;
            case F64_CONVERT_UI32:
                stack.pushF64(Integer.toUnsignedLong(stack.popI32()));
                break;
            case F64_CONVERT_SI64:
                stack.pushF64(stack.popI64());
                break;
            case F64_CONVERT_UI64: {
                long value = stack.popI64();
                double dValue = (double) (value & UNSIGNED_MASK);
                if (value < 0) {
                    dValue += 0x1.0p63;
                }
                stack.pushF64(dValue);
            }
            break;
            case F64_PROMOTE_F32:
                stack.pushF64(stack.popF32());
                break;
            default:
                throw new RuntimeException("unknown opcode " + ins.getCode());
        }
    }


    // br l
    private void branch(int l) {
        int idx = this.labelPos - 1 - l;
        long val = labelsArity[idx] ? stack.pop() : 0;
        // Repeat l+1 times
        for (int i = 0; i < l + 1; i++) {
            popAndClearLabel();
        }
        if (labelsArity[idx]) {
            stack.push(val);
        }
        if (labelsLoop[idx]) {
            labelsPC[idx] = 0;
        } else {
            labelsPC[idx] = labelsBody[idx].length;
        }
        int prevPc = labelsPC[idx];
        pushLabel(labelsArity[idx], labelsBody[idx], labelsLoop[idx]);
        labelsPC[labelPos - 1] = prevPc;
    }

}
