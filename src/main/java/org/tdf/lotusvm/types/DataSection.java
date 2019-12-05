package org.tdf.lotusvm.types;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.tdf.lotusvm.common.BytesReader;
import org.tdf.lotusvm.common.Vector;

import java.util.ArrayList;
import java.util.List;

public class DataSection extends AbstractSection {
    public DataSection(SectionID id, long size, BytesReader payload) {
        super(id, size, payload);
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class DataSegment{
        private int memoryIndex;

        private List<Instruction> expression;

        private byte[] init;

        public static DataSegment readFrom(BytesReader reader){
            return new DataSegment(reader.readVarUint32(),
                    Instruction.readExpressionFrom(reader),
                    Vector.readBytesFrom(reader)
            );
        }

        public static List<DataSegment> readDataSegmentsFrom(BytesReader reader){
            int length = reader.readVarUint32();
            List<DataSegment> res = new ArrayList<>(length);
            for(int i = 0; i < length; i++){
                res.add(readFrom(reader));
            }
            return res;
        }
    }

    @Getter
    private List<DataSegment> dataSegments;

    @Override
    void readPayload() {
        dataSegments = DataSegment.readDataSegmentsFrom(getReader());
    }
}
