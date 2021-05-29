package org.tdf.lotusvm.runtime

import org.tdf.lotusvm.types.LimitType

interface Memory : AutoCloseable {
    companion object {
        const val PAGE_SIZE = 64 * (1 shl 10) // 64 KB
        const val MAX_PAGES = 0xFFFF
    }

    fun setLimit(limit: LimitType)
    fun put(offset: Int, data: ByteArray)
    fun load(offset: Int, length: Int): ByteArray
    fun load32(offset: Int): Int
    fun load64(offset: Int): Long
    fun load8(offset: Int): Byte
    fun load16(offset: Int): Short
    fun storeI32(offset: Int, value: Int)
    fun storeI64(offset: Int, n: Long)
    fun storeI16(offset: Int, num: Short)
    fun storeI8(offset: Int, n: Byte)
    fun grow(n: Int): Int
    val pages: Int

    override fun close() {}
}