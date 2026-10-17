package emu.grasscutter.database;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.Grasscutter;

import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

class DatabaseWriteFenceTest {
    private final CountDownLatch release = new CountDownLatch(1);
    private ExecutorService jobs;
    private final List<DatabaseWriteQueue> queues = new ArrayList<>();

    @BeforeEach
    void setup() {
        Grasscutter.getConfig();
        jobs = Executors.newFixedThreadPool(3);
    }

    @AfterEach
    void close() throws Exception {
        release.countDown();
        queues.forEach(DatabaseWriteQueue::shutdown);
        jobs.shutdownNow();
        for (var queue : queues) assertTrue(queue.awaitIdle(Duration.ofSeconds(5)));
        assertTrue(jobs.awaitTermination(5, TimeUnit.SECONDS));
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
    }

    private void held() {
        try {
            assertTrue(release.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    private DatabaseWriteQueue queue(int capacity) {
        var queue = new DatabaseWriteQueue("fence-" + queues.size(), 1, capacity);
        queues.add(queue);
        return queue;
    }

    private static void waitFor(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    @Test
    void unrelatedDirectWritesDoNotDelayACompletedOperation() throws Exception {
        var fence = new DatabaseWriteFence();
        fence.run(() -> {});
        var entered = new CountDownLatch(1);
        var unrelated =
                jobs.submit(
                        () ->
                                DatabaseWriteBarrier.run(
                                        () -> {
                                            entered.countDown();
                                            held();
                                        }));
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        try {
            assertTrue(fence.await(Duration.ofMillis(30)));
        } finally {
            release.countDown();
            unrelated.get(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void anotherAdmittedWriterCanWaitForThisCompletedOperationWithoutWaitingForItself() {
        var fence = new DatabaseWriteFence();
        fence.run(() -> {});
        DatabaseWriteBarrier.run(
                () -> assertTrue(assertDoesNotThrow(() -> fence.await(Duration.ofMillis(30)))));
    }

    @Test
    void aFenceHasOneOperationAndItsResultAndValidatesWaits() throws Exception {
        var fence = new DatabaseWriteFence();
        assertFalse(fence.isDrained());
        assertFalse(fence.await(Duration.ZERO));
        assertThrows(NullPointerException.class, () -> fence.call(null));
        assertEquals(42, fence.call(() -> 42));
        assertTrue(fence.await(Duration.ZERO));
        assertTrue(fence.isDrained());
        assertThrows(IllegalStateException.class, () -> fence.run(() -> fail("reused")));
        assertThrows(IllegalArgumentException.class, () -> fence.await(Duration.ofNanos(-1)));
        assertThrows(NullPointerException.class, () -> fence.await(null));
    }

    @Test
    void queuedChildrenAndGrandchildrenRemainTrackedAfterTheCallerReturns() throws Exception {
        var first = queue(4);
        var second = queue(4);
        var fence = new DatabaseWriteFence();
        var entered = new CountDownLatch(1);
        var childEntered = new CountDownLatch(1);
        var childRelease = new CountDownLatch(1);
        var writes = new AtomicInteger();
        try {
            fence.run(
                    () ->
                            first.submit(
                                    () -> {
                                        entered.countDown();
                                        held();
                                        second.submit(
                                                () -> {
                                                    childEntered.countDown();
                                                    waitFor(childRelease);
                                                    DatabaseWriteBarrier.run(
                                                            writes::incrementAndGet);
                                                });
                                    }));
            waitFor(entered);
            assertFalse(fence.await(Duration.ofMillis(20)));
            release.countDown();
            waitFor(childEntered);
            assertFalse(fence.await(Duration.ofMillis(20)));
            childRelease.countDown();
            assertTrue(fence.await(Duration.ofSeconds(2)));
            assertEquals(1, writes.get());
        } finally {
            release.countDown();
            childRelease.countDown();
        }
    }

    @Test
    void nestedFencesRestoreTheOuterContextWithoutJoiningUnrelatedWork() throws Exception {
        var queue = queue(4);
        var outer = new DatabaseWriteFence();
        var inner = new DatabaseWriteFence();
        outer.run(
                () -> {
                    inner.run(() -> {});
                    assertTrue(assertDoesNotThrow(() -> inner.await(Duration.ZERO)));
                    queue.submit(this::held);
                    assertThrows(IllegalStateException.class, () -> outer.await(Duration.ZERO));
                });
        assertTrue(inner.isDrained());
        assertFalse(outer.await(Duration.ofMillis(20)));
        assertTrue(DatabaseWriteBarrier.currentFences().isEmpty());
        release.countDown();
        assertTrue(outer.await(Duration.ofSeconds(2)));
    }

    @Test
    void callerRunWorkBelongsToItsFenceWhileOtherQueueWorkRemainsUnrelated() throws Exception {
        var queue = queue(1);
        var entered = new CountDownLatch(1);
        queue.submit(
                () -> {
                    entered.countDown();
                    held();
                });
        waitFor(entered);
        queue.submit(() -> {});
        var fence = new DatabaseWriteFence();
        var inline = new CountDownLatch(1);
        var releaseInline = new CountDownLatch(1);
        try {
            var caller =
                    jobs.submit(
                            () ->
                                    fence.run(
                                            () ->
                                                    queue.submit(
                                                            () -> {
                                                                inline.countDown();
                                                                waitFor(releaseInline);
                                                            })));
            waitFor(inline);
            assertFalse(fence.await(Duration.ofMillis(20)));
            releaseInline.countDown();
            caller.get(2, TimeUnit.SECONDS);
            assertTrue(fence.await(Duration.ofSeconds(2)));
            assertEquals(1, queue.snapshot().callerRuns());
            assertFalse(queue.awaitIdle(Duration.ofMillis(20)));
        } finally {
            releaseInline.countDown();
            release.countDown();
        }
    }

    @Test
    void aCoalescedSnapshotAlsoTracksChildrenOfTheOriginalWrite() throws Exception {
        var queue = queue(4);
        var children = queue(4);
        var entered = new CountDownLatch(1);
        queue.submit(
                () -> {
                    entered.countDown();
                    held();
                });
        waitFor(entered);
        var object = new Object();
        var childEntered = new CountDownLatch(1);
        var childRelease = new CountDownLatch(1);
        var writes = new AtomicInteger();
        try {
            queue.saveSnapshot(
                    object,
                    () -> {
                        writes.incrementAndGet();
                        children.submit(
                                () -> {
                                    childEntered.countDown();
                                    waitFor(childRelease);
                                });
                    });
            var fence = new DatabaseWriteFence();
            fence.run(() -> queue.saveSnapshot(object, () -> fail("coalesced callback ran")));
            assertFalse(fence.await(Duration.ofMillis(20)));
            release.countDown();
            waitFor(childEntered);
            assertEquals(1, writes.get());
            assertFalse(fence.await(Duration.ofMillis(20)));
            childRelease.countDown();
            assertTrue(fence.await(Duration.ofSeconds(2)));
            assertEquals(1, queue.snapshot().coalesced());
        } finally {
            release.countDown();
            childRelease.countDown();
        }
    }

    @Test
    void independentFencesCoalescingTheSamePendingSnapshotBothWaitForItsWrite() throws Exception {
        var queue = queue(4);
        var entered = new CountDownLatch(1);
        queue.submit(
                () -> {
                    entered.countDown();
                    held();
                });
        waitFor(entered);
        var a = new DatabaseWriteFence();
        var b = new DatabaseWriteFence();
        var object = new Object();
        var writes = new AtomicInteger();
        a.run(() -> queue.saveSnapshot(object, writes::incrementAndGet));
        b.run(() -> queue.saveSnapshot(object, () -> fail("duplicate")));
        assertFalse(a.await(Duration.ofMillis(20)));
        assertFalse(b.await(Duration.ofMillis(20)));
        release.countDown();
        assertTrue(a.await(Duration.ofSeconds(2)));
        assertTrue(b.await(Duration.ofSeconds(2)));
        assertEquals(1, writes.get());
    }

    @Test
    void aLateCompletionCannotRemoveANewerSnapshotOfTheSameObject() throws Exception {
        var queue = queue(8);
        var object = new Object();
        var firstEntered = new CountDownLatch(1);
        var scheduleChild = new CountDownLatch(1);
        var blockerEntered = new CountDownLatch(1);
        var writes = new AtomicInteger();
        var first = new DatabaseWriteFence();
        var coalesced = new DatabaseWriteFence();
        try {
            first.run(
                    () ->
                            queue.saveSnapshot(
                                    object,
                                    () -> {
                                        firstEntered.countDown();
                                        waitFor(scheduleChild);
                                        queue.saveSnapshot(object, writes::incrementAndGet);
                                    }));
            waitFor(firstEntered);
            queue.submit(
                    () -> {
                        blockerEntered.countDown();
                        held();
                    });
            scheduleChild.countDown();
            waitFor(blockerEntered);
            coalesced.run(() -> queue.saveSnapshot(object, () -> fail("new snapshot lost")));
            assertEquals(1, queue.snapshot().coalesced());
            assertFalse(coalesced.await(Duration.ofMillis(20)));
            release.countDown();
            assertTrue(first.await(Duration.ofSeconds(2)));
            assertTrue(coalesced.await(Duration.ofSeconds(2)));
            assertEquals(1, writes.get());
        } finally {
            scheduleChild.countDown();
            release.countDown();
        }
    }

    @Test
    void cancellingAQueuedCoalescedSnapshotReleasesItsFenceWithoutRunningTheWrite()
            throws Exception {
        var queue = queue(4);
        var entered = new CountDownLatch(1);
        queue.submit(
                () -> {
                    entered.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                });
        waitFor(entered);
        var object = new Object();
        var writes = new AtomicInteger();
        queue.saveSnapshot(object, writes::incrementAndGet);
        var fence = new DatabaseWriteFence();
        fence.run(() -> queue.saveSnapshot(object, writes::incrementAndGet));
        assertFalse(fence.await(Duration.ofMillis(20)));
        assertEquals(1, queue.shutdownNow().size());
        assertTrue(fence.await(Duration.ofSeconds(2)));
        assertEquals(0, writes.get());
        assertEquals(1, queue.snapshot().cancelled());
    }

    @Test
    void rejectedSnapshotAdmissionDoesNotLeakFenceReferences() throws Exception {
        var queue = queue(4);
        queue.shutdown();
        var fence = new DatabaseWriteFence();
        assertThrows(
                RejectedExecutionException.class,
                () ->
                        fence.run(
                                () ->
                                        queue.saveSnapshot(
                                                new Object(), () -> fail("rejected write"))));
        assertTrue(fence.await(Duration.ZERO));
        assertTrue(DatabaseWriteBarrier.currentFences().isEmpty());
    }

    @Test
    void operationFailureStillTracksAlreadyAcceptedWrites() throws Exception {
        var queue = queue(4);
        var fence = new DatabaseWriteFence();
        var failure = new IllegalStateException("Injected operation failure");
        assertSame(
                failure,
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                fence.run(
                                        () -> {
                                            queue.submit(this::held);
                                            throw failure;
                                        })));
        assertFalse(fence.await(Duration.ofMillis(20)));
        assertTrue(DatabaseWriteBarrier.currentFences().isEmpty());
        release.countDown();
        assertTrue(fence.await(Duration.ofSeconds(2)));
    }

    @Test
    void failedQueuedWritesAreFinishedButAreNotReportedAsPersistedSuccess() throws Exception {
        var queue = queue(4);
        var fence = new DatabaseWriteFence();
        fence.run(
                () ->
                        queue.submit(
                                () -> {
                                    throw new IllegalStateException("Injected queue failure");
                                }));
        assertTrue(fence.await(Duration.ofSeconds(2)));
        assertTrue(queue.awaitIdle(Duration.ofSeconds(2)));
        assertEquals(1, queue.snapshot().failed());
    }

    @Test
    void drainCallbacksRunOnceAndTheFenceDoesNotLeakIntoTheNextOperation() throws Exception {
        var queue = queue(4);
        var fence = new DatabaseWriteFence();
        var callbacks = new AtomicInteger();
        var callbackFinished = new CountDownLatch(1);
        fence.whenDrained(
                () -> {
                    assertFalse(DatabaseWriteBarrier.currentFences().contains(fence));
                    callbacks.incrementAndGet();
                    callbackFinished.countDown();
                });
        fence.run(() -> queue.submit(() -> {}));
        assertTrue(fence.await(Duration.ofSeconds(2)));
        waitFor(callbackFinished);
        fence.whenDrained(callbacks::incrementAndGet);
        assertEquals(2, callbacks.get());
        var next =
                queue.submit(
                        () -> {
                            assertTrue(DatabaseWriteBarrier.currentFences().isEmpty());
                            held();
                        });
        assertTrue(fence.await(Duration.ZERO));
        release.countDown();
        next.get(2, TimeUnit.SECONDS);
    }

    @Test
    void interruptingAFenceWaitDoesNotCancelAcceptedWrites() throws Exception {
        var queue = queue(4);
        var fence = new DatabaseWriteFence();
        fence.run(() -> queue.submit(this::held));
        var entered = new CountDownLatch(1);
        var interrupted = new AtomicBoolean();
        var waiter =
                new Thread(
                        () -> {
                            entered.countDown();
                            try {
                                fence.await(Duration.ofSeconds(5));
                            } catch (InterruptedException expected) {
                                interrupted.set(true);
                                Thread.currentThread().interrupt();
                            }
                        });
        try {
            waiter.start();
            waitFor(entered);
            waiter.interrupt();
            waiter.join(2000);
            assertFalse(waiter.isAlive());
            assertTrue(interrupted.get());
            assertFalse(fence.isDrained());
            release.countDown();
            assertTrue(fence.await(Duration.ofSeconds(2)));
        } finally {
            release.countDown();
            waiter.interrupt();
            waiter.join(2000);
        }
    }
}
