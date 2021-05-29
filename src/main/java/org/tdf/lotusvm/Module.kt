package org.tdf.lotusvm

import org.tdf.lotusvm.types.*

interface Module : AutoCloseable {
    companion object {
        @JvmStatic
        fun create(bin: ByteArray): Module {
            return ModuleImpl(bin)
        }
    }

    val customSections: MutableList<CustomSection>

    // the magic number of wasm
    val magic: Int

    // version of wasm binary
    val version: Int
    val typeSection: TypeSection?
    val importSection: ImportSection?
    val functionSection: FunctionSection?

    // In the current version of WebAssembly, at most one table may be defined or imported in a single module,
    // and all constructs implicitly reference this table 0. This restriction may be lifted in future versions.
    val tableSection: TableSection?

    // In the current version of WebAssembly, at most one memory may be defined or imported in a single
    // module, and all constructs implicitly reference this memory 0. This restriction may be lifted in future versions.
    val memorySection: MemorySection?
    val globalSection: GlobalSection?
    val exportSection: ExportSection?
    val startSection: StartSection?
    val elementSection: ElementSection?
    val codeSection: CodeSection?
    val dataSection: DataSection?
    val insPool: InstructionPool?

    override fun close()
}
