package org.tdf.lotusvm.types;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.tdf.lotusvm.common.BytesReader;
import org.tdf.lotusvm.common.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * The element section has the id 9. It decodes into a vector of element segments that represent the elem component of a module.
 */
public class ElementSection extends AbstractSection {
    @Getter
    private List<Element> elements;


    public ElementSection(SectionID id, long size, BytesReader payload, int offset, int limit) {
        super(id, size, payload, offset, limit);
    }

    @Override
    void readPayload() {
        elements = Element.readElementsFrom(getReader());
    }

    @Getter
    @AllArgsConstructor
    public static class Element {
        // In the current version of WebAssembly, at most one table is allowed in a module. Consequently, the only
        // valid tableidx is 0.
        private final int tableIndex;
        private final long expression;
        private final int[] functionIndex;

        public static Element readFrom(BytesReader reader) {
            return new Element(reader.readVarUint32(),
                reader.getInsPool().readExpressionFrom(reader),
                Vector.readUint32VectorFrom(reader));
        }

        public static List<Element> readElementsFrom(BytesReader reader) {
            int length = reader.readVarUint32();
            List<Element> res = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                res.add(Element.readFrom(reader));
            }
            return res;
        }
    }
}
