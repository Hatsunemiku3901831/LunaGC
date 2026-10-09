package emu.grasscutter.data;

import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.binout.config.ConfigEntityBase;
import emu.grasscutter.data.binout.config.ConfigLevelEntity;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.lang.annotation.Annotation;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResourceLoaderIsolationTest {
    @TempDir Path directory;

    @BeforeAll
    static void initializeConfiguration() {
        // Startup initializes Grasscutter before FileUtils; keep the same order in isolation.
        Grasscutter.getLogger();
    }

    public static class Row extends GameResource {
        public int id;
        public int value;
        public boolean failLoad;
        public boolean failId;
        public boolean fatal;
        public int computedId;

        public Row() {}

        @Override
        public int getId() {
            if (failId) throw new IllegalArgumentException("invalid ID");
            return id;
        }

        @Override
        public void onLoad() {
            if (fatal) throw new AssertionError("unrecoverable failure");
            if (failLoad) throw new IllegalArgumentException("invalid row");
            if (computedId != 0) id = computedId;
        }
    }

    private Path file(String name, String contents) throws Exception {
        return Files.writeString(directory.resolve(name), contents);
    }

    private Int2ObjectOpenHashMap<Row> load(String contents) throws Exception {
        var map = new Int2ObjectOpenHashMap<Row>();
        ResourceLoader.loadFromResource(Row.class, file("rows.json", contents), map);
        return map;
    }

    @Test
    void initializationFailureDoesNotLoseLaterRows() throws Exception {
        var map = load("[{\"id\":1},{\"id\":2,\"failLoad\":true},{\"id\":3}]");
        assertEquals(Set.of(1, 3), map.keySet());
    }

    @Test
    void badNumericFieldDoesNotLoseOtherRows() throws Exception {
        var map = load("[{\"id\":1},{\"id\":\"broken\"},{\"id\":3}]");
        assertEquals(Set.of(1, 3), map.keySet());
    }

    @Test
    void wrongRowShapeDoesNotLoseOtherRows() throws Exception {
        var map = load("[{\"id\":1},[2],{\"id\":3}]");
        assertEquals(Set.of(1, 3), map.keySet());
    }

    @Test
    void nullRowDoesNotLoseOtherRows() throws Exception {
        var map = load("[{\"id\":1},null,{\"id\":3}]");
        assertEquals(Set.of(1, 3), map.keySet());
    }

    @Test
    void failingIdDoesNotLoseOtherRows() throws Exception {
        var map = load("[{\"id\":1},{\"id\":2,\"failId\":true},{\"id\":3}]");
        assertEquals(Set.of(1, 3), map.keySet());
    }

    @Test
    void rejectedReplacementPreservesExistingEntry() throws Exception {
        var map = load("[{\"id\":1,\"value\":42}]");
        var existing = map.get(1);
        ResourceLoader.loadFromResource(
                Row.class, file("replacement.json", "[{\"id\":1,\"failLoad\":true},{\"id\":3}]"), map);
        assertSame(existing, map.get(1));
        assertTrue(map.containsKey(3));
    }

    @Test
    void syntaxErrorDoesNotPartiallyInstallTheFile() throws Exception {
        var map = load("[{\"id\":99}]");
        assertThrows(
                Exception.class,
                () ->
                        ResourceLoader.loadFromResource(
                                Row.class, file("broken.json", "[{\"id\":1},{\"id\":2}"), map));
        assertEquals(Set.of(99), map.keySet());
    }

    @Test
    void wrongTopLevelShapeIsReportedWithoutChanges() throws Exception {
        var map = new Int2ObjectOpenHashMap<Row>();
        assertThrows(
                Exception.class,
                () -> ResourceLoader.loadFromResource(Row.class, file("object.json", "{\"id\":1}"), map));
        assertTrue(map.isEmpty());
    }

    @Test
    void duplicateIdsKeepTheLastValidRowAndZeroIsAValidKey() throws Exception {
        var map = load("[{\"id\":0,\"value\":8},{\"id\":1,\"value\":2},{\"id\":1,\"value\":3}]");
        assertEquals(2, map.size());
        assertEquals(8, map.get(0).value);
        assertEquals(3, map.get(1).value);
    }

    @Test
    void keyComputedDuringInitializationIsUsed() throws Exception {
        var map = load("[{\"computedId\":258}]");
        assertTrue(map.containsKey(258));
        assertFalse(map.containsKey(0));
    }

    @Test
    void unrecoverableErrorsAreNotSwallowed() throws Exception {
        var map = new Int2ObjectOpenHashMap<Row>();
        assertThrows(
                AssertionError.class,
                () ->
                        ResourceLoader.loadFromResource(
                                Row.class, file("fatal.json", "[{\"id\":1,\"fatal\":true},{\"id\":3}]"), map));
        assertTrue(map.isEmpty());
    }

    @Test
    void nullPlaceholderAndEmptyFileListLoadNoRows() throws Exception {
        assertTrue(load("null").isEmpty());
        assertTrue(load("[]").isEmpty());
    }

    @Test
    void tsjDeserializationFailureDoesNotLoseLaterRows() throws Exception {
        var map = new Int2ObjectOpenHashMap<Row>();
        ResourceLoader.loadFromResource(
                Row.class, file("rows.tsj", "id\tvalue\n1\t10\nbad\t20\n3\t30\n"), map);
        assertEquals(Set.of(1, 3), map.keySet());
    }

    @Test
    void tsjInitializationFailureDoesNotLoseLaterRows() throws Exception {
        var map = new Int2ObjectOpenHashMap<Row>();
        ResourceLoader.loadFromResource(
                Row.class, file("rows.tsj", "id\tfailLoad\n1\tfalse\n2\ttrue\n3\tfalse\n"), map);
        assertEquals(Set.of(1, 3), map.keySet());
    }

    @Test
    void tsvInitializationFailureDoesNotLoseLaterRows() throws Exception {
        var map = new Int2ObjectOpenHashMap<Row>();
        ResourceLoader.loadFromResource(
                Row.class, file("rows.tsv", "id\tfailLoad\n1\tfalse\n2\ttrue\n3\tfalse\n"), map);
        assertEquals(Set.of(1, 3), map.keySet());
    }

    @Test
    void validReloadDoesNotReportCollapsedIds() throws Exception {
        var contents =
                "["
                        + String.join(
                                ",",
                                java.util.stream.IntStream.range(0, 16)
                                        .mapToObj(id -> "{\"id\":" + id + "}")
                                        .toList())
                        + "]";
        var map = load(contents);
        var appender = capture();
        try {
            ResourceLoader.loadFromResource(Row.class, file("reload.json", contents), map);
            assertTrue(
                    appender.list.stream()
                            .noneMatch(e -> e.getFormattedMessage().contains("are the id field")));
            assertEquals(16, map.size());
        } finally {
            Grasscutter.getLogger().detachAppender(appender);
        }
    }

    @Test
    void genuinelyCollapsedIdsAreStillReported() throws Exception {
        var appender = capture();
        try {
            var map = load("[" + String.join(",", Collections.nCopies(16, "{\"value\":7}")) + "]");
            assertEquals(1, map.size());
            assertTrue(
                    appender.list.stream()
                            .anyMatch(e -> e.getFormattedMessage().contains("are the id field")));
        } finally {
            Grasscutter.getLogger().detachAppender(appender);
        }
    }

    @Test
    void aFailedFileDoesNotSkipTheOtherFilesOfTheResource() throws Exception {
        var first = file("first.json", "{}");
        var second = file("second.json", "[{\"id\":3}]");
        var map = new Int2ObjectOpenHashMap<Row>();
        var type = resourceType(first, second);
        assertThrows(
                Exception.class, () -> ResourceLoader.loadFromResource(Row.class, type, map, true));
        assertTrue(map.containsKey(3));
    }

    @Test
    void fileFailureDoesNotPoisonTheLoadCache() throws Exception {
        var first = file("first.json", "{}");
        var second = file("second.json", "[{\"id\":3}]");
        var map = new Int2ObjectOpenHashMap<Row>();
        var type = resourceType(first, second);
        assertThrows(
                Exception.class, () -> ResourceLoader.loadFromResource(Row.class, type, map, true));
        Files.writeString(first, "[{\"id\":1}]");
        ResourceLoader.loadFromResource(Row.class, type, map, false);
        assertEquals(Set.of(1, 3), map.keySet());
    }

    @Test
    void missingTabularFileIsReportedWhileOtherFilesStillLoad() throws Exception {
        var first = directory.resolve("absent.tsj");
        var second = file("second.json", "[{\"id\":3}]");
        var map = new Int2ObjectOpenHashMap<Row>();
        assertThrows(
                Exception.class,
                () -> ResourceLoader.loadFromResource(Row.class, resourceType(first, second), map, true));
        assertTrue(map.containsKey(3));
    }

    @Test
    void failedReloadOfAnAlreadyLoadedClassCanBeRetried() throws Exception {
        var first = file("first.json", "[{\"id\":1}]");
        var type = resourceType(first);
        var map = new Int2ObjectOpenHashMap<Row>();
        ResourceLoader.loadFromResource(Row.class, type, map, true);
        Files.writeString(first, "{}");
        assertThrows(
                Exception.class, () -> ResourceLoader.loadFromResource(Row.class, type, map, true));
        Files.writeString(first, "[{\"id\":2}]");
        ResourceLoader.loadFromResource(Row.class, type, map, false);
        assertEquals(Set.of(1, 2), map.keySet());
    }

    @Test
    void nullSingleConfigDoesNotOverwriteExistingConfig() throws Exception {
        file("good.json", "{\"abilities\":[]}");
        file("placeholder.json", "null");
        var map = new HashMap<String, ConfigEntityBase>();
        var existing = new ConfigEntityBase();
        map.put("placeholder", existing);
        configs("loadConfigData", map);
        assertSame(existing, map.get("placeholder"));
        assertNotNull(map.get("good"));
    }

    @Test
    void malformedSingleConfigDoesNotSkipGoodFiles() throws Exception {
        file("bad.json", "{broken");
        file("good.json", "{\"abilities\":[]}");
        var map = new HashMap<String, ConfigEntityBase>();
        configs("loadConfigData", map);
        assertEquals(Set.of("good"), map.keySet());
    }

    @Test
    void nullConfigMapIsAPlaceholderWithoutAnError() throws Exception {
        file("placeholder.json", "null");
        file("good.json", "{\"good\":{\"abilities\":[]}}");
        var map = new HashMap<String, ConfigEntityBase>();
        var appender = capture();
        try {
            configs("loadConfigDataMap", map);
            assertEquals(Set.of("good"), map.keySet());
            assertTrue(appender.list.stream().noneMatch(e -> e.getLevel().toInt() >= 40000));
        } finally {
            Grasscutter.getLogger().detachAppender(appender);
        }
    }

    @Test
    void emptyArrayConfigMapRemainsAValidPlaceholder() throws Exception {
        file("placeholder.json", "[]");
        var map = new HashMap<String, ConfigEntityBase>();
        var appender = capture();
        try {
            configs("loadConfigDataMap", map);
            assertTrue(map.isEmpty());
            assertTrue(appender.list.stream().noneMatch(e -> e.getLevel().toInt() >= 40000));
        } finally {
            Grasscutter.getLogger().detachAppender(appender);
        }
    }

    @Test
    void legacyArrayConfigMapStillLoadsItsKeyValuePairs() throws Exception {
        file("legacy.json", "[[\"first\",{\"abilities\":[]}],[\"last\",{\"abilities\":[]}]]");
        var map = new HashMap<String, ConfigEntityBase>();
        configs("loadConfigDataMap", map);
        assertEquals(Set.of("first", "last"), map.keySet());
    }

    @Test
    void legacyArrayConfigMapRejectsOnlyItsBadPairs() throws Exception {
        file(
                "legacy.json",
                "[[\"first\",{\"abilities\":[]}],[\"bad\",{\"abilities\":12}],[\"wrongShape\"],[\"null\",null],[\"last\",{\"abilities\":[]}]]");
        var map = new HashMap<String, ConfigEntityBase>();
        configs("loadConfigDataMap", map);
        assertEquals(Set.of("first", "last"), map.keySet());
    }

    @Test
    void nullConfigEntryDoesNotOverwriteExistingConfig() throws Exception {
        file("rows.json", "{\"good\":{\"abilities\":[]},\"placeholder\":null}");
        var map = new HashMap<String, ConfigEntityBase>();
        var existing = new ConfigEntityBase();
        map.put("placeholder", existing);
        configs("loadConfigDataMap", map);
        assertSame(existing, map.get("placeholder"));
        assertNotNull(map.get("good"));
    }

    @Test
    void oneBadConfigEntryDoesNotLoseTheWholeMapFile() throws Exception {
        file(
                "rows.json",
                "{\"first\":{\"abilities\":[]},\"bad\":{\"abilities\":12},\"last\":{\"abilities\":[]}}");
        var map = new HashMap<String, ConfigEntityBase>();
        configs("loadConfigDataMap", map);
        assertEquals(Set.of("first", "last"), map.keySet());
    }

    @Test
    void malformedConfigMapDoesNotPartiallyInstallEntries() throws Exception {
        file("rows.json", "{\"good\":{\"abilities\":[]},");
        var map = new HashMap<String, ConfigEntityBase>();
        configs("loadConfigDataMap", map);
        assertTrue(map.isEmpty());
    }

    @Test
    void levelEntityConfigsSkipNullAndInvalidEntries() throws Exception {
        file(
                "level.json",
                "{\"first\":{\"preloadMonsterEntityIDs\":[1]},\"null\":null,\"bad\":{\"abilities\":12},\"last\":{\"avatarAbilities\":[]}}");
        var map = new HashMap<String, ConfigLevelEntity>();
        var method =
                ResourceLoader.class.getDeclaredMethod(
                        "loadConfigDataMap", Map.class, String.class, Class.class);
        method.setAccessible(true);
        method.invoke(null, map, directory.toString(), ConfigLevelEntity.class);
        assertEquals(Set.of("first", "last"), map.keySet());
        assertEquals(List.of(1), map.get("first").getPreloadMonsterEntityIDs());
    }

    @Test
    void failuresReportRowsAndBoundTheDiagnosticSamples() throws Exception {
        var appender = capture();
        try {
            var map = load("[" + String.join(",", Collections.nCopies(20, "null")) + ", {\"id\":9}]");
            assertEquals(Set.of(9), map.keySet());
            var warnings =
                    appender.list.stream().filter(e -> e.getFormattedMessage().contains("rejected")).toList();
            assertEquals(1, warnings.size());
            var message = warnings.get(0).getFormattedMessage();
            assertTrue(message.contains("20"));
            assertTrue(message.contains("rows.json"));
            assertTrue(message.contains("row 1"));
            assertFalse(message.contains("row 20"));
            assertTrue(
                    appender.list.stream()
                            .noneMatch(e -> e.getFormattedMessage().contains("are the id field")));
        } finally {
            Grasscutter.getLogger().detachAppender(appender);
        }
    }

    private void configs(String methodName, Map<String, ConfigEntityBase> map) throws Exception {
        var method =
                ResourceLoader.class.getDeclaredMethod(methodName, Map.class, String.class, Class.class);
        method.setAccessible(true);
        method.invoke(null, map, directory.toString(), ConfigEntityBase.class);
    }

    private static ListAppender<ILoggingEvent> capture() {
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        Grasscutter.getLogger().addAppender(appender);
        return appender;
    }

    private static ResourceType resourceType(Path... paths) {
        return new ResourceType() {
            @Override
            public String[] name() {
                return Arrays.stream(paths).map(Path::toString).toArray(String[]::new);
            }

            @Override
            public LoadPriority loadPriority() {
                return LoadPriority.NORMAL;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return ResourceType.class;
            }
        };
    }
}
