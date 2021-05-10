package org.tdf.lotusvm.runtime;


import org.tdf.lotusvm.common.Constants;
import org.tdf.lotusvm.common.OpCode;
import org.tdf.lotusvm.types.FunctionType;
import org.tdf.lotusvm.types.Instruction;
import org.tdf.lotusvm.types.ResultType;

public abstract class AbstractStackAllocator implements StackAllocator{
    protected ModuleInstanceImpl module;

    public ModuleInstanceImpl getModule() {
        return module;
    }

    public void setModule(ModuleInstanceImpl module) {
        this.module = module;
    }

    // push a value into a stack
    void push(long value) {
        push(current(), value);
    }

    long pop() {
        return pop(current());
    }

    int popI32() {
        return (int) pop();
    }

    void pushI32(int i) {
        push(current(), i & 0xffffffffL);
    }

    void pushI8(byte x) {
        push(current(), ((long) x) & 0xffL);
    }

    void pushI16(short x) {
        push(current(), ((long) x) & 0xffffL);
    }

    float popF32() {
        return Float.intBitsToFloat(popI32());
    }

    double popF64() {
        return Double.longBitsToDouble(pop());
    }

    void pushF32(float i) {
        pushI32(Float.floatToIntBits(i));
    }

    void pushF64(double i) {
        push(current(), Double.doubleToLongBits(i));
    }

    void pushBoolean(boolean b) {
        push(current(), b ? 1 : 0);
    }

    long getLocal(int idx) {
        return getLocal(current(), idx);
    }

    void setLocal(int idx, long value) {
        setLocal(current(), idx, value);
    }

    void pushLabel(boolean arity, Instruction[] body, boolean loop) {
        pushLabel(current(), arity, body, loop);
    }

    void branch(int l) {
        branch(current(), l);
    }

    boolean labelIsEmpty() {
        return getLabelSize(current()) == 0;
    }

    void drop() {
        drop(current());
    }

    void popLabel() {
        popLabel(current());
    }

    WASMFunction getFunction() {
        return getFunction(current());
    }

    Instruction[] getBody(int frameId) {
        return getFunction(frameId).getBody();
    }

    Instruction[] getBody() {
        return getFunction().getBody();
    }

    long[] popLongs(int n) {
        if (n == 0) return Constants.EMPTY_LONGS;
        long[] res = new long[n];
        int index = popN(current(), n);
        for (int i = 0; i < n; i++) {
            res[i] = getUnchecked( i + index);
        }
        return res;
    }


    long returns() {
        FunctionType type = getFunction(current()).getType();
        if (type.getResultTypes().size() == 0) {
            drop();
            return 0;
        }
        long res = pop();
        switch (type.getResultTypes().get(0)) {
            case F32:
            case I32:
                // shadow bits
                res = res & 0xffffffffL;
        }
        drop();
        return res;
    }

    public long execute() throws RuntimeException {
        FunctionType type = getFunction().getType();
        pushLabel(type.getResultTypes().size() != 0, getBody(), false);
        while (!labelIsEmpty()) {
            int idx = getLabelSize(current()) - 1;
            int pc = getPc(current(), idx);
            Instruction[] body = getInstructions(current(), idx);
            if (pc >= body.length) {
                popLabel();
                continue;
            }
            Instruction ins = body[pc];
            if (ins.getCode().equals(OpCode.RETURN)) {
                return returns();
            }
            setPc(current(), idx, pc + 1);
            invoke(ins);
        }
        for (int i = 0; i < getModule().hooks.length; i++) {
            getModule().hooks[i].onFrameExit();
        }
        return returns();
        // clear stack and local variables
    }

