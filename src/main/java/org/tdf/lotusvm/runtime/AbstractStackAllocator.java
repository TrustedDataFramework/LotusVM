package org.tdf.lotusvm.runtime;


import org.tdf.lotusvm.common.Constants;
import org.tdf.lotusvm.common.OpCode;
import org.tdf.lotusvm.types.InstructionPool;
import org.tdf.lotusvm.types.ResultType;
import org.tdf.lotusvm.types.ValueType;

import java.util.Objects;

public abstract class AbstractStackAllocator implements StackAllocator {
    protected final int maxFrames;
    protected final int maxLabelSize;
    protected final int maxStackSize;
    protected ModuleInstanceImpl module;

    protected AbstractStackAllocator(int maxStackSize, int maxFrames, int maxLabelSize) {
        if (maxStackSize <= 0 || maxFrames <= 0 || maxLabelSize <= 0)
            throw new RuntimeException("invalid limits <= 0" + maxStackSize + " " + maxFrames + " " + maxLabelSize);
        this.maxFrames = maxFrames;
        this.maxLabelSize = maxLabelSize;
        this.maxStackSize = maxStackSize;
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

    void pushBoolean(boolean b) {
        push(current(), b ? 1 : 0);
    }

    long getLocal(int idx) {
        return getLocal(current(), idx);
    }

    void setLocal(int idx, long value) {
        setLocal(current(), idx, value);
    }

    void pushLabel(boolean arity, long body, boolean loop) {
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

    protected abstract long getBody();

    protected abstract ValueType getResultType();

    long[] popLongs(int n) {
        if (n == 0) return Constants.EMPTY_LONGS;
        long[] res = new long[n];
        int index = popN(current(), n);
        for (int i = 0; i < n; i++) {
            res[i] = getUnchecked(i + index);
        }
        return res;
    }

    long returns() {
        if (getResultType() == null) {
            drop();
            return 0;
        }
        long res = pop();
        switch (getResultType()) {
            case F32:
            case I32:
                // shadow bits
                res = res & 0xffffffffL;
        }
        drop();
        return res;
    }

    public long execute() throws RuntimeException {
        InstructionPool pool = module.insPool;
        pushLabel(getResultType() != null, getBody(), false);
        while (!labelIsEmpty()) {
            int idx = getLabelSize(current()) - 1;
            int pc = getPc(current(), idx);
            long body = getInstructions(current(), idx);
            int length = InstructionPool.getInstructionsSize(body);
            if (pc >= length) {
                popLabel();
                continue;
            }
            int ins = pool.getInstructionInArray(body, pc);

            OpCode c = pool.getOpCode(ins);
            if (c.equals(OpCode.RETURN)) {
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

    private int getMemoryOffset(int ins) {
        int offset = module.insPool.getMemoryBase(ins);
        long l = Integer.toUnsignedLong(popI32()) + Integer.toUnsignedLong(offset);
        if (Long.compareUnsigned(l, 0x7FFFFFFFL) > 0)
            throw new RuntimeException("memory access overflow");
        return (int) l;
    }

    void invoke(int ins) throws RuntimeException {
        OpCode code = module.insPool.getOpCode(ins);
        InstructionPool pool = module.insPool;
        for (int i = 0; i < getModule().hooks.length; i++) {
            getModule().hooks[i].onInstruction(code, getModule());
        }
        switch (code) {
            // these operations are essentially no-ops.
            case NOP:
            case I32_REINTERPRET_F32:
            case I64_REINTERPRET_F64:
            case I64_EXTEND_UI32:
                break;
            case UNREACHABLE:
                throw new RuntimeException("exec: reached unreachable");
                // parametric instructions
            case BLOCK:
                pushLabel(Objects.requireNonNull(pool.getResultType(ins)) != ResultType.EMPTY, pool.getBranch0(ins), false);
                break;
            case LOOP:
                pushLabel(false, pool.getBranch0(ins), true);
                break;
            case IF: {
                long c = pop();
                if (c != 0) {
                    pushLabel(Objects.requireNonNull(pool.getResultType(ins)) != ResultType.EMPTY, pool.getBranch0(ins), false);
                    break;
                }
                if (!pool.isNullBranch(ins, 1)) {
                    pushLabel(Objects.requireNonNull(pool.getResultType(ins)) != ResultType.EMPTY, pool.getBranch1(ins), false);
                }
                break;
            }
            case BR: {
                // the l is non-negative here
                int l = pool.getStackBase(ins);
                branch(l);
                break;
            }
            case BR_IF: {
                long c = pop();
                if (c == 0) {
                    break;
                }
                // the l is non-negative here
                int l = pool.getStackBase(ins);
                branch(l);
                break;
            }
            case BR_TABLE: {
                int operandSize = pool.getOperandsSize(ins);
                // n is non-negative
                int n = pool.getOperandAsInt(ins, operandSize - 1);
                // cannot determine sign of i
                int i = popI32();
                // length of l* is operands.cap() - 1
                if (Integer.compareUnsigned(i, operandSize - 1) < 0) {
                    n = pool.getOperandAsInt(ins, i);
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
                FunctionInstance function = getModule().functions.get(pool.getStackBase(ins));
                long res;
                if (function.isHost()) {
                    for (int i = 0; i < getModule().hooks.length; i++) {
                        getModule().hooks[i].onHostFunction((HostFunction) function, getModule());
                    }
                    res = function.execute(
                        popLongs(function.parametersLength())
                    );
                } else {
                    pushFrame(pool.getStackBase(ins), null);
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
                    pushFrame((int) (elementIndex | TABLE_MASK), null);
                    r = execute();
                }
                if (getModule().validateFunctionType && !function.getType().equals(getModule().types.get(pool.getOperandAsInt(ins, 0)))) {
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
                push(current(), getLocal(pool.getStackBase(ins)));
                break;
            case SET_LOCAL:
                setLocal(pool.getStackBase(ins), pop());
                break;
            case TEE_LOCAL: {
                long val = pop();
                push(current(), val);
                push(current(), val);
                setLocal(pool.getStackBase(ins), pop());
                break;
            }
            case GET_GLOBAL:
                push(getModule().globals[pool.getStackBase(ins)]);
                break;
            case SET_GLOBAL:
                if (!getModule().globalTypes.get(pool.getStackBase(ins)).isMutable())
                    throw new RuntimeException("modify a immutable global");
                getModule().globals[pool.getStackBase(ins)] = pop();
                break;
            // memory instructions
            case I32_LOAD:
            case I64_LOAD32_U:
                pushI32(
                    getModule().memory.load32(getMemoryOffset(ins))
                );
                break;
            case I64_LOAD:
                push(
                    getModule().memory.load64(getMemoryOffset(ins))
                );
                break;
            case I32_LOAD8_S:
                pushI32(getModule().memory.load8(getMemoryOffset(ins)));
                break;
            case I64_LOAD8_S: {
                push(getModule().memory.load8(getMemoryOffset(ins)));
                break;
            }
            case I32_LOAD8_U:
            case I64_LOAD8_U:
                pushI8(getModule().memory.load8(getMemoryOffset(ins)));
                break;
            case I32_LOAD16_S: {
                pushI32(getModule().memory.load16(getMemoryOffset(ins)));
                break;
            }
            case I64_LOAD16_S:
                push(getModule().memory.load16(getMemoryOffset(ins)));
                break;
            case I32_LOAD16_U:
            case I64_LOAD16_U:
                pushI16(getModule().memory.load16(getMemoryOffset(ins)));
                break;
            case I64_LOAD32_S:
                push(getModule().memory.load32(getMemoryOffset(ins)));
                break;
            case I32_STORE8:
            case I64_STORE8: {
                byte c = (byte) pop();
                getModule().memory.storeI8(getMemoryOffset(ins), c);
                break;
            }
            case I32_STORE16:
            case I64_STORE16: {
                short c = (short) pop();
                getModule().memory.storeI16(getMemoryOffset(ins), c);
                break;
            }
            case I32_STORE:
            case I64_STORE32: {
                int c = popI32();
                getModule().memory.storeI32(getMemoryOffset(ins), c);
                break;
            }
            case I64_STORE: {
                long c = pop();
                getModule().memory.storeI64(getMemoryOffset(ins), c);
                break;
            }
            case CURRENT_MEMORY:
                pushI32(getModule().memory.getPages());
                break;
            case GROW_MEMORY: {
                int n = popI32();
                int before = getModule().memory.getPages() * Memory.PAGE_SIZE;
                int after = (getModule().memory.getPages() + n) * Memory.PAGE_SIZE;
                for (int i = 0; i < getModule().hooks.length; i++) {
                    getModule().hooks[i].onMemoryGrow(before, after);
                }
                pushI32(getModule().memory.grow(n));
                break;
            }
            case I32_CONST:
            case I64_CONST:
                push(pool.getOperand(ins, 0));
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
                if (v1 == 0x80000000 && v2 == -1)
                    throw new RuntimeException("math over flow: divide i32.min_value by -1");
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
                if (v1 == 0x8000000000000000L && v2 == -1)
                    throw new RuntimeException("math overflow: divide i64.min_value by -1");
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
            case I32_WRAP_I64:
                // drop leading bits
                pushI32((int) pop());
                break;
            case I64_EXTEND_SI32:
                push(popI32());
                break;
            case F32_STORE:
            case F64_STORE:
            case F32_LOAD:
            case F64_LOAD:
            case F32_REINTERPRET_I32:
            case F64_REINTERPRET_I64:
            case F32_CONST:
            case F64_CONST:
            case F32_ABS:
            case F32_NEG:
            case F32_CEIL:
            case F32_FLOOR:
            case F32_TRUNC:
            case F32_NEAREST:
            case F32_SQRT:
            case F32_ADD:
            case F32_SUB:
            case F32_MUL:
            case F32_DIV:
            case F32_MIN:
            case F32_MAX:
            case F32_COPYSIGN:
            case F32_EQ:
            case F32_NE:
            case F32_LT:
            case F32_GT:
            case F32_LE:
            case F32_GE:
            case F64_ABS:
            case F64_NEG:
            case F64_CEIL:
            case F64_FLOOR:
            case F64_TRUNC:
            case F64_NEAREST:
            case F64_SQRT:
            case F64_ADD:
            case F64_SUB:
            case F64_MUL:
            case F64_DIV:
            case F64_MIN:
            case F64_MAX:
            case F64_COPYSIGN:
            case F64_EQ:
            case F64_NE:
            case F64_LT:
            case F64_GT:
            case F64_LE:
            case F64_GE:
            case I32_TRUNC_SF32:
            case I32_TRUNC_SF64:
            case I32_TRUNC_UF32:
            case I32_TRUNC_UF64:
            case I64_TRUNC_SF32:
            case I64_TRUNC_SF64:
            case I64_TRUNC_UF32:
            case I64_TRUNC_UF64:
            case F32_CONVERT_SI32:
            case F32_CONVERT_UI32:
            case F32_CONVERT_SI64:
            case F32_CONVERT_UI64:
            case F32_DEMOTE_F64:
            case F64_CONVERT_SI32:
            case F64_CONVERT_UI32:
            case F64_CONVERT_SI64:
            case F64_CONVERT_UI64:
            case F64_PROMOTE_F32:
                throw new UnsupportedOperationException("float number op " + code.name + " is not allowed");
            default:
                throw new RuntimeException("unknown opcode " + code);
        }
    }
}
