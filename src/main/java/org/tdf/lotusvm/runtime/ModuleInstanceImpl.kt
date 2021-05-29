package org.tdf.lotusvm.runtime

import org.tdf.lotusvm.Builder
import org.tdf.lotusvm.ModuleInstance
import org.tdf.lotusvm.common.Constants
import org.tdf.lotusvm.common.OpCode
import org.tdf.lotusvm.types.*
import java.util.*
import java.util.function.Consumer
import java.util.stream.Collectors

/**
 * A module instance is the runtime representation of a module.
 * It is created by instantiating a module, and collects runtime representations of all entities that are imported,
 * deﬁned, or exported by the module.
 */
class ModuleInstanceImpl(builder: Builder) : ModuleInstance {

    override var globalTypes: List<GlobalType> = emptyList()

    // memories
    // In the current version of WebAssembly, all memory instructions implicitly operate on memory index 0.
    // This restriction may be lifted in future versions.
    override val memory: Memory = builder.memory!!

    // In the current version of WebAssembly, at most one table may be defined or imported in a single module,
    // and all constructs implicitly reference this table 0. This restriction may be lifted in future versions.
    var table: Table? = null

    // all functions
    var functions: MutableList<FunctionInstance> = ArrayList()

    // main function
    // The start function is intended for initializing the state of a module. The module and its exports are not
    // accessible before this initialization has completed.
    var startFunction: FunctionInstance? = null

    // hooks

    private var hookArray: Array<Hook> = builder.hooks.toTypedArray()

    // exported functions
    private val exports: MutableMap<String, Int> = mutableMapOf()

    val types: List<FunctionType>

    val validateFunctionType: Boolean = builder.validateFunctionType
    var stackAllocator: StackAllocator = builder.stackAllocator!!

    var insPool: InstructionPool


    override var hooks: Set<Hook>
        get() = setOf(*hookArray)
        set(value) {
            this.hookArray = value.toTypedArray()
        }

    override var globals: LongArray = Constants.EMPTY_LONGS
        set(value) {
            require(value.size == globalTypes.size) { "length of globals should be " + globalTypes.size }
            field = value
        }

    fun getGlobal(idx: Int): Long {
        return globals[idx]
    }

    fun setGlobal(idx: Int, value: Long) {
        if (!globalTypes[idx].isMutable)
            throw RuntimeException("modify a immutable global")
        globals[idx] = value
    }

    override fun execute(functionIndex: Int, vararg parameters: Long): LongArray {
        stackAllocator.pushFrame(functionIndex, Objects.requireNonNull(parameters))
        val ins = functions[functionIndex]
        val r = stackAllocator.execute()
        return if (ins.arity > 0) longArrayOf(r) else Constants.EMPTY_LONGS
    }

    override fun execute(funcName: String, vararg parameters: Long): LongArray {
        val idx = exports[funcName]!!
        val ins = functions[exports[funcName]!!]
        stackAllocator.pushFrame(idx, Objects.requireNonNull(parameters))
        val r = stackAllocator.execute()
        return if (ins.arity > 0) longArrayOf(r) else Constants.EMPTY_LONGS
    }

    private fun executeExpression(instructions: Long, type: ValueType): Long {
        stackAllocator.pushExpression(instructions, type)
        return stackAllocator.execute()
    }

    fun getFuncInTable(idx: Int): FunctionInstance {
        if (idx < 0 || idx >= tableSize)
            throw RuntimeException("access function in table overflow")
        return table?.functions?.get(idx)!!
    }

    private val tableSize: Int
        get() = table?.functions?.size ?: 0

    override fun containsExport(funcName: String): Boolean {
        return exports.containsKey(funcName)
    }