    void invoke(Instruction ins) throws RuntimeException {
        for (int i = 0; i < getModule().hooks.length; i++) {
            getModule().hooks[i].onInstruction(ins, getModule());
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
                long c = pop();
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
                long c = pop();
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
                int i = popI32();
                // length of l* is operands.cap() - 1
                if (Integer.compareUnsigned(i, ins.getOperandLen() - 1) < 0) {
                    n = ins.getOperandInt(i);
                }
                branch(n);
                break;
            }
            case DROP:
                // Pop the value val from the stack.
                pop();
                break;
//            case RETURN:
//                break;
            case CALL: {
                FunctionInstance function = getModule().functions.get(ins.getOperandInt(0));
                long res;
                if (function.isHost()) {
                    for (int i = 0; i < getModule().hooks.length; i++) {
                        getModule().hooks[i].onHostFunction((HostFunction) function, getModule());
                    }
                    res = function.execute(
                        popLongs(function.parametersLength())
                    );
                } else {
                    pushFrame(ins.getOperandInt(0));
                    res = execute();
                }

                int resLength = function.getArity();
                if (getModule().validateFunctionType && resLength != function.getArity()) {
                    throw new RuntimeException("the result of function " + function + " is not equals to its arity");
                }
                if (function.getArity() > 0) {
                    push(
                        res
                    );
                }
                break;
            }
            case CALL_INDIRECT: {
                int elementIndex = popI32();
                if (elementIndex < 0 || elementIndex >= getModule().table.getFunctions().length) {
                    throw new RuntimeException("undefined element index");
                }
                FunctionInstance function = getModule().table.getFunctions()[elementIndex];
                long r;
                if (function.isHost()) {
                    for (int i = 0; i < getModule().hooks.length; i++) {
                        getModule().hooks[i].onHostFunction((HostFunction) function, getModule());
                    }
                    r = function.execute(
                        popLongs(function.parametersLength())
                    );
                } else {
                    pushFrame((int) (elementIndex | TABLE_MASK));
                    r = execute();
                }
                if (getModule().validateFunctionType && !function.getType().equals(getModule().types.get(ins.getOperandInt(0)))) {
                    throw new RuntimeException("failed exec: signature mismatch in call_indirect expected");
                }
                if (function.getArity() > 0) {
                    push(current(), r);
                }
                break;
            }
            case SELECT: {
                int c = popI32();
                long val2 = pop();
                long val1 = pop();
                if (c != 0) {
                    push(current(), val1);
                    break;
                }
                push(current(), val2);
                break;
            }
            // variable instructions
            case GET_LOCAL:
                push(current(), getLocal(ins.getOperandInt(0)));
                break;
            case SET_LOCAL:
                setLocal(ins.getOperandInt(0), pop());
                break;
            case TEE_LOCAL: {
                long val = pop();
                push(current(), val);
                push(current(), val);
                setLocal(ins.getOperandInt(0), pop());
                break;
            }
            case GET_GLOBAL:
                push(getModule().globals[ins.getOperandInt(0)]);
                break;
            case SET_GLOBAL:
                if (!getModule().globalTypes.get(
                    ins.getOperandInt(0)
                ).isMutable()) throw new RuntimeException("modify a immutable global");
                getModule().globals[ins.getOperandInt(0)] = pop();
                break;
            // memory instructions
            case I32_LOAD:
            case F32_LOAD:
            case I64_LOAD32_U:
                pushI32(
                    getModule().memory.load32(popI32() + ins.getOperandInt(1))
                );
                break;
            case I64_LOAD:
            case F64_LOAD:
                push(
                    getModule().memory.load64(popI32() + ins.getOperandInt(1))
                );
                break;
            case I32_LOAD8_S:
                pushI32(getModule().memory.load8(popI32() + ins.getOperandInt(1)));
                break;
            case I64_LOAD8_S: {
                push(getModule().memory.load8(popI32() + ins.getOperandInt(1)));
                break;
            }
            case I32_LOAD8_U:
            case I64_LOAD8_U:
                pushI8(getModule().memory.load8(popI32() + ins.getOperandInt(1)));
                break;
            case I32_LOAD16_S: {
                pushI32(getModule().memory.load16(popI32() + ins.getOperandInt(1)));
                break;
            }
            case I64_LOAD16_S:
                push(getModule().memory.load16(popI32() + ins.getOperandInt(1)));
                break;
            case I32_LOAD16_U:
            case I64_LOAD16_U:
                pushI16(getModule().memory.load16(popI32() + ins.getOperandInt(1)));
                break;
            case I64_LOAD32_S:
                push(getModule().memory.load32(popI32() + ins.getOperandInt(1)));
                break;
            case I32_STORE8:
            case I64_STORE8: {
                byte c = (byte) pop();
                getModule().memory.storeI8(popI32() + ins.getOperandInt(1), c);
                break;
            }
            case I32_STORE16:
            case I64_STORE16: {
                short c = (short) pop();
                getModule().memory.storeI16(popI32() + ins.getOperandInt(1), c);
                break;
            }
            case I32_STORE:
            case F32_STORE:
            case I64_STORE32: {
                int c = popI32();
                getModule().memory.storeI32(popI32() + ins.getOperandInt(1), c);
                break;
            }
            case I64_STORE:
            case F64_STORE: {
                long c = pop();
                getModule().memory.storeI64(popI32() + ins.getOperandInt(1), c);
                break;
            }
            case CURRENT_MEMORY:
                pushI32(getModule().memory.getPages());
                break;
            case GROW_MEMORY: {
                int n = popI32();
                int before = getModule().memory.getPages() * BaseMemory.PAGE_SIZE;
                int after = (getModule().memory.getPages() + n) * BaseMemory.PAGE_SIZE;
                for (int i = 0; i < getModule().hooks.length; i++) {
                    getModule().hooks[i].onMemoryGrow(before, after);
                }
                pushI32(getModule().memory.grow(n));
                break;
            }
            case I32_CONST:
            case I64_CONST:
            case F32_CONST:
            case F64_CONST:
                push(ins.getOperandLong(0));
                break;
            case I32_CLZ:
                pushI32(Integer.numberOfLeadingZeros(popI32()));
                break;
            case I32_CTZ:
                pushI32(Integer.numberOfTrailingZeros(popI32()));
                break;
            case I32_POPCNT:
                pushI32(Integer.bitCount(popI32()));
                break;
            case I32_ADD:
                pushI32(popI32() + popI32());
                break;
            case I32_MUL:
                pushI32(popI32() * popI32());
                break;
            case I32_DIVS: {
                int v2 = popI32();
                int v1 = popI32();
                pushI32(v1 / v2);
                break;
            }
            case I32_DIVU: {
                int v2 = popI32();
                int v1 = popI32();
                pushI32(Integer.divideUnsigned(v1, v2));
                break;
            }
            case I32_REMS: {
                int v2 = popI32();
                int v1 = popI32();
                pushI32(v1 % v2);
                break;
            }
            case I32_REMU: {
                int v2 = popI32();
                int v1 = popI32();
                pushI32(Integer.remainderUnsigned(v1, v2));
                break;
            }
            case I32_SUB: {
                int v2 = popI32();
                int v1 = popI32();
                pushI32(v1 - v2);
                break;
            }
            case I32_AND:
                pushI32(popI32() & popI32());
                break;
            case I32_OR:
                pushI32(popI32() | popI32());
                break;
            case I32_XOR:
                pushI32(popI32() ^ popI32());
                break;
            case I32_SHL: {
                int v2 = popI32();
                int v1 = popI32();
                pushI32(v1 << v2);
                break;
            }
            case I32_SHRU: {
                int v2 = popI32();
                int v1 = popI32();
                pushI32(v1 >>> v2);
                break;
            }
            case I32_SHRS: {
                int v2 = popI32();
                int v1 = popI32();
                pushI32(v1 >> v2);
                break;
            }
            case I32_ROTL: {
                int v2 = popI32();
                int v1 = popI32();
                pushI32(Integer.rotateLeft(v1, v2));
                break;
            }

            case I32_ROTR: {
                int v2 = popI32();
                int v1 = popI32();
                pushI32(Integer.rotateLeft(v1, -v2));
                break;
            }

            case I32_LES: {
                int v2 = popI32();
                int v1 = popI32();
                pushBoolean(v1 <= v2);
                break;
            }
            case I32_LEU: {
                int v2 = popI32();
                int v1 = popI32();
                pushBoolean(Integer.compareUnsigned(v1, v2) <= 0);
                break;
            }

            case I32_LTS: {
                int v2 = popI32();
                int v1 = popI32();
                pushBoolean(v1 < v2);
                break;
            }
            case I32_LTU: {
                int v2 = popI32();
                int v1 = popI32();
                pushBoolean(Integer.compareUnsigned(v1, v2) < 0);
                break;
            }
            case I32_GTS: {
                int v2 = popI32();
                int v1 = popI32();
                pushBoolean(v1 > v2);
                break;
            }
            case I32_GTU: {
                int v2 = popI32();
                int v1 = popI32();
                pushBoolean(Integer.compareUnsigned(v1, v2) > 0);
                break;
            }
            case I32_GES: {
                int v2 = popI32();
                int v1 = popI32();
                pushBoolean(v1 >= v2);
                break;
            }
            case I32_GEU: {
                int v2 = popI32();
                int v1 = popI32();
                pushBoolean(Integer.compareUnsigned(v1, v2) >= 0);
                break;
            }
            case I32_EQZ:
                pushBoolean(popI32() == 0);
                break;
            case I32_EQ:
                pushBoolean(popI32() == popI32());
                break;
            case I32_NE:
                pushBoolean(popI32() != popI32());
                break;
            case I64_CLZ:
                push(Long.numberOfLeadingZeros(pop()));
                break;
            case I64_CTZ:
                push(Long.numberOfTrailingZeros(pop()));
                break;
            case I64_POPCNT:
                push(Long.bitCount(pop()));
                break;
            case I64_ADD:
                push(pop() + pop());
                break;
            case I64_SUB: {
                long v2 = pop();
                long v1 = pop();
                push(v1 - v2);
                break;
            }
            case I64_MUL:
                push(pop() * pop());
                break;
            case I64_DIVS: {
                long v2 = pop();
                long v1 = pop();
                push(v1 / v2);
                break;
            }
            case I64_DIVU: {
                long v2 = pop();
                long v1 = pop();
                push(Long.divideUnsigned(v1, v2));
                break;
            }
            case I64_REMS: {
                long v2 = pop();
                long v1 = pop();
                push(v1 % v2);
                break;
            }
            case I64_REMU: {
                long v2 = pop();
                long v1 = pop();
                push(Long.remainderUnsigned(v1, v2));
                break;
            }
            case I64_AND:
                push(pop() & pop());
                break;
            case I64_OR:
                push(pop() | pop());
                break;
            case I64_XOR:
                push(pop() ^ pop());
                break;
            case I64_SHL: {
                long v2 = pop();
                long v1 = pop();
                push(v1 << v2);
                break;
            }
            case I64_SHRS: {
                long v2 = pop();
                long v1 = pop();
                push(v1 >> v2);
                break;
            }
            case I64_SHRU: {
                long v2 = pop();
                long v1 = pop();
                push(v1 >>> v2);
                break;
            }
            case I64_ROTL: {
                long v2 = pop();
                long v1 = pop();
                push(Long.rotateLeft(v1, (int) v2));
                break;
            }
            case I64_ROTR: {
                long v2 = pop();
                long v1 = pop();
                push(Long.rotateLeft(v1, -(int) v2));
                break;
            }
            case I64_EQ:
                pushBoolean(pop() == pop());
                break;
            case I64_EQZ:
                pushBoolean(pop() == 0);
                break;
            case I64_NE:
                pushBoolean(pop() != pop());
                break;
            case I64_LTS: {
                long v2 = pop();
                long v1 = pop();
                pushBoolean(v1 < v2);
                break;
            }
            case I64_LTU: {
                long v2 = pop();
                long v1 = pop();
                pushBoolean(Long.compareUnsigned(v1, v2) < 0);
                break;
            }

            case I64_GTS: {
                long v2 = pop();
                long v1 = pop();
                pushBoolean(v1 > v2);
                break;
            }
            case I64_GTU: {
                long v2 = pop();
                long v1 = pop();
                pushBoolean(Long.compareUnsigned(v1, v2) > 0);
                break;
            }
            case I64_LEU: {
                long v2 = pop();
                long v1 = pop();
                pushBoolean(Long.compareUnsigned(v1, v2) <= 0);
                break;
            }
            case I64_LES: {
                long v2 = pop();
                long v1 = pop();
                pushBoolean(v1 <= v2);
                break;
            }
            case I64_GES: {
                long v2 = pop();
                long v1 = pop();
                pushBoolean(v1 >= v2);
                break;
            }
            case I64_GEU: {
                long v2 = pop();
                long v1 = pop();
                pushBoolean(Long.compareUnsigned(v1, v2) >= 0);
                break;
            }
            case F32_ABS:
                pushF32(Math.abs(popF32()));
                break;
            case F32_NEG:
                pushF32(-popF32());
                break;
            case F32_CEIL:
                pushF32((float) Math.ceil(popF32()));
                break;
            case F32_FLOOR:
                pushF32((float) Math.floor(popF32()));
                break;
            case F32_TRUNC: {
                pushF32(truncFloat(popF32()));
                break;
            }
            case F32_NEAREST: {
                float f = popF32();
                pushF32((int) (f + (float) (Math.copySign(0.5, f))));
                break;
            }
            case F32_SQRT: {
                pushF32((float) Math.sqrt(popF32()));
                break;
            }
            case F32_ADD:
                pushF32(popF32() + popF32());
                break;
            case F32_SUB: {
                float v2 = popF32();
                float v1 = popF32();
                pushF32(v1 - v2);
                break;
            }
            case F32_MUL:
                pushF32(popF32() * popF32());
                break;
            case F32_DIV: {
                float v2 = popF32();
                float v1 = popF32();
                pushF32(v1 / v2);
                break;
            }
            case F32_MIN:
                pushF32(Math.min(popF32(), popF32()));
                break;
            case F32_MAX:
                pushF32(Math.max(popF32(), popF32()));
                break;
            case F32_COPYSIGN:
                pushF32(Math.copySign(popF32(), popF32()));
                break;
            case F32_EQ:
                pushBoolean(popF32() == popF32());
                break;
            case F32_NE:
                pushBoolean(popF32() != popF32());
                break;
            case F32_LT: {
                float v2 = popF32();
                float v1 = popF32();
                pushBoolean(v1 < v2);
                break;
            }
            case F32_GT: {
                float v2 = popF32();
                float v1 = popF32();
                pushBoolean(v1 > v2);
                break;
            }
            case F32_LE: {
                float v2 = popF32();
                float v1 = popF32();
                pushBoolean(v1 <= v2);
                break;
            }
            case F32_GE: {
                float v2 = popF32();
                float v1 = popF32();
                pushBoolean(v1 >= v2);
                break;
            }
            case F64_ABS:
                pushF64(Math.abs(popF64()));
                break;
            case F64_NEG:
                pushF64(-popF64());
                break;
            case F64_CEIL:
                pushF64(Math.ceil(popF64()));
                break;
            case F64_FLOOR:
                pushF64(Math.floor(popF64()));
                break;
            case F64_TRUNC: {
                double d = popF64();
                pushF64(truncDouble(d));
                break;
            }
            case F64_NEAREST: {
                double d = popF64();
                pushF64((long) (d + Math.copySign(0.5, d)));
                break;
            }
            case F64_SQRT:
                pushF64(Math.sqrt(popF64()));
                break;
            case F64_ADD:
                pushF64(popF64() + popF64());
                break;
            case F64_SUB: {
                double v2 = popF64();
                double v1 = popF64();
                pushF64(v1 - v2);
                break;
            }
            case F64_MUL:
                pushF64(popF64() * popF64());
                break;
            case F64_DIV: {
                double v2 = popF64();
                double v1 = popF64();
                pushF64(v1 / v2);
                break;
            }
            case F64_MIN:
                pushF64(Math.min(popF64(), popF64()));
                break;
            case F64_MAX:
                pushF64(Math.max(popF64(), popF64()));
                break;
            case F64_COPYSIGN:
                pushF64(Math.copySign(popF64(), popF64()));
                break;
            case F64_EQ:
                pushBoolean(popF64() == popF64());
                break;
            case F64_NE:
                pushBoolean(popF64() != popF64());
                break;
            case F64_LT: {
                double v2 = popF64();
                double v1 = popF64();
                pushBoolean(v1 < v2);
                break;
            }
            case F64_GT: {
                double v2 = popF64();
                double v1 = popF64();
                pushBoolean(v1 > v2);
                break;
            }
            case F64_LE: {
                double v2 = popF64();
                double v1 = popF64();
                pushBoolean(v1 <= v2);
                break;
            }
            case F64_GE: {
                double v2 = popF64();
                double v1 = popF64();
                pushBoolean(v1 >= v2);
                break;
            }
            case I32_WRAP_I64:
                // drop leading bits
                pushI32((int) pop());
                break;
            case I32_TRUNC_SF32:
            case I32_TRUNC_SF64: {
                double src = ins.getCode().equals(OpCode.I32_TRUNC_SF32) ? popF32() : popF64();
                double f = truncDouble(src);
                if (f > Integer.MAX_VALUE || f < Integer.MIN_VALUE)
                    throw new RuntimeException("trunc" + src + " to i32 failed, math overflow");
                pushI32((int) f);
                break;
            }
            case I32_TRUNC_UF32:
            case I32_TRUNC_UF64: {
                double src = ins.getCode().equals(OpCode.I32_TRUNC_UF32) ? popF32() : popF64();
                double f = truncDouble(src);
                if (f < 0 || f > MAXIMUM_UNSIGNED_I32)
                    throw new RuntimeException("trunc " + src + " to u32 failed, math overflow");
                push((long) f);
                break;
            }
            case I64_EXTEND_SI32:
                push(popI32());
                break;
            case I64_TRUNC_SF32:
            case I64_TRUNC_SF64: {
                double src = ins.getCode().equals(OpCode.I64_TRUNC_SF32) ? popF32() : popF64();
                double f = truncDouble(src);
                if (f > Long.MAX_VALUE || f < Long.MIN_VALUE)
                    throw new RuntimeException("trunc" + src + " to i64 failed, math overflow");
                push((long) f);
                break;
            }
            case I64_TRUNC_UF32:
            case I64_TRUNC_UF64: {
                double src = ins.getCode().equals(OpCode.I64_TRUNC_UF32) ? popF32() : popF64();
                double f = truncDouble(src);
                long l = (long) (f);
                if (f < 0 || l != f)
                    throw new RuntimeException("trunc " + src + " to u64 failed, math overflow");
                push(
                    l
                );
                break;
            }
            case F32_CONVERT_SI32:
                pushF32(popI32());
                break;
            case F32_CONVERT_UI32:
                pushF32(Integer.toUnsignedLong(popI32()));
                break;
            case F32_CONVERT_SI64:
                pushF32(pop());
                break;
            case F32_CONVERT_UI64: {
                long value = pop();
                float fValue = (float) (value & UNSIGNED_MASK);
                if (value < 0) {
                    fValue += 0x1.0p63f;
                }
                pushF32(fValue);
                break;
            }
            case F32_DEMOTE_F64:
                pushF32((float) popF64());
                break;
            // force number conversion
            case F64_CONVERT_SI32:
                pushF64(popI32());
                break;
            case F64_CONVERT_UI32:
                pushF64(Integer.toUnsignedLong(popI32()));
                break;
            case F64_CONVERT_SI64:
                pushF64(pop());
                break;
            case F64_CONVERT_UI64: {
                long value = pop();
                double dValue = (double) (value & UNSIGNED_MASK);
                if (value < 0) {
                    dValue += 0x1.0p63;
                }
                pushF64(dValue);
            }
            break;
            case F64_PROMOTE_F32:
                pushF64(popF32());
                break;
            default:
                throw new RuntimeException("unknown opcode " + ins.getCode());
        }
    }



    // math trunc
    protected static double truncDouble(double d) {
        if (d > 0) {
            return Math.floor(d);
        } else {
            return Math.ceil(d);
        }
    }

    protected static float truncFloat(float f) {
        return (float) truncDouble(f);
    }
}
