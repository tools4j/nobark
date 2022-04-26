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
package org.tools4j.nobark.loop;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit test for {@link IdleStrategy}.
 */
public class IdleStrategyTest {

    @Test
    public void idleInvokesIdleAndReset() {
        //given
        final AtomicBoolean idleInvoked = new AtomicBoolean(false);
        final AtomicBoolean resetInvoked = new AtomicBoolean(false);
        final IdleStrategy idleStrategy = new IdleStrategy() {
            @Override
            public void idle() {
                idleInvoked.set(true);
            }

            @Override
            public void reset() {
                resetInvoked.set(true);
            }
        };

        //when
        idleStrategy.idle(true);

        //then
        assertTrue(resetInvoked.getAndSet(false));
        assertFalse(idleInvoked.getAndSet(false));

        //when
        idleStrategy.idle(false);

        //then
        assertFalse(resetInvoked.getAndSet(false));
        assertTrue(idleInvoked.getAndSet(false));

        //... and for the sake of 100% coverage
        IdleStrategy.NO_OP.idle();
    }
}