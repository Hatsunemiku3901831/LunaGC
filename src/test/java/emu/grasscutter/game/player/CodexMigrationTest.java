package emu.grasscutter.game.player;

import static org.junit.jupiter.api.Assertions.*;

import com.mongodb.client.*;

import dev.morphia.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.codex.CodexReliquaryData;
import emu.grasscutter.database.*;
import emu.grasscutter.game.managers.SotSManager;
import emu.grasscutter.utils.JsonUtils;

import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Real Player reads and durable login migration, including a deterministically delayed stale save.
 */
class CodexMigrationTest {
    static List<CodexReliquaryData> rows;
    List<CodexReliquaryData> previousRows;
    Map<Integer, CodexReliquaryData> previousSuitMap;
    MongoClient client;
    Datastore store, previousStore;
    CodexReliquaryData sample;

    static class RecordingPlayer extends Player {
        int saves;

        @Override
        public void save() {
            saves++;
        }
    }

    @BeforeAll
    static void resources() throws Exception {
        rows =
                JsonUtils.loadToList(
                        Path.of("resources/ExcelBinOutput/ReliquaryCodexExcelConfigData.json"),
                        CodexReliquaryData.class);
    }

    @BeforeEach
    void setup() throws Exception {
        String uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        Assumptions.assumeTrue(uri != null && !uri.isBlank());
        Grasscutter.getConfig();
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        previousRows = new ArrayList<>(GameData.getCodexReliquaryArrayList());
        previousSuitMap = new HashMap<>(GameData.getCodexReliquaryDataIdMap());
        GameData.getCodexReliquaryArrayList().clear();
        GameData.getCodexReliquaryDataIdMap().clear();
        rows.forEach(CodexReliquaryData::onLoad);
        sample =
                rows.stream()
                        .filter(
                                x ->
                                        x.getLevel() == 5
                                                && x.getIds().stream().allMatch(id -> id > 0)
                                                && new HashSet<>(x.getIds()).size() == 5)
                        .findFirst()
                        .orElseThrow();
        previousStore = DatabaseManager.getGameDatastore();
        client = MongoClients.create(uri);
        store =
                Morphia.createDatastore(
                        client,
                        "lunagc_codex_migration_" + UUID.randomUUID().toString().replace("-", ""));
        store.getMapper().map(Player.class);
        setStore(store);
    }

    static void setStore(Datastore value) throws Exception {
        var field = DatabaseManager.class.getDeclaredField("gameDatastore");
        field.setAccessible(true);
        field.set(null, value);
    }

    @AfterEach
    void close() throws Exception {
        if (store == null) return;
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        setStore(previousStore);
        store.getDatabase().drop();
        client.close();
        GameData.getCodexReliquaryArrayList().clear();
        GameData.getCodexReliquaryArrayList().addAll(previousRows);
        GameData.getCodexReliquaryDataIdMap().clear();
        GameData.getCodexReliquaryDataIdMap().putAll(previousSuitMap);
    }

    Set<Integer> canonical() {
        return new HashSet<>(sample.getIds());
    }

    Set<Integer> legacy() {
        var ids = new HashSet<Integer>();
        canonical()
                .forEach(
                        id -> {
                            ids.add(id + 3);
                            ids.add(id + 4);
                        });
        return ids;
    }

    Player seed(boolean oldIds) throws Exception {
        var player = new Player();
        player.setUid(10002);
        var team = Player.class.getDeclaredField("teamManager");
        team.setAccessible(true);
        team.set(player, new TeamManager(player));
        player.getArtifactTransmuterData().setProgressUnits(1500);
        player.getCodex().getUnlockedReliquary().addAll(oldIds ? legacy() : canonical());
        player.getCodex().getUnlockedBook().add(100178);
        player.getCodex().getUnlockedWeapon().add(11501);
        store.save(player);
        return player;
    }

    Document document() {
        return store.getDatabase()
                .getCollection("players")
                .find(new Document("_id", 10002))
                .first();
    }

    Set<Integer> savedIds() {
        var ids =
                document().get("codex", Document.class).getList("unlockedReliquary", Integer.class);
        return ids == null ? Set.of() : new HashSet<>(ids);
    }

    void login(Player player, Runnable save) {
        player.getCodex().onLogin(save);
    }

    @Test
    void bindingAnEmptyOrCanonicalCodexDoesNotSaveThePlayer() {
        var player = new RecordingPlayer();
        player.getCodex().setPlayer(player);
        assertEquals(0, player.saves);
        player.getCodex().getUnlockedReliquary().addAll(canonical());
        player.getCodex().getUnlockedReliquarySuitCodex().add(sample.getId());
        player.getCodex().setPlayer(player);
        assertEquals(0, player.saves);
    }

