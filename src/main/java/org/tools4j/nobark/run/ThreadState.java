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
package org.tools4j.nobark.run;

/**
 * The running state is a property of a running service such as a thread and is closely related to
 * {@link Thread.State}.
 */
@FunctionalInterface
public interface ThreadState {

    /**
     * Returns the state of the underlying thread.
     *
     * @return the underlying thread's state
     * @see Thread#getState()
     */
    Thread.State threadState();

    /**
     * Returns true if the underlying thread has been started but has not yet terminated.
     *
     * @return  true if {@link #threadState() thread state} is neither {@link Thread.State#NEW NEW} nor
     *          {@link Thread.State#TERMINATED TERMINATED}
     */
    default boolean isRunning() {
        final Thread.State state = threadState();
        return state != Thread.State.NEW & state != Thread.State.TERMINATED;
    }

    /**
     * Returns true if the underlying thread has terminated.
     *
     * @return  true if {@link #threadState() thread state} is {@link Thread.State#TERMINATED TERMINATED}
     */
    default boolean isTerminated() {
        return threadState() == Thread.State.TERMINATED;
    }
}
