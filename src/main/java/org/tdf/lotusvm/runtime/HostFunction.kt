package org.tdf.lotusvm.runtime

import org.tdf.lotusvm.types.FunctionType
import java.util.*

abstract class HostFunction : FunctionInstance {
    override val type: FunctionType
    val name: String

    @JvmField
    var instance: ModuleInstanceImpl? = null

    val alias: MutableSet<String> = mutableSetOf()


    constructor(name: String, type: FunctionType, vararg alias: String) {
        this.name = name
        this.type = type
        this.alias.addAll(listOf(*alias))
    }

    override val parametersLength: Int
        get() = type.parameterTypes.size
    override val arity: Int
        get() = type.resultTypes.size

    abstract override fun execute(parameters: LongArray): Long
    override val isHost: Boolean
        get() = true

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as HostFunction
        return name == that.name
    }

    override fun hashCode(): Int {
        return Objects.hash(name)
    }

    protected fun putMemory(offset: Int, data: ByteArray?) {
        instance!!.memory.put(offset, data!!)
    }

    protected fun loadMemory(offset: Int, length: Int): ByteArray {
        return instance!!.memory.load(offset, length)
    }
}