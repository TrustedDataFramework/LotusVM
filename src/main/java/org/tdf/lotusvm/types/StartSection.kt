package org.tdf.lotusvm.types

import org.tdf.lotusvm.common.BytesReader

/**
 * The start section has the id 8. It decodes into an optional start function that represents the start component of a module.
 */
class StartSection(id: SectionID, size: Long, payload: BytesReader, offset: Int, limit: Int) : AbstractSection(
    id, size, payload, offset, limit
) {
    var functionIndex = 0
        private set

    override fun readPayload() {
        functionIndex = reader.readVarUint32()
    }
}