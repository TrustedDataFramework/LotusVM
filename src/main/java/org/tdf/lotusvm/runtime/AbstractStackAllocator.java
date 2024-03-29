package org.tdf.lotusvm.runtime;


import org.jetbrains.annotations.NotNull;
import org.tdf.lotusvm.common.Constants;
import org.tdf.lotusvm.common.OpCode;
import org.tdf.lotusvm.types.InstructionId;
import org.tdf.lotusvm.types.InstructionPool;
import org.tdf.lotusvm.types.ResultType;
import org.tdf.lotusvm.types.ValueType;

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

    // push a value into a stack
    int popI32() {
        return (int) pop();
    }

    void pushI32(int i) {
        push(i & 0xffffffffL);
    }

    void pushI8(byte x) {
        push(((long) x) & 0xffL);
    }

    void pushI16(short x) {
        push(((long) x) & 0xffffL);
    }

    void pushBoolean(boolean b) {
        push(b ? 1 : 0);
    }

    abstract void pushLabel(boolean arity, long body, boolean loop);

    abstract void branch(int l);

    abstract boolean labelIsEmpty();

    abstract void popFrame();

    abstract void popLabel();

    protected abstract long getFunctionBody();

    protected abstract ValueType getResultType();

    public abstract int currentFrameIndex();

    long[] popLongs(int n) {
        if (n == 0) return Constants.EMPTY_LONGS;
        long[] res = new long[n];
        int index = popN(currentFrameIndex(), n);
        for (int i = 0; i < n; i++) {
            res[i] = getUnchecked(i + index);
        }
        return res;
    }

    long returns() {
        if (getResultType() == null) {
            popFrame();
            return 0;
        }
        long res = pop();
        switch (getResultType()) {
            case F32:
            case I32:
                // shadow bits
                res = res & 0xffffffffL;
        }
        popFrame();
        return res;
    }

    public long execute() throws RuntimeException {
        InstructionPool pool = module.getInsPool();

        pushLabel(getResultType() != null, getFunctionBody(), false);
        while (!labelIsEmpty()) {
            int pc = getPc();
            long body = getInstructions();
            int length = InstructionPool.getInstructionsSize(body);
            if (pc >= length) {
                popLabel();
                continue;
            }
            long ins = pool.getInstructionInArray(body, pc);

            OpCode c = InstructionId.getOpCode(ins);
            if (c.equals(OpCode.RETURN)) {
                return returns();
            }
            setPc(pc + 1);
            invoke(ins);
        }
        module.touchFrameExit();
        return returns();
        // clear stack and local variables
    }

    private int getMemoryOffset(long ins) {
        int r = InstructionId.getLeft32(ins);
        long l = Integer.toUnsignedLong(popI32()) + Integer.toUnsignedLong(r);
        if (Long.compareUnsigned(l, Integer.MAX_VALUE) > 0)
            throw new RuntimeException("memory access overflow");
        return (int) l;
    }

    void invoke(long ins) throws RuntimeException {
        OpCode code = InstructionId.getOpCode(ins);
        module.touchIns(code);
        InstructionPool pool = module.getInsPool();
        Memory mem = module.getMemory();

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
                pushLabel(InstructionId.getResultType(ins) != ResultType.EMPTY, pool.getBranch0(ins), false);
                break;
            case LOOP:
                pushLabel(false, pool.getBranch0(ins), true);
                break;
            case IF: {
                long c = pop();
                if (c != 0) {
                    pushLabel(InstructionId.getResultType(ins) != ResultType.EMPTY, pool.getBranch0(ins), false);
                    break;
                }
                long branch1 = pool.getBranch1(ins);
                if(pool.isNullBranch(branch1))
                    break;
                pushLabel(InstructionId.getResultType(ins) != ResultType.EMPTY, branch1, false);
                break;
            }
            case BR: {
                // the l is non-negative here
                int l = InstructionId.getLeft32(ins);
                branch(l);
                break;
            }
            case BR_IF: {
                long c = pop();
                if (c == 0) {
                    break;
                }
                // the l is non-negative here
                int l = InstructionId.getLeft32(ins);
                branch(l);
                break;
            }
            case BR_TABLE: {
                int operandSize = InstructionId.getOperandSize(ins);
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
            case CALL: {
                int f = InstructionId.getLeft32(ins);
                FunctionInstance function = module.getFunc(f);
                long res;
                if (function.isHost()) {
                    module.touchHostFunc((HostFunction) function);

                    res = function.execute(
                        popLongs(function.getParamSize())
                    );
                } else {
                    pushFrame(f, null);
                    res = execute();
                }

                int resLength = function.getArity();
                if (module.getValidateFunctionType() && resLength != function.getArity()) {
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
                FunctionInstance function = module.getFuncInTable(elementIndex);

                long r;
                if (function.isHost()) {
                    module.touchHostFunc((HostFunction) function);

                    r = function.execute(
                        popLongs(function.getParamSize())
                    );
                } else {
                    pushFrame((int) (elementIndex | TABLE_MASK), null);
                    r = execute();
                }
                if (module.getValidateFunctionType()
                    && !function.getType().equals(module.getTypes().get(InstructionId.getLeft32(ins)))) {
                    throw new RuntimeException("failed exec: signature mismatch in call_indirect expected");
                }
                if (function.getArity() > 0) {
                    push(r);
                }
                break;
            }
            case SELECT: {
                int c = popI32();
                long val2 = pop();
                long val1 = pop();
                if (c != 0) {
                    push(val1);
                    break;
                }
                push(val2);
                break;
            }
            // variable instructions
            case GET_LOCAL:
                push(getLocal(InstructionId.getLeft32(ins)));
                break;
            case SET_LOCAL:
                setLocal(InstructionId.getLeft32(ins), pop());
                break;
            case TEE_LOCAL: {
                long val = pop();
                push(val);
                push(val);
                setLocal(InstructionId.getLeft32(ins), pop());
                break;
            }
            case GET_GLOBAL:
                push(module.getGlobal(InstructionId.getLeft32(ins)));
                break;
            case SET_GLOBAL:
                module.setGlobal(InstructionId.getLeft32(ins), pop());
                break;
            // memory instructions
            case I32_LOAD:
            case I64_LOAD32_U:
                pushI32(
                    mem.load32(getMemoryOffset(ins))
                );
                break;
            case I64_LOAD:
                push(
                    mem.load64(getMemoryOffset(ins))
                );
                break;
            case I32_LOAD8_S:
                pushI32(mem.load8(getMemoryOffset(ins)));
                break;
            case I64_LOAD8_S: {
                push(mem.load8(getMemoryOffset(ins)));
                break;
            }
            case I32_LOAD8_U:
            case I64_LOAD8_U:
                pushI8(mem.load8(getMemoryOffset(ins)));
                break;
            case I32_LOAD16_S: {
                pushI32(mem.load16(getMemoryOffset(ins)));
                break;
            }
            case I64_LOAD16_S:
                push(mem.load16(getMemoryOffset(ins)));
                break;
            case I32_LOAD16_U:
            case I64_LOAD16_U:
                pushI16(mem.load16(getMemoryOffset(ins)));
                break;
            case I64_LOAD32_S:
                push(mem.load32(getMemoryOffset(ins)));
                break;
            case I32_STORE8:
            case I64_STORE8: {
                byte c = (byte) pop();
                mem.storeI8(getMemoryOffset(ins), c);
                break;
            }
            case I32_STORE16:
            case I64_STORE16: {
                short c = (short) pop();
                mem.storeI16(getMemoryOffset(ins), c);
                break;
            }
            case I32_STORE:
            case I64_STORE32: {
                int c = popI32();
                mem.storeI32(getMemoryOffset(ins), c);
                break;
            }
            case I64_STORE: {
                long c = pop();
                mem.storeI64(getMemoryOffset(ins), c);
                break;
            }
            case CURRENT_MEMORY:
                pushI32(mem.getPages());
                break;
            case GROW_MEMORY: {
                int n = popI32();
                int before = mem.getPages() * Memory.PAGE_SIZE;
                int after = (mem.getPages() + n) * Memory.PAGE_SIZE;
                module.touchMemGrow(before, after);
                pushI32(mem.grow(n));
                break;
            }
            case I32_CONST:
                pushI32(InstructionId.getLeft32(ins));
                break;
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

    @NotNull
    @Override
    public ModuleInstanceImpl getModule() {
        return module;
    }

    public void setModule(ModuleInstanceImpl module) {
        this.module = module;
    }
}
