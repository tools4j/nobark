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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

import org.tools4j.nobark.run.ShutdownableThread;
import org.tools4j.nobark.run.StoppableThread;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit test for {@link Loop}.
 */
public class LoopTest {

    private static final ExceptionHandler NULL_HANDLER = (loop, step, throwable) -> {};

    @Test
    public void loopOnceIfConditinoIsFalse() {
        //given
        final AtomicInteger loopCounter = new AtomicInteger();
        final Step step = () -> loopCounter.incrementAndGet() > 0;
        final Loop loop = new Loop(workDone -> false, IdleStrategy.NO_OP, NULL_HANDLER, step);

        //when
        loop.run();

        //then
        assertEquals(1, loopCounter.get());
    }

    @Test
    public void loopTwiceIfConditinoIsFalseInSecondIteration() {
        //given
        final AtomicInteger loopCounter = new AtomicInteger();
        final Step step = () -> loopCounter.incrementAndGet() > 0;
        final Loop loop = new Loop(workDone -> loopCounter.get() <= 1, IdleStrategy.NO_OP,
                NULL_HANDLER, step);

        //when
        loop.run();

        //then
        assertEquals(2, loopCounter.get());
    }

    @Test
    public void workDoneIsFalseIfAllStepsPerformNoWork() {
        //given
        final AtomicInteger loopCounter = new AtomicInteger();
        final Step[] step = {
                Step.NO_OP,
                Boolean.FALSE::booleanValue,
                () -> false,
                () -> loopCounter.incrementAndGet() < 0
        };
        final Loop loop = new Loop(workDone -> workDone, IdleStrategy.NO_OP, NULL_HANDLER, step);

        //when
        loop.run();

        //then
        assertEquals(1, loopCounter.get());
    }

    @Test
    public void workDoneIsTrueIfOneStepPerformsWork() {
        //given
        final AtomicInteger loopCounter = new AtomicInteger();
        final Step[] step = {
                Step.NO_OP,
                Boolean.FALSE::booleanValue,
                () -> true,
                () -> loopCounter.incrementAndGet() < 0
        };
        final Loop loop = new Loop(workDone -> workDone && loopCounter.get() < 10,
                IdleStrategy.NO_OP, NULL_HANDLER, step);

        //when
        loop.run();

        //then
        assertEquals(10, loopCounter.get());
    }

    @Test
    public void ideStrategyIsInvokedIfNoWorkIsDone() {
        //given
        final AtomicInteger loopCounter = new AtomicInteger();
        final AtomicInteger idleCounter = new AtomicInteger();
        final IdleStrategy idleStrategy = idleCounter::incrementAndGet;
        final Step[] step = {
                Step.NO_OP,
                Boolean.FALSE::booleanValue,
                () -> false,
                () -> loopCounter.incrementAndGet() % 2 == 0 /* no work in half the cases*/
        };
        final Loop loop = new Loop(workDone -> loopCounter.get() < 10, idleStrategy, NULL_HANDLER, step);

        //when
        loop.run();

        //then
        assertEquals(10, loopCounter.get());
        assertEquals(5, idleCounter.get());
    }

    @Test
    public void ideStrategyIsResetIfWorkIsDone() {
        //given
        final AtomicInteger loopCounter = new AtomicInteger();
        final List<String> idleActions = new ArrayList<>();
        final IdleStrategy idleStrategy = new IdleStrategy() {
            @Override
            public void idle() {
                idleActions.add("idle");
            }

            @Override
            public void reset() {
                idleActions.add("reset");
            }
        };
        final Step[] step = {
                Step.NO_OP,
                Boolean.FALSE::booleanValue,
                () -> false,
                () -> loopCounter.incrementAndGet() % 3 != 0 /* no work at iteration 3 and 6*/
        };
        final Loop loop = new Loop(workDone -> loopCounter.get() < 6, idleStrategy, NULL_HANDLER, step);

        //when
        loop.run();

        //then
        assertEquals(6, loopCounter.get());
        assertEquals(Arrays.asList("reset", "reset", "idle", "reset", "reset", "idle"), idleActions);
    }

