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

import sun.misc.Contended;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A thread that performs a main {@link java.lang.Runnable runnable} in a new thread and another shutdown runnable the
 * graceful {@link #shutdown} phase of the thread.  The thread is started immediately upon construction.
 */
public class ShutdownableThread implements ThreadLike, Shutdownable {

    private static final int RUNNING = 0;
    private static final int SHUTDOWN = 1;
    private static final int SHUTDOWN_NOW = 2;

    private final RunnableFactory mainRunnableFactory;
    private final RunnableFactory shutdownRunnableFactory;
    private final Thread thread;
    @Contended
    private final AtomicInteger state = new AtomicInteger(RUNNING);

    /**
     * Constructor for shutdownable thread; it is recommended to use the static start(..) methods instead.
     *
     * @param mainRunnableFactory       the factory for the main runnable;
     *                                  the <i>{@link #keepMainRunning}</i> condition is passed to the factory as lambda
     * @param shutdownRunnableFactory   the factory for the shutdown phase runnable;
     *                                  the <i>{@link #keepShutdownRunning}</i> condition is passed to the factory as lambda
     * @param threadFactory             the factory to provide the thread
     */
    protected ShutdownableThread(final RunnableFactory mainRunnableFactory,
                                 final RunnableFactory shutdownRunnableFactory,
                                 final ThreadFactory threadFactory) {
        this.mainRunnableFactory = Objects.requireNonNull(mainRunnableFactory);
        this.shutdownRunnableFactory = Objects.requireNonNull(shutdownRunnableFactory);
        this.thread = threadFactory.newThread(this::run);
        thread.start();
    }

    /**
     * Creates, starts and returns a new shutdownable thread.
     *
     * @param mainRunnableFactory       the factory for the main runnable;
     *                                  the <i>{@link #keepMainRunning}</i> condition is passed to the factory as lambda
     * @param shutdownRunnableFactory   the factory for the shutdown phase runnable;
     *                                  the <i>{@link #keepShutdownRunning}</i> condition is passed to the factory as lambda
     * @param threadFactory             the factory to provide the thread
     * @return the newly created and started shutdownable thread
     */
    public static ShutdownableThread start(final RunnableFactory mainRunnableFactory,
                                           final RunnableFactory shutdownRunnableFactory,
                                           final ThreadFactory threadFactory) {
        return new ShutdownableThread(mainRunnableFactory, shutdownRunnableFactory, threadFactory);
    }

    private void run() {
        final Runnable main = mainRunnableFactory.create(this::keepMainRunning);
        final Runnable shutdown = shutdownRunnableFactory.create(this::keepShutdownRunning);
        main.run();
        shutdown.run();
    }

    @Override
    public void shutdown() {
        state.compareAndSet(RUNNING, SHUTDOWN);
    }


    @Override
    public List<Runnable> shutdownNow() {
        final int shutdownAndNow = SHUTDOWN | SHUTDOWN_NOW;
        if (!state.compareAndSet(RUNNING, shutdownAndNow)) {
            state.compareAndSet(SHUTDOWN, shutdownAndNow);
        }
        return Collections.emptyList();
    }

    private boolean keepMainRunning() {
        return (state.get() & SHUTDOWN) == 0;
    }

    private boolean keepShutdownRunning() {
        return (state.get() & SHUTDOWN_NOW) == 0;
    }

    @Override
    public boolean isShutdown() {
        return (state.get() & SHUTDOWN) != 0;
    }

    @Override
    public boolean isTerminated() {
        return threadState() == Thread.State.TERMINATED;
    }

    @Override
    public boolean awaitTermination(final long timeout, final TimeUnit unit) {
        if (timeout < 0) {
            throw new IllegalArgumentException("timeout value is negative: " + timeout);
        }
        if (isTerminated()) {
            return true;
        }
        final long millis = unit.toMillis(timeout);
        final long nanos = unit.toNanos(timeout - unit.convert(millis, TimeUnit.MILLISECONDS));
        try {
            if (nanos == 0) {
                thread.join(millis);
            } else {
                thread.join(millis, (int) nanos);
            }
        } catch (final InterruptedException e) {
            throw new IllegalStateException("Join interrupted for thread " + thread);
        }
        return isTerminated();
    }

    @Override
    public Thread.State threadState() {
        return thread.getState();
    }

    /**
     * Calls {@link #shutdown()}.
     */
    @Override
    public void stop() {
        shutdown();
    }

    /**
     * Delegates to {@link #awaitTermination(long, TimeUnit)}.
     */
    @Override
    public void join(final long millis) {
        awaitTermination(millis, TimeUnit.MILLISECONDS);
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
