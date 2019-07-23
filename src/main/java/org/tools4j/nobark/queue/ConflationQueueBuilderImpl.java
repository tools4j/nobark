/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 nobark (tools4j), Marco Terzer, Anton Anufriev
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
import java.util.function.Supplier;

/**
 * Package local builder implementation as returned by the static methods of {@link ConflationQueueBuilder}.
 *
 * @param <K> the conflation key type
 * @param <V> the value type
 */
class ConflationQueueBuilderImpl<K,V> implements ConflationQueueBuilder<K,V> {

    private final CqFactory<K,V> cqFactory;

    private Supplier<? extends AppenderListener<? super K, ? super V>> appenderListenerSupplier = () -> AppenderListener.NOOP;
    private Supplier<? extends PollerListener<? super K, ? super V>> pollerListenerSupplier = () -> PollerListener.NOOP;

    private ConflationQueueBuilderImpl(final CqFactory<K,V> cqFactory) {
        this.cqFactory = Objects.requireNonNull(cqFactory);
    }

    @Override
    public ConflationQueueBuilder<K, V> withAppenderListener(final Supplier<? extends AppenderListener<? super K, ? super V>> listenerSupplier) {
        this.appenderListenerSupplier = Objects.requireNonNull(listenerSupplier);
        return this;
    }

    @Override
    public ConflationQueueBuilder<K, V> withPollerListener(final Supplier<? extends PollerListener<? super K, ? super V>> listenerSupplier) {
        this.pollerListenerSupplier = Objects.requireNonNull(listenerSupplier);
        return this;
    }

    @Override
    public ExchangeConflationQueueBuilder<K,V> withExchangeValueSupport() {
        return new EvictConflationQueueBuilder();
    }

    @Override
    public ExchangeConflationQueueBuilder<K, V> withMerging(final Merger<? super K, V> merger) {
        return new MergeConflationQueueBuilder(merger);
    }

    @Override
    public ConflationQueue<K,V> build(final Supplier<? extends Queue<Object>> queueFactory) {
        return cqFactory.atomicQueue(queueFactory, appenderListenerSupplier, pollerListenerSupplier);
    }

    /**
     * Builder-internal factory for different types of conflation queues.
     *
     * @param <K> the conflation key type
     * @param <V> the value type
     */
    interface CqFactory<K,V> {
        /**
         * Factory method for {@link AtomicConflationQueue}
         * @param queueFactory factory for the backing queue
         * @param appenderListenerSupplier  supplier acting as appender listener factory
         * @param pollerListenerSupplier    supplier acting as poller listener factory
         * @return a new {@link AtomicConflationQueue} instance
         */
        AtomicConflationQueue<K,V> atomicQueue(Supplier<? extends Queue<Object>> queueFactory,
                                               Supplier<? extends AppenderListener<? super K, ? super V>> appenderListenerSupplier,
                                               Supplier<? extends PollerListener<? super K, ? super V>> pollerListenerSupplier);
        /**
         * Factory method for {@link EvictConflationQueue}.
         * @param queueFactory factory for the backing queue
         * @param appenderListenerSupplier  supplier acting as appender listener factory
         * @param pollerListenerSupplier    supplier acting as poller listener factory
         * @return a new {@link EvictConflationQueue} instance
         */
        EvictConflationQueue<K,V> evictQueue(Supplier<? extends Queue<Object>> queueFactory,
                                             Supplier<? extends AppenderListener<? super K, ? super V>> appenderListenerSupplier,
                                             Supplier<? extends PollerListener<? super K, ? super V>> pollerListenerSupplier);
        /**
         * Factory method for {@link MergeConflationQueue}.
         * @param queueFactory  factory for the backing queue
         * @param merger        merge strategy to use when conflation occurs
         * @param appenderListenerSupplier  supplier acting as appender listener factory
         * @param pollerListenerSupplier    supplier acting as poller listener factory
         * @return a new {@link MergeConflationQueue} instance
         */
        MergeConflationQueue<K,V> mergeQueue(Supplier<? extends Queue<Object>> queueFactory,
                                             Merger<? super K, V> merger,
                                             Supplier<? extends AppenderListener<? super K, ? super V>> appenderListenerSupplier,
                                             Supplier<? extends PollerListener<? super K, ? super V>> pollerListenerSupplier);
    }

