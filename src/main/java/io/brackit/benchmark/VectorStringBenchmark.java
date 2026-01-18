package io.brackit.benchmark;

import jdk.incubator.vector.*;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * JMH Benchmark for Vector API string (byte array) operations.
 * Compares SIMD-accelerated string equality and comparison against scalar implementations.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgs = {"--enable-preview", "--add-modules=jdk.incubator.vector"})
public class VectorStringBenchmark {

    private static final VectorSpecies<Byte> BYTE_SPECIES = ByteVector.SPECIES_PREFERRED;

    @Param({"64", "256", "1024", "4096"})
    int size;

    byte[] strA;
    byte[] strB;
    byte[] strDifferent;

    @Setup
    public void setup() {
        strA = new byte[size];
        strB = new byte[size];
        strDifferent = new byte[size];

        Random rng = new Random(42);
        rng.nextBytes(strA);
        System.arraycopy(strA, 0, strB, 0, size);
        System.arraycopy(strA, 0, strDifferent, 0, size);
        // Make last byte different
        strDifferent[size - 1] = (byte) (strA[size - 1] + 1);
    }

    // ==================== STRING EQUALS (identical strings) ====================

    @Benchmark
    public boolean equalsScalar_identical() {
        return Arrays.equals(strA, strB);
    }

    @Benchmark
    public boolean equalsVector_identical() {
        return stringEqualsVector(strA, strB);
    }

    // ==================== STRING EQUALS (different strings) ====================

    @Benchmark
    public boolean equalsScalar_different() {
        return Arrays.equals(strA, strDifferent);
    }

    @Benchmark
    public boolean equalsVector_different() {
        return stringEqualsVector(strA, strDifferent);
    }

    // ==================== STRING COMPARE ====================

    @Benchmark
    public int compareScalar() {
        return Arrays.compare(strA, strDifferent);
    }

    @Benchmark
    public int compareVector() {
        return stringCompareVector(strA, strDifferent);
    }

    // ==================== IMPLEMENTATIONS ====================

    private boolean stringEqualsVector(byte[] a, byte[] b) {
        if (a == b) return true;
        if (a.length != b.length) return false;

        int i = 0;
        int length = a.length;
        int vectorLength = BYTE_SPECIES.length();
        int limit = length - (length % vectorLength);

        for (; i < limit; i += vectorLength) {
            ByteVector va = ByteVector.fromArray(BYTE_SPECIES, a, i);
            ByteVector vb = ByteVector.fromArray(BYTE_SPECIES, b, i);
            if (!va.eq(vb).allTrue()) {
                return false;
            }
        }

        // Handle remaining bytes
        for (; i < length; i++) {
            if (a[i] != b[i]) return false;
        }

        return true;
    }

    private int stringCompareVector(byte[] a, byte[] b) {
        if (a == b) return 0;

        int minLength = Math.min(a.length, b.length);
        int i = 0;

        int vectorLength = BYTE_SPECIES.length();
        int limit = minLength - (minLength % vectorLength);

        for (; i < limit; i += vectorLength) {
            ByteVector va = ByteVector.fromArray(BYTE_SPECIES, a, i);
            ByteVector vb = ByteVector.fromArray(BYTE_SPECIES, b, i);
            VectorMask<Byte> neq = va.compare(VectorOperators.NE, vb);
            if (neq.anyTrue()) {
                int firstDiff = neq.firstTrue();
                return Byte.compareUnsigned(a[i + firstDiff], b[i + firstDiff]);
            }
        }

        // Handle remaining bytes
        for (; i < minLength; i++) {
            int cmp = Byte.compareUnsigned(a[i], b[i]);
            if (cmp != 0) return cmp;
        }

        return a.length - b.length;
    }

    public static void main(String[] args) throws RunnerException {
        System.out.println("Vector API Information:");
        System.out.println("  BYTE_SPECIES: " + BYTE_SPECIES + " (" + BYTE_SPECIES.length() + " bytes)");
        System.out.println();

        Options opt = new OptionsBuilder()
                .include(VectorStringBenchmark.class.getSimpleName())
                .build();

        new Runner(opt).run();
    }
}
