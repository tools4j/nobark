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

import java.util.Objects;

/**
 * Extension of {@link Step} with enhanced functionality facilitating the composition of step chains.
 */
@FunctionalInterface
public interface ComposableStep extends Step {

    /**
     * Returns a composable step that performs first <i><tt>this</tt></i> and then the <i><tt>next</tt></i> step.
     * <p>
     * The procedure eliminates {@link #NO_OP no-OP} steps, i.e. it returns <i><tt>this</tt></i> if
     * <i><tt>(next == NO_OP)</tt></i>.
     *
     * @param next the next step to be performed after <i><tt>this</tt></i>
     * @return a composite step performing <i><tt>this</tt></i> and then <i><tt>next</tt></i>
     */
    default ComposableStep then(final Step next) {
        Objects.requireNonNull(next);
        return next == NO_OP ? this : () -> perform() | next.perform();
    }

    /**
     * Returns a composable step that performs first <i><tt>this</tt></i> step and then the <i><tt>next</tt></i> step
     * only if the first performed any work as indicated by result of the {@link #perform()} invocation.
     * <p>
     * The procedure eliminates {@link #NO_OP no-OP} steps, i.e. it returns <i><tt>this</tt></i> if
     * <i><tt>(next == NO_OP)</tt></i>.
     *
     * @param next the next step to be performed after <i><tt>this</tt></i> only if it performed some work
     * @return  a composite step performing <i><tt>this</tt></i>, and if some work was performed subsequently also
     *          <i><tt>next</tt></i>
     */
    default ComposableStep thenIfPerformed(final Step next) {
        Objects.requireNonNull(next);
        return next == NO_OP ? this : () -> perform() && (true | next.perform());//NOTE: true is needed for correctness
    }

    /**
     * Returns a composable step that performs first <i><tt>this</tt></i> step and then the <i><tt>next</tt></i> step
     * only if the first performed no work as indicated by result of the {@link #perform()} invocation.
     * <p>
     * The procedure eliminates {@link #NO_OP no-OP} steps, i.e. it returns <i><tt>this</tt></i> if
     * <i><tt>(next == NO_OP)</tt></i>.
     *
     * @param next the next step to be performed after <i><tt>this</tt></i> only if it performed no work
     * @return  a composite step performing <i><tt>this</tt></i>, and if no work was performed subsequently
     *          <i><tt>next</tt></i>
     */
    default ComposableStep thenIfNotPerformed(final Step next) {
        Objects.requireNonNull(next);
        return next == NO_OP ? this : () -> perform() || next.perform();
    }

    /**
     * Returns a composable step given a "normal" step.
     * <p>
     * The procedure preserves {@link #NO_OP no-OP} steps as well as existing instances of {@code ComposableStep},
     * which means that such values are returned unchanged.
     *
     * @param step the step to be wrapped as a {@code ComposableStep}
     * @return  a {@code ComposableStep} doing exactly the same work as <i><tt>step</tt></i> but with a richer interface
     *          for step coposition
     */
    static ComposableStep create(final Step step) {
        Objects.requireNonNull(step);
        return step == NO_OP ? NO_OP : (step instanceof ComposableStep ? (ComposableStep)step : step::perform);
    }

    /**
     * Returns a step that runs all given component steps;  the {@link #perform()} method of the resulting composite
     * step returns true if any of the component steps returns true.  The procedure uses {@link #then(Step)} for the
     * composition and eliminates {@link #NO_OP no-OP} components.
     *
     * @param components the component steps forming the parts of the returned step
     * @return a new step that performs all component steps
     * @see #NO_OP
     */
    static ComposableStep composite(final Step... components) {
        Objects.requireNonNull(components);
        ComposableStep result = NO_OP;
        for (final Step component : components) {
            result = result.then(component);
        }
        return result;
    }

    /**
     * Step performing a no-OP; the implementation returns false indicating that no work was performed.  All composition
     * methods are optimised to eliminate no-OP steps.
     */
    ComposableStep NO_OP = new ComposableStep() {
        @Override
        public boolean perform() {
            return false;
        }

        @Override
        public ComposableStep then(final Step next) {
            return ComposableStep.create(next);
        }

        @Override
        public ComposableStep thenIfPerformed(final Step next) {
            Objects.requireNonNull(next);
            return NO_OP;
        }

        @Override
        public ComposableStep thenIfNotPerformed(final Step next) {
            return ComposableStep.create(next);
        }
    };

}