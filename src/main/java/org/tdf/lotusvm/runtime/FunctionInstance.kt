package org.tdf.lotusvm.runtime

import org.tdf.lotusvm.types.FunctionType

interface FunctionInstance {
    val paramSize: Int
    val arity: Int
    val type: FunctionType
    fun execute(parameters: LongArray): Long
    val isHost: Boolean
}