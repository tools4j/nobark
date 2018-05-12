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
public interface AppenderListener<K,V> {
    enum Conflation {
        UNCONFLATED,
        EVICTED,
        MERGED
    }

    void enqueued(ConflationQueue<? extends K, ? extends V> queue, K key, V newValue, V oldValue, Conflation conflation);

    AppenderListener<Object,Object> NOOP = (q, k, v, o, c) -> {};

    static <K,V> AppenderListener<K,V> threadLocal(final Supplier<? extends AppenderListener<K,V>> listenerSupplier) {
        final ThreadLocal<AppenderListener<K,V>> threadLocal = ThreadLocal.withInitial(listenerSupplier);
        return (q,k,v,o,c) -> threadLocal.get().enqueued(q,k,v,o,c);
    }

    static <L extends AppenderListener<?,?>> Supplier<L> threadLocalSupplier(final Supplier<L> listenerSupplier) {
        return ThreadLocal.withInitial(listenerSupplier)::get;
    }
}
