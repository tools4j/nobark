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

import org.HdrHistogram.PackedHistogram;
import org.junit.Test;
import org.tools4j.nobark.histogram.HistogramExperiments.IntCountByteBitHist;
import org.tools4j.nobark.histogram.HistogramExperiments.VarCountByteBitHist;

import java.util.Arrays;

import static org.tools4j.nobark.histogram.HistogramMetrics.printHistogramMetrics;

/**
 * Unit test for {@link org.tools4j.nobark.histogram.Histogram}, its implementations and some additional
 * {@link HistogramExperiments}
 */
public class HistogramTest {

    @Test
    public void testHistograms() {
        final int testRuns = 3;
        final int digits = 3;
//        final int n = 100_000;
        final int n = 1_000_000;
//        final int n = 10_000_000;
//        final int n = 100_000_000;
        for (int i = 0; i < testRuns; i++) {
            randomSampleHist(digits, n);
        }
    }

    private static void randomSampleHist(final int digits, final int n) {
        final boolean testMax = false;
        final LongRandom rand = new LongRandom();
        final org.HdrHistogram.Histogram hist0 = new org.HdrHistogram.Histogram(1L, Long.MAX_VALUE / 2, digits);
        final org.HdrHistogram.Histogram hist1 = new PackedHistogram(1L, Long.MAX_VALUE / 2, digits);
//        final org.tools4j.nobark.histogram.Histogram hist3 = new BitHist(digits);
//        final org.tools4j.nobark.histogram.Histogram hist4 = new ByteBitHist(digits);
        final Histogram hist2 = new LongCountsHistogram(digits).preAllocateUpTo(Long.MAX_VALUE);
        final Histogram hist3 = new IntCountsHistogram(digits);
        final Histogram hist4 = new VarCountsHistogram(digits);
        final Histogram hist5 = new VarCountsHistogram(digits).preAllocateUpTo(Long.MAX_VALUE);
        final Histogram hist6 = new IntCountByteBitHist(digits);
        final Histogram hist7 = new VarCountByteBitHist(digits);
        final long[] vals = new long[n];
        for (int i = 0; i < n; i++) {
//            final double pow10 = 8 - (int)Math.log10(1 + rand.nextInt(999999999));
//            final long value = rand.nextInt((int)(10 * Math.pow(10, pow10)));
            final double pow10 = 17 - (int) Math.log10(1 + rand.nextLong(999999999999999999L));
            final long value = rand.nextLong((long) (10 * Math.pow(10, pow10)));
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
        for (final double p : new double[]{0.5, 0.9, 0.99, 0.999, 0.9999, 0.99999, 0.999999, 0.9999999}) {
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

        printHistogramMetrics(hist0);
        printHistogramMetrics(hist1);
        printHistogramMetrics(hist2);
        printHistogramMetrics(hist3);
        printHistogramMetrics(hist4);
        printHistogramMetrics(hist5);
        printHistogramMetrics(hist6);
        printHistogramMetrics(hist7);
        printHistogramMetrics(vals);
        System.out.println();
    }

    private static long valueAtPercentile(final int n, final double p, final long[] sorted) {
        final double requestedPercentile =
                Math.min(Math.max(Math.nextAfter(p, Double.NEGATIVE_INFINITY), 0.0D), 1.0);
        final long countAtPercentile = Math.max(1, (long)(Math.ceil(requestedPercentile * n)));

        return sorted[(int)countAtPercentile - 1];
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

}
