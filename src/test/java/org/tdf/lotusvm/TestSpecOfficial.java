package org.tdf.lotusvm;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Collections;

@RunWith(JUnit4.class)
public class TestSpecOfficial {
    @Test
    public void test0() throws Exception{
        TestModule module = Util.getTestModule("spec-official");
        module.testAll();
//        module.testSpecFunctions("address.3.wasm", Collections.singletonList("32_good5"), 0, 1);
    }
}
