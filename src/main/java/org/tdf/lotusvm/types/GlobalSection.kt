package org.tdf.lotusvm.types

import org.tdf.lotusvm.common.BytesReader
import org.tdf.lotusvm.common.ObjectReader

/**
 * The global section has the id 6. It decodes into a vector of globals that represent the globals component of a module.
 */
class GlobalSection(id: SectionID, size: Long, payload: BytesReader, offset: Int, limit: Int) :
    AbstractSection(id, size, payload, offset, limit) {
    var globals: List<Global> = emptyList()
        private set

    public override fun readPayload() {
        globals = reader.readObjectVec(Global.Companion)
    }
}

data class Global(val globalType: GlobalType, val expression: Long) {
    companion object: ObjectReader<Global> {
        override fun readFrom(reader: BytesReader): Global {
            val globalType = GlobalType.readFrom(reader)
            val expression = reader.insPool.readExpressionFrom(reader)
            return Global(globalType, expression)
        }
    }
}