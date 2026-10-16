package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.UnknownFieldSet;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.database.*;
import emu.grasscutter.game.entity.EntityTeam;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.PlayerProperty;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.MusicGameBookAllDataNotifyOuterClass.MusicGameBookAllDataNotify;
import emu.grasscutter.net.proto.MusicGameBookSaveSettingsReqOuterClass.MusicGameBookSaveSettingsReq;
import emu.grasscutter.net.proto.MusicGameBookSaveSettingsRspOuterClass.MusicGameBookSaveSettingsRsp;
import emu.grasscutter.net.proto.MusicGameBookSettingsOuterClass.MusicGameBookSettings;
import emu.grasscutter.net.proto.MusicGameBookSettleReqOuterClass.MusicGameBookSettleReq;
import emu.grasscutter.net.proto.MusicGameBookStartReqOuterClass.MusicGameBookStartReq;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import emu.grasscutter.scripts.ScriptLoader;
import emu.grasscutter.server.game.*;
import emu.grasscutter.utils.JsonUtils;

import org.bson.Document;
import org.junit.jupiter.api.*;

import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Native request dispatch with real Player documents, lost acknowledgements and cold reads. */
class MusicGameBookSettingsTest extends QuestTestFixture {
    static GameServerPacketHandler dispatcher;
    final List<BasePacket> packets = new CopyOnWriteArrayList<>();
    final AtomicBoolean failWrite = new AtomicBoolean();
    final AtomicBoolean failAfterWrite = new AtomicBoolean();
    final AtomicBoolean failReply = new AtomicBoolean();
    final AtomicInteger writes = new AtomicInteger();
    Player actual;
    GuardedPlayerTestStore playerStore;
    ItemData oldBook;
    boolean bookSeeded;

    @BeforeAll
    static void handlers() throws Exception {
        if (ScriptLoader.getEngine() == null) ScriptLoader.init();
        dispatcher = new GameServerPacketHandler(PacketHandler.class);
    }

    @BeforeEach
    void preferencesPlayer() throws Exception {
        actual = new Player();
        actual.setUid(10002);
        attach(actual);
        playerStore =
                new GuardedPlayerTestStore(
                        actual,
                        () -> {
                            if (failWrite.get())
                                throw new IllegalStateException("settings before-write fault");
                            writes.incrementAndGet();
                        },
                        document -> {
                            if (failAfterWrite.getAndSet(false))
                                throw new IllegalStateException(
                                        "settings acknowledgement lost after commit");
                        });
        replaceField(DatabaseManager.class, null, "gameDatastore", playerStore.datastore());
    }

    void attach(Player target) throws Exception {
        var session =
                new GameSession(player.server) {
                    @Override
                    public void send(BasePacket packet) {
                        if (packet.getOpcode() == PacketOpcodes.MusicGameBookSaveSettingsRsp) {
                            try {
                                var reply =
                                        MusicGameBookSaveSettingsRsp.parseFrom(packet.getData());
                                if (reply.getRetcode() == 0)
                                    assertNotNull(
                                            document().get("musicGameBookSettings"),
                                            "A success cannot precede the acknowledged Player"
                                                    + " write");
                            } catch (com.google.protobuf.InvalidProtocolBufferException error) {
                                throw new AssertionError(error);
                            }
                            if (failReply.getAndSet(false))
                                throw new IllegalStateException(
                                        "settings response transport fault");
                        }
                        packets.add(packet);
                    }
                };
        session.setPlayer(target);
        session.setState(GameSession.SessionState.ACTIVE);
        target.setSession(session);
        target.setWorld(player.getWorld());
        target.setScene(player.getScene());
        replaceField(Player.class, target, "teamManager", new TeamManager(target));
        target.getTeamManager().setEntity(new EntityTeam(target));
    }

