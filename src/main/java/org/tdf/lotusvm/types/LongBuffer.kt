package org.tdf.lotusvm.types

import java.io.Closeable

interface LongBuffer : Closeable {
    operator fun get(index: Int): Long
    operator fun set(index: Int, `val`: Long)
    fun size(): Int
    fun push(value: Long)
    fun setSize(size: Int)
    override fun close() {}
}