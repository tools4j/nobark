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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.function.Supplier;

/**
 * Builder for the different types of conflation queues serving as alternative to the queue constructors;  a builder is
 * returned by the static methods of this interface.
 *
 * @param <K> the type of the conflation key
 * @param <V> the type of elements in the queue
 */
public interface ConflationQueueBuilder<K,V> {

    /**
     * Creates an initial builder;  use this method only when conflation keys are not known in advance
     *
     * @param <K> the type of the conflation key
     * @param <V> the type of elements in the queue
     * @return a new builder
     */
    static <K,V> ConflationQueueBuilder<K,V> builder() {
        return new ConflationQueueBuilderImpl.DefaultBuilder<>();
    }

    /**
     * Creates an initial builder;  use this method only when conflation keys are not known in advance.
     *
     * @param keyType   the type of the conflation key, used only to infer the generic type of the builder
     * @param valueType the type of elements in the queue, used only to infer the generic type of the builder
     * @param <K> the type of the conflation key
     * @param <V> the type of elements in the queue
     * @return a new builder
     */
    static <K,V> ConflationQueueBuilder<K,V> builder(@SuppressWarnings("unused") final Class<K> keyType,
                                                     @SuppressWarnings("unused") final Class<V> valueType) {
        return new ConflationQueueBuilderImpl.DefaultBuilder<>();
    }

    /**
     * Creates an initial builder;  use this method only when conflation keys are not known in advance.  If multiple
     * producers are used, the map returned by the given map factory should be thread-safe.
     *
     * @param entryMapFactory the factory to create the map that manages entries per conflation key
     * @param <K> the type of the conflation key
     * @param <V> the type of elements in the queue
     * @return a new builder
     */
    static <K,V> ConflationQueueBuilder<K,V> builder(final Supplier<? extends Map<Object,Object>> entryMapFactory) {
        return new ConflationQueueBuilderImpl.EntryMapFactoryBuilder<>(entryMapFactory);
    }

    /**
     * Creates an initial builder;  use this method only when conflation keys are not known in advance.  If multiple
     * producers are used, the map returned by the given map factory should be thread-safe.
     *
     * @param keyType   the type of the conflation key, used only to infer the generic type of the builder
     * @param valueType the type of elements in the queue, used only to infer the generic type of the builder
     * @param entryMapFactory the factory to create the map that manages entries per conflation key
     * @param <K> the type of the conflation key
     * @param <V> the type of elements in the queue
     * @return a new builder
     */
    static <K,V> ConflationQueueBuilder<K,V> builder(@SuppressWarnings("unused") final Class<K> keyType,
                                                     @SuppressWarnings("unused") final Class<V> valueType,
                                                     final Supplier<? extends Map<Object,Object>> entryMapFactory) {
        return new ConflationQueueBuilderImpl.EntryMapFactoryBuilder<>(entryMapFactory);
    }

    /**
     * Creates an initial builder given all conflation keys (exhaustive!).
     *
     * @param allConflationKeys an exhaustive list of all conflation keys that will ever be used for the queue
     * @param <K> the type of the conflation key
     * @param <V> the type of elements in the queue
     * @return a new builder
     */
    @SafeVarargs
    static <K,V> ConflationQueueBuilder<K,V> declareAllConflationKeys(final K... allConflationKeys) {
        //NOTE: this creates garbage
        return declareAllConflationKeys(Arrays.asList(allConflationKeys));
    }

    /**
     * Creates an initial builder given all conflation keys (exhaustive!).
     *
     * @param allConflationKeys an exhaustive list of all conflation keys that will ever be used for the queue
     * @param <K> the type of the conflation key
     * @param <V> the type of elements in the queue
     * @return a new builder
     */
    static <K,V> ConflationQueueBuilder<K,V> declareAllConflationKeys(final List<K> allConflationKeys) {
        return new ConflationQueueBuilderImpl.DeclaredKeysBuilder<>(allConflationKeys);
    }

    /**
     * Creates an initial builder given all conflation keys (exhaustive!).
     *
     * @param allConflationKeys an exhaustive list of all conflation keys that will ever be used for the queue
     * @param valueType         the type of elements in the queue, used only to infer the generic type of the builder
     * @param <K> the type of the conflation key
     * @param <V> the type of elements in the queue
     * @return a new builder
     */
    static <K,V> ConflationQueueBuilder<K,V> declareAllConflationKeys(final List<K> allConflationKeys,
                                                                      @SuppressWarnings("unused") final Class<V> valueType) {
        return new ConflationQueueBuilderImpl.DeclaredKeysBuilder<>(allConflationKeys);
    }

