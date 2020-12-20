package org.tdf.lotusvm.types;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.tdf.lotusvm.common.BytesReader;

/**
 * Each section consists of
 * - a one-byte section id,
 * - the u32 size of the contents, in bytes,
 * - the actual contents, whose structure is depended on the section id.
 */
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public abstract class AbstractSection {
    @Getter
    private final SectionID id;
    @Getter
    private final long size; // unsigned integer
    @Getter(AccessLevel.PROTECTED)
    private BytesReader reader;


    abstract void readPayload();

    // clean payload after read
    public void clearPayload() {
        reader = null;
    }
}
