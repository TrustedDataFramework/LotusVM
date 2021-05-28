package org.tdf.lotusvm.types

import org.tdf.lotusvm.common.BytesReader

/**
 * The memory section has the id 5. It decodes into a vector of memories that represent the mems component of a module.
 */
class MemorySection(id: SectionID, size: Long, payload: BytesReader, offset: Int, limit: Int) :
    AbstractSection(id, size, payload, offset, limit) {
    var memories: List<LimitType> = emptyList()
        private set

    public override fun readPayload() {
        memories = reader.readObjectVec(LimitType.Companion)
    }
}