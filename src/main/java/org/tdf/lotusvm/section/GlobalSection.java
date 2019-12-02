package org.tdf.lotusvm.section;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.tdf.lotusvm.BytesReader;
import org.tdf.lotusvm.Instruction;
import org.tdf.lotusvm.types.GlobalType;

import java.util.ArrayList;
import java.util.List;

/**
 * The global section has the id 6. It decodes into a vector of globals that represent the globals component of a module.
 */
public class GlobalSection extends Section {

    @Getter
    private List<Global> globals;

    @AllArgsConstructor
    @Getter
    public static class Global {

        private GlobalType globalType;

        private List<Instruction> expression;

        public static Global readFrom(BytesReader reader) {
            GlobalType globalType = GlobalType.readFrom(reader);
            List<Instruction> expression = Instruction.readExpressionFrom(reader);
            return new Global(globalType, expression);
        }

        public static List<Global> readLocalsFrom(BytesReader reader) {
            int length = reader.readVarUint32();
            List<Global> memories = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                memories.add(readFrom(reader));
            }
            return memories;
        }
    }

    public GlobalSection(SectionID id, long size, byte[] payload) {
        super(id, size, payload);
    }

    @Override
    void readPayload() throws RuntimeException {
        BytesReader reader = new BytesReader(getPayload());
        globals = Global.readLocalsFrom(reader);
    }
}