    /**
     * Default builder for the case that no conflation keys are declared.
     *
     * @param <K> the conflation key type
     * @param <V> the value type
     */
    static class DefaultBuilder<K,V> extends ConflationQueueBuilderImpl<K,V> {
        DefaultBuilder() {
            super(new CqFactory<K, V>() {
                @Override
                public AtomicConflationQueue<K, V> atomicQueue(Supplier<? extends Queue<Object>> queueFactory,
                                                               Supplier<? extends AppenderListener<? super K, ? super V>> appenderListenerSupplier,
                                                               Supplier<? extends PollerListener<? super K, ? super V>> pollerListenerSupplier) {
                    return new AtomicConflationQueue<>(queueFactory, appenderListenerSupplier.get(), pollerListenerSupplier.get());
                }

                @Override
                public EvictConflationQueue<K, V> evictQueue(Supplier<? extends Queue<Object>> queueFactory,
                                                             Supplier<? extends AppenderListener<? super K, ? super V>> appenderListenerSupplier,
                                                             Supplier<? extends PollerListener<? super K, ? super V>> pollerListenerSupplier) {
                    return new EvictConflationQueue<>(queueFactory, appenderListenerSupplier, pollerListenerSupplier);
                }

                @Override
                public MergeConflationQueue<K, V> mergeQueue(Supplier<? extends Queue<Object>> queueFactory,
                                                             Merger<? super K, V> merger,
                                                             Supplier<? extends AppenderListener<? super K, ? super V>> appenderListenerSupplier,
                                                             Supplier<? extends PollerListener<? super K, ? super V>> pollerListenerSupplier) {
                    return new MergeConflationQueue<>(queueFactory, merger, appenderListenerSupplier, pollerListenerSupplier);
                }
            });
        }
    }

    /**
     * Builder for the case that an entry map factory has been provided.
     *
     * @param <K> the conflation key type
     * @param <V> the value type
     */
    static class EntryMapFactoryBuilder<K,V> extends ConflationQueueBuilderImpl<K,V> {
        EntryMapFactoryBuilder(final Supplier<? extends Map<Object,Object>> entryMapFactory) {
            super(new CqFactory<K, V>() {
                @Override
                public AtomicConflationQueue<K, V> atomicQueue(Supplier<? extends Queue<Object>> queueFactory,
                                                               Supplier<? extends AppenderListener<? super K, ? super V>> appenderListenerSupplier,
                                                               Supplier<? extends PollerListener<? super K, ? super V>> pollerListenerSupplier) {
                    return new AtomicConflationQueue<>(queueFactory, entryMapFactory, appenderListenerSupplier.get(), pollerListenerSupplier.get());
                }

                @Override
                public EvictConflationQueue<K, V> evictQueue(Supplier<? extends Queue<Object>> queueFactory,
                                                             Supplier<? extends AppenderListener<? super K, ? super V>> appenderListenerSupplier,
                                                             Supplier<? extends PollerListener<? super K, ? super V>> pollerListenerSupplier) {
                    return new EvictConflationQueue<>(queueFactory, entryMapFactory, appenderListenerSupplier, pollerListenerSupplier);
                }

                @Override
                public MergeConflationQueue<K, V> mergeQueue(Supplier<? extends Queue<Object>> queueFactory,
                                                             Merger<? super K, V> merger,
                                                             Supplier<? extends AppenderListener<? super K, ? super V>> appenderListenerSupplier,
                                                             Supplier<? extends PollerListener<? super K, ? super V>> pollerListenerSupplier) {
                    return new MergeConflationQueue<>(queueFactory, entryMapFactory, merger, appenderListenerSupplier, pollerListenerSupplier);
                }
            });
        }
    }

    /**
     * Builder for enum conflation key types.
     *
     * @param <K> the conflation key type
     * @param <V> the value type
     */
    static class EnumKeyBuilder<K extends Enum<K>,V> extends ConflationQueueBuilderImpl<K,V> {
        EnumKeyBuilder(final Class<K> enumConflationKeyClass) {
            super(new CqFactory<K, V>() {
                @Override
                public AtomicConflationQueue<K, V> atomicQueue(Supplier<? extends Queue<Object>> queueFactory,
                                                               Supplier<? extends AppenderListener<? super K, ? super V>> appenderListenerSupplier,
                                                               Supplier<? extends PollerListener<? super K, ? super V>> pollerListenerSupplier) {
                    return AtomicConflationQueue.forEnumConflationKey(queueFactory, enumConflationKeyClass, appenderListenerSupplier.get(), pollerListenerSupplier.get());
                }

                @Override
                public EvictConflationQueue<K, V> evictQueue(Supplier<? extends Queue<Object>> queueFactory,
                                                             Supplier<? extends AppenderListener<? super K, ? super V>> appenderListenerSupplier,
                                                             Supplier<? extends PollerListener<? super K, ? super V>> pollerListenerSupplier) {
                    return EvictConflationQueue.forEnumConflationKey(queueFactory, enumConflationKeyClass, appenderListenerSupplier, pollerListenerSupplier);
                }

                @Override
                public MergeConflationQueue<K, V> mergeQueue(Supplier<? extends Queue<Object>> queueFactory,
                                                             Merger<? super K, V> merger,
                                                             Supplier<? extends AppenderListener<? super K, ? super V>> appenderListenerSupplier,
                                                             Supplier<? extends PollerListener<? super K, ? super V>> pollerListenerSupplier) {
                    return MergeConflationQueue.forEnumConflationKey(queueFactory, merger, enumConflationKeyClass, appenderListenerSupplier, pollerListenerSupplier);
                }
            });
        }
    }

