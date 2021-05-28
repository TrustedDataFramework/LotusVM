package org.tdf.lotusvm.runtime

import org.tdf.lotusvm.types.FunctionType
import org.tdf.lotusvm.types.Local

internal class WASMFunction(
    override val type: FunctionType, // params + localvars
    val body: Long,
    private val locals: List<Local>
) : FunctionInstance {

    fun getLocals(): Int {
        var ret = 0
        for (i in locals.indices) {
            ret += locals[i].count
        }
        return ret
    }

    override fun execute(parameters: LongArray): Long {
        throw UnsupportedOperationException()
    }

    override val isHost: Boolean
        get() = false

    override val parametersLength: Int
    get() {
        return type.parameterTypes.size
    }

    override val arity: Int
        get() = type.resultTypes.size
}