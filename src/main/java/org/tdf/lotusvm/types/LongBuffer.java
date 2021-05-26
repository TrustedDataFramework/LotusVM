package org.tdf.lotusvm.types;

import java.io.Closeable;

public interface LongBuffer extends Closeable {
    long get(int index);
    void set(int index, long val);
    int size();
    void push(long val);

    default void close() {}
}
