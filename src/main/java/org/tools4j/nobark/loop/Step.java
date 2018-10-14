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
 * Step is similar to {@link Runnable} but returns true if substantial work has been performed.  Steps can for instance
 * be used as building blocks in a {@link Loop}.
 */
public interface Step {
    /**
     * Executes this step and returns true if substantial work was performed.
     *
     * @return true if work was performed
     */
    boolean perform();

    /**
     * Returns a step that runs all given component steps;  the {@link #perform()} method of the resulting composite
     * step returns true if any of the component steps returns true.
     *
     * @param components the component steps forming the parts of the returned step
     * @return a new step that performs all component steps
     */
    static Step composite(final Step... components) {
        Objects.requireNonNull(components);
        return () -> {
            boolean any = false;
            for (final Step component : components) {
                any |= component.perform();
            }
            return any;
        };
    }

    /**
     * Step performing a no-OP; the implementation returns false indicating that no work was performed.
     */
    Step NO_OP = () -> false;
}
