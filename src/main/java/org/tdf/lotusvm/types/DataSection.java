package org.tdf.lotusvm.types;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.tdf.lotusvm.common.BytesReader;
import org.tdf.lotusvm.common.Vector;

import java.util.ArrayList;
import java.util.List;

public class DataSection extends AbstractSection {
    @Getter
    private List<DataSegment> dataSegments;

    public DataSection(SectionID id, long size, BytesReader payload, int offset, int limit) {
        super(id, size, payload, offset, limit);
    }

    @Override
    void readPayload() {
        dataSegments = DataSegment.readDataSegmentsFrom(getReader());
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class DataSegment {
        private final int memoryIndex;

        private final Instruction[] expression;

        private final byte[] init;

        public static DataSegment readFrom(BytesReader reader) {
            return new DataSegment(reader.readVarUint32(),
                Instruction.readExpressionFrom(reader),
                Vector.readBytesFrom(reader)
            );
        }

        public static List<DataSegment> readDataSegmentsFrom(BytesReader reader) {
            int length = reader.readVarUint32();
            List<DataSegment> res = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                res.add(readFrom(reader));
            }
            return res;
        }
    }
}
