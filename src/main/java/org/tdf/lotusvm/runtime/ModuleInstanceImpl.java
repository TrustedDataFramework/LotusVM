package org.tdf.lotusvm.runtime;

import lombok.Getter;
import org.tdf.lotusvm.ModuleInstance;
import org.tdf.lotusvm.common.Register;
import org.tdf.lotusvm.types.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A module instance is the runtime representation of a module.
 * It is created by instantiating a module, and collects runtime representations of all entities that are imported,
 * deﬁned, or exported by the module.
 */
public class ModuleInstanceImpl implements ModuleInstance {
    // globals
    Register globals = new Register(new long[0]);

    @Getter
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

    // hooks
    Set<Hook> hooks;

    // exported functions
    Map<String, FunctionInstance> exports = new HashMap<>();

    List<FunctionType> types;

    public ModuleInstanceImpl(Builder builder) {
        Module module = new Module(builder.getBinary());
        types = module.getTypeSection().getFunctionTypes();
        hooks = builder.getHooks();

        Map<String, HostFunction> functionsMap =
                builder.getHostFunctions().stream()
                        .collect(Collectors.toMap(HostFunction::getName, Function.identity()));

        // imports
        if (module.getImportSection() != null) {
            for (ImportSection.Import imp : module.getImportSection().getImports()) {
                if (!imp.getType().equals(ImportType.TYPE_INDEX)) {
                    continue;
                }
                HostFunction func = functionsMap.get(imp.getName());
                if (func == null) {
                    throw new RuntimeException("unsupported host function " + imp.getName());
                }
                if (!module.getTypeSection().getFunctionTypes().get(imp.getTypeIndex()).equals(func.getType())) {
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
        if (module.getGlobalSection() != null && builder.isInitGlobals()) {
            globals = new Register(module.getGlobalSection().getGlobals().size());
            for (int i = 0; i < globals.getData().length; i++) {
                globals.set(i, executeExpression(
                        module.getGlobalSection().getGlobals().get(i).getExpression(),
                        module.getGlobalSection().getGlobals().get(i).getGlobalType().getValueType()
                ));
            }
        }
        if (builder.getGlobals() != null) {
            globals = new Register(builder.getGlobals());
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
                if (offset < 0) throw new RuntimeException("invalid offset, overflow Integer.MAX_VALUE");
                table.putElements(offset,
                        Arrays.stream(x.getFunctionIndex()).mapToObj(i -> functions.get(i)).collect(Collectors.toList())
                );
            });
        }

        if (module.getMemorySection() != null) {
            memory = new Memory(module.getMemorySection().getMemories()
                    .get(0));
        }

        // put data into memory
        if (module.getDataSection() != null && builder.isInitMemory()) {
            module.getDataSection().getDataSegments().forEach(x -> {
                int offset = (int) executeExpression(x.getExpression(), ValueType.I32);
                memory.put(offset, x.getInit());
            });
        }

        if (builder.getMemory() != null && memory != null) {
            memory.copyFrom(builder.getMemory());
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

    @Override
    public long[] getGlobals() {
        return globals.getData();
    }

    @Override
    public byte[] getMemory() {
        return memory.getData();
    }

    @Override
    public long[] execute(int functionIndex, long... parameters) {
        return functions.get(functionIndex).execute(parameters);
    }

    @Override
    public long[] execute(String funcName, long... parameters) throws RuntimeException {
        return exports.get(funcName).execute(parameters);
    }


    private long executeExpression(List<Instruction> instructions, ValueType type) {
        return new Frame(instructions, new FunctionType(Collections.emptyList(), Collections.singletonList(type)), this,
                new Register(), new Register()).execute()[0];
    }

    @Override
    public boolean hasExport(String funcName) {
        return exports.containsKey(funcName);
    }
}
