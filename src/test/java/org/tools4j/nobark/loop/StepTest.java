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

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit test for {@link Step}
 */
public class StepTest {

    @Test
    public void composite_workDoneIfOneStepPerformsWork() {
        //given
        final AtomicBoolean workToDo = new AtomicBoolean();
        final Step workStep = workToDo::get;

        //when + then (NO WORK)
        assertFalse("all steps perform no work", Step.composite(
                () -> false,
                workStep,
                () -> false
        ).perform());

        //when + then (Step has WORK)
        workToDo.set(true);
        assertTrue("work step performed some work", Step.composite(
                () -> false,
                workStep,
                () -> false
        ).perform());

        //when + then (Other steps have WORK)
        workToDo.set(false);
        assertTrue("other step performed some work", Step.composite(
                () -> false,
                workStep,
                () -> true
        ).perform());
    }

    @Test
    public void composite_performEmptyReturnsFalse() {
        assertFalse("empty steps perform no work", Step.composite().perform());
    }

    @Test
    public void noop_performsNoWork() {
        assertFalse("NO_OP step performs no work", Step.NO_OP.perform());
    }

}