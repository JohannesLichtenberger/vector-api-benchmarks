package io.brackit.benchmark;

import jdk.incubator.vector.*;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

/**
 * JMH Benchmark for Vector API sum operations.
 * Demonstrates the performance benefits of loop unrolling with multiple accumulators.
 *
 * <h2>Key Findings</h2>
 * <ul>
 *   <li>4x loop unrolling provides significant speedup by breaking dependency chains</li>
 *   <li>sumLong: ~1.6x faster than scalar with unrolling</li>
 *   <li>sumDouble: ~9x faster than scalar with unrolling</li>
 * </ul>
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgs = {"--enable-preview", "--add-modules=jdk.incubator.vector"})
public class VectorSumBenchmark {

    private static final VectorSpecies<Long> LONG_SPECIES = LongVector.SPECIES_PREFERRED;
    private static final VectorSpecies<Double> DOUBLE_SPECIES = DoubleVector.SPECIES_PREFERRED;

    @Param({"1024", "4096", "16384"})
    int size;

    long[] longs;
    double[] doubles;

    @Setup
    public void setup() {
        longs = new long[size];
        doubles = new double[size];
        for (int i = 0; i < size; i++) {
            longs[i] = i;
            doubles[i] = i * 0.1;
        }
    }

    // ==================== LONG SUM BENCHMARKS ====================

    @Benchmark
    public long sumLongScalar() {
        long sum = 0;
        for (long v : longs) sum += v;
        return sum;
    }

    @Benchmark
    public long sumLongVector() {
        int vectorLength = LONG_SPECIES.length();
        LongVector sum = LongVector.zero(LONG_SPECIES);
        int limit = size - (size % vectorLength);

        for (int i = 0; i < limit; i += vectorLength) {
            sum = sum.add(LongVector.fromArray(LONG_SPECIES, longs, i));
        }

        long result = sum.reduceLanes(VectorOperators.ADD);
        for (int i = limit; i < size; i++) {
            result += longs[i];
        }
        return result;
    }

    @Benchmark
    public long sumLongVectorUnrolled2() {
        int vectorLength = LONG_SPECIES.length();
        int step = vectorLength * 2;
        int limit = size - (size % step);

        LongVector sum0 = LongVector.zero(LONG_SPECIES);
        LongVector sum1 = LongVector.zero(LONG_SPECIES);

        int i = 0;
        for (; i < limit; i += step) {
            sum0 = sum0.add(LongVector.fromArray(LONG_SPECIES, longs, i));
            sum1 = sum1.add(LongVector.fromArray(LONG_SPECIES, longs, i + vectorLength));
        }

        long result = sum0.add(sum1).reduceLanes(VectorOperators.ADD);

        // Remaining vectors and tail
        int vectorLimit = size - (size % vectorLength);
        for (; i < vectorLimit; i += vectorLength) {
            result += LongVector.fromArray(LONG_SPECIES, longs, i).reduceLanes(VectorOperators.ADD);
        }
        for (; i < size; i++) {
            result += longs[i];
        }
        return result;
    }

    @Benchmark
    public long sumLongVectorUnrolled4() {
        int vectorLength = LONG_SPECIES.length();
        int step = vectorLength * 4;
        int limit = size - (size % step);

        LongVector sum0 = LongVector.zero(LONG_SPECIES);
        LongVector sum1 = LongVector.zero(LONG_SPECIES);
        LongVector sum2 = LongVector.zero(LONG_SPECIES);
        LongVector sum3 = LongVector.zero(LONG_SPECIES);

        int i = 0;
        for (; i < limit; i += step) {
            sum0 = sum0.add(LongVector.fromArray(LONG_SPECIES, longs, i));
            sum1 = sum1.add(LongVector.fromArray(LONG_SPECIES, longs, i + vectorLength));
            sum2 = sum2.add(LongVector.fromArray(LONG_SPECIES, longs, i + vectorLength * 2));
            sum3 = sum3.add(LongVector.fromArray(LONG_SPECIES, longs, i + vectorLength * 3));
        }

        long result = sum0.add(sum1).add(sum2).add(sum3).reduceLanes(VectorOperators.ADD);

        // Remaining vectors and tail
        int vectorLimit = size - (size % vectorLength);
        for (; i < vectorLimit; i += vectorLength) {
            result += LongVector.fromArray(LONG_SPECIES, longs, i).reduceLanes(VectorOperators.ADD);
        }
        for (; i < size; i++) {
            result += longs[i];
        }
        return result;
    }

    @Benchmark
    public long sumLongVectorUnrolled8() {
        int vectorLength = LONG_SPECIES.length();
        int step = vectorLength * 8;
        int limit = size - (size % step);

        LongVector s0 = LongVector.zero(LONG_SPECIES);
        LongVector s1 = LongVector.zero(LONG_SPECIES);
        LongVector s2 = LongVector.zero(LONG_SPECIES);
        LongVector s3 = LongVector.zero(LONG_SPECIES);
        LongVector s4 = LongVector.zero(LONG_SPECIES);
        LongVector s5 = LongVector.zero(LONG_SPECIES);
        LongVector s6 = LongVector.zero(LONG_SPECIES);
        LongVector s7 = LongVector.zero(LONG_SPECIES);

        int i = 0;
        for (; i < limit; i += step) {
            s0 = s0.add(LongVector.fromArray(LONG_SPECIES, longs, i));
            s1 = s1.add(LongVector.fromArray(LONG_SPECIES, longs, i + vectorLength));
            s2 = s2.add(LongVector.fromArray(LONG_SPECIES, longs, i + vectorLength * 2));
            s3 = s3.add(LongVector.fromArray(LONG_SPECIES, longs, i + vectorLength * 3));
            s4 = s4.add(LongVector.fromArray(LONG_SPECIES, longs, i + vectorLength * 4));
            s5 = s5.add(LongVector.fromArray(LONG_SPECIES, longs, i + vectorLength * 5));
            s6 = s6.add(LongVector.fromArray(LONG_SPECIES, longs, i + vectorLength * 6));
            s7 = s7.add(LongVector.fromArray(LONG_SPECIES, longs, i + vectorLength * 7));
        }

        long result = s0.add(s1).add(s2).add(s3).add(s4).add(s5).add(s6).add(s7)
                .reduceLanes(VectorOperators.ADD);

        // Tail
        for (; i < size; i++) {
            result += longs[i];
        }
        return result;
    }

    // ==================== DOUBLE SUM BENCHMARKS ====================

    @Benchmark
    public double sumDoubleScalar() {
        double sum = 0;
        for (double v : doubles) sum += v;
        return sum;
    }

    @Benchmark
    public double sumDoubleVector() {
        int vectorLength = DOUBLE_SPECIES.length();
        DoubleVector sum = DoubleVector.zero(DOUBLE_SPECIES);
        int limit = size - (size % vectorLength);

        for (int i = 0; i < limit; i += vectorLength) {
            sum = sum.add(DoubleVector.fromArray(DOUBLE_SPECIES, doubles, i));
        }

        double result = sum.reduceLanes(VectorOperators.ADD);
        for (int i = limit; i < size; i++) {
            result += doubles[i];
        }
        return result;
    }

    @Benchmark
    public double sumDoubleVectorUnrolled4() {
        int vectorLength = DOUBLE_SPECIES.length();
        int step = vectorLength * 4;
        int limit = size - (size % step);

        DoubleVector sum0 = DoubleVector.zero(DOUBLE_SPECIES);
        DoubleVector sum1 = DoubleVector.zero(DOUBLE_SPECIES);
        DoubleVector sum2 = DoubleVector.zero(DOUBLE_SPECIES);
        DoubleVector sum3 = DoubleVector.zero(DOUBLE_SPECIES);

        int i = 0;
        for (; i < limit; i += step) {
            sum0 = sum0.add(DoubleVector.fromArray(DOUBLE_SPECIES, doubles, i));
            sum1 = sum1.add(DoubleVector.fromArray(DOUBLE_SPECIES, doubles, i + vectorLength));
            sum2 = sum2.add(DoubleVector.fromArray(DOUBLE_SPECIES, doubles, i + vectorLength * 2));
            sum3 = sum3.add(DoubleVector.fromArray(DOUBLE_SPECIES, doubles, i + vectorLength * 3));
        }

        double result = sum0.add(sum1).add(sum2).add(sum3).reduceLanes(VectorOperators.ADD);

        // Remaining vectors and tail
        int vectorLimit = size - (size % vectorLength);
        for (; i < vectorLimit; i += vectorLength) {
            result += DoubleVector.fromArray(DOUBLE_SPECIES, doubles, i).reduceLanes(VectorOperators.ADD);
        }
        for (; i < size; i++) {
            result += doubles[i];
        }
        return result;
    }

    public static void main(String[] args) throws RunnerException {
        System.out.println("Vector API Information:");
        System.out.println("  LONG_SPECIES: " + LONG_SPECIES + " (" + LONG_SPECIES.length() + " elements)");
        System.out.println("  DOUBLE_SPECIES: " + DOUBLE_SPECIES + " (" + DOUBLE_SPECIES.length() + " elements)");
        System.out.println();

        Options opt = new OptionsBuilder()
                .include(VectorSumBenchmark.class.getSimpleName())
                .build();

        new Runner(opt).run();
    }
}
