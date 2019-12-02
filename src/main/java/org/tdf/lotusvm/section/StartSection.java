package org.tdf.lotusvm.section;

import lombok.Getter;
import org.tdf.lotusvm.BytesReader;

/**
 * The start section has the id 8. It decodes into an optional start function that represents the start component of a module.
 */
public class StartSection extends AbstractSection {
    public StartSection(SectionID id, long size, BytesReader payload) {
        super(id, size, payload);
    }

    @Getter
    private int functionIndex;

    @Override
    void readPayload() {
        functionIndex = getReader().readVarUint32();
    }
}