    /**
     * Builder for the case that conflation keys are declared.
     *
     * @param <K> the conflation key type
     * @param <V> the value type
     */
    static class DeclaredKeysBuilder<K,V> extends ConflationQueueBuilderImpl<K,V> {
        DeclaredKeysBuilder(final List<? extends K> allConflationKeys) {
            super(new CqFactory<K, V>() {
                @Override
                public AtomicConflationQueue<K, V> atomicQueue(Supplier<? extends Queue<Object>> queueFactory,
                                                               Supplier<? extends AppenderListener<? super K, ? super V>> appenderListenerSupplier,
                                                               Supplier<? extends PollerListener<? super K, ? super V>> pollerListenerSupplier) {
                    return new AtomicConflationQueue<>(queueFactory, allConflationKeys, appenderListenerSupplier.get(), pollerListenerSupplier.get());
                }

                @Override
                public EvictConflationQueue<K, V> evictQueue(Supplier<? extends Queue<Object>> queueFactory,
                                                             Supplier<? extends AppenderListener<? super K, ? super V>> appenderListenerSupplier,
                                                             Supplier<? extends PollerListener<? super K, ? super V>> pollerListenerSupplier) {
                    return new EvictConflationQueue<>(queueFactory, allConflationKeys, appenderListenerSupplier, pollerListenerSupplier);
                }

                @Override
                public MergeConflationQueue<K, V> mergeQueue(Supplier<? extends Queue<Object>> queueFactory,
                                                             Merger<? super K, V> merger,
                                                             Supplier<? extends AppenderListener<? super K, ? super V>> appenderListenerSupplier,
                                                             Supplier<? extends PollerListener<? super K, ? super V>> pollerListenerSupplier) {
                    return new MergeConflationQueue<>(queueFactory, merger, allConflationKeys, appenderListenerSupplier, pollerListenerSupplier);
                }
            });
        }
    }

    /**
     * Builder for {@link EvictConflationQueue}.
     */
    class EvictConflationQueueBuilder implements ExchangeConflationQueueBuilder<K,V> {
        @Override
        public EvictConflationQueueBuilder withAppenderListener(final Supplier<? extends AppenderListener<? super K, ? super V>> listenerSupplier) {
            ConflationQueueBuilderImpl.this.withAppenderListener(listenerSupplier);
            return this;
        }

        @Override
        public EvictConflationQueueBuilder withPollerListener(final Supplier<? extends PollerListener<? super K, ? super V>> listenerSupplier) {
            ConflationQueueBuilderImpl.this.withPollerListener(listenerSupplier);
            return this;
        }

        @Override
        public ExchangeConflationQueue<K, V> build(final Supplier<? extends Queue<Object>> queueFactory) {
            return cqFactory.evictQueue(queueFactory, appenderListenerSupplier, pollerListenerSupplier);
        }
    }

    /**
     * Builder for {@link MergeConflationQueue}.
     */
    class MergeConflationQueueBuilder implements ExchangeConflationQueueBuilder<K,V> {
        private final Merger<? super K, V> merger;
        MergeConflationQueueBuilder(final Merger<? super K, V> merger) {
            this.merger = Objects.requireNonNull(merger);
        }
        @Override
        public MergeConflationQueueBuilder withAppenderListener(final Supplier<? extends AppenderListener<? super K, ? super V>> listenerSupplier) {
            ConflationQueueBuilderImpl.this.withAppenderListener(listenerSupplier);
            return this;
        }

        @Override
        public MergeConflationQueueBuilder withPollerListener(final Supplier<? extends PollerListener<? super K, ? super V>> listenerSupplier) {
            ConflationQueueBuilderImpl.this.withPollerListener(listenerSupplier);
            return this;
        }

        @Override
        public MergeConflationQueue<K, V> build(final Supplier<? extends Queue<Object>> queueFactory) {
            return cqFactory.mergeQueue(queueFactory, merger, appenderListenerSupplier, pollerListenerSupplier);
        }


    }
}