    /**
     * Creates an initial builder given the enum conflation key class.
     *
     * @param keyType   the type of the conflation key
     * @param <K> the type of the conflation key
     * @param <V> the type of elements in the queue
     * @return a new builder
     */
    static <K extends Enum<K>,V> ConflationQueueBuilder<K,V> forEnumConflationKey(final Class<K> keyType) {
        return new ConflationQueueBuilderImpl.EnumKeyBuilder<>(keyType);
    }

    /**
     * Creates an initial builder given the enum conflation key class.
     *
     * @param keyType   the type of the conflation key
     * @param valueType the type of elements in the queue, used only to infer the generic type of the builder
     * @param <K> the type of the conflation key
     * @param <V> the type of elements in the queue
     * @return a new builder
     */
    static <K extends Enum<K>,V> ConflationQueueBuilder<K,V> forEnumConflationKey(final Class<K> keyType,
                                                                                  @SuppressWarnings("unused") final Class<V> valueType) {
        return new ConflationQueueBuilderImpl.EnumKeyBuilder<>(keyType);
    }

    /**
     * Register a listener as created by the specified supplier when creating queue appenders.  The created listeners
     * must be thread safe for multi-producer queues, e.g. consider using
     * {@link AppenderListener#threadLocalSupplier(Supplier)}.
     *
     * @param listenerSupplier  a listener supplier acting as listener factory
     * @return the builder
     */
    ConflationQueueBuilder<K,V> withAppenderListener(Supplier<? extends AppenderListener<? super K, ? super V>> listenerSupplier);

    /**
     * Register a listener as created by the specified supplier when creating queue pollers.  The created listeners
     * must be thread safe for multi-consumer queues, e.g. consider using
     * {@link PollerListener#threadLocalSupplier(Supplier)}.
     *
     * @param listenerSupplier  a listener supplier acting as listener factory
     * @return the builder
     */
    ConflationQueueBuilder<K,V> withPollerListener(Supplier<? extends PollerListener<? super K, ? super V>> listenerSupplier);

    /**
     * Switch builder mode to create a conflation queue that allows exchanging of elements between the consumer and the
     * producer, that is, the builder will now create a {@link ExchangeConflationQueue}.
     *
     * @return a builder for an {@link ExchangeConflationQueue}, more specifically an {@link EvictConflationQueue}
     * @see ExchangeConflationQueue
     * @see EvictConflationQueue
     * @see #withMerging(Merger)
     */
    ExchangeConflationQueueBuilder<K,V> withExchangeValueSupport();

    /**
     * Switch builder mode to create a conflation queue that supports merging of old and new values when conflation
     * occurs, that is, the builder will now create a {@link MergeConflationQueue}.
     *
     * @param merger merge strategy to use when conflation occurs
     * @return a builder for an {@link ExchangeConflationQueue}, more specifically a {@link MergeConflationQueue}
     * @see ExchangeConflationQueue
     * @see MergeConflationQueue
     * @see #withExchangeValueSupport()
     */
    ExchangeConflationQueueBuilder<K,V> withMerging(Merger<? super K, V> merger);

    /**
     * Builds and returns a new conflation queue instance using the given queue factory for the backing queue.  The
     * backing queue determines whether single or multiple producers and consumers are supported.
     *
     * @param queueFactory the factory to create the backing queue
     * @return a new conflation queue instance
     */
    ConflationQueue<K,V> build(Supplier<? extends Queue<Object>> queueFactory);

    /**
     * Builder for {@link ExchangeConflationQueue} subtypes.
     *
     * @param <K> the type of the conflation key
     * @param <V> the type of elements in the queue
     */
    interface ExchangeConflationQueueBuilder<K,V> {
        /**
         * Register a listener as created by the specified supplier when creating queue appenders.  The created listeners
         * must be thread safe for multi-producer queues, e.g. consider using
         * {@link AppenderListener#threadLocalSupplier(Supplier)}.
         *
         * @param listenerSupplier  a listener supplier acting as listener factory
         * @return the builder
         */
        ExchangeConflationQueueBuilder<K,V> withAppenderListener(Supplier<? extends AppenderListener<? super K, ? super V>> listenerSupplier);

        /**
         * Register a listener as created by the specified supplier when creating queue pollers.  The created listeners
         * must be thread safe for multi-consumer queues, e.g. consider using
         * {@link PollerListener#threadLocalSupplier(Supplier)}.
         *
         * @param listenerSupplier  a listener supplier acting as listener factory
         * @return the builder
         */
        ExchangeConflationQueueBuilder<K,V> withPollerListener(Supplier<? extends PollerListener<? super K, ? super V>> listenerSupplier);

        /**
         * Builds and returns a new exchange conflation queue instance using the given queue factory for the backing
         * queue.  The backing queue determines whether single or multiple producers and consumers are supported.
         *
         * @param queueFactory the factory to create the backing queue
         * @return a new exchange conflation queue instance
         */
        ExchangeConflationQueue<K,V> build(Supplier<? extends Queue<Object>> queueFactory);
    }
}
