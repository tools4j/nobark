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

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * A conflation queue implementation that atomically evicts old values from the queue if a new one is enqueued with the
 * same conflation key.  The {@link EvictConflationQueue} is very similar but not atomic and it supports value exchange
 * on polling, which this queue does not.  A backing queue is supplied to the constructor and it determines whether
 * single or multiple producers and consumers are supported.
 * <p>
 * {@link #appender() Appender} and {@link #poller()} are both stateless and hence thread-safe.  Note that appender
 * listener and poller listener must also be thread safe if multiple producers or consumers are used, e.g. use
 * {@link AppenderListener#threadLocal(Supplier)} and {@link PollerListener#threadLocal(Supplier)} to create thread
 * local listener instances.
 *
 * @param <K> the type of the conflation key
 * @param <V> the type of elements in the queue
 */
public class AtomicConflationQueue<K,V> implements ConflationQueue<K,V> {
    private final Queue<Entry<K,V>> queue;
    private final Map<K,Entry<K,V>> entryMap;

    private final Appender<K,V> appender = new AtomicQueueAppender();
    private final Poller<K,V> poller = new AtomicQueuePoller();

    private final AppenderListener<? super K, ? super V> appenderListener;
    private final PollerListener<? super K, ? super V> pollerListener;

    private AtomicConflationQueue(final Queue<Entry<K,V>> queue,
                                  final Map<K,Entry<K,V>> entryMap,
                                  final AppenderListener<? super K, ? super V> appenderListener,
                                  final PollerListener<? super K, ? super V> pollerListener) {
        this.queue = Objects.requireNonNull(queue);
        this.entryMap = Objects.requireNonNull(entryMap);
        this.appenderListener= Objects.requireNonNull(appenderListener);
        this.pollerListener = Objects.requireNonNull(pollerListener);
    }

    /**
     * Constructor with queue factory.  A concurrent hash map is used to manage entries per conflation key.
     *
     * @param queueFactory the factory to create the backing queue
     */
    public AtomicConflationQueue(final Supplier<? extends Queue<Object>> queueFactory) {
        this(queueFactory, AppenderListener.NOOP, PollerListener.NOOP);
    }

    /**
     * Constructor with queue factory.  A concurrent hash map is used to manage entries per conflation key.
     *
     * @param queueFactory the factory to create the backing queue
     * @param appenderListener a listener to monitor the enqueue operations
     * @param pollerListener a listener to monitor the poll operations
     */
    public AtomicConflationQueue(final Supplier<? extends Queue<Object>> queueFactory,
                                 final AppenderListener<? super K, ? super V> appenderListener,
                                 final PollerListener<? super K, ? super V> pollerListener) {
        this(queueFactory, ConcurrentHashMap::new, appenderListener, pollerListener);
    }

    /**
     * Constructor with queue factory and entry map factory.
     *
     * @param queueFactory      the factory to create the backing queue
     * @param entryMapFactory   the factory to create the map that manages entries per conflation key
     * @param appenderListener  a listener to monitor the enqueue operations
     * @param pollerListener    a listener to monitor the poll operations
     */
    public AtomicConflationQueue(final Supplier<? extends Queue<Object>> queueFactory,
                                 final Supplier<? extends Map<Object, Object>> entryMapFactory,
                                 final AppenderListener<? super K, ? super V> appenderListener,
                                 final PollerListener<? super K, ? super V> pollerListener) {
        this(Factories.createQueue(queueFactory), Factories.createMap(entryMapFactory), appenderListener, pollerListener);
    }

    /**
     * Constructor with queue factory and the exhaustive list of conflation keys.  A hash map is pre-initialized with
     * all the conflation keys and pre-allocated entries.
     *
     * @param queueFactory the factory to create the backing queue
     * @param allConflationKeys all conflation keys that will ever be used with this conflation queue instance
     */
    public AtomicConflationQueue(final Supplier<? extends Queue<Object>> queueFactory,
                                 final List<? extends K> allConflationKeys) {
        this(queueFactory, allConflationKeys, AppenderListener.NOOP, PollerListener.NOOP);
    }

    /**
     * Constructor with queue factory and the exhaustive list of conflation keys.  A hash map is pre-initialized with
     * all the conflation keys and pre-allocated entries.
     *
     * @param queueFactory the factory to create the backing queue
     * @param allConflationKeys all conflation keys that will ever be used with this conflation queue instance
     * @param appenderListener a listener to monitor the enqueue operations
     * @param pollerListener a listener to monitor the poll operations
     */
    public AtomicConflationQueue(final Supplier<? extends Queue<Object>> queueFactory,
                                 final List<? extends K> allConflationKeys,
                                 final AppenderListener<? super K, ? super V> appenderListener,
                                 final PollerListener<? super K, ? super V> pollerListener) {
        this(Factories.createQueue(queueFactory), Entry.eagerlyInitialiseEntryMap(allConflationKeys, () -> null),
                appenderListener, pollerListener);
    }

    /**
     * Static constructor method for a conflation queue with queue factory and the conflation key enum class.  An enum
     * map is pre-initialized with all the conflation keys and pre-allocated entries.
     *
     * @param queueFactory the factory to create the backing queue
     * @param conflationKeyClass the conflation key enum class
     * @param <K> the type of the conflation key
     * @param <V> the type of elements in the queue
     * @return the new conflation queue instance
     */
    public static <K extends Enum<K>,V> AtomicConflationQueue<K,V> forEnumConflationKey(final Supplier<? extends Queue<Object>> queueFactory,
                                                                                        final Class<K> conflationKeyClass) {
        return forEnumConflationKey(queueFactory, conflationKeyClass, AppenderListener.NOOP, PollerListener.NOOP);
    }

    /**
     * Static constructor method for a conflation queue with queue factory and the conflation key enum class.  An enum
     * map is pre-initialized with all the conflation keys and pre-allocated entries.
     *
     * @param queueFactory the factory to create the backing queue
     * @param conflationKeyClass the conflation key enum class
     * @param appenderListener a listener to monitor the enqueue operations
     * @param pollerListener a listener to monitor the poll operations
     * @param <K> the type of the conflation key
     * @param <V> the type of elements in the queue
     * @return the new conflation queue instance
     */
    public static <K extends Enum<K>,V> AtomicConflationQueue<K,V> forEnumConflationKey(final Supplier<? extends Queue<Object>> queueFactory,
                                                                                        final Class<K> conflationKeyClass,
                                                                                        final AppenderListener<? super K, ? super V> appenderListener,
                                                                                        final PollerListener<? super K, ? super V> pollerListener) {
        return new AtomicConflationQueue<>(Factories.createQueue(queueFactory),
                Entry.eagerlyInitialiseEntryEnumMap(conflationKeyClass, () -> null), appenderListener, pollerListener);
    }

    @Override
    public Appender<K, V> appender() {
        return appender;
    }

    @Override
    public Poller<K, V> poller() {
        return poller;
    }

    @Override
    public int size() {
        return queue.size();
    }

    private final class AtomicQueueAppender implements Appender<K,V> {
        @Override
        public V enqueue(final K conflationKey, final V value) {
            Objects.requireNonNull(value);
            final Entry<K,V> entry = entryMap.computeIfAbsent(conflationKey, k -> new Entry<>(k, null));
            final V old = entry.value.getAndSet(value);
            final AppenderListener.Conflation conflation;
            if (old == null) {
                queue.add(entry);
                conflation = AppenderListener.Conflation.UNCONFLATED;
            } else {
                conflation = AppenderListener.Conflation.EVICTED;
            }
            appenderListener.enqueued(AtomicConflationQueue.this, conflationKey, value, old, conflation);
            return old;
        }
    }

    private final class AtomicQueuePoller implements Poller<K,V> {
        @Override
        public V poll(final BiConsumer<? super K, ? super V> consumer) {
            final Entry<K,V> entry = queue.poll();
            if (entry != null) {
                final V value = entry.value.getAndSet(null);
                consumer.accept(entry.key, value);
                pollerListener.polled(AtomicConflationQueue.this, entry.key, value);
                return value;
            } else {
                pollerListener.polledButFoundEmpty(AtomicConflationQueue.this);
                return null;
            }
        }
    }
}
