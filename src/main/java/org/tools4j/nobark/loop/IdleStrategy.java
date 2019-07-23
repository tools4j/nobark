/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 nobark (tools4j), Marco Terzer, Anton Anufriev
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

/**
 * Handler to define strategy in idle loops when no work is performed.  Some applications may want to busy spin in a
 * loop, others prefer to back off and release the CPU core to perform more useful work.
 */
@FunctionalInterface
public interface IdleStrategy {

    /** Invoked if no work was done */
    void idle();

    /**
     * Invoked with boolean flag indicating whether work was done or not.  Delegates to {@link #reset()} when work was
     * done and to {@link #idle()} if not.
     *
     * @param  workDone true if some work was performed
     */
    default void idle(final boolean workDone) {
        if (workDone) {
            reset();
        } else {
            idle();
        }
    }

    /** Invoked if some work was performed and an idle strategy should reset to prepare for the next idle phase */
    default void reset() {}

    /**
     * Idle strategy that performs a no-OP, that is, an idle loop will essentially become a busy-spin loop.
     */
    IdleStrategy NO_OP = new IdleStrategy() {
        @Override
        public void idle() {
            //no op
        }

        @Override
        public void idle(final boolean workDone) {
            //no op
        }
    };
}
