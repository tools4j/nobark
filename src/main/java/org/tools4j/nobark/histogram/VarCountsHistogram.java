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
 * Counts are stored in buckets in a variable way to optimise memory usage, at a minimal expense of performance and with
 * occasional need to dynamically allocate more storage when necessary.
 * <p>
 * This class is not thread safe.
 */
public class VarCountsHistogram implements Histogram {

    private static final int DIV_2_SHIFT    = 1;    //(a /  2) == (a >>> DIV_2_SHIFT)
    private static final int DIV_64_SHIFT   = 6;    //(a / 64) == (a >>> DIV_64_SHIFT)
    private static final int MOD_64_MASK    = 0x3f; //(a % 64) == (a & MOD_64_MASK)

    private final int significantBits;
    private final int bucketLength;
    private final long[][] bitCounts;
    private final byte[][][] byteCounts;
    private final long[][][] longCounts;

    private long min;
    private long max;
    private long count;

    private final Recorder recorder = new VarCountsRecorder();
    private final Reporter reporter = new VarCountsReporter();

    /**
     * Constructor for histogram with precision digits for values captured by the histogram.
     *
     * @param digits the number of decimal precision digits for captured values
     */
    public VarCountsHistogram(final int digits) {
        this.significantBits = 1 + (int) Math.ceil(Math.log(Math.pow(10, digits))/Math.log(2));
        this.bucketLength = 1 << significantBits;
        this.bitCounts = new long[64 - significantBits][];
        this.byteCounts = new byte[64 - significantBits][][];
        this.longCounts = new long[64 - significantBits][][];
    }

