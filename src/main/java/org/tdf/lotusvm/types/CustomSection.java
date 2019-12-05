package org.tdf.lotusvm.types;

import lombok.Getter;
import org.tdf.lotusvm.common.BytesReader;
import org.tdf.lotusvm.common.Vector;

/**
 * Custom sections have the id 0. They are intended to be used for debugging information or third-party extensions,
 * and are ignored by the WebAssembly semantics. Their contents consist of a name further identifying the custom
 * section, followed by an uninterpreted sequence of bytes for custom use
 */
public class CustomSection extends AbstractSection {
    @Getter
    private String name;
    @Getter
    private byte[] data;

    public CustomSection(SectionID id, long size, BytesReader contents) {
        super(id, size, contents);
    }

    @Override
    void readPayload() {
        name = Vector.readStringFrom(getReader());
        data = getReader().readAll();
    }
}
