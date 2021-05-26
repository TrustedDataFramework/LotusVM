package org.tdf.lotusvm.runtime;

import lombok.Getter;
import org.tdf.lotusvm.types.CodeSection;
import org.tdf.lotusvm.types.FunctionType;
import org.tdf.lotusvm.types.Instruction;

import java.util.List;

class WASMFunction implements FunctionInstance {
    private final FunctionType type;
    // params + localvars
    private final ModuleInstanceImpl module;

    @Getter
    private final long body;
    private final List<CodeSection.Local> locals;

    WASMFunction(FunctionType type, ModuleInstanceImpl module, long body, List<CodeSection.Local> locals) {
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

    @Override
    public long execute(long[] parameters) {
        throw new UnsupportedOperationException();
    }


    @Override
    public boolean isHost() {
        return false;
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
