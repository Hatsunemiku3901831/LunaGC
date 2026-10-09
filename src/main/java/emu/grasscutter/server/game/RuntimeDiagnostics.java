package emu.grasscutter.server.game;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.database.DatabaseHelper;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.List;
import java.util.Map;

/** Reads liveness without acquiring any world, scene or tick monitor. */
public final class RuntimeDiagnostics {
    private RuntimeDiagnostics() {}

    public static Map<String, Object> snapshot() {
        var server = Grasscutter.getGameServer();
        var tick = server == null ? null : server.tickStatus();
        var queues = DatabaseHelper.queueStatus();
        int deadlocked = deadlockedThreadCount(ManagementFactory.getThreadMXBean());
        boolean overloaded = queues.stream().anyMatch(q -> (long) q.queued() * 5 >= (long) q.capacity() * 4);
        return Map.of("healthy", (tick == null || !tick.stalled()) && deadlocked == 0 && !overloaded,
                "tick", tick == null ? Map.of() : tick,
                "deadlockedThreads", deadlocked, "databaseOverloaded", overloaded, "queues", queues);
    }

    static int deadlockedThreadCount(ThreadMXBean threads) {
        long[] ids = threads.isSynchronizerUsageSupported()
                ? threads.findDeadlockedThreads() : threads.findMonitorDeadlockedThreads();
        return ids == null ? 0 : ids.length;
    }
}
