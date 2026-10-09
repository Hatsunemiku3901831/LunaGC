package emu.grasscutter.game.beyond;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MiliastraAssetStoreTest {
    @TempDir Path directory;

    @Test
    void persistsExactFileAcrossRestartAndDeduplicatesConcurrentImports() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
        var store = new MiliastraAssetStore(directory);
        byte[] bytes = MiliastraFileTest.fixture("editor-sample.gia");
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> store.importFile("节点.gia", bytes));
            var second = executor.submit(() -> store.importFile("节点副本.gia", bytes));
            var a = first.get(10, TimeUnit.SECONDS);
            var b = second.get(10, TimeUnit.SECONDS);
            assertEquals(a.asset().id(), b.asset().id());
            assertNotEquals(a.duplicate(), b.duplicate());
            assertEquals(1, store.list("", "", 0, 20).total());
            var restarted = new MiliastraAssetStore(directory);
            assertArrayEquals(bytes, restarted.download(a.asset().id()).bytes());
            try (var paths = Files.list(directory)) {
                assertEquals(1, paths.count());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void importsGraphAndFiltersPagesWithoutAlteringItsBytes() throws Exception {
        var store = new MiliastraAssetStore(directory);
        byte[] graph = MiliastraFileTest.fixture("editor-sample.server.json");
        var json = store.importFile("测试.server.json", graph).asset();
        store.importFile("测试.gia", MiliastraFileTest.fixture("editor-sample.gia"));
        assertEquals(2, store.list("测试", "", 0, 20).total());
        assertEquals(1, store.list("", "GRAPH_JSON", 0, 20).total());
        assertEquals(1, store.list("", "", 1, 1).assets().size());
        assertTrue(store.list("", "", Integer.MAX_VALUE, 1).assets().isEmpty());
        assertArrayEquals(graph, store.download(json.id()).bytes());
    }

    @Test
    void invalidImportsAndIdsNeverWriteOutsideArchive() throws Exception {
        var store = new MiliastraAssetStore(directory);
        assertThrows(
                IllegalArgumentException.class, () -> store.importFile("../bad.gia", new byte[24]));
        assertThrows(IllegalArgumentException.class, () -> store.importFile("bad.gia", new byte[24]));
        assertThrows(IllegalArgumentException.class, () -> store.download("../source.bin"));
        try (var paths = Files.list(directory)) {
            assertEquals(0, paths.count());
        }
    }

    @Test
    void damagedArchiveCannotBeExportedOrAcknowledgedAsDuplicate() throws Exception {
        var store = new MiliastraAssetStore(directory);
        byte[] bytes = MiliastraFileTest.fixture("editor-sample.gia");
        var asset = store.importFile("sample.gia", bytes).asset();
        byte[] corrupt = bytes.clone();
        corrupt[30] ^= 1;
        Files.write(directory.resolve(asset.id()).resolve("source.bin"), corrupt);
        assertThrows(IOException.class, () -> store.download(asset.id()));
        assertThrows(IOException.class, () -> store.importFile("sample.gia", bytes));
        Files.writeString(directory.resolve(asset.id()).resolve("metadata.json"), "{}");
        assertEquals(1, store.list("", "", 0, 20).unreadable());
    }

    @Test
    void reportsGuidOverlapBetweenDifferentSourceVersions() throws Exception {
        var store = new MiliastraAssetStore(directory);
        var info = MiliastraFileTest.message(out -> out.writeUInt32(4, 1078000001));
        var firstAsset =
                MiliastraFileTest.message(
                        out -> {
                            out.writeByteArray(1, info);
                            out.writeString(3, "旧版实体");
                            out.writeUInt32(5, 3);
                        });
        var secondAsset =
                MiliastraFileTest.message(
                        out -> {
                            out.writeByteArray(1, info);
                            out.writeString(3, "新版实体");
                            out.writeUInt32(5, 3);
                        });
        store.importFile(
                "first.gia",
                MiliastraFileTest.container(
                        3, MiliastraFileTest.message(out -> out.writeByteArray(1, firstAsset))));
        var second =
                store.importFile(
                        "second.gia",
                        MiliastraFileTest.container(
                                3, MiliastraFileTest.message(out -> out.writeByteArray(1, secondAsset))));
        assertFalse(second.duplicate());
        assertTrue(
                second.asset().warnings().stream()
                        .anyMatch(s -> s.contains("GUID 重合") && s.contains("first.gia")));
        assertEquals(2, store.list("", "", 0, 20).total());
    }

    @Test
    void boundsVerboseMetadataWhilePreservingTheWholeFile() throws Exception {
        var store = new MiliastraAssetStore(directory);
        byte[] asset = MiliastraFileTest.message(out -> out.writeString(3, "x".repeat(4096)));
        byte[] payload =
                MiliastraFileTest.message(
                        out -> {
                            for (int index = 0; index < 1050; index++) out.writeByteArray(1, asset);
                        });
        byte[] bytes = MiliastraFileTest.container(3, payload);
        var entry = store.importFile("verbose.gia", bytes).asset();
        assertEquals(1050, entry.binary().assetCount());
        assertEquals(100, entry.binary().assets().size());
        assertArrayEquals(bytes, new MiliastraAssetStore(directory).download(entry.id()).bytes());
    }
}
