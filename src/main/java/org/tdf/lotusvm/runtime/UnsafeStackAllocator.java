package org.tdf.lotusvm.runtime;

import org.tdf.lotusvm.types.LongBuffer;
import org.tdf.lotusvm.types.UnsafeLongBuffer;

public class UnsafeStackAllocator extends BaseStackAllocator{
    public UnsafeStackAllocator(int maxStackSize, int maxFrames, int maxLabelSize) {
        super(maxStackSize, maxFrames, maxLabelSize);
    }

    @Override
    protected LongBuffer createBuffer(int cap) {
        return new UnsafeLongBuffer(cap);
    }
}
