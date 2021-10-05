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
package org.tools4j.nobark.run;

/**
 * A factory for a {@link Runnable} that can be stopped by complying with a
 * {@link RunnableFactory.RunningCondition RunningCondition} that is passed to the factory method upon construction of
 * the runnable.
 */
@FunctionalInterface
public interface RunnableFactory {

    /**
     * Creates a runnable that stops when the supplied {@code runningCondition} returns false.  THe runnable is expected
     * to perform some iterative function and it is obliged to check the running condition regularly and return from the
     * {@link Runnable#run() run()} method when the running condition returns false.
     *
     * @param runningCondition the condition to be checked regularly by the returned runnable telling it either to
     *                         continue or to abort
     * @return a new runnable that complies with the supplied running condition
     */
    Runnable create(RunningCondition runningCondition);

    /**
     * Condition for a runnable created by {@link RunnableFactory#create(RunningCondition)} telling the runnable when
     * to stop.
     */
    @FunctionalInterface
    interface RunningCondition {
        /**
         * Returns true if the runnable should continue its work and false if it should abort and return from te
         * {@link Runnable#run() run()} method.
         *
         * @return true to keep running and false to abort
         */
        boolean keepRunning();
    }
}
