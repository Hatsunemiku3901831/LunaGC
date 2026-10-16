package emu.grasscutter.data;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.dungeons.*;
import emu.grasscutter.utils.JsonUtils;

import it.unimi.dsi.fastutil.ints.*;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.*;

/** Loads configured legacy rewards as one snapshot, keeping the previous catalog on failure. */
public final class DungeonDropCatalog {
    private DungeonDropCatalog() {}

    private static final FileCatalog FILES =
            new FileCatalog(
                    () -> emu.grasscutter.utils.FileUtils.getDataPath("DungeonDrop.json"),
                    path -> Files.newBufferedReader(path, java.nio.charset.StandardCharsets.UTF_8),
                    System::nanoTime);

    public static boolean reloadIfChanged() {
        return FILES.reloadIfChanged();
    }

    @FunctionalInterface
    interface ReaderFactory {
        Reader open(Path path) throws IOException;
    }

    @FunctionalInterface
    interface CatalogPublisher {
        void install(Int2ObjectMap<List<DungeonDropEntry>> snapshot) throws IOException;
    }

    private record Stamp(Path path, long size, FileTime modified, Object key, boolean regular) {}

    private record WatchState(Stamp accepted, Stamp failed, long failedAt, boolean hasFailure) {}

    static final class FileCatalog {
        private final Supplier<Path> source;
        private final ReaderFactory readers;
        private final LongSupplier clock;
        private final CatalogPublisher publisher;
        private final String name;
        private final ReentrantLock gate = new ReentrantLock();
        private volatile WatchState state;
        private volatile boolean initialized;

        FileCatalog(Supplier<Path> source, ReaderFactory readers, LongSupplier clock) {
            this(source, readers, clock, GameData::installDungeonDropData, "DungeonDrop.json");
        }

        FileCatalog(
                Supplier<Path> source,
                ReaderFactory readers,
                LongSupplier clock,
                CatalogPublisher publisher,
                String name) {
            this.source = source;
            this.readers = readers;
            this.clock = clock;
            this.publisher = publisher;
            this.name = name;
        }

        void ensureLoaded() throws IOException {
            if (initialized) {
                reloadIfChanged();
                return;
            }
            gate.lock();
            try {
                if (!initialized) reloadFile(current());
            } finally {
                gate.unlock();
            }
        }

        int reload() throws IOException {
            gate.lock();
            try {
                return reloadFile(current());
            } finally {
                gate.unlock();
            }
        }

        int reload(Reader reader) throws IOException {
            gate.lock();
            try {
                return publish(parse(reader, name), null);
            } finally {
                gate.unlock();
            }
        }

        boolean reloadIfChanged() {
            var seen = state;
            if (seen == null) return false;
            Stamp candidate;
            try {
                candidate = current();
            } catch (IOException | RuntimeException failure) {
                if (gate.tryLock()) {
                    try {
                        failed(null, failure);
                    } finally {
                        gate.unlock();
                    }
                }
                return false;
            }
            if (candidate.equals(seen.accepted()) || !gate.tryLock()) return false;
            try {
                var active = state;
                if (active == null) return false;
                candidate = current();
                if (candidate.equals(active.accepted())
                        || !retryAllowed(active, candidate, clock.getAsLong())) return false;
                reloadFile(candidate);
                return true;
            } catch (IOException | RuntimeException failure) {
                failed(candidate, failure);
                return false;
            } finally {
                gate.unlock();
            }
        }

        private Stamp current() throws IOException {
            var path = source.get().toAbsolutePath().normalize();
            var attributes = Files.readAttributes(path, BasicFileAttributes.class);
            return new Stamp(
                    path,
                    attributes.size(),
                    attributes.lastModifiedTime(),
                    attributes.fileKey(),
                    attributes.isRegularFile());
        }

        private int reloadFile(Stamp before) throws IOException {
            if (!before.regular())
                throw new IOException(
                        "Dungeon drop source is not a regular file: " + before.path());
            Int2ObjectMap<List<DungeonDropEntry>> snapshot;
            try (var reader = readers.open(before.path())) {
                snapshot = parse(reader, name);
            }
            var after = current();
            if (!before.equals(after))
                throw new IOException("Dungeon drop source changed while it was being read");
            return publish(snapshot, new WatchState(after, null, 0, false));
        }

