package org.tdf.lotusvm.section;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.tdf.lotusvm.BytesReader;
import org.tdf.lotusvm.Vector;
import org.tdf.lotusvm.types.GlobalType;
import org.tdf.lotusvm.types.LimitType;
import org.tdf.lotusvm.types.TableType;

import java.util.ArrayList;
import java.util.List;

/**
 * The import section has the id 2. It decodes into a vector of imports that represent the imports component of a module.
 */
public class ImportSection extends Section {
    public ImportSection(SectionID id, long size, byte[] contents) {
        super(id, size, contents);
    }


    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Import {
        private String module;
        private String name;
        private ImportType type;
        private int typeIndex;

        private TableType tableType;

        private LimitType limitType;

        private GlobalType globalType;

        public static Import readFrom(BytesReader reader) {
            String module = Vector.readStringFrom(reader);
            String name = Vector.readStringFrom(reader);
            if (reader.peek() < 0 || reader.peek() >= ImportType.values().length)
                throw new RuntimeException("import desc type incorrect");
            ImportType type = ImportType.values()[reader.read()];
            int typeIndex = 0;
            TableType tableType = null;
            LimitType limitType = null;
            GlobalType globalType = null;
            switch (type) {
                case TYPE_INDEX:
                    typeIndex = reader.readVarUint32();
                    break;
                case TABLE_TYPE:
                    tableType = TableType.readFrom(reader);
                    break;
                case MEMORY_TYPE:
                    limitType = LimitType.readFrom(reader);
                    break;
                case GLOBAL_TYPE:
                    globalType = GlobalType.readFrom(reader);
                    break;
                default:
                    throw new RuntimeException("import desc type incorrect");
            }
            return builder().globalType(globalType).limitType(limitType).module(module)
                    .name(name).tableType(tableType).type(type).typeIndex(typeIndex).build();
        }

        public static List<Import> readImportsFrom(BytesReader reader) {
            int length = reader.readVarUint32();
            List<Import> imports = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                imports.add(readFrom(reader));
            }
            return imports;
        }

    }


    @Getter
    private List<Import> imports;


    @Override
    void readPayload() throws RuntimeException {
        BytesReader reader = new BytesReader(getPayload());
        imports = Import.readImportsFrom(reader);
    }
}
