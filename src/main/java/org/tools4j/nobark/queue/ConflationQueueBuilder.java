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

import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.function.Supplier;

public interface ConflationQueueBuilder<K,V> {

    static <K,V> ConflationQueueBuilder<K,V> builder() {
        return new ConflationQueueBuilderImpl.DefaultBuilder<>();
    }

    static <K,V> ConflationQueueBuilder<K,V> builder(Class<K> keyClass, Class<V> valueClass) {
        return new ConflationQueueBuilderImpl.DefaultBuilder<>();
    }

    @SafeVarargs
    static <K,V> ConflationQueueBuilder<K,V> declareAllConflationKeys(K... allConflationKeys) {
        //NOTE: this creates garbage
        return declareAllConflationKeys(Arrays.asList(allConflationKeys));
    }

    static <K,V> ConflationQueueBuilder<K,V> declareAllConflationKeys(List<K> allConflationKeys) {
        return new ConflationQueueBuilderImpl.DeclaredKeysBuilder<>(allConflationKeys);
    }

    static <K,V> ConflationQueueBuilder<K,V> declareAllConflationKeys(List<K> allConflationKeys, Class<V> valuetType) {
        return new ConflationQueueBuilderImpl.DeclaredKeysBuilder<>(allConflationKeys);
    }

    static <K extends Enum<K>,V> ConflationQueueBuilder<K,V> forEnumConflationKey(Class<K> conflationKeyClass) {
        return new ConflationQueueBuilderImpl.EnumKeyBuilder<>(conflationKeyClass);
    }

    static <K extends Enum<K>,V> ConflationQueueBuilder<K,V> forEnumConflationKey(Class<K> conflationKeyClass, Class<V> valueType) {
        return new ConflationQueueBuilderImpl.EnumKeyBuilder<>(conflationKeyClass);
    }

    ConflationQueueBuilder<K,V> withAppenderListener(Supplier<? extends AppenderListener<? super K, ? super V>> listenerSupplier);
    ConflationQueueBuilder<K,V> withPollerListener(Supplier<? extends PollerListener<? super K, ? super V>> listenerSupplier);

    ExchangeConflationQueueBuilder<K,V> withExchangeValueSupport();
    ExchangeConflationQueueBuilder<K,V> withMerging(Merger<? super K, V> merger);

    ConflationQueue<K,V> build(Supplier<? extends Queue<Object>> queueFactory);

    interface ExchangeConflationQueueBuilder<K,V> {
        ExchangeConflationQueueBuilder<K,V> withAppenderListener(Supplier<? extends AppenderListener<? super K, ? super V>> listenerSupplier);
        ExchangeConflationQueueBuilder<K,V> withPollerListener(Supplier<? extends PollerListener<? super K, ? super V>> listenerSupplier);

        ExchangeConflationQueue<K,V> build(Supplier<? extends Queue<Object>> queueFactory);
    }
}
