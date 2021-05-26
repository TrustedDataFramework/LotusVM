package org.tdf.lotusvm;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.tdf.lotusvm.common.BytesReader;
import org.tdf.lotusvm.types.Instruction;
import org.tdf.lotusvm.types.InstructionPool;

@RunWith(JUnit4.class)
public class InstructionPoolTest {

    @Test
    public void test() {

    }

    @Test
    public void test1() {
        byte[] o = Util.readClassPathFile("ins.data");
        Instruction[] instructions = Instruction.readExpressionFrom(new BytesReader(o));

        InstructionPool p = new InstructionPool();
        BytesReader rd = new BytesReader(o);
        long instructions2 = p.readExpressionFrom(rd);

        Instruction[] ins3 = new Instruction[InstructionPool.getInstructionsSize(instructions2)];

        for(int i = 0; i < ins3.length; i++) {
            ins3[i] = p.toInstruction(p.getInstructionInArray(instructions2, i));
        }

        for(int i = 0; i < ins3.length; i++) {
            if(!ins3[i].equals(instructions[i])) {
            }
        }
    }
}
