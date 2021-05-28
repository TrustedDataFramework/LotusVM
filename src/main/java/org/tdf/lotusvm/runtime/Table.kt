package org.tdf.lotusvm.runtime

import org.tdf.lotusvm.types.LimitType

class Table internal constructor(val limit: LimitType) {
    var functions: Array<FunctionInstance?>
        private set

    fun putElements(offset: Int, functions: Collection<FunctionInstance?>) {
        for ((i, f) in functions.withIndex()) {
            val index = offset + i
            spaceCheck(index)
            this.functions[index] = f
        }
    }

    private fun spaceCheck(index: Int) {
        if (index < functions.size) return
        if (limit.bounded && index + 1 > limit.maximum) {
            throw RuntimeException("table index overflow, max is " + limit.maximum)
        }
        val tmp = functions
        functions = arrayOfNulls(tmp.size * 2)
        System.arraycopy(tmp, 0, functions, 0, tmp.size)
    }

    init {
        functions = arrayOfNulls(Math.max(limit.minimum, 8))
    }
}