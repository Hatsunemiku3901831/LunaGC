package emu.grasscutter.server.game;

import java.util.Objects;
import java.util.function.LongSupplier;

/** Session-local play time announced in PlayerTimeNotify, independent of PingReq and scene time. */
public final class PlayerTimeClock {
    public record Snapshot(long playerTime, boolean paused) {}

    private final LongSupplier nanoTime;
    private long anchor;
    private long elapsed;
    private boolean paused;

    public PlayerTimeClock() {
        this(System::nanoTime);
    }

    public PlayerTimeClock(LongSupplier nanoTime) {
        this.nanoTime = Objects.requireNonNull(nanoTime);
        anchor = nanoTime.getAsLong();
    }

    private long elapsedAt(long now) {
        return elapsed + Math.max(0, now - anchor);
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(
                (paused ? elapsed : elapsedAt(nanoTime.getAsLong())) / 1_000_000, paused);
    }

    public synchronized void setPaused(boolean value) {
        if (paused == value) return;
        long now = nanoTime.getAsLong();
        if (value) elapsed = elapsedAt(now);
        anchor = now;
        paused = value;
    }
}
