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

import org.HdrHistogram.Histogram;
import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BiConsumer;

public class ConflationQueuePerfTest {

    public static void main(final String[] args) throws InterruptedException {

        final String sentinelKey = "key_sentinel";

        final int keyCount = 20*50;//markets x symbols
        final long totalUpdates = 2000000;
        //final double frequencyPerSecondAndKey = 500;
        final double frequencyPerSecondAndKey = 200;
        //final double producerSleepNanos = 0;
        final double producerSleepNanos = 1e9 / frequencyPerSecondAndKey;

        final List<String> keys = new ArrayList<>(keyCount + 1);
        for (int i = 0; i < keyCount; i++) keys.add("key_" + i);
        keys.add(sentinelKey);

        //final ConflationQueue<String, PriceEntry> conflationQueue;
        //conflationQueue = new AtomicConflationQueue<>(() -> new ManyToOneConcurrentArrayQueue<>(keyCount), keys);
        final ExchangeConflationQueue<String, PriceEntry> conflationQueue;
        //conflationQueue = new EvictConflationQueue<>(() -> new ManyToOneConcurrentArrayQueue<>(keyCount), keys);
        conflationQueue = new MergeConflationQueue<>(() -> new ManyToOneConcurrentArrayQueue<>(keyCount), new PriceEntry.Merger(), keys);

        ConflationQueueBuilder
                .<String,PriceEntry>declareAllConflationKeys(keys)
                .withMerging(new PriceEntry.Merger())
                .build(() -> new ManyToOneConcurrentArrayQueue<>(keyCount));

        final Histogram enqueueLatencyHistogram = new Histogram(1, TimeUnit.SECONDS.toNanos(100), 3);

        final Thread consumerThread = new Thread(() -> {
            //final ConflationQueue.Poller<String, PriceEntry> poller = conflationQueue.poller();
            final ExchangeConflationQueue.ExchangePoller<String, PriceEntry> poller = conflationQueue.poller();
            final Histogram mergedEntriesHistogram = new Histogram(1, totalUpdates, 3);
            final Histogram inQueueLatencyHistogram = new Histogram(1, TimeUnit.SECONDS.toNanos(100), 3);
            final Histogram lastUpdateLatencyHistogram = new Histogram(1, TimeUnit.SECONDS.toNanos(100), 3);
            final Histogram totalLatencyHistogramInQueue = new Histogram(1, TimeUnit.SECONDS.toNanos(100), 3);
            final Histogram totalLatencyHistogramOverall = new Histogram(1, TimeUnit.SECONDS.toNanos(100), 3);

            final String[] keyHolder = {null};
            final BiConsumer<String, Object> keyFetcher = (k,v) -> keyHolder[0] = k;

            long nonConflated = 0;
            long conflated = 0;
            long conflatedDuringWarmup = 0;
            long receivedCount = 0;
            long warmUp = 200000;

            PriceEntry priceEntry = new PriceEntry();
            final long timeStartMillis = System.currentTimeMillis();
            while (true) {
                //final PriceEntry polledEntry = poller.poll(keyFetcher);
                final PriceEntry polledEntry = poller.poll(keyFetcher, priceEntry);
                if (polledEntry != null) {
                    if (polledEntry.getLast() == totalUpdates) break;
                    receivedCount++;
                    if (receivedCount > warmUp) {
                        final long receiveTime = System.nanoTime();
                        final long lastUpdateLatency = receiveTime - polledEntry.getTime();
                        final long inQueueLatency = polledEntry.getLatest() - polledEntry.getEarliest();
                        final long mergeCount = polledEntry.getCount();
                        if (mergeCount > 1) {
                            mergedEntriesHistogram.recordValue(mergeCount);
                            inQueueLatencyHistogram.recordValue(inQueueLatency);
                            totalLatencyHistogramInQueue.recordValue(inQueueLatency + lastUpdateLatency);
                        } else {
                            nonConflated++;
                        }
                        conflated += polledEntry.getCount() - 1;
                        lastUpdateLatencyHistogram.recordValue(lastUpdateLatency);
                        totalLatencyHistogramOverall.recordValue(inQueueLatency + lastUpdateLatency);
                    } else {
                        conflatedDuringWarmup += polledEntry.getCount() - 1;
                    }
                    priceEntry = polledEntry;
                }
            }
            final long timeEndMillis = System.currentTimeMillis();
            final long totalMillis = timeEndMillis - timeStartMillis;

            printHistogram("Merge-count", 1, mergedEntriesHistogram);
            System.out.println();
            printHistogram("In-queue latencies (us)", 1000.0, inQueueLatencyHistogram);
            System.out.println();
            printHistogram("Total (in-queue) latencies (us)", 1000.0, totalLatencyHistogramInQueue);
            System.out.println();
            printHistogram("Last update latencies (us)", 1000.0, lastUpdateLatencyHistogram);
            System.out.println();
            printHistogram("Total (overall) latencies (us)", 1000.0, totalLatencyHistogramOverall);
            System.out.println();
            printHistogram("Enqueue latencies (us)", 1000.0, enqueueLatencyHistogram);

            System.out.println();
            System.out.println("Produced:");
            System.out.println("................. total : " + totalUpdates + " (" + ((1000 * totalUpdates) / totalMillis) + " per second)");
            System.out.println("................ warmup : " + (warmUp+conflatedDuringWarmup));
            System.out.println("............... counted : " + (totalUpdates-warmUp-conflatedDuringWarmup));
            System.out.println(".... enqueued w/o merge : " + (receivedCount-warmUp) + perc(receivedCount-warmUp, totalUpdates-warmUp-conflatedDuringWarmup));
            System.out.println("... enqueued with merge : " + conflated + perc(conflated, totalUpdates-warmUp-conflatedDuringWarmup));

            System.out.println();
            System.out.println("Consumed:");
            System.out.println(".............. received : " + receivedCount + " (" + ((1000 * receivedCount) / totalMillis) + " per second)");
            System.out.println("................ warmup : " + warmUp);
            System.out.println("............... counted : " + (receivedCount-warmUp));
            System.out.println("....... polled unmerged : " + nonConflated + perc(nonConflated, receivedCount-warmUp));
            System.out.println("......... polled merged : " + (receivedCount-warmUp-nonConflated) + perc(receivedCount-warmUp-nonConflated, receivedCount-warmUp)
                                                            + String.format(" consisting of %1.1f merged entries each on average", mergedEntriesHistogram.getMean()));
        });
        consumerThread.setDaemon(true);
        consumerThread.start();

        final ConflationQueue.Appender<String, PriceEntry> appender = conflationQueue.appender();

        PriceEntry priceEntry = new PriceEntry();
        final long timeStartNanos = System.nanoTime();
        for (int i = 0; i < totalUpdates; i++) {
            final int keyIndex = i % keyCount;//exclude sentinel key here
            final String key = keys.get(keyIndex);

            final long timeBefore;
            priceEntry.reset();
            priceEntry.setLast(i);
            priceEntry.setTime(timeBefore = System.nanoTime());

            final PriceEntry exchangedEntry = appender.enqueue(key, priceEntry);
            final long timeAfter = System.nanoTime();
            enqueueLatencyHistogram.recordValue(timeAfter - timeBefore);

            if (exchangedEntry != null) {
                priceEntry = exchangedEntry;
            } else {
                if (i >= keyCount && conflationQueue instanceof ExchangeConflationQueue) {
                    throw new IllegalStateException("should receive an entry back in exchange after first round");
                }
                priceEntry = new PriceEntry();
            }

            if (keyIndex == 0 & producerSleepNanos != 0) {
                final long timeExpectedNanos = (long)((i * producerSleepNanos) / keyCount);
                long time = System.nanoTime();
                while (time - timeStartNanos < timeExpectedNanos) {
                    LockSupport.parkNanos(Math.max(0, Math.min(time - timeStartNanos - timeExpectedNanos, (long)producerSleepNanos)));
                    time = System.nanoTime();
                }
            }
        }
        //send sentinel to tell consumer to stop, on it's own sentinel key to ensure that no conflation occurs to
        //ensure it is the last to come out of the queue
        priceEntry.reset();
        priceEntry.setLast(totalUpdates);
        appender.enqueue(sentinelKey, priceEntry);

        //wait for consumer now
        consumerThread.join();
    }

