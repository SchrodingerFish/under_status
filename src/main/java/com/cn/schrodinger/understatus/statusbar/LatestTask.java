package com.cn.schrodinger.understatus.statusbar;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

public final class LatestTask<T> implements AutoCloseable {

    private final Executor executor;
    private final AtomicLong generation = new AtomicLong();
    private volatile boolean closed;

    public LatestTask(Executor executor) {
        this.executor = Objects.requireNonNull(executor);
    }

    public void submit(Callable<T> work, Consumer<T> success, Consumer<Throwable> failure) {
        Objects.requireNonNull(work);
        Objects.requireNonNull(success);
        Objects.requireNonNull(failure);
        if (closed) throw new IllegalStateException("Task is closed");
        long submitted = generation.incrementAndGet();
        executor.execute(() -> {
            try {
                T result = work.call();
                SwingUtilities.invokeLater(() -> deliver(submitted, () -> success.accept(result)));
            } catch (Throwable error) {
                SwingUtilities.invokeLater(() -> deliver(submitted, () -> failure.accept(error)));
            }
        });
    }

    private void deliver(long submitted, Runnable callback) {
        if (!closed && generation.get() == submitted) callback.run();
    }

    @Override
    public void close() {
        closed = true;
        generation.incrementAndGet();
    }
}
