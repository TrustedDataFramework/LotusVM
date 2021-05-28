package org.tdf.lotusvm.runtime;

// memory allocation for frame id
// label size (2byte) | local size (2byte) | stack size (2byte) | frame index (2byte)
final class FrameId {
    private FrameId() {}

    static final long LABEL_SIZE_MASK = 0xffff000000000000L;
    static final int LABEL_SIZE_SHIFTS = 48;

    static final long LOCAL_SIZE_MASK = 0x0000ffff00000000L;
    static final int LOCAL_SIZE_SHIFTS = 32;

    static final long STACK_SIZE_MASK = 0x00000000ffff0000L;
    static final int STACK_SIZE_SHIFTS = 16;

    static final long FUNCTION_INDEX_MASK = 0x000000000000ffffL;
    static final int FUNCTION_INDEX_SHIFTS = 0;

    static final long MAX_UNSIGNED_SHORT = 0xffffL;

    static int getLabelSize(long frameId) {
        return (int) ((frameId & LABEL_SIZE_MASK) >>> LABEL_SIZE_SHIFTS);
    }

    static int getLocalSize(long frameId) {
        return (int) ((frameId & LOCAL_SIZE_MASK) >>> LOCAL_SIZE_SHIFTS);
    }

    static int getStackSize(long frameId) {
        return (int) ((frameId & STACK_SIZE_MASK) >>> STACK_SIZE_SHIFTS);
    }

    static int getFunctionIndex(long frameId) {
        return (int) ((frameId & FUNCTION_INDEX_MASK) >>> FUNCTION_INDEX_SHIFTS);
    }

    static long setLabelSize(long frameId, int labelSize) {
        return (frameId & (~LABEL_SIZE_MASK)) | ((labelSize & MAX_UNSIGNED_SHORT) << LABEL_SIZE_SHIFTS);
    }

    static long setLocalSize(long frameId, int localSize) {
        return (frameId & (~LOCAL_SIZE_MASK)) | ((localSize & MAX_UNSIGNED_SHORT) << LOCAL_SIZE_SHIFTS);
    }

    static long setStackSize(long frameId, int stackSize) {
        return (frameId & (~STACK_SIZE_MASK)) | ((stackSize & MAX_UNSIGNED_SHORT) << STACK_SIZE_SHIFTS);
    }

    static long setFunctionIndex(long frameId, int frameIndex) {
        return (frameId & (~FUNCTION_INDEX_MASK)) | ((frameIndex & MAX_UNSIGNED_SHORT) << FUNCTION_INDEX_SHIFTS);
    }
}
