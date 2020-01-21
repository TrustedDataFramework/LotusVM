package org.tdf.lotusvm.types;

import lombok.Getter;
import org.tdf.lotusvm.common.BytesReader;

/**
 * The start section has the id 8. It decodes into an optional start function that represents the start component of a module.
 */
public class StartSection extends AbstractSection {
    @Getter
    private int functionIndex;

    public StartSection(SectionID id, long size, BytesReader payload) {
        super(id, size, payload);
    }

    @Override
    void readPayload() {
        functionIndex = getReader().readVarUint32();
    }
}
