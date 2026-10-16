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
import emu.grasscutter.net.proto.MusicGameBookRecordOuterClass.MusicGameBookRecord;
import emu.grasscutter.net.proto.MusicGameBookSaveModsReqOuterClass.MusicGameBookSaveModsReq;
import emu.grasscutter.net.proto.MusicGameBookSaveModsRspOuterClass.MusicGameBookSaveModsRsp;
import emu.grasscutter.net.proto.MusicGameBookSaveSettingsReqOuterClass.MusicGameBookSaveSettingsReq;
import emu.grasscutter.net.proto.MusicGameBookSettingsOuterClass.MusicGameBookSettings;
import emu.grasscutter.net.proto.MusicGameBookSettleReqOuterClass.MusicGameBookSettleReq;
import emu.grasscutter.net.proto.MusicGameBookStartReqOuterClass.MusicGameBookStartReq;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import emu.grasscutter.scripts.ScriptLoader;
import emu.grasscutter.server.game.*;
import emu.grasscutter.utils.JsonUtils;

import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.Stream;

/** Current native dispatch, real guarded Player writes and resource-backed exclusive choices. */
class MusicGameBookModsTest extends QuestTestFixture {
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
    void modPlayer() throws Exception {
        actual = new Player();
        actual.setUid(10002);
        attach(actual);
        playerStore =
                new GuardedPlayerTestStore(
                        actual,
                        () -> {
                            if (failWrite.get())
                                throw new IllegalStateException("mods before-write fault");
                            writes.incrementAndGet();
                        },
                        document -> {
                            if (failAfterWrite.getAndSet(false))
                                throw new IllegalStateException(
                                        "mods acknowledgement lost after commit");
                        });
        replaceField(DatabaseManager.class, null, "gameDatastore", playerStore.datastore());
    }

