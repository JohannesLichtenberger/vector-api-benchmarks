# Java Vector API Benchmarks

JMH benchmarks demonstrating performance characteristics of Java's Vector API (JEP 338/417/438).

## Key Finding: GraalVM-specific `LongVector.min()` / `LongVector.max()` Performance Bug

**On GraalVM, the `min()` and `max()` methods on `LongVector` are 9x slower than on OpenJDK Temurin.**

This is a **GraalVM-specific issue** - OpenJDK Temurin handles these methods correctly.

| JVM | minVectorMethod | minScalar | minCompareBlend | Status |
|-----|-----------------|-----------|-----------------|--------|
| **Temurin 25.0.1** | 0.706 ops/us | 1.638 ops/us | 0.729 ops/us | ✓ OK |
| **GraalVM 25.0.1** | 0.076 ops/us | 0.739 ops/us | 0.736 ops/us | ✗ 9x slower |

### Reproduction

```java
// SLOW (~5-10x slower than expected):
min = min.min(v);

// FAST (workaround):
VectorMask<Long> mask = v.lt(min);
min = min.blend(v, mask);
```

Both produce identical results, but the compare+blend pattern is significantly faster.

## Building

```bash
mvn clean package
```

## Running Benchmarks

### All benchmarks:
```bash
java --enable-preview --add-modules=jdk.incubator.vector -jar target/benchmarks.jar
```

### Specific benchmarks:
```bash
# Min/Max performance issue demonstration
java --enable-preview --add-modules=jdk.incubator.vector -jar target/benchmarks.jar VectorMinMaxBenchmark

# Sum operations with loop unrolling
java --enable-preview --add-modules=jdk.incubator.vector -jar target/benchmarks.jar VectorSumBenchmark

# String operations
java --enable-preview --add-modules=jdk.incubator.vector -jar target/benchmarks.jar VectorStringBenchmark
```

### Quick test (fewer iterations):
```bash
java --enable-preview --add-modules=jdk.incubator.vector -jar target/benchmarks.jar -wi 2 -i 3 -f 1
```

## Actual Benchmark Results

### Temurin OpenJDK 25.0.1 (size=4096) - CORRECT

```
Benchmark                                       (size)   Mode  Cnt  Score   Units
VectorMinMaxBenchmark.maxCompareBlend             4096  thrpt    3  0.729  ops/us
VectorMinMaxBenchmark.maxCompareBlendUnrolled4    4096  thrpt    3  2.011  ops/us
VectorMinMaxBenchmark.maxScalar                   4096  thrpt    3  1.550  ops/us
VectorMinMaxBenchmark.maxVectorMethod             4096  thrpt    3  0.691  ops/us  <-- OK
VectorMinMaxBenchmark.minCompareBlend             4096  thrpt    3  0.729  ops/us
VectorMinMaxBenchmark.minCompareBlendUnrolled4    4096  thrpt    3  2.012  ops/us
VectorMinMaxBenchmark.minLanewise                 4096  thrpt    3  0.703  ops/us  <-- OK
VectorMinMaxBenchmark.minScalar                   4096  thrpt    3  1.638  ops/us
VectorMinMaxBenchmark.minVectorMethod             4096  thrpt    3  0.706  ops/us  <-- OK
```

### GraalVM 25.0.1 (size=4096) - BUG

```
Benchmark                                       (size)   Mode  Cnt  Score   Units
VectorMinMaxBenchmark.maxCompareBlend             4096  thrpt    3  0.738  ops/us
VectorMinMaxBenchmark.maxCompareBlendUnrolled4    4096  thrpt    3  2.086  ops/us
VectorMinMaxBenchmark.maxScalar                   4096  thrpt    3  0.740  ops/us
VectorMinMaxBenchmark.maxVectorMethod             4096  thrpt    3  0.077  ops/us  <-- BUG: 9x slower
VectorMinMaxBenchmark.minCompareBlend             4096  thrpt    3  0.736  ops/us
VectorMinMaxBenchmark.minCompareBlendUnrolled4    4096  thrpt    3  2.055  ops/us
VectorMinMaxBenchmark.minLanewise                 4096  thrpt    3  0.077  ops/us  <-- BUG: 9x slower
VectorMinMaxBenchmark.minScalar                   4096  thrpt    3  0.739  ops/us
VectorMinMaxBenchmark.minVectorMethod             4096  thrpt    3  0.076  ops/us  <-- BUG: 9x slower
```

On GraalVM, `.min()` and `.max()` are **9x slower than scalar code** and **9x slower than on Temurin**!

## Test Environment

- CPU: 12th Gen Intel Core i7-12700H
- OS: Linux 6.8.0-90-generic x86_64 (Ubuntu)
- Memory: 32 GB
- JVMs tested:
  - Temurin 25.0.1+8-LTS
  - GraalVM 25.0.1+8.1-LTS
- Vector API: jdk.incubator.vector (incubator module)
- SPECIES_PREFERRED: LongVector[4] (256-bit AVX2)

## Benchmarks Included

1. **VectorMinMaxBenchmark** - Demonstrates the min()/max() performance issue
2. **VectorSumBenchmark** - Sum operations with various unrolling factors
3. **VectorStringBenchmark** - Byte array equality and comparison

## Bug Report Draft (for GraalVM)

### Title
`LongVector.min()` and `LongVector.max()` 9x slower on GraalVM than on OpenJDK Temurin

### Description
The `min()` and `max()` methods on `LongVector` are 9x slower on GraalVM 25.0.1 compared to OpenJDK Temurin 25.0.1. On Temurin, these methods perform correctly. On GraalVM, they are slower than scalar code.

### Expected Behavior
Performance should match OpenJDK Temurin:
```
minVectorMethod: ~0.7 ops/us (Temurin)
```

### Actual Behavior on GraalVM
```
minVectorMethod: ~0.07 ops/us (GraalVM) - 9x slower!
```

### Steps to Reproduce
1. Clone this repository
2. Build: `mvn clean package`
3. Run with GraalVM: `java --enable-preview --add-modules=jdk.incubator.vector -jar target/benchmarks.jar VectorMinMaxBenchmark`
4. Compare with Temurin to see the difference

### Environment
- GraalVM 25.0.1 (Oracle GraalVM 25.0.1+8.1)
- Temurin 25.0.1 (for comparison)
- OS: Linux x86_64

### Additional Notes
- The issue affects both `min()` and `max()` methods
- The issue affects both direct method calls and `lanewise(VectorOperators.MIN/MAX, ...)`
- The compare+blend workaround (`min.blend(v, v.lt(min))`) works correctly on GraalVM
- This appears to be a GraalVM compiler issue, not a Vector API issue

## License

BSD 3-Clause License (same as Brackit project)
