package org.tdf.lotusvm.types

import org.tdf.lotusvm.common.BytesReader
import org.tdf.lotusvm.common.ObjectReader

/**
 * The export section has the id 7. It decodes into a vector of exports that represent the exports component of a module.
 */
class ExportSection(id: SectionID, size: Long, payload: BytesReader, offset: Int, limit: Int) :
    AbstractSection(id, size, payload, offset, limit) {
    var exports: List<Export> = emptyList()
        private set

    public override fun readPayload() {
        exports = reader.readObjectVec(Export.Companion)
    }
}

enum class ExportType(val code: Int) {
    FUNCTION_INDEX(0x00), TABLE_INDEX(0x01), MEMORY_INDEX(0x02), GLOBAL_INDEX(0x03);

    companion object : ObjectReader<ExportType> {
        override fun readFrom(reader: BytesReader): ExportType {
            val b = reader.read()
            if (b < 0 || b >= values().size) throw RuntimeException(String.format("unknown export type %x", b))
            return values()[b]
        }
    }
}

class Export(val name: String, val type: ExportType, val index: Int) {
    companion object : ObjectReader<Export> {
        override fun readFrom(reader: BytesReader): Export {
            return Export(reader.readCharVec(), ExportType.readFrom(reader), reader.readVarUint32())
        }
    }
}