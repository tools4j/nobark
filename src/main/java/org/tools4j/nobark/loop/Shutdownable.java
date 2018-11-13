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
package org.tools4j.nobark.loop;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Shutdownable is a running service such as a thread that can be shutdown orderly or abruptly in a way similar to
 * {@link ExecutorService}.
 */
public interface Shutdownable {
    /**
     * Initiates an orderly shutdown in which the service will be continue to perform its duties
     * as long as there is work to be done.  For instance for a sequence of steps, the service could
     * continue to invoke the steps' {@link Step#perform() perform()} methods until all invocations
     * yield false (indicating that no work was done).
     * <p>
     * This method does not wait for for termination; use {@link #awaitTermination awaitTermination}
     * to do that.
     * <p>
     * Invocation has no additional effect if already shut down.
     */
    void shutdown();

    /**
     * Initiates an immediate shutdown in which the service will stop even if there is more work
     * to be done.  For instance for a sequence of steps, the service could abort invocation of the
     * steps' {@link Step#perform() perform()} irrespective of prior invocation results.
     * <p>
     * This method does not wait for for termination; use {@link #awaitTermination awaitTermination}
     * to do that.
     * <p>
     * Invocation has no additional effect if already shut down.
     */
    void shutdownNow();

    /**
     * Returns {@code true} if this service has been shut down.
     *
     * @return {@code true} if this service has been shut down
     */
    boolean isShutdown();

    /**
     * Returns {@code true} if this service has terminated following shut down.  Note that
     * {@code isTerminated} is never {@code true} unless either {@code shutdown} or
     * {@code shutdownNow} was called first.
     *
     * @return {@code true} if the service has terminated following shut down
     */
    boolean isTerminated();

    /**
     * Blocks until all tasks have completed execution after a shutdown request, or the timeout occurs,
     * whichever happens first.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @return {@code true} if this executor terminated and
     *         {@code false} if the timeout elapsed before termination
     */
    boolean awaitTermination(long timeout, TimeUnit unit);
}
