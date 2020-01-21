package org.tdf.lotusvm.types;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.tdf.lotusvm.common.BytesReader;

import java.util.ArrayList;
import java.util.List;

/**
 * The global section has the id 6. It decodes into a vector of globals that represent the globals component of a module.
 */
public class GlobalSection extends AbstractSection {

    @Getter
    private List<Global> globals;

    public GlobalSection(SectionID id, long size, BytesReader payload) {
        super(id, size, payload);
    }

    @Override
    void readPayload() throws RuntimeException {
        globals = Global.readLocalsFrom(getReader());
    }

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
}