    @Test
    void bindingNormalizesLegacyIdsAndBackfillsCompleteSuitsWithoutWriting() {
        var player = new RecordingPlayer();
        player.getCodex().getUnlockedReliquary().addAll(legacy());
        player.getCodex().getUnlockedBook().add(100178);
        player.getCodex().setPlayer(player);
        assertEquals(canonical(), player.getCodex().getUnlockedReliquary());
        assertTrue(player.getCodex().getUnlockedReliquarySuitCodex().contains(sample.getId()));
        assertEquals(Set.of(100178), player.getCodex().getUnlockedBook());
        assertEquals(0, player.saves);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void fullPlayerReadsNeverQueueWritesOrMigrateTheStoredDocument(boolean helper)
            throws Exception {
        seed(true);
        var before = document();
        long writes = DatabaseHelper.getEventExecutor().snapshot().completed();
        var copy =
                helper
                        ? DatabaseHelper.getPlayerByUid(10002)
                        : store.find(Player.class).field("id").equal(10002).first();
        assertEquals(canonical(), copy.getCodex().getUnlockedReliquary());
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        assertEquals(writes, DatabaseHelper.getEventExecutor().snapshot().completed());
        assertEquals(before, document());
    }

    static final class HeldQueue implements AutoCloseable {
        final CountDownLatch release = new CountDownLatch(1);
        final DatabaseWriteQueue queue;

        HeldQueue(DatabaseWriteQueue queue) throws Exception {
            this.queue = queue;
            var entered = new CountDownLatch(queue.getCorePoolSize());
            for (int i = 0; i < queue.getCorePoolSize(); i++)
                queue.execute(
                        () -> {
                            entered.countDown();
                            try {
                                release.await();
                            } catch (InterruptedException interrupted) {
                                Thread.currentThread().interrupt();
                            }
                        });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
        }

        @Override
        public void close() throws Exception {
            release.countDown();
            assertTrue(queue.awaitIdle(Duration.ofSeconds(5)));
        }
    }

    @Test
    void anOrdinaryLookupCannotRollBackANewerEconomicCommitAfterTheQueueIsReleased()
            throws Exception {
        var canonical = seed(false);
        try (var held = new HeldQueue(DatabaseHelper.getEventExecutor())) {
            var snapshot = DatabaseHelper.getPlayerByUid(10002);
            assertEquals(1500, snapshot.getArtifactTransmuterData().getProgressUnits());
            canonical.getArtifactTransmuterData().setProgressUnits(4500);
            DatabaseHelper.saveGameSync(canonical);
        }
        assertEquals(
                4500,
                ((Number)
                                document()
                                        .get("artifactTransmuterData", Document.class)
                                        .get("progressUnits"))
                        .longValue());
    }

    @Test
    void loginPersistsTheNormalizedCodexOnceAndRetainsUnrelatedCollections() throws Exception {
        seed(true);
        var player = DatabaseHelper.getPlayerByUid(10002);
        var writes = new AtomicInteger();
        Runnable save =
                () -> {
                    writes.incrementAndGet();
                    DatabaseHelper.saveGameSync(player);
                };
        login(player, save);
        login(player, save);
        assertEquals(1, writes.get());
        assertEquals(canonical(), savedIds());
        var codex = document().get("codex", Document.class);
        assertEquals(List.of(100178), codex.getList("unlockedBook", Integer.class));
        assertEquals(List.of(11501), codex.getList("unlockedWeapon", Integer.class));
        assertFalse(codex.containsKey("reliquaryMigrationPending"));
        assertEquals(1500, player.getArtifactTransmuterData().getProgressUnits());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void failedMigrationAcknowledgementKeepsTheLoginRetryPending(boolean committed)
            throws Exception {
        seed(true);
        var player = DatabaseHelper.getPlayerByUid(10002);
        assertThrows(
                IllegalStateException.class,
                () ->
                        login(
                                player,
                                () -> {
                                    if (committed) DatabaseHelper.saveGameSync(player);
                                    throw new IllegalStateException(
                                            "injected codex migration acknowledgement failure");
                                }));
        var writes = new AtomicInteger();
        Runnable retry =
                () -> {
                    writes.incrementAndGet();
                    DatabaseHelper.saveGameSync(player);
                };
        login(player, retry);
        login(player, retry);
        assertEquals(1, writes.get());
        assertEquals(canonical(), savedIds());
    }

    static class LoginProbe extends RuntimeException {}

    @Test
    void theActualPlayerLoginPersistsMigrationBeforeStartingTheWorldManagers() throws Exception {
        var player = seed(true);
        var field = Player.class.getDeclaredField("sotsManager");
        field.setAccessible(true);
        field.set(
                player,
                new SotSManager(player) {
                    @Override
                    public void startRecovery() {
                        throw new LoginProbe();
                    }
                });
        assertThrows(LoginProbe.class, player::onLogin);
        assertEquals(canonical(), savedIds());
    }

    @Test
    void newlyLoadedResourceSuitsAreRecheckedAtLoginWithoutGrantingIncompleteSuits()
            throws Exception {
        var player = new RecordingPlayer();
        player.getCodex().getUnlockedReliquary().addAll(canonical());
        GameData.getCodexReliquaryArrayList().clear();
        player.getCodex().setPlayer(player);
        GameData.getCodexReliquaryArrayList().addAll(rows);
        var writes = new AtomicInteger();
        login(player, writes::incrementAndGet);
        assertEquals(1, writes.get());
        assertTrue(player.getCodex().getUnlockedReliquarySuitCodex().contains(sample.getId()));
        var incomplete = new RecordingPlayer();
        incomplete.getCodex().getUnlockedReliquary().add(sample.getIds().iterator().next());
        incomplete.getCodex().setPlayer(incomplete);
        login(incomplete, () -> fail("Incomplete suit must not trigger a migration save"));
        assertFalse(incomplete.getCodex().getUnlockedReliquarySuitCodex().contains(sample.getId()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"nullCodex", "nullCollections", "nullEntry"})
    void legacyNullCodexDataCanBeReadWithoutWritingAndExplicitlySaved(String kind)
            throws Exception {
        seed(false);
        var raw = document();
        if (kind.equals("nullCodex")) raw.put("codex", null);
        else {
            var codex = raw.get("codex", Document.class);
            if (kind.equals("nullCollections")) {
                for (String name :
                        List.of(
                                "unlockedWeapon",
                                "unlockedAnimal",
                                "unlockedMaterial",
                                "unlockedBook",
                                "unlockedTip",
                                "unlockedView",
                                "unlockedReliquary",
                                "unlockedReliquarySuitCodex")) codex.put(name, null);
            } else
                codex.put(
                        "unlockedReliquary",
                        Arrays.asList(null, sample.getIds().iterator().next() + 3));
        }
        store.getDatabase().getCollection("players").replaceOne(new Document("_id", 10002), raw);
        var player = assertDoesNotThrow(() -> DatabaseHelper.getPlayerByUid(10002));
        assertNotNull(player.getCodex().getUnlockedReliquary());
        assertNotNull(player.getCodex().getUnlockedBook());
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        assertEquals(raw, document());
        var writes = new AtomicInteger();
        login(
                player,
                () -> {
                    writes.incrementAndGet();
                    DatabaseHelper.saveGameSync(player);
                });
        DatabaseHelper.saveGameSync(player);
        var reloaded = assertDoesNotThrow(() -> DatabaseHelper.getPlayerByUid(10002));
        assertNotNull(reloaded.getCodex().getUnlockedBook());
        assertNotNull(reloaded.getCodex().getUnlockedReliquary());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void emptyAndFullyCanonicalCodexNeedNoLoginWrite(boolean collected) {
        var player = new RecordingPlayer();
        if (collected) {
            player.getCodex().getUnlockedReliquary().addAll(canonical());
            for (var row : rows)
                if (canonical().containsAll(row.getIds()))
                    player.getCodex().getUnlockedReliquarySuitCodex().add(row.getId());
        }
        player.getCodex().setPlayer(player);
        login(player, () -> fail("An unchanged codex must not write on login"));
        assertEquals(0, player.saves);
    }

    @Test
    void rebindingKeepsAnUnsavedMigrationPendingAndDoesNotScheduleWrites() throws Exception {
        seed(true);
        var player = DatabaseHelper.getPlayerByUid(10002);
        player.getCodex().setPlayer(player);
        var writes = new AtomicInteger();
        Runnable save =
                () -> {
                    writes.incrementAndGet();
                    DatabaseHelper.saveGameSync(player);
                };
        login(player, save);
        player.getCodex().setPlayer(player);
        login(player, save);
        assertEquals(1, writes.get());
        assertEquals(canonical(), savedIds());
    }
}
