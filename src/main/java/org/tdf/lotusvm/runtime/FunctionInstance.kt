package org.tdf.lotusvm.runtime

import org.tdf.lotusvm.types.FunctionType

interface FunctionInstance {
    val parametersLength: Int
    val arity: Int
    val type: FunctionType
    fun execute(parameters: LongArray): Long
    val isHost: Boolean
}