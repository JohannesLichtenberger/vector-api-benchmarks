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

## Expected Results

### VectorMinMaxBenchmark (size=4096)

| Benchmark | Throughput | Notes |
|-----------|------------|-------|
| minScalar | baseline | |
| minVectorMethod | ~0.5x scalar | **BUG: should be faster** |
| minCompareBlend | ~2-3x scalar | Workaround |
| minCompareBlendUnrolled4 | ~3-4x scalar | Best performance |

### VectorSumBenchmark (size=4096)

| Benchmark | Throughput | Notes |
|-----------|------------|-------|
| sumLongScalar | baseline | |
| sumLongVector | ~1.2x scalar | Basic SIMD |
| sumLongVectorUnrolled4 | ~1.6x scalar | With unrolling |
| sumDoubleScalar | baseline | |
| sumDoubleVectorUnrolled4 | ~9x scalar | Significant speedup |

## Test Environment

- Java 25 EA (build 25-ea+5-356)
- OS: Linux 6.8.0
- Vector API: jdk.incubator.vector (incubator module)

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
- Java version: 25-ea+5-356
- OS: Linux x86_64
- CPU: (varies)

### Additional Notes
- The issue affects both `min()` and `max()` methods
- The issue affects both direct method calls and `lanewise(VectorOperators.MIN/MAX, ...)`
- The compare+blend workaround provides expected SIMD performance
- 4x loop unrolling further improves performance by breaking dependency chains

## License

BSD 3-Clause License (same as Brackit project)
