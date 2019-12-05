package org.tdf.lotusvm.runtime;

import lombok.Getter;
import org.tdf.lotusvm.types.LimitType;

import java.util.Collection;

class Table {
    @Getter
    private FunctionInstance[] functions;
    private LimitType limit;

    Table(LimitType limit) {
        this.functions = new FunctionInstance[limit.getMinimum()];
    }

    void putElements(int offset, Collection<? extends FunctionInstance> functions) {
        int i = 0;
        for (FunctionInstance f : functions) {
            int index = offset + i;
            spaceCheck(index);
            this.functions[index] = f;
            i++;
        }
    }

    private void spaceCheck(int index) throws RuntimeException {
        if (index < functions.length) return;
        if (limit.isBounded() && (index + 1) > limit.getMaximum()) {
            throw new RuntimeException("table index overflow, max is " + limit.getMaximum());
        }
        FunctionInstance[] tmp = functions;
        functions = new FunctionInstance[tmp.length * 2];
        System.arraycopy(tmp, 0, functions, 0, tmp.length);
    }
}
