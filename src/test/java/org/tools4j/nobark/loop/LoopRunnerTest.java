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

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit test for {@link LoopRunner}.
 */
public class LoopRunnerTest {

    private static final ExceptionHandler NULL_HANDLER = (loop, step, throwable) -> {};

    @Test
    public void shutdown() {
        //given
        final LoopRunner runner = LoopRunner.start(IdleStrategy.NO_OP, NULL_HANDLER, Thread::new, forShutdown -> Step.NO_OP);
        assertFalse(runner.isShutdown());

        //when
        runner.shutdown();

        //then
        assertTrue(runner.isShutdown());

        //when
        runner.shutdown();
        runner.shutdown();

        //then
        assertTrue(runner.isShutdown());
    }

    @Test
    public void shutdownNow() {
        //given
        final LoopRunner runner = LoopRunner.start(IdleStrategy.NO_OP, NULL_HANDLER, Thread::new,
                StepProvider.alwaysProvide(() -> true)
        );

        //then
        assertFalse(runner.isTerminated());

        //when
        runner.shutdownNow();

        //then
        assertTrue(runner.isShutdown());

        //when
        runner.awaitTermination(5, TimeUnit.SECONDS);

        //then
        assertTrue(runner.isTerminated());

        //when
        runner.shutdownNow();

        //then
        assertTrue(runner.isShutdown());
        assertTrue(runner.isTerminated());
    }

    @Test
    public void shutdownNowAfterShutdown() {
        //given
        final LoopRunner runner = LoopRunner.start(IdleStrategy.NO_OP, NULL_HANDLER, Thread::new,
                StepProvider.alwaysProvide(() -> true)
        );

        //when
        runner.shutdown();

        //then
        assertTrue(runner.isShutdown());

        //when
        runner.awaitTermination(300, TimeUnit.MILLISECONDS);

        //then
        assertFalse(runner.isTerminated());

        //when
        runner.shutdownNow();

        //then
        assertTrue(runner.isShutdown());

        //when
        runner.awaitTermination(5, TimeUnit.SECONDS);

        //then
        assertTrue(runner.isTerminated());

        //when
        runner.shutdownNow();

        //then
        assertTrue(runner.isShutdown());
        assertTrue(runner.isTerminated());
    }

    @Test
    public void awaitTermination() {
        //given
        final AtomicBoolean terminate = new AtomicBoolean(false);
        final long startTime = 1234;
        final int increment = 100;
        final AtomicLong nanoTime = new AtomicLong(startTime);
        final LongSupplier nanoClock = () -> nanoTime.getAndAdd(increment);
        final LoopRunner runner = LoopRunner.start(IdleStrategy.NO_OP, NULL_HANDLER, Thread::new, nanoClock,
                StepProvider.alwaysProvide(() -> !terminate.get())
        );
        boolean terminated;

        //when
        runner.shutdown();

        //when
        terminated = runner.awaitTermination(0, TimeUnit.SECONDS);

        //then
        assertFalse(terminated);
        assertFalse(runner.isTerminated());
        assertEquals(startTime, nanoTime.get());

        //when
        runner.awaitTermination(500, TimeUnit.NANOSECONDS);

        //then
        assertFalse(terminated);
        assertFalse(runner.isTerminated());
        assertEquals(startTime + increment + (500/100)*increment, nanoTime.get());

        //when
        runner.awaitTermination(30, TimeUnit.MICROSECONDS);

        //then
        assertFalse(terminated);
        assertFalse(runner.isTerminated());
        assertEquals(startTime + increment + (500/100)*increment + increment + (30000/100)*increment, nanoTime.get());

        //when
        terminate.set(true);
        terminated = runner.awaitTermination(300, TimeUnit.MILLISECONDS);

        //then
        assertTrue(terminated);
        assertTrue(runner.isTerminated());
    }

    @Test
    public void awaitTerminationReturnsImmediately() throws InterruptedException {
        //given
        final String threadName = "loop-runner";
        final AtomicReference<Runnable> runnableHolder = new AtomicReference<>();
        final Thread thread = new Thread(null, () -> {
            if (runnableHolder.get() != null) runnableHolder.get().run();
        }, threadName);
        final ThreadFactory threadFactory = r -> {runnableHolder.set(r); return thread;};
        final AtomicBoolean terminate = new AtomicBoolean(false);

        //when
        final LoopRunner runner = LoopRunner.start(IdleStrategy.NO_OP, NULL_HANDLER, threadFactory,
                StepProvider.alwaysProvide(() -> !terminate.get())
        );

        //then
        assertEquals(threadName, runner.toString());

        //when
        runner.shutdown();

        //then
        assertFalse(runner.awaitTermination(100, TimeUnit.MILLISECONDS));

        //when
        terminate.set(true);
        thread.join(TimeUnit.SECONDS.toMillis(5));

        //then
        assertTrue(runner.awaitTermination(5, TimeUnit.SECONDS));
        assertTrue(runner.isTerminated());
        assertEquals(threadName, runner.toString());
    }

