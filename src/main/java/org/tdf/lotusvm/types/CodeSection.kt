package org.tdf.lotusvm.types

import org.tdf.lotusvm.common.BytesReader
import org.tdf.lotusvm.common.ObjectReader

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
class CodeSection(id: SectionID, size: Long, payload: BytesReader, offset: Int, limit: Int) :
    AbstractSection(id, size, payload, offset, limit) {
    var codes: List<Code> = emptyList()
        private set

    public override fun readPayload() {
        codes = reader.readObjectVec(Code.Companion)
    }
}

class Local(val count: Int, val type: ValueType) {
    companion object : ObjectReader<Local> {
        override fun readFrom(reader: BytesReader): Local {
            return Local(reader.readVarUint32(), ValueType.readFrom(reader))
        }
    }
}

class Function(val locals: List<Local>, val expression: Long) {
    companion object : ObjectReader<Function> {
        override fun readFrom(reader: BytesReader): Function {
            return Function(
                reader.readObjectVec(Local.Companion),
                reader.insPool.readExpressionFrom(reader)
            )
        }
    }
}

class Code(val size: Int, val code: Function) {
    companion object : ObjectReader<Code> {
        override fun readFrom(reader: BytesReader): Code {
            val size = reader.readVarUint32()
            return Code(
                size, Function.readFrom(
                    BytesReader(reader.read(size)).withPool(reader.insPool)
                )
            )
        }
    }
}