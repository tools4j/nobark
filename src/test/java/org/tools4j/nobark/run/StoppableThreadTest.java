/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2018-2022 nobark (tools4j), Marco Terzer, Anton Anufriev
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

import org.junit.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit test for {@link StoppableThread}.
 */
public class StoppableThreadTest {

    private static final RunnableFactory LOOP_WHILE_RUNNING = run -> () -> {
        while (run.keepRunning());
    };

    @Test
    public void stop() {
        //given
        final ThreadLike thread = StoppableThread.start(LOOP_WHILE_RUNNING, Thread::new);
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
        ThreadLike thread;

        try (final ThreadLike t = StoppableThread.start(LOOP_WHILE_RUNNING, Thread::new)) {
            assertTrue(t.isRunning());
            thread = t;
        }

        //when
        thread.join(1000);

        //then
        assertFalse(thread.isRunning());
        assertTrue(thread.isTerminated());
    }

    @Test
    public void toStringReturnsThreadName() {
        //given
        final String threadName = "mainLoopThread";

        //when
        final ThreadLike thread = StoppableThread.start(LOOP_WHILE_RUNNING, r -> new Thread(null, r, threadName));

        //then
        assertEquals(threadName, thread.toString());

        //when
        thread.stop();
        thread.join();

        //then
        assertEquals(threadName, thread.toString());
    }

    @Test(expected = IllegalStateException.class)
    public void joinInterrupted() {
        //given
        final Thread joiner = Thread.currentThread();
        final CountDownLatch terminate = new CountDownLatch(1);
        try {
            final ThreadLike thread = StoppableThread.start(run -> () -> {
                //wait for stop
                while (run.keepRunning());
                catchAll(() -> {
                    //wait for join
                    while (joiner.getState() == Thread.State.RUNNABLE);
                    //interrupt
                    synchronized (joiner) {
                        joiner.interrupt();
                    }
                    //wait for exception before terminating
                    terminate.await();
                    return null;
                });
            }, Thread::new);

            //when
            thread.stop();
            thread.join();
        } finally {
            terminate.countDown();
        }
        //then: exception
    }

    @Test(expected = NullPointerException.class)
    public void startThrowsNpe_nullRunnableFactory() {
        StoppableThread.start(null, Thread::new);
    }

    @Test(expected = NullPointerException.class)
    public void startThrowsNpe_nullThreadFactory() {
        StoppableThread.start(run -> () -> {}, null);
    }

    static void catchAll(final Callable<?> callable) {
        try {
            callable.call();
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }
}