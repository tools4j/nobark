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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

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
        final Loop loop = new Loop("testloop", workDone -> false, IdleStrategy.NO_OP, NULL_HANDLER, step);

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
        final Loop loop = new Loop("testloop", workDone -> loopCounter.get() <= 1, IdleStrategy.NO_OP,
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
        final Loop loop = new Loop("testloop", workDone -> workDone, IdleStrategy.NO_OP, NULL_HANDLER, step);

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
        final Loop loop = new Loop("testloop", workDone -> workDone && loopCounter.get() < 10,
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
        final Loop loop = new Loop("testloop", workDone -> loopCounter.get() < 10, idleStrategy, NULL_HANDLER, step);

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
        final Loop loop = new Loop("testloop", workDone -> loopCounter.get() < 6, idleStrategy, NULL_HANDLER, step);

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
        final Loop loop = new Loop("testloop", workDone -> loopCounter.get() < 2, IdleStrategy.NO_OP, exceptionHandler, step);

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
                "mainTestLoop", workDone -> loopCounter.get() < 2, IdleStrategy.NO_OP, NULL_HANDLER,
                StepProvider.alwaysProvide(step[0]),
                StepProvider.silenceDuringShutdown(step[1]),
                StepProvider.silenceDuringShutdown(step[2]),
                StepProvider.alwaysProvide(step[3])
        );

        //when
        loop.run();

        //then
        assertEquals("mainTestLoop", loop.toString());
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
                "shutdownLoop", workDone -> loopCounter.get() < 2, IdleStrategy.NO_OP, NULL_HANDLER,
                StepProvider.alwaysProvide(step[0]),
                StepProvider.silenceDuringShutdown(step[1]),
                StepProvider.silenceDuringShutdown(step[2]),
                StepProvider.alwaysProvide(step[3])
        );

        //when
        loop.run();

        //then
        assertEquals("shutdownLoop", loop.toString());
        assertEquals(2, loopCounter.get());
        assertEquals(Arrays.asList("step 1", "step 4", "step 1", "step 4"), stepActions);
    }

    @Test(expected = NullPointerException.class)
    public void constructorThrowsNpe_nullName() {
        new Loop(null, workDone -> true, IdleStrategy.NO_OP, NULL_HANDLER, Step.NO_OP);
    }

    @Test(expected = NullPointerException.class)
    public void constructorThrowsNpe_nullCondition() {
        new Loop("bla", null, IdleStrategy.NO_OP, NULL_HANDLER, Step.NO_OP);
    }

    @Test(expected = NullPointerException.class)
    public void constructorThrowsNpe_nullIdleStrategy() {
        new Loop("bla", workDone -> true, null, NULL_HANDLER, Step.NO_OP);
    }

    @Test(expected = NullPointerException.class)
    public void constructorThrowsNpe_nullExceptionHandler() {
        new Loop("bla", workDone -> true, IdleStrategy.NO_OP, null, Step.NO_OP);
    }

    @Test(expected = NullPointerException.class)
    public void constructorThrowsNpe_nullSteps() {
        new Loop("bla", workDone -> true, IdleStrategy.NO_OP, NULL_HANDLER, (Step[])null);
    }

    @Test
    public void constructor_allowsEmptySteps() {
        new Loop("bla", workDone -> true, IdleStrategy.NO_OP, NULL_HANDLER);
    }

    @Test(expected = NullPointerException.class)
    public void mainLoopThrowsNpe_nullSuppliers() {
        Loop.mainLoop("bla", workDone -> true, IdleStrategy.NO_OP, NULL_HANDLER, (StepProvider[])null);
    }

    @Test
    public void mainLoop_allowsEmptySuppliers() {
        Loop.mainLoop("bla", workDone -> true, IdleStrategy.NO_OP, NULL_HANDLER);
    }

    @Test(expected = NullPointerException.class)
    public void shutdownLoopThrowsNpe_nullSuppliers() {
        Loop.shutdownLoop("bla", workDone -> true, IdleStrategy.NO_OP, NULL_HANDLER, (StepProvider[])null);
    }

    @Test
    public void shutdownLoop_allowsEmptySuppliers() {
        Loop.shutdownLoop("bla", workDone -> true, IdleStrategy.NO_OP, NULL_HANDLER);
    }

}