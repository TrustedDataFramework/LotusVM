package org.tdf.lotusvm.section;

import lombok.Getter;
import org.tdf.lotusvm.BytesReader;
import org.tdf.lotusvm.types.TableType;

import java.util.List;

/**
 * The table section has the id 4. It decodes into a vector of tables that represent the tables component of a module.
 */
public class TableSection extends AbstractSection {

    @Getter
    private List<TableType> tableTypes;

    public TableSection(SectionID id, long size, BytesReader payload) {
        super(id, size, payload);
    }

    @Override
    void readPayload() throws RuntimeException {
        tableTypes = TableType.readTableTypesFrom(getReader());
    }

}
