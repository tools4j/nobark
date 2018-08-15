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

import sun.misc.Contended;

import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.LongSupplier;

/**
 * Service that performs a main {@link Loop} in a new thread and another shutdown loop during the graceful
 * {@link #shutdown} phase of the service.  The service is started immediately upon construction.
 */
@SuppressWarnings("unused")
public class LoopService implements Service {

    private static final int RUNNING = 0;
    private static final int SHUTDOWN = 1;
    private static final int SHUTDOWN_NOW = 2;
    private static final int TERMINATED = 4;

    private final Runnable mainLoop;
    private final Runnable shutdownLoop;
    private final LongSupplier nanoClock;
    private final Thread thread;
    @Contended
    private final AtomicInteger state = new AtomicInteger(RUNNING);

    /**
     * Constructor with idle strategy, exception handler for exceptions thrown by steps, thread factory, and the
     * factories for the loop steps.
     *
     * @param idleStrategy      the strategy handling idle main loop phases
     * @param exceptionHandler  the step exception handler
     * @param threadFactory     the factory to create the service thread
     * @param stepFactories     the factories used to create the loop steps
     */
    public LoopService(final IdleStrategy idleStrategy,
                       final ExceptionHandler exceptionHandler,
                       final ThreadFactory threadFactory,
                       final StepSupplier... stepFactories) {
        this(idleStrategy, exceptionHandler, threadFactory, System::nanoTime, stepFactories);
    }

    /**
     * Constructor with idle strategy, exception handler for exceptions thrown by steps, thread factory, nano-time clock
     * and the factories for the loop steps.
     *
     * @param idleStrategy      the strategy handling idle main loop phases
     * @param exceptionHandler  the step exception handler
     * @param threadFactory     the factory to create the service thread
     * @param nanoClock         the nano-time clock used in {@link #awaitTermination(long, TimeUnit) awaitTermination(..)}
     * @param stepFactories     the factories used to create the loop steps
     */
    public LoopService(final IdleStrategy idleStrategy,
                       final ExceptionHandler exceptionHandler,
                       final ThreadFactory threadFactory,
                       final LongSupplier nanoClock,
                       final StepSupplier... stepFactories) {
        this.thread = threadFactory.newThread(this::run);
        this.nanoClock = Objects.requireNonNull(nanoClock);
        this.mainLoop = Loop.mainLoop(thread.getName(), this::isRunning, idleStrategy, exceptionHandler, stepFactories);
        this.shutdownLoop = Loop.shutdownLoop(thread.getName() + "-shutdown", this::isShutdownRunning,
                IdleStrategy.NO_OP, exceptionHandler, stepFactories);
        thread.start();
    }

    private void run() {
        mainLoop.run();
        shutdownLoop.run();
        notifyTerminated();
    }

    @Override
    public void shutdown() {
        state.compareAndSet(RUNNING, SHUTDOWN);
    }

    @Override
    public void shutdownNow() {
        final int shutdownAndNow = SHUTDOWN | SHUTDOWN_NOW;
        if (!state.compareAndSet(RUNNING, shutdownAndNow)) {
            state.compareAndSet(SHUTDOWN, shutdownAndNow);
        }
    }

    private void notifyTerminated() {
        if (!state.compareAndSet(SHUTDOWN, SHUTDOWN | TERMINATED)) {
            state.compareAndSet(SHUTDOWN | SHUTDOWN_NOW, SHUTDOWN | SHUTDOWN_NOW | TERMINATED);
        }
    }

    @SuppressWarnings("unused")
    private boolean isRunning(final boolean workDone) {
        return (state.get() & SHUTDOWN) == 0;
    }

    private boolean isShutdownRunning(final boolean workDone) {
        return workDone && (state.get() & SHUTDOWN_NOW) == 0;
    }

    @Override
    public boolean isShutdown() {
        return (state.get() & SHUTDOWN) != 0;
    }

    @Override
    public boolean isTerminated() {
        return (state.get() & TERMINATED) != 0;
    }

    @Override
    public boolean awaitTermination(final long timeout, final TimeUnit unit) {
        if (isTerminated()) {
            return true;
        }
        if (timeout > 0) {
            final long timeoutNanos = unit.toNanos(timeout);
            final long start = nanoClock.getAsLong();
            long wait = timeoutNanos;
            do {
                final long sleep = Math.min(100, wait);
                LockSupport.parkNanos(sleep);
                if (isTerminated()) {
                    return true;
                }
                wait = timeoutNanos - (nanoClock.getAsLong() - start);
            } while (wait > 0);
        }
        return false;
    }

    /**
     * Returns the name of the thread that was created with the thread factory passed to the constructor.
     *
     * @return the service thread's name
     * @see Thread#getName()
     */
    @Override
    public String toString() {
        return thread.getName();
    }
}
