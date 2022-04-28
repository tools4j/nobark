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

/**
 * Histogram implementations allow for efficient frequency tracking of non-negative long values.  The API separates
 * write access for {@link #recorder() recording} of values from read access to {@link #reporter() report} the captured
 * values.
 */
public interface Histogram {
    /**
     * Pre-allocates structures to capture values up to (and inclusive of) the specified value.  This method can be used
     * to minimize allocations on the hot recording path (some implementations guarantee zero allocations for future
     * recorded values up the given threshold).
     *
     * @param value the value up to which future record calls will have minimal allocations
     * @return this histogram
     */
    Histogram preAllocateUpTo(long value);

    /**
     * Returns the {@link Recorder} API for recording of values.
     * @return recorder to track values
     */
    Recorder recorder();

    /**
     * Returns the {@link Reporter} API for reporting of captured values.
     * @return reporter to output captured values
     */
    Reporter reporter();

    /**
     * Recorder API to record values, or to reset the histogram.
     */
    interface Recorder {
        /**
         * Resets the histogram bringing all counts to zero without freeing dynamically allocated structures.
         */
        void reset();
        /**
         * Clears the histogram bringing all counts to zero and freeing all dynamically allocated structures.  After
         * calling this method, the histogram is in the exact same state as immediately after instantiation.
         */
        void clear();
        /**
         * Records the given value.  Determines the bucket for the given value, and increments the bucket count by one.
         * Depending on the chosen precision for this histogram, and on the magnitude of the value, the bucket
         * represents only that value, or multiple values in its proximity.
         *
         * @param value the value to capture
         * @throws IllegalArgumentException if the value is negative
         */
        void record(long value);
    }

    /**
     * Reporter API to output captured values
     */
    interface Reporter {
        /**
         * Returns the number of values captured.
         * @return the count of values captured in the histogram
         */
        long count();
        /**
         * Returns the smallest captured value, or zero if {@link #count() count} is zero.
         * @return the smallest captured value
         */
        long min();
        /**
         * Returns the largest captured value, or zero if {@link #count() count} is zero.
         * @return the largest captured value
         */
        long max();

        /**
         * Returns the value at a given percentile.  The returned value is the largest value that
         * (1.0 - percentile) [+/- 1 ulp] of the overall recorded value entries in the histogram are either larger than
         * or equivalent to. Returns zero if {@link #count() count} is zero.
         *
         * @param percentile the percentile (0.0 to 1.0) for which to return the associated value
         * @return  the largest value that (1.0 - percentile) [+/- 1 ulp] of the overall recorded value entries in the
         *          histogram are either larger than or equivalent to, or 0 if no recorded values exist
         */
        long valueAtPercentile(double percentile);
    }
}
