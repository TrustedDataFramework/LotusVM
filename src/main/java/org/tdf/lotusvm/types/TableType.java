package org.tdf.lotusvm.types;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.tdf.lotusvm.BytesReader;

import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
public class TableType {
    @Getter
    private static final int ELEMENT_TYPE = 0x70;
    @Getter
    private LimitType limit;

    public static TableType readFrom(BytesReader reader){
        int type = reader.read();
        if(type != ELEMENT_TYPE){
            throw new RuntimeException(String.format("invalid element type %s", type));
        }
        LimitType limit = LimitType.readFrom(reader);
        return new TableType(limit);
    }

    public static List<TableType> readTableTypesFrom(BytesReader reader) {
        int length = reader.readVarUint32();
        List<TableType> limitTables = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            limitTables.add(readFrom(reader));
        }
        return limitTables;
    }

}
