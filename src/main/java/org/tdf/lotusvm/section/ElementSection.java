package org.tdf.lotusvm.section;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.tdf.lotusvm.BytesReader;
import org.tdf.lotusvm.Instruction;
import org.tdf.lotusvm.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * The element section has the id 9. It decodes into a vector of element segments that represent the elem component of a module.
 */
public class ElementSection extends AbstractSection {
    public ElementSection(SectionID id, long size, BytesReader payload) {
        super(id, size, payload);
    }


    @Getter
    @AllArgsConstructor
    public static class Element{
        // In the current version of WebAssembly, at most one table is allowed in a module. Consequently, the only
        // valid tableidx is 0.
        private int tableIndex;
        private List<Instruction> expression;
        private int[] functionIndex;

        public static Element readFrom(BytesReader reader){
            return new Element(reader.readVarUint32(),
                    Instruction.readExpressionFrom(reader),
                    Vector.readUint32VectorFrom(reader));
        }

        public static List<Element> readElementsFrom(BytesReader reader){
            int length = reader.readVarUint32();
            List<Element> res = new ArrayList<>(length);
            for(int i = 0; i < length; i++) {
                res.add(Element.readFrom(reader));
            }
            return res;
        }
    }

    @Getter
    private List<Element> elements;

    @Override
    void readPayload() {
        elements = Element.readElementsFrom(getReader());
    }
}
