package org.tdf.lotusvm.common

interface ObjectReader<T> {
    fun readFrom(reader: BytesReader): T
}