    /**
     * Pre-allocates structures to capture values up to (and inclusive of) the specified value.  This method can be used
     * to minimize allocations on the hot recording path (no more than 2 allocations, a byte and long array of length 64
     * each).
     *
     * @param value the value up to which future record calls will have minimal allocations
     * @return this histogram
     */
    @Override
    public VarCountsHistogram preAllocateUpTo(final long value) {
        if (value < 0) {
            return this;
        }
        for (int bucket = 0; bucket < bitCounts.length; bucket++) {
            final int length = ((bucket == 0 ? bucketLength : (bucketLength >>> DIV_2_SHIFT)) + 63) >>> DIV_64_SHIFT;
            final long[] bitC = bitCounts[bucket];
            if (bitC == null) {
                bitCounts[bucket] = new long[length];
            }
            final byte[][] byteC = byteCounts[bucket];
            if (byteC == null) {
                byteCounts[bucket] = new byte[length][];
            }
            final long[][] longC = longCounts[bucket];
            if (longC == null) {
                longCounts[bucket] = new long[length][];
            }
            final int maxIndex = (length << DIV_64_SHIFT) - 1;
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

    private final class VarCountsRecorder implements Recorder {
        @Override
        public void reset() {
            for (final long[] c : bitCounts) {
                if (c != null) {
                    Arrays.fill(c, 0);
                }
            }
            for (final byte[][] c : byteCounts) {
                if (c != null) {
                    for (final byte[] cc : c) {
                        if (cc != null) {
                            Arrays.fill(cc, (byte)0);
                        }
                    }
                }
            }
            for (final long[][] c : longCounts) {
                if (c != null) {
                    for (final long[] cc : c) {
                        if (cc != null) {
                            Arrays.fill(cc, 0);
                        }
                    }
                }
            }
            count = 0;
            min = 0;
            max = 0;
        }

        @Override
        public void clear() {
            Arrays.fill(bitCounts, null);
            Arrays.fill(byteCounts, null);
            Arrays.fill(longCounts, null);
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
            final int length = ((bucket == 0 ? bucketLength : (bucketLength >>> DIV_2_SHIFT)) + 63) >>> DIV_64_SHIFT;
            long[] bitC = bitCounts[bucket];
            if (bitC == null) {
                bitC = bitCounts[bucket] = new long[length];
            }
            if (testOneBit(bitC, offset)) {
                byte[][] byteC = byteCounts[bucket];
                if (byteC == null) {
                    byteC = byteCounts[bucket] = new byte[length][];
                }
                byte[] byteCC = byteC[offset >>> DIV_64_SHIFT];
                if (byteCC == null) {
                    byteCC = byteC[offset >>> DIV_64_SHIFT] = new byte[64];
                }
                if ((++byteCC[offset & MOD_64_MASK]) == 0) {
                    --byteCC[offset & MOD_64_MASK];
                    long[][] longC = longCounts[bucket];
                    if (longC == null) {
                        longC = longCounts[bucket] = new long[length][];
                    }
                    long[] longCC = longC[offset >>> DIV_64_SHIFT];
                    if (longCC == null) {
                        longCC = longC[offset >>> DIV_64_SHIFT] = new long[64];
                    }
                    longCC[offset & MOD_64_MASK]++;
                }
            } else {
                setOneBit(bitC, offset);
            }
            min = Math.min(min, value);
            max = Math.max(max, value);
            count++;
        }
    }

    private final class VarCountsReporter implements Reporter {
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

            int bucket = 0;
            long totalToCurrentIndex = 0;
            for (int i = 0; i < bitCounts.length; i++) {
                final long[] bitC = bitCounts[i];
                if (bitC != null) {
                    for (int j = 0; j < bitC.length; j++) {
                        if (bitC[j] != 0) {
                            for (int k = 0; k < 64; k++) {
                                final int index = (j << DIV_64_SHIFT) + k;
                                if (testOneBit(bitC, index)) {
                                    totalToCurrentIndex++;
                                    final byte[][] byteC = byteCounts[i];
                                    if (byteC != null) {
                                        final byte[] byteCC = byteC[j];
                                        final int cnt = byteCC == null ? 0 : (0xff & byteCC[k]);
                                        totalToCurrentIndex += cnt;
                                        if (cnt == 0xff) {
                                            final long[][] longC = longCounts[i];
                                            if (longC != null) {
                                                final long[] longCC = longC[j];
                                                totalToCurrentIndex += longCC == null ? 0 : longCC[k];
                                            }
                                        }
                                    }
                                    if (totalToCurrentIndex >= countAtPercentile) {
                                        return valueAt(bucket, index);
                                    }
                                }
                            }
                        }
                    }
                }
                bucket++;
            }
            return 0;
        }
    }

    private long valueAt(final int bucket, final int index) {
        final int offset = bucket == 0 ? 0 : (bucketLength >>> DIV_2_SHIFT);
        return ((1L + offset + index) << bucket) - 1;
    }

    private static boolean testOneBit(final long[] ones, final int index) {
        final int arrayIndex = index >>> DIV_64_SHIFT;
        final int arrayBit = index & MOD_64_MASK;
        final long mask = 1L << arrayBit;
        return 0 != (mask & ones[arrayIndex]);
    }

    private static void setOneBit(final long[] ones, final int index) {
        final int arrayIndex = index >>> DIV_64_SHIFT;
        final int arrayBit = index & MOD_64_MASK;
        final long mask = 1L << arrayBit;
        ones[arrayIndex] |= mask;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final VarCountsHistogram that = (VarCountsHistogram) o;

        if (significantBits != that.significantBits) return false;
        if (bucketLength != that.bucketLength) return false;
        if (count != that.count) return false;
        if (!Arrays.deepEquals(bitCounts, that.bitCounts)) return false;
        if (!Arrays.deepEquals(byteCounts, that.byteCounts)) return false;
        return Arrays.deepEquals(longCounts, that.longCounts);
    }

    @Override
    public int hashCode() {
        int result = significantBits;
        result = 31 * result + bucketLength;
        result = 31 * result + (int) (count ^ (count >>> 32));
        result = 31 * result + Arrays.deepHashCode(bitCounts);
        result = 31 * result + Arrays.deepHashCode(byteCounts);
        result = 31 * result + Arrays.deepHashCode(longCounts);
        return result;
    }

    @Override
    public String toString() {
        return "VarCountsHistogram{" +
                "significantBits=" + significantBits +
                ", min=" + min +
                ", max=" + max +
                ", count=" + count +
                '}';
    }
}