    private static void printHistogram(final String name, final double divide, final Histogram histogram) {
        System.out.println(name);
        System.out.printf("   min         = %1.3f\n", histogram.getMinValue() / divide);
        System.out.printf("   50 %%        = %1.3f\n", histogram.getValueAtPercentile(50) / divide);
        System.out.printf("   90 %%        = %1.3f\n", histogram.getValueAtPercentile(90) / divide);
        System.out.printf("   99 %%        = %1.3f\n", histogram.getValueAtPercentile(99) / divide);
        System.out.printf("   99.9 %%      = %1.3f\n", histogram.getValueAtPercentile(99.9) / divide);
        System.out.printf("   99.99 %%     = %1.3f\n", histogram.getValueAtPercentile(99.99) / divide);
        System.out.printf("   99.999 %%    = %1.3f\n", histogram.getValueAtPercentile(99.999) / divide);
        System.out.printf("   99.9999 %%   = %1.3f\n", histogram.getValueAtPercentile(99.9999) / divide);
        System.out.printf("   99.99999 %%  = %1.3f\n", histogram.getValueAtPercentile(99.99999) / divide);
        System.out.printf("   99.999999 %% = %1.3f\n", histogram.getValueAtPercentile(99.999999) / divide);
        System.out.printf("   max         = %1.3f\n", histogram.getMaxValue() / divide);
        System.out.printf("   mean / std  = %1.3f +/- %1.3f\n", histogram.getMean() / divide, histogram.getStdDeviation() / divide);
        System.out.printf("   count       = %d\n", histogram.getTotalCount());
    }

    private static String perc(final long fraction, final long total) {
        return String.format(" (%1.2f%%)", fraction/(total/100f));
    }

}