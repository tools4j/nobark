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

/**
 * Merger strategy passed to some conflation queues upon construction to support merging of values when conflation
 * occurs.
 *
 * @param <K> the type of the conflation key
 * @param <V> the type of values in the queue
 */
@FunctionalInterface
public interface Merger<K,V> {
    /**
     * Method called to merge two values;  the merged value is returned.
     *
     * @param conflationKey the conflation key
     * @param olderValue    the older value that was already present in the queue
     * @param newValue      the newer value that is currently being added to the queue
     * @return  the merged value; can be {@code newValue} itself but should not be {@code olderValue} as this instance
     *          is returned by the enqueue method and may be reused by the producer
     */
    V merge(K conflationKey, V olderValue, V newValue);
}
