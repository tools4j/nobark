/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2018 process (tools4j), Marco Terzer, Anton Anufriev
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
 * A conflation queue implementation that evicts old values from the queue if a new one is enqueued with the same
 * conflation key.  The {@link AtomicConflationQueue} is very similar but this queue implements
 * {@link ExchangeConflationQueue} and hence supports value exchange between producer and consumer.  The backing queue
 * is supplied to the constructor and it determines whether single or multiple producers and consumers are supported.
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
public class EvictConflationQueue<K,V> implements ExchangeConflationQueue<K,V> {

    private final Map<K,Entry<K,MarkedValue<V>>> entryMap;
    private final Queue<Entry<K,MarkedValue<V>>> queue;

    private final ThreadLocal<Appender<K,V>> appender = ThreadLocal.withInitial(EvictQueueAppender::new);
    private final ThreadLocal<ExchangePoller<K,V>> poller = ThreadLocal.withInitial(EvictQueuePoller::new);

    private final Supplier<? extends AppenderListener<? super K, ? super V>> appenderListenerSupplier;
    private final Supplier<? extends PollerListener<? super K, ? super V>> pollerListenerSupplier;

    @SuppressWarnings("unchecked")//casting a queue that takes objects to one that takes Entry is fine as long as we only add Entry objects
    private EvictConflationQueue(final Map<K,Entry<K,MarkedValue<V>>> entryMap,
                                 final Supplier<? extends Queue<Object>> queueFactory,
                                 final Supplier<? extends AppenderListener<? super K, ? super V>> appenderListenerSupplier,
                                 final Supplier<? extends PollerListener<? super K, ? super V>> pollerListenerSupplier) {
        this(entryMap, (Queue<Entry<K,MarkedValue<V>>>)(Object)queueFactory.get(), appenderListenerSupplier, pollerListenerSupplier);
    }

    private EvictConflationQueue(final Map<K,Entry<K,MarkedValue<V>>> entryMap,
                                 final Queue<Entry<K,MarkedValue<V>>> queue,
                                 final Supplier<? extends AppenderListener<? super K, ? super V>> appenderListenerSupplier,
                                 final Supplier<? extends PollerListener<? super K, ? super V>> pollerListenerSupplier) {
        this.entryMap = Objects.requireNonNull(entryMap);
        this.queue = Objects.requireNonNull(queue);
        this.appenderListenerSupplier = Objects.requireNonNull(appenderListenerSupplier);
        this.pollerListenerSupplier = Objects.requireNonNull(pollerListenerSupplier);
    }

    /**
     * Constructor with queue cqFactory.  A concurrent hash map is used to to recycle entries per conflation key.
     *
     * @param queueFactory the cqFactory to create the backing queue
     */
    public EvictConflationQueue(final Supplier<? extends Queue<Object>> queueFactory) {
        this(queueFactory, () -> AppenderListener.NOOP, () -> PollerListener.NOOP);
    }

    /**
     * Constructor with queue cqFactory.  A concurrent hash map is used to to recycle entries per conflation key.
     *
     * @param queueFactory the cqFactory to create the backing queue
     * @param appenderListenerSupplier a supplier for a listener to monitor the enqueue operations
     * @param pollerListenerSupplier a supplier for a listener to monitor the poll operations
     */
    public EvictConflationQueue(final Supplier<? extends Queue<Object>> queueFactory,
                                final Supplier<? extends AppenderListener<? super K, ? super V>> appenderListenerSupplier,
                                final Supplier<? extends PollerListener<? super K, ? super V>> pollerListenerSupplier) {
        this(new ConcurrentHashMap<>(), queueFactory, appenderListenerSupplier, pollerListenerSupplier);
    }

    /**
     * Constructor with queue cqFactory and the exhaustive list of conflation keys.  A hash map is pre-initialized with
     * all the conflation keys and pre-allocated entries.
     *
     * @param queueFactory the cqFactory to create the backing queue
     * @param allConflationKeys all conflation keys that will ever be used with this conflation queue instance
     */
    public EvictConflationQueue(final Supplier<? extends Queue<Object>> queueFactory,
                                final List<? extends K> allConflationKeys) {
        this(queueFactory, allConflationKeys, () -> AppenderListener.NOOP, () -> PollerListener.NOOP);
    }

    /**
     * Constructor with queue cqFactory and the exhaustive list of conflation keys.  A hash map is pre-initialized with
     * all the conflation keys and pre-allocated entries.
     *
     * @param queueFactory the cqFactory to create the backing queue
     * @param allConflationKeys all conflation keys that will ever be used with this conflation queue instance
     * @param appenderListenerSupplier a supplier for a listener to monitor the enqueue operations
     * @param pollerListenerSupplier a supplier for a listener to monitor the poll operations
     */
    public EvictConflationQueue(final Supplier<? extends Queue<Object>> queueFactory,
                                final List<? extends K> allConflationKeys,
                                final Supplier<? extends AppenderListener<? super K, ? super V>> appenderListenerSupplier,
                                final Supplier<? extends PollerListener<? super K, ? super V>> pollerListenerSupplier) {
        this(Entry.eagerlyInitialiseEntryMap(allConflationKeys, MarkedValue::new), queueFactory, appenderListenerSupplier, pollerListenerSupplier);
    }

