package org.tdf.lotusvm.types

import org.tdf.lotusvm.common.BytesReader

/**
 * Each section consists of
 * - a one-byte section id,
 * - the u32 size of the contents, in bytes,
 * - the actual contents, whose structure is depended on the section id.
 */
abstract class AbstractSection internal constructor(
    val id: SectionID, // unsigned integer
    val size: Long,
    protected var reader: BytesReader,
    val offset: Int,
    val limit: Int
) {
    protected abstract fun readPayload()
}