    init {
        stackAllocator.module = this

        val module = builder.module!!
        insPool = module.insPool!!

        types = module.typeSection?.functionTypes ?: emptyList()

        val functionsMap: MutableMap<String, HostFunction> = mutableMapOf()
        for (f in builder.hostFunctions) {
            if (functionsMap.containsKey(f.name)) throw RuntimeException("create module instance failed: duplicated host function " + f.name)
            functionsMap[f.name] = f
            for (alias in f.alias) {
                if (functionsMap.containsKey(alias)) throw RuntimeException("create module instance failed: duplicated host function $alias")
                functionsMap[alias] = f
            }
        }


        // imports
        if (module.importSection != null) {
            for (imp in module.importSection!!.imports) {
                if (imp.type != ImportType.TYPE_INDEX) {
                    continue
                }
                val func = functionsMap[imp.name] ?: throw RuntimeException("unsupported host function " + imp.name)
                //                if (!module.getTypeSection().getFunctionTypes().get(imp.getTypeIndex()).equals(func.getType())) {
//                    throw new RuntimeException("invalid function type: " + func.getName());
//                }
                func.instance = this
                functions.add(func)
            }
        }

        // init global variables
        if (module.globalSection != null) {
            globalTypes = module.globalSection!!
                .globals.map { it.globalType }
        }

        if (module.globalSection != null && builder.globals == null) {
            globals = LongArray(module.globalSection!!.globals.size)
            for (i in globals.indices) {
                globals[i] = executeExpression(
                    module.globalSection!!.globals[i].expression,
                    module.globalSection!!.globals[i].globalType.valueType
                )
            }
        }

        if (builder.globals != null) {
            globals = builder.globals!!
        }

        // init tables
        if (module.tableSection != null) {
            table = Table(module.tableSection!!.tableTypes[0].limit)
        }

        // init function instances
        if (module.functionSection != null) {
            for (i in 0 until module.functionSection!!.typeIndices.size) {
                val typeIndex = module.functionSection!!.typeIndices[i]
                val code = module.codeSection!!.codes[i]
                val type = module.typeSection!!.functionTypes[typeIndex]
                functions.add(
                    WASMFunction(type, code.code.expression, code.code.locals)
                )
            }
        }

        // init elements
        if (module.elementSection != null) {
            module.elementSection!!.elements.forEach(Consumer { x: Element ->
                val offset = executeExpression(x.expression, ValueType.I32).toInt()
                if (offset < 0) throw RuntimeException("invalid offset, overflow Integer.MAX_VALUE")
                table!!.putElements(
                    offset,
                    Arrays.stream(x.functionIndex).mapToObj { i: Int -> functions[i] }.collect(Collectors.toList())
                )
            })
        }
        if (module.memorySection != null) {
            if (module.memorySection!!.memories.size > 1) throw RuntimeException("too much memory section")
            memory.setLimit(
                module.memorySection!!.memories[0]
            )
        }

        // put data into memory
        if (module.dataSection != null) {
            module.dataSection!!.dataSegments.forEach(Consumer { (_, expression, init) ->
                val offset = executeExpression(expression, ValueType.I32)
                memory.put(offset.toInt(), init)
            })
        }

        // load and execute start function
        if (module.startSection != null) {
            startFunction = functions[module.startSection!!.functionIndex]
            stackAllocator.pushFrame(module.startSection!!.functionIndex, Constants.EMPTY_LONGS)
            stackAllocator.execute()
        }

        // exports
        if (module.exportSection != null) {
            module.exportSection!!.exports.stream()
                .filter { x: Export -> x.type === ExportType.FUNCTION_INDEX }
                .forEach { x: Export -> exports[x.name] = x.index }
        }

        val tableSize = table?.functions?.size ?: 0

        if (tableSize > StackAllocator.FUNCTION_INDEX_MASK || functions.size > StackAllocator.FUNCTION_INDEX_MASK) throw RuntimeException(
            "function index overflow"
        )
    }

    fun touchIns(ins: OpCode) {
        for (i in 0 until hookArray.size) {
            hookArray[i].onInstruction(ins, this)
        }
    }

    fun touchNewFrame() {
        for (i in 0 until hookArray.size) {
            hookArray[i].onNewFrame()
        }
    }

    fun touchFrameExit() {
        for (i in 0 until hookArray.size) {
            hookArray[i].onFrameExit()
        }
    }

    fun touchHostFunc(host: HostFunction) {
        for (i in 0 until hookArray.size) {
            hookArray[i].onHostFunction(host, this)
        }
    }

    fun touchMemGrow(beforeGrow: Int, afterGrow: Int) {
        for (i in 0 until hookArray.size) {
            hookArray[i].onMemoryGrow(beforeGrow, afterGrow)
        }
    }

    fun getFunc(idx: Int): FunctionInstance {
        return functions[idx]
    }
}