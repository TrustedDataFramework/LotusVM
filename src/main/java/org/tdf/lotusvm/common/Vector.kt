package org.tdf.lotusvm.common

object Vector {

    @JvmStatic
    fun readUint32VectorAsLongFrom(reader: BytesReader): LongArray {
        val length = reader.readVarUint32()
        if (length == 0) return Constants.EMPTY_LONGS
        val res = LongArray(length)
        for (i in res.indices) {
            res[i] = reader.readVarUint32AsLong()
        }
        return res
    }
}