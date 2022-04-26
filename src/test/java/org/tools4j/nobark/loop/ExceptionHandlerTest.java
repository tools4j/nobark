/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2022 nobark (tools4j), Marco Terzer, Anton Anufriev
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
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

/**
 * Unit test for {@link ExceptionHandler}.
 */
public class ExceptionHandlerTest {

    @Test
    public void performQuietlyAllowsNullLoop() {
        //given
        final AtomicBoolean handlerInvoked = new AtomicBoolean(false);
        final Step step = Step.NO_OP;
        final ExceptionHandler exceptionHandler = (loop, step1, throwable) -> handlerInvoked.set(true);

        //when
        exceptionHandler.performQuietly(null, step);

        //then
        assertFalse(handlerInvoked.get());
    }

    @Test
    public void performQuietlyThrowsInvokesHadlerWithNpeWhenStepIsNull() {
        final AtomicReference<Throwable> handlerException = new AtomicReference<>();
        final ExceptionHandler exceptionHandler = (loop, step1, throwable) -> handlerException.set(throwable);
        final Loop loop = new Loop(workDone -> false, IdleStrategy.NO_OP, exceptionHandler);

        //when
        exceptionHandler.performQuietly(loop, null);

        //then
        assertNotNull(handlerException.get());
        assertThat(handlerException.get(), instanceOf(NullPointerException.class));
    }
}