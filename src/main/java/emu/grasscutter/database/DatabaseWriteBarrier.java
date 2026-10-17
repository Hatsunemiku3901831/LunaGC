package emu.grasscutter.database;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** Freeze external admission and drain accepted queued, caller-run and direct writes. */
public final class DatabaseWriteBarrier implements AutoCloseable {
    private static final Object gate = new Object();
    private static final ThreadLocal<Integer> depth = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Set<DatabaseWriteFence>> fences =
            ThreadLocal.withInitial(Set::of);
    private static long admitted;
    private static boolean pending;
    private static Thread owner;
    private static int ownerDepth;
    private final Thread holder;
    private boolean closed;

    private DatabaseWriteBarrier(Thread holder) {
        this.holder = holder;
    }

    static final class Permit implements AutoCloseable {
        private final AtomicBoolean closed = new AtomicBoolean();
        private final Set<DatabaseWriteFence> tracked = currentFences();

        Permit() {
            tracked.forEach(DatabaseWriteFence::retain);
        }

        Scope enter() {
            return new Scope(tracked);
        }

        public void close() {
            if (closed.compareAndSet(false, true)) {
                synchronized (gate) {
                    admitted--;
                    gate.notifyAll();
                }
                tracked.forEach(DatabaseWriteFence::release);
            }
        }
    }

    static final class Scope implements AutoCloseable {
        private final int before = depth.get();
        private final Set<DatabaseWriteFence> beforeFences = currentFences();

        Scope(Set<DatabaseWriteFence> tracked) {
            depth.set(before + 1);
            fences.set(tracked);
        }

        public void close() {
            if (before == 0) depth.remove();
            else depth.set(before);
            restoreFences(beforeFences);
        }
    }

    static Set<DatabaseWriteFence> currentFences() {
        return fences.get();
    }

    private static void restoreFences(Set<DatabaseWriteFence> previous) {
        if (previous.isEmpty()) fences.remove();
        else fences.set(previous);
    }

    static <T> T withFences(Set<DatabaseWriteFence> added, Supplier<T> operation) {
        if (added.isEmpty()) return operation.get();
        var previous = currentFences();
        var inherited = new HashSet<>(previous);
        inherited.addAll(added);
        fences.set(Set.copyOf(inherited));
        try {
            return operation.get();
        } finally {
            restoreFences(previous);
        }
    }

    static Permit admit() {
        synchronized (gate) {
            // Previously admitted work can finish its causal child writes while we drain.
            // A maintenance owner may only write synchronously, never dispatch new async work.
            if (owner != null || pending && depth.get() == 0)
                throw new RejectedExecutionException("Database write maintenance is active");
            var permit = new Permit();
            admitted++;
            return permit;
        }
    }

    public static <T> T call(Supplier<T> operation) {
        boolean nested;
        synchronized (gate) {
            nested = depth.get() > 0 || owner == Thread.currentThread();
        }
        if (nested) return operation.get();
        try (var permit = admit();
                var scope = permit.enter()) {
            return operation.get();
        }
    }

    public static void run(Runnable operation) {
        call(
                () -> {
                    operation.run();
                    return null;
                });
    }

    static boolean maintenanceActive() {
        synchronized (gate) {
            return pending || owner != null;
        }
    }

    static void requireMaintenanceCaller() {
        if (depth.get() > 0)
            throw new IllegalStateException("A database writer cannot acquire maintenance");
    }

    public static DatabaseWriteBarrier acquire(Duration timeout) {
        if (timeout.isNegative() || timeout.isZero())
            throw new IllegalArgumentException("Positive timeout required");
        long deadline = System.nanoTime() + timeout.toNanos();
        boolean requestedHere = false;
        synchronized (gate) {
            if (owner == Thread.currentThread()) {
                ownerDepth++;
                return new DatabaseWriteBarrier(owner);
            }
            if (depth.get() > 0)
                throw new IllegalStateException("A database writer cannot acquire maintenance");
            try {
                while (pending || owner != null) waitUntil(deadline);
                pending = true;
                requestedHere = true;
                while (admitted != 0) waitUntil(deadline);
                owner = Thread.currentThread();
                ownerDepth = 1;
                pending = false;
                requestedHere = false;
                return new DatabaseWriteBarrier(owner);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "Interrupted while draining database writes", interrupted);
            } finally {
                if (requestedHere) {
                    pending = false;
                    gate.notifyAll();
                }
            }
        }
    }

    private static void waitUntil(long deadline) throws InterruptedException {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) throw new IllegalStateException("Timed out draining database writes");
        TimeUnit.NANOSECONDS.timedWait(gate, remaining);
    }

    static boolean awaitIdle(Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        synchronized (gate) {
            if (depth.get() > 0)
                throw new IllegalStateException(
                        "A database writer cannot await its own completion");
            while (admitted != 0) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) return false;
                TimeUnit.NANOSECONDS.timedWait(gate, remaining);
            }
            return true;
        }
    }

    @Override
    public void close() {
        synchronized (gate) {
            if (Thread.currentThread() != holder)
                throw new IllegalStateException("Maintenance owner required");
            if (closed) return;
            closed = true;
            if (--ownerDepth == 0) {
                owner = null;
                gate.notifyAll();
            }
        }
    }
}
