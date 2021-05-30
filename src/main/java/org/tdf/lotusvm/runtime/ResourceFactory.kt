package org.tdf.lotusvm.runtime

import org.tdf.lotusvm.types.ArrayLongBuffer
import org.tdf.lotusvm.types.LongBuffer
import org.tdf.lotusvm.types.UnsafeLongBuffer


object ResourceFactory {
    var useUnsafe: Boolean = false

    @JvmStatic
    fun createBuffer(cap: Int): LongBuffer {
        return if (useUnsafe) { UnsafeLongBuffer(cap) } else { ArrayLongBuffer(cap) }
    }

    @JvmStatic
    fun createMemory(): Memory {
        return if (useUnsafe) { UnsafeMemory() } else { BaseMemory() }
    }

    @JvmStatic
    fun createStack(maxStackSize: Int, maxFrames: Int, maxLabelSize: Int): StackAllocator {
        return StackAllocatorImpl(maxStackSize, maxFrames, maxLabelSize)
    }
}