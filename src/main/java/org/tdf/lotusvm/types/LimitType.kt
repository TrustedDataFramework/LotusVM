package org.tdf.lotusvm.types

import org.tdf.lotusvm.common.BytesReader
import org.tdf.lotusvm.common.ObjectReader
import org.tdf.lotusvm.runtime.Memory

/**
 * Limits are encoded with a preceding flag indicating whether a maximum is present.
 *
 *
 * also used for memory types
 */
data class LimitType(val bounded: Boolean = true, val minimum: Int = 0, val maximum: Int = Memory.MAX_PAGES) {
    companion object : ObjectReader<LimitType> {
        override fun readFrom(reader: BytesReader): LimitType {
            return if (reader.read() == 0) {
                LimitType(minimum = reader.readVarUint32())
            } else LimitType(minimum = reader.readVarUint32(), maximum = reader.readVarUint32())
        }
    }
}