package org.tdf.lotusvm.types;

import org.tdf.lotusvm.common.BytesReader;

public class SectionReader {
    private final BytesReader reader;

    public SectionReader(BytesReader reader) {
        this.reader = reader;
    }

    <T extends AbstractSection> T readSection(Class<T> clazz) throws RuntimeException {
        int current = reader.getOffset();
        SectionID id = SectionID.values()[reader.read()];

        int size = reader.readVarUint32();
        BytesReader contents = reader.readAsReader(size);
        T section = null;
        try {
            section = clazz.getConstructor(SectionID.class, long.class, BytesReader.class, int.class, int.class)
                    .newInstance(id, size, contents, current, contents.getLimit());
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
        section.readPayload();
        section.clearPayload();
        return section;
    }
}
