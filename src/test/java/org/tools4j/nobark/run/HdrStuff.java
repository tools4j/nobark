/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2018-2022 nobark (tools4j), Marco Terzer, Anton Anufriev
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.tools4j.nobark.run;

import org.HdrHistogram.Histogram;
import org.HdrHistogram.PackedHistogram;
import org.HdrHistogram.packedarray.PackedLongArray;
import org.tools4j.nobark.histogram.Histogram.Recorder;
import org.tools4j.nobark.histogram.Histogram.Reporter;
import org.tools4j.nobark.histogram.IntCountsHistogram;
import org.tools4j.nobark.histogram.LongCountsHistogram;
import org.tools4j.nobark.histogram.VarCountsHistogram;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.util.Arrays;

/**
 * <pre>

 ..00  >> 0
 ..01  >> 0
 ..10  >> 0
 ..11  >> 0
(n * 2^0)

 .10x  >> 1
 .11x  >> 1
 (n/2 * 2^1 == n * 2^0)

 10xx  >> 2
 11xx  >> 2
 (n/2 * 2^1 == n * 2^1)

 * </pre>
 */
public class HdrStuff {

    public static void main(String[] args) {
//        printCountSizes();

        final int digits = 3;
//        final int n = 100_000;
        final int n = 1_000_000;
//        final int n = 10_000_000;
//        final int n = 100_000_000;
        for (int i = 0; i < 3; i++) {
            randomSampleHist(digits, n);
        }
    }

    private static void printCountSizes() {
        for (int prec = 1; prec <= 5; prec++) {
            for (final long max : new long[] {1_000_000_000, 10_000_000_000L, 100_000_000_000L, 1000_000_000_000L, Long.MAX_VALUE}) {
                final Histogram histogram = new Histogram(1, max, prec);
                printCountSize(histogram);
            }
        }
    }

