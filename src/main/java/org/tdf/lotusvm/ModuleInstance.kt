package org.tdf.lotusvm

import org.tdf.lotusvm.runtime.*
import org.tdf.lotusvm.types.GlobalType

/**
 * Not support:
 * 1. sign-extension operators
 * 2. return multi-value
 * 3. per frame stack size < 65536
 * 4. per frame locals size < 65536
 * 5. per frame labels size < 65536
 * 6. float number, i.e. f32_nearest, f64_round (platform undefined behavior)
 */
interface ModuleInstance {
    var globals: LongArray
    val globalTypes: List<GlobalType>
    val memory: Memory
    var hooks: Set<Hook>

    fun containsExport(funcName: String): Boolean
    fun execute(functionIndex: Int, vararg parameters: Long): LongArray

    fun execute(funcName: String, vararg parameters: Long): LongArray


    companion object {
        @JvmStatic
        fun builder(): Builder {
            return Builder.builder()
        }
    }
}

class Builder private constructor() {
    var validateFunctionType = false
        private set
    var hostFunctions: Set<HostFunction> = emptySet()
        private set
    var hooks: Set<Hook> = emptySet()
        private set
    var globals: LongArray? = null
        private set
    var memory: Memory? = null
        private set
    var module: Module? = null
    var stackAllocator: StackAllocator? = null

    fun module(module: Module): Builder {
        this.module = module
        return this
    }

    fun stackAllocator(allocator: StackAllocator): Builder {
        stackAllocator = allocator
        return this
    }

    fun hostFunctions(hostFunctions: Set<HostFunction>): Builder {
        this.hostFunctions = hostFunctions
        return this
    }

    fun hooks(hooks: Set<Hook>): Builder {
        this.hooks = hooks
        return this
    }

    fun globals(globals: LongArray): Builder {
        this.globals = globals
        return this
    }

    fun memory(memory: Memory): Builder {
        this.memory = memory
        return this
    }

    fun validateFunctionType(): Builder {
        validateFunctionType = true
        return this
    }

    fun build(): ModuleInstance {
        return ModuleInstanceImpl(this)
    }

    companion object {
        @JvmStatic
        fun builder(): Builder {
            return Builder()
        }
    }
}