package org.tdf.lotusvm.runtime;

import org.tdf.lotusvm.Instruction;
import org.tdf.lotusvm.section.CodeSection;
import org.tdf.lotusvm.types.FunctionType;

import java.util.List;

public class WASMFunction implements FunctionInstance{
    private FunctionType type;
    // params + localvars
    private ModuleInstance module;
    private List<Instruction> body;
    private List<CodeSection.Local> locals;

    @Override
    public FunctionType getType() {
        return type;
    }

    public WASMFunction(FunctionType type, ModuleInstance module, List<Instruction> body, List<CodeSection.Local> locals) {
        this.type = type;
        this.module = module;
        this.body = body;
        this.locals = locals;
    }


    private Frame newFrame(long... parameters) {
        // init localvars
        Register localVariables = new Register(parameters.length +
                locals.stream().map(CodeSection.Local::getCount).reduce(0, Integer::sum));
        for (int i = 0; i < parameters.length; i++) {
            localVariables.setI64(i, parameters[i]);
        }
        localVariables.fillAll();
        Register stack = new Register();
        return new Frame(body, type, module, localVariables, stack);
    }

    @Override
    public boolean isHost() {
        return false;
    }

    @Override
    public long[] execute(long... parameters) throws RuntimeException{
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

    @Override
    public long getGas() {
        return 0;
    }
}