    private static void printCountSize(final Object obj) {
        if (obj instanceof Histogram) {
            if (obj instanceof PackedHistogram) {
                final PackedHistogram hist = (PackedHistogram) obj;
                final long[] counts = getFieldValue(Histogram.class, long[].class, "counts", hist);
                assert counts == null;
                final PackedLongArray packedLongArray = getFieldValue(PackedHistogram.class, PackedLongArray.class, "packedCounts", hist);
                final Object arrayContext = getFieldValue(packedLongArray.getClass().getSuperclass(), Object.class, "arrayContext", packedLongArray);
                final long[] array = getFieldValue(arrayContext.getClass(), long[].class, "array", arrayContext);
                System.out.printf("%s: precision=%d, max=%d, array.length=%d, bytes=%d\n",
                        hist.getClass().getSimpleName(),
                        hist.getNumberOfSignificantValueDigits(), hist.getHighestTrackableValue(),
                        array.length,
                        bytes(array));
            } else {
                final Histogram hist = (Histogram) obj;
                final long[] counts = getFieldValue(Histogram.class, long[].class, "counts", hist);
                System.out.printf("%s: precision=%d, max=%d, counts.length=%d, bytes=%d\n",
                        hist.getClass().getSimpleName(),
                        hist.getNumberOfSignificantValueDigits(), hist.getHighestTrackableValue(),
                        counts.length,
                        bytes(counts));
            }
        } else if (obj instanceof LongCountsHistogram) {
            final LongCountsHistogram hist = (LongCountsHistogram)obj;
            final int significantBits = getFieldValue(LongCountsHistogram.class, int.class, "significantBits", hist);
            final long[][] counts = getFieldValue(LongCountsHistogram.class, long[][].class, "counts", hist);
            System.out.printf("%s: bits=%d, counts.length=%d, sum(counts[*].length)=%d, bytes=%d\n",
                    obj.getClass().getSimpleName(),
                    significantBits, counts.length,
                    Arrays.stream(counts).mapToInt(a -> a == null ? 0 : a.length).sum(),
                    bytes(counts)
            );
        } else if (obj instanceof IntCountsHistogram) {
            final IntCountsHistogram hist = (IntCountsHistogram)obj;
            final int significantBits = getFieldValue(IntCountsHistogram.class, int.class, "significantBits", hist);
            final int[][] counts = getFieldValue(IntCountsHistogram.class, int[][].class, "counts", hist);
            System.out.printf("%s: bits=%d, counts.length=%d, sum(counts[*].length)=%d, bytes=%d\n",
                    obj.getClass().getSimpleName(),
                    significantBits, counts.length,
                    Arrays.stream(counts).mapToInt(a -> a == null ? 0 : a.length).sum(),
                    bytes(counts)
            );
        } else if (obj instanceof VarCountsHistogram) {
            final VarCountsHistogram hist = (VarCountsHistogram)obj;
            final int significantBits = getFieldValue(VarCountsHistogram.class, int.class, "significantBits", hist);
            final long[][] bitCounts = getFieldValue(VarCountsHistogram.class, long[][].class, "bitCounts", hist);
            final byte[][][] byteCounts = getFieldValue(VarCountsHistogram.class, byte[][][].class, "byteCounts", hist);
            final long[][][] longCounts = getFieldValue(VarCountsHistogram.class, long[][][].class, "longCounts", hist);
            System.out.printf("%s: bits=%d, bitCounts.length=%d, sum(bitCounts[*].length)=%d, " +
                            "byteCounts.length=%d, sum(byteCounts[*/**].length)=%d/%d, " +
                            "longCounts.length=%d, sum(longCounts[*/**].length)=%d/%s, bytes=%d\n",
                    hist.getClass().getSimpleName(),
                    significantBits,
                    bitCounts.length, Arrays.stream(bitCounts).mapToInt(a -> a == null ? 0 : a.length).sum(),
                    byteCounts.length, Arrays.stream(byteCounts).mapToInt(a -> a == null ? 0 : a.length).sum(),
                    Arrays.stream(byteCounts).mapToInt(a ->
                            a == null ? 0 : Arrays.stream(a).mapToInt(b -> b == null ? 0 : b.length).sum()).sum(),
                    longCounts.length, Arrays.stream(longCounts).mapToInt(a -> a == null ? 0 : a.length).sum(),
                    Arrays.stream(longCounts).mapToInt(a ->
                            a == null ? 0 : Arrays.stream(a).mapToInt(b -> b == null ? 0 : b.length).sum()).sum(),
                    bytes(bitCounts) + bytes(byteCounts) + bytes(longCounts)
            );
        } else if (obj instanceof Hist) {
            final Hist hist = (Hist)obj;
            System.out.printf("%s: bits=%d, counts.length=%d, sum(counts[*].length)=%d, bytes=%d\n",
                    obj.getClass().getSimpleName(),
                    hist.significantBits, hist.counts.length,
                    Arrays.stream(hist.counts).mapToInt(a -> a == null ? 0 : a.length).sum(),
                    bytes(hist.counts)
            );
        } else if (obj instanceof BitHist) {
            final BitHist hist = (BitHist)obj;
            System.out.printf("%s: bits=%d, ones.length=%d, sum(ones[*].length)=%d, counts.length=%d, " +
                            "sum(counts[*/**].length)=%d/%d, bytes=%d\n",
                    hist.getClass().getSimpleName(),
                    hist.significantBits,
                    hist.ones.length, Arrays.stream(hist.ones).mapToInt(a -> a == null ? 0 : a.length).sum(),
                    hist.counts.length, Arrays.stream(hist.counts).mapToInt(a -> a == null ? 0 : a.length).sum(),
                    Arrays.stream(hist.counts).mapToInt(a ->
                            a == null ? 0 : Arrays.stream(a).mapToInt(b -> b == null ? 0 : b.length).sum()).sum(),
                    bytes(hist.ones) + bytes(hist.counts)
            );
        } else if (obj instanceof ByteBitHist) {
            final ByteBitHist hist = (ByteBitHist)obj;
            System.out.printf("%s: bits=%d, ones.length=%d, sum(ones[*].length)=%d, counts.length=%d, " +
                            "sum(counts[*/**].length)=%d/%d, bytes=%d\n",
                    hist.getClass().getSimpleName(),
                    hist.significantBits,
                    hist.ones.length, Arrays.stream(hist.ones).mapToInt(a -> a == null ? 0 : a.length).sum(),
                    hist.counts.length, Arrays.stream(hist.counts).mapToInt(a -> a == null ? 0 : a.length).sum(),
                    Arrays.stream(hist.counts).mapToInt(a ->
                            a == null ? 0 : Arrays.stream(a).mapToInt(b -> b == null ? 0 : b.length).sum()).sum(),
                    bytes(hist.ones) + bytes(hist.counts)
            );
        } else if (obj instanceof IntCountByteBitHist) {
            final IntCountByteBitHist hist = (IntCountByteBitHist)obj;
            System.out.printf("%s: bits=%d, ones.length=%d, sum(ones[*].length)=%d, counts.length=%d, " +
                            "sum(counts[*/**].length)=%d/%d, bytes=%d\n",
                    hist.getClass().getSimpleName(),
                    hist.significantBits,
                    hist.ones.length, Arrays.stream(hist.ones).mapToInt(a -> a == null ? 0 : a.length).sum(),
                    hist.counts.length, Arrays.stream(hist.counts).mapToInt(a -> a == null ? 0 : a.length).sum(),
                    Arrays.stream(hist.counts).mapToInt(a ->
                            a == null ? 0 : Arrays.stream(a).mapToInt(b -> b == null ? 0 : b.length).sum()).sum(),
                    bytes(hist.ones) + bytes(hist.counts)
            );
        } else if (obj instanceof VarCountByteBitHist) {
            final VarCountByteBitHist hist = (VarCountByteBitHist)obj;
            System.out.printf("%s: bits=%d, ones.length=%d, sum(ones[*].length)=%d, " +
                            "counts.length=%d, sum(counts[*/**].length)=%d/%d, " +
                            "bigCounts.length=%d, sum(bigCounts[*/**].length)=%d/%s, bytes=%d\n",
                    hist.getClass().getSimpleName(),
                    hist.significantBits,
                    hist.ones.length, Arrays.stream(hist.ones).mapToInt(a -> a == null ? 0 : a.length).sum(),
                    hist.counts.length, Arrays.stream(hist.counts).mapToInt(a -> a == null ? 0 : a.length).sum(),
                    Arrays.stream(hist.counts).mapToInt(a ->
                            a == null ? 0 : Arrays.stream(a).mapToInt(b -> b == null ? 0 : b.length).sum()).sum(),
                    hist.bigCounts.length, Arrays.stream(hist.bigCounts).mapToInt(a -> a == null ? 0 : a.length).sum(),
                    Arrays.stream(hist.bigCounts).mapToInt(a ->
                            a == null ? 0 : Arrays.stream(a).mapToInt(b -> b == null ? 0 : b.length).sum()).sum(),
                    bytes(hist.ones) + bytes(hist.counts) + bytes(hist.bigCounts)
            );
        } else if (obj instanceof VarCountBitHist) {
            final VarCountBitHist hist = (VarCountBitHist)obj;
            System.out.printf("%s: bits=%d, ones.length=%d, sum(ones[*].length)=%d, " +
                            "counts.length=%d, sum(counts[*/**].length)=%d/%d, " +
                            "bigCounts.length=%d, sum(bigCounts[*/**].length)=%d/%s, bytes=%d\n",
                    hist.getClass().getSimpleName(),
                    hist.significantBits,
                    hist.ones.length, Arrays.stream(hist.ones).mapToInt(a -> a == null ? 0 : a.length).sum(),
                    hist.counts.length, Arrays.stream(hist.counts).mapToInt(a -> a == null ? 0 : a.length).sum(),
                    Arrays.stream(hist.counts).mapToInt(a ->
                            a == null ? 0 : Arrays.stream(a).mapToInt(b -> b == null ? 0 : b.length).sum()).sum(),
                    hist.bigCounts.length, Arrays.stream(hist.bigCounts).mapToInt(a -> a == null ? 0 : a.length).sum(),
                    Arrays.stream(hist.bigCounts).mapToInt(a ->
                            a == null ? 0 : Arrays.stream(a).mapToInt(b -> b == null ? 0 : b.length).sum()).sum(),
                    bytes(hist.ones) + bytes(hist.counts) + bytes(hist.bigCounts)
            );
        } else {
            throw new IllegalArgumentException("Unsupported: " + obj);
        }
    }

