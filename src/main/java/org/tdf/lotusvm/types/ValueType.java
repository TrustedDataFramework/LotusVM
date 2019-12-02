package org.tdf.lotusvm.types;

import org.tdf.lotusvm.BytesReader;

import java.util.ArrayList;
import java.util.List;

import static org.tdf.lotusvm.Constants.*;


/**
 * valtype ::= 0x7F ⇒ i32
 * 0x7E ⇒ i64
 * 0x7D ⇒ f32
 * 0x7C ⇒ f64
 */
public enum ValueType {
    I32(VALUE_I32),
    I64(VALUE_I64),
    F32(VALUE_F32),
    F64(VALUE_F64);
    public final int code;

    ValueType(int code){
        this.code = code;
    }

    public static ValueType readFrom(BytesReader reader){
        int type = reader.read();
        switch (type){
            case VALUE_I32:
                return I32;
            case VALUE_I64:
                return I64;
            case VALUE_F32:
                return F32;
            case VALUE_F64:
                return F64;
            default: throw new IllegalArgumentException(String.format("unknown value type %x", type));
        }
    }

    public static List<ValueType> readValueTypesFrom(BytesReader reader){
        int length = reader.readVarUint32();
        List<ValueType> res = new ArrayList<>(length);
        for(int i = 0; i < length; i++){
            res.add(readFrom(reader));
        }
        return res;
    }
}
