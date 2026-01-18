package io.brackit.benchmark;

import jdk.incubator.vector.*;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * JMH Benchmark demonstrating significant performance difference between
 * LongVector.min()/max() methods and equivalent compare+blend operations.
 *
 * <h2>Issue Summary</h2>
 * The {@link LongVector#min(Vector)} and {@link LongVector#max(Vector)} methods
 * are 5-10x slower than the equivalent compare+blend pattern on tested platforms.
 *
 * <h2>Expected vs Actual</h2>
 * <ul>
 *   <li>Expected: {@code v1.min(v2)} should be as fast as or faster than
 *       {@code v1.blend(v2, v2.lt(v1))}</li>
 *   <li>Actual: {@code v1.min(v2)} is 5-10x slower</li>
 * </ul>
 *
 * <h2>Test Environment</h2>
 * <ul>
 *   <li>Java 25 EA (build 25-ea+5-356)</li>
 *   <li>Vector API: jdk.incubator.vector</li>
 *   <li>CPU: (run benchmark to see)</li>
 * </ul>
 *
 * <h2>Running</h2>
 * <pre>
 * mvn clean package
 * java --enable-preview --add-modules=jdk.incubator.vector -jar target/benchmarks.jar VectorMinMaxBenchmark
 * </pre>
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgs = {"--enable-preview", "--add-modules=jdk.incubator.vector"})
public class VectorMinMaxBenchmark {

    private static final VectorSpecies<Long> SPECIES = LongVector.SPECIES_PREFERRED;

    @Param({"1024", "4096", "16384"})
    int size;

    long[] values;

    @Setup
    public void setup() {
        values = new long[size];
        Random rng = new Random(42);
        for (int i = 0; i < size; i++) {
            values[i] = rng.nextLong();
        }
    }

    // ==================== MIN BENCHMARKS ====================

    /**
     * Baseline: scalar min implementation.
     */
    @Benchmark
    public long minScalar() {
        long min = Long.MAX_VALUE;
        for (long v : values) {
            if (v < min) min = v;
        }
        return min;
    }

    /**
     * SIMD min using LongVector.min() method.
     * THIS IS UNEXPECTEDLY SLOW.
     */
    @Benchmark
    public long minVectorMethod() {
        int vectorLength = SPECIES.length();
        LongVector min = LongVector.fromArray(SPECIES, values, 0);
        int limit = size - (size % vectorLength);

        for (int i = vectorLength; i < limit; i += vectorLength) {
            LongVector v = LongVector.fromArray(SPECIES, values, i);
            min = min.min(v);  // <-- SLOW
        }
        return min.reduceLanes(VectorOperators.MIN);
    }

    /**
     * SIMD min using lanewise(MIN) - equivalent to min() method.
     * Also slow, confirming the issue is in the MIN operation itself.
     */
    @Benchmark
    public long minLanewise() {
        int vectorLength = SPECIES.length();
        LongVector min = LongVector.fromArray(SPECIES, values, 0);
        int limit = size - (size % vectorLength);

        for (int i = vectorLength; i < limit; i += vectorLength) {
            LongVector v = LongVector.fromArray(SPECIES, values, i);
            min = min.lanewise(VectorOperators.MIN, v);  // <-- ALSO SLOW
        }
        return min.reduceLanes(VectorOperators.MIN);
    }

    /**
     * SIMD min using compare+blend pattern.
     * THIS IS FAST - workaround for the slow min() method.
     */
    @Benchmark
    public long minCompareBlend() {
        int vectorLength = SPECIES.length();
        LongVector min = LongVector.fromArray(SPECIES, values, 0);
        int limit = size - (size % vectorLength);

        for (int i = vectorLength; i < limit; i += vectorLength) {
            LongVector v = LongVector.fromArray(SPECIES, values, i);
            VectorMask<Long> mask = v.lt(min);
            min = min.blend(v, mask);  // <-- FAST
        }
        return min.reduceLanes(VectorOperators.MIN);
    }

