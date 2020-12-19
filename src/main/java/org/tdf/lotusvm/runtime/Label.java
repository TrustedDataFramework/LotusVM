package org.tdf.lotusvm.runtime;

import lombok.AccessLevel;
import lombok.Getter;
import org.tdf.lotusvm.types.Instruction;

import java.util.List;

@Getter(AccessLevel.PACKAGE)
class Label {
    int pc;
   Instruction[] body;
    private boolean arity;
    private boolean loop;

    Label(boolean arity, Instruction[] body,    boolean loop) {
        this.arity = arity;
        this.loop = loop;
        this.body = body;
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
