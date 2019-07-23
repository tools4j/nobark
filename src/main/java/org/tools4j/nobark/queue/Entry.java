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

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Package local immutable wrapper object used by the queue implementations;  holds conflation key and a value.
 *
 * @param <K> the conflation key type
 * @param <V> the value type
 */
final class Entry<K,V> {
    final K key;
    final AtomicReference<V> value;

    /**
     * Constructor with conflation key and value
     *
     * @param key   the conflation key
     * @param value the value
     */
    Entry(final K key, final V value) {
        this.key = Objects.requireNonNull(key);
        this.value = new AtomicReference<>(value);//nulls allowed for value
    }

    /**
     * Eagerly initializes a hash map using all provided keys and creating values with the given value factory.
     *
     * @param allKeys       a list with all conflation keys
     * @param valueFactory  the value factory
     * @param <K> the conflation key type
     * @param <V> the value type
     * @return the map initialized with new values for all keys
     */
    static <K,V> Map<K,Entry<K,V>> eagerlyInitialiseEntryMap(final List<? extends K> allKeys,
                                                             final Supplier<V> valueFactory) {
        final Map<K,Entry<K,V>> entryMap = new HashMap<>((int)Math.ceil(allKeys.size() / 0.75));
        //NOTE: we intentionally use index here to avoid iterator garbage
        for (int i = 0; i < allKeys.size(); i++) {
            final K key = allKeys.get(i);
            entryMap.put(key, new Entry<>(key, valueFactory.get()));
        }
        return entryMap;
    }

    /**
     * Eagerly initializes an enum map using all enum constants as keys and creating values with the given value factory.
     *
     * @param keyClass      the enum key class
     * @param valueFactory  the value factory
     * @param <K> the conflation key type
     * @param <V> the value type
     * @return the enum map initialized with new values for all key constants
     */
    static <K extends Enum<K>,V> Map<K,Entry<K,V>> eagerlyInitialiseEntryEnumMap(final Class<K> keyClass,
                                                                                 final Supplier<V> valueFactory) {
        final EnumMap<K, Entry<K,V>> entryMap = new EnumMap<>(keyClass);
        //NOTE: we create garbage when getting enum constants, but escape analysis most likely eliminates this
        for (final K key : keyClass.getEnumConstants()) {
            entryMap.put(key, new Entry<>(key, valueFactory.get()));
        }
        return entryMap;
    }
}