    /**
     * Static constructor method for a conflation queue with queue cqFactory and the conflation key enum class.  An enum
     * map is pre-initialized with all the conflation keys and pre-allocated entries.
     *
     * @param queueFactory the cqFactory to create the backing queue
     * @param conflationKeyClass the conflation key enum class
     * @param <K> the type of the conflation key
     * @param <V> the type of elements in the queue
     * @return the new conflation queue instance
     */
    public static <K extends Enum<K>,V> EvictConflationQueue<K,V> forEnumConflationKey(final Supplier<? extends Queue<Object>> queueFactory,
                                                                                       final Class<K> conflationKeyClass) {
        return forEnumConflationKey(queueFactory, conflationKeyClass, () -> AppenderListener.NOOP, () -> PollerListener.NOOP);
    }

    /**
     * Static constructor method for a conflation queue with queue cqFactory and the conflation key enum class.  An enum
     * map is pre-initialized with all the conflation keys and pre-allocated entries.
     *
     * @param queueFactory the cqFactory to create the backing queue
     * @param conflationKeyClass the conflation key enum class
     * @param appenderListenerSupplier a supplier for a listener to monitor the enqueue operations
     * @param pollerListenerSupplier a supplier for a listener to monitor the poll operations
     * @param <K> the type of the conflation key
     * @param <V> the type of elements in the queue
     * @return the new conflation queue instance
     */
    public static <K extends Enum<K>,V> EvictConflationQueue<K,V> forEnumConflationKey(final Supplier<? extends Queue<Object>> queueFactory,
                                                                                       final Class<K> conflationKeyClass,
                                                                                       final Supplier<? extends AppenderListener<? super K, ? super V>> appenderListenerSupplier,
                                                                                       final Supplier<? extends PollerListener<? super K, ? super V>> pollerListenerSupplier) {
        return new EvictConflationQueue<>(
                Entry.eagerlyInitialiseEntryEnumMap(conflationKeyClass, MarkedValue::new), queueFactory,
                appenderListenerSupplier, pollerListenerSupplier
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
        enum State {USED, UNUSED}
        V value;
        State state = State.UNUSED;
        MarkedValue<V> initializeWithUsed(final V value) {
            this.value = Objects.requireNonNull(value);
            this.state = State.USED;
            return this;
        }
        MarkedValue<V> initalizeWithUnused(final V value) {
            this.value = value;//nulls allowed here
            this.state = State.UNUSED;
            return this;
        }

        V markUnusedAndRelease() {
            final V released = value;
            state = State.UNUSED;
            value = null;
            return released;
        }

        boolean isUnused() {
            return state == State.UNUSED;
        }
    }

    private final class EvictQueueAppender implements Appender<K,V> {
        final AppenderListener<? super K, ? super V> appenderListener = appenderListenerSupplier.get();
        @Contended
        MarkedValue<V> markedValue = new MarkedValue<>();
        @Override
        public V enqueue(final K conflationKey, final V value) {
            Objects.requireNonNull(value);
            final Entry<K, MarkedValue<V>> entry = entryMap.computeIfAbsent(conflationKey, k -> new Entry<>(k, new MarkedValue<>()));
            final MarkedValue<V> newValue = markedValue.initializeWithUsed(value);
            final MarkedValue<V> oldValue = entry.value.getAndSet(newValue);
            final AppenderListener.Conflation conflation;
            if (oldValue.isUnused()) {
                queue.add(entry);
                conflation = AppenderListener.Conflation.UNCONFLATED;
            } else {
                conflation = AppenderListener.Conflation.EVICTED;
            }
            markedValue = oldValue;
            final V old = oldValue.markUnusedAndRelease();
            //NOTE: ok if listener throws exception now as it cannot mess up this queue's state
            appenderListener.enqueued(EvictConflationQueue.this, conflationKey, value, old, conflation);
            return old;
        }
    }

    private final class EvictQueuePoller implements ExchangePoller<K,V> {
        final PollerListener<? super K, ? super V> pollerListener = pollerListenerSupplier.get();
        @Contended
        MarkedValue<V> markedValue = new MarkedValue<>();
        @Override
        public V poll(final BiConsumer<? super K, ? super V> consumer, final V exchange) {
            final Entry<K,MarkedValue<V>> entry = queue.poll();
            if (entry != null) {
                final MarkedValue<V> exchangeValue = markedValue.initalizeWithUnused(exchange);
                final MarkedValue<V> polledValue = entry.value.getAndSet(exchangeValue);
                final V value = polledValue.markUnusedAndRelease();
                markedValue = polledValue;
                consumer.accept(entry.key, value);
                pollerListener.polled(EvictConflationQueue.this, entry.key, value);
                return value;
            } else {
                pollerListener.polledButFoundEmpty(EvictConflationQueue.this);
                return null;
            }
        }
    }
}
