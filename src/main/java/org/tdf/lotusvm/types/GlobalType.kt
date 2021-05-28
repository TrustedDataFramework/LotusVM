package org.tdf.lotusvm.types

import org.tdf.lotusvm.common.BytesReader
import org.tdf.lotusvm.common.ObjectReader

/**
 * Global types are encoded by their value type and a flag for their mutability.
 */
data class GlobalType(
    val valueType: ValueType, // true var ,false const
    val isMutable: Boolean
) {

    companion object : ObjectReader<GlobalType> {
        override fun readFrom(reader: BytesReader): GlobalType {
            val valueType = ValueType.readFrom(reader)
            val mutable = reader.read() != 0
            return GlobalType(valueType, mutable)
        }
    }
}