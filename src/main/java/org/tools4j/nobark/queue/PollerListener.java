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
 * A listener for an {@link ConflationQueue.Poller poller} of a {@link ConflationQueue} for instance useful to
 * record performance metrics related to poll operations.  The listener or a supplier thereof is usually passed to
 * the constructor of a conflation queue.  Note that listeners must be thread safe if used in a multi-consumer
 * environment;  best practice is to use separate listener instances for each consumer thread e.g. by using
 * {@link #threadLocal(Supplier)} or {@link #threadLocalSupplier(Supplier)}.
 *
 * @param <K> the type of the conflation key
 * @param <V> the type of values in the queue
 */
@FunctionalInterface
public interface PollerListener<K,V> {

    /**
     * Called whenever a value is polled via any of the {@link ConflationQueue.Poller#poll() poll} methods.  By default
     * this method is also invoked when the queue was found empty (with null key and value) unless
     * {@link #polledButFoundEmpty(ConflationQueue)} is overridden and implemented differently.
     *
     * @param queue the conflation queue, sometimes useful to record queue size metrics, never null
     * @param key   the conflation key, may be null for poll invocations of the empty queue
     * @param value the value that was polled, or null for unsuccessful poll invocations (i.e. when the queue was found
     *              empty)
     */
    void polled(ConflationQueue<? extends K, ? extends V> queue, K key, V value);

    /**
     * Called whenever a poll attempt was made but the queue was found empty.  The default implementation delegates the
     * call to {@link #polled(ConflationQueue, Object, Object)} passing nulls for key and value.
     *
     * @param queue the conflation queue, sometimes useful to record queue size metrics, never null
     */
    default void polledButFoundEmpty(final ConflationQueue<? extends K, ? extends V> queue) {
        polled(queue, null, null);
    }

    /**
     * Constant for a no-op listener.
     */
    PollerListener<Object,Object> NOOP = (q, k, v) -> {};

    /**
     * Creates a poller listener that delegates to thread-local listener instances created on demand by the given
     * supplier.  This listener can be passed to the constructors of {@link AtomicConflationQueue} which use a single
     * poller instance for all consumer threads.
     *
     * @param listenerSupplier  the supplier acting as factory for per-thread listener instances
     * @param <K> the type of the conflation key
     * @param <V> the type of values in the queue
     * @return a listener that delegates to thread-local listener instances
     */
    static <K,V> PollerListener<K,V> threadLocal(final Supplier<? extends PollerListener<K,V>> listenerSupplier) {
        final ThreadLocal<PollerListener<K,V>> threadLocal = ThreadLocal.withInitial(listenerSupplier);
        return (q,k,v) -> threadLocal.get().polled(q,k,v);
    }

    /**
     * Creates a thread local supplier that creates a new instance once for every caller thread.  This listener can be
     * passed to the constructors of {@link EvictConflationQueue} and {@link MergeConflationQueue} which use a per
     * thread poller instances;  the returned supplier will create a new thread-local listener instance for every
     * poller instance that is created.
     *
     * @param listenerSupplier  the supplier acting as factory for per-thread listener instances
     * @param <L> the poller listener (sub-)type
     * @return a supplier creating thread-local listener instances
     */
    static <L extends PollerListener<?,?>> Supplier<L> threadLocalSupplier(final Supplier<L> listenerSupplier) {
        return ThreadLocal.withInitial(listenerSupplier)::get;
    }
}
