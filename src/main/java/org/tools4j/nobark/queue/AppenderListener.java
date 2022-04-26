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
package org.tools4j.nobark.queue;

import java.util.function.Supplier;

/**
 * A listener for an {@link ConflationQueue.Appender appender} of a {@link ConflationQueue} for instance useful to
 * record performance metrics related to enqueue operations.  The listener or a supplier thereof is usually passed to
 * the constructor of a conflation queue.  Note that listeners must be thread safe if used in a multi-producer
 * environment;  best practice is to use separate listener instances for each producer thread e.g. by using
 * {@link #threadLocal(Supplier)} or {@link #threadLocalSupplier(Supplier)}.
 *
 * @param <K> the type of the conflation key
 * @param <V> the type of values in the queue
 */
@FunctionalInterface
public interface AppenderListener<K,V> {
    /**
     * The type of conflation that occurred when an element was enqueued.
     */
    enum Conflation {
        /**
         * No conflation occurred, that is, no other value with the same conflation key exists in the queue at the
         * time of enqueueing the value.
         */
        UNCONFLATED,
        /**
         * Conflation occurred and the existing old value present in the queue was evicted and replaced by the newly
         * enqueued value.
         */
        EVICTED,
        /**
         * Conflation occurred and the existing old element present in the queue was merged with the newly enqueued
         * element via a {@link Merger};  the result of the merge operation is the value in the queue now.
         */
        MERGED
    }

    /**
     * Called whenever a value is {@link ConflationQueue.Appender#enqueue(Object, Object) enqueued}.
     *
     * @param queue         the conflation queue, sometimes useful to record queue size metrics, never null
     * @param key           the conflation key, never null
     * @param newValue      the value that has been enqueued (the merged value if conflation==MERGED), never null
     * @param oldValue      the old value that is replaced, unless conflation=UNCONFLATED in which case old value is
     *                      either null or the exchange value in case of an {@link ExchangeConflationQueue}
     * @param conflation    the type of conflation that has occurred, if any
     */
    void enqueued(ConflationQueue<? extends K, ? extends V> queue, K key, V newValue, V oldValue, Conflation conflation);

    /**
     * Constant for a no-op listener.
     */
    AppenderListener<Object,Object> NOOP = (q, k, v, o, c) -> {};

    /**
     * Creates an appender listener that delegates to thread-local listener instances created on demand by the given
     * supplier.  This listener can be passed to the constructors of {@link AtomicConflationQueue} which use a single
     * appender instance for all producer threads.
     *
     * @param listenerSupplier  the supplier acting as factory for per-thread listener instances
     * @param <K> the type of the conflation key
     * @param <V> the type of values in the queue
     * @return a listener that delegates to thread-local listener instances
     */
    static <K,V> AppenderListener<K,V> threadLocal(final Supplier<? extends AppenderListener<K,V>> listenerSupplier) {
        final ThreadLocal<AppenderListener<K,V>> threadLocal = ThreadLocal.withInitial(listenerSupplier);
        return (q,k,v,o,c) -> threadLocal.get().enqueued(q,k,v,o,c);
    }

    /**
     * Creates a thread local supplier that creates a new instance once for every caller thread.  This listener can be
     * passed to the constructors of {@link EvictConflationQueue} and {@link MergeConflationQueue} which use a per
     * thread appender instances;  the returned supplier will create a new thread-local listener instance for every
     * appender instance that is created.
     *
     * @param listenerSupplier  the supplier acting as factory for per-thread listener instances
     * @param <L> the appender listener (sub-)type
     * @return a supplier creating thread-local listener instances
     */
    static <L extends AppenderListener<?,?>> Supplier<L> threadLocalSupplier(final Supplier<L> listenerSupplier) {
        return ThreadLocal.withInitial(listenerSupplier)::get;
    }
}
