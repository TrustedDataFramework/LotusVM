package org.tdf.lotusvm.runtime

import org.tdf.lotusvm.ModuleInstance
import org.tdf.lotusvm.types.FunctionType
import java.util.*

abstract class HostFunction(
    val name: String,
    override val type: FunctionType,
    vararg alias: String
) : FunctionInstance {

    lateinit var instance: ModuleInstance

    val alias: MutableSet<String> = mutableSetOf()

    init {
        this.alias.addAll(listOf(*alias))
    }

    protected val memory: Memory
        get() = instance.memory

    override val paramSize: Int
        get() = type.parameterTypes.size
    override val arity: Int
        get() = type.resultTypes.size

    abstract override fun execute(args: LongArray): Long
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
}