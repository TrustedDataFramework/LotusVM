package org.tdf.lotusvm.section;

import lombok.Getter;
import org.tdf.lotusvm.BytesReader;
import org.tdf.lotusvm.types.LimitType;

import java.util.List;

/**
 * The memory section has the id 5. It decodes into a vector of memories that represent the mems component of a module.
 */
public class MemorySection extends AbstractSection {

    @Getter
    private List<LimitType> memories;

    public MemorySection(SectionID id, long size, BytesReader payload) {
        super(id, size, payload);
    }

    @Override
    void readPayload() {
        memories = LimitType.readLimitTypesFrom(getReader());
    }
}