    @Test
    public void exceptionHandlerIsinvokdIfStepthrowsException() {
        //given
        final RuntimeException step2Exception = new RuntimeException("step 2 test exception");
        final List<Object> stepActions = new ArrayList<>();
        final ExceptionHandler exceptionHandler = (loop, step, throwable) -> stepActions.add(throwable);
        final AtomicInteger loopCounter = new AtomicInteger();
        final Step[] step = {
                () -> stepActions.add("step 1"),
                () -> {stepActions.add("step 2"); throw step2Exception;},
                () -> {loopCounter.incrementAndGet(); stepActions.add("step 3"); return true;}
        };
        final Loop loop = new Loop(workDone -> loopCounter.get() < 2, IdleStrategy.NO_OP, exceptionHandler, step);

        //when
        loop.run();

        //then
        assertEquals(2, loopCounter.get());
        assertEquals(Arrays.asList("step 1", "step 2", step2Exception, "step 3",
                "step 1", "step 2", step2Exception, "step 3"), stepActions);
    }

    @Test
    public void mainLoop() {
        //given
        final List<Object> stepActions = new ArrayList<>();
        final AtomicInteger loopCounter = new AtomicInteger();
        final Step[] step = {
                () -> stepActions.add("step 1"),
                () -> stepActions.add("step 2"),
                () -> stepActions.add("step 3"),
                () -> {stepActions.add("step 4");loopCounter.incrementAndGet(); return true;}
        };
        final Loop loop = Loop.mainLoop(
                workDone -> loopCounter.get() < 2, IdleStrategy.NO_OP, NULL_HANDLER,
                StepProvider.alwaysProvide(step[0]),
                StepProvider.silenceDuringShutdown(step[1]),
                StepProvider.silenceDuringShutdown(step[2]),
                StepProvider.alwaysProvide(step[3])
        );

        //when
        loop.run();

        //then
        assertEquals(2, loopCounter.get());
        assertEquals(Arrays.asList(
                "step 1", "step 2", "step 3", "step 4",
                "step 1", "step 2", "step 3", "step 4"
        ), stepActions);
    }

    @Test
    public void shutdownLoop() {
        //given
        final List<Object> stepActions = new ArrayList<>();
        final AtomicInteger loopCounter = new AtomicInteger();
        final Step[] step = {
                () -> stepActions.add("step 1"),
                () -> stepActions.add("step 2"),
                () -> stepActions.add("step 3"),
                () -> {stepActions.add("step 4");loopCounter.incrementAndGet(); return true;}
        };
        final Loop loop = Loop.shutdownLoop(
                workDone -> loopCounter.get() < 2, IdleStrategy.NO_OP, NULL_HANDLER,
                StepProvider.alwaysProvide(step[0]),
                StepProvider.silenceDuringShutdown(step[1]),
                StepProvider.silenceDuringShutdown(step[2]),
                StepProvider.alwaysProvide(step[3])
        );

        //when
        loop.run();

        //then
        assertEquals(2, loopCounter.get());
        assertEquals(Arrays.asList("step 1", "step 4", "step 1", "step 4"), stepActions);
    }

    @Test
    public void start_invokeHandlerAndSteps_thenStop() {
        //given
        final int minIterationRounds = 10;
        final RuntimeException step2Exception = new RuntimeException("step 2 test exception");
        final List<Object> actions = new ArrayList<>();
        final ExceptionHandler exceptionHandler = (loop, step, throwable) -> actions.add(throwable);
        final AtomicLong iterationCounter = new AtomicLong();
        final StoppableThread thread = Loop.start(IdleStrategy.NO_OP, exceptionHandler, Thread::new,
                () -> actions.add("step 1"),
                () -> {throw step2Exception;},
                () -> actions.add("step 3"),
                () -> iterationCounter.incrementAndGet() == 0
        );

        //when
        while (iterationCounter.get() < minIterationRounds) ;
        thread.stop();
        thread.join();

        //then
        assertFalse(thread.isRunning());
        assertEquals(3 * iterationCounter.get(), actions.size());
        assertEquals("step 1", actions.get(0));
        assertEquals(step2Exception, actions.get(1));
        assertEquals("step 3", actions.get(2));
        assertEquals("step 1", actions.get(3));
        assertEquals(step2Exception, actions.get(4));
        assertEquals("step 3", actions.get(5));
        assertEquals("step 3", actions.get(actions.size() - 1));
    }

