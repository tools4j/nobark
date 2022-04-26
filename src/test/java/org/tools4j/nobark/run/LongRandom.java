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

import java.util.Random;

public class LongRandom extends Random {

    public long nextLong(final long bound) {
        if (bound <= Integer.MAX_VALUE)
            return nextInt((int)bound);

        long r = next64(63);
        long m = bound - 1;
        if ((bound & m) == 0)  // i.e., bound is a power of 2
            r >>= Long.numberOfLeadingZeros(bound);
        else {
            for (long u = r;
                 u - (r = u % bound) + m < 0;
                 u = next64(63))
                ;
        }
        return r;
    }

    public long next64(final int bits) {
        if (bits <= 32)
            return 0xffffffffL & next(bits);
        long r = 0xffffffffL & next(bits - 32);
        r <<= 32;
        r |= (0xffffffffL & next(32));
        return r;
    }
}
