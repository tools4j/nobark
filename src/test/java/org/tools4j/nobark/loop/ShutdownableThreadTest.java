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
package org.tools4j.nobark.loop;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.LongSupplier;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit test for {@link ShutdownableThread}.
 */
public class ShutdownableThreadTest {

    private static final Function<BooleanSupplier, Runnable> LOOP_WHILE_RUNNING = run -> () -> {
        while (run.getAsBoolean());
    };

    private static Function<BooleanSupplier, Runnable> loopUntil(final BooleanSupplier loopCondition) {
        return run -> () -> {
            while (loopCondition.getAsBoolean()) ;
        };
    }

    @Test
    public void shutdown() {
        //given
        final ShutdownableThread thread = ShutdownableThread.start(LOOP_WHILE_RUNNING, runShutdown -> () -> {}, Thread::new);
        assertFalse(thread.isShutdown());

        //when
        thread.shutdown();

        //then
        assertTrue(thread.isShutdown());

        //when
        thread.shutdown();
        thread.shutdown();

        //then
        assertTrue(thread.isShutdown());
    }

    @Test
    public void shutdownNow() {
        //given
        final ShutdownableThread thread = ShutdownableThread.start(LOOP_WHILE_RUNNING, LOOP_WHILE_RUNNING, Thread::new);

        //then
        assertFalse(thread.isTerminated());

        //when
        thread.shutdownNow();

        //then
        assertTrue(thread.isShutdown());

        //when
        thread.awaitTermination(5, TimeUnit.SECONDS);

        //then
        assertTrue(thread.isTerminated());

        //when
        thread.shutdownNow();

        //then
        assertTrue(thread.isShutdown());
        assertTrue(thread.isTerminated());
    }

    @Test
    public void shutdownNowAfterShutdown() {
        //given
        final ShutdownableThread thread = ShutdownableThread.start(LOOP_WHILE_RUNNING, LOOP_WHILE_RUNNING, Thread::new);

        //when
        thread.shutdown();

        //then
        assertTrue(thread.isShutdown());

        //when
        thread.awaitTermination(300, TimeUnit.MILLISECONDS);

        //then
        assertFalse(thread.isTerminated());

        //when
        thread.shutdownNow();

        //then
        assertTrue(thread.isShutdown());

        //when
        thread.awaitTermination(5, TimeUnit.SECONDS);

        //then
        assertTrue(thread.isTerminated());

        //when
        thread.shutdownNow();

        //then
        assertTrue(thread.isShutdown());
        assertTrue(thread.isTerminated());
    }

    @Test
    public void awaitTermination() {
        //given
        final AtomicBoolean terminate = new AtomicBoolean(false);
        final long startTime = 1234;
        final int increment = 100;
        final AtomicLong nanoTime = new AtomicLong(startTime);
        final LongSupplier nanoClock = () -> nanoTime.getAndAdd(increment);
        final Function<BooleanSupplier, Runnable> loopTillTerminate = loopUntil(() -> !terminate.get());
        final ShutdownableThread thread = ShutdownableThread.start(loopTillTerminate, loopTillTerminate, Thread::new, nanoClock);
        boolean terminated;

        //when
        thread.shutdown();

        //when
        terminated = thread.awaitTermination(0, TimeUnit.SECONDS);

        //then
        assertFalse(terminated);
        assertFalse(thread.isTerminated());
        assertEquals(startTime, nanoTime.get());

        //when
        thread.awaitTermination(500, TimeUnit.NANOSECONDS);

        //then
        assertFalse(terminated);
        assertFalse(thread.isTerminated());
        assertEquals(startTime + increment + (500/100)*increment, nanoTime.get());

        //when
        thread.awaitTermination(30, TimeUnit.MICROSECONDS);

        //then
        assertFalse(terminated);
        assertFalse(thread.isTerminated());
        assertEquals(startTime + increment + (500/100)*increment + increment + (30000/100)*increment, nanoTime.get());

        //when
        terminate.set(true);
        terminated = thread.awaitTermination(300, TimeUnit.MILLISECONDS);

        //then
        assertTrue(terminated);
        assertTrue(thread.isTerminated());
    }

    @Test
    public void awaitTerminationReturnsImmediately() throws InterruptedException {
        //given
        final String threadName = "loop-thread";
        final AtomicReference<Runnable> runnableHolder = new AtomicReference<>();
        final Thread t = new Thread(null, () -> {
            if (runnableHolder.get() != null) runnableHolder.get().run();
        }, threadName);
        final ThreadFactory threadFactory = r -> {runnableHolder.set(r); return t;};
        final AtomicBoolean terminate = new AtomicBoolean(false);
        final Function<BooleanSupplier, Runnable> loopTillTerminate = loopUntil(() -> !terminate.get());

        //when
        final ShutdownableThread thread = ShutdownableThread.start(loopTillTerminate, loopTillTerminate, threadFactory);

        //then
        assertEquals(threadName, thread.toString());

        //when
        thread.shutdown();

        //then
        assertFalse(thread.awaitTermination(100, TimeUnit.MILLISECONDS));

        //when
        terminate.set(true);
        t.join(TimeUnit.SECONDS.toMillis(5));

        //then
        assertTrue(thread.awaitTermination(5, TimeUnit.SECONDS));
        assertTrue(thread.isTerminated());
        assertEquals(threadName, thread.toString());
    }

    @Test(expected = NullPointerException.class)
    public void startThrowsNpe_nullMainRunnable() {
        ShutdownableThread.start(null, () -> {}, Thread::new);
    }

    @Test(expected = NullPointerException.class)
    public void startThrowsNpe_nullShutdownRunnable() {
        ShutdownableThread.start(() -> {}, null, Thread::new);
    }

    @Test(expected = NullPointerException.class)
    public void startThrowsNpe_nullMainRunnableFactory() {
        ShutdownableThread.start(null, run -> () -> {}, Thread::new, System::nanoTime);
    }

    @Test(expected = NullPointerException.class)
    public void startThrowsNpe_nullShutdowRunnableFactory() {
        ShutdownableThread.start(run -> () -> {}, null, Thread::new, System::nanoTime);
    }

    @Test(expected = NullPointerException.class)
    public void startThrowsNpe_nullThreadFactory_3params() {
        ShutdownableThread.start(() -> {}, () -> {}, null);
    }

    @Test(expected = NullPointerException.class)
    public void startThrowsNpe_nullThreadFactory_4params() {
        ShutdownableThread.start(run -> () -> {}, run -> () -> {}, null, System::nanoTime);
    }

    @Test(expected = NullPointerException.class)
    public void startThrowsNpe_nullNanoClock() {
        ShutdownableThread.start(run -> () -> {}, run -> () -> {}, Thread::new, null);
    }


}