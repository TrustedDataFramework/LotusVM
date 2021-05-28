package org.tdf.lotusvm.types

import org.tdf.lotusvm.common.BytesReader
import org.tdf.lotusvm.common.Constants
import org.tdf.lotusvm.common.ObjectReader

/**
 * valtype ::= 0x7F -> i32
 * 0x7E -> i64
 * 0x7D -> f32
 * 0x7C ->  f64
 */
enum class ValueType(val code: Int) {
    I32(Constants.VALUE_I32), I64(Constants.VALUE_I64), F32(Constants.VALUE_F32), F64(Constants.VALUE_F64);

    companion object : ObjectReader<ValueType> {
        override fun readFrom(reader: BytesReader): ValueType {
            return when (val type = reader.read()) {
                Constants.VALUE_I32 -> I32
                Constants.VALUE_I64 -> I64
                Constants.VALUE_F32 -> F32
                Constants.VALUE_F64 -> F64
                else -> throw IllegalArgumentException(String.format("unknown value type %x", type))
            }
        }
    }
}