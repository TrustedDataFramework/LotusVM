package org.tdf.lotusvm.section;

import lombok.Getter;
import org.tdf.lotusvm.BytesReader;
import org.tdf.lotusvm.Vector;

/**
 * Custom sections have the id 0. They are intended to be used for debugging information or third-party extensions,
 * and are ignored by the WebAssembly semantics. Their contents consist of a name further identifying the custom
 * section, followed by an uninterpreted sequence of bytes for custom use
 */
public class CustomSection extends Section{
    @Getter
    private String name;
    @Getter
    private byte[] data;

    public CustomSection(SectionID id, long size, byte[] contents) {
        super(id, size, contents);
    }

    @Override
    void readPayload() {
        BytesReader reader = new BytesReader(getPayload());
        name = Vector.readStringFrom(reader);
        data = reader.readAll();
    }
}
