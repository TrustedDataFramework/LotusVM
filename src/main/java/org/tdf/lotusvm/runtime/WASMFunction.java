package org.tdf.lotusvm.runtime;

import org.tdf.lotusvm.types.CodeSection;
import org.tdf.lotusvm.types.FunctionType;
import org.tdf.lotusvm.types.Instruction;

import java.util.List;

class WASMFunction implements FunctionInstance {
    private final FunctionType type;
    // params + localvars
    private final ModuleInstanceImpl module;
    private final Instruction[] body;
    private final List<CodeSection.Local> locals;

    WASMFunction(FunctionType type, ModuleInstanceImpl module, Instruction[] body, List<CodeSection.Local> locals) {
        this.type = type;
        this.module = module;
        this.body = body;
        this.locals = locals;
    }

    int getLocals() {
        int ret = 0;
        for (int i = 0; i < locals.size(); i++) {
            ret += locals.get(i).getCount();
        }
        return ret;
    }

    @Override
    public FunctionType getType() {
        return type;
    }

    Frame newFrame(long[] parameters) {
        long[] localVariables = new long[parameters.length + getLocals()];
        System.arraycopy(parameters, 0, localVariables, 0, parameters.length);
        return new Frame(body, type, module, localVariables);
    }

    Frame newFrame(int start, int parameterLen) {
        // init localvars
        return new Frame(body, type, module, start, parameterLen, getLocals() + parameterLen);
    }

    @Override
    public boolean isHost() {
        return false;
    }

    @Override
    public long execute(long[] parameters) throws RuntimeException {
        return newFrame(parameters).execute();
    }


    @Override
    public int parametersLength() {
        return type.getParameterTypes().size();
    }

    @Override
    public int getArity() {
        return type.getResultTypes().size();
    }
}
