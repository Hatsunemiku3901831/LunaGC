package emu.grasscutter.database;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;

class DatabaseWriteQueueTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private static void waitFor(CountDownLatch latch) {
        try { assertTrue(latch.await(5, TimeUnit.SECONDS)); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
    }

    @Test void fullQueueRunsOnTheSubmitterAndNeverDiscardsWork() throws Exception {
        var pool = new DatabaseWriteQueue("test", 1, 1);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var ran = new AtomicInteger();
        try {
            pool.submit(() -> { entered.countDown(); waitFor(release); ran.incrementAndGet(); });
            waitFor(entered);
            pool.submit(ran::incrementAndGet);
            var thread = Thread.currentThread();
            pool.submit(() -> { assertSame(thread, Thread.currentThread()); ran.incrementAndGet(); }).get();
            assertEquals(1, pool.snapshot().callerRuns());
            assertEquals(1, pool.snapshot().queued());
            release.countDown();
            assertTrue(pool.awaitIdle(TIMEOUT));
            assertEquals(3, ran.get());
            assertEquals(3, pool.snapshot().completed());
        } finally { release.countDown(); pool.shutdown(); assertTrue(pool.awaitIdle(TIMEOUT)); }
    }

    @Test void shutdownWaitIncludesWorkRunningOnASubmitter() throws Exception {
        var pool = new DatabaseWriteQueue("test", 1, 1);
        var entered = new CountDownLatch(1);
        var workerRelease = new CountDownLatch(1);
        var inlineEntered = new CountDownLatch(1);
        var inlineRelease = new CountDownLatch(1);
        var submitter = Executors.newSingleThreadExecutor();
        try {
            pool.submit(() -> { entered.countDown(); waitFor(workerRelease); });
            waitFor(entered);
            pool.submit(() -> {});
            var inline = submitter.submit(() -> pool.submit(() -> { inlineEntered.countDown(); waitFor(inlineRelease); }));
            waitFor(inlineEntered);
            pool.shutdown();
            workerRelease.countDown();
            assertTrue(pool.awaitTermination(2, TimeUnit.SECONDS));
            assertFalse(pool.awaitIdle(Duration.ofMillis(20)), "Executor termination alone misses caller-run writes");
            assertEquals(1, pool.snapshot().outstanding());
            inlineRelease.countDown();
            inline.get(5, TimeUnit.SECONDS);
            assertTrue(pool.awaitIdle(TIMEOUT));
        } finally { workerRelease.countDown(); inlineRelease.countDown(); pool.shutdown(); submitter.shutdownNow(); }
    }

    @Test void shutdownRejectsRatherThanReturningAnUnfinishedFuture() throws Exception {
        var pool = new DatabaseWriteQueue("test", 1, 1);
        pool.shutdown();
        assertThrows(RejectedExecutionException.class, () -> pool.submit(() -> {}));
        assertThrows(RejectedExecutionException.class, () -> pool.saveSnapshot(new Object(), () -> {}));
        assertTrue(pool.awaitIdle(TIMEOUT));
        assertEquals(2, pool.snapshot().rejected());
    }

    @Test void queuedSnapshotsCoalesceByIdentityButChangesDuringAWritingRunAreSaved() throws Exception {
        var pool = new DatabaseWriteQueue("test", 1, 16);
        var blocker = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var started = new CountDownLatch(1);
        var writingRelease = new CountDownLatch(1);
        var data = new AtomicInteger();
        var durable = new AtomicInteger();
        var writes = new AtomicInteger();
        try {
            pool.submit(() -> { blocker.countDown(); waitFor(release); });
            waitFor(blocker);
            for (int i = 1; i <= 100; i++) {
                data.set(i);
                pool.saveSnapshot(data, () -> {
                    int snapshot = data.get();
                    started.countDown();
                    waitFor(writingRelease);
                    durable.set(snapshot);
                    writes.incrementAndGet();
                });
            }
            assertEquals(1, pool.snapshot().queued());
            assertEquals(99, pool.snapshot().coalesced());
            release.countDown(); waitFor(started);
            data.set(101);
            pool.saveSnapshot(data, () -> { durable.set(data.get()); writes.incrementAndGet(); });
            writingRelease.countDown();
            assertTrue(pool.awaitIdle(TIMEOUT));
            assertEquals(2, writes.get());
            assertEquals(101, durable.get());
        } finally { release.countDown(); writingRelease.countDown(); pool.shutdown(); assertTrue(pool.awaitIdle(TIMEOUT)); }
    }

