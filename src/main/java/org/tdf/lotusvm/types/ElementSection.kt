package org.tdf.lotusvm.types

import org.tdf.lotusvm.common.BytesReader
import org.tdf.lotusvm.common.ObjectReader

/**
 * The element section has the id 9. It decodes into a vector of element segments that represent the elem component of a module.
 */
class ElementSection(id: SectionID, size: Long, payload: BytesReader, offset: Int, limit: Int) :
    AbstractSection(id, size, payload, offset, limit) {
    var elements: List<Element> = emptyList()
        private set

    public override fun readPayload() {
        elements = reader.readObjectVec(Element.Companion)
    }


}

class Element(// In the current version of WebAssembly, at most one table is allowed in a module. Consequently, the only
    // valid tableidx is 0.
    val tableIndex: Int, val expression: Long, val functionIndex: IntArray
) {
    companion object : ObjectReader<Element> {
        override fun readFrom(reader: BytesReader): Element {
            return Element(
                reader.readVarUint32(),
                reader.insPool.readExpressionFrom(reader),
                reader.readUint32Vec()
            )
        }
    }
}