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

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * Unit test for {@link StepSupplier}.
 */
public class StepSupplierTest {

    @Test
    public void normalStep() {
        //given
        final AtomicReference<Boolean> resultIsShutdown = new AtomicReference<>();

        //when
        StepSupplier.normalStep(isShutdown -> {
            resultIsShutdown.set(isShutdown);
            return Step.NO_OP;
        });

        //then
        assertNotNull(resultIsShutdown.get());
        assertEquals(false, resultIsShutdown.get());
    }

    @Test
    public void shutdownStep() {
        //given
        final AtomicReference<Boolean> resultIsShutdown = new AtomicReference<>();

        //when
        StepSupplier.shutdownStep(isShutdown -> {
            resultIsShutdown.set(isShutdown);
            return Step.NO_OP;
        });

        //then
        assertNotNull(resultIsShutdown.get());
        assertEquals(true, resultIsShutdown.get());
    }

    @Test
    public void requiredDuringShutdown() {
        //given
        final Step step = Boolean.TRUE::booleanValue;
        final StepSupplier supplier = StepSupplier.requiredDuringShutdown(step);

        //when + then
        assertSame(step, supplier.create(false));
        assertSame(step, supplier.create(true));
    }

    @Test
    public void idleDuringShutdown() {
        //given
        final Step step = Boolean.TRUE::booleanValue;
        final StepSupplier supplier = StepSupplier.idleDuringShutdown(step);

        //when + then
        assertSame(step, supplier.create(false));
        assertSame(Step.NO_OP, supplier.create(true));
    }


    @Test(expected = NullPointerException.class)
    public void requiredDuringShutdown_nullStepNotAllowed() {
        StepSupplier.requiredDuringShutdown(null);
    }

    @Test(expected = NullPointerException.class)
    public void idleDuringShutdown_nullStepNotAllowed() {
        StepSupplier.idleDuringShutdown(null);
    }
}