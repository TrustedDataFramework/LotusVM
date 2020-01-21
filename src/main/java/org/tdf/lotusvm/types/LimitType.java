package org.tdf.lotusvm.types;

import lombok.Getter;
import org.tdf.lotusvm.common.BytesReader;

import java.util.ArrayList;
import java.util.List;

/**
 * Limits are encoded with a preceding flag indicating whether a maximum is present.
 * <p>
 * also used for memory types
 */
@Getter
public class LimitType {
    private boolean bounded; // maxIsExist
    private int minimum;
    private int maximum;

    public LimitType() {
        bounded = true;
    }

    private LimitType(int minimum) {
        this.minimum = minimum;
    }

    private LimitType(int minimum, int maximum) {
        this.minimum = minimum;
        this.maximum = maximum;
        this.bounded = true;
    }

    static LimitType readFrom(BytesReader reader) throws RuntimeException {
        if (reader.read() == 0) {
            return new LimitType(reader.readVarUint32());
        }
        return new LimitType(reader.readVarUint32(), reader.readVarUint32());
    }

    static List<LimitType> readLimitTypesFrom(BytesReader reader) {
        int length = reader.readVarUint32();
        List<LimitType> limitTypes = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            limitTypes.add(LimitType.readFrom(reader));
        }
        return limitTypes;
    }
}
