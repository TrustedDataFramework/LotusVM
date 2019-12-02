package org.tdf.lotusvm.runtime;

import lombok.Getter;
import org.tdf.lotusvm.Instruction;

import java.util.List;

@Getter
public class Label {
    private int pc;
    private List<Instruction> body;
    private int arity;
    private boolean loop;

    public Label(boolean hasArity, List<Instruction> body) {
        if(hasArity){
            this.arity = 1;
        }
        this.body = body;
    }

    public Label withLoop(){
        this.loop = true;
        return this;
    }

    int incrementAndGet() {
        return ++pc;
    }

    void jumpToContinuation(){
        if(isLoop()){
            // continuation is the start of the loop label
            this.pc = 0;
            return;
        }
        //  continuation is the end of the block label
        this.pc = body.size();
    }
}
