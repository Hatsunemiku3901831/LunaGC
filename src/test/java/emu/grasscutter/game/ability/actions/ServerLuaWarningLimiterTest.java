package emu.grasscutter.game.ability.actions;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ServerLuaWarningLimiterTest {
    @Test
    void firstWarningAndIntervalBoundaryReportSuppression() {
        var limiter = new ActionServerLuaCall.MissingFunctionWarningLimiter(100);
        assertEquals(0, limiter.acquireWarning(1_000));
        assertEquals(-1, limiter.acquireWarning(1_000));
        assertEquals(-1, limiter.acquireWarning(1_099));
        assertEquals(2, limiter.acquireWarning(1_100));
        assertEquals(0, limiter.acquireWarning(1_200));
    }

    @Test
    void monotonicClockWraparoundPreservesTheInterval() {
        var limiter = new ActionServerLuaCall.MissingFunctionWarningLimiter(100);
        long first = Long.MAX_VALUE - 50;
        assertEquals(0, limiter.acquireWarning(first));
        assertEquals(-1, limiter.acquireWarning(first + 99));
        assertEquals(1, limiter.acquireWarning(first + 100));
    }

    @Test
    void theMinimumClockValueIsARealTimestampRatherThanAnInitializationFlag() {
        var limiter = new ActionServerLuaCall.MissingFunctionWarningLimiter(100);
        assertEquals(0, limiter.acquireWarning(Long.MIN_VALUE));
        assertEquals(-1, limiter.acquireWarning(Long.MIN_VALUE));
        assertEquals(1, limiter.acquireWarning(Long.MIN_VALUE + 100));
    }

    @Test
    void concurrentCallersShareOneBudget() throws Exception {
        var limiter = new ActionServerLuaCall.MissingFunctionWarningLimiter(100);
        var executor = Executors.newFixedThreadPool(20);
        var start = new CountDownLatch(1);
        var warnings = new AtomicInteger();
        var tasks = new ArrayList<Future<?>>();
        try {
            for (int i = 0; i < 20; i++)
                tasks.add(
                        executor.submit(
                                () -> {
                                    try {
                                        assertTrue(start.await(5, TimeUnit.SECONDS));
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                        throw new AssertionError(e);
                                    }
                                    for (int call = 0; call < 1_000; call++)
                                        if (limiter.acquireWarning(1_000) >= 0) warnings.incrementAndGet();
                                }));
            start.countDown();
            for (var task : tasks) task.get(10, TimeUnit.SECONDS);
            assertEquals(1, warnings.get());
            assertEquals(19_999, limiter.acquireWarning(1_100));
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}