    @Test void equalObjectsStillHaveIndependentSnapshots() throws Exception {
        var pool = new DatabaseWriteQueue("test", 1, 4);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var writes = new AtomicInteger();
        try {
            pool.submit(() -> { entered.countDown(); waitFor(release); }); waitFor(entered);
            pool.saveSnapshot(new String("same"), writes::incrementAndGet);
            pool.saveSnapshot(new String("same"), writes::incrementAndGet);
            release.countDown(); assertTrue(pool.awaitIdle(TIMEOUT));
            assertEquals(2, writes.get());
        } finally { release.countDown(); pool.shutdown(); }
    }

    @Test void hiddenFutureFailuresAreVisibleAndDoNotStopSubsequentWrites() throws Exception {
        var pool = new DatabaseWriteQueue("test", 1, 8);
        try {
            var failure = pool.submit(() -> { throw new IllegalStateException("simulated database outage"); });
            assertThrows(ExecutionException.class, () -> failure.get(5, TimeUnit.SECONDS));
            assertEquals(42, pool.submit(() -> 42).get(5, TimeUnit.SECONDS));
            assertTrue(pool.awaitIdle(TIMEOUT));
            assertEquals(1, pool.snapshot().failed());
            assertEquals(2, pool.snapshot().completed());
            assertEquals(0, pool.snapshot().outstanding());
        } finally { pool.shutdown(); }
    }

    @Test void anExplicitForcedShutdownCancelsQueuedFuturesAndReleasesTheirAccounting() throws Exception {
        var pool = new DatabaseWriteQueue("test", 1, 8);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try {
            pool.submit(() -> {
                entered.countDown();
                try { release.await(); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            });
            waitFor(entered);
            var queued = pool.submit(() -> {});
            assertEquals(1, pool.shutdownNow().size());
            assertTrue(queued.isCancelled());
            assertTrue(pool.awaitIdle(TIMEOUT));
            assertEquals(1, pool.snapshot().cancelled());
            assertEquals(0, pool.snapshot().outstanding());
        } finally { release.countDown(); pool.shutdown(); }
    }

    @Test void startingOneScopedWriteKeepsTheOtherDestinationsPendingMarker() throws Exception {
        var pool=new DatabaseWriteQueue("scope",1,8);
        var object=new Object(); var first=new Object(); var second=new Object();
        var blocked=new CountDownLatch(1); var release=new CountDownLatch(1);
        var writing=new CountDownLatch(1); var writingRelease=new CountDownLatch(1);
        var firstWrites=new AtomicInteger(); var secondWrites=new AtomicInteger();
        try {
            pool.submit(()->{ blocked.countDown(); waitFor(release); }); waitFor(blocked);
            pool.saveSnapshot(object,first,()->{ writing.countDown(); waitFor(writingRelease); firstWrites.incrementAndGet(); });
            pool.saveSnapshot(object,second,secondWrites::incrementAndGet);
            release.countDown(); waitFor(writing);
            pool.saveSnapshot(object,second,secondWrites::incrementAndGet);
            pool.saveSnapshot(object,first,firstWrites::incrementAndGet);
            assertEquals(2,pool.snapshot().queued()); assertEquals(1,pool.snapshot().coalesced());
            writingRelease.countDown(); assertTrue(pool.awaitIdle(TIMEOUT));
            assertEquals(2,firstWrites.get()); assertEquals(1,secondWrites.get());
        } finally { release.countDown(); writingRelease.countDown(); pool.shutdown(); assertTrue(pool.awaitIdle(TIMEOUT)); }
    }

    @Test void forcedShutdownCancelsEveryDestinationOfAScopedObject() throws Exception {
        var pool=new DatabaseWriteQueue("scope",1,8);
        var entered=new CountDownLatch(1); var release=new CountDownLatch(1);
        var writes=new AtomicInteger(); var object=new Object();
        try {
            pool.submit(()->{ entered.countDown(); try { release.await(); } catch(InterruptedException e) { Thread.currentThread().interrupt(); } });
            waitFor(entered);
            pool.saveSnapshot(object,new Object(),writes::incrementAndGet);
            pool.saveSnapshot(object,new Object(),writes::incrementAndGet);
            assertEquals(2,pool.shutdownNow().size()); assertTrue(pool.awaitIdle(TIMEOUT));
            assertEquals(2,pool.snapshot().cancelled()); assertEquals(0,pool.snapshot().outstanding());
            assertEquals(0,writes.get());
            assertThrows(RejectedExecutionException.class,()->pool.saveSnapshot(object,new Object(),writes::incrementAndGet));
        } finally { release.countDown(); pool.shutdown(); assertTrue(pool.awaitIdle(TIMEOUT)); }
    }
}
