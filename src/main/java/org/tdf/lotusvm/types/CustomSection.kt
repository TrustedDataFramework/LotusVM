package org.tdf.lotusvm.types

import org.tdf.lotusvm.common.BytesReader
import org.tdf.lotusvm.common.Constants

/**
 * Custom sections have the id 0. They are intended to be used for debugging information or third-party extensions,
 * and are ignored by the WebAssembly semantics. Their contents consist of a name further identifying the custom
 * section, followed by an uninterpreted sequence of bytes for custom use
 */
class CustomSection(id: SectionID, size: Long, contents: BytesReader, offset: Int, limit: Int) :
    AbstractSection(id, size, contents, offset, limit) {
    var name: String = ""
        private set
    var data: ByteArray = Constants.EMPTY_BYTE_ARRAY
        private set

    public override fun readPayload() {
        name = reader.readCharVec()
        data = reader.readAll()
    }
}