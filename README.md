# Java Vector API Benchmarks

JMH benchmarks demonstrating performance characteristics of Java's Vector API (JEP 338/417/438).

## Key Finding: `LongVector.min()` and `LongVector.max()` Performance Issue

**The `min()` and `max()` methods on `LongVector` are 5-10x slower than the equivalent compare+blend pattern.**

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

### VectorMinMaxBenchmark (size=4096, Java 25)

```
Benchmark                                       (size)   Mode  Cnt  Score   Units
VectorMinMaxBenchmark.maxCompareBlend             4096  thrpt    2  0.734  ops/us
VectorMinMaxBenchmark.maxCompareBlendUnrolled4    4096  thrpt    2  1.917  ops/us
VectorMinMaxBenchmark.maxScalar                   4096  thrpt    2  0.666  ops/us
VectorMinMaxBenchmark.maxVectorMethod             4096  thrpt    2  0.070  ops/us  <-- BUG
VectorMinMaxBenchmark.minCompareBlend             4096  thrpt    2  0.658  ops/us
VectorMinMaxBenchmark.minCompareBlendUnrolled4    4096  thrpt    2  1.873  ops/us
VectorMinMaxBenchmark.minLanewise                 4096  thrpt    2  0.070  ops/us  <-- BUG
VectorMinMaxBenchmark.minScalar                   4096  thrpt    2  0.653  ops/us
VectorMinMaxBenchmark.minVectorMethod             4096  thrpt    2  0.072  ops/us  <-- BUG
```

| Benchmark | Throughput | vs Scalar | Notes |
|-----------|------------|-----------|-------|
| minScalar | 0.653 ops/us | baseline | |
| **minVectorMethod** | 0.072 ops/us | **9x slower** | **BUG** |
| **minLanewise** | 0.070 ops/us | **9x slower** | **BUG** |
| minCompareBlend | 0.658 ops/us | ~same | Workaround |
| minCompareBlendUnrolled4 | 1.873 ops/us | 2.9x faster | Best |

The `.min()` and `.max()` methods are **9x slower than scalar code**!

## Test Environment

- Java 25 (build 25+37-LTS-jvmci-b01, GraalVM)
- OS: Linux 6.8.0-90-generic x86_64
- Vector API: jdk.incubator.vector (incubator module)
- SPECIES_PREFERRED: LongVector[4] (256-bit AVX2)

## Benchmarks Included

1. **VectorMinMaxBenchmark** - Demonstrates the min()/max() performance issue
2. **VectorSumBenchmark** - Sum operations with various unrolling factors
3. **VectorStringBenchmark** - Byte array equality and comparison

## Bug Report Draft

### Title
`LongVector.min()` and `LongVector.max()` significantly slower than compare+blend equivalent

### Description
The `min()` and `max()` methods on `LongVector` (and likely other vector types) are 5-10x slower than the semantically equivalent compare+blend pattern.

### Expected Behavior
```java
min = min.min(v);
```
Should be at least as fast as:
```java
min = min.blend(v, v.lt(min));
```

### Actual Behavior
The `min()` method is 5-10x slower than the compare+blend pattern in JMH benchmarks.

### Steps to Reproduce
1. Clone this repository
2. Build: `mvn clean package`
3. Run: `java --enable-preview --add-modules=jdk.incubator.vector -jar target/benchmarks.jar VectorMinMaxBenchmark`

### Environment
- Java version: 25
- OS: Linux x86_64
- CPU: (varies)

### Additional Notes
- The issue affects both `min()` and `max()` methods
- The issue affects both direct method calls and `lanewise(VectorOperators.MIN/MAX, ...)`
- The compare+blend workaround provides expected SIMD performance
- 4x loop unrolling further improves performance by breaking dependency chains

## License

BSD 3-Clause License (same as Brackit project)
