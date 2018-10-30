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

import java.util.function.BooleanSupplier;
import java.util.function.Function;

import static org.junit.Assert.*;

/**
 * Unit test for {@link StoppableThread}.
 */
public class StoppableThreadTest {

    private static final Function<BooleanSupplier, Runnable> LOOP_WHILE_RUNNING = run -> () -> {
        while (run.getAsBoolean());
    };

    private static Function<BooleanSupplier, Runnable> loopUntil(final BooleanSupplier loopCondition) {
        return run -> () -> {
            while (loopCondition.getAsBoolean()) ;
        };
    }

    @Test
    public void stop() {
        //given
        final StoppableThread thread = StoppableThread.start(LOOP_WHILE_RUNNING, Thread::new);
        assertTrue(thread.isRunning());

        //when
        thread.stop();

        //then
        assertFalse(thread.isRunning());

        //when
        thread.stop();
        thread.stop();

        //then
        assertFalse(thread.isRunning());
    }

    @Test
    public void close() {
        //given
        StoppableThread thread;

        try (final StoppableThread t = StoppableThread.start(LOOP_WHILE_RUNNING, Thread::new)) {
            assertTrue(t.isRunning());
            thread = t;
        }

        //when: stopped throough auto close

        //then
        assertFalse(thread.isRunning());
    }

    @Test
    public void toStringReturnsThreadName() {
        //given
        final String threadName = "mainLoopThread";

        //when
        final StoppableThread thread = StoppableThread.start(LOOP_WHILE_RUNNING, r -> new Thread(null, r, threadName));

        //then
        assertEquals(threadName, thread.toString());

        //when
        thread.stop();

        //then
        assertEquals("StoppableThread[STOPPED]", thread.toString());
    }

    @Test(expected = NullPointerException.class)
    public void startThrowsNpe_nullRunnableFactory() {
        StoppableThread.start(null, Thread::new);
    }

    @Test(expected = NullPointerException.class)
    public void startThrowsNpe_nullThreadFactory() {
        StoppableThread.start(run -> () -> {}, null);
    }
}