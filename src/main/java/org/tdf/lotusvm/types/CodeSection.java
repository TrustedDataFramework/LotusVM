package org.tdf.lotusvm.types;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.tdf.lotusvm.common.BytesReader;

import java.util.ArrayList;
import java.util.List;

/**
 * The code section has the id 10. It decodes into a vector of code entries that are pairs of value type vectors and
 * expressions. They represent the locals and body field of the functions in the funcs component of a module. The
 * type fields of the respective functions are encoded separately in the function section.
 * The encoding of each code entry consists of
 * the u32 size of the function code in bytes,
 * the actual function code, which in turn consists of
 * the declaration of locals,
 * the function body as an expression.
 * Local declarations are compressed into a vector whose entries consist of
 * a u32 count,
 * a value type,
 */
public class CodeSection extends AbstractSection {
    @AllArgsConstructor
    @Getter
    public static class Local {
        private int count;
        private ValueType type;

        public static Local readFrom(BytesReader reader) {
            return new Local(reader.readVarUint32(), ValueType.readFrom(reader));
        }

        public static List<Local> readLocalsFrom(BytesReader reader) {
            int length = reader.readVarUint32();
            List<Local> locals = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                locals.add(readFrom(reader));
            }
            return locals;
        }
    }

    @AllArgsConstructor
    @Getter
    public static class Function {
        private List<Local> locals;
        private List<Instruction> expression;

        public static Function readFrom(BytesReader reader) {
            return new Function(Local.readLocalsFrom(reader), Instruction.readExpressionFrom(reader));
        }
    }

    @AllArgsConstructor
    @Getter
    public static class Code {
        private int size;
        private Function code;

        public static Code readFrom(BytesReader reader) {
            int size = reader.readVarUint32();
            return new Code(size, Function.readFrom(
                    new BytesReader(reader.read(size))
            ));
        }

        public static List<Code> readCodesFrom(BytesReader reader) {
            int length = reader.readVarUint32();
            List<Code> codes = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                codes.add(readFrom(reader));
            }
            return codes;
        }
    }

    @Getter
    private List<Code> codes;

    public CodeSection(SectionID id, long size, BytesReader payload) {
        super(id, size, payload);
    }

    @Override
    void readPayload() {
        codes = Code.readCodesFrom(getReader());
    }
}
