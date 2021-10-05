/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 nobark (tools4j), Marco Terzer, Anton Anufriev
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

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.tools4j.nobark.loop.ComposableStep.NO_OP;

/**
 * Unit test for {@link ComposableStep}
 */
public class ComposableStepTest {

    private static final ComposableStep WORK = () -> true;

    private static class Counter implements ComposableStep {
        final boolean result;
        int count;
        Counter(final boolean result) {
            this.result = result;
        }

        @Override
        public boolean perform() {
            count++;
            return result;
        }
        int getAndResetCount() {
            final int c = count;
            count = 0;
            return c;
        }
    }

    @Test
    public void perform_then() {
        //given
        final Counter work1 = new Counter(true);
        final Counter work2 = new Counter(true);
        final Counter nada1 = new Counter(false);
        final Counter nada2 = new Counter(false);
        boolean result;

        //when
        result = work1.then(work2).perform();

        //then
        assertTrue(result);
        assertEquals(1, work1.getAndResetCount());
        assertEquals(1, work2.getAndResetCount());

        //when
        result = work1.then(nada1).perform();

        //then
        assertTrue(result);
        assertEquals(1, work1.getAndResetCount());
        assertEquals(1, nada1.getAndResetCount());

        //when
        result = nada2.then(work2).perform();

        //then
        assertTrue(result);
        assertEquals(1, nada2.getAndResetCount());
        assertEquals(1, work2.getAndResetCount());

        //when
        result = nada1.then(nada2).perform();

        //then
        assertFalse(result);
        assertEquals(1, nada1.getAndResetCount());
        assertEquals(1, nada2.getAndResetCount());
    }

    @Test
    public void perform_thenIfPerformed() {
        //given
        final Counter work1 = new Counter(true);
        final Counter work2 = new Counter(true);
        final Counter nada1 = new Counter(false);
        final Counter nada2 = new Counter(false);
        boolean result;

        //when
        result = work1.thenIfPerformed(work2).perform();

        //then
        assertTrue(result);
        assertEquals(1, work1.getAndResetCount());
        assertEquals(1, work2.getAndResetCount());

        //when
        result = work1.thenIfPerformed(nada1).perform();

        //then
        assertTrue(result);
        assertEquals(1, work1.getAndResetCount());
        assertEquals(1, nada1.getAndResetCount());

        //when
        result = nada2.thenIfPerformed(work2).perform();

        //then
        assertFalse(result);
        assertEquals(1, nada2.getAndResetCount());
        assertEquals(0, work2.getAndResetCount());

        //when
        result = nada1.thenIfPerformed(nada2).perform();

        //then
        assertFalse(result);
        assertEquals(1, nada1.getAndResetCount());
        assertEquals(0, nada2.getAndResetCount());
    }

    @Test
    public void perform_thenIfNotPerformed() {
        //given
        final Counter work1 = new Counter(true);
        final Counter work2 = new Counter(true);
        final Counter nada1 = new Counter(false);
        final Counter nada2 = new Counter(false);
        boolean result;

        //when
        result = work1.thenIfNotPerformed(work2).perform();

        //then
        assertTrue(result);
        assertEquals(1, work1.getAndResetCount());
        assertEquals(0, work2.getAndResetCount());

        //when
        result = work1.thenIfNotPerformed(nada1).perform();

        //then
        assertTrue(result);
        assertEquals(1, work1.getAndResetCount());
        assertEquals(0, nada1.getAndResetCount());

        //when
        result = nada2.thenIfNotPerformed(work2).perform();

        //then
        assertTrue(result);
        assertEquals(1, nada2.getAndResetCount());
        assertEquals(1, work2.getAndResetCount());

        //when
        result = nada1.thenIfNotPerformed(nada2).perform();

        //then
        assertFalse(result);
        assertEquals(1, nada1.getAndResetCount());
        assertEquals(1, nada2.getAndResetCount());
    }


    @Test
    public void thenWthNoOp() {
        assertSame(WORK, WORK.then(Step.NO_OP));
        assertSame(WORK, WORK.thenIfPerformed(Step.NO_OP));
        assertSame(WORK, WORK.thenIfNotPerformed(Step.NO_OP));
    }

    @Test
    public void noopWithOther() {
        assertSame(WORK, NO_OP.then(WORK));
        assertSame(NO_OP, NO_OP.thenIfPerformed(WORK));
        assertSame(WORK, NO_OP.thenIfNotPerformed(WORK));
    }

    @Test
    public void create() {
        final Step step = () -> true;
        final ComposableStep composableStep = () -> true;
        assertSame("no-OP yields same instance", ComposableStep.NO_OP, ComposableStep.create(Step.NO_OP));
        assertNotSame("new instance from step", step, ComposableStep.create(step));
        assertSame("same instance if already composable", composableStep, ComposableStep.create(composableStep));
    }

    @Test
    public void composite_workDoneIfOneStepPerformsWork() {
        //given
        final AtomicBoolean workToDo = new AtomicBoolean();
        final Step workStep = workToDo::get;

        //when + then (NO WORK)
        assertFalse("all steps perform no work",
                ComposableStep.composite(
                        () -> false,
                        workStep,
                        () -> false
                ).perform()
        );

        //when + then (Step has WORK)
        workToDo.set(true);
        assertTrue("work step performed some work",
                ComposableStep.composite(
                        () -> false,
                        workStep,
                        () -> false
                ).perform()
        );

        //when + then (Other steps have WORK)
        workToDo.set(false);
        assertTrue("other step performed some work",
                ComposableStep.composite(
                        () -> false,
                        workStep,
                        () -> true
                ).perform()
        );
    }

    @Test
    public void composite_performEmptyReturnsFalse() {
        assertFalse("empty steps perform no work", ComposableStep.composite().perform());
    }

    @Test
    public void noop_performsNoWork() {
        assertFalse("NO_OP step performs no work", NO_OP.perform());
    }

}