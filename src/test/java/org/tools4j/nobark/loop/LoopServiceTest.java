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

import org.junit.Test;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

import static org.junit.Assert.*;

/**
 * Unit test for {@link LoopService}.
 */
public class LoopServiceTest {

    private static final ExceptionHandler NULL_HANDLER = (loop, step, throwable) -> {};

    @Test
    public void shutdown() {
        //given
        final LoopService loopService = new LoopService(IdleStrategy.NO_OP, NULL_HANDLER, Thread::new, forShutdown -> Step.NO_OP);
        assertFalse(loopService.isShutdown());

        //when
        loopService.shutdown();

        //then
        assertTrue(loopService.isShutdown());

        //when
        loopService.shutdown();
        loopService.shutdown();

        //then
        assertTrue(loopService.isShutdown());
    }

    @Test
    public void shutdownNow() {
        //given
        final LoopService loopService = new LoopService(IdleStrategy.NO_OP, NULL_HANDLER, Thread::new,
                StepSupplier.requiredDuringShutdown(() -> true)
        );

        //then
        assertFalse(loopService.isTerminated());

        //when
        loopService.shutdownNow();

        //then
        assertTrue(loopService.isShutdown());

        //when
        loopService.awaitTermination(5, TimeUnit.SECONDS);

        //then
        assertTrue(loopService.isTerminated());

        //when
        loopService.shutdownNow();

        //then
        assertTrue(loopService.isShutdown());
        assertTrue(loopService.isTerminated());
    }

    @Test
    public void shutdownNowAfterShutdown() {
        //given
        final LoopService loopService = new LoopService(IdleStrategy.NO_OP, NULL_HANDLER, Thread::new,
                StepSupplier.requiredDuringShutdown(() -> true)
        );

        //when
        loopService.shutdown();

        //then
        assertTrue(loopService.isShutdown());

        //when
        loopService.awaitTermination(300, TimeUnit.MILLISECONDS);

        //then
        assertFalse(loopService.isTerminated());

        //when
        loopService.shutdownNow();

        //then
        assertTrue(loopService.isShutdown());

        //when
        loopService.awaitTermination(5, TimeUnit.SECONDS);

        //then
        assertTrue(loopService.isTerminated());

        //when
        loopService.shutdownNow();

        //then
        assertTrue(loopService.isShutdown());
        assertTrue(loopService.isTerminated());
    }

    @Test
    public void awaitTermination() {
        //given
        final AtomicBoolean terminate = new AtomicBoolean(false);
        final long startTime = 1234;
        final int increment = 100;
        final AtomicLong nanoTime = new AtomicLong(startTime);
        final LongSupplier nanoClock = () -> nanoTime.getAndAdd(increment);
        final LoopService loopService = new LoopService(IdleStrategy.NO_OP, NULL_HANDLER, Thread::new, nanoClock,
                StepSupplier.requiredDuringShutdown(() -> !terminate.get())
        );
        boolean terminated;

        //when
        loopService.shutdown();

        //when
        terminated = loopService.awaitTermination(0, TimeUnit.SECONDS);

        //then
        assertFalse(terminated);
        assertFalse(loopService.isTerminated());
        assertEquals(startTime, nanoTime.get());

        //when
        loopService.awaitTermination(500, TimeUnit.NANOSECONDS);

        //then
        assertFalse(terminated);
        assertFalse(loopService.isTerminated());
        assertEquals(startTime + increment + (500/100)*increment, nanoTime.get());

        //when
        loopService.awaitTermination(30, TimeUnit.MICROSECONDS);

        //then
        assertFalse(terminated);
        assertFalse(loopService.isTerminated());
        assertEquals(startTime + increment + (500/100)*increment + increment + (30000/100)*increment, nanoTime.get());

        //when
        terminate.set(true);
        terminated = loopService.awaitTermination(300, TimeUnit.MILLISECONDS);

        //then
        assertTrue(terminated);
        assertTrue(loopService.isTerminated());
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
        final LoopService loopService = new LoopService(IdleStrategy.NO_OP, NULL_HANDLER, threadFactory,
                StepSupplier.requiredDuringShutdown(() -> !terminate.get())
        );

        //then
        assertEquals(threadName, loopService.toString());

        //when
        loopService.shutdown();

        //then
        assertFalse(loopService.awaitTermination(100, TimeUnit.MILLISECONDS));

        //when
        terminate.set(true);
        thread.join(TimeUnit.SECONDS.toMillis(5));

        //then
        assertTrue(loopService.awaitTermination(5, TimeUnit.SECONDS));
        assertTrue(loopService.isTerminated());
        assertEquals(threadName, loopService.toString());
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
        final LoopService loopService = new LoopService(IdleStrategy.NO_OP, exceptionHandler, Thread::new,
                StepSupplier.idleDuringShutdown(() -> actions.add("step 1")),
                StepSupplier.requiredDuringShutdown(() -> {throw step2Exception;}),
                StepSupplier.idleDuringShutdown(() -> actions.add("step 3")),
                StepSupplier.idleDuringShutdown(() -> iterationCounter.incrementAndGet() == 0),
                StepSupplier.requiredDuringShutdown(() -> shutdownCounter.incrementAndGet() - iterationCounter.get() < shutdownRounds)
        );

        //when
        while(iterationCounter.get() < minIterationRounds);
        loopService.shutdown();
        loopService.awaitTermination(5, TimeUnit.SECONDS);

        //then
        assertTrue(loopService.isTerminated());
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
        final LoopService loopService = new LoopService(IdleStrategy.NO_OP, exceptionHandler, threadFactory,
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
        loopService.shutdown();
        loopService.awaitTermination(5, TimeUnit.SECONDS);

        //then
        assertEquals(2, loopNames.size());
        assertEquals(threadName, loopNames.poll());
        assertEquals(threadName + "-shutdown", loopNames.poll());
    }

    @Test(expected = NullPointerException.class)
    public void constructorThrowsNpe_nullIdleStrategy() {
        new LoopService(null, NULL_HANDLER, Thread::new, forShutdown -> Step.NO_OP);
    }

    @Test(expected = NullPointerException.class)
    public void constructorThrowsNpe_nullExceptionHandler() {
        new LoopService(IdleStrategy.NO_OP, null, Thread::new, forShutdown -> Step.NO_OP);
    }

    @Test(expected = NullPointerException.class)
    public void constructorThrowsNpe_nullThreadFactory() {
        new LoopService(IdleStrategy.NO_OP, NULL_HANDLER, null, forShutdown -> Step.NO_OP);
    }

    @Test(expected = NullPointerException.class)
    public void constructorThrowsNpe_nullStepSuppliers() {
        new LoopService(IdleStrategy.NO_OP, NULL_HANDLER, Thread::new, null);
    }

    @Test
    public void constructor_allowEmptySteps() {
        new LoopService(IdleStrategy.NO_OP, NULL_HANDLER, Thread::new);
    }

}