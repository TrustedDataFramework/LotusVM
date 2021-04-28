package org.tdf.lotusvm.types;

import lombok.Getter;
import org.tdf.lotusvm.common.BytesReader;

import java.util.List;

/**
 * The memory section has the id 5. It decodes into a vector of memories that represent the mems component of a module.
 */
public class MemorySection extends AbstractSection {

    @Getter
    private List<LimitType> memories;

    public MemorySection(SectionID id, long size, BytesReader payload, int offset, int limit) {
        super(id, size, payload, offset, limit);
    }

    @Override
    void readPayload() {
        memories = LimitType.readLimitTypesFrom(getReader());
    }
}
