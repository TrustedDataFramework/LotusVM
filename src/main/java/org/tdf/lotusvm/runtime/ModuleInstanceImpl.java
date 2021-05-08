package org.tdf.lotusvm.runtime;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import org.tdf.lotusvm.ModuleInstance;
import org.tdf.lotusvm.types.*;
import org.tdf.lotusvm.types.Module;

import java.util.*;
import java.util.stream.Collectors;

import static org.tdf.lotusvm.common.Constants.EMPTY_LONGS;

/**
 * A module instance is the runtime representation of a module.
 * It is created by instantiating a module, and collects runtime representations of all entities that are imported,
 * deﬁned, or exported by the module.
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ModuleInstanceImpl implements ModuleInstance {
    // globals
    long[] globals;

    @Getter
    List<GlobalType> globalTypes = Collections.emptyList();

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
    Hook[] hooks;
    // exported functions
    Map<String, FunctionInstance> exports;
    List<FunctionType> types;
    boolean validateFunctionType;

    StackProvider stackProvider = new LimitedStackProvider(256, 32768);

    public ModuleInstanceImpl(Builder builder) {
        Module module = builder.getModule() == null ? new Module(builder.getBinary()) : builder.getModule();
        types = module.getTypeSection().getFunctionTypes();
        hooks = new ArrayList<>(builder.getHooks()).toArray(new Hook[]{});
        this.validateFunctionType = builder.isValidateFunctionType();

        Map<String, HostFunction> functionsMap = new HashMap<>();

        for (HostFunction f : builder.getHostFunctions()) {
            if (functionsMap.containsKey(f.getName()))
                throw new RuntimeException("create module instance failed: duplicated host function " + f.getName());
            functionsMap.put(f.getName(), f);
            for (String alias : f.getAlias()) {
                if (functionsMap.containsKey(alias))
                    throw new RuntimeException("create module instance failed: duplicated host function " + alias);
                functionsMap.put(alias, f);
            }
        }


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
//                if (!module.getTypeSection().getFunctionTypes().get(imp.getTypeIndex()).equals(func.getType())) {
//                    throw new RuntimeException("invalid function type: " + func.getName());
//                }
                func.instance = this;
                functions.add(func);
            }
        }

        // init global variables
        if (module.getGlobalSection() != null) {
            globalTypes = module.getGlobalSection()
                    .getGlobals().stream().map(GlobalSection.Global::getGlobalType)
                    .collect(Collectors.toList());
        }
        if (module.getGlobalSection() != null && builder.getGlobals() == null) {
            globals = new long[module.getGlobalSection().getGlobals().size()];
            for (int i = 0; i < globals.length; i++) {
                globals[i] = executeExpression(
                        module.getGlobalSection().getGlobals().get(i).getExpression(),
                        module.getGlobalSection().getGlobals().get(i).getGlobalType().getValueType()
                );
            }
        }
        if (builder.getGlobals() != null) {
            globals = builder.getGlobals();
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
        if (module.getDataSection() != null && builder.getMemory() == null) {
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
            startFunction.execute(EMPTY_LONGS);
        }

        // exports
        if (module.getExportSection() != null) {
            exports = new HashMap<>();
            module.getExportSection().getExports().stream()
                    .filter(x -> x.getType() == ExportSection.ExportType.FUNCTION_INDEX)
                    .forEach(x -> exports.put(x.getName(), functions.get(x.getIndex())));
        }
    }

    public Set<Hook> getHooks() {
        return new HashSet<>(Arrays.asList(hooks));
    }

    @Override
    public void setHooks(Set<Hook> hooks) {
        this.hooks = new ArrayList<>(hooks).toArray(new Hook[]{});
    }

    @Override
    public long[] getGlobals() {
        return globals;
    }

    @Override
    public void setGlobals(@NonNull long[] globals) {
        if (globals.length != globalTypes.size())
            throw new IllegalArgumentException("length of globals should be " + globalTypes.size());
        this.globals = globals;
    }

    @Override
    public Memory getMemory() {
        return memory;
    }

    @Override
    public void setMemory(@NonNull byte[] memory) {
        if (this.memory == null) throw new IllegalArgumentException("this module instance contains non memory");
        this.memory.copyFrom(memory);
    }

    @Override
    public long[] execute(int functionIndex, long... parameters) {
        FunctionInstance ins = functions.get(functionIndex);
        long r = ins.execute(parameters);
        return ins.getArity() > 0 ? new long[]{r} : EMPTY_LONGS;
    }

    @Override
    public long[] execute(String funcName, long... parameters) throws RuntimeException {
        FunctionInstance ins = exports.get(funcName);
        long r = ins.execute(parameters);
        return ins.getArity() > 0 ? new long[]{r} : EMPTY_LONGS;
    }


    private long executeExpression(Instruction[] instructions, ValueType type) {
        return new Frame(instructions, new FunctionType(Collections.emptyList(), Collections.singletonList(type)), this,
                EMPTY_LONGS).execute();
    }

    @Override
    public boolean containsExport(String funcName) {
        return exports.containsKey(funcName);
    }

    @Override
    public ModuleInstance clone() {
        ModuleInstance ret = new ModuleInstanceImpl(
                null,
                globalTypes,
                new Memory(memory.getLimit()),
                table,
                functions,
                startFunction,
                hooks,
                exports,
                types,
                validateFunctionType,
                new BaseStackProvider()
        );
        ret.setGlobals(globals);
        ret.setMemory(memory.getData());
        return ret;
    }
}
