package org.tdf.lotusvm.runtime

import org.tdf.lotusvm.types.LimitType
import org.tdf.lotusvm.types.UnsafeUtil
import java.nio.ByteOrder

internal class UnsafeMemory : AbstractMemory() {
    private var limit = LimitType()
    private var pointer: Long = 0
    private var rawSize = 0
    override var pages = 0
        private set

    fun setRawSize(rawSize: Int) {
        pointer = UnsafeUtil.UNSAFE.allocateMemory(rawSize.toLong())
        this.rawSize = rawSize
        UnsafeUtil.UNSAFE.setMemory(pointer, rawSize.toLong(), 0.toByte())
    }

    override fun setLimit(limit: LimitType) {
        pages = limit.minimum
        val rawSize = pages * Memory.PAGE_SIZE
        if (rawSize < 0) throw RuntimeException("memory overflow")
        setRawSize(rawSize)
        this.limit = limit
    }

    override fun load32(offset: Int): Int {
        if (offset + 4 > rawSize) throw RuntimeException("memory access overflow")
        return UnsafeUtil.UNSAFE.getInt(pointer + offset)
    }

    override fun load64(offset: Int): Long {
        if (offset + 8 > rawSize) throw RuntimeException("memory access overflow")
        return UnsafeUtil.UNSAFE.getLong(pointer + offset)
    }

    override fun load8(offset: Int): Byte {
        if (offset >= rawSize) throw RuntimeException("memory access overflow")
        return UnsafeUtil.UNSAFE.getByte(pointer + offset)
    }

    override fun load16(offset: Int): Short {
        if (offset + 2 > rawSize) throw RuntimeException("memory access overflow")
        return UnsafeUtil.UNSAFE.getShort(pointer + offset)
    }

    override fun storeI32(offset: Int, `val`: Int) {
        if (offset + 4 > rawSize) throw RuntimeException("memory access overflow")
        UnsafeUtil.UNSAFE.putInt(pointer + offset, `val`)
    }

    override fun storeI64(offset: Int, n: Long) {
        if (offset + 8 > rawSize) throw RuntimeException("memory access overflow")
        UnsafeUtil.UNSAFE.putLong(pointer + offset, n)
    }

    override fun storeI16(offset: Int, num: Short) {
        if (offset + 2 > rawSize) throw RuntimeException("memory access overflow")
        UnsafeUtil.UNSAFE.putShort(pointer + offset, num)
    }

    override fun storeI8(offset: Int, n: Byte) {
        if (offset >= rawSize) throw RuntimeException("memory access overflow")
        UnsafeUtil.UNSAFE.putByte(pointer + offset, n)
    }

    override fun grow(n: Int): Int {
        if (limit.bounded && pages + n > limit.maximum) {
            return -1
        }
        if (n + pages > Memory.MAX_PAGES) return -1
        val prevRawSize = rawSize
        val newRawSize = (pages + n) * Memory.PAGE_SIZE
        if (newRawSize < 0) throw RuntimeException("memory overflow")
        val newPointer = UnsafeUtil.UNSAFE.reallocateMemory(pointer, newRawSize.toLong())
        UnsafeUtil.UNSAFE.setMemory(newPointer + prevRawSize, (newRawSize - prevRawSize).toLong(), 0.toByte())
        val prev = pages
        pages += n
        pointer = newPointer
        rawSize = newRawSize
        return prev
    }

    override fun close() {
        if (pointer == 0L) return
        UnsafeUtil.UNSAFE.freeMemory(pointer)
        pointer = 0
    }

    init {
        if (ByteOrder.nativeOrder() != ByteOrder.LITTLE_ENDIAN) throw RuntimeException("create unsafe memory failed: native byte order is not little endian!")
    }
}