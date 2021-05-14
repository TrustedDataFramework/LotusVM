# LotusVM

A web assembly 1.0 interpreter implementation in pure java.
This interpreter intended to provide a smart contract runtime for block chain. 

We use off-heap memory to avoid frequently garbage collection (See UnsafeMemory.java and UnsafeStackAllocator.java).
The UnsafeMemory and UnsafeStackAllocator should be closed explicitly.

- How to Use?

See src/test/org.tdf.lotusvm.Bench.java 

Not support:
1. sign-extension operators
2. return multi-value
3. floating number operations, i.e. f32.const, f32_nearest, f64_round (undefined behavior in some platform and error-prone)

TODO: module validation