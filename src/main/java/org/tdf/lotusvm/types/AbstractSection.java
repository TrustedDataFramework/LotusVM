package org.tdf.lotusvm.types;

import lombok.AccessLevel;
import lombok.Getter;
import org.tdf.lotusvm.common.BytesReader;

/**
 * Each section consists of
 * - a one-byte section id,
 * - the u32 size of the contents, in bytes,
 * - the actual contents, whose structure is depended on the section id.
 */
public abstract class AbstractSection {
    @Getter
    private final SectionID id;
    @Getter
    private final long size; // unsigned integer
    @Getter
    private final int offset;
    @Getter
    private final int limit;
    @Getter(AccessLevel.PROTECTED)
    private BytesReader reader;

    AbstractSection(SectionID id, long size, BytesReader reader, int offset, int limit) {
        this.id = id;
        this.size = size;
        this.reader = reader;
        this.offset = offset;
        this.limit = limit;
    }

    abstract void readPayload();

    // clean payload after read
    public void clearPayload() {
        reader = null;
    }
}
