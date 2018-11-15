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
import java.util.concurrent.ThreadFactory;
import java.util.function.Function;

/**
 * A loop performing a series of {@link Step steps} in an iterative manner as long as the {@link LoopCondition} is true.
 * If no step performs any work in a whole iteration, an {@link IdleStrategy} is invoked.
 */
public class Loop implements Runnable {

    private final LoopCondition loopCondition;
    private final IdleStrategy idleStrategy;
    private final ExceptionHandler exceptionHandler;
    private final Step[] steps;

    /**
     * Constructor with loop condition, idle strategy, step exception handler and the steps to perform.
     *
     * @param loopCondition     the condition defining when the loop terminates
     * @param idleStrategy      the idle strategy defining how to handle situations without work to do
     * @param exceptionHandler  the handler for step exceptions
     * @param steps             the steps executed in the loop
     */
    public Loop(final LoopCondition loopCondition,
                final IdleStrategy idleStrategy,
                final ExceptionHandler exceptionHandler,
                final Step... steps) {
        this.loopCondition = Objects.requireNonNull(loopCondition);
        this.idleStrategy = Objects.requireNonNull(idleStrategy);
        this.exceptionHandler = Objects.requireNonNull(exceptionHandler);
        this.steps = Objects.requireNonNull(steps);
    }

    /**
     * Static factory method creating a loop with {@link StepProvider#normalStep(StepProvider) normal} steps using the
     * given providers to construct the loop steps.
     *
     * @param loopCondition     the condition defining when the loop terminates
     * @param idleStrategy      the idle strategy defining how to handle situations without work to do
     * @param exceptionHandler  the handler for step exceptions
     * @param stepProviders     the providers for the steps executed during the loop
     * @return new loop with steps to execute in the normal phase of a process
     */
    public static Loop mainLoop(final LoopCondition loopCondition,
                                final IdleStrategy idleStrategy,
                                final ExceptionHandler exceptionHandler,
                                final StepProvider... stepProviders) {
        return new Loop(loopCondition, idleStrategy, exceptionHandler, toSteps(stepProviders, StepProvider::normalStep));
    }

    /**
     * Static factory method creating a loop with {@link StepProvider#shutdownStep(StepProvider) shutdown} steps using
     * the given providers to construct the loop steps.
     *
     * @param loopCondition     the condition defining when the loop terminates
     * @param idleStrategy      the idle strategy defining how to handle situations without work to do
     * @param exceptionHandler  the handler for step exceptions
     * @param stepProviders     the providers for the steps executed during the loop
     * @return new loop with steps to execute in the shutdown phase of a process
     */
    public static Loop shutdownLoop(final LoopCondition loopCondition,
                                    final IdleStrategy idleStrategy,
                                    final ExceptionHandler exceptionHandler,
                                    final StepProvider... stepProviders) {
        return new Loop(loopCondition, idleStrategy, exceptionHandler, toSteps(stepProviders, StepProvider::shutdownStep));
    }

    /**
     * Creates, starts and returns a new thread running a loop with the given steps.
     *
     * @param idleStrategy      the strategy handling idle loop phases
     * @param exceptionHandler  the step exception handler
     * @param threadFactory     the factory to provide the service thread
     * @param steps             the steps executed during the loop
     * @return the newly created and started thread running the loop
     */
    public static StoppableThread start(final IdleStrategy idleStrategy,
                                        final ExceptionHandler exceptionHandler,
                                        final ThreadFactory threadFactory,
                                        final Step... steps) {
        Objects.requireNonNull(idleStrategy);
        Objects.requireNonNull(exceptionHandler);
        Objects.requireNonNull(steps);
        return StoppableThread.start(
                running -> new Loop(workDone -> running.isRunning(), idleStrategy, exceptionHandler, steps),
                threadFactory);
    }

    /**
     * Creates, starts and returns a new thread running first a main loop and then another shutdown loop during the
     * graceful {@link ShutdownableThread#shutdown shutdown} phase.  The loops are created with steps constructed with
     * the given providers using {@link StepProvider#normalStep(StepProvider) normal} steps for the main loop and
     * {@link StepProvider#shutdownStep(StepProvider) shutdown} steps for the shutdown loop.
     *
     * @param idleStrategy      the strategy handling idle main loop phases
     * @param exceptionHandler  the step exception handler
     * @param threadFactory     the factory to provide the service thread
     * @param stepProviders     the providers for the steps executed during the loop
     * @return the newly created and started thread running the loop
     */
    public static ShutdownableThread start(final IdleStrategy idleStrategy,
                                           final ExceptionHandler exceptionHandler,
                                           final ThreadFactory threadFactory,
                                           final StepProvider... stepProviders) {
        Objects.requireNonNull(idleStrategy);
        Objects.requireNonNull(exceptionHandler);
        Objects.requireNonNull(stepProviders);
        return ShutdownableThread.start(
                main -> mainLoop(workDone -> main.isRunning(), idleStrategy, exceptionHandler, stepProviders),
                shutdown -> shutdownLoop(workDone -> workDone && shutdown.isRunning(), IdleStrategy.NO_OP, exceptionHandler, stepProviders),
                threadFactory);
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
}
