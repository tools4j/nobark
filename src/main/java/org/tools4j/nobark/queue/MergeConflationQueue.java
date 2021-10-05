/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 nobark (tools4j), Marco Terzer, Anton Anufriev
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

import sun.misc.Contended;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * A conflation queue implementation that merges old and new value if a value is enqueued and another one already exists
 * in the queue with the same conflation key.  The backing queue is supplied to the constructor and it determines
 * whether single or multiple producers and consumers are supported.
 * <p>
 * Each producer should acquire its own {@link #appender() appender} from the producing thread, and similarly every
 * consumer should call {@link #poller()} from the consuming thread.  Note that appender listener and poller listener
 * must be thread safe if multiple producers or consumers are used, e.g. use
 * {@link AppenderListener#threadLocalSupplier(Supplier)} and {@link PollerListener#threadLocalSupplier(Supplier)} to
 * create separate listener instances per producer/consumer thread.
 *
 * @param <K> the type of the conflation key
 * @param <V> the type of elements in the queue
 */
public class MergeConflationQueue<K,V> implements ExchangeConflationQueue<K,V> {

    private final Queue<Entry<K,MarkedValue<V>>> queue;
    private final Map<K,Entry<K,MarkedValue<V>>> entryMap;
    private final Merger<? super K, V> merger;

    private final ThreadLocal<Appender<K,V>> appender = ThreadLocal.withInitial(MergeQueueAppender::new);
    private final ThreadLocal<ExchangePoller<K,V>> poller = ThreadLocal.withInitial(MergeQueuePoller::new);

    private final Supplier<? extends AppenderListener<? super K, ? super V>> appenderListenerSupplier;
    private final Supplier<? extends PollerListener<? super K, ? super V>> pollerListenerSupplier;

    private MergeConflationQueue(final Queue<Entry<K,MarkedValue<V>>> queue,
                                 final Map<K,Entry<K,MarkedValue<V>>> entryMap,
                                 final Merger<? super K, V> merger,
                                 final Supplier<? extends AppenderListener<? super K, ? super V>> appenderListenerSupplier,
                                 final Supplier<? extends PollerListener<? super K, ? super V>> pollerListenerSupplier) {
        this.queue = Objects.requireNonNull(queue);
        this.entryMap = Objects.requireNonNull(entryMap);
        this.merger = Objects.requireNonNull(merger);
        this.appenderListenerSupplier = Objects.requireNonNull(appenderListenerSupplier);
        this.pollerListenerSupplier = Objects.requireNonNull(pollerListenerSupplier);
    }

    /**
     * Constructor with queue factory and merger.  A concurrent hash map is used to manage entries per conflation
     * key.
     *
     * @param queueFactory the factory to create the backing queue
     * @param merger the merge strategy to use if conflation occurs
     */
    public MergeConflationQueue(final Supplier<? extends Queue<Object>> queueFactory,
                                final Merger<? super K, V> merger) {
        this(queueFactory, merger, () -> AppenderListener.NOOP, () -> PollerListener.NOOP);
    }

    /**
     * Constructor with queue factory and merger.  A concurrent hash map is used to manage entries per conflation
     * key.
     *
     * @param queueFactory  the factory to create the backing queue
     * @param merger        the merge strategy to use if conflation occurs
     * @param appenderListenerSupplier  a supplier for a listener to monitor the enqueue operations
     * @param pollerListenerSupplier    a supplier for a listener to monitor the poll operations
     */
    public MergeConflationQueue(final Supplier<? extends Queue<Object>> queueFactory,
                                final Merger<? super K, V> merger,
                                final Supplier<? extends AppenderListener<? super K, ? super V>> appenderListenerSupplier,
                                final Supplier<? extends PollerListener<? super K, ? super V>> pollerListenerSupplier) {
        this(queueFactory, ConcurrentHashMap::new, merger, appenderListenerSupplier, pollerListenerSupplier);
    }

    /**
     * Constructor with queue factory, entry map factory and merger.
     *
     * @param queueFactory      the factory to create the backing queue
     * @param entryMapFactory   the factory to create the map that manages entries per conflation key
     * @param merger            the merge strategy to use if conflation occurs
     * @param appenderListenerSupplier  a supplier for a listener to monitor the enqueue operations
     * @param pollerListenerSupplier    a supplier for a listener to monitor the poll operations
     */
    public MergeConflationQueue(final Supplier<? extends Queue<Object>> queueFactory,
                                final Supplier<? extends Map<Object, Object>> entryMapFactory,
                                final Merger<? super K, V> merger,
                                final Supplier<? extends AppenderListener<? super K, ? super V>> appenderListenerSupplier,
                                final Supplier<? extends PollerListener<? super K, ? super V>> pollerListenerSupplier) {
        this(Factories.createQueue(queueFactory), Factories.createMap(entryMapFactory), merger,
                appenderListenerSupplier, pollerListenerSupplier);
    }

    /**
     * Constructor with queue factory, merger and the exhaustive list of conflation keys.  A hash map is pre-initialized
     * with all the conflation keys and pre-allocated entries.
     *
     * @param queueFactory the factory to create the backing queue
     * @param merger the merge strategy to use if conflation occurs
     * @param allConflationKeys all conflation keys that will ever be used with this conflation queue instance
     */
    public MergeConflationQueue(final Supplier<? extends Queue<Object>> queueFactory,
                                final Merger<? super K, V> merger,
                                final List<? extends K> allConflationKeys) {
        this(queueFactory, merger, allConflationKeys, () -> AppenderListener.NOOP, () -> PollerListener.NOOP);
    }

    /**
     * Constructor with queue factory, merger and the exhaustive list of conflation keys.  A hash map is pre-initialized
     * with all the conflation keys and pre-allocated entries.
     *
     * @param queueFactory the factory to create the backing queue
     * @param merger the merge strategy to use if conflation occurs
     * @param allConflationKeys all conflation keys that will ever be used with this conflation queue instance
     * @param appenderListenerSupplier a supplier for a listener to monitor the enqueue operations
     * @param pollerListenerSupplier a supplier for a listener to monitor the poll operations
     */
    public MergeConflationQueue(final Supplier<? extends Queue<Object>> queueFactory,
                                final Merger<? super K, V> merger,
                                final List<? extends K> allConflationKeys,
                                final Supplier<? extends AppenderListener<? super K, ? super V>> appenderListenerSupplier,
                                final Supplier<? extends PollerListener<? super K, ? super V>> pollerListenerSupplier) {
        this(Factories.createQueue(queueFactory), Entry.eagerlyInitialiseEntryMap(allConflationKeys, MarkedValue::new),
                merger, appenderListenerSupplier, pollerListenerSupplier);
    }

    /**
     * Static constructor method for a conflation queue with queue factory, merger and the conflation key enum class.
     * An enum map is pre-initialized with all the conflation keys and pre-allocated entries.
     *
     * @param queueFactory the factory to create the backing queue
     * @param merger the merge strategy to use if conflation occurs
     * @param conflationKeyClass the conflation key enum class
     * @param <K> the type of the conflation key
     * @param <V> the type of elements in the queue
     * @return the new conflation queue instance
     */
    public static <K extends Enum<K>,V> MergeConflationQueue<K,V> forEnumConflationKey(final Supplier<? extends Queue<Object>> queueFactory,
                                                                                       final Merger<? super K, V> merger,
                                                                                       final Class<K> conflationKeyClass) {
        return forEnumConflationKey(queueFactory, merger, conflationKeyClass, () -> AppenderListener.NOOP, () -> PollerListener.NOOP);
    }

    /**
     * Static constructor method for a conflation queue with queue factory, merger and the conflation key enum class.
     * An enum map is pre-initialized with all the conflation keys and pre-allocated entries.
     *
     * @param queueFactory the factory to create the backing queue
     * @param merger the merge strategy to use if conflation occurs
     * @param conflationKeyClass the conflation key enum class
     * @param appenderListenerSupplier a supplier for a listener to monitor the enqueue operations
     * @param pollerListenerSupplier a supplier for a listener to monitor the poll operations
     * @param <K> the type of the conflation key
     * @param <V> the type of elements in the queue
     * @return the new conflation queue instance
     */
    public static <K extends Enum<K>,V> MergeConflationQueue<K,V> forEnumConflationKey(final Supplier<? extends Queue<Object>> queueFactory,
                                                                                       final Merger<? super K, V> merger,
                                                                                       final Class<K> conflationKeyClass,
                                                                                       final Supplier<? extends AppenderListener<? super K, ? super V>> appenderListenerSupplier,
                                                                                       final Supplier<? extends PollerListener<? super K, ? super V>> pollerListenerSupplier) {
        return new MergeConflationQueue<>(
                Factories.createQueue(queueFactory), Entry.eagerlyInitialiseEntryEnumMap(conflationKeyClass, MarkedValue::new),
                merger, appenderListenerSupplier, pollerListenerSupplier
        );
    }

    @Override
    public Appender<K, V> appender() {
        return appender.get();
    }

    @Override
    public ExchangePoller<K, V> poller() {
        return poller.get();
    }

    @Override
    public int size() {
        return queue.size();
    }

    @Contended
    private final static class MarkedValue<V> {
        enum State {UNCONFIRMED, CONFIRMED, UNUSED}
        V value;
        volatile State state = State.UNUSED;
        MarkedValue<V> initializeWithUnconfirmed(final V value) {
            this.value = Objects.requireNonNull(value);
            this.state = State.UNCONFIRMED;
            return this;
        }
        MarkedValue<V> initalizeWithUnused(final V value) {
            this.value = value;//nulls allowed here
            this.state = State.UNUSED;
            return this;
        }
        void confirm() {
            this.state = State.CONFIRMED;
        }

        void confirmWith(final V value) {
            this.value = value;
            this.state = State.CONFIRMED;
        }

        V awaitAndRelease() {
            awaitFinalState();
            return release();
        }

        V release() {
            final V released = value;
            state = State.UNUSED;
            this.value = null;
            return released;
        }

        boolean isUnused() {
            return state == State.UNUSED;
        }

        private State awaitFinalState() {
            State s;
            do {
                s = state;
            } while (s == State.UNCONFIRMED);
            return s;
        }
    }

    private final class MergeQueueAppender implements Appender<K,V> {
        final AppenderListener<? super K, ? super V> appenderListener = appenderListenerSupplier.get();
        @Contended
        MarkedValue<V> markedValue = new MarkedValue<>();
        @Override
        public V enqueue(final K conflationKey, final V value) {
            Objects.requireNonNull(value);
            final Entry<K,MarkedValue<V>> entry = entryMap.computeIfAbsent(conflationKey, k -> new Entry<>(k, new MarkedValue<>()));
            final MarkedValue<V> newValue = markedValue.initializeWithUnconfirmed(value);
            final MarkedValue<V> oldValue = entry.value.getAndSet(newValue);
            final V add;
            final V old;
            final AppenderListener.Conflation conflation;
            try {
                if (oldValue.isUnused()) {
                    old = oldValue.release();
                    add = value;
                    newValue.confirm();
                    queue.add(entry);
                    conflation = AppenderListener.Conflation.UNCONFLATED;
                } else {
                    old = oldValue.awaitAndRelease();
                    try {
                        add = merger.merge(conflationKey, old, value);
                    } catch (final Throwable t) {
                        newValue.confirmWith(old);
                        throw t;
                    }
                    newValue.confirmWith(add);
                    conflation = AppenderListener.Conflation.MERGED;
                }
            } finally {
                markedValue = oldValue;
            }
            //NOTE: ok if below listener throws exception now as it cannot messs with the queue's state
            appenderListener.enqueued(MergeConflationQueue.this, conflationKey, add, old, conflation);
            return old;
        }
    }

    private final class MergeQueuePoller implements ExchangePoller<K,V> {
        final PollerListener<? super K, ? super V> pollerListener = pollerListenerSupplier.get();
        @Contended
        MarkedValue<V> markedValue = new MarkedValue<>();
        @Override
        public V poll(final BiConsumer<? super K, ? super V> consumer, final V exchange) {
            final Entry<K,MarkedValue<V>> entry = queue.poll();
            if (entry != null) {
                final MarkedValue<V> exchangeValue = markedValue.initalizeWithUnused(exchange);
                final MarkedValue<V> polledValue = entry.value.getAndSet(exchangeValue);
                final V value = polledValue.awaitAndRelease();
                markedValue = polledValue;
                consumer.accept(entry.key, value);
                pollerListener.polled(MergeConflationQueue.this, entry.key, value);
                return value;
            } else {
                pollerListener.polledButFoundEmpty(MergeConflationQueue.this);
                return null;
            }
        }
    }
}
