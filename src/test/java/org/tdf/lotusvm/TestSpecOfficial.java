package org.tdf.lotusvm;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;


@RunWith(JUnit4.class)
public class TestSpecOfficial {
    @Test
    public void test0() throws Exception {
        TestModule module = Util.getTestModule("spec-official");
        module.testAll();
    }
}
