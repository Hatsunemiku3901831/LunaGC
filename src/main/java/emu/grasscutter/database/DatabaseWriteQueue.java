package emu.grasscutter.database;

import emu.grasscutter.Grasscutter;

import io.netty.util.concurrent.FastThreadLocalThread;

import java.time.Duration;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded database work, including caller-run work in idle and shutdown accounting. */
public final class DatabaseWriteQueue extends ThreadPoolExecutor {
    private final String name;
    private final int capacity;
    private final Object progress = new Object();
    private long outstanding;
    private final AtomicLong completed = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong callerRuns = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();
    private final AtomicLong coalesced = new AtomicLong();
    private final AtomicLong cancelled = new AtomicLong();
    private static final Object defaultScope = new Object();
    private final Map<Object, Map<Object, PendingSnapshot>> pending = new IdentityHashMap<>();

    public DatabaseWriteQueue(String name, int threads, int capacity) {
        super(
                threads,
                threads,
                60,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(capacity),
                new ThreadFactory() {
                    private final AtomicLong sequence = new AtomicLong();

                    public Thread newThread(Runnable task) {
                        return new FastThreadLocalThread(
                                task, "database-" + name + "-" + sequence.incrementAndGet());
                    }
                });
        this.name = name;
        this.capacity = capacity;
        setRejectedExecutionHandler(
                (task, executor) -> {
                    if (executor.isShutdown())
                        throw new RejectedExecutionException("Database queue is closed: " + name);
                    callerRuns.incrementAndGet();
                    task.run();
                });
    }

    @Override
    public void execute(Runnable command) {
        Objects.requireNonNull(command);
        DatabaseWriteBarrier.Permit permit;
        try {
            permit = DatabaseWriteBarrier.admit();
        } catch (RejectedExecutionException rejected) {
            this.rejected.incrementAndGet();
            throw rejected;
        }
        synchronized (progress) {
            if (isShutdown()) {
                permit.close();
                rejected.incrementAndGet();
                throw new RejectedExecutionException("Database queue is closed: " + name);
            }
            outstanding++;
        }
        // FutureTask hides its exception from ThreadPoolExecutor. Inspect it even for inline work.
        var tracked = new TrackedOperation(command, permit);
        try {
            super.execute(tracked);
        } catch (RuntimeException exception) {
            rejected.incrementAndGet();
            tracked.settle(false);
            throw exception;
        }
    }

    private final class TrackedOperation implements Runnable {
        private final Runnable command;
        private final AtomicBoolean settled = new AtomicBoolean();
        private final DatabaseWriteBarrier.Permit permit;

        private TrackedOperation(Runnable command, DatabaseWriteBarrier.Permit permit) {
            this.command = command;
            this.permit = permit;
        }

        @Override
        public void run() {
            try (var scope = permit.enter()) {
                command.run();
                if (command instanceof Future<?> future && future.isDone()) future.get();
            } catch (Throwable exception) {
                failed.incrementAndGet();
                var cause =
                        exception instanceof ExecutionException ? exception.getCause() : exception;
                Grasscutter.getLogger().error("Database {} operation failed.", name, cause);
                if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            } finally {
                settle(true);
            }
        }

        private void settle(boolean wasRun) {
            if (settled.compareAndSet(false, true)) {
                if (wasRun) completed.incrementAndGet();
                finished();
                permit.close();
            }
        }

        private void cancel() {
            if (command instanceof Future<?> future) future.cancel(false);
            cancelled.incrementAndGet();
            settle(false);
        }
    }

    /**
     * Normal shutdown never calls this; explicit forced shutdown must also settle returned futures.
     */
    @Override
    public List<Runnable> shutdownNow() {
        var dropped = super.shutdownNow();
        dropped.forEach(task -> ((TrackedOperation) task).cancel());
        synchronized (pending) {
            pending.clear();
        }
        return dropped;
    }

    private void finished() {
        synchronized (progress) {
            outstanding--;
            progress.notifyAll();
        }
    }

