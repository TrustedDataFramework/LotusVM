package org.tdf.lotusvm.types;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.tdf.lotusvm.common.BytesReader;
import org.tdf.lotusvm.common.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * The export section has the id 7. It decodes into a vector of exports that represent the exports component of a module.
 */
public class ExportSection extends AbstractSection {
    @Getter
    private List<Export> exports;

    public ExportSection(SectionID id, long size, BytesReader payload) {
        super(id, size, payload);
    }

    @Override
    void readPayload() {
        exports = Export.readExportsFrom(getReader());
    }

    public enum ExportType {
        FUNCTION_INDEX(0x00),
        TABLE_INDEX(0x01),
        MEMORY_INDEX(0x02),
        GLOBAL_INDEX(0x03);
        public final int code;

        ExportType(int code) {
            this.code = code;
        }

        public static ExportType readFrom(BytesReader reader) {
            int b = reader.read();
            if (b < 0 || b >= ExportType.values().length)
                throw new RuntimeException(String.format("unknown export type %x", b));
            return ExportType.values()[b];
        }
    }

    @Getter
    @AllArgsConstructor
    public static class Export {
        private String name;
        private ExportType type;
        private int index;

        public static Export readFrom(BytesReader reader) {
            return new Export(Vector.readStringFrom(reader), ExportType.readFrom(reader), reader.readVarUint32());
        }

        public static List<Export> readExportsFrom(BytesReader reader) {
            int length = reader.readVarUint32();
            List<Export> exports = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                exports.add(readFrom(reader));
            }
            return exports;
        }
    }
}
