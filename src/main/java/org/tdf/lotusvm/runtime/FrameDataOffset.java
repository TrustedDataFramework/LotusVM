package org.tdf.lotusvm.runtime;

// frame data offset = label base + stack base
final class FrameDataOffset {
    static final long MAX_SIGNED_INT = 0x7fffffffL;
    static final long STACK_BASE_MASK = 0x7fffffffL;
    static final int STACK_BASE_SHIFTS = 0;
    static final long LABEL_BASE_MASK = 0x7fffffff00000000L;
    static final int LABEL_BASE_SHIFTS = 32;

    private FrameDataOffset() {
    }

    static int getStackBase(long offset) {
        return (int) ((offset & STACK_BASE_MASK) >>> STACK_BASE_SHIFTS);
    }


    static int getLabelBase(long offset) {
        return (int) ((offset & LABEL_BASE_MASK) >>> LABEL_BASE_SHIFTS);
    }

    static long withAll(int labelBase, int stackBase) {
        long r = labelBase & MAX_SIGNED_INT;
        r = r << 32;
        return r | (stackBase & MAX_SIGNED_INT);
    }
}