    @AfterEach
    void closePreferencesStore() throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        if (playerStore != null) playerStore.close();
        if (bookSeeded) {
            if (oldBook == null) GameData.getItemDataMap().remove(220110);
            else GameData.getItemDataMap().put(220110, oldBook);
        }
    }

    MusicGameBookSettings settings(int first, int second, int third, int flags) {
        return MusicGameBookSettings.newBuilder()
                .setNHIEOPAEFLF(first)
                .setDBGINECAKNF(second)
                .setOFKMLIACIHC(third)
                .setFPCNMIDOPLO((flags & 1) != 0)
                .setDKOKBJOJGAP((flags & 2) != 0)
                .setJPMNHOHBKCA((flags & 4) != 0)
                .setJDPOJFFPMHN((flags & 8) != 0)
                .build();
    }

    void dispatch(Player target, MusicGameBookSaveSettingsReq request, int sequence)
            throws Exception {
        dispatcher.handle(
                target.getSession(),
                PacketOpcodes.MusicGameBookSaveSettingsReq,
                PacketHead.newBuilder().setClientSequenceId(sequence).build().toByteArray(),
                request.toByteArray());
    }

    MusicGameBookSaveSettingsRsp save(MusicGameBookSettings settings) throws Exception {
        return save(
                actual, MusicGameBookSaveSettingsReq.newBuilder().setNKFEGNKDBBK(settings).build());
    }

    MusicGameBookSaveSettingsRsp save(Player target, MusicGameBookSaveSettingsReq request)
            throws Exception {
        packets.clear();
        dispatch(target, request, 37);
        var replies =
                packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.MusicGameBookSaveSettingsRsp)
                        .toList();
        assertEquals(1, replies.size(), "An active request receives exactly its own response");
        assertEquals(37, sequence(replies.get(0)));
        return MusicGameBookSaveSettingsRsp.parseFrom(replies.get(0).getData());
    }

    MusicGameBookAllDataNotify snapshot(Player target) throws Exception {
        packets.clear();
        target.getMusicGameBook().onLogin();
        var row =
                packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.MusicGameBookAllDataNotify)
                        .reduce((a, b) -> b)
                        .orElseThrow();
        return MusicGameBookAllDataNotify.parseFrom(row.getData());
    }

    Document document() {
        return playerStore
                .datastore()
                .getDatabase()
                .getCollection("players")
                .find(new Document("_id", actual.getUid()))
                .first();
    }

    int sequence(BasePacket packet) {
        return assertDoesNotThrow(
                () -> PacketHead.parseFrom(packet.getHeader()).getClientSequenceId());
    }

    Player cold() throws Exception {
        var loaded =
                playerStore
                        .datastore()
                        .find(Player.class)
                        .filter(
                                dev.morphia.query.experimental.filters.Filters.eq(
                                        "_id", actual.getUid()))
                        .first();
        assertNotNull(loaded);
        attach(loaded);
        return loaded;
    }

    void stockBook() throws Exception {
        var data =
                JsonUtils.loadToList(
                                Path.of("resources/ExcelBinOutput/MaterialExcelConfigData.json"),
                                ItemData.class)
                        .stream()
                        .filter(row -> row.getId() == 220110)
                        .findFirst()
                        .orElseThrow();
        data.onLoad();
        oldBook = GameData.getItemDataMap().put(220110, data);
        bookSeeded = true;
        var item = new GameItem(data, 1);
        item.setOwner(actual);
        actual.getInventory().getItems().put(item.getGuid(), item);
        actual.getInventory().getInventoryTab(item.getItemType()).onAddItem(item);
    }

    void beginGuide() {
        assertEquals(
                0,
                actual.getMusicGameBook()
                        .startPerformance(
                                MusicGameBookStartReq.newBuilder().setIHPMDKPGJLB(9991).build())
                        .getRetcode());
    }

    MusicGameBookSettleReq guide() {
        int score = MusicGameBookCatalog.current().beatmap(9991).maximumScore();
        return MusicGameBookSettleReq.newBuilder()
                .setIHPMDKPGJLB(9991)
                .setCAEKFNKKEPG(score)
                .setFNCOKHKCDDG(true)
                .setLKIMDBMGKEM(1)
                .build();
    }

    @Test
    void newProfilesKeepClientDefaultsWithoutAnImplicitWrite() throws Exception {
        assertFalse(snapshot(actual).hasNKFEGNKDBBK());
        assertNull(document());
        assertEquals(0, writes.get());
    }

    @Test
    void legacyProfilesWithoutTheFieldKeepDefaultsWithoutAnImplicitMigration() throws Exception {
        DatabaseHelper.saveGameSync(actual);
        playerStore
                .datastore()
                .getDatabase()
                .getCollection("players")
                .updateOne(
                        new Document("_id", actual.getUid()),
                        new Document("$unset", new Document("musicGameBookSettings", "")));
        var before = document();
        assertFalse(before.containsKey("musicGameBookSettings"));
        assertFalse(snapshot(cold()).hasNKFEGNKDBBK());
        assertEquals(before, document());
        assertEquals(1, writes.get());
    }

    @Test
    void preferencesAreAcknowledgedPersistedAndSentAfterAColdRead() throws Exception {
        var expected = settings(21, 42, 63, 11);
        var response = save(expected);
        assertEquals(0, response.getRetcode());
        assertEquals(expected, response.getNKFEGNKDBBK());
        assertEquals(expected, snapshot(actual).getNKFEGNKDBBK());
        assertEquals(expected, snapshot(cold()).getNKFEGNKDBBK());
        assertEquals(1, writes.get());
    }

    @Test
    void unsignedWordsRetainAllTheirBitsAcrossMongoAndTheWire() throws Exception {
        var expected = settings(-1, Integer.MIN_VALUE, Integer.MAX_VALUE, 15);
        assertEquals(0, save(expected).getRetcode());
        assertEquals(expected, snapshot(cold()).getNKFEGNKDBBK());
    }

    @Test
    void falseFlagsReplaceTrueFlagsWithoutMergingTheOldSetting() throws Exception {
        for (int flags = 15; flags >= 0; flags--) {
            var expected = settings(31, 47, 59, flags);
            assertEquals(expected, save(expected).getNKFEGNKDBBK());
            assertEquals(expected, snapshot(cold()).getNKFEGNKDBBK());
        }
    }

    @Test
    void explicitZeroSettingsCanResetAPreviousPreference() throws Exception {
        save(settings(9, 8, 7, 15));
        var zero = MusicGameBookSettings.getDefaultInstance();
        assertEquals(0, save(zero).getRetcode());
        var data = snapshot(cold());
        assertTrue(data.hasNKFEGNKDBBK());
        assertEquals(zero, data.getNKFEGNKDBBK());
    }

    @Test
    void absentSettingsDoNotSilentlyResetAPersistedPreference() throws Exception {
        var expected = settings(12, 34, 56, 6);
        save(expected);
        var before = document();
        var response = save(actual, MusicGameBookSaveSettingsReq.getDefaultInstance());
        assertNotEquals(0, response.getRetcode());
        assertFalse(response.hasNKFEGNKDBBK());
        assertEquals(before, document());
        assertEquals(expected, snapshot(cold()).getNKFEGNKDBBK());
    }

    @Test
    void confirmedIdenticalRequestsDoNotWriteAnotherPlayerRevision() throws Exception {
        var expected = settings(25, 50, 75, 3);
        var first = save(expected);
        var before = document();
        assertEquals(first, save(expected));
        assertEquals(before, document());
        assertEquals(1, writes.get());
    }

    @Test
    void failureBeforeTheFirstWritePublishesNoSuccessfulPreference() throws Exception {
        var expected = settings(11, 22, 33, 5);
        failWrite.set(true);
        var response = save(expected);
        assertNotEquals(0, response.getRetcode());
        assertFalse(response.hasNKFEGNKDBBK());
        assertNull(document());
        packets.clear();
        actual.getMusicGameBook().onLogin();
        assertTrue(
                packets.stream()
                        .noneMatch(p -> p.getOpcode() == PacketOpcodes.MusicGameBookAllDataNotify));
        failWrite.set(false);
        assertEquals(0, save(expected).getRetcode());
        assertEquals(expected, snapshot(cold()).getNKFEGNKDBBK());
    }

    @Test
    void failedReplacementKeepsTheLastCommittedPreferenceUntilRetry() throws Exception {
        var first = settings(10, 20, 30, 4);
        var second = settings(20, 30, 40, 8);
        save(first);
        var before = document();
        failWrite.set(true);
        assertNotEquals(0, save(second).getRetcode());
        assertEquals(before, document());
        assertEquals(first, snapshot(cold()).getNKFEGNKDBBK());
        failWrite.set(false);
        assertEquals(0, save(second).getRetcode());
        assertEquals(second, snapshot(cold()).getNKFEGNKDBBK());
    }

    @Test
    void acknowledgementLossIsReconciledBeforeTheNextPreference() throws Exception {
        var first = settings(1, 2, 3, 4);
        var second = settings(4, 5, 6, 7);
        failAfterWrite.set(true);
        assertNotEquals(0, save(first).getRetcode());
        assertEquals(first, snapshot(cold()).getNKFEGNKDBBK());
        assertEquals(0, save(second).getRetcode());
        var committed = document();
        assertEquals(second, save(second).getNKFEGNKDBBK());
        assertEquals(committed, document());
        assertEquals(second, snapshot(cold()).getNKFEGNKDBBK());
    }

    @Test
    void unresolvedWritesCannotBeOverwrittenByAnotherRequestInMemory() throws Exception {
        var first = settings(2, 3, 4, 5);
        failWrite.set(true);
        assertNotEquals(0, save(first).getRetcode());
        assertNotEquals(0, save(settings(80, 90, 100, 15)).getRetcode());
        failWrite.set(false);
        assertEquals(first, snapshot(actual).getNKFEGNKDBBK());
        assertEquals(first, snapshot(cold()).getNKFEGNKDBBK());
    }

    @Test
    void lostResponseCanBeRetriedWithoutRepeatingTheConfirmedWrite() throws Exception {
        var expected = settings(7, 8, 9, 10);
        failReply.set(true);
        dispatch(
                actual,
                MusicGameBookSaveSettingsReq.newBuilder().setNKFEGNKDBBK(expected).build(),
                71);
        assertTrue(
                packets.stream()
                        .noneMatch(
                                p -> p.getOpcode() == PacketOpcodes.MusicGameBookSaveSettingsRsp));
        var before = document();
        assertEquals(0, save(expected).getRetcode());
        assertEquals(before, document());
        assertEquals(1, writes.get());
    }

    @Test
    void concurrentIdenticalRequestsShareOneWriteAndKeepTheirOwnSequences() throws Exception {
        var request =
                MusicGameBookSaveSettingsReq.newBuilder()
                        .setNKFEGNKDBBK(settings(3, 6, 9, 12))
                        .build();
        var executor = Executors.newFixedThreadPool(4);
        try {
            var start = new CountDownLatch(1);
            var futures = new ArrayList<Future<?>>();
            for (int i = 0; i < 8; i++) {
                int sequence = 100 + i;
                futures.add(
                        executor.submit(
                                () -> {
                                    start.await();
                                    dispatch(actual, request, sequence);
                                    return null;
                                }));
            }
            start.countDown();
            for (var future : futures) future.get(20, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        var responses =
                packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.MusicGameBookSaveSettingsRsp)
                        .toList();
        assertEquals(8, responses.size());
        assertEquals(
                java.util.stream.IntStream.range(100, 108)
                        .boxed()
                        .collect(java.util.stream.Collectors.toSet()),
                responses.stream()
                        .map(this::sequence)
                        .collect(java.util.stream.Collectors.toSet()));
        for (var response : responses)
            assertEquals(
                    0, MusicGameBookSaveSettingsRsp.parseFrom(response.getData()).getRetcode());
        assertEquals(1, writes.get());
        assertEquals(request.getNKFEGNKDBBK(), snapshot(cold()).getNKFEGNKDBBK());
    }

    @Test
    void unknownFieldsAreNotPersistedAsNewPreferencesOrProgress() throws Exception {
        var future =
                UnknownFieldSet.newBuilder()
                        .addField(4095, UnknownFieldSet.Field.newBuilder().addVarint(999).build())
                        .build();
        var known = settings(5, 10, 15, 1);
        assertEquals(
                known, save(known.toBuilder().setUnknownFields(future).build()).getNKFEGNKDBBK());
        assertEquals(known, snapshot(cold()).getNKFEGNKDBBK());
        assertTrue(actual.getInventory().getItems().isEmpty());
        assertTrue(snapshot(actual).getPAEINCPKPHHList().isEmpty());
    }

    @Test
    void malformedPayloadCannotReplaceAnAcknowledgedPreference() throws Exception {
        save(settings(1, 1, 1, 1));
        var before = document();
        packets.clear();
        dispatcher.handle(
                actual.getSession(),
                PacketOpcodes.MusicGameBookSaveSettingsReq,
                new byte[0],
                HexFormat.of().parseHex("7202ff"));
        assertEquals(before, document());
        assertTrue(
                packets.stream()
                        .noneMatch(
                                p -> p.getOpcode() == PacketOpcodes.MusicGameBookSaveSettingsRsp));
    }

    @Test
    void pendingPreferencesBlockThemeChangesAndNewPerformancesUntilConfirmed() throws Exception {
        stockBook();
        var expected = settings(10, 12, 14, 9);
        failWrite.set(true);
        assertNotEquals(0, save(expected).getRetcode());
        assertThrows(IllegalStateException.class, () -> actual.getMusicGameBook().unlockTheme(1));
        assertNotEquals(
                0,
                actual.getMusicGameBook()
                        .startPerformance(
                                MusicGameBookStartReq.newBuilder().setIHPMDKPGJLB(9991).build())
                        .getRetcode());
        failWrite.set(false);
        var data = snapshot(actual);
        assertEquals(expected, data.getNKFEGNKDBBK());
        assertEquals(List.of(8), data.getDHDNMAMDHIPList());
        assertTrue(data.getPAEINCPKPHHList().isEmpty());
        beginGuide();
    }

    @Test
    void preferencesAndAnOrdinaryFirstRewardSurviveTheSamePlayerDocument() throws Exception {
        stockBook();
        var expected = settings(15, 30, 45, 6);
        assertEquals(0, save(expected).getRetcode());
        beginGuide();
        assertEquals(0, actual.getMusicGameBook().settlePerformance(guide()).getRetcode());
        var loaded = cold();
        var data = snapshot(loaded);
        assertEquals(expected, data.getNKFEGNKDBBK());
        assertEquals(20, loaded.getProperties().get(PlayerProperty.PROP_PLAYER_HCOIN.getId()));
        assertEquals(
                20,
                loaded.getProperties()
                        .get(PlayerProperty.PROP_PLAYER_MUSIC_GAME_BOOK_COIN.getId()));
        assertTrue(
                data.getPAEINCPKPHHList().stream()
                        .anyMatch(row -> row.getIHPMDKPGJLB() == 9991 && row.getNFHCCJALOAJ()));
    }

    @Test
    void settingWritesReconcileAnEarlierRewardAndDoNotPayItTwice() throws Exception {
        stockBook();
        beginGuide();
        failWrite.set(true);
        assertNotEquals(0, actual.getMusicGameBook().settlePerformance(guide()).getRetcode());
        var expected = settings(8, 16, 24, 5);
        assertNotEquals(0, save(expected).getRetcode());
        assertNull(document());
        failWrite.set(false);
        assertEquals(0, save(expected).getRetcode());
        assertEquals(0, actual.getMusicGameBook().settlePerformance(guide()).getRetcode());
        var loaded = cold();
        assertEquals(expected, snapshot(loaded).getNKFEGNKDBBK());
        assertEquals(20, loaded.getProperties().get(PlayerProperty.PROP_PLAYER_HCOIN.getId()));
        assertEquals(
                20,
                loaded.getProperties()
                        .get(PlayerProperty.PROP_PLAYER_MUSIC_GAME_BOOK_COIN.getId()));
    }

    @Test
    void settingWritesConfirmAPendingThemeGrantBeforeChangingPreferences() throws Exception {
        failWrite.set(true);
        assertThrows(IllegalStateException.class, () -> actual.getMusicGameBook().unlockTheme(1));
        var expected = settings(4, 8, 12, 2);
        assertNotEquals(0, save(expected).getRetcode());
        assertNull(document());
        failWrite.set(false);
        assertEquals(0, save(expected).getRetcode());
        var data = snapshot(cold());
        assertEquals(expected, data.getNKFEGNKDBBK());
        assertEquals(List.of(1, 8), data.getDHDNMAMDHIPList());
    }

    @Test
    void anActivePerformanceCannotSettlePastAnUnconfirmedPreference() throws Exception {
        stockBook();
        beginGuide();
        var balances = new HashMap<>(actual.getProperties());
        var expected = settings(12, 24, 36, 7);
        failWrite.set(true);
        assertNotEquals(0, save(expected).getRetcode());
        assertNotEquals(0, actual.getMusicGameBook().settlePerformance(guide()).getRetcode());
        assertEquals(balances, actual.getProperties());
        assertNull(document());
        failWrite.set(false);
        assertEquals(0, save(expected).getRetcode());
        assertEquals(0, actual.getMusicGameBook().settlePerformance(guide()).getRetcode());
        var loaded = cold();
        assertEquals(expected, snapshot(loaded).getNKFEGNKDBBK());
        assertEquals(20, loaded.getProperties().get(PlayerProperty.PROP_PLAYER_HCOIN.getId()));
        assertEquals(
                20,
                loaded.getProperties()
                        .get(PlayerProperty.PROP_PLAYER_MUSIC_GAME_BOOK_COIN.getId()));
    }
}
