package emu.grasscutter.scripts;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.scripts.constants.EventType;
import emu.grasscutter.scripts.data.ScriptArgs;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Getter;

/** Resource timelines contain absolute offsets in seconds, including fractional time points. */
public final class SceneTimeAxis {
    private static final ScheduledThreadPoolExecutor scheduler;

    static {
        var sequence = new AtomicInteger();
        scheduler =
                new ScheduledThreadPoolExecutor(
                        2,
                        task -> {
                            var thread = new Thread(task, "Scene-time-axis-" + sequence.incrementAndGet());
                            thread.setDaemon(true);
                            return thread;
                        });
        scheduler.setRemoveOnCancelPolicy(true);
    }

    @Getter private final SceneScriptManager handle;
    @Getter private final int groupId;
    @Getter private final String identifier;
    @Getter private final boolean loop;
    private final long[] offsets;
    private boolean started;
    private volatile boolean stopped;
    private long cycleStart;
    private int point;
    private ScheduledFuture<?> scheduled;
    private CompletableFuture<Void> event;

    /** Keep the existing single-point Java entry point. */
    public SceneTimeAxis(
            SceneScriptManager handle, int groupId, String identifier, int delay, boolean loop) {
        this(handle, groupId, identifier, new double[] {delay}, loop);
    }

    public SceneTimeAxis(
            SceneScriptManager handle, int groupId, String identifier, double[] delays, boolean loop) {
        this.handle = Objects.requireNonNull(handle);
        if (groupId <= 0
                || identifier == null
                || identifier.isBlank()
                || delays == null
                || delays.length == 0) throw new IllegalArgumentException("Invalid time axis");
        this.groupId = groupId;
        this.identifier = identifier;
        this.loop = loop;
        offsets = new long[delays.length];
        double previous = -1;
        for (int i = 0; i < delays.length; i++) {
            double seconds = delays[i];
            if (!Double.isFinite(seconds)
                    || seconds < 0
                    || seconds < previous
                    || seconds > TimeUnit.NANOSECONDS.toSeconds(Long.MAX_VALUE))
                throw new IllegalArgumentException("Time points must be finite, nonnegative and ordered");
            offsets[i] = Math.round(seconds * 1_000_000_000d);
            previous = seconds;
        }
        if (loop && offsets[offsets.length - 1] == 0)
            throw new IllegalArgumentException("A looping time axis needs a positive period");
    }

    public int getDelay() {
        return (int) TimeUnit.NANOSECONDS.toSeconds(offsets[0]);
    }

    public boolean isStopped() {
        return stopped;
    }

    /**
     * Idempotent: replacement, group unload or destruction may have already canceled registration.
     */
    public synchronized void start() {
        if (started || stopped) return;
        if (!handle.isCurrentTimeAxis(this)) {
            stop();
            return;
        }
        started = true;
        cycleStart = System.nanoTime();
        scheduleNext();
    }

    public synchronized void stop() {
        if (stopped) return;
        stopped = true;
        if (scheduled != null) scheduled.cancel(false);
        if (event != null) event.cancel(false);
        scheduled = null;
        event = null;
        handle.finishTimeAxis(this);
    }

    private void scheduleNext() {
        if (stopped || !handle.isCurrentTimeAxis(this)) {
            stop();
            return;
        }
        // Subtraction also handles System.nanoTime wrapping; offsets are durations below 2^63.
        long wait = Math.max(0, cycleStart + offsets[point] - System.nanoTime());
        scheduled = scheduler.schedule(this::dispatch, wait, TimeUnit.NANOSECONDS);
    }

    private synchronized void dispatch() {
        if (stopped || !handle.isCurrentTimeAxis(this)) return;
        try {
            event =
                    handle.callTimeAxisEvent(
                            this,
                            new ScriptArgs(groupId, EventType.EVENT_TIME_AXIS_PASS, point + 1)
                                    .setEventSource(identifier));
            event.whenComplete((ignored, failure) -> completed(failure));
        } catch (RuntimeException failure) {
            Grasscutter.getLogger()
                    .warn("Cannot dispatch time axis {} in group {}", identifier, groupId, failure);
            stop();
        }
    }

    private synchronized void completed(Throwable failure) {
        if (stopped) return;
        event = null;
        if (failure != null) {
            Grasscutter.getLogger().warn("Time axis {} in group {} failed", identifier, groupId, failure);
            stop();
            return;
        }
        if (++point == offsets.length) {
            if (!loop) {
                stop();
                return;
            }
            point = 0;
            cycleStart += offsets[offsets.length - 1];
        }
        // One pending event per axis preserves Lua event order without an unbounded backlog.
        scheduleNext();
    }
}
