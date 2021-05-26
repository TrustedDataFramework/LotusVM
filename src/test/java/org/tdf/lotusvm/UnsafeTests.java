package org.tdf.lotusvm;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.tdf.lotusvm.runtime.UnsafeMemory;
import sun.misc.Unsafe;

@RunWith(JUnit4.class)
public class UnsafeTests {

    @Test
    public void test0() {
        Unsafe u = Unsafe.getUnsafe();
        long ptr = u.allocateMemory(8);
        u.putShort(ptr + 4, (short) 1);
        assert u.getShort(ptr | 4) == 1;
        u.putLong(ptr, 0);
        assert u.getShort(ptr | 4) == 0;
        assert  ((7 << 3) | 7 )== (7 * 8 + 7) ;
    }

}
