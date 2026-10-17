package emu.grasscutter.database;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

class DatabaseWriteBarrierTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException failed) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failed);
        }
    }

    private static void pending() throws Exception {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (!DatabaseWriteBarrier.maintenanceActive() && System.nanoTime() < deadline)
            Thread.sleep(1);
        assertTrue(DatabaseWriteBarrier.maintenanceActive());
    }

    @AfterEach
    void idle() throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(TIMEOUT));
        assertFalse(DatabaseWriteBarrier.maintenanceActive());
    }

    @Test
    void queuedCallerRunAndDirectWritesAllDrainBeforeMaintenance() throws Exception {
        var queue = new DatabaseWriteQueue("barrier", 1, 1);
        var jobs = Executors.newFixedThreadPool(3);
        var worker = new CountDownLatch(1);
        var releaseWorker = new CountDownLatch(1);
        var inline = new CountDownLatch(1);
        var releaseInline = new CountDownLatch(1);
        var direct = new CountDownLatch(1);
        var releaseDirect = new CountDownLatch(1);
        try {
            queue.submit(
                    () -> {
                        worker.countDown();
                        await(releaseWorker);
                    });
            await(worker);
            queue.submit(() -> {});
            var inlineJob =
                    jobs.submit(
                            () ->
                                    queue.submit(
                                            () -> {
                                                inline.countDown();
                                                await(releaseInline);
                                            }));
            await(inline);
            var directJob =
                    jobs.submit(
                            () ->
                                    DatabaseWriteBarrier.run(
                                            () -> {
                                                direct.countDown();
                                                await(releaseDirect);
                                            }));
            await(direct);
            var barrier =
                    jobs.submit(
                            () -> {
                                try (var ignored = DatabaseWriteBarrier.acquire(TIMEOUT)) {
                                    return true;
                                }
                            });
            pending();
            assertThrows(
                    RejectedExecutionException.class,
                    () -> queue.submit(() -> fail("new admission")));
            releaseWorker.countDown();
            assertThrows(TimeoutException.class, () -> barrier.get(30, TimeUnit.MILLISECONDS));
            releaseInline.countDown();
            inlineJob.get(5, TimeUnit.SECONDS);
            assertThrows(TimeoutException.class, () -> barrier.get(30, TimeUnit.MILLISECONDS));
            releaseDirect.countDown();
            directJob.get(5, TimeUnit.SECONDS);
            assertTrue(barrier.get(5, TimeUnit.SECONDS));
        } finally {
            releaseWorker.countDown();
            releaseInline.countDown();
            releaseDirect.countDown();
            queue.shutdown();
            assertTrue(queue.awaitIdle(TIMEOUT));
            jobs.shutdownNow();
        }
    }

    @Test
    void acceptedWritersMayFinishCausalChildWorkWhileExternalAdmissionIsFrozen() throws Exception {
        var queue = new DatabaseWriteQueue("nested-barrier", 1, 4);
        var jobs = Executors.newSingleThreadExecutor();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var writes = new AtomicInteger();
        try {
            queue.submit(
                    () -> {
                        entered.countDown();
                        await(release);
                        DatabaseWriteBarrier.run(writes::incrementAndGet);
                        queue.submit(writes::incrementAndGet);
                    });
            await(entered);
            var barrier =
                    jobs.submit(
                            () -> {
                                try (var ignored = DatabaseWriteBarrier.acquire(TIMEOUT)) {
                                    return writes.get();
                                }
                            });
            pending();
            assertThrows(
                    RejectedExecutionException.class,
                    () -> DatabaseWriteBarrier.run(() -> fail("external write")));
            release.countDown();
            assertEquals(2, barrier.get(5, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            queue.shutdown();
            assertTrue(queue.awaitIdle(TIMEOUT));
            jobs.shutdownNow();
        }
    }

    @Test
    void maintenanceTimeoutRestoresAdmissionWithoutCancellingAcceptedWork() throws Exception {
        var jobs = Executors.newSingleThreadExecutor();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try {
            var accepted =
                    jobs.submit(
                            () ->
                                    DatabaseWriteBarrier.run(
                                            () -> {
                                                entered.countDown();
                                                await(release);
                                            }));
            await(entered);
            assertThrows(
                    IllegalStateException.class,
                    () -> DatabaseWriteBarrier.acquire(Duration.ofMillis(25)));
            assertFalse(DatabaseWriteBarrier.maintenanceActive());
            assertEquals(7, DatabaseWriteBarrier.call(() -> 7));
            release.countDown();
            accepted.get(5, TimeUnit.SECONDS);
            try (var ignored = DatabaseWriteBarrier.acquire(TIMEOUT)) {
                assertTrue(DatabaseHelper.awaitIdle(TIMEOUT));
            }
        } finally {
            release.countDown();
            jobs.shutdownNow();
        }
    }

    @Test
    void interruptingADrainRestoresAdmissionAndPreservesTheInterruptFlag() throws Exception {
        var jobs = Executors.newSingleThreadExecutor();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var interrupted = new AtomicBoolean();
        Thread waiter =
                new Thread(
                        () -> {
                            assertThrows(
                                    IllegalStateException.class,
                                    () -> DatabaseWriteBarrier.acquire(TIMEOUT));
                            interrupted.set(Thread.currentThread().isInterrupted());
                        });
        try {
            var accepted =
                    jobs.submit(
                            () ->
                                    DatabaseWriteBarrier.run(
                                            () -> {
                                                entered.countDown();
                                                await(release);
                                            }));
            await(entered);
            waiter.start();
            pending();
            waiter.interrupt();
            waiter.join(5000);
            assertFalse(waiter.isAlive());
            assertTrue(interrupted.get());
            assertFalse(DatabaseWriteBarrier.maintenanceActive());
            DatabaseWriteBarrier.run(() -> {});
            release.countDown();
            accepted.get(5, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            waiter.interrupt();
            waiter.join(5000);
            jobs.shutdownNow();
        }
    }

    @Test
    void onlyTheOwnerCanCloseAndNestedMaintenanceKeepsAdmissionFrozen() throws Exception {
        var jobs = Executors.newSingleThreadExecutor();
        try (var outer = DatabaseWriteBarrier.acquire(TIMEOUT)) {
            jobs.submit(() -> assertThrows(IllegalStateException.class, outer::close))
                    .get(5, TimeUnit.SECONDS);
            try (var inner = DatabaseWriteBarrier.acquire(TIMEOUT)) {
                assertEquals(3, DatabaseWriteBarrier.call(() -> 3));
                inner.close();
            }
            jobs.submit(
                            () ->
                                    assertThrows(
                                            RejectedExecutionException.class,
                                            () ->
                                                    DatabaseWriteBarrier.run(
                                                            () -> fail("non-owner"))))
                    .get(5, TimeUnit.SECONDS);
        } finally {
            jobs.shutdownNow();
        }
    }

    @Test
    void maintenanceOwnerCannotDispatchAnAsynchronousWrite() throws Exception {
        var queue = new DatabaseWriteQueue("owner-barrier", 1, 4);
        try (var ignored = DatabaseWriteBarrier.acquire(TIMEOUT)) {
            DatabaseWriteBarrier.run(() -> {});
            assertThrows(
                    RejectedExecutionException.class,
                    () -> queue.submit(() -> fail("async owner write")));
            assertThrows(
                    RejectedExecutionException.class,
                    () -> queue.saveSnapshot(new Object(), () -> fail("async snapshot")));
        } finally {
            queue.shutdown();
            assertTrue(queue.awaitIdle(TIMEOUT));
        }
    }

    @Test
    void anAdmittedWriterCannotWaitForItsOwnBarrier() {
        DatabaseWriteBarrier.run(
                () ->
                        assertThrows(
                                IllegalStateException.class,
                                () -> DatabaseWriteBarrier.acquire(TIMEOUT)));
        assertThrows(
                IllegalArgumentException.class, () -> DatabaseWriteBarrier.acquire(Duration.ZERO));
        assertThrows(
                IllegalStateException.class,
                () ->
                        DatabaseWriteBarrier.run(
                                () -> {
                                    throw new IllegalStateException("write failed");
                                }));
    }

    @Test
    void aCoalescedSnapshotCannotSlipThroughFrozenExternalAdmission() throws Exception {
        var queue = new DatabaseWriteQueue("coalesced-barrier", 1, 4);
        var jobs = Executors.newSingleThreadExecutor();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var instance = new Object();
        var writes = new AtomicInteger();
        try {
            queue.submit(
                    () -> {
                        entered.countDown();
                        await(release);
                    });
            await(entered);
            queue.saveSnapshot(instance, writes::incrementAndGet);
            var barrier =
                    jobs.submit(
                            () -> {
                                try (var ignored = DatabaseWriteBarrier.acquire(TIMEOUT)) {
                                    return writes.get();
                                }
                            });
            pending();
            assertThrows(
                    RejectedExecutionException.class,
                    () -> queue.saveSnapshot(instance, writes::incrementAndGet));
            release.countDown();
            assertEquals(1, barrier.get(5, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            queue.shutdown();
            assertTrue(queue.awaitIdle(TIMEOUT));
            jobs.shutdownNow();
        }
    }

    @Test
    void ordinaryIdleChecksIncludeDirectWrites() throws Exception {
        var jobs = Executors.newSingleThreadExecutor();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try {
            var direct =
                    jobs.submit(
                            () ->
                                    DatabaseWriteBarrier.run(
                                            () -> {
                                                entered.countDown();
                                                await(release);
                                            }));
            await(entered);
            assertFalse(DatabaseHelper.awaitIdle(Duration.ofMillis(20)));
            release.countDown();
            direct.get(5, TimeUnit.SECONDS);
            assertTrue(DatabaseHelper.awaitIdle(TIMEOUT));
        } finally {
            release.countDown();
            jobs.shutdownNow();
        }
    }
}
