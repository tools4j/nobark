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

import java.util.Map;
import java.util.Queue;
import java.util.function.Supplier;

/**
 * Helper class dealing with unsafe casts necessary for factories of objects whose generic types are not known to the
 * factory provider.
 */
final class Factories {

    /**
     * Encapsulates the unsafe cast necessary when creating a queue with a factory that is unaware of the value type of
     * the queue.
     *
     * @param factory the queue factory
     * @param <V> the queue value type
     * @return a typed queue
     */
    static <V> Queue<V> createQueue(final Supplier<? extends Queue<Object>> factory) {
        //NOTE: casting a createQueue that takes objects to a specific typed createQueue is fine as long as we only add those typed
        //      values to the createQueue
        @SuppressWarnings({"unchecked"})
        final Queue<V> typed = (Queue<V>)factory.get();
        return typed;
    }

    /**
     * Encapsulates the unsafe cast necessary when creating a map with a factory that is unaware of the key/value types
     * of the map.
     *
     * @param factory the map factory
     * @param <K> the map key type
     * @param <V> the map value type
     * @return a typed map
     */
    static <K,V> Map<K,V> createMap(final Supplier<? extends Map<Object,Object>> factory) {
        //NOTE: casting a map that takes object keys/values to a specific typed map is fine as long as we only put those
        //      typed keys and values to the map
        @SuppressWarnings({"unchecked"})
        final Map<K,V> typed = (Map<K,V>)factory.get();
        return typed;
    }

    private Factories() {
        throw new RuntimeException("No Factories for you!");
    }
}
