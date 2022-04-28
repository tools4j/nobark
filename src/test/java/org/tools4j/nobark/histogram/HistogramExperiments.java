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

import org.tools4j.nobark.histogram.Histogram.Recorder;
import org.tools4j.nobark.histogram.Histogram.Reporter;

/**
 * <pre>
     NOTE: - this example illustrates the workings of histogram buckets
           - the bucket index is equivalent with the right-shift of the value used to erase ignored digits
           - the value after shifting determines the counter position in the bucket
           - the first bucket is 2x the size of subsequent buckets (since half the n-bit values start with a zero bit)

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
enum HistogramExperiments {
    ;

    interface HistApi extends org.tools4j.nobark.histogram.Histogram, Recorder, Reporter {
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

    static class BitHist implements HistApi {

        final int significantBits;
        final int bucketLength;
        final long[][] ones;
        final long[][][] counts;

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


    static class ByteBitHist implements HistApi {

        final int significantBits;
        final int bucketLength;
        final byte[][] ones;
        final long[][][] counts;

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

    static class IntCountByteBitHist implements HistApi {

        final int significantBits;
        final int bucketLength;
        final byte[][] ones;
        final int[][][] counts;

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

    static class VarCountByteBitHist implements HistApi {

        final int significantBits;
        final int bucketLength;
        final byte[][] ones;
        final byte[][][] counts;
        final long[][][] bigCounts;

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

}
