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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * A thread that performs a {@link java.lang.Runnable runnable} in a new thread.
 * The thread is started immediately upon construction and it can be stopped via stop or auto-close method.
 */
public class StoppableThread implements Stoppable {
    private final Function<BooleanSupplier, Runnable> runnableFactory;
    @Contended
    private final AtomicReference<Thread> threadOrNull = new AtomicReference<>(null);

    /**
     * Constructor for stoppable thread; it is recommended to use the static start(..) methods instead.
     *
     * @param runnableFactory   the factory for the runnable;
     *                          the <i>{@link #isRunning}</i> condition is passed to the factory as lambda
     * @param threadFactory     the factory to provide the thread
     */
    protected StoppableThread(final Function<BooleanSupplier, Runnable> runnableFactory,
                              final ThreadFactory threadFactory) {
        final Thread thread = threadFactory.newThread(this::run);
        this.runnableFactory = Objects.requireNonNull(runnableFactory);
        this.threadOrNull.set(thread);
        thread.start();
    }

    /**
     * Creates, starts and returns a new shutdownable thread.
     *
     * @param runnableFactory   the factory for the runnable;
     *                          the <i>{@link #isRunning}</i> condition is passed to the factory as lambda
     * @param threadFactory     the factory to provide the thread
     * @return the newly created and started stoppable thread
     */
    public static StoppableThread start(final Function<BooleanSupplier, Runnable> runnableFactory,
                                        final ThreadFactory threadFactory) {
        return new StoppableThread(runnableFactory, threadFactory);
    }

    private void run() {
        final Runnable runnable = runnableFactory.apply(this::isRunning);
        runnable.run();
    }

    public boolean isRunning() {
        return threadOrNull.get() != null;
    }

    @Override
    public void stop() {
        threadOrNull.set(null);
    }

    @Override
    public String toString() {
        final Thread thread = threadOrNull.get();
        return thread != null ? thread.getName() : "StoppableThread[STOPPED]";
    }
}
