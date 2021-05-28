package org.tdf.lotusvm.types;

import lombok.Getter;
import org.tdf.lotusvm.common.BytesReader;

/**
 * The function section has the id 3.
 * It decodes into a vector of type indices that represent the type ﬁelds of the functions in the funcs component of a module.
 * The locals and body ﬁelds of the respective functions are encoded separately in the code section.
 */
public class FunctionSection extends AbstractSection {
    @Getter
    private int[] typeIndices;

    public FunctionSection(SectionID id, long size, BytesReader payload, int offset, int limit) {
        super(id, size, payload, offset, limit);
    }

    @Override
    void readPayload() {
        typeIndices = getReader().readUint32Vec();
    }
}
