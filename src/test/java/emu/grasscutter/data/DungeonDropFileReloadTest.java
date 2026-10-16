package emu.grasscutter.data;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.dungeons.DungeonDropEntry;

import it.unimi.dsi.fastutil.ints.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;

class DungeonDropFileReloadTest {
    @TempDir Path directory;
    Int2ObjectMap<List<DungeonDropEntry>> before;
    AtomicLong clock = new AtomicLong();

    @BeforeEach
    void save() {
        Grasscutter.getConfig();
        before = new Int2ObjectOpenHashMap<>(GameData.getDungeonDropDataMap());
    }

    @AfterEach
    void restore() {
        GameData.getDungeonDropDataMap().clear();
        GameData.getDungeonDropDataMap().putAll(before);
    }

    String row(int id) {
        return "{dungeonId:" + id + ",drops:[{counts:[1],items:[202]}]}";
    }

    Path write(Path path, String body) throws IOException {
        long time = Files.exists(path) ? Files.getLastModifiedTime(path).toMillis() + 2000 : 10000;
        Files.writeString(path, body);
        Files.setLastModifiedTime(path, FileTime.fromMillis(time));
        return path;
    }

    Path file(int id) throws IOException {
        return write(directory.resolve("DungeonDrop.json"), "[" + row(id) + "]");
    }

    DungeonDropCatalog.FileCatalog watcher(
            Supplier<Path> source, DungeonDropCatalog.ReaderFactory readers) {
        return new DungeonDropCatalog.FileCatalog(source, readers, clock::get);
    }

    DungeonDropCatalog.FileCatalog watcher(Path path) {
        return watcher(() -> path, Files::newBufferedReader);
    }

    @Test
    void changedFileReplacesTheWholeSnapshotAndKeepsEarlierReaders() throws Exception {
        Path path = file(1);
        var files = watcher(path);
        files.reload();
        var old = GameData.getDungeonDropDataMap();
        write(path, "[" + row(2) + "," + row(3) + "]");
        assertTrue(files.reloadIfChanged());
        assertEquals(Set.of(2, 3), GameData.getDungeonDropDataMap().keySet());
        assertEquals(Set.of(1), old.keySet());
        assertNotSame(old, GameData.getDungeonDropDataMap());
    }

    @Test
    void unchangedFilesAreNotReopened() throws Exception {
        Path path = file(1);
        var opens = new AtomicInteger();
        var files =
                watcher(
                        () -> path,
                        selected -> {
                            opens.incrementAndGet();
                            return Files.newBufferedReader(selected);
                        });
        files.reload();
        var old = GameData.getDungeonDropDataMap();
        assertFalse(files.reloadIfChanged());
        assertFalse(files.reloadIfChanged());
        assertSame(old, GameData.getDungeonDropDataMap());
        assertEquals(1, opens.get());
    }

    @Test
    void malformedWritesKeepTheOldCatalogAndLaterValidChangesRecover() throws Exception {
        Path path = file(1);
        var files = watcher(path);
        files.reload();
        var old = GameData.getDungeonDropDataMap();
        write(path, "[" + row(2) + ",{dungeonId:3,drops:[]}]");
        assertFalse(files.reloadIfChanged());
        assertSame(old, GameData.getDungeonDropDataMap());
        write(path, "[" + row(4) + "]");
        assertTrue(files.reloadIfChanged());
        assertEquals(Set.of(4), GameData.getDungeonDropDataMap().keySet());
    }

    @Test
    void creatingAndRemovingTheUserFileSwitchesTheResolvedSource() throws Exception {
        Path fallback = write(directory.resolve("bundled.json"), "[" + row(1) + "]");
        Path user = directory.resolve("DungeonDrop.json");
        var files = watcher(() -> Files.exists(user) ? user : fallback, Files::newBufferedReader);
        files.reload();
        write(user, "[" + row(2) + "]");
        assertTrue(files.reloadIfChanged());
        assertEquals(Set.of(2), GameData.getDungeonDropDataMap().keySet());
        Files.delete(user);
        assertTrue(files.reloadIfChanged());
        assertEquals(Set.of(1), GameData.getDungeonDropDataMap().keySet());
    }

