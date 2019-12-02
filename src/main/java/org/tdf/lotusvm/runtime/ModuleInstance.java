package org.tdf.lotusvm.runtime;

import lombok.*;
import org.tdf.lotusvm.Constants;
import org.tdf.lotusvm.Instruction;
import org.tdf.lotusvm.section.*;
import org.tdf.lotusvm.types.FunctionType;
import org.tdf.lotusvm.types.GlobalType;
import org.tdf.lotusvm.types.ValueType;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A module instance is the runtime representation of a module.
 * It is created by instantiating a module, and collects runtime representations of all entities that are imported,
 * deﬁned, or exported by the module.
 */
@Getter
public class ModuleInstance {
    // globals
    Register globals = new Register(new long[0]);
    List<GlobalType> globalTypes = new ArrayList<>();

    // memories
    // In the current version of WebAssembly, all memory instructions implicitly operate on memory index 0.
    // This restriction may be lifted in future versions.
    Memory memory = new Memory();

    // In the current version of WebAssembly, at most one table may be defined or imported in a single module,
    // and all constructs implicitly reference this table 0. This restriction may be lifted in future versions.
    Table table;

    // all functions
    List<FunctionInstance> functions = new ArrayList<>();

    // main function
    // The start function is intended for initializing the state of a module. The module and its exports are not
    // accessible before this initialization has completed.
    FunctionInstance startFunction;

    // exported functions
    Map<String, FunctionInstance> exports = new HashMap<>();

    List<FunctionType> types;

    long gas;

    long gasLimit;

    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    @Builder
    public static class Config{
        @Builder.Default
        private boolean initGlobals = true;
        @Builder.Default
        private boolean initMemory = true;
        @Builder.Default
        private Set<HostFunction> hostFunctions = new HashSet<>();
        @Builder.Default
        private long gasLimit = Long.MAX_VALUE;

        private byte[] binary;
        private long[] globals;
        private byte[] memory;
    }

    public ModuleInstance(Config config) throws Exception{
        Module module = new Module(config.getBinary());
        types = module.getTypeSection().getFunctionTypes();


        Map<String, HostFunction> functionsMap =
                config.hostFunctions.stream()
                .collect(Collectors.toMap(HostFunction::getName, Function.identity()));

        gasLimit = config.gasLimit;

        // imports
        if(module.getImportSection() != null){
            for(ImportSection.Import imp: module.getImportSection().getImports()){
                if(!imp.getType().equals(ImportType.TYPE_INDEX)){
                    continue;
                }
                HostFunction func = functionsMap.get(imp.getName());
                if(func == null){
                    throw new RuntimeException("unsupported host function " + imp.getName());
                }
                if(!module.getTypeSection().getFunctionTypes().get(imp.getTypeIndex()).equals(func.getType())){
                    throw new RuntimeException("invalid function type");
                }
                func.setInstance(this);
                functions.add(func);
            }
        }

        // init global variables
        if (module.getGlobalSection() != null) {
            globalTypes = module.getGlobalSection()
                    .getGlobals().stream().map(GlobalSection.Global::getGlobalType)
                    .collect(Collectors.toList());
        }
        if(module.getGlobalSection() != null && config.initGlobals){
            globals = new Register(module.getGlobalSection().getGlobals().size());
            for (int i = 0; i < globals.getData().length; i++) {
                globals.set(i, executeExpression(
                        module.getGlobalSection().getGlobals().get(i).getExpression(),
                        module.getGlobalSection().getGlobals().get(i).getGlobalType().getValueType()
                ));
            }
        }
        if(config.globals != null){
            globals = new Register(config.globals);
        }

        // init tables
        if (module.getTableSection() != null) {
            table = new Table(module.getTableSection().getTableTypes().get(0).getLimit());
        }

        // init function instances
        for (int i = 0; i < module.getFunctionSection().getTypeIndices().length; i++) {
            int typeIndex = module.getFunctionSection().getTypeIndices()[i];
            CodeSection.Code code = module.getCodeSection().getCodes().get(i);
            FunctionType type = module.getTypeSection().getFunctionTypes().get(typeIndex);
            functions.add(
                    new WASMFunction(type, this, code.getCode().getExpression(), code.getCode().getLocals())
            );
        }

        // init elements
        if (module.getElementSection() != null) {
            module.getElementSection().getElements().forEach(x -> {
                int offset = (int) executeExpression(x.getExpression(), ValueType.I32);
                if(offset < 0) throw new RuntimeException("invalid offset, overflow Integer.MAX_VALUE");
                table.putElements(offset,
                        Arrays.stream(x.getFunctionIndex()).mapToObj(i -> functions.get(i)).collect(Collectors.toList())
                );
            });
        }

        if (module.getMemorySection() != null) {
            memory = new Memory(module.getMemorySection().getMemories()
                    .get(0));
        }

        if(config.initMemory){
            gas += memory.getPageSize() * Constants.MEMORY_GROW_GAS_USAGE;
        }

        // put data into memory
        if (module.getDataSection() != null && config.initMemory) {
            module.getDataSection().getDataSegments().forEach(x -> {
                int offset = (int) executeExpression(x.getExpression(), ValueType.I32);
                memory.put(offset, x.getInit());
            });
        }

        if(config.memory != null && memory != null){
            memory.copyFrom(config.memory);
        }

        // load and execute start function
        if (module.getStartSection() != null) {
            startFunction = functions.get(module.getStartSection().getFunctionIndex());
            startFunction.execute();
        }

        // exports
        if (module.getExportSection() != null) {
            module.getExportSection().getExports().stream()
                    .filter(x -> x.getType() == ExportSection.ExportType.FUNCTION_INDEX)
                    .forEach(x -> exports.put(x.getName(), functions.get(x.getIndex())));
        }
    }

    public long[] execute(int functionIndex, long... parameters) {
        return functions.get(functionIndex).execute(parameters);
    }

    public long[] execute(String funcName, long... parameters) throws RuntimeException {
        return exports.get(funcName).execute(parameters);
    }


    private long executeExpression(List<Instruction> instructions, ValueType type) {
        return new Frame(instructions, new FunctionType(new ArrayList<>(), Collections.singletonList(type)), this,
                new Register(), new Register()).execute()[0];
    }
}
