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
package org.tools4j.nobark.run;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.tools4j.nobark.run.StoppableThreadTest.catchAll;

/**
 * Unit test for {@link ShutdownableThread}.
 */
public class ShutdownableThreadTest {

    private static final RunnableFactory LOOP_WHILE_RUNNING = run -> () -> {
        while (run.keepRunning());
    };

    private static final RunnableFactory LOOP_ONCE = run -> () -> {};

    private static RunnableFactory loopUntil(final BooleanSupplier loopCondition) {
        return run -> () -> {
            while (loopCondition.getAsBoolean()) ;
        };
    }

    @Test
    public void shutdown() {
        //given
        final Shutdownable thread = ShutdownableThread.start(LOOP_WHILE_RUNNING, LOOP_ONCE, Thread::new);
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
        final Shutdownable thread = ShutdownableThread.start(LOOP_WHILE_RUNNING, LOOP_WHILE_RUNNING, Thread::new);

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
        final Shutdownable thread = ShutdownableThread.start(LOOP_WHILE_RUNNING, LOOP_WHILE_RUNNING, Thread::new);

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
        final RunnableFactory loopTillTerminate = loopUntil(() -> !terminate.get());
        final Shutdownable thread = ShutdownableThread.start(loopTillTerminate, loopTillTerminate, Thread::new);
        boolean terminated;

        //when
        thread.shutdown();

        //when
        terminated = thread.awaitTermination(10, TimeUnit.MILLISECONDS);

        //then
        assertFalse(terminated);
        assertFalse(thread.isTerminated());

        //when
        terminated = thread.awaitTermination(2345, TimeUnit.MICROSECONDS);

        //then
        assertFalse(terminated);
        assertFalse(thread.isTerminated());

        //when
        thread.awaitTermination(500, TimeUnit.NANOSECONDS);

        //then
        assertFalse(terminated);
        assertFalse(thread.isTerminated());

        //when
        terminate.set(true);
        terminated = thread.awaitTermination(5, TimeUnit.SECONDS);

        //then
        assertTrue(terminated);
        assertTrue(thread.isTerminated());
    }

    @Test
    public void awaitTimeout_zeroWaitsNotAtAll() {
        //given
        final Shutdownable thread = ShutdownableThread.start(LOOP_WHILE_RUNNING, LOOP_ONCE, Thread::new);

        //when + then
        assertFalse(thread.awaitTermination(0, TimeUnit.SECONDS));
        assertFalse(thread.isTerminated());

        thread.shutdownNow();
    }

    @Test
    public void toString_ReturnsThreadName() {
        //given
        final String threadName = "loop-thread";
        final ThreadFactory factory = runnable -> new Thread(null, runnable, threadName);

        //when
        final Shutdownable thread = ShutdownableThread.start(LOOP_WHILE_RUNNING, LOOP_ONCE, factory);

        //then
        assertEquals(threadName, thread.toString());

        //when
        thread.shutdown();

        //then
        assertTrue(thread.awaitTermination(5, TimeUnit.SECONDS));
        assertTrue(thread.isTerminated());
        assertEquals(threadName, thread.toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void awaitTermination_negativeTimeout() {
        //given
        final Shutdownable thread = ShutdownableThread.start(LOOP_WHILE_RUNNING, LOOP_ONCE, Thread::new);

        try {
            //when
            thread.awaitTermination(-100, TimeUnit.NANOSECONDS);

            //then: exception
        } finally {
            //cleanup
            thread.shutdown();
        }
    }

    @Test(expected = IllegalStateException.class)
    public void awaitTerminationInterrupted() {
        //given
        final Thread awaiter = Thread.currentThread();
        final CountDownLatch terminate = new CountDownLatch(1);
        try {
            final Shutdownable thread = ShutdownableThread.start(run -> () -> {
                //wait for stop
                while (run.keepRunning());
                catchAll(() -> {
                    //wait for awaitTermination call
                    while (awaiter.getState() == Thread.State.RUNNABLE);
                    //interrupt
                    synchronized (awaiter) {
                        awaiter.interrupt();
                    }
                    //wait for exception before terminating
                    terminate.await();
                    return null;
                });
            }, run -> () -> {}, Thread::new);

            //when
            thread.shutdown();
            thread.awaitTermination(10, TimeUnit.SECONDS);
        } finally {
            terminate.countDown();
        }
        //then: exception
    }

    @Test
    public void stop() {
        //given
        final ThreadLike thread = ShutdownableThread.start(LOOP_WHILE_RUNNING, LOOP_ONCE, Thread::new);
        assertTrue(thread.isRunning());
        assertFalse(thread.isTerminated());

        //when
        thread.stop();
        thread.join(1000);

        //then
        assertFalse(thread.isRunning());
        assertTrue(thread.isTerminated());

        //when
        thread.stop();
        thread.join();
        thread.stop();

        //then
        assertFalse(thread.isRunning());
        assertTrue(thread.isTerminated());
    }

    @Test
    public void close() {
        //given
        final ThreadLike thread;

        try (final ThreadLike t = ShutdownableThread.start(LOOP_WHILE_RUNNING, LOOP_ONCE, Thread::new)) {
            assertTrue(t.isRunning());
            thread = t;
        }

        //when
        thread.join(1000);

        //then
        assertFalse(thread.isRunning());
        assertTrue(thread.isTerminated());
    }

    @Test(expected = NullPointerException.class)
    public void startThrowsNpe_nullMainRunnableFactory() {
        ShutdownableThread.start(null, run -> () -> {}, Thread::new);
    }

    @Test(expected = NullPointerException.class)
    public void startThrowsNpe_nullShutdowRunnableFactory() {
        ShutdownableThread.start(run -> () -> {}, null, Thread::new);
    }

    @Test(expected = NullPointerException.class)
    public void startThrowsNpe_nullThreadFactory() {
        ShutdownableThread.start(run -> () -> {}, run -> () -> {}, null);
    }
}