package emu.grasscutter.database;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;

/** A drain handle for one operation and its database work. */
public final class DatabaseWriteFence {
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicInteger outstanding = new AtomicInteger(1);
    private final CompletableFuture<Void> drained = new CompletableFuture<>();

    public <T> T call(Supplier<T> operation) {
        Objects.requireNonNull(operation, "Operation");
        if (!started.compareAndSet(false, true))
            throw new IllegalStateException("A write fence can only be started once");
        try {
            return DatabaseWriteBarrier.withFences(
                    Set.of(this), () -> DatabaseWriteBarrier.call(operation));
        } finally {
            release();
        }
    }

    public void run(Runnable operation) {
        call(
                () -> {
                    operation.run();
                    return null;
                });
    }

    public boolean await(Duration timeout) throws InterruptedException {
        Objects.requireNonNull(timeout, "Timeout");
        if (timeout.isNegative())
            throw new IllegalArgumentException("Nonnegative timeout required");
        if (DatabaseWriteBarrier.currentFences().contains(this))
            throw new IllegalStateException("An operation cannot await its own write fence");
        try {
            drained.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
            return true;
        } catch (TimeoutException pending) {
            return false;
        } catch (ExecutionException impossible) {
            throw new IllegalStateException("Invalid write fence completion", impossible);
        }
    }

    public boolean isDrained() {
        return drained.isDone();
    }

    public void whenDrained(Runnable action) {
        drained.thenRun(Objects.requireNonNull(action, "Drain callback"));
    }

    void retain() {
        for (; ; ) {
            int before = outstanding.get();
            if (before == 0) throw new IllegalStateException("Write fence has already drained");
            if (outstanding.compareAndSet(before, Math.addExact(before, 1))) return;
        }
    }

    void release() {
        int remaining = outstanding.decrementAndGet();
        if (remaining == 0) drained.complete(null);
        else if (remaining < 0) throw new IllegalStateException("Write fence released twice");
    }
}
