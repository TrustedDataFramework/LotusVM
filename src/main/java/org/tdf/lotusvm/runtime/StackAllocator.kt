package org.tdf.lotusvm.runtime

import org.tdf.lotusvm.types.ValueType

// stack provider to avoid array create
interface StackAllocator : AutoCloseable {
    val isEmpty: Boolean
    fun execute(): Long

    // create a frame, return the frame Id, the function referred by index must be wasm function
    fun pushFrame(functionIndex: Int, params: LongArray?)
    fun pushExpression(instructions: Long, type: ValueType?)
    val labelSize: Int

    // get from stack by index, unchecked
    fun getUnchecked(index: Int): Long
    fun popN(frameIndex: Int, n: Int): Int
    var pc: Int
    val instructions: Long
    fun pop(): Long
    fun push(value: Long)
    fun getLocal(idx: Int): Long
    fun setLocal(idx: Int, value: Long)

    // clear frames
    fun clear()
    var module: ModuleInstanceImpl

    override fun close() {}

    companion object {
        const val FUNCTION_INDEX_MASK = 0x0000000000007fffL
        const val TABLE_MASK = 0x0000000000008000L
    }
}