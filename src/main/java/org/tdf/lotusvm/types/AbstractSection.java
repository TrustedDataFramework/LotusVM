package org.tdf.lotusvm.types;

import org.tdf.lotusvm.common.BytesReader;

/**
 * Each section consists of
 * - a one-byte section id,
 * - the u32 size of the contents, in bytes,
 * - the actual contents, whose structure is depended on the section id.
 */
public abstract class AbstractSection {
    private final SectionID id;
    private final long size; // unsigned integer
    private final int offset;
    private final int limit;
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

    public SectionID getId() {
        return this.id;
    }

    public long getSize() {
        return this.size;
    }

    public int getOffset() {
        return this.offset;
    }

    public int getLimit() {
        return this.limit;
    }

    protected BytesReader getReader() {
        return this.reader;
    }
}
