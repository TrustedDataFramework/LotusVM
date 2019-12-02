package org.tdf.lotusvm.section;

import lombok.Getter;
import org.tdf.lotusvm.BytesReader;
import org.tdf.lotusvm.types.LimitType;

import java.util.List;

/**
 * The memory section has the id 5. It decodes into a vector of memories that represent the mems component of a module.
 */
public class MemorySection extends Section {

    @Getter
    private List<LimitType> memories;

    public MemorySection(SectionID id, long size, byte[] payload) {
        super(id, size, payload);
    }

    @Override
    void readPayload() {
        BytesReader reader = new BytesReader(getPayload());
        memories = LimitType.readLimitTypesFrom(reader);
    }
}