    private static void randomSampleHist(final int digits, final int n) {
        final boolean testMax = false;
        final LongRandom rand = new LongRandom();
        final Histogram hist0 = new Histogram(1L,Long.MAX_VALUE / 2, digits);
        final Histogram hist1 = new PackedHistogram(1L,Long.MAX_VALUE / 2, digits);
//        final org.tools4j.nobark.histogram.Histogram hist2 = new Hist(digits);
//        final org.tools4j.nobark.histogram.Histogram hist3 = new BitHist(digits);
//        final org.tools4j.nobark.histogram.Histogram hist4 = new ByteBitHist(digits);
        final org.tools4j.nobark.histogram.Histogram hist2 = new LongCountsHistogram(digits);
        final org.tools4j.nobark.histogram.Histogram hist3 = new IntCountsHistogram(digits);
        final org.tools4j.nobark.histogram.Histogram hist4 = new VarCountsHistogram(digits);
        final org.tools4j.nobark.histogram.Histogram hist5 = new VarCountsHistogram(digits).preAllocateUpTo(Long.MAX_VALUE);
//        final org.tools4j.nobark.histogram.Histogram hist5 = new IntCountByteBitHist(digits);
        final org.tools4j.nobark.histogram.Histogram hist6 = new VarCountByteBitHist(digits);
        final org.tools4j.nobark.histogram.Histogram hist7 = new VarCountBitHist(digits);
        final long[] vals = new long[n];
        for (int i = 0; i < n; i++) {
//            final double pow10 = 8 - (int)Math.log10(1 + rand.nextInt(999999999));
//            final long value = rand.nextInt((int)(10 * Math.pow(10, pow10)));
            final double pow10 = 17 - (int)Math.log10(1 + rand.nextLong(999999999999999999L));
            final long value = rand.nextLong((long)(10 * Math.pow(10, pow10)));
            hist0.recordValue(value);
            hist1.recordValue(value);
            hist2.recorder().record(testMax && i == 0 ? Long.MAX_VALUE : value);
            hist3.recorder().record(testMax && i == 0 ? Long.MAX_VALUE : value);
            hist4.recorder().record(testMax && i == 0 ? Long.MAX_VALUE : value);
            hist5.recorder().record(testMax && i == 0 ? Long.MAX_VALUE : value);
            hist6.recorder().record(testMax && i == 0 ? Long.MAX_VALUE : value);
            hist7.recorder().record(testMax && i == 0 ? Long.MAX_VALUE : value);
            vals[i] = testMax && i == 0 ? Long.MAX_VALUE : value;
        }
        Arrays.sort(vals);

        System.out.printf("count=%d / %d / %d / %d / %d / %d / %d / %d\n", hist0.getTotalCount(), hist1.getTotalCount(),
                hist2.reporter().count(), hist3.reporter().count(), hist4.reporter().count(), hist5.reporter().count(),
                hist6.reporter().count(), hist7.reporter().count());
        for (final double p : new double[] {0.5, 0.9, 0.99, 0.999, 0.9999, 0.99999, 0.999999, 0.9999999}) {
            if (eq(hist0.getValueAtPercentile(p * 100),
                    hist2.reporter().valueAtPercentile(p),
                    hist3.reporter().valueAtPercentile(p),
                    hist4.reporter().valueAtPercentile(p),
                    hist5.reporter().valueAtPercentile(p),
                    hist6.reporter().valueAtPercentile(p),
                    hist7.reporter().valueAtPercentile(p)) ||
            valueAtPercentile(n, p, vals) < hist2.reporter().valueAtPercentile(p)) {
                System.out.printf("%s=%d / %d / %d / %d / %d / %d / %d / %d / %d\n",
                        p,
                        hist0.getValueAtPercentile(p * 100),
                        hist1.getValueAtPercentile(p * 100),
                        hist2.reporter().valueAtPercentile(p),
                        hist3.reporter().valueAtPercentile(p),
                        hist4.reporter().valueAtPercentile(p),
                        hist5.reporter().valueAtPercentile(p),
                        hist6.reporter().valueAtPercentile(p),
                        hist7.reporter().valueAtPercentile(p),
                        valueAtPercentile(n, p, vals));
            } else {
                System.err.printf("%s=%d / %d / %d / %d / %d / %d / %d / %d / %d\n",
                        p,
                        hist0.getValueAtPercentile(p * 100),
                        hist1.getValueAtPercentile(p * 100),
                        hist2.reporter().valueAtPercentile(p),
                        hist3.reporter().valueAtPercentile(p),
                        hist4.reporter().valueAtPercentile(p),
                        hist5.reporter().valueAtPercentile(p),
                        hist6.reporter().valueAtPercentile(p),
                        hist7.reporter().valueAtPercentile(p),
                        valueAtPercentile(n, p, vals));
            }
        }
        printCountSize(hist0);
        printCountSize(hist1);
        printCountSize(hist2);
        printCountSize(hist3);
        printCountSize(hist4);
        printCountSize(hist5);
        printCountSize(hist6);
        printCountSize(hist7);
        System.out.println();
    }

