package org.tdf.lotusvm.types

import org.tdf.lotusvm.common.BytesReader
import org.tdf.lotusvm.common.ObjectReader

/**
 * The import section has the id 2. It decodes into a vector of imports that represent the imports component of a module.
 */
class ImportSection(id: SectionID, size: Long, contents: BytesReader, offset: Int, limit: Int) :
    AbstractSection(id, size, contents, offset, limit) {
    var imports: List<Import> = emptyList()
        private set

    public override fun readPayload() {
        imports = reader.readObjectVec(Import.Companion)
    }

    class Import constructor(
        val module: String,
        val name: String,
        val type: ImportType,
        val typeIndex: Int,
        val tableType: TableType?,
        val limitType: LimitType?,
        val globalType: GlobalType?
    ) {

        companion object : ObjectReader<Import> {
            override fun readFrom(reader: BytesReader): Import {
                val module = reader.readCharVec()
                val name = reader.readCharVec()
                if (reader.peek() < 0 || reader.peek() >= ImportType.values().size) throw RuntimeException("import desc type incorrect")
                val type = ImportType.values()[reader.read()]
                var typeIndex = 0
                var tableType: TableType? = null
                var limitType: LimitType? = null
                var globalType: GlobalType? = null
                when (type) {
                    ImportType.TYPE_INDEX -> typeIndex = reader.readVarUint32()
                    ImportType.TABLE_TYPE -> tableType = TableType.readFrom(reader)
                    ImportType.MEMORY_TYPE -> limitType = LimitType.readFrom(reader)
                    ImportType.GLOBAL_TYPE -> globalType = GlobalType.readFrom(reader)
                }
                return Import(module, name, type, typeIndex, tableType, limitType, globalType)
            }
        }
    }
}