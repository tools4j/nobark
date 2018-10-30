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
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * A thread that performs a main {@link java.lang.Runnable runnable} in a new thread and another shutdown runnable the
 * graceful {@link #shutdown} phase of the thread.  The thread is started immediately upon construction.
 */
public class ShutdownableThread implements Shutdownable {

    private static final int RUNNING = 0;
    private static final int SHUTDOWN = 1;
    private static final int SHUTDOWN_NOW = 2;
    private static final int TERMINATED = 4;

    private final Function<BooleanSupplier, Runnable> mainRunnableFactory;
    private final Function<BooleanSupplier, Runnable> shutdownRunnableFactory;
    private final LongSupplier nanoClock;
    private final Thread thread;
    @Contended
    private final AtomicInteger state = new AtomicInteger(RUNNING);

    /**
     * Constructor for shutdownable thread; it is recommended to use the static start(..) methods instead.
     *
     * @param mainRunnableFactory       the factory for the main runnable;
     *                                  the <i>{@link #isRunning}</i> condition is passed to the factory as lambda
     * @param shutdownRunnableFactory   the factory for the shutdown phase runnable;
     *                                  the <i>{@link #isShutdownRunning}</i> condition is passed to the factory as lambda
     * @param threadFactory             the factory to provide the thread
     * @param nanoClock                 the nano-time clock used in {@link #awaitTermination(long, TimeUnit) awaitTermination(..)}
     */
    protected ShutdownableThread(final Function<BooleanSupplier, Runnable> mainRunnableFactory,
                                 final Function<BooleanSupplier, Runnable> shutdownRunnableFactory,
                                 final ThreadFactory threadFactory,
                                 final LongSupplier nanoClock) {
        this.mainRunnableFactory = Objects.requireNonNull(mainRunnableFactory);
        this.shutdownRunnableFactory = Objects.requireNonNull(shutdownRunnableFactory);
        this.thread = threadFactory.newThread(this::run);
        this.nanoClock = Objects.requireNonNull(nanoClock);
        thread.start();
    }

    /**
     * Creates, starts and returns a new shutdownable thread.
     *
     * @param mainRunnableFactory       the factory for the main runnable;
     *                                  the <i>{@link #isRunning}</i> condition is passed to the factory as lambda
     * @param shutdownRunnableFactory   the factory for the shutdown phase runnable;
     *                                  the <i>{@link #isShutdownRunning}</i> condition is passed to the factory as lambda
     * @param threadFactory             the factory to provide the thread
     * @return the newly created and started shutdownable thread
     */
    public static ShutdownableThread start(final Function<BooleanSupplier, Runnable> mainRunnableFactory,
                                           final Function<BooleanSupplier, Runnable> shutdownRunnableFactory,
                                           final ThreadFactory threadFactory) {
        return start(mainRunnableFactory, shutdownRunnableFactory, threadFactory, System::nanoTime);
    }

    /**
     * Creates, starts and returns a new shutdownable thread.
     *
     * @param mainRunnableFactory       the factory for the main runnable;
     *                                  the <i>{@link #isRunning}</i> condition is passed to the factory as lambda
     * @param shutdownRunnableFactory   the factory for the shutdown phase runnable;
     *                                  the <i>{@link #isShutdownRunning}</i> condition is passed to the factory as lambda
     * @param threadFactory             the factory to provide the thread
     * @param nanoClock                 the nano-time clock used in {@link #awaitTermination(long, TimeUnit) awaitTermination(..)}
     * @return the newly created and started shutdownable thread
     */
    public static ShutdownableThread start(final Function<BooleanSupplier, Runnable> mainRunnableFactory,
                                           final Function<BooleanSupplier, Runnable> shutdownRunnableFactory,
                                           final ThreadFactory threadFactory,
                                           final LongSupplier nanoClock) {
        return new ShutdownableThread(mainRunnableFactory, shutdownRunnableFactory, threadFactory, nanoClock);
    }

    private void run() {
        final Runnable main = mainRunnableFactory.apply(this::isRunning);
        final Runnable shutdown = shutdownRunnableFactory.apply(this::isShutdownRunning);
        main.run();
        shutdown.run();
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

    private boolean isRunning() {
        return (state.get() & SHUTDOWN) == 0;
    }

    private boolean isShutdownRunning() {
        return (state.get() & SHUTDOWN_NOW) == 0;
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