    public interface HistApi extends org.tools4j.nobark.histogram.Histogram, Recorder, Reporter {
        @Override
        default Recorder recorder() {
            return this;
        }

        @Override
        default Reporter reporter() {
            return this;
        }

        @Override
        default org.tools4j.nobark.histogram.Histogram preAllocateUpTo(long value) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        default long min() {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        default long max() {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        default void reset() {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        default void clear() {
            throw new UnsupportedOperationException("not implemented");
        }
    }

    public static class Hist implements HistApi {

        private final int significantBits;
        private final int bucketLength;
        private final long[][] counts;

        private long count;

        public Hist(final int digits) {
            this.significantBits = 1 + (int) Math.ceil(Math.log(Math.pow(10, digits))/Math.log(2));
            this.bucketLength = 1 << significantBits;
            this.counts = new long[63 - significantBits][];
        }

        @Override
        public long count() {
            return count;
        }

        @Override
        public void record(final long value) {
            if (value < 0) {
                throw new IllegalArgumentException("Value cannot be negative: " + value);
            }
            final int bits = 64 - Long.numberOfLeadingZeros(value);
            final int shift = bits <= significantBits ? 0 : bits - significantBits;
            final int offset = (int)(value >>> shift) - (bits <= significantBits ? 0 : bucketLength / 2);
            long[] c = counts[shift];
            if (c == null) {
                c = counts[shift] = new long[shift == 0 ? bucketLength : bucketLength / 2];
            }
            c[offset]++;
            count++;
        }

        @Override
        public long valueAtPercentile(final double percentile) {
            final double requestedPercentile =
                    Math.min(Math.max(Math.nextAfter(percentile, Double.NEGATIVE_INFINITY), 0.0D), 1.0);
            final long countAtPercentile = Math.max(1, (long)(Math.ceil((requestedPercentile * count))));

            int shift = 0;
            long totalToCurrentIndex = 0;
            for (final long[] c : counts) {
                if (c != null) {
                    for (int i = 0; i < c.length; i++) {
                        totalToCurrentIndex += c[i];
                        if (totalToCurrentIndex >= countAtPercentile) {
                            final int offset = shift == 0 ? 0 : bucketLength / 2;
                            return ((1L + offset+ i) << shift) - 1;
                        }
                    }
                }
                shift++;
            }
            return 0;
        }
    }

    public static class BitHist implements HistApi {

        private final int significantBits;
        private final int bucketLength;
        private final long[][] ones;
        private final long[][][] counts;

        private long count;

        public BitHist(final int digits) {
            this.significantBits = 1 + (int) Math.ceil(Math.log(Math.pow(10, digits))/Math.log(2));
            this.bucketLength = 1 << significantBits;
            this.ones = new long[63 - significantBits][];
            this.counts = new long[63 - significantBits][][];
        }

        @Override
        public long count() {
            return count;
        }

        @Override
        public void record(final long value) {
            if (value < 0) {
                throw new IllegalArgumentException("Value cannot be negative: " + value);
            }
            final int bits = 64 - Long.numberOfLeadingZeros(value);
            final int shift = bits <= significantBits ? 0 : bits - significantBits;
            final int offset = (int)(value >>> shift) - (bits <= significantBits ? 0 : bucketLength / 2);
            final int bucketLength = shift == 0 ? this.bucketLength : this.bucketLength / 2;
            long[] o = ones[shift];
            if (o == null) {
                o = ones[shift] = new long[onesLength(bucketLength, 64)];
            }
            if (testOneBit(o, offset)) {
                long[][] c = counts[shift];
                if (c == null) {
                    c = counts[shift] = new long[onesLength(bucketLength, 64)][];
                }
                long[] cc = c[offset / 64];
                if (cc == null) {
                    cc = c[offset / 64] = new long[64];
                }
                cc[offset % 64]++;
            } else {
                setOneBit(o, offset);
            }
            count++;
        }

        @Override
        public long valueAtPercentile(final double percentile) {
            final double requestedPercentile =
                    Math.min(Math.max(Math.nextAfter(percentile, Double.NEGATIVE_INFINITY), 0.0D), 1.0);
            final long countAtPercentile = Math.max(1, (long)(Math.ceil((requestedPercentile * count))));

            int shift = 0;
            long totalToCurrentIndex = 0;
            for (int i = 0; i < ones.length; i++) {
                final long[] o = ones[i];
                if (o != null) {
                    for (int j = 0; j < o.length; j++) {
                        if (o[j] != 0) {
                            for (int k = 0; k < 64; k++) {
                                final int index = j * 64 + k;
                                if (testOneBit(o, index)) {
                                    totalToCurrentIndex++;
                                    final long[][] c = counts[i];
                                    if (c != null) {
                                        final long[] cc = c[j];
                                        totalToCurrentIndex += (cc == null ? 0 : cc[k]);
                                    }
                                    if (totalToCurrentIndex >= countAtPercentile) {
                                        final int offset = shift == 0 ? 0 : bucketLength / 2;
                                        return ((1L + offset + index) << shift) - 1;
                                    }
                                }
                            }
                        }
                    }
                }
                shift++;
            }
            return 0;
        }
    }


    public static class ByteBitHist implements HistApi {

        private final int significantBits;
        private final int bucketLength;
        private final byte[][] ones;
        private final long[][][] counts;

        private long count;

        public ByteBitHist(final int digits) {
            this.significantBits = 1 + (int) Math.ceil(Math.log(Math.pow(10, digits))/Math.log(2));
            this.bucketLength = 1 << significantBits;
            this.ones = new byte[63 - significantBits][];
            this.counts = new long[63 - significantBits][][];
        }

        @Override
        public long count() {
            return count;
        }

        @Override
        public void record(final long value) {
            if (value < 0) {
                throw new IllegalArgumentException("Value cannot be negative: " + value);
            }
            final int bits = 64 - Long.numberOfLeadingZeros(value);
            final int shift = bits <= significantBits ? 0 : bits - significantBits;
            final int offset = (int)(value >>> shift) - (bits <= significantBits ? 0 : bucketLength / 2);
            final int bucketLength = shift == 0 ? this.bucketLength : this.bucketLength / 2;
            byte[] o = ones[shift];
            if (o == null) {
                o = ones[shift] = new byte[onesLength(bucketLength, 8)];
            }
            if (testOneBit(o, offset)) {
                long[][] c = counts[shift];
                if (c == null) {
                    c = counts[shift] = new long[onesLength(bucketLength, 8)][];
                }
                long[] cc = c[offset / 8];
                if (cc == null) {
                    cc = c[offset / 8] = new long[8];
                }
                cc[offset % 8]++;
            } else {
                setOneBit(o, offset);
            }
            count++;
        }

        @Override
        public long valueAtPercentile(final double percentile) {
            final double requestedPercentile =
                    Math.min(Math.max(Math.nextAfter(percentile, Double.NEGATIVE_INFINITY), 0.0D), 1.0);
            final long countAtPercentile = Math.max(1, (long)(Math.ceil((requestedPercentile * count))));

            int shift = 0;
            long totalToCurrentIndex = 0;
            for (int i = 0; i < ones.length; i++) {
                final byte[] o = ones[i];
                if (o != null) {
                    for (int j = 0; j < o.length; j++) {
                        if (o[j] != 0) {
                            for (int k = 0; k < 8; k++) {
                                final int index = j * 8 + k;
                                if (testOneBit(o, index)) {
                                    totalToCurrentIndex++;
                                    final long[][] c = counts[i];
                                    if (c != null) {
                                        final long[] cc = c[j];
                                        totalToCurrentIndex += (cc == null ? 0 :cc[k]);
                                    }
                                    if (totalToCurrentIndex >= countAtPercentile) {
                                        final int offset = shift == 0 ? 0 : bucketLength / 2;
                                        return ((1L + offset + index) << shift) - 1;
                                    }
                                }
                            }
                        }
                    }
                }
                shift++;
            }
            return 0;
        }
    }

    public static class IntCountByteBitHist implements HistApi {

        private final int significantBits;
        private final int bucketLength;
        private final byte[][] ones;
        private final int[][][] counts;

        private long count;

        public IntCountByteBitHist(final int digits) {
            this.significantBits = 1 + (int) Math.ceil(Math.log(Math.pow(10, digits))/Math.log(2));
            this.bucketLength = 1 << significantBits;
            this.ones = new byte[64 - significantBits][];
            this.counts = new int[64 - significantBits][][];
        }

        @Override
        public long count() {
            return count;
        }

        @Override
        public void record(final long value) {
            if (value < 0) {
                throw new IllegalArgumentException("Value cannot be negative: " + value);
            }
            final int bits = 64 - Long.numberOfLeadingZeros(value);
            final int shift = bits <= significantBits ? 0 : bits - significantBits;
            final int offset = (int)(value >>> shift) - (bits <= significantBits ? 0 : bucketLength / 2);
            final int bucketLength = shift == 0 ? this.bucketLength : this.bucketLength / 2;
            byte[] o = ones[shift];
            if (o == null) {
                o = ones[shift] = new byte[onesLength(bucketLength, 8)];
            }
            if (testOneBit(o, offset)) {
                int[][] c = counts[shift];
                if (c == null) {
                    c = counts[shift] = new int[onesLength(bucketLength, 8)][];
                }
                int[] cc = c[offset / 8];
                if (cc == null) {
                    cc = c[offset / 8] = new int[8];
                }
                cc[offset % 8]++;
            } else {
                setOneBit(o, offset);
            }
            count++;
        }

        @Override
        public long valueAtPercentile(final double percentile) {
            final double requestedPercentile =
                    Math.min(Math.max(Math.nextAfter(percentile, Double.NEGATIVE_INFINITY), 0.0D), 1.0);
            final long countAtPercentile = Math.max(1, (long)(Math.ceil((requestedPercentile * count))));

            int shift = 0;
            long totalToCurrentIndex = 0;
            for (int i = 0; i < ones.length; i++) {
                final byte[] o = ones[i];
                if (o != null) {
                    for (int j = 0; j < o.length; j++) {
                        if (o[j] != 0) {
                            for (int k = 0; k < 8; k++) {
                                final int index = j * 8 + k;
                                if (testOneBit(o, index)) {
                                    totalToCurrentIndex++;
                                    final int[][] c = counts[i];
                                    if (c != null) {
                                        final int[] cc = c[j];
                                        totalToCurrentIndex += (cc == null ? 0 :cc[k]);
                                    }
                                    if (totalToCurrentIndex >= countAtPercentile) {
                                        final int offset = shift == 0 ? 0 : bucketLength / 2;
                                        return ((1L + offset + index) << shift) - 1;
                                    }
                                }
                            }
                        }
                    }
                }
                shift++;
            }
            return 0;
        }
    }

    public static class VarCountByteBitHist implements HistApi {

        private final int significantBits;
        private final int bucketLength;
        private final byte[][] ones;
        private final byte[][][] counts;
        private final long[][][] bigCounts;

        private long count;

        public VarCountByteBitHist(final int digits) {
            this.significantBits = 1 + (int) Math.ceil(Math.log(Math.pow(10, digits))/Math.log(2));
            this.bucketLength = 1 << significantBits;
            this.ones = new byte[64 - significantBits][];
            this.counts = new byte[64 - significantBits][][];
            this.bigCounts = new long[64 - significantBits][][];
        }

        @Override
        public long count() {
            return count;
        }

        @Override
        public void record(final long value) {
            if (value < 0) {
                throw new IllegalArgumentException("Value cannot be negative: " + value);
            }
            final int bits = 64 - Long.numberOfLeadingZeros(value);
            final int shift = bits <= significantBits ? 0 : bits - significantBits;
            final int offset = (int)(value >>> shift) - (bits <= significantBits ? 0 : bucketLength / 2);
            final int bucketLength = shift == 0 ? this.bucketLength : this.bucketLength / 2;
            byte[] o = ones[shift];
            if (o == null) {
                o = ones[shift] = new byte[onesLength(bucketLength, 8)];
            }
            if (testOneBit(o, offset)) {
                byte[][] c = counts[shift];
                if (c == null) {
                    c = counts[shift] = new byte[onesLength(bucketLength, 8)][];
                }
                byte[] cc = c[offset / 8];
                if (cc == null) {
                    cc = c[offset / 8] = new byte[8];
                }
                if ((++cc[offset % 8]) == -1) {
                    --cc[offset % 8];
                    long[][] bc = bigCounts[shift];
                    if (bc == null) {
                        bc = bigCounts[shift] = new long[onesLength(bucketLength, 8)][];
                    }
                    long[] bcc = bc[offset / 8];
                    if (bcc == null) {
                        bcc = bc[offset / 8] = new long[8];
                    }
                    bcc[offset % 8]++;
                }
            } else {
                setOneBit(o, offset);
            }
            count++;
        }

        @Override
        public long valueAtPercentile(final double percentile) {
            final double requestedPercentile =
                    Math.min(Math.max(Math.nextAfter(percentile, Double.NEGATIVE_INFINITY), 0.0D), 1.0);
            final long countAtPercentile = Math.max(1, (long)(Math.ceil((requestedPercentile * count))));

            int shift = 0;
            long totalToCurrentIndex = 0;
            for (int i = 0; i < ones.length; i++) {
                final byte[] o = ones[i];
                if (o != null) {
                    for (int j = 0; j < o.length; j++) {
                        if (o[j] != 0) {
                            for (int k = 0; k < 8; k++) {
                                final int index = j * 8 + k;
                                if (testOneBit(o, index)) {
                                    totalToCurrentIndex++;
                                    final byte[][] c = counts[i];
                                    if (c != null) {
                                        final byte[] cc = c[j];
                                        final int cnt = cc == null ? 0 : (0xff & cc[k]);
                                        totalToCurrentIndex += cnt;
                                        if (cnt == 0xfe) {
                                            totalToCurrentIndex += bigCounts[i][j][k];
                                        }
                                    }
                                    if (totalToCurrentIndex >= countAtPercentile) {
                                        final int offset = shift == 0 ? 0 : bucketLength / 2;
                                        return ((1L + offset + index) << shift) - 1;
                                    }
                                }
                            }
                        }
                    }
                }
                shift++;
            }
            return 0;
        }
    }

    private static long valueAtPercentile(final int n, final double p, final long[] sorted) {
        final double requestedPercentile =
                Math.min(Math.max(Math.nextAfter(p, Double.NEGATIVE_INFINITY), 0.0D), 1.0);
        final long countAtPercentile = Math.max(1, (long)(Math.ceil(requestedPercentile * n)));

        return sorted[(int)countAtPercentile - 1];
    }

    public static class VarCountBitHist implements HistApi {

        private final int significantBits;
        private final int bucketLength;
        private final long[][] ones;
        private final byte[][][] counts;
        private final long[][][] bigCounts;

        private long count;

        public VarCountBitHist(final int digits) {
            this.significantBits = 1 + (int) Math.ceil(Math.log(Math.pow(10, digits))/Math.log(2));
            this.bucketLength = 1 << significantBits;
            this.ones = new long[64 - significantBits][];
            this.counts = new byte[64 - significantBits][][];
            this.bigCounts = new long[64 - significantBits][][];
        }

        @Override
        public long count() {
            return count;
        }

        @Override
        public void record(final long value) {
            if (value < 0) {
                throw new IllegalArgumentException("Value cannot be negative: " + value);
            }
            final int bits = 64 - Long.numberOfLeadingZeros(value);
            final int shift = bits <= significantBits ? 0 : bits - significantBits;
            final int offset = (int)(value >>> shift) - (bits <= significantBits ? 0 : bucketLength / 2);
            final int bucketLength = shift == 0 ? this.bucketLength : this.bucketLength / 2;
            long[] o = ones[shift];
            if (o == null) {
                o = ones[shift] = new long[onesLength(bucketLength, 64)];
            }
            if (testOneBit(o, offset)) {
                byte[][] c = counts[shift];
                if (c == null) {
                    c = counts[shift] = new byte[onesLength(bucketLength, 64)][];
                }
                byte[] cc = c[offset / 64];
                if (cc == null) {
                    cc = c[offset / 64] = new byte[64];
                }
                if ((++cc[offset % 64]) == -1) {
                    --cc[offset % 64];
                    long[][] bc = bigCounts[shift];
                    if (bc == null) {
                        bc = bigCounts[shift] = new long[onesLength(bucketLength, 64)][];
                    }
                    long[] bcc = bc[offset / 64];
                    if (bcc == null) {
                        bcc = bc[offset / 64] = new long[64];
                    }
                    bcc[offset % 64]++;
                }
            } else {
                setOneBit(o, offset);
            }
            count++;
        }

        @Override
        public long valueAtPercentile(final double percentile) {
            final double requestedPercentile =
                    Math.min(Math.max(Math.nextAfter(percentile, Double.NEGATIVE_INFINITY), 0.0D), 1.0);
            final long countAtPercentile = Math.max(1, (long)(Math.ceil((requestedPercentile * count))));

            int shift = 0;
            long totalToCurrentIndex = 0;
            for (int i = 0; i < ones.length; i++) {
                final long[] o = ones[i];
                if (o != null) {
                    for (int j = 0; j < o.length; j++) {
                        if (o[j] != 0) {
                            for (int k = 0; k < 64; k++) {
                                final int index = j * 64 + k;
                                if (testOneBit(o, index)) {
                                    totalToCurrentIndex++;
                                    final byte[][] c = counts[i];
                                    if (c != null) {
                                        final byte[] cc = c[j];
                                        final int cnt = cc == null ? 0 : (0xff & cc[k]);
                                        totalToCurrentIndex += cnt;
                                        if (cnt == 0xfe) {
                                            totalToCurrentIndex += bigCounts[i][j][k];
                                        }
                                    }
                                    if (totalToCurrentIndex >= countAtPercentile) {
                                        final int offset = shift == 0 ? 0 : bucketLength / 2;
                                        return ((1L + offset + index) << shift) - 1;
                                    }
                                }
                            }
                        }
                    }
                }
                shift++;
            }
            return 0;
        }
    }

    private static boolean eq(final long... values) {
        if (values.length > 1) {
            final long ref = values[0];
            for (int i = 1; i < values.length; i++) {
                if (values[i] != ref) {
                    return false;
                }
            }
        }
        return true;
    }

    private static <T> T getFieldValue(final Class<?> declaringClass,
                                       final Class<T> fieldType,
                                       final String fieldName,
                                       final Object instance) {
        try {
            final Field field = declaringClass.getDeclaredField(fieldName);
            field.setAccessible(true);
            final Object value = field.get(instance);
            if (fieldType.isPrimitive()) {
                @SuppressWarnings("unchecked")
                final T boxed = (T)value;
                return boxed;
            }
            return fieldType.cast(value);
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static int onesLength(final int length, final int max) {
        return (length + max - 1) / max;
    }

    private static boolean testOneBit(final long[] ones, final int index) {
        final int arrayIndex = index / 64;
        final int arrayBit = index % 64;
        final long mask = 1L << arrayBit;
        return 0 != (mask & ones[arrayIndex]);
    }

    private static boolean testOneBit(final byte[] ones, final int index) {
        final int arrayIndex = index / 8;
        final int arrayBit = index % 8;
        final int mask = 1 << arrayBit;
        return 0 != (mask & ones[arrayIndex]);
    }

    private static void setOneBit(final long[] ones, final int index) {
        final int arrayIndex = index / 64;
        final int arrayBit = index % 64;
        final long mask = 1L << arrayBit;
        ones[arrayIndex] |= mask;
    }

    private static void setOneBit(final byte[] ones, final int index) {
        final int arrayIndex = index / 8;
        final int arrayBit = index % 8;
        final int mask = 1 << arrayBit;
        ones[arrayIndex] |= mask;
    }

    private static int bytes(final Object o) {
        try {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            new ObjectOutputStream(out).writeObject(o);
            return out.toByteArray().length;
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

}
