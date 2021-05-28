package org.tdf.lotusvm.types

import org.tdf.lotusvm.common.BytesReader

/**
 * The function section has the id 3.
 * It decodes into a vector of type indices that represent the type ﬁelds of the functions in the funcs component of a module.
 * The locals and body ﬁelds of the respective functions are encoded separately in the code section.
 */
class FunctionSection(id: SectionID, size: Long, payload: BytesReader, offset: Int, limit: Int) :
    AbstractSection(id, size, payload, offset, limit) {
    var typeIndices: IntArray = IntArray(0)
        private set

    public override fun readPayload() {
        typeIndices = reader.readUint32Vec()
    }
}