package org.tdf.lotusvm.types

import org.tdf.lotusvm.common.BytesReader

/**
 * The type section has the id 1. It decodes into a vector of function types that represent the types component of a
 * module.
 */
class TypeSection(id: SectionID, size: Long, contents: BytesReader, offset: Int, limit: Int) :
    AbstractSection(id, size, contents, offset, limit) {
    var functionTypes: List<FunctionType> = emptyList()
        private set

    public override fun readPayload() {
        functionTypes = reader.readObjectVec(FunctionType)
    }
}