    @Test
    public void start_invokeHandlerAndSteps_thenShutdown_keepInvokingHandlerAndShutdownStepsUntilDone() {
        //given
        final int minIterationRounds = 10;
        final int shutdownRounds = 3;
        final RuntimeException step2Exception = new RuntimeException("step 2 test exception");
        final List<Object> actions = new ArrayList<>();
        final ExceptionHandler exceptionHandler = (loop, step, throwable) -> actions.add(throwable);
        final AtomicLong iterationCounter = new AtomicLong();
        final AtomicLong shutdownCounter = new AtomicLong();
        final ShutdownableThread thread = Loop.start(IdleStrategy.NO_OP, exceptionHandler, Thread::new,
                StepProvider.silenceDuringShutdown(() -> actions.add("step 1")),
                StepProvider.alwaysProvide(() -> {throw step2Exception;}),
                StepProvider.silenceDuringShutdown(() -> actions.add("step 3")),
                StepProvider.silenceDuringShutdown(() -> iterationCounter.incrementAndGet() == 0),
                StepProvider.alwaysProvide(() -> shutdownCounter.incrementAndGet() - iterationCounter.get() < shutdownRounds)
        );

        //when
        while(iterationCounter.get() < minIterationRounds);
        thread.shutdown();
        thread.awaitTermination(5, TimeUnit.SECONDS);

        //then
        assertTrue(thread.isTerminated());
        assertEquals(3 * iterationCounter.get() + shutdownRounds, actions.size());
        assertEquals("step 1", actions.get(0));
        assertEquals(step2Exception, actions.get(1));
        assertEquals("step 3", actions.get(2));
        assertEquals("step 1", actions.get(3));
        assertEquals(step2Exception, actions.get(4));
        assertEquals("step 3", actions.get(5));
        assertEquals(step2Exception, actions.get(actions.size() - 1));
    }

    @Test(expected = NullPointerException.class)
    public void constructorThrowsNpe_nullCondition() {
        new Loop(null, IdleStrategy.NO_OP, NULL_HANDLER, Step.NO_OP);
    }

    @Test(expected = NullPointerException.class)
    public void constructorThrowsNpe_nullIdleStrategy() {
        new Loop(workDone -> true, null, NULL_HANDLER, Step.NO_OP);
    }

    @Test(expected = NullPointerException.class)
    public void constructorThrowsNpe_nullExceptionHandler() {
        new Loop(workDone -> true, IdleStrategy.NO_OP, null, Step.NO_OP);
    }

    @Test(expected = NullPointerException.class)
    public void constructorThrowsNpe_nullSteps() {
        new Loop(workDone -> true, IdleStrategy.NO_OP, NULL_HANDLER, (Step[])null);
    }

    @Test
    public void constructor_allowsEmptySteps() {
        new Loop(workDone -> true, IdleStrategy.NO_OP, NULL_HANDLER);
    }

    @Test(expected = NullPointerException.class)
    public void mainLoopThrowsNpe_nullProviders() {
        Loop.mainLoop(workDone -> true, IdleStrategy.NO_OP, NULL_HANDLER, (StepProvider[])null);
    }

    @Test
    public void mainLoop_allowsEmptyProviders() {
        Loop.mainLoop(workDone -> true, IdleStrategy.NO_OP, NULL_HANDLER);
    }

    @Test(expected = NullPointerException.class)
    public void shutdownLoopThrowsNpe_nullProviders() {
        Loop.shutdownLoop(workDone -> true, IdleStrategy.NO_OP, NULL_HANDLER, (StepProvider[])null);
    }

    @Test
    public void shutdownLoop_allowsEmptyProviders() {
        Loop.shutdownLoop(workDone -> true, IdleStrategy.NO_OP, NULL_HANDLER);
    }

    @Test(expected = NullPointerException.class)
    public void startThrowsNpe_nullIdleStrategy() {
        Loop.start(null, NULL_HANDLER, Thread::new, forShutdown -> Step.NO_OP);
    }

    @Test(expected = NullPointerException.class)
    public void startThrowsNpe_nullExceptionHandler() {
        Loop.start(IdleStrategy.NO_OP, null, Thread::new, forShutdown -> Step.NO_OP);
    }

    @Test(expected = NullPointerException.class)
    public void startThrowsNpe_nullThreadFactory() {
        Loop.start(IdleStrategy.NO_OP, NULL_HANDLER, null, forShutdown -> Step.NO_OP);
    }

    @Test(expected = NullPointerException.class)
    public void startThrowsNpe_nullStepProviders() {
        Loop.start(IdleStrategy.NO_OP, NULL_HANDLER, Thread::new, (StepProvider[])null);
    }

    @Test
    public void start_allowEmptySteps() {
        Loop.start(IdleStrategy.NO_OP, NULL_HANDLER, Thread::new, new Step[0]);
    }

    @Test
    public void start_allowEmptyStepProviders() {
        Loop.start(IdleStrategy.NO_OP, NULL_HANDLER, Thread::new, new StepProvider[0]);
    }
}