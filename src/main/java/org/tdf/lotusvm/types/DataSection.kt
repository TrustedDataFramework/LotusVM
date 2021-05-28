package org.tdf.lotusvm.types

import org.tdf.lotusvm.common.BytesReader
import org.tdf.lotusvm.common.ObjectReader

class DataSection(id: SectionID, size: Long, payload: BytesReader, offset: Int, limit: Int) :
    AbstractSection(id, size, payload, offset, limit) {
    var dataSegments: List<DataSegment> = emptyList()
        private set

    public override fun readPayload() {
        dataSegments = reader.readObjectVec(DataSegment.Companion)
    }

    data class DataSegment(val memoryIndex: Int, val expression: Long, val init: ByteArray) {
        companion object : ObjectReader<DataSegment> {
            override fun readFrom(reader: BytesReader): DataSegment {
                return DataSegment(
                    reader.readVarUint32(),
                    reader.insPool.readExpressionFrom(reader),
                    reader.readByteVec()
                )
            }
        }
    }
}