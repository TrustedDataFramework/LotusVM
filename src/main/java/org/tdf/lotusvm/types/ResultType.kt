package org.tdf.lotusvm.types

import org.tdf.lotusvm.common.BytesReader
import org.tdf.lotusvm.common.Constants

enum class ResultType(val code: Int) {
    EMPTY(Constants.RESULT_EMPTY), I32(Constants.VALUE_I32), I64(Constants.VALUE_I64), F32(Constants.VALUE_F32), F64(
        Constants.VALUE_F64
    );

    companion object {
        @JvmField
        val VALUES = values()

        @JvmStatic
        fun readFrom(reader: BytesReader): ResultType {
            val type = reader.read()
            return when (type) {
                Constants.VALUE_I32 -> I32
                Constants.VALUE_I64 -> I64
                Constants.VALUE_F32 -> F32
                Constants.VALUE_F64 -> F64
                Constants.RESULT_EMPTY -> EMPTY
                else -> throw IllegalArgumentException(String.format("unknown value type %x", type))
            }
        }
    }
}