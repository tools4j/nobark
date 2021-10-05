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

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * A conflation queue variant that allows exchanging of elements between the consumer and the producer.  Naturally the
 * producer already sends values to the consumer;  with an exchange queue the consumer can pass values back to the
 * producer when polling values.  This can for instance be used to reduce garbage by circulating and reusing values
 * between producer and consumer.
 *
 * @param <K> the type of the conflation key
 * @param <V> the type of values in the queue
 */
public interface ExchangeConflationQueue<K,V> extends ConflationQueue<K,V> {
    @Override
    Appender<K,V> appender();

    @Override
    ExchangePoller<K,V> poller();

    /**
     * Poller object used by the consumer to poll values;  exchange poller adds functionality for exchanging values with
     * the producer during poll operations.
     *
     * @param <K> the type of the conflation key
     * @param <V> the type of values in the queue
     */
    @FunctionalInterface
    interface ExchangePoller<K, V> extends Poller<K,V> {
        /**
         * Polls the queue passing an unused value in exchange to the queue.  The given consumer is invoked with
         * conflation key and polled value if the queue was non-empty and the value is returned.  If the queue was
         * empty, null is returned.
         * <p>
         * If polling was successful, the exchange value will at some point be returned by an enqueue operation with
         * the same conflation key and can be reused by the producer.  The caller retains ownership of the exchange
         * value if polling failed and null was returned.
         *
         * @param consumer consumer for conflation key and polled value
         * @param  exchange a value offered in exchange for the polled value, to be returned by an enqueue operation
         * @return the polled value, or null if the queue was empty
         */
        V poll(BiConsumer<? super K, ? super V> consumer, V exchange);

        /**
         * Polls the queue passing an unused value in exchange to the queue.  Returns the polled value if any value was
         * present in the queue, or null if the queue was empty.
         * <p>
         * If polling was successful, the exchange value will at some point be returned by an enqueue operation with
         * the same conflation key and can be reused by the producer.  The caller retains ownership of the exchange
         * value if polling failed and null was returned.
         *
         * @param  exchange a value offered in exchange for the polled value, to be returned by an enqueue operation
         * @return the polled value, or null if the queue was empty
         */
        default V poll(final V exchange) {
            return poll((k,v) -> {}, exchange);
        }

        @Override
        default V poll(final BiConsumer<? super K, ? super V> consumer) {
            return poll(consumer, null);
        }
    }

    /**
     * Returns an {@link ExchangeConflationQueue} whose appender is guaranteed to never return null on enqueue.  If the
     * unsafe queue returns null on enqueue e.g. because the queue is empty and no exchange values can be retrieved yet,
     * the specified factory is used to create a value.
     *
     * @param unsafeQueue   the unsafe queue possibly returning null values on enqueue
     * @param valueFactory  the value factory used if the unsafe queue returned null when a value was enqueued
     * @param <K> the type of the conflation key
     * @param <V> the type of values in the queue
     * @return a null-safe version of the queue that never returns null values when enqueuing values
     */
    static <K,V> ExchangeConflationQueue<K, V> nullSafe(final ExchangeConflationQueue<K,V> unsafeQueue,
                                                        final Function<? super K, ? extends V> valueFactory) {
        Objects.requireNonNull(unsafeQueue);
        Objects.requireNonNull(valueFactory);
        return new ExchangeConflationQueue<K, V>() {
            final ThreadLocal<Appender<K,V>> appender = ThreadLocal.withInitial(() -> nullSafe(unsafeQueue.appender(), valueFactory));
            @Override
            public Appender<K, V> appender() {
                return appender.get();
            }

            @Override
            public ExchangePoller<K, V> poller() {
                return unsafeQueue.poller();
            }

            @Override
            public int size() {
                return unsafeQueue.size();
            }
        };
    }

    /**
     * Returns an {@link Appender} whose {@link Appender#enqueue(Object, Object) enqueue(..)} method is guaranteed to
     * never return null.  If the unsafe appender returns null on enqueue e.g. because the queue is empty and no
     * exchange values can be retrieved yet, the specified factory is used to create a value.
     *
     * @param unsafeAppender    the unsafe appender possibly returning null values on enqueue
     * @param valueFactory      the value factory used if the unsafe enqueuing operation returned null
     * @param <K> the type of the conflation key
     * @param <V> the type of values in the queue
     * @return a null-safe version of the appender that never returns null values when enqueuing values
     */
    static <K,V> Appender<K, V> nullSafe(final Appender<K,V> unsafeAppender,
                                         final Function<? super K, ? extends V> valueFactory) {
        Objects.requireNonNull(unsafeAppender);
        Objects.requireNonNull(valueFactory);
        return (k, v) -> {
            final V value = unsafeAppender.enqueue(k, v);
            return value == null ?
                    Objects.requireNonNull(valueFactory.apply(k), "value factory returned null") :
                    value;
        };
    }
}