    /** Combine queued writes of the same live instance; writes already running are never folded. */
    public void saveSnapshot(Object object, Runnable write) {
        saveSnapshot(object, defaultScope, write);
    }

    /** A different destination must receive its own write, even for the same live object. */
    public void saveSnapshot(Object object, Object destination, Runnable write) {
        DatabaseWriteBarrier.run(() -> saveSnapshotAdmitted(object, destination, write));
    }

    private void saveSnapshotAdmitted(Object object, Object destination, Runnable write) {
        final PendingSnapshot snapshot;
        synchronized (pending) {
            if (isShutdown()) {
                rejected.incrementAndGet();
                throw new RejectedExecutionException("Database queue is closed: " + name);
            }
            var destinations = pending.computeIfAbsent(object, key -> new IdentityHashMap<>());
            var existing = destinations.get(destination);
            if (existing != null) {
                existing.followCurrent();
                coalesced.incrementAndGet();
                return;
            }
            snapshot = new PendingSnapshot(object, destination, write);
            destinations.put(destination, snapshot);
        }
        var task = snapshot.task();
        try {
            execute(task);
        } catch (RuntimeException | Error exception) {
            task.cancel(false);
            throw exception;
        }
    }

    /**
     * Caller holds pending; a late completion cannot remove a newer snapshot of the same object.
     */
    private void removePending(PendingSnapshot snapshot) {
        var destinations = pending.get(snapshot.object);
        if (destinations == null || destinations.get(snapshot.destination) != snapshot) return;
        destinations.remove(snapshot.destination);
        if (destinations.isEmpty()) pending.remove(snapshot.object);
    }

    private final class PendingSnapshot {
        final Object object, destination;
        final Runnable write;
        Set<DatabaseWriteFence> followers;

        PendingSnapshot(Object object, Object destination, Runnable write) {
            this.object = object;
            this.destination = destination;
            this.write = write;
        }

        // Only coalesced callers need extra references; the original task has its own permit.
        void followCurrent() {
            for (var fence : DatabaseWriteBarrier.currentFences()) {
                if (followers == null) followers = new HashSet<>();
                if (!followers.contains(fence)) {
                    fence.retain();
                    followers.add(fence);
                }
            }
        }

        FutureTask<Void> task() {
            return new FutureTask<>(
                    () -> {
                        final Set<DatabaseWriteFence> inherited;
                        synchronized (pending) {
                            removePending(this);
                            inherited = followers == null ? Set.of() : Set.copyOf(followers);
                        }
                        return DatabaseWriteBarrier.withFences(
                                inherited,
                                () -> {
                                    write.run();
                                    return null;
                                });
                    }) {
                @Override
                protected void done() {
                    final Set<DatabaseWriteFence> released;
                    synchronized (pending) {
                        removePending(PendingSnapshot.this);
                        released = followers == null ? Set.of() : followers;
                        followers = null;
                    }
                    // Drain callbacks can acquire queue locks, so release outside pending.
                    released.forEach(DatabaseWriteFence::release);
                }
            };
        }
    }

    /** Includes tasks running on submitters when the queue is full. */
    public boolean awaitIdle(Duration timeout) throws InterruptedException {
        long remaining = timeout.toNanos();
        long deadline = System.nanoTime() + remaining;
        synchronized (progress) {
            while (outstanding != 0) {
                if (remaining <= 0) return false;
                TimeUnit.NANOSECONDS.timedWait(progress, remaining);
                remaining = deadline - System.nanoTime();
            }
            return true;
        }
    }

    public Snapshot snapshot() {
        synchronized (progress) {
            return new Snapshot(
                    name,
                    capacity,
                    getQueue().size(),
                    getActiveCount(),
                    outstanding,
                    completed.get(),
                    failed.get(),
                    callerRuns.get(),
                    rejected.get(),
                    coalesced.get(),
                    cancelled.get(),
                    isShutdown());
        }
    }

    public record Snapshot(
            String name,
            int capacity,
            int queued,
            int active,
            long outstanding,
            long completed,
            long failed,
            long callerRuns,
            long rejected,
            long coalesced,
            long cancelled,
            boolean shutdown) {}
}
