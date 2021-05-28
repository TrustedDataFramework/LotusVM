package org.tdf.lotusvm.types

import org.tdf.lotusvm.common.BytesReader
import org.tdf.lotusvm.common.Constants
import java.io.Closeable

/**
 * The binary encoding of modules is organized into sections. Most sections correspond to one component of a
 * module record, except that function definitions are split into two sections, separating their type declarations in the
 * function section from their bodies in the code section.
 */
class Module(binary: ByteArray) : Closeable {
    private val customSections: MutableList<CustomSection> = ArrayList()

    // the magic number of wasm
    var magic = 0
        private set

    // version of wasm binary
    var version = 0
        private set
    var typeSection: TypeSection? = null
        private set
    var importSection: ImportSection? = null
        private set
    var functionSection: FunctionSection? = null
        private set

    // In the current version of WebAssembly, at most one table may be defined or imported in a single module,
    // and all constructs implicitly reference this table 0. This restriction may be lifted in future versions.
    var tableSection: TableSection? = null
        private set

    // In the current version of WebAssembly, at most one memory may be defined or imported in a single
    // module, and all constructs implicitly reference this memory 0. This restriction may be lifted in future versions.
    var memorySection: MemorySection? = null
        private set
    var globalSection: GlobalSection? = null
        private set
    var exportSection: ExportSection? = null
        private set
    var startSection: StartSection? = null
        private set
    var elementSection: ElementSection? = null
        private set
    var codeSection: CodeSection? = null
        private set
    var dataSection: DataSection? = null
        private set
    var insPool: InstructionPool? = null
        private set

    private fun parse(binary: ByteArray) {
        val reader = BytesReader(binary)
        magic = reader.readUint32()
        if (magic != Constants.MAGIC_NUMBER) throw RuntimeException("wasm: Invalid magic number")
        version = reader.readUint32()
        if (version != Constants.VERSION) throw RuntimeException(
            String.format(
                "wasm: unknown binary version: %d",
                version
            )
        )
        readSections(reader)
        insPool = reader.insPool
    }

    private fun readSections(reader: BytesReader) {
        val sectionReader = SectionReader(reader)
        while (reader.remaining() > 0) {
            if (reader.peek() < 0 || reader.peek() >= SectionID.values().size) throw RuntimeException(
                String.format(
                    "unknown section type %x",
                    reader.peek()
                )
            )
            val id = SectionID.values()[reader.peek()]
            when (id) {
                SectionID.CUSTOM -> customSections.add(sectionReader.readSection(CustomSection::class.java))
                SectionID.TYPE -> typeSection = sectionReader.readSection(TypeSection::class.java)
                SectionID.IMPORT -> importSection = sectionReader.readSection(ImportSection::class.java)
                SectionID.FUNCTION -> functionSection = sectionReader.readSection(FunctionSection::class.java)
                SectionID.TABLE -> tableSection = sectionReader.readSection(TableSection::class.java)
                SectionID.MEMORY -> memorySection = sectionReader.readSection(MemorySection::class.java)
                SectionID.GLOBAL -> globalSection = sectionReader.readSection(GlobalSection::class.java)
                SectionID.EXPORT -> exportSection = sectionReader.readSection(ExportSection::class.java)
                SectionID.START -> startSection = sectionReader.readSection(StartSection::class.java)
                SectionID.ELEMENT -> elementSection = sectionReader.readSection(ElementSection::class.java)
                SectionID.CODE -> codeSection = sectionReader.readSection(CodeSection::class.java)
                SectionID.DATA -> dataSection = sectionReader.readSection(DataSection::class.java)
            }
        }
    }

    override fun close() {
        insPool!!.close()
    }

    init {
        parse(binary)
    }
}