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

import java.util.Objects;

/**
 * Supplier for {@link Step} distinguishing between normal (main loop) steps and shutdown steps that are used during the
 * termination phase of a {@link Service}.
 */
@FunctionalInterface
public interface StepSupplier {
    /**
     * Returns a step for normal or shutdown use.
     *
     * @param forShutdown true if the returned step will be used in the shutdown phase, and false otherwise
     * @return the step, never null
     */
    Step create(boolean forShutdown);

    /**
     * Returns a normal step using the given supplier.
     *
     * @param supplier the supplier that provides the step
     * @return a step for use during the normal phase
     */
    static Step normalStep(final StepSupplier supplier) {
        return supplier.create(false);
    }

    /**
     * Returns a shutdown step using the given supplier.
     *
     * @param supplier the supplier that provides the step
     * @return a step for use during the shutdown phase
     */
    static Step shutdownStep(final StepSupplier supplier) {
        return supplier.create(true);
    }


    /**
     * Returns a supplier for the given step used for both normal and shutdown phase.
     *
     * @param step the step to be returned by the supplier
     * @return a supplier that always returns the given step
     */
    static StepSupplier requiredDuringShutdown(final Step step) {
        Objects.requireNonNull(step);
        return forShutdown -> step;
    }

    /**
     * Returns a supplier for the given step used for the normal phase, and a {@link Step#NO_OP no-OP} for the shutdown
     * phase.
     *
     * @param step the step to be returned by the supplier for the normal phase
     * @return a supplier that returns the given step for normal phase and a no-OP for the shutdown phase
     */
    static StepSupplier idleDuringShutdown(final Step step) {
        Objects.requireNonNull(step);
        return forShutdown -> forShutdown ? Step.NO_OP : step;
    }
}
