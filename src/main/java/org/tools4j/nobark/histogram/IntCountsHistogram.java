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

import java.util.Arrays;

/**
 * Histogram implementation with configurable precision to efficiently track the frequency of non-negative long values.
 * Counts are stored in buckets as unsigned ints, meaning that the maximum count per bucket is 4,294,967,295.
 * <p>
 * For best recording performance, buckets can be {@link #preAllocateUpTo(long) pre-allocated}, otherwise they are
 * allocated when needed.
 * <p>
 * This class is not thread safe.
 */
public class IntCountsHistogram implements Histogram {

    private static final int DIV_2_SHIFT = 1;//(a / 2) == (a >>> DIV_2_SHIFT)

    private final int significantBits;
    private final int bucketLength;
    private final int[][] counts;

    private long min;
    private long max;
    private long count;

    private final Recorder recorder = new IntCountsRecorder();
    private final Reporter reporter = new IntCountsReporter();

    /**
     * Constructor for histogram with precision digits for values captured by the histogram.
     *
     * @param digits the number of decimal precision digits for captured values
     */
    public IntCountsHistogram(final int digits) {
        this.significantBits = 1 + (int) Math.ceil(Math.log(Math.pow(10, digits))/Math.log(2));
        this.bucketLength = 1 << significantBits;
        this.counts = new int[64 - significantBits][];
    }

    /**
     * Pre-allocates structures to capture values up to (and inclusive of) the specified value.  This method can be used
     * to keep allocations out of the hot recording path.
     *
     * @param value the value up to which future record calls will be guaranteed to be allocation free
     * @return this histogram
     */
    @Override
    public IntCountsHistogram preAllocateUpTo(final long value) {
        if (value < 0) {
            return this;
        }
        for (int bucket = 0; bucket < counts.length; bucket++) {
            int[] c = counts[bucket];
            if (c == null) {
                c = counts[bucket] = new int[bucket == 0 ? bucketLength : (bucketLength >>> DIV_2_SHIFT)];
            }
            final int maxIndex = c.length - 1;
            if (value <= valueAt(bucket, maxIndex)) {
                break;
            }
        }
        return this;
    }

    @Override
    public Recorder recorder() {
        return recorder;
    }

    @Override
    public Reporter reporter() {
        return reporter;
    }

    private final class IntCountsRecorder implements Recorder {
        @Override
        public void reset() {
            for (final int[] c : counts) {
                if (c != null) {
                    Arrays.fill(c, 0);
                }
            }
            count = 0;
            min = 0;
            max = 0;
        }

        @Override
        public void clear() {
            Arrays.fill(counts, null);
            count = 0;
            min = 0;
            max = 0;
        }

        @Override
        public void record(final long value) {
            if (value < 0) {
                throw new IllegalArgumentException("Value cannot be negative: " + value);
            }
            final int bits = 64 - Long.numberOfLeadingZeros(value);
            final int bucket = Math.max(0, bits - significantBits);
            final int offset = (int)(bucket == 0 ? value : (value >>> bucket) - (bucketLength >>> DIV_2_SHIFT));
            int[] c = counts[bucket];
            if (c == null) {
                c = counts[bucket] = new int[bucket == 0 ? bucketLength : (bucketLength >>> DIV_2_SHIFT)];
            }
            c[offset]++;
            min = Math.min(min, value);
            max = Math.max(max, value);
            count++;
        }
    }

    private final class IntCountsReporter implements Reporter {
        @Override
        public long count() {
            return count;
        }

        @Override
        public long min() {
            return min;
        }

        @Override
        public long max() {
            return max;
        }

        @Override
        public long valueAtPercentile(final double percentile) {
            final double requestedPercentile =
                    Math.min(Math.max(Math.nextAfter(percentile, Double.NEGATIVE_INFINITY), 0.0D), 1.0);
            final long countAtPercentile = Math.max(1, (long)(Math.ceil((requestedPercentile * count))));

            long totalToCurrentIndex = 0;
            for (int bucket = 0; bucket < counts.length; bucket++) {
                final int[] c;
                if ((c = counts[bucket]) != null) {
                    for (int index = 0; index < c.length; index++) {
                        totalToCurrentIndex += (0xffffffffL & c[index]);
                        if (totalToCurrentIndex >= countAtPercentile) {
                            return valueAt(bucket, index);
                        }
                    }
                }
            }
            return 0;
        }
    }

    private long valueAt(final int bucket, final int index) {
        final int offset = bucket == 0 ? 0 : (bucketLength >>> DIV_2_SHIFT);
        return ((1L + offset + index) << bucket) - 1;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final IntCountsHistogram that = (IntCountsHistogram) o;

        if (significantBits != that.significantBits) return false;
        if (bucketLength != that.bucketLength) return false;
        if (count != that.count) return false;
        return Arrays.deepEquals(counts, that.counts);
    }

    @Override
    public int hashCode() {
        int result = significantBits;
        result = 31 * result + bucketLength;
        result = 31 * result + (int) (count ^ (count >>> 32));
        result = 31 * result + Arrays.deepHashCode(counts);
        return result;
    }

    @Override
    public String toString() {
        return "IntCountsHistogram{" +
                "significantBits=" + significantBits +
                ", min=" + min +
                ", max=" + max +
                ", count=" + count +
                '}';
    }
}
