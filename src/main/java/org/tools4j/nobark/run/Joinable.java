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

/**
 * Joinable is a running service such as a thread that can be {@link #join() joined} to await
 * its termination.
 */
@FunctionalInterface
public interface Joinable {
    /**
     * Waits for this Joinable to die.
     *
     * <p> An invocation of this method behaves in exactly the same
     * way as the invocation
     *
     * <blockquote>
     * {@linkplain #join(long) join}{@code (0)}
     * </blockquote>
     *
     * @throws  IllegalStateException
     *          if any thread has interrupted the current thread
     * @see Thread#join()
     */
    default void join() {
        join(0);
    }

    /**
     * Waits at most {@code millis} milliseconds for this Joinable to
     * die. A timeout of {@code 0} means to wait forever.
     *
     * @param  millis
     *         the time to wait in milliseconds
     * @throws  IllegalArgumentException
     *          if the value of {@code millis} is negative
     * @throws  IllegalStateException
     *          if any thread has interrupted the current thread
     * @see Thread#join(long)
     */
    void join(long millis);
}
