package org.tdf.lotusvm.types;


import org.tdf.lotusvm.common.BytesReader;

import static org.tdf.lotusvm.common.Constants.*;

public enum ResultType {
    EMPTY(RESULT_EMPTY),
    I32(VALUE_I32),
    I64(VALUE_I64),
    F32(VALUE_F32),
    F64(VALUE_F64);
    public final int code;

    ResultType(int code) {
        this.code = code;
    }

    static ResultType readFrom(BytesReader reader) {
        int type = reader.read();
        switch (type) {
            case VALUE_I32:
                return I32;
            case VALUE_I64:
                return I64;
            case VALUE_F32:
                return F32;
            case VALUE_F64:
                return F64;
            case RESULT_EMPTY:
                return EMPTY;
            default:
                throw new IllegalArgumentException(String.format("unknown value type %x", type));
        }
    }
}
