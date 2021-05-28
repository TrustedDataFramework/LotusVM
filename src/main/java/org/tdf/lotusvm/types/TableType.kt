package org.tdf.lotusvm.types

import org.tdf.lotusvm.common.BytesReader
import org.tdf.lotusvm.common.ObjectReader

class TableType(val limit: LimitType) {
    companion object: ObjectReader<TableType> {
        const val ELEMENT_TYPE = 0x70

        override fun readFrom(reader: BytesReader): TableType {
            val type = reader.read()
            if (type != ELEMENT_TYPE) {
                throw RuntimeException(String.format("invalid element type %s", type))
            }
            val limit = LimitType.readFrom(reader)
            return TableType(limit)
        }
    }
}