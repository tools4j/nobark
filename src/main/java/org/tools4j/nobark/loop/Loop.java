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
import java.util.function.Function;

/**
 * A loop performing a series of {@link Step steps} in an iterative manner as long as the {@link LoopCondition} is true.
 * If no step performs any work in a whole iteration, an {@link IdleStrategy} is invoked.
 */
public class Loop implements Runnable {

    private final String name;
    private final LoopCondition loopCondition;
    private final IdleStrategy idleStrategy;
    private final ExceptionHandler exceptionHandler;
    private final Step[] steps;

    /**
     * Constructor with loop name and condition, idle strategy, step exception handler and the steps to perform.
     *
     * @param name              the loop name returned by {@link #toString()}
     * @param loopCondition     the condition defining when the loop terminates
     * @param idleStrategy      the idle strategy defining how to handle situations without work to do
     * @param exceptionHandler  the handler for step exceptions
     * @param steps             the steps executed in the loop
     */
    public Loop(final String name,
                final LoopCondition loopCondition,
                final IdleStrategy idleStrategy,
                final ExceptionHandler exceptionHandler,
                final Step... steps) {
        this.name = Objects.requireNonNull(name);
        this.loopCondition = Objects.requireNonNull(loopCondition);
        this.idleStrategy = Objects.requireNonNull(idleStrategy);
        this.exceptionHandler = Objects.requireNonNull(exceptionHandler);
        this.steps = Objects.requireNonNull(steps);
    }

    /**
     * Static factory method creating a loop with {@link StepProvider#normalStep(StepProvider) normal} steps using the
     * given suppliers to provide the steps.
     *
     * @param name              the loop name returned by {@link #toString()}
     * @param loopCondition     the condition defining when the loop terminates
     * @param idleStrategy      the idle strategy defining how to handle situations without work to do
     * @param exceptionHandler  the handler for step exceptions
     * @param stepProviders     the providers for the steps executed during the loop
     * @return new loop with steps to execute in the normal phase of a process
     */
    public static Loop mainLoop(final String name,
                                final LoopCondition loopCondition,
                                final IdleStrategy idleStrategy,
                                final ExceptionHandler exceptionHandler,
                                final StepProvider... stepProviders) {
        return new Loop(name, loopCondition, idleStrategy, exceptionHandler, toSteps(stepProviders, StepProvider::normalStep));
    }

    /**
     * Static factory method creating a loop with {@link StepProvider#shutdownStep(StepProvider) shutdown} steps using
     * the given suppliers to provide the steps.
     *
     * @param name              the loop name returned by {@link #toString()}
     * @param loopCondition     the condition defining when the loop terminates
     * @param idleStrategy      the idle strategy defining how to handle situations without work to do
     * @param exceptionHandler  the handler for step exceptions
     * @param stepProviders     the providers for the steps executed during the loop
     * @return new loop with steps to execute in the shutdown phase of a process
     */
    public static Loop shutdownLoop(final String name,
                                    final LoopCondition loopCondition,
                                    final IdleStrategy idleStrategy,
                                    final ExceptionHandler exceptionHandler,
                                    final StepProvider... stepProviders) {
        return new Loop(name, loopCondition, idleStrategy, exceptionHandler, toSteps(stepProviders, StepProvider::shutdownStep));
    }

    @Override
    public void run() {
        boolean workDone;
        do {
            workDone = false;
            for (final Step step : steps) {
                workDone |= exceptionHandler.performQuietly(this, step);
            }
            idleStrategy.idle(workDone);
        } while (loopCondition.loopAgain(workDone));
    }

    private static Step[] toSteps(final StepProvider[] providers, final Function<StepProvider, Step> providerInvoker) {
        //count first to avoid garbage
        int count = 0;
        for (final StepProvider provider : providers) {
            if (providerInvoker.apply(provider) != Step.NO_OP) {
                count++;
            }
        }
        //now provide the array
        final Step[] steps = new Step[count];
        int index = 0;
        for (final StepProvider provider : providers) {
            final Step step = providerInvoker.apply(provider);
            if (step != Step.NO_OP) {
                steps[index] = step;
                index++;
            }
        }
        assert count == index;
        return steps;
    }

    /**
     * Returns the loop name that was provided at construction time of the loop
     * @return the name that was provided to the loop constructor
     */
    @Override
    public String toString() {
        return name;
    }
}
