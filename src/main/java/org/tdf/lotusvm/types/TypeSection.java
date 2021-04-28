package org.tdf.lotusvm.types;

import lombok.Getter;
import org.tdf.lotusvm.common.BytesReader;

import java.util.List;

/**
 * The type section has the id 1. It decodes into a vector of function types that represent the types component of a
 * module.
 */
public class TypeSection extends AbstractSection {
    @Getter
    private List<FunctionType> functionTypes;

    public TypeSection(SectionID id, long size, BytesReader contents, int offset, int limit) {
        super(id, size, contents, offset, limit);
    }

    @Override
    void readPayload() {
        functionTypes = FunctionType.readFunctionTypesFrom(getReader());
    }
}
