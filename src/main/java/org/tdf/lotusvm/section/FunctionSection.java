package org.tdf.lotusvm.section;

import lombok.Getter;
import org.tdf.lotusvm.BytesReader;
import org.tdf.lotusvm.Vector;

/**
 * The function section has the id 3.
 * It decodes into a vector of type indices that represent the type ﬁelds of the functions in the funcs component of a module.
 * The locals and body ﬁelds of the respective functions are encoded separately in the code section.
 */
public class FunctionSection extends AbstractSection {
    public FunctionSection(SectionID id, long size, BytesReader payload) {
        super(id, size, payload);
    }

    @Getter
    private int[] typeIndices;

    @Override
    void readPayload() {
        typeIndices = Vector.readUint32VectorFrom(getReader());
    }
}