    void attach(Player target) throws Exception {
        var session =
                new GameSession(player.server) {
                    @Override
                    public void send(BasePacket packet) {
                        if (packet.getOpcode() == PacketOpcodes.MusicGameBookSaveModsRsp) {
                            try {
                                var reply = MusicGameBookSaveModsRsp.parseFrom(packet.getData());
                                if (reply.getRetcode() == 0
                                        && !reply.getJNJDOLEBBOOList().isEmpty())
                                    assertEquals(
                                            reply.getJNJDOLEBBOOList(),
                                            document().getList("musicGameBookMods", Integer.class),
                                            "Success must follow the acknowledged selection write");
                            } catch (com.google.protobuf.InvalidProtocolBufferException error) {
                                throw new AssertionError(error);
                            }
                            if (failReply.getAndSet(false))
                                throw new IllegalStateException("mods response transport fault");
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
    void closeModStore() throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        if (playerStore != null) playerStore.close();
        if (bookSeeded) {
            if (oldBook == null) GameData.getItemDataMap().remove(220110);
            else GameData.getItemDataMap().put(220110, oldBook);
        }
    }

    void dispatch(MusicGameBookSaveModsReq request, int sequence) throws Exception {
        dispatcher.handle(
                actual.getSession(),
                PacketOpcodes.MusicGameBookSaveModsReq,
                PacketHead.newBuilder().setClientSequenceId(sequence).build().toByteArray(),
                request.toByteArray());
    }

    MusicGameBookSaveModsRsp save(Integer... mods) throws Exception {
        return save(MusicGameBookSaveModsReq.newBuilder().addAllJNJDOLEBBOO(List.of(mods)).build());
    }

    MusicGameBookSaveModsRsp save(MusicGameBookSaveModsReq request) throws Exception {
        packets.clear();
        dispatch(request, 53);
        var replies =
                packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.MusicGameBookSaveModsRsp)
                        .toList();
        assertEquals(1, replies.size(), "An active selection request receives its own response");
        assertEquals(53, sequence(replies.get(0)));
        return MusicGameBookSaveModsRsp.parseFrom(replies.get(0).getData());
    }

    int sequence(BasePacket packet) {
        return assertDoesNotThrow(
                () -> PacketHead.parseFrom(packet.getHeader()).getClientSequenceId());
    }

    MusicGameBookAllDataNotify snapshot(Player target) throws Exception {
        packets.clear();
        target.getMusicGameBook().onLogin();
        var packet =
                packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.MusicGameBookAllDataNotify)
                        .reduce((a, b) -> b)
                        .orElseThrow();
        return MusicGameBookAllDataNotify.parseFrom(packet.getData());
    }

    Document document() {
        return playerStore
                .datastore()
                .getDatabase()
                .getCollection("players")
                .find(new Document("_id", actual.getUid()))
                .first();
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
        return MusicGameBookSettleReq.newBuilder()
                .setIHPMDKPGJLB(9991)
                .setCAEKFNKKEPG(MusicGameBookCatalog.current().beatmap(9991).maximumScore())
                .setFNCOKHKCDDG(true)
                .setLKIMDBMGKEM(1)
                .build();
    }

    @Test
    void freshProfilesHaveNoSelectionAndNeedNoImplicitWrite() throws Exception {
        assertTrue(snapshot(actual).getJNJDOLEBBOOList().isEmpty());
        assertNull(document());
        assertEquals(0, writes.get());
    }

    @Test
    void legacyProfilesWithoutModsKeepDefaultsWithoutAWrite() throws Exception {
        DatabaseHelper.saveGameSync(actual);
        playerStore
                .datastore()
                .getDatabase()
                .getCollection("players")
                .updateOne(
                        new Document("_id", actual.getUid()),
                        new Document("$unset", new Document("musicGameBookMods", "")));
        var before = document();
        assertTrue(snapshot(cold()).getJNJDOLEBBOOList().isEmpty());
        assertEquals(before, document());
        assertEquals(1, writes.get());
    }

    @Test
    void acknowledgedSelectionIsSortedPersistedAndIncludedInColdSnapshots() throws Exception {
        var response = save(4, 2, 3);
        assertEquals(0, response.getRetcode());
        assertEquals(List.of(2, 3, 4), response.getJNJDOLEBBOOList());
        assertEquals(response.getJNJDOLEBBOOList(), snapshot(actual).getJNJDOLEBBOOList());
        var loaded = snapshot(cold());
        assertEquals(response.getJNJDOLEBBOOList(), loaded.getJNJDOLEBBOOList());
        assertTrue(loaded.getPAEINCPKPHHList().isEmpty(), "Selection is not a completed mod badge");
        assertEquals(1, writes.get());
        assertTrue(actual.getInventory().getItems().isEmpty());
    }

    @Test
    void emptyRequestsClearTheSelectionAndRepeatedClearsDoNotWrite() throws Exception {
        assertEquals(0, save(1, 3).getRetcode());
        var empty = save();
        assertEquals(0, empty.getRetcode());
        assertTrue(empty.getJNJDOLEBBOOList().isEmpty());
        assertTrue(snapshot(cold()).getJNJDOLEBBOOList().isEmpty());
        var before = document();
        assertEquals(empty, save());
        assertEquals(before, document());
        assertEquals(2, writes.get());
    }

    @Test
    void clearingAnAbsentSelectionDoesNotCreateAPlayerDocument() throws Exception {
        assertEquals(0, save().getRetcode());
        assertNull(document());
        assertEquals(0, writes.get());
    }

    @Test
    void reorderingAnAcknowledgedSelectionDoesNotWriteAnotherRevision() throws Exception {
        var first = save(4, 3, 2);
        var before = document();
        assertEquals(first, save(2, 4, 3));
        assertEquals(before, document());
        assertEquals(1, writes.get());
    }

    static Stream<List<Integer>> invalidSelections() {
        return Stream.of(
                List.of(0),
                List.of(-1),
                List.of(Integer.MAX_VALUE),
                List.of(99),
                List.of(1, 1),
                List.of(1, 2),
                List.of(3, 4, 2, 1));
    }

    @ParameterizedTest
    @MethodSource("invalidSelections")
    void invalidUnknownDuplicateAndExclusiveSelectionsDoNotReplaceTheSavedList(List<Integer> mods)
            throws Exception {
        save(3);
        var before = document();
        var response = save(MusicGameBookSaveModsReq.newBuilder().addAllJNJDOLEBBOO(mods).build());
        assertNotEquals(0, response.getRetcode());
        assertTrue(response.getJNJDOLEBBOOList().isEmpty());
        assertEquals(before, document());
        assertEquals(List.of(3), snapshot(cold()).getJNJDOLEBBOOList());
    }

    @Test
    void displayCategoriesDoNotMakeAllHardModsMutuallyExclusive() throws Exception {
        assertEquals(List.of(2, 3, 4), save(2, 3, 4).getJNJDOLEBBOOList());
        assertEquals(List.of(1, 3, 4), save(1, 3, 4).getJNJDOLEBBOOList());
    }

    @Test
    void failureBeforeWriteDoesNotPublishAPreferenceOrSnapshot() throws Exception {
        failWrite.set(true);
        var response = save(1);
        assertNotEquals(0, response.getRetcode());
        assertTrue(response.getJNJDOLEBBOOList().isEmpty());
        assertNull(document());
        packets.clear();
        actual.getMusicGameBook().onLogin();
        assertTrue(
                packets.stream()
                        .noneMatch(p -> p.getOpcode() == PacketOpcodes.MusicGameBookAllDataNotify));
        failWrite.set(false);
        assertEquals(0, save(1).getRetcode());
        assertEquals(List.of(1), snapshot(cold()).getJNJDOLEBBOOList());
    }

    @Test
    void failedReplacementKeepsThePreviousCommittedValueUntilRetry() throws Exception {
        save(1);
        var before = document();
        failWrite.set(true);
        assertNotEquals(0, save(2).getRetcode());
        assertEquals(before, document());
        assertEquals(List.of(1), snapshot(cold()).getJNJDOLEBBOOList());
        failWrite.set(false);
        assertEquals(0, save(2).getRetcode());
        assertEquals(List.of(2), snapshot(cold()).getJNJDOLEBBOOList());
    }

    @Test
    void acknowledgementLossIsReconciledBeforeTheNextSelection() throws Exception {
        failAfterWrite.set(true);
        assertNotEquals(0, save(1).getRetcode());
        assertEquals(List.of(1), snapshot(cold()).getJNJDOLEBBOOList());
        assertEquals(0, save(2, 3).getRetcode());
        var before = document();
        assertEquals(List.of(2, 3), save(3, 2).getJNJDOLEBBOOList());
        assertEquals(before, document());
        assertEquals(List.of(2, 3), snapshot(cold()).getJNJDOLEBBOOList());
    }

    @Test
    void unresolvedWriteCannotBeReplacedByAnotherSelectionInMemory() throws Exception {
        failWrite.set(true);
        assertNotEquals(0, save(1).getRetcode());
        assertNotEquals(0, save(2, 4).getRetcode());
        failWrite.set(false);
        assertEquals(List.of(1), snapshot(actual).getJNJDOLEBBOOList());
        assertEquals(List.of(1), snapshot(cold()).getJNJDOLEBBOOList());
    }

    @Test
    void lostResponseRetriesTheConfirmedValueWithoutAnotherWrite() throws Exception {
        failReply.set(true);
        dispatch(MusicGameBookSaveModsReq.newBuilder().addJNJDOLEBBOO(2).build(), 71);
        assertTrue(
                packets.stream()
                        .noneMatch(p -> p.getOpcode() == PacketOpcodes.MusicGameBookSaveModsRsp));
        var before = document();
        assertEquals(0, save(2).getRetcode());
        assertEquals(before, document());
        assertEquals(1, writes.get());
    }

    @Test
    void concurrentIdenticalRequestsShareOneWriteAndKeepTheirOwnSequence() throws Exception {
        var request =
                MusicGameBookSaveModsReq.newBuilder().addAllJNJDOLEBBOO(List.of(2, 3, 4)).build();
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
                                    dispatch(request, sequence);
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
                        .filter(p -> p.getOpcode() == PacketOpcodes.MusicGameBookSaveModsRsp)
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
            assertEquals(0, MusicGameBookSaveModsRsp.parseFrom(response.getData()).getRetcode());
        assertEquals(1, writes.get());
        assertEquals(List.of(2, 3, 4), snapshot(cold()).getJNJDOLEBBOOList());
    }

    @Test
    void unknownWireFieldsDoNotCreateACompletedModRecord() throws Exception {
        var future =
                UnknownFieldSet.newBuilder()
                        .addField(4095, UnknownFieldSet.Field.newBuilder().addVarint(999).build())
                        .build();
        var response =
                save(
                        MusicGameBookSaveModsReq.newBuilder()
                                .addJNJDOLEBBOO(4)
                                .setUnknownFields(future)
                                .build());
        assertEquals(List.of(4), response.getJNJDOLEBBOOList());
        var data = snapshot(cold());
        assertEquals(List.of(4), data.getJNJDOLEBBOOList());
        assertTrue(data.getPAEINCPKPHHList().isEmpty());
    }

    @Test
    void malformedPayloadDoesNotChangeTheCommittedSelection() throws Exception {
        save(2);
        var before = document();
        packets.clear();
        dispatcher.handle(
                actual.getSession(),
                PacketOpcodes.MusicGameBookSaveModsReq,
                new byte[0],
                HexFormat.of().parseHex("4a02ff"));
        assertEquals(before, document());
        assertTrue(
                packets.stream()
                        .noneMatch(p -> p.getOpcode() == PacketOpcodes.MusicGameBookSaveModsRsp));
    }

    @Test
    void pendingSelectionsBlockSettingsThemesAndNewPerformancesUntilConfirmed() throws Exception {
        stockBook();
        failWrite.set(true);
        assertNotEquals(0, save(1).getRetcode());
        assertThrows(IllegalStateException.class, () -> actual.getMusicGameBook().unlockTheme(1));
        assertNotEquals(
                0,
                actual.getMusicGameBook()
                        .saveSettings(
                                MusicGameBookSaveSettingsReq.newBuilder()
                                        .setNKFEGNKDBBK(
                                                MusicGameBookSettings.newBuilder()
                                                        .setNHIEOPAEFLF(7))
                                        .build())
                        .getRetcode());
        assertNotEquals(
                0,
                actual.getMusicGameBook()
                        .startPerformance(
                                MusicGameBookStartReq.newBuilder().setIHPMDKPGJLB(9991).build())
                        .getRetcode());
        failWrite.set(false);
        var data = snapshot(actual);
        assertEquals(List.of(1), data.getJNJDOLEBBOOList());
        assertFalse(data.hasNKFEGNKDBBK());
        assertEquals(List.of(8), data.getDHDNMAMDHIPList());
        beginGuide();
    }

    @Test
    void selectionWritesConfirmPendingSettingsBeforeReplacingTheDocument() throws Exception {
        var expected = MusicGameBookSettings.newBuilder().setNHIEOPAEFLF(45).build();
        failWrite.set(true);
        assertNotEquals(
                0,
                actual.getMusicGameBook()
                        .saveSettings(
                                MusicGameBookSaveSettingsReq.newBuilder()
                                        .setNKFEGNKDBBK(expected)
                                        .build())
                        .getRetcode());
        assertNotEquals(0, save(2, 3).getRetcode());
        failWrite.set(false);
        var data = snapshot(actual);
        assertEquals(expected, data.getNKFEGNKDBBK());
        assertTrue(data.getJNJDOLEBBOOList().isEmpty());
        assertEquals(0, save(2, 3).getRetcode());
        var loaded = snapshot(cold());
        assertEquals(expected, loaded.getNKFEGNKDBBK());
        assertEquals(List.of(2, 3), loaded.getJNJDOLEBBOOList());
    }

    @Test
    void selectionWritesReconcileAnEarlierRewardWithoutPayingItTwice() throws Exception {
        stockBook();
        beginGuide();
        failWrite.set(true);
        assertNotEquals(0, actual.getMusicGameBook().settlePerformance(guide()).getRetcode());
        assertNotEquals(0, save(2).getRetcode());
        assertNull(document());
        failWrite.set(false);
        assertEquals(0, save(2).getRetcode());
        assertEquals(0, actual.getMusicGameBook().settlePerformance(guide()).getRetcode());
        var loaded = cold();
        assertEquals(List.of(2), snapshot(loaded).getJNJDOLEBBOOList());
        assertEquals(20, loaded.getProperties().get(PlayerProperty.PROP_PLAYER_HCOIN.getId()));
        assertEquals(
                20,
                loaded.getProperties()
                        .get(PlayerProperty.PROP_PLAYER_MUSIC_GAME_BOOK_COIN.getId()));
    }

    void begin(int id) {
        assertEquals(
                0,
                actual.getMusicGameBook()
                        .startPerformance(
                                MusicGameBookStartReq.newBuilder().setIHPMDKPGJLB(id).build())
                        .getRetcode());
    }

    MusicGameBookSettleReq performance(int id, int score, boolean fullCombo) {
        int count = MusicGameBookCatalog.current().beatmap(id).noteCount();
        return MusicGameBookSettleReq.newBuilder()
                .setIHPMDKPGJLB(id)
                .setCAEKFNKKEPG(score)
                .setFNCOKHKCDDG(true)
                .setLKIMDBMGKEM(1)
                .setFJDDGBILJCF(fullCombo ? 1 : 0)
                .addAllKBBAMIPLHEJ(List.of(count - (fullCombo ? 0 : 1), 0, fullCombo ? 0 : 1))
                .build();
    }

    MusicGameBookRecord record(Player target, int id) throws Exception {
        return snapshot(target).getPAEINCPKPHHList().stream()
                .filter(row -> row.getIHPMDKPGJLB() == id)
                .findFirst()
                .orElse(MusicGameBookRecord.getDefaultInstance());
    }

    @Test
    void easyOfficialPerformancesDoNotStoreScoresOrFullCombo() throws Exception {
        stockBook();
        save(1, 3, 4);
        begin(1);
        var result =
                actual.getMusicGameBook()
                        .settlePerformance(
                                performance(
                                        1,
                                        MusicGameBookCatalog.current().beatmap(1).maximumScore(),
                                        true));
        assertEquals(0, result.getRetcode());
        assertFalse(result.getPHKLGIKKAMA(), "Lower-difficulty official scores are not recorded");
        assertEquals(0, record(cold(), 1).getMCPDKDGGBDD());
        assertFalse(
                document()
                        .get("musicGameBookRecords", Document.class)
                        .get("1", Document.class)
                        .getBoolean("fullCombo", false));
    }

    @Test
    void anEasyRunCannotReplaceAnEarlierOfficialRecordOrItsFullCombo() throws Exception {
        stockBook();
        int maximum = MusicGameBookCatalog.current().beatmap(1).maximumScore();
        int previous = (maximum + 1) / 2;
        begin(1);
        assertTrue(
                actual.getMusicGameBook()
                        .settlePerformance(performance(1, previous, false))
                        .getPHKLGIKKAMA());
        save(1);
        var before = document();
        begin(1);
        var result = actual.getMusicGameBook().settlePerformance(performance(1, maximum, true));
        assertEquals(0, result.getRetcode());
        assertFalse(result.getPHKLGIKKAMA());
        assertEquals(before, document());
        assertEquals(previous, record(cold(), 1).getMCPDKDGGBDD());
        assertFalse(
                document()
                        .get("musicGameBookRecords", Document.class)
                        .get("1", Document.class)
                        .getBoolean("fullCombo", false));
    }

    static Stream<List<Integer>> harderSelections() {
        return Stream.of(List.of(2), List.of(3), List.of(4), List.of(2, 3, 4));
    }

    @ParameterizedTest
    @MethodSource("harderSelections")
    void harderModsRetainOfficialScoresAndFullCombo(List<Integer> mods) throws Exception {
        stockBook();
        save(MusicGameBookSaveModsReq.newBuilder().addAllJNJDOLEBBOO(mods).build());
        begin(1);
        int maximum = MusicGameBookCatalog.current().beatmap(1).maximumScore();
        var result = actual.getMusicGameBook().settlePerformance(performance(1, maximum, true));
        assertEquals(0, result.getRetcode());
        assertTrue(result.getPHKLGIKKAMA());
        assertEquals(maximum, record(cold(), 1).getMCPDKDGGBDD());
        assertTrue(
                document()
                        .get("musicGameBookRecords", Document.class)
                        .get("1", Document.class)
                        .getBoolean("fullCombo"));
    }

    @Test
    void guideBeatmapsIgnoreModsThatTheClientDoesNotActivate() throws Exception {
        stockBook();
        save(1);
        beginGuide();
        var result = actual.getMusicGameBook().settlePerformance(guide());
        assertEquals(0, result.getRetcode());
        assertTrue(result.getPHKLGIKKAMA());
        var loaded = cold();
        assertEquals(
                MusicGameBookCatalog.current().beatmap(9991).maximumScore(),
                record(loaded, 9991).getMCPDKDGGBDD());
        assertEquals(20, loaded.getProperties().get(PlayerProperty.PROP_PLAYER_HCOIN.getId()));
    }

    @Test
    void clearingTheNextSelectionCannotTurnAnEasyActiveRunIntoAnOfficialRecord() throws Exception {
        stockBook();
        save(1);
        begin(1);
        save();
        int maximum = MusicGameBookCatalog.current().beatmap(1).maximumScore();
        assertFalse(
                actual.getMusicGameBook()
                        .settlePerformance(performance(1, maximum, true))
                        .getPHKLGIKKAMA());
        assertEquals(0, record(cold(), 1).getMCPDKDGGBDD());
        begin(1);
        assertTrue(
                actual.getMusicGameBook()
                        .settlePerformance(performance(1, maximum, true))
                        .getPHKLGIKKAMA());
        assertEquals(maximum, record(cold(), 1).getMCPDKDGGBDD());
    }

    @Test
    void changingFutureModsDoesNotDiscardTheCurrentlyAdmittedOrdinaryScore() throws Exception {
        stockBook();
        begin(1);
        save(1);
        int maximum = MusicGameBookCatalog.current().beatmap(1).maximumScore();
        int previous = (maximum + 1) / 2;
        assertTrue(
                actual.getMusicGameBook()
                        .settlePerformance(performance(1, previous, false))
                        .getPHKLGIKKAMA());
        begin(1);
        assertFalse(
                actual.getMusicGameBook()
                        .settlePerformance(performance(1, maximum, true))
                        .getPHKLGIKKAMA());
        assertEquals(previous, record(cold(), 1).getMCPDKDGGBDD());
    }

    @Test
    void admittingANewPerformanceReplacesItsOwnFrozenModEligibility() throws Exception {
        stockBook();
        save(1);
        begin(1);
        save(2);
        begin(1);
        int maximum = MusicGameBookCatalog.current().beatmap(1).maximumScore();
        assertTrue(
                actual.getMusicGameBook()
                        .settlePerformance(performance(1, maximum, true))
                        .getPHKLGIKKAMA());
        assertEquals(maximum, record(cold(), 1).getMCPDKDGGBDD());
    }

    @Test
    void aRejectedStartCannotReplaceTheEligibilityOfTheActiveRun() throws Exception {
        stockBook();
        save(1);
        begin(1);
        save(2);
        assertNotEquals(
                0,
                actual.getMusicGameBook()
                        .startPerformance(
                                MusicGameBookStartReq.newBuilder().setIHPMDKPGJLB(999999).build())
                        .getRetcode());
        int maximum = MusicGameBookCatalog.current().beatmap(1).maximumScore();
        assertFalse(
                actual.getMusicGameBook()
                        .settlePerformance(performance(1, maximum, true))
                        .getPHKLGIKKAMA());
        assertEquals(0, record(cold(), 1).getMCPDKDGGBDD());
    }

    @Test
    void repeatingAnEasySettlementDoesNotInventARecordOrRepeatItsExistingClearWrite()
            throws Exception {
        stockBook();
        save(1);
        begin(1);
        var request =
                performance(1, MusicGameBookCatalog.current().beatmap(1).maximumScore(), true);
        var first = actual.getMusicGameBook().settlePerformance(request);
        assertEquals(0, first.getRetcode());
        assertFalse(first.getPHKLGIKKAMA());
        var before = document();
        assertEquals(first, actual.getMusicGameBook().settlePerformance(request));
        assertEquals(before, document());
    }

    @Test
    void anUncertainExistingClearWriteCannotPublishAnEasyScoreDuringReconciliation()
            throws Exception {
        stockBook();
        save(1);
        begin(1);
        var request =
                performance(1, MusicGameBookCatalog.current().beatmap(1).maximumScore(), true);
        failAfterWrite.set(true);
        assertNotEquals(0, actual.getMusicGameBook().settlePerformance(request).getRetcode());
        assertEquals(0, record(cold(), 1).getMCPDKDGGBDD());
        var result = actual.getMusicGameBook().settlePerformance(request);
        assertEquals(0, result.getRetcode());
        assertFalse(result.getPHKLGIKKAMA());
        var before = document();
        assertEquals(result, actual.getMusicGameBook().settlePerformance(request));
        assertEquals(before, document());
    }
}
