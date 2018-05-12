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

import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class MergeConflationQueueTest {

    private static final String KEY = "book1";

    private ExchangeConflationQueue.Appender<String, PriceEntry> appender;
    private ExchangeConflationQueue.Poller<String, PriceEntry> poller;

    @Before
    public void init() {
        final PriceEntry.Merger merger = new PriceEntry.Merger();
        final ExchangeConflationQueue<String, PriceEntry> conflationQueue = new MergeConflationQueue<>(
                () -> new ManyToOneConcurrentArrayQueue<>(100), merger
        );

        appender = conflationQueue.appender();
        poller = conflationQueue.poller();
    }

    @Test
    public void enqueueWithMerge() {

        PriceEntry updatablePriceEntry;
        updatablePriceEntry = new PriceEntry();
        updatablePriceEntry.reset().setLast(10);
        appender.enqueue(KEY, updatablePriceEntry);

        updatablePriceEntry = new PriceEntry();
        updatablePriceEntry.reset().setLast(5);
        updatablePriceEntry = appender.enqueue(KEY, updatablePriceEntry);

        updatablePriceEntry.reset().setLast(15);
        updatablePriceEntry = appender.enqueue(KEY, updatablePriceEntry);

        PriceEntry consumablePriceEntry = poller.poll();

        System.out.println(consumablePriceEntry);

        assertThat(KEY, is(KEY));
        assertThat(consumablePriceEntry.getLast(), is(15d));
        assertThat(consumablePriceEntry.getOpen(), is(10d));
        assertThat(consumablePriceEntry.getLow(), is(5d));
        assertThat(consumablePriceEntry.getHigh(), is(15d));
        assertThat(consumablePriceEntry.getClose(), is(15d));
    }

    @Test
    public void enqueueWithMerge_pollAfterFirstMerge() {
        PriceEntry updatablePriceEntry;
        PriceEntry consumablePriceEntry;

        updatablePriceEntry = new PriceEntry();
        updatablePriceEntry.reset().setLast(10);
        appender.enqueue(KEY, updatablePriceEntry);

        updatablePriceEntry = new PriceEntry();
        updatablePriceEntry.reset().setLast(5);
        updatablePriceEntry = appender.enqueue(KEY, updatablePriceEntry);

        consumablePriceEntry = poller.poll();
        System.out.println(consumablePriceEntry);

        assertThat(KEY, is(KEY));
        assertThat(consumablePriceEntry.getLast(), is(5d));
        assertThat(consumablePriceEntry.getOpen(), is(10d));
        assertThat(consumablePriceEntry.getLow(), is(5d));
        assertThat(consumablePriceEntry.getHigh(), is(10d));
        assertThat(consumablePriceEntry.getClose(), is(5d));

        updatablePriceEntry.reset().setLast(15);
        updatablePriceEntry = appender.enqueue(KEY, updatablePriceEntry);

        consumablePriceEntry = poller.poll();
        System.out.println(consumablePriceEntry);

        assertThat(KEY, is(KEY));
        assertThat(consumablePriceEntry.getLast(), is(15d));
        assertThat(consumablePriceEntry.getOpen(), is(15d));
        assertThat(consumablePriceEntry.getLow(), is(15d));
        assertThat(consumablePriceEntry.getHigh(), is(15d));
        assertThat(consumablePriceEntry.getClose(), is(15d));
    }

}