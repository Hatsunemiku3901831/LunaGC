package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.ability.AbilityManager;

import java.util.ArrayList;
import java.util.concurrent.*;

/** Hold workers so initialization callbacks can be advanced at an exact test boundary. */
final class PausedAbilityActions implements AutoCloseable {
    private final CountDownLatch release = new CountDownLatch(1);
    private final ArrayList<Future<?>> held = new ArrayList<>();
    private final ThreadPoolExecutor executor = (ThreadPoolExecutor) AbilityManager.eventExecutor;

    PausedAbilityActions() throws Exception {
        var entered = new CountDownLatch(executor.getCorePoolSize());
        try {
            for (int i = 0; i < executor.getCorePoolSize(); i++)
                held.add(
                        executor.submit(
                                () -> {
                                    entered.countDown();
                                    try {
                                        release.await();
                                    } catch (InterruptedException interrupted) {
                                        Thread.currentThread().interrupt();
                                    }
                                }));
            assertTrue(entered.await(5, TimeUnit.SECONDS), "Ability workers did not pause");
        } catch (Throwable failure) {
            release.countDown();
            throw failure;
        }
    }

    void runPending() throws Exception {
        int count = 0;
        for (Runnable task; (task = executor.getQueue().poll()) != null; ) {
            assertTrue(++count <= 1000, "Ability initialization did not settle");
            task.run();
            if (task instanceof Future<?> future) future.get(5, TimeUnit.SECONDS);
        }
    }

    int queued() {
        return executor.getQueue().size();
    }

    @Override
    public void close() throws Exception {
        try {
            runPending();
        } finally {
            release.countDown();
            for (var future : held) future.get(5, TimeUnit.SECONDS);
        }
    }
}
