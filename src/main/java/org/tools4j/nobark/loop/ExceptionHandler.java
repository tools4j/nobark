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

/**
 * Handler for unexpected exceptions thrown by a {@link Step} in a {@link Loop}.
 */
@FunctionalInterface
public interface ExceptionHandler {

    /**
     * Handles an unexpected exception that was thrown by the given {@code step} that is part of the given {@code loop}.
     *
     * @param loop      the loop which the failed step is part of
     * @param step      the step that throws the specified exception
     * @param throwable the exception thrown by step
     */
    void handleException(Loop loop, Step step, Throwable throwable);

    /**
     * {@link Step#perform() Performs} the step's method handling possible exceptions by invoking this handler's
     * {@link #handleException(Loop, Step, Throwable)} method.
     *
     * @param loop the loop to which the step belongs
     * @param step the step to perform
     * @return true if work was performed by the step, and false otherwise or if an exception was caught
     */
    default boolean performQuietly(final Loop loop, final Step step) {
        try {
            return step.perform();
        } catch (final Throwable t) {
            handleException(loop, step, t);
            return false;
        }
    }
}