    @Test
    void readersContinueUsingTheOldSnapshotDuringAConcurrentLoad() throws Exception {
        Path path = file(1);
        var gate = new CountDownLatch(1);
        var entered = new CountDownLatch(1);
        var block = new AtomicBoolean();
        var files =
                watcher(
                        () -> path,
                        selected -> {
                            if (block.getAndSet(false)) {
                                entered.countDown();
                                try {
                                    if (!gate.await(5, TimeUnit.SECONDS))
                                        throw new IOException("Read gate timed out");
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    throw new IOException(e);
                                }
                            }
                            return Files.newBufferedReader(selected);
                        });
        files.reload();
        var old = GameData.getDungeonDropDataMap();
        write(path, "[" + row(2) + "," + row(3) + "]");
        block.set(true);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var future = executor.submit(files::reloadIfChanged);
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertTimeoutPreemptively(
                    Duration.ofSeconds(1), () -> assertFalse(files.reloadIfChanged()));
            assertSame(old, GameData.getDungeonDropDataMap());
            assertEquals(Set.of(1), old.keySet());
            gate.countDown();
            assertTrue(future.get(5, TimeUnit.SECONDS));
            assertEquals(Set.of(2, 3), GameData.getDungeonDropDataMap().keySet());
        } finally {
            gate.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void aSourceChangedDuringReadingCannotPublishItsIntermediateContents() throws Exception {
        Path path = file(1);
        var mutate = new AtomicBoolean();
        var files =
                watcher(
                        () -> path,
                        selected ->
                                new FilterReader(Files.newBufferedReader(selected)) {
                                    @Override
                                    public void close() throws IOException {
                                        super.close();
                                        if (mutate.getAndSet(false))
                                            write(selected, "[" + row(3) + "," + row(4) + "]");
                                    }
                                });
        files.reload();
        var old = GameData.getDungeonDropDataMap();
        write(path, "[" + row(2) + "]");
        mutate.set(true);
        assertFalse(files.reloadIfChanged());
        assertSame(old, GameData.getDungeonDropDataMap());
        assertTrue(files.reloadIfChanged());
        assertEquals(Set.of(3, 4), GameData.getDungeonDropDataMap().keySet());
    }

    @Test
    void aTemporaryReadFailureRetriesWithoutAnotherFileEdit() throws Exception {
        Path path = file(1);
        var fail = new AtomicBoolean();
        var opens = new AtomicInteger();
        var files =
                watcher(
                        () -> path,
                        selected -> {
                            opens.incrementAndGet();
                            if (fail.get()) throw new IOException("Controlled sharing violation");
                            return Files.newBufferedReader(selected);
                        });
        files.reload();
        var old = GameData.getDungeonDropDataMap();
        write(path, "[" + row(2) + "]");
        fail.set(true);
        assertFalse(files.reloadIfChanged());
        assertSame(old, GameData.getDungeonDropDataMap());
        assertEquals(2, opens.get());
        fail.set(false);
        assertFalse(files.reloadIfChanged());
        assertEquals(2, opens.get());
        clock.addAndGet(TimeUnit.SECONDS.toNanos(10));
        assertTrue(files.reloadIfChanged());
        assertEquals(Set.of(2), GameData.getDungeonDropDataMap().keySet());
    }

    @Test
    void explicitReaderSnapshotsDoNotAcquireAnAutomaticFileSource() throws Exception {
        Path path = file(1);
        var files = watcher(path);
        files.reload();
        files.reload(new StringReader("[" + row(2) + "]"));
        write(path, "[" + row(3) + "]");
        assertFalse(files.reloadIfChanged());
        assertEquals(Set.of(2), GameData.getDungeonDropDataMap().keySet());
    }

    @Test
    void aFailedExplicitReloadKeepsThePreviousAutomaticSourceActive() throws Exception {
        Path path = file(1);
        var files = watcher(path);
        files.reload();
        var old = GameData.getDungeonDropDataMap();
        write(path, "[");
        assertThrows(IOException.class, files::reload);
        assertSame(old, GameData.getDungeonDropDataMap());
        write(path, "[" + row(2) + "]");
        assertTrue(files.reloadIfChanged());
        assertEquals(Set.of(2), GameData.getDungeonDropDataMap().keySet());
    }
}
