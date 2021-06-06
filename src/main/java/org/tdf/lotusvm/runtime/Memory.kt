package org.tdf.lotusvm.runtime

import org.tdf.lotusvm.types.LimitType

interface Memory : AutoCloseable {
    companion object {
        const val PAGE_SIZE = 64 * (1 shl 10) // 64 KB
        const val MAX_PAGES = 0xFFFF
    }

    fun setLimit(limit: LimitType)

    fun read(memOff: Int, dst: ByteArray, dstPos: Int = 0, length: Int = dst.size)

    fun write(memOff: Int, src: ByteArray, srcPos: Int = 0, length: Int = src.size)

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

abstract class AbstractMemory: Memory{
    override fun read(memOff: Int, dst: ByteArray, dstPos: Int, length: Int) {
        for(i in 0 until length)
            dst[dstPos + i] = load8(memOff + i)
    }

    override fun write(memOff: Int, src: ByteArray, srcPos: Int, length: Int) {
        for(i in 0 until length)
            storeI8(memOff + i, src[srcPos + i])
    }
}