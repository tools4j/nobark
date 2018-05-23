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

import java.util.function.BiConsumer;

/**
 * A conflation queue is a queue with a safety mechanism to prevent overflow.  Values are enqueued with a conflation
 * key, and if a value with the same key already resides in the queue then the two values will be "conflated".
 * Conflation in the simplest case means that the most recent value survives and replaces older values;  some more
 * advanced implementations support merging when conflation occurs.
 *
 * @param <K> the type of the conflation key
 * @param <V> the type of values in the queue
 */
public interface ConflationQueue<K,V> {
    /**
     * Returns the appender object used by the producer to enqueue values.  Some conflation queue implementations return
     * thread-safe appender instances, other implementations return an instance per caller thread.  Hence it is highly
     * recommended to access the appender via this method directly from the producer thread and not share it between
     * threads.
     *
     * @return the appender object used to enqueue values
     */
    Appender<K,V> appender();

    /**
     * Returns the poller object used by the consumer to poll values.  Some conflation queue implementations return
     * thread-safe poller instances, other implementations return an instance per caller thread.  Hence it is highly
     * recommended to access the poller via this method directly from the consumer thread and not share it between
     * threads.
     *
     * @return the poller object used to poll values
     */
    Poller<K,V> poller();

    /**
     * Returns the number of elements in this queue.
     *
     * <p>Beware that, unlike in most collections, this method may
     * <em>NOT</em> be a constant-time operation. Because of the
     * asynchronous nature of concurrent queues, determining the current
     * number of elements may require an O(n) traversal.
     * Additionally, if elements are added or removed during execution
     * of this method, the returned result may be inaccurate.  Thus,
     * this method is typically not very useful in concurrent
     * applications.
     *
     * @return the number of elements in this queue
     */
    int size();

    /**
     * Appender used by the producer to enqueue values.
     *
     * @param <K> the type of the conflation key
     * @param <V> the type of values in the queue
     */
    interface Appender<K,V> {
        /**
         * Enqueue the specified value using the given conflation key.  If conflation occurred, the conflated value
         * is returned.  If no conflation occurs, null is returned, or for {@link ExchangeConflationQueue}
         * implementations an exchange value from the poller may be returned if present.
         *
         * @param conflationKey the conflation key
         * @param value         the value to enqueue
         * @return the conflated value if conflation occurred, or otherwise null or an exchange value from the poller
         *         for {@link ExchangeConflationQueue} implementations
         */
        V enqueue(K conflationKey, V value);
    }

    /**
     * Poller object used by the consumer to poll values.
     *
     * @param <K> the type of the conflation key
     * @param <V> the type of values in the queue
     */
    interface Poller<K, V> {
        /**
         * Polls the queue passing a consumer which is invoked with conflation key and polled value if the queue was
         * non-empty.  Returns the polled value if any value was present in the queue, or null if the queue was empty.
         *
         * @param consumer consumer for conflation key and polled value
         * @return the polled value, or null if the queue was empty
         */
        V poll(BiConsumer<? super K, ? super V> consumer);

        /**
         * Polls the queue and returns the value if any value was present in the queue, or null if the queue was empty.
         *
         * @return the polled value, or null if the queue was empty
         */
        default V poll() {
            return poll((k,v) -> {});
        }
    }
}
