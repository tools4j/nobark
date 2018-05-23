/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2018 nobark (tools4j), Marco Terzer, Anton Anufriev
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
package org.tools4j.nobark.queue;

import java.util.function.DoubleBinaryOperator;

class PriceEntry {

    public static class Merger implements org.tools4j.nobark.queue.Merger<Object, PriceEntry> {

        private final ThreadLocal<PriceEntry> unused = ThreadLocal.withInitial(PriceEntry::new);

        @Override
        public PriceEntry merge(final Object key, final PriceEntry olderValue, final PriceEntry newValue) {
            final PriceEntry merged = unused.get();
            merged.reset();
            merged.count = olderValue.count + newValue.count;
            merged.setLast(newValue.getLast());
            merged.setTime(newValue.getTime());
            merged.earliest = Math.min(olderValue.getEarliest(), newValue.getEarliest());
            merged.latest = Math.max(olderValue.getLatest(), newValue.getLatest());
            merged.open = nanSafe((a, b) -> a, olderValue.getOpen(), newValue.getOpen());
            merged.low = nanSafe(Math::min, olderValue.getLow(), newValue.getLow());
            merged.high = nanSafe(Math::max, olderValue.getHigh(), newValue.getHigh());
            unused.set(newValue);
            return merged;
        }
    }

    private long count;
    private long time;
    private long earliest;
    private long latest;
    private double last;
    private double open;
    private double low;
    private double high;
    private double close;

    public PriceEntry() {
        reset();
    }

    public PriceEntry reset() {
        count = 1;
        time = 0;
        earliest = Long.MAX_VALUE;
        latest = Long.MIN_VALUE;
        last = Double.NaN;
        open = Double.NaN;
        low = Double.NaN;
        high = Double.NaN;
        close = Double.NaN;
        return this;
    }

    public long getCount() {
        return count;
    }

    public long getTime() {
        return time;
    }

    public void setTime(final long time) {
        this.time = time;
        this.earliest = Math.min(this.earliest, time);
        this.latest = Math.max(this.latest, time);
    }

    public long getEarliest() {
        return Math.min(earliest, time);
    }

    public long getLatest() {
        return Math.max(latest, time);
    }

    public double getLast() {
        return last;
    }

    public void setLast(final double last) {
        this.last = last;
        this.open = notNanElse(this.open, last);
        this.low = notNanElse(this.low, last);
        this.high = notNanElse(this.high, last);
        this.close = notNanElse(this.close, last);
    }

    public double getOpen() {
        return open;
    }

    public double getLow() {
        return low;
    }

    public double getHigh() {
        return high;
    }

    public double getClose() {
        return close;
    }

    @Override
    public String toString() {
        return "PriceEntry{" +
                "count=" + count +
                ", time=" + time +
                ", earliest=" + earliest +
                ", latest=" + latest +
                ", last=" + last +
                ", open=" + open +
                ", low=" + low +
                ", high=" + high +
                ", close=" + close +
                '}';
    }

    private static double notNanElse(final double value, final double set) {
        return Double.isNaN(value) ? set : value;
    }

    private static double nanSafe(final DoubleBinaryOperator op, final double... values) {
        double result = Double.NaN;
        for (final double value : values) {
            if (Double.isNaN(result)) result = value;
            else if (!Double.isNaN(value)) result = op.applyAsDouble(result, value);
        }
        return result;
    }
}
