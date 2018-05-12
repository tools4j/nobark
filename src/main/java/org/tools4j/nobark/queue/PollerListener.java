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

import java.util.function.Supplier;

@FunctionalInterface
public interface PollerListener<K,V> {
    void polled(ConflationQueue<? extends K, ? extends V> queue, K key, V value);

    default void polledButFoundEmpty(final ConflationQueue<? extends K, ? extends V> queue) {
        polled(queue, null, null);
    }

    PollerListener<Object,Object> NOOP = (q, k, v) -> {};

    static <K,V> PollerListener<K,V> threadLocal(final Supplier<? extends PollerListener<K,V>> listenerSupplier) {
        final ThreadLocal<PollerListener<K,V>> threadLocal = ThreadLocal.withInitial(listenerSupplier);
        return (q,k,v) -> threadLocal.get().polled(q,k,v);
    }

    static <L extends PollerListener<?,?>> Supplier<L> threadLocalSupplier(final Supplier<L> listenerSupplier) {
        return ThreadLocal.withInitial(listenerSupplier)::get;
    }
}
