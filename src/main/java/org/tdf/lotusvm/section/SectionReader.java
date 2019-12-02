package org.tdf.lotusvm.section;

import org.tdf.lotusvm.BytesReader;

public class SectionReader {
    private BytesReader reader;

    public SectionReader(BytesReader reader) {
        this.reader = reader;
    }

    <T extends Section> T readSection(Class<T> clazz) throws RuntimeException {
        SectionID id = SectionID.values()[reader.read()];

        int size = reader.readVarUint32();
        byte[] contents = reader.read(size);
        T section = null;
        try {
            section = clazz.getConstructor(SectionID.class, long.class, byte[].class).newInstance(id, size, contents);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
        section.readPayload();
        section.clearPayload();
        return section;
    }
}