    @Test
    public void invokeHandlerAndSteps_thenShutdown_keepInvokingHandlerAndShutdownStepsUntilDone() {
        //given
        final int minIterationRounds = 10;
        final int shutdownRounds = 3;
        final RuntimeException step2Exception = new RuntimeException("step 2 test exception");
        final List<Object> actions = new ArrayList<>();
        final ExceptionHandler exceptionHandler = (loop, step, throwable) -> actions.add(throwable);
        final AtomicLong iterationCounter = new AtomicLong();
        final AtomicLong shutdownCounter = new AtomicLong();
        final LoopRunner runner = LoopRunner.start(IdleStrategy.NO_OP, exceptionHandler, Thread::new,
                StepProvider.silenceDuringShutdown(() -> actions.add("step 1")),
                StepProvider.alwaysProvide(() -> {throw step2Exception;}),
                StepProvider.silenceDuringShutdown(() -> actions.add("step 3")),
                StepProvider.silenceDuringShutdown(() -> iterationCounter.incrementAndGet() == 0),
                StepProvider.alwaysProvide(() -> shutdownCounter.incrementAndGet() - iterationCounter.get() < shutdownRounds)
        );

        //when
        while(iterationCounter.get() < minIterationRounds);
        runner.shutdown();
        runner.awaitTermination(5, TimeUnit.SECONDS);

        //then
        assertTrue(runner.isTerminated());
        assertEquals(3 * iterationCounter.get() + shutdownRounds, actions.size());
        assertEquals("step 1", actions.get(0));
        assertEquals(step2Exception, actions.get(1));
        assertEquals("step 3", actions.get(2));
        assertEquals("step 1", actions.get(3));
        assertEquals(step2Exception, actions.get(4));
        assertEquals("step 3", actions.get(5));
        assertEquals(step2Exception, actions.get(actions.size() - 1));
    }

    @Test
    public void mainLoopAndShutdownLoopNames() {
        //given
        final String threadName = "main-loop";
        final ThreadFactory threadFactory = r -> new Thread(null, r, threadName);
        final Deque<String> loopNames = new ConcurrentLinkedDeque<>();
        final ExceptionHandler exceptionHandler = (loop, step, throwable) -> loopNames.offer(loop.toString());
        final LoopRunner runner = LoopRunner.start(IdleStrategy.NO_OP, exceptionHandler, threadFactory,
                isShutdown -> {
                    if (isShutdown) {
                        return () -> {
                            final String loopName = loopNames.getFirst();
                            loopNames.clear();
                            loopNames.offer(loopName);
                            throw new RuntimeException("shutdown step test exception");
                        };
                    } else {
                        return () -> {
                            throw new RuntimeException("normal step test exception");
                        };
                    }
                }
        );

        //when
        while(loopNames.isEmpty());
        runner.shutdown();
        runner.awaitTermination(5, TimeUnit.SECONDS);

        //then
        assertEquals(2, loopNames.size());
        assertEquals(threadName, loopNames.poll());
        assertEquals(threadName + "-shutdown", loopNames.poll());
    }

    @Test(expected = NullPointerException.class)
    public void startThrowsNpe_nullIdleStrategy() {
        LoopRunner.start(null, NULL_HANDLER, Thread::new, forShutdown -> Step.NO_OP);
    }

    @Test(expected = NullPointerException.class)
    public void startThrowsNpe_nullExceptionHandler() {
        LoopRunner.start(IdleStrategy.NO_OP, null, Thread::new, forShutdown -> Step.NO_OP);
    }

    @Test(expected = NullPointerException.class)
    public void startThrowsNpe_nullThreadFactory() {
        LoopRunner.start(IdleStrategy.NO_OP, NULL_HANDLER, null, forShutdown -> Step.NO_OP);
    }

    @Test(expected = NullPointerException.class)
    public void startThrowsNpe_nullStepSuppliers() {
        LoopRunner.start(IdleStrategy.NO_OP, NULL_HANDLER, Thread::new, (StepProvider[])null);
    }

    @Test
    public void start_allowEmptySteps() {
        LoopRunner.start(IdleStrategy.NO_OP, NULL_HANDLER, Thread::new);
    }

}