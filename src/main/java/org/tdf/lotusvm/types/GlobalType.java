package org.tdf.lotusvm.types;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.tdf.lotusvm.common.BytesReader;

/**
 * Global types are encoded by their value type and a flag for their mutability.
 */
@AllArgsConstructor
@Builder
@Getter
public class GlobalType {
    private ValueType valueType;
    private boolean mutable; // true var ,false const

    static GlobalType readFrom(BytesReader reader){
        ValueType valueType = ValueType.readFrom(reader);
        boolean mutable = reader.read() != 0;
        return GlobalType.builder().valueType(valueType).mutable(mutable).build();
    }
}
