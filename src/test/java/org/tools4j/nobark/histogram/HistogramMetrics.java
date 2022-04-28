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
package org.tools4j.nobark.histogram;

import org.HdrHistogram.Histogram;
import org.HdrHistogram.PackedHistogram;
import org.HdrHistogram.packedarray.PackedLongArray;
import org.tools4j.nobark.histogram.HistogramExperiments.BitHist;
import org.tools4j.nobark.histogram.HistogramExperiments.ByteBitHist;
import org.tools4j.nobark.histogram.HistogramExperiments.IntCountByteBitHist;
import org.tools4j.nobark.histogram.HistogramExperiments.VarCountByteBitHist;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.util.Arrays;

enum HistogramMetrics {
    ;

    static void printHistogramMetrics(final long[] longs) {
        System.out.printf("long[]: length=%d, bytes=%d\n", longs.length, bytes(longs));
    }

    static void printHistogramMetrics(final Histogram hist) {
        if (hist instanceof PackedHistogram) {
            final PackedHistogram pHist = (PackedHistogram) hist;
            final long[] counts = getFieldValue(org.HdrHistogram.Histogram.class, long[].class, "counts", pHist);
            assert counts == null;
            final PackedLongArray packedLongArray = getFieldValue(PackedHistogram.class, PackedLongArray.class, "packedCounts", pHist);
            final Object arrayContext = getFieldValue(packedLongArray.getClass().getSuperclass(), Object.class, "arrayContext", packedLongArray);
            final long[] array = getFieldValue(arrayContext.getClass(), long[].class, "array", arrayContext);
            System.out.printf("%s: precision=%d, max=%d, array.length=%d, bytes=%d\n",
                    pHist.getClass().getSimpleName(),
                    pHist.getNumberOfSignificantValueDigits(), pHist.getHighestTrackableValue(),
                    array.length,
                    bytes(array));
        } else {
            final long[] counts = getFieldValue(Histogram.class, long[].class, "counts", hist);
            System.out.printf("%s: precision=%d, max=%d, counts.length=%d, bytes=%d\n",
                    hist.getClass().getSimpleName(),
                    hist.getNumberOfSignificantValueDigits(), hist.getHighestTrackableValue(),
                    counts.length,
                    bytes(counts));
        }
    }

    static void printHistogramMetrics(final org.tools4j.nobark.histogram.Histogram obj) {
        if (obj instanceof LongCountsHistogram) {
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
        } else {
            throw new IllegalArgumentException("Unsupported: " + obj);
        }
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