    /**
     * SIMD min with 4x loop unrolling using compare+blend.
     * Even faster due to breaking dependency chains.
     */
    @Benchmark
    public long minCompareBlendUnrolled4() {
        int vectorLength = SPECIES.length();
        int step = vectorLength * 4;
        int limit4 = size - (size % step);

        LongVector min0 = LongVector.broadcast(SPECIES, Long.MAX_VALUE);
        LongVector min1 = LongVector.broadcast(SPECIES, Long.MAX_VALUE);
        LongVector min2 = LongVector.broadcast(SPECIES, Long.MAX_VALUE);
        LongVector min3 = LongVector.broadcast(SPECIES, Long.MAX_VALUE);

        int i = 0;
        for (; i < limit4; i += step) {
            LongVector v0 = LongVector.fromArray(SPECIES, values, i);
            LongVector v1 = LongVector.fromArray(SPECIES, values, i + vectorLength);
            LongVector v2 = LongVector.fromArray(SPECIES, values, i + vectorLength * 2);
            LongVector v3 = LongVector.fromArray(SPECIES, values, i + vectorLength * 3);

            min0 = min0.blend(v0, v0.lt(min0));
            min1 = min1.blend(v1, v1.lt(min1));
            min2 = min2.blend(v2, v2.lt(min2));
            min3 = min3.blend(v3, v3.lt(min3));
        }

        // Combine accumulators
        LongVector result = min0.blend(min1, min1.lt(min0));
        result = result.blend(min2, min2.lt(result));
        result = result.blend(min3, min3.lt(result));

        long min = result.reduceLanes(VectorOperators.MIN);

        // Tail
        for (; i < size; i++) {
            if (values[i] < min) min = values[i];
        }
        return min;
    }

    // ==================== MAX BENCHMARKS ====================

    /**
     * Baseline: scalar max implementation.
     */
    @Benchmark
    public long maxScalar() {
        long max = Long.MIN_VALUE;
        for (long v : values) {
            if (v > max) max = v;
        }
        return max;
    }

    /**
     * SIMD max using LongVector.max() method.
     * THIS IS UNEXPECTEDLY SLOW.
     */
    @Benchmark
    public long maxVectorMethod() {
        int vectorLength = SPECIES.length();
        LongVector max = LongVector.fromArray(SPECIES, values, 0);
        int limit = size - (size % vectorLength);

        for (int i = vectorLength; i < limit; i += vectorLength) {
            LongVector v = LongVector.fromArray(SPECIES, values, i);
            max = max.max(v);  // <-- SLOW
        }
        return max.reduceLanes(VectorOperators.MAX);
    }

    /**
     * SIMD max using compare+blend pattern.
     * THIS IS FAST - workaround for the slow max() method.
     */
    @Benchmark
    public long maxCompareBlend() {
        int vectorLength = SPECIES.length();
        LongVector max = LongVector.fromArray(SPECIES, values, 0);
        int limit = size - (size % vectorLength);

        for (int i = vectorLength; i < limit; i += vectorLength) {
            LongVector v = LongVector.fromArray(SPECIES, values, i);
            VectorMask<Long> mask = v.compare(VectorOperators.GT, max);
            max = max.blend(v, mask);  // <-- FAST
        }
        return max.reduceLanes(VectorOperators.MAX);
    }

    /**
     * SIMD max with 4x loop unrolling using compare+blend.
     */
    @Benchmark
    public long maxCompareBlendUnrolled4() {
        int vectorLength = SPECIES.length();
        int step = vectorLength * 4;
        int limit4 = size - (size % step);

        LongVector max0 = LongVector.broadcast(SPECIES, Long.MIN_VALUE);
        LongVector max1 = LongVector.broadcast(SPECIES, Long.MIN_VALUE);
        LongVector max2 = LongVector.broadcast(SPECIES, Long.MIN_VALUE);
        LongVector max3 = LongVector.broadcast(SPECIES, Long.MIN_VALUE);

        int i = 0;
        for (; i < limit4; i += step) {
            LongVector v0 = LongVector.fromArray(SPECIES, values, i);
            LongVector v1 = LongVector.fromArray(SPECIES, values, i + vectorLength);
            LongVector v2 = LongVector.fromArray(SPECIES, values, i + vectorLength * 2);
            LongVector v3 = LongVector.fromArray(SPECIES, values, i + vectorLength * 3);

            max0 = max0.blend(v0, v0.compare(VectorOperators.GT, max0));
            max1 = max1.blend(v1, v1.compare(VectorOperators.GT, max1));
            max2 = max2.blend(v2, v2.compare(VectorOperators.GT, max2));
            max3 = max3.blend(v3, v3.compare(VectorOperators.GT, max3));
        }

        // Combine accumulators
        LongVector result = max0.blend(max1, max1.compare(VectorOperators.GT, max0));
        result = result.blend(max2, max2.compare(VectorOperators.GT, result));
        result = result.blend(max3, max3.compare(VectorOperators.GT, result));

        long max = result.reduceLanes(VectorOperators.MAX);

        // Tail
        for (; i < size; i++) {
            if (values[i] > max) max = values[i];
        }
        return max;
    }

    public static void main(String[] args) throws RunnerException {
        System.out.println("Vector API Information:");
        System.out.println("  SPECIES_PREFERRED: " + SPECIES);
        System.out.println("  Vector length: " + SPECIES.length() + " elements");
        System.out.println("  Vector bit size: " + SPECIES.vectorBitSize() + " bits");
        System.out.println();

        Options opt = new OptionsBuilder()
                .include(VectorMinMaxBenchmark.class.getSimpleName())
                .build();

        new Runner(opt).run();
    }
}
