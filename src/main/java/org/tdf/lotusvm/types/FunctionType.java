package org.tdf.lotusvm.types;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.tdf.lotusvm.BytesReader;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Function types are encoded by the byte 0x60 followed by the respective vectors of parameter and result types
 */
@AllArgsConstructor
@Getter
public class FunctionType {
    private static final int PREFIX = 0x60;
    private List<ValueType> parameterTypes;
    private List<ValueType> resultTypes;

    public static FunctionType readFrom(BytesReader reader){
        if(reader.read() != PREFIX){
            throw new RuntimeException("functype incorrect");
        }
        return new FunctionType(ValueType.readValueTypesFrom(reader), ValueType.readValueTypesFrom(reader));
    }

    public static List<FunctionType> readFunctionTypesFrom(BytesReader reader){
        int length = reader.readVarUint32();
        List<FunctionType> res = new ArrayList<>(length);
        for(int i = 0; i < length; i++){
            res.add(readFrom(reader));
        }
        return res;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FunctionType that = (FunctionType) o;
        if(parameterTypes.size() != that.parameterTypes.size()) return false;
        for(int i = 0; i < parameterTypes.size(); i++){
            if(!parameterTypes.get(i).equals(that.parameterTypes.get(i))) return false;
        }
        if(resultTypes.size() != that.resultTypes.size()) return false;
        for(int i = 0; i < resultTypes.size(); i++){
            if(!resultTypes.get(i).equals(that.resultTypes.get(i))) return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(parameterTypes, resultTypes);
    }
}
