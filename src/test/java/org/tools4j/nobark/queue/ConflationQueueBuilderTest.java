/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2022 nobark (tools4j), Marco Terzer, Anton Anufriev
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

import org.junit.Test;

import java.util.concurrent.ConcurrentLinkedDeque;

import static org.junit.Assert.assertNotNull;

public class ConflationQueueBuilderTest {

    enum Key {
        Yello, Blue, Pink
    }

    @Test
    public void atomicConflationQueue() {
        final ConflationQueue<String, Integer> q1 = ConflationQueueBuilder.builder(String.class, Integer.class)
                .withPollerListener(() -> PollerListener.NOOP)
                .withAppenderListener(() -> AppenderListener.NOOP)
                .build(ConcurrentLinkedDeque::new);
        final ConflationQueue<String, Integer> q2 = ConflationQueueBuilder.<String, Integer>builder()
                .withPollerListener(() -> PollerListener.NOOP)
                .withAppenderListener(() -> AppenderListener.NOOP)
                .build(ConcurrentLinkedDeque::new);
        final ConflationQueue<Integer, String> q3 = ConflationQueueBuilder.<Integer, String>declareAllConflationKeys(1, 2, 3, 4)
                .withPollerListener(() -> PollerListener.NOOP)
                .withAppenderListener(() -> AppenderListener.NOOP)
                .build(ConcurrentLinkedDeque::new);
        final ConflationQueue<Key, Integer> q4 = ConflationQueueBuilder.forEnumConflationKey(Key.class, Integer.class)
                .withPollerListener(() -> PollerListener.NOOP)
                .withAppenderListener(() -> AppenderListener.NOOP)
                .build(ConcurrentLinkedDeque::new);
        assertNotNull(q1);
        assertNotNull(q2);
        assertNotNull(q3);
        assertNotNull(q4);
    }

    @Test
    public void exchangeConflationQueue() {
        final ExchangeConflationQueue<String, Integer> q1 = ConflationQueueBuilder.builder(String.class, Integer.class)
                .withPollerListener(() -> PollerListener.NOOP)
                .withAppenderListener(() -> AppenderListener.NOOP)
                .withExchangeValueSupport()
                .build(ConcurrentLinkedDeque::new);
        final ExchangeConflationQueue<String, Integer> q2 = ConflationQueueBuilder.<String, Integer>builder()
                .withExchangeValueSupport()
                .withPollerListener(() -> PollerListener.NOOP)
                .withAppenderListener(() -> AppenderListener.NOOP)
                .build(ConcurrentLinkedDeque::new);
        final ExchangeConflationQueue<Integer, String> q3 = ConflationQueueBuilder.<Integer, String>declareAllConflationKeys(1, 2, 3, 4)
                .withExchangeValueSupport()
                .withPollerListener(() -> PollerListener.NOOP)
                .withAppenderListener(() -> AppenderListener.NOOP)
                .build(ConcurrentLinkedDeque::new);
        final ExchangeConflationQueue<Key, Integer> q4 = ConflationQueueBuilder.forEnumConflationKey(Key.class, Integer.class)
                .withPollerListener(() -> PollerListener.NOOP)
                .withExchangeValueSupport()
                .withAppenderListener(() -> AppenderListener.NOOP)
                .build(ConcurrentLinkedDeque::new);
        assertNotNull(q1);
        assertNotNull(q2);
        assertNotNull(q3);
        assertNotNull(q4);
    }

    @Test
    public void mergeConflationQueue() {
        final Merger<String, Integer> siMerger = (key, first, second) -> second;
        final Merger<Integer, String> isMerger = (key, first, second) -> second;
        final Merger<Key, Integer> kiMerger = (key, first, second) -> second;
        final ExchangeConflationQueue<String, Integer> q1 = ConflationQueueBuilder.builder(String.class, Integer.class)
                .withPollerListener(() -> PollerListener.NOOP)
                .withAppenderListener(() -> AppenderListener.NOOP)
                .withMerging(siMerger)
                .build(ConcurrentLinkedDeque::new);
        final ExchangeConflationQueue<String, Integer> q2 = ConflationQueueBuilder.<String, Integer>builder()
                .withMerging(siMerger)
                .withPollerListener(() -> PollerListener.NOOP)
                .withAppenderListener(() -> AppenderListener.NOOP)
                .build(ConcurrentLinkedDeque::new);
        final ExchangeConflationQueue<Integer, String> q3 = ConflationQueueBuilder.<Integer, String>declareAllConflationKeys(1, 2, 3, 4)
                .withMerging(isMerger)
                .withPollerListener(() -> PollerListener.NOOP)
                .withAppenderListener(() -> AppenderListener.NOOP)
                .build(ConcurrentLinkedDeque::new);
        final ExchangeConflationQueue<Key, Integer> q4 = ConflationQueueBuilder.forEnumConflationKey(Key.class, Integer.class)
                .withPollerListener(() -> PollerListener.NOOP)
                .withMerging(kiMerger)
                .withAppenderListener(() -> AppenderListener.NOOP)
                .build(ConcurrentLinkedDeque::new);
        assertNotNull(q1);
        assertNotNull(q2);
        assertNotNull(q3);
        assertNotNull(q4);
    }
}
