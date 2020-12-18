package org.tdf.lotusvm.runtime;

import lombok.AccessLevel;
import lombok.Getter;
import org.tdf.lotusvm.types.Instruction;

import java.util.List;

@Getter(AccessLevel.PACKAGE)
class Label {
    private int pc;
    private Instruction[] body;
    private int arity;
    private boolean loop;

    Label(boolean hasArity, Instruction[] body) {
        if (hasArity) {
            this.arity = 1;
        }
        this.body = body;
    }

    Label withLoop() {
        this.loop = true;
        return this;
    }

    int incrementAndGet() {
        return ++pc;
    }

    void jumpToContinuation() {
        if (isLoop()) {
            // continuation is the start of the loop label
            this.pc = 0;
            return;
        }
        //  continuation is the end of the block label
        this.pc = body.length;
    }
}
