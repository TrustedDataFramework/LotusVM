package org.tdf.lotusvm.types

import org.tdf.lotusvm.common.BytesReader

/**
 * The table section has the id 4. It decodes into a vector of tables that represent the tables component of a module.
 */
class TableSection(id: SectionID, size: Long, payload: BytesReader, offset: Int, limit: Int) :
    AbstractSection(id, size, payload, offset, limit) {
    var tableTypes: List<TableType> = emptyList()
        private set

    public override fun readPayload() {
        tableTypes = reader.readObjectVec(TableType)
    }
}