        private int publish(Int2ObjectMap<List<DungeonDropEntry>> snapshot, WatchState next)
                throws IOException {
            publisher.install(snapshot);
            state = next;
            initialized = true;
            Grasscutter.getLogger()
                    .info("Loaded {} dungeon reward tables from {}", snapshot.size(), name);
            return snapshot.size();
        }

        private boolean retryAllowed(WatchState active, Stamp candidate, long now) {
            return !active.hasFailure()
                    || !Objects.equals(active.failed(), candidate)
                    || now - active.failedAt() >= TimeUnit.SECONDS.toNanos(1);
        }

        private void failed(Stamp candidate, Exception failure) {
            var active = state;
            if (active == null) return;
            long now = clock.getAsLong();
            if (!retryAllowed(active, candidate, now)) return;
            boolean report = !active.hasFailure() || !Objects.equals(active.failed(), candidate);
            state = new WatchState(active.accepted(), candidate, now, true);
            if (report)
                Grasscutter.getLogger()
                        .warn(
                                "Cannot automatically reload dungeon rewards from {}; keeping the"
                                        + " previous catalog",
                                candidate == null ? "the configured source" : candidate.path(),
                                failure);
        }
    }

    public static int reload() throws IOException {
        return FILES.reload();
    }

    public static int reload(Reader reader) throws IOException {
        return FILES.reload(reader);
    }

    private static Int2ObjectMap<List<DungeonDropEntry>> parse(Reader reader, String name)
            throws IOException {
        var snapshot = new Int2ObjectOpenHashMap<List<DungeonDropEntry>>();
        try {
            var rows = JsonUtils.loadToList(reader, DungeonDrop.class);
            require(rows != null && !rows.isEmpty(), "Empty dungeon drop catalog");
            for (var row : rows) {
                require(row != null && row.getDungeonId() > 0, "Invalid dungeon ID");
                int id = row.getDungeonId();
                require(!snapshot.containsKey(id), "Duplicate dungeon ID " + id);
                require(
                        row.getDrops() != null && !row.getDrops().isEmpty(),
                        "Empty drops for " + id);
                var entries = new ArrayList<DungeonDropEntry>();
                for (var entry : row.getDrops()) {
                    require(entry != null, "Null drop group for " + id);
                    var counts = entry.getCounts();
                    require(counts != null && !counts.isEmpty(), "Missing counts for " + id);
                    int previous = -1;
                    for (var count : counts) {
                        require(
                                count != null
                                        && count >= 0
                                        && count > previous
                                        && count < Integer.MAX_VALUE,
                                "Invalid counts for " + id);
                        previous = count;
                    }
                    // Sparse count arrays describe an inclusive range, matching rollRewards.
                    long candidates = (long) counts.get(counts.size() - 1) - counts.get(0) + 1;
                    var items = entry.getItems();
                    require(
                            items != null
                                    && !items.isEmpty()
                                    && items.stream().allMatch(item -> item != null && item > 0),
                            "Invalid items for " + id);
                    validateWeights(entry.getProbabilities(), candidates, id);
                    validateWeights(entry.getItemProbabilities(), items.size(), id);
                    var copy = new DungeonDropEntry();
                    copy.setCounts(List.copyOf(counts));
                    copy.setItems(List.copyOf(items));
                    copy.setProbabilities(copyWeights(entry.getProbabilities()));
                    copy.setItemProbabilities(copyWeights(entry.getItemProbabilities()));
                    copy.setMpDouble(entry.isMpDouble());
                    entries.add(copy);
                }
                snapshot.put(id, List.copyOf(entries));
            }
        } catch (RuntimeException invalid) {
            throw new IOException("Invalid " + name + ": " + invalid.getMessage(), invalid);
        }
        return snapshot;
    }

    private static List<Integer> copyWeights(List<Integer> weights) {
        return weights == null ? null : List.copyOf(weights);
    }

    private static void validateWeights(List<Integer> weights, long size, int id)
            throws IOException {
        if (weights == null) return;
        require(weights.size() == size, "Weight count mismatch for " + id);
        long sum = 0;
        for (var weight : weights) {
            require(weight != null && weight >= 0, "Invalid weight for " + id);
            sum += weight;
        }
        require(sum > 0 && sum < Integer.MAX_VALUE, "Invalid weight total for " + id);
    }

    private static void require(boolean condition, String message) throws IOException {
        if (!condition) throw new IOException(message);
    }
}
