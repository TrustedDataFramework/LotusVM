package org.tdf.lotusvm.types;

import lombok.Getter;
import org.tdf.lotusvm.common.BytesReader;

import java.util.List;

/**
 * The table section has the id 4. It decodes into a vector of tables that represent the tables component of a module.
 */
public class TableSection extends AbstractSection {

    @Getter
    private List<TableType> tableTypes;

    public TableSection(SectionID id, long size, BytesReader payload, int offset, int limit) {
        super(id, size, payload, offset, limit);
    }

    @Override
    void readPayload() throws RuntimeException {
        tableTypes = TableType.readTableTypesFrom(getReader());
    }

}
