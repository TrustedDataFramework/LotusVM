package org.tdf.lotusvm.types

import org.tdf.lotusvm.common.BytesReader
import org.tdf.lotusvm.common.ObjectReader
import java.util.*

/**
 * Function types are encoded by the byte 0x60 followed by the respective vectors of parameter and result types
 */
class FunctionType(val parameterTypes: List<ValueType>, val resultTypes: List<ValueType>) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as FunctionType
        if (parameterTypes.size != that.parameterTypes.size) return false
        for (i in parameterTypes.indices) {
            if (parameterTypes[i] != that.parameterTypes[i]) return false
        }
        if (resultTypes.size != that.resultTypes.size) return false
        for (i in resultTypes.indices) {
            if (resultTypes[i] != that.resultTypes[i]) return false
        }
        return true
    }

    override fun hashCode(): Int {
        return Objects.hash(parameterTypes, resultTypes)
    }

    companion object: ObjectReader<FunctionType> {
        private const val PREFIX = 0x60
        override fun readFrom(reader: BytesReader): FunctionType {
            if (reader.read() != PREFIX) {
                throw RuntimeException("functype incorrect")
            }
            val t = FunctionType(
                reader.readObjectVec(ValueType),
                reader.readObjectVec(ValueType)
            )
            if (t.resultTypes.size > 1) throw RuntimeException("unsupported multi returns for function ")
            return t
        }
    }
}