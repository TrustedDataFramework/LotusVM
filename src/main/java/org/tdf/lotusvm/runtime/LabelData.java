package org.tdf.lotusvm.runtime;

// label data = stack pc (2byte) | label pc (2byte) | 0x00  | 0x00  | arity (1byte) | loop (1byte)
final class LabelData {
    static final long MAX_UNSIGNED_SHORT = 0xffffL;

    static final long STACK_PC_MASK = 0xffff000000000000L;
    static final int STACK_PC_SHIFTS = 48;

    static final long LABEL_PC_MASK = 0x0000ffff00000000L;
    static final int LABEL_PC_SHIFTS = 32;

    static final long ARITY_MASK = 0xff00L;

    static final long LOOP_MASK = 0xffL;

    private LabelData() {
    }


    static long withAll(int stackPc, int labelPc, boolean arity, boolean loop) {
        long l = stackPc & MAX_UNSIGNED_SHORT;
        l = l << 16;
        l = l | (labelPc & MAX_UNSIGNED_SHORT);
        l = l << 32;
        l = l | (arity ? ARITY_MASK : 0);
        l = l | (loop ? LOOP_MASK : 0);
        return l;
    }

    static int getStackPc(long labelData) {
        return (int) ((labelData & STACK_PC_MASK) >>> STACK_PC_SHIFTS);
    }

    static int getLabelPc(long labelData) {
        return (int) ((labelData & LABEL_PC_MASK) >>> LABEL_PC_SHIFTS);
    }

    static boolean getArity(long labelData) {
        return (labelData & ARITY_MASK) != 0;
    }

    static boolean getLoop(long labelData) {
        return (labelData & LOOP_MASK) != 0;
    }

    public static void main(String[] args) {
        long l = withAll(2558, 123, true, false);
        System.out.printf("%s %s %s %s\n",
            getStackPc(l),
            getLabelPc(l),
            getArity(l),
            getLoop(l)
        );
    }
}
