package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;

import dev.morphia.query.experimental.filters.Filters;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.entity.EntityTeam;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.systems.InventorySystem;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.MusicGameBookAllDataNotifyOuterClass.MusicGameBookAllDataNotify;
import emu.grasscutter.net.proto.MusicGameBookRecordOuterClass.MusicGameBookRecord;
import emu.grasscutter.net.proto.MusicGameBookSettleReqOuterClass.MusicGameBookSettleReq;
import emu.grasscutter.net.proto.MusicGameBookSettleRspOuterClass.MusicGameBookSettleRsp;
import emu.grasscutter.net.proto.MusicGameBookStartReqOuterClass.MusicGameBookStartReq;
import emu.grasscutter.net.proto.MusicGameBookStartRspOuterClass.MusicGameBookStartRsp;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import emu.grasscutter.scripts.ScriptLoader;
import emu.grasscutter.server.game.*;
import emu.grasscutter.utils.JsonUtils;

import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Official resource scores through packet dispatch, actual Player and isolated acknowledged writes.
 */
class MusicGameBookPerformanceTest extends QuestTestFixture {
    static GameServerPacketHandler dispatcher;
    static ItemData bookData;
    static final Map<Integer, Integer> maximums = new HashMap<>();
    static final Map<Integer, Integer> notes = new HashMap<>();
    final List<BasePacket> packets = new CopyOnWriteArrayList<>();
    final AtomicBoolean failWrite = new AtomicBoolean();
    final AtomicBoolean failAfterWrite = new AtomicBoolean();
    final AtomicBoolean failSnapshot = new AtomicBoolean();
    final AtomicBoolean failRewardHint = new AtomicBoolean();
    final AtomicBoolean failSettleReply = new AtomicBoolean();
    final Thread caller = Thread.currentThread();
    Player actual;
    GuardedPlayerTestStore playerStore;
    ItemData oldBook;
    GameItem book;

    @BeforeAll
    static void resources() throws Exception {
        if (ScriptLoader.getEngine() == null) ScriptLoader.init();
        dispatcher = new GameServerPacketHandler(PacketHandler.class);
        bookData =
                JsonUtils.loadToList(
                                Path.of("resources/ExcelBinOutput/MaterialExcelConfigData.json"),
                                ItemData.class)
                        .stream()
                        .filter(x -> x.getId() == 220110)
                        .findFirst()
                        .orElseThrow();
        bookData.onLoad();
        for (var entry :
                JsonParser.parseString(
                                Files.readString(
                                        Path.of(
                                                "resources/ExcelBinOutput/MusicGameBookBeatmapSettleExcelConfigData.json")))
                        .getAsJsonArray()) {
            var row = entry.getAsJsonObject();
            maximums.put(row.get("ELHHKOMFEMG").getAsInt(), row.get("JCLCGFHNJMB").getAsInt());
            notes.put(row.get("ELHHKOMFEMG").getAsInt(), row.get("noteCount").getAsInt());
        }
        assertEquals(559, maximums.size());
    }

    @BeforeEach
    void playerForMusic() throws Exception {
        replaceField(
                GameServer.class,
                player.server,
                "inventorySystem",
                new InventorySystem(player.server));
        oldBook = GameData.getItemDataMap().put(220110, bookData);
        actual = new Player();
        actual.setUid(10002);
        attach(actual);
        playerStore =
                new GuardedPlayerTestStore(
                        actual,
                        () -> {
                            if (failWrite.get())
                                throw new IllegalStateException("music score before-write fault");
                        },
                        doc -> {
                            if (Thread.currentThread() == caller
                                    && doc.containsKey("musicGameBookRecords")
                                    && failAfterWrite.getAndSet(false))
                                throw new IllegalStateException(
                                        "music score acknowledgement lost after actual commit");
                        });
        book = new GameItem(bookData, 1);
        book.setOwner(actual);
        actual.getInventory().getItems().put(book.getGuid(), book);
        actual.getInventory().getInventoryTab(book.getItemType()).onAddItem(book);
        store.save(book);
        packets.clear();
    }

    void attach(Player target) throws Exception {
        var session =
                new GameSession(player.server) {
                    @Override
                    public void send(BasePacket packet) {
                        if (packet.getOpcode() == PacketOpcodes.MusicGameBookSettleRsp
                                && failSettleReply.getAndSet(false))
                            throw new IllegalStateException(
                                    "music settlement response transport fault");
                        if (packet.getOpcode() == PacketOpcodes.ItemAddHintNotify
                                && failRewardHint.getAndSet(false))
                            throw new IllegalStateException("music reward hint transport fault");
                        if (packet.getOpcode() == PacketOpcodes.MusicGameBookAllDataNotify
                                && failSnapshot.getAndSet(false))
                            throw new IllegalStateException("music score snapshot transport fault");
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
    void restoreMusic() throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        if (playerStore != null) playerStore.close();
        if (oldBook == null) GameData.getItemDataMap().remove(220110);
        else GameData.getItemDataMap().put(220110, oldBook);
    }

    MusicGameBookStartRsp start(int id) throws Exception {
        return start(MusicGameBookStartReq.newBuilder().setIHPMDKPGJLB(id).build());
    }

    MusicGameBookStartRsp start(MusicGameBookStartReq req) throws Exception {
        packets.clear();
        dispatcher.handle(
                actual.getSession(),
                PacketOpcodes.MusicGameBookStartReq,
                new byte[0],
                req.toByteArray());
        var replies =
                packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.MusicGameBookStartRsp)
                        .toList();
        assertEquals(1, replies.size(), "A book start must always receive its own response");
        return MusicGameBookStartRsp.parseFrom(replies.get(0).getData());
    }

    MusicGameBookSettleReq report(int id, int score, boolean fullCombo) {
        return MusicGameBookSettleReq.newBuilder()
                .setIHPMDKPGJLB(id)
                .setCAEKFNKKEPG(score)
                .setFNCOKHKCDDG(true)
                .setFJDDGBILJCF(fullCombo ? 1 : 0)
                .setLKIMDBMGKEM(1)
                .setDFPIEMBCGCN(score * 100f / maximums.get(id))
                .addAllKBBAMIPLHEJ(
                        List.of(notes.get(id) - (fullCombo ? 0 : 1), 0, fullCombo ? 0 : 1))
                .build();
    }

    MusicGameBookSettleRsp settle(MusicGameBookSettleReq req) throws Exception {
        packets.clear();
        dispatcher.handle(
                actual.getSession(),
                PacketOpcodes.MusicGameBookSettleReq,
                new byte[0],
                req.toByteArray());
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        var replies =
                packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.MusicGameBookSettleRsp)
                        .toList();
        assertEquals(1, replies.size(), "A book settlement must always receive its own response");
        return MusicGameBookSettleRsp.parseFrom(replies.get(0).getData());
    }

    List<MusicGameBookRecord> snapshot() throws Exception {
        packets.clear();
        actual.getMusicGameBook().onLogin();
        var notify =
                packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.MusicGameBookAllDataNotify)
                        .reduce((a, b) -> b)
                        .orElseThrow();
        return MusicGameBookAllDataNotify.parseFrom(notify.getData()).getPAEINCPKPHHList();
    }

    Document document() {
        return playerStore
                .datastore()
                .getDatabase()
                .getCollection("players")
                .withReadConcern(ReadConcern.MAJORITY)
                .withReadPreference(ReadPreference.primary())
                .find(new Document("_id", actual.getUid()))
                .first();
    }

    @Test
    void all559ResourceBeatmapsHaveAWorkingStartWithoutConsumingTheBook() throws Exception {
        for (int id : new TreeSet<>(maximums.keySet())) {
            var response = start(id);
            assertEquals(0, response.getRetcode(), "basic ID " + id);
            assertEquals(id, response.getIHPMDKPGJLB());
            assertEquals(0, response.getBCBDBEFONEF());
        }
        assertEquals(1, book.getCount());
        assertSame(book, actual.getInventory().getItemByGuid(book.getGuid()));
        assertNull(playerStore.read(actual.getUid()));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 100, 552, 9991, 9997})
    void scoreAndFullComboArePersistedForSongsAndGuides(int id) throws Exception {
        assertEquals(0, start(id).getRetcode());
        var result = settle(report(id, maximums.get(id), true));
        assertEquals(0, result.getRetcode());
        assertEquals(id, result.getIHPMDKPGJLB());
        assertTrue(result.getPHKLGIKKAMA());
        var saved = snapshot();
        var expected =
                id <= 4
                        ? java.util.stream.IntStream.rangeClosed(1, id).boxed().toList()
                        : id == 100
                                ? List.of(97, 98, 99, 100)
                                : id == 552 ? List.of(549, 550, 551, 552) : List.of(id);
        assertEquals(expected, saved.stream().map(MusicGameBookRecord::getIHPMDKPGJLB).toList());
        var played =
                saved.stream().filter(row -> row.getIHPMDKPGJLB() == id).findFirst().orElseThrow();
        assertEquals(maximums.get(id).intValue(), played.getMCPDKDGGBDD());
        assertTrue(played.getNFHCCJALOAJ());
        assertEquals(
                0, played.getJNJDOLEBBOOCount(), "Modifier completion has a separate workflow");
        assertNotNull(document());
    }

    @Test
    void bestScoreAndFullComboMergeIndependentlyAndLowerScoresDoNotOverwrite() throws Exception {
        start(1);
        assertTrue(settle(report(1, 2000, false)).getPHKLGIKKAMA());
        start(1);
        assertFalse(settle(report(1, 1000, true)).getPHKLGIKKAMA());
        start(1);
        assertTrue(settle(report(1, 2500, false)).getPHKLGIKKAMA());
        var row = snapshot().get(0);
        assertEquals(2500, row.getMCPDKDGGBDD());
        assertTrue(row.getNFHCCJALOAJ());
    }

    @Test
    void immediateIdenticalSettlementRetryKeepsTheOriginalResultWithoutAnotherWrite()
            throws Exception {
        start(1);
        var req = report(1, 1800, false);
        var first = settle(req);
        var before = document();
        assertEquals(first, settle(req));
        assertEquals(before, document());
    }

    @Test
    void differentBeatmapsKeepSeparateRecordsAndSnapshotOrder() throws Exception {
        start(9991);
        settle(report(9991, 100, false));
        start(2);
        settle(report(2, 200, false));
        assertEquals(
                List.of(2, 9991),
                snapshot().stream().map(MusicGameBookRecord::getIHPMDKPGJLB).toList());
    }

    @Test
    void scoreSurvivesAnActualColdMorphiaRead() throws Exception {
        start(1);
        settle(report(1, 1800, true));
        var cold =
                playerStore
                        .datastore()
                        .find(Player.class)
                        .filter(Filters.eq("_id", actual.getUid()))
                        .first();
        assertNotNull(cold);
        actual = cold;
        attach(cold);
        var row = snapshot().get(0);
        assertEquals(1800, row.getMCPDKDGGBDD());
        assertTrue(row.getNFHCCJALOAJ());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 553, 9998, 100000})
    void unknownResourceIdDoesNotStartOrCreateARecord(int id) throws Exception {
        assertNotEquals(0, start(id).getRetcode());
        assertTrue(snapshot().isEmpty());
        assertNull(document());
    }

    @Test
    void startRequiresAnOwnedSingleBook() throws Exception {
        book.setCount(2);
        assertNotEquals(0, start(1).getRetcode());
        book.setCount(1);
        replaceField(GameItem.class, book, "ownerId", 10003);
        assertNotEquals(0, start(1).getRetcode());
        replaceField(GameItem.class, book, "ownerId", actual.getUid());
        actual.getInventory().getItems().remove(book.getGuid());
        assertNotEquals(0, start(1).getRetcode());
        assertNull(document());
    }

    @Test
    void customGuidDoesNotBecomeAnOfficialRun() throws Exception {
        assertNotEquals(
                0,
                start(
                                MusicGameBookStartReq.newBuilder()
                                        .setIHPMDKPGJLB(1)
                                        .setBCBDBEFONEF(-1L)
                                        .build())
                        .getRetcode());
        assertNotEquals(
                0,
                settle(report(1, 100, false).toBuilder().setBCBDBEFONEF(-1L).build()).getRetcode());
        assertTrue(snapshot().isEmpty());
    }

    @Test
    void unsolicitedSettlementDoesNotCreateProgress() throws Exception {
        assertNotEquals(0, settle(report(1, 100, false)).getRetcode());
        assertNull(document());
    }

    @Test
    void wrongRunDoesNotCloseTheCorrectRun() throws Exception {
        start(1);
        assertNotEquals(0, settle(report(2, 100, false)).getRetcode());
        assertEquals(0, settle(report(1, 100, false)).getRetcode());
        assertEquals(1, snapshot().get(0).getIHPMDKPGJLB());
    }

    @Test
    void restartingChangesTheActiveBeatmap() throws Exception {
        start(1);
        start(2);
        assertNotEquals(0, settle(report(1, 100, false)).getRetcode());
        assertEquals(0, settle(report(2, 100, false)).getRetcode());
    }

    @Test
    void interruptedNativeRequestCanOmitBasicIdAndDoesNotSaveAScore() throws Exception {
        start(1);
        assertEquals(0, settle(MusicGameBookSettleReq.getDefaultInstance()).getRetcode());
        assertTrue(snapshot().isEmpty());
        assertNull(document());
        assertNotEquals(0, settle(report(1, 100, false)).getRetcode());
    }

    @Test
    void zeroScoreIsNotANewHighScore() throws Exception {
        start(1);
        var result = settle(report(1, 0, false));
        assertEquals(0, result.getRetcode());
        assertFalse(result.getPHKLGIKKAMA());
        assertTrue(snapshot().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, Integer.MIN_VALUE, 3481})
    void unsignedOverflowAndScoresAboveTheResourceMaximumAreRejected(int score) throws Exception {
        assertEquals(3480, maximums.get(1));
        start(1);
        assertNotEquals(0, settle(report(1, score, false)).getRetcode());
        assertNull(document());
        assertEquals(0, settle(report(1, 100, false)).getRetcode());
    }

    @Test
    void contradictoryFullComboDoesNotGrantFullCombo() throws Exception {
        start(1);
        var bad = report(1, 100, true).toBuilder().setKBBAMIPLHEJ(2, 1).build();
        assertNotEquals(0, settle(bad).getRetcode());
        assertTrue(snapshot().isEmpty());
        assertEquals(0, settle(report(1, 100, false)).getRetcode());
    }

    @Test
    void missingJudgementCountersDoesNotAcceptAClaimedFullCombo() throws Exception {
        start(1);
        assertNotEquals(
                0,
                settle(report(1, 100, true).toBuilder().clearKBBAMIPLHEJ().build()).getRetcode());
        assertTrue(snapshot().isEmpty());
    }

    @Test
    void storageFailureCanBeRetriedWithoutPublishingAnUnconfirmedScore() throws Exception {
        start(1);
        failWrite.set(true);
        var req = report(1, 1000, true);
        assertNotEquals(0, settle(req).getRetcode());
        assertNull(document());
        assertTrue(
                packets.stream()
                        .noneMatch(p -> p.getOpcode() == PacketOpcodes.MusicGameBookAllDataNotify));
        failWrite.set(false);
        assertEquals(0, settle(req).getRetcode());
        assertEquals(1000, snapshot().get(0).getMCPDKDGGBDD());
    }

    @Test
    void lostAcknowledgementAfterCommitIsConfirmedByReopeningAndRetryRemainsIdempotent()
            throws Exception {
        start(1);
        failAfterWrite.set(true);
        var req = report(1, 1000, true);
        assertNotEquals(0, settle(req).getRetcode());
        assertNotNull(document());
        assertTrue(
                packets.stream()
                        .noneMatch(p -> p.getOpcode() == PacketOpcodes.MusicGameBookAllDataNotify));
        assertEquals(1000, snapshot().get(0).getMCPDKDGGBDD());
        var before = document();
        assertEquals(0, settle(req).getRetcode());
        assertEquals(before, document());
    }

    @Test
    void pendingWriteCannotBeOverwrittenByANewRun() throws Exception {
        start(1);
        failWrite.set(true);
        assertNotEquals(0, settle(report(1, 1000, true)).getRetcode());
        assertNotEquals(0, start(2).getRetcode());
        failWrite.set(false);
        assertEquals(0, start(2).getRetcode());
        settle(report(2, 200, false));
        assertEquals(
                List.of(1, 2),
                snapshot().stream().map(MusicGameBookRecord::getIHPMDKPGJLB).toList());
    }

    @Test
    void scoreResponsesRetainTheRequestSequence() throws Exception {
        packets.clear();
        dispatcher.handle(
                actual.getSession(),
                PacketOpcodes.MusicGameBookStartReq,
                PacketHead.newBuilder().setClientSequenceId(88).build().toByteArray(),
                MusicGameBookStartReq.newBuilder().setIHPMDKPGJLB(1).build().toByteArray());
        var started =
                packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.MusicGameBookStartRsp)
                        .findFirst()
                        .orElseThrow();
        assertNotNull(started.getHeader());
        assertEquals(88, PacketHead.parseFrom(started.getHeader()).getClientSequenceId());
        packets.clear();
        dispatcher.handle(
                actual.getSession(),
                PacketOpcodes.MusicGameBookSettleReq,
                PacketHead.newBuilder().setClientSequenceId(89).build().toByteArray(),
                report(1, 100, false).toByteArray());
        var ended =
                packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.MusicGameBookSettleRsp)
                        .findFirst()
                        .orElseThrow();
        assertNotNull(ended.getHeader());
        assertEquals(89, PacketHead.parseFrom(ended.getHeader()).getClientSequenceId());
    }

    @Test
    void snapshotTransportFailureDoesNotRejectAnAcknowledgedScore() throws Exception {
        start(1);
        failSnapshot.set(true);
        assertEquals(0, settle(report(1, 1000, true)).getRetcode());
        assertEquals(1000, snapshot().get(0).getMCPDKDGGBDD());
        assertNotNull(document());
    }

    @Test
    void allZeroJudgementsCannotClaimAFullCombo() throws Exception {
        start(1);
        assertNotEquals(
                0,
                settle(
                                report(1, 100, true).toBuilder()
                                        .clearKBBAMIPLHEJ()
                                        .addAllKBBAMIPLHEJ(List.of(0, 0, 0))
                                        .build())
                        .getRetcode());
        assertTrue(snapshot().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void themeAndPerformanceConfirmationsKeepBothIntents(boolean committed) throws Exception {
        var theme =
                JsonUtils.loadToList(
                                Path.of("resources/ExcelBinOutput/MaterialExcelConfigData.json"),
                                ItemData.class)
                        .stream()
                        .filter(x -> x.getId() == 319001)
                        .findFirst()
                        .orElseThrow();
        theme.onLoad();
        var old = GameData.getItemDataMap().put(319001, theme);
        try {
            start(1);
            if (committed) failAfterWrite.set(true);
            else failWrite.set(true);
            assertNotEquals(0, settle(report(1, 1000, true)).getRetcode());
            failWrite.set(false);
            assertTrue(actual.getInventory().addItem(new GameItem(theme, 1)));
            assertEquals(1000, snapshot().get(0).getMCPDKDGGBDD());
            var notify =
                    MusicGameBookAllDataNotify.parseFrom(
                            packets.stream()
                                    .filter(
                                            p ->
                                                    p.getOpcode()
                                                            == PacketOpcodes
                                                                    .MusicGameBookAllDataNotify)
                                    .findFirst()
                                    .orElseThrow()
                                    .getData());
            assertEquals(List.of(1, 8), notify.getDHDNMAMDHIPList());
            var saved = document();
            assertEquals(List.of(1), saved.getList("musicGameBookThemes", Integer.class));
            assertNotNull(saved.get("musicGameBookRecords"));
        } finally {
            if (old == null) GameData.getItemDataMap().remove(319001);
            else GameData.getItemDataMap().put(319001, old);
        }
    }

    private int musicCoins() {
        return actual.getProperties().getOrDefault(10058, 0);
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, MusicGameBookRecordData> records() throws Exception {
        var field = Player.class.getDeclaredField("musicGameBookRecords");
        field.setAccessible(true);
        return (Map<Integer, MusicGameBookRecordData>) field.get(actual);
    }

    private Set<Integer> completedMaps() throws Exception {
        var result = new TreeSet<Integer>();
        for (var record : snapshot())
            if (record.getNFHCCJALOAJ()) result.add(record.getIHPMDKPGJLB());
        return result;
    }

    @Test
    void normalPassPaysItsResourceRewardWithoutRequiringAFullCombo() throws Exception {
        start(1);
        assertEquals(0, settle(report(1, 1740, false)).getRetcode());
        assertEquals(20, musicCoins());
        assertEquals(Set.of(1), completedMaps());
        assertFalse(records().get(1).isFullCombo());
    }

    @Test
    void aFullComboBelowTheFirstRatingDoesNotSetTheRewardCompletionMark() throws Exception {
        start(1);
        assertEquals(0, settle(report(1, 1739, true)).getRetcode());
        assertEquals(0, musicCoins());
        assertTrue(completedMaps().isEmpty());
        assertTrue(records().get(1).isFullCombo());
    }

    @Test
    void aReportedPercentageCannotReplaceTheServerScoreRatio() throws Exception {
        start(1);
        settle(report(1, 1, false).toBuilder().setDFPIEMBCGCN(100).build());
        assertEquals(0, musicCoins());
        assertTrue(completedMaps().isEmpty());
    }

    @Test
    void aMasterClearAlsoPaysTheTwoLowerDifficultiesOfTheSameSong() throws Exception {
        start(3);
        assertEquals(0, settle(report(3, 3415, false)).getRetcode());
        assertEquals(120, musicCoins());
        assertEquals(Set.of(1, 2, 3), completedMaps());
        assertEquals(0, records().get(1).getMaximumScore());
        assertEquals(0, records().get(2).getMaximumScore());
        assertFalse(records().containsKey(9));
    }

    @Test
    void legendaryClearPaysLowerRewardsWithoutInventingALegendaryReward() throws Exception {
        start(4);
        assertEquals(0, settle(report(4, 4558, false)).getRetcode());
        assertEquals(120, musicCoins());
        assertEquals(Set.of(1, 2, 3, 4), completedMaps());
    }

    @Test
    void increasingDifficultyOnlyPaysTheRemainingRewards() throws Exception {
        for (int id = 1; id <= 3; id++) {
            start(id);
            assertEquals(0, settle(report(id, maximums.get(id), false)).getRetcode());
            assertEquals(id == 1 ? 20 : id == 2 ? 60 : 120, musicCoins());
        }
        start(2);
        settle(report(2, maximums.get(2), true));
        assertEquals(120, musicCoins());
    }

    @Test
    void allSevenGuidesHaveSeparateFirstClearRewards() throws Exception {
        for (int id = 9991; id <= 9997; id++) {
            start(id);
            assertEquals(0, settle(report(id, maximums.get(id), false)).getRetcode());
        }
        assertEquals(140, actual.getPrimogems());
        assertEquals(140, musicCoins());
        assertEquals(7, completedMaps().size());
        start(9991);
        settle(report(9991, maximums.get(9991), false));
        assertEquals(140, actual.getPrimogems());
        assertEquals(140, musicCoins());
    }

    @Test
    void repeatedRewardSettlementDoesNotChangeTheConfirmedDocument() throws Exception {
        start(3);
        var request = report(3, 4000, false);
        var result = settle(request);
        assertEquals(120, musicCoins());
        var confirmed = document();
        assertEquals(result, settle(request));
        assertEquals(confirmed, document());
        assertEquals(120, musicCoins());
    }

    @Test
    void rewardAndAllCompletionMarksSurviveAColdReadAndAnotherClear() throws Exception {
        start(3);
        settle(report(3, 4000, false));
        var cold =
                playerStore
                        .datastore()
                        .find(Player.class)
                        .filter(Filters.eq("_id", actual.getUid()))
                        .first();
        assertNotNull(cold);
        actual = cold;
        attach(cold);
        assertEquals(120, musicCoins());
        assertEquals(Set.of(1, 2, 3), completedMaps());
        var datastore =
                emu.grasscutter.database.DatabaseManager.class.getDeclaredField("gameDatastore");
        datastore.setAccessible(true);
        var previous = datastore.get(null);
        try {
            datastore.set(null, playerStore.datastore());
            book.setOwner(cold);
            cold.getInventory().getItems().put(book.getGuid(), book);
            cold.getInventory().getInventoryTab(book.getItemType()).onAddItem(book);
            assertEquals(0, start(1).getRetcode());
            assertEquals(0, settle(report(1, 2000, false)).getRetcode());
            assertEquals(120, musicCoins());
        } finally {
            assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
            datastore.set(null, previous);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void uncertainRewardWriteCanOnlyConfirmTheOriginalSingleGrant(boolean committed)
            throws Exception {
        start(9991);
        if (committed) failAfterWrite.set(true);
        else failWrite.set(true);
        var request = report(9991, 300, false);
        assertNotEquals(0, settle(request).getRetcode());
        assertEquals(
                0,
                packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.ItemAddHintNotify)
                        .count());
        if (committed) {
            var saved = document();
            assertNotNull(saved);
            assertEquals(20, saved.get("properties", Document.class).getInteger("10058"));
            assertTrue(
                    saved.get("musicGameBookRecords", Document.class)
                            .get("9991", Document.class)
                            .getBoolean("normalCompleted"));
        } else assertNull(document());
        failWrite.set(false);
        assertEquals(0, settle(request).getRetcode());
        assertEquals(20, musicCoins());
        assertEquals(20, actual.getPrimogems());
        var confirmed = document();
        assertEquals(0, settle(request).getRetcode());
        assertEquals(confirmed, document());
    }

    @Test
    void rewardOverflowIsCheckedBeforeAnyCurrencyOrRecordChanges() throws Exception {
        actual.getProperties()
                .put(
                        emu.grasscutter.game.props.PlayerProperty.PROP_PLAYER_HCOIN.getId(),
                        Integer.MAX_VALUE - 10);
        start(9991);
        assertNotEquals(0, settle(report(9991, 300, false)).getRetcode());
        assertEquals(Integer.MAX_VALUE - 10, actual.getPrimogems());
        assertEquals(0, musicCoins());
        assertTrue(snapshot().isEmpty());
    }

    @Test
    void legacyFullComboRecordDoesNotClaimAnUnpaidReward() throws Exception {
        replaceField(
                Player.class,
                actual,
                "musicGameBookRecords",
                Map.of(1, new MusicGameBookRecordData(2000, true)));
        assertTrue(completedMaps().isEmpty());
        start(1);
        settle(report(1, 1800, false));
        assertEquals(20, musicCoins());
        assertEquals(Set.of(1), completedMaps());
        assertEquals(2000, records().get(1).getMaximumScore());
    }

    @Test
    void simultaneousDuplicateClearsCannotPayTheResourceRewardsTwice() throws Exception {
        start(3);
        var request = report(3, 4000, false);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> actual.getMusicGameBook().settlePerformance(request));
            var second = pool.submit(() -> actual.getMusicGameBook().settlePerformance(request));
            assertEquals(0, first.get(15, java.util.concurrent.TimeUnit.SECONDS).getRetcode());
            assertEquals(0, second.get(15, java.util.concurrent.TimeUnit.SECONDS).getRetcode());
        } finally {
            pool.shutdownNow();
        }
        assertEquals(120, musicCoins());
    }

    @Test
    void negativeStoredCurrencyDoesNotConsumeTheFirstClearReward() throws Exception {
        actual.getProperties().put(10058, -1);
        start(1);
        assertNotEquals(0, settle(report(1, 1740, false)).getRetcode());
        assertEquals(-1, musicCoins());
        assertTrue(snapshot().isEmpty());
    }

    @Test
    void aLegacyProfileCanReceiveItsFirstMusicRewardWithoutAnExistingCurrencyProperty()
            throws Exception {
        actual.getProperties().remove(10058);
        start(1);
        assertEquals(0, settle(report(1, 1740, false)).getRetcode());
        assertEquals(20, musicCoins());
        assertEquals(Set.of(1), completedMaps());
    }

    private void copyRewardResources(Path directory) throws Exception {
        for (var name :
                List.of("Song", "Album", "Guide", "Beatmap", "BeatmapSettle", "ConstValue")) {
            var file = "MusicGameBook" + name + "ExcelConfigData.json";
            Files.copy(Path.of("resources/ExcelBinOutput", file), directory.resolve(file));
        }
        Files.copy(
                Path.of("resources/ExcelBinOutput/RewardExcelConfigData.json"),
                directory.resolve("RewardExcelConfigData.json"));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(
            strings = {"missing", "negative", "physical", "duplicate", "overflow", "extra"})
    void invalidRewardResourcesCannotPayOrConsumeTheFirstClear(
            String fault, @org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        copyRewardResources(directory);
        var rewardFile = directory.resolve("RewardExcelConfigData.json");
        var original = Files.readString(rewardFile);
        var rewards = JsonParser.parseString(original).getAsJsonArray();
        JsonObject row = null;
        int index = 0;
        for (; index < rewards.size(); index++) {
            var candidate = rewards.get(index).getAsJsonObject();
            if (candidate.get("rewardId").getAsInt() == 4120011) {
                row = candidate;
                break;
            }
        }
        assertNotNull(row);
        var item = row.getAsJsonArray("rewardItemList").get(0).getAsJsonObject();
        switch (fault) {
            case "missing" -> rewards.remove(index);
            case "negative" -> item.addProperty("itemCount", -20);
            case "physical" -> item.addProperty("itemId", 100001);
            case "duplicate" -> rewards.add(row.deepCopy());
            case "overflow" -> row.getAsJsonArray("rewardItemList")
                    .add(JsonParser.parseString("{itemId:207,itemCount:2147483647}"));
            case "extra" -> row.addProperty("hcoin", 1);
            default -> fail("Unknown resource fault");
        }
        Files.writeString(rewardFile, rewards.toString());
        assertEquals(0, start(1).getRetcode());
        replaceField(
                MusicGameBook.class,
                actual.getMusicGameBook(),
                "activeCatalog",
                MusicGameBookCatalog.load(directory));
        var request = report(1, 1740, false);
        assertNotEquals(0, settle(request).getRetcode());
        assertEquals(0, musicCoins());
        assertEquals(0, actual.getPrimogems());
        assertTrue(snapshot().isEmpty());
        assertNull(document());
        assertEquals(
                0,
                packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.ItemAddHintNotify)
                        .count());
        Files.writeString(rewardFile, original);
        assertEquals(0, settle(request).getRetcode());
        assertEquals(20, musicCoins());
        assertEquals(Set.of(1), completedMaps());
    }

    @Test
    void notificationFailureCannotUndoOrDuplicateAConfirmedMusicReward() throws Exception {
        start(9991);
        failRewardHint.set(true);
        var request = report(9991, 300, false);
        assertEquals(0, settle(request).getRetcode());
        assertEquals(20, actual.getPrimogems());
        assertEquals(20, musicCoins());
        assertEquals(Set.of(9991), completedMaps());
        var saved = document();
        assertEquals(0, settle(request).getRetcode());
        assertEquals(saved, document());
        assertEquals(20, musicCoins());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2, 3})
    void nonstandardFinishReasonDoesNotConsumeTheSinglePlayerReward(int reason) throws Exception {
        start(1);
        settle(report(1, 2000, false).toBuilder().setLKIMDBMGKEM(reason).build());
        assertEquals(0, musicCoins());
        assertTrue(completedMaps().isEmpty());
    }

    @Test
    void opaqueNoteTelemetryAndSettingsDoNotSuppressAnOrdinarySinglePlayerClear() throws Exception {
        start(1);
        var request =
                report(1, 2000, false).toBuilder()
                        .setLKIMDBMGKEM(1)
                        .addAllFMBMLBAGIIO(List.of(1, 3, 2, 1, 0, 4))
                        .setNGMCGJBAJJC(true)
                        .setDGBEKHBEHFN(true)
                        .build();
        assertEquals(0, settle(request).getRetcode());
        assertEquals(20, musicCoins());
        assertEquals(Set.of(1), completedMaps());
    }

    @Test
    void nativeFullComboUsesTheOneBitIntegerInsteadOfAnOpaqueSettingFlag() throws Exception {
        start(1);
        var request = report(1, 2000, true).toBuilder().setDGBEKHBEHFN(false).build();
        assertEquals(0, settle(request).getRetcode());
        assertTrue(records().get(1).isFullCombo());
    }

    @ParameterizedTest
    @ValueSource(ints = {2, -1})
    void nativeFullComboIntegerOnlyAcceptsZeroOrOne(int flag) throws Exception {
        start(1);
        var request = report(1, 2000, false).toBuilder().setFJDDGBILJCF(flag).build();
        assertNotEquals(0, settle(request).getRetcode());
        assertTrue(snapshot().isEmpty());
        assertEquals(0, musicCoins());
    }

    @Test
    void theFirstRatingThresholdComesFromTheResourceTable(
            @org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        copyRewardResources(directory);
        var file = directory.resolve("MusicGameBookConstValueExcelConfigData.json");
        var constants = JsonParser.parseString(Files.readString(file)).getAsJsonArray();
        for (var value : constants) {
            var row = value.getAsJsonObject();
            if (row.has("name")
                    && row.get("name").getAsString().equals("MGB_CONST_VALUE_SCORE_RATIO"))
                row.getAsJsonArray("value").set(0, new JsonPrimitive("60"));
        }
        Files.writeString(file, constants.toString());
        for (int score : new int[] {2087, 2088}) {
            start(1);
            replaceField(
                    MusicGameBook.class,
                    actual.getMusicGameBook(),
                    "activeCatalog",
                    MusicGameBookCatalog.load(directory));
            assertEquals(0, settle(report(1, score, false)).getRetcode());
            assertEquals(score == 2087 ? 0 : 20, musicCoins());
        }
    }

    @Test
    void ordinaryFirstClearReturnsTheResourceRewardIdForTheClientDisplay() throws Exception {
        start(1);
        var result = settle(report(1, 1740, false));
        assertEquals(0, result.getRetcode());
        assertEquals(List.of(4120011), result.getOFKFKNJHDIOList());
        assertEquals(20, musicCoins());
        assertTrue(
                document()
                        .get("musicGameBookRecords", Document.class)
                        .get("1", Document.class)
                        .getBoolean("normalCompleted"));
    }

    @Test
    void aLegendaryFirstClearListsEveryActuallyPaidLowerDifficultyReward() throws Exception {
        start(4);
        var result = settle(report(4, maximums.get(4), false));
        assertEquals(0, result.getRetcode());
        assertEquals(List.of(4120011, 4120012, 4120013), result.getOFKFKNJHDIOList());
        assertEquals(120, musicCoins());
    }

    @Test
    void increasingDifficultyDisplaysOnlyTheStillUnpaidRewardIds() throws Exception {
        for (int id = 1; id <= 3; id++) {
            start(id);
            var result = settle(report(id, maximums.get(id), false));
            assertEquals(0, result.getRetcode());
            assertEquals(List.of(4120010 + id), result.getOFKFKNJHDIOList());
        }
        start(4);
        var repeated = settle(report(4, maximums.get(4), false));
        assertEquals(0, repeated.getRetcode());
        assertTrue(repeated.getOFKFKNJHDIOList().isEmpty());
        assertEquals(120, musicCoins());
    }

    @Test
    void guideRewardIdsDescribeBothDeliveredCurrencies() throws Exception {
        start(9991);
        var result = settle(report(9991, 300, false));
        assertEquals(0, result.getRetcode());
        assertEquals(List.of(4120001), result.getOFKFKNJHDIOList());
        assertEquals(20, actual.getPrimogems());
        assertEquals(20, musicCoins());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2, 3})
    void nonstandardSettlementsDoNotAdvertiseAnOrdinaryReward(int reason) throws Exception {
        start(1);
        var result = settle(report(1, 2000, false).toBuilder().setLKIMDBMGKEM(reason).build());
        assertEquals(0, result.getRetcode());
        assertTrue(result.getOFKFKNJHDIOList().isEmpty());
        assertEquals(0, musicCoins());
    }

    @Test
    void subThresholdAndInterruptedPerformancesHaveNoRewardDisplay() throws Exception {
        start(1);
        var subThreshold = settle(report(1, 1739, false));
        assertEquals(0, subThreshold.getRetcode());
        assertTrue(subThreshold.getOFKFKNJHDIOList().isEmpty());
        start(1);
        var interrupted = settle(report(1, 2000, false).toBuilder().setFNCOKHKCDDG(false).build());
        assertEquals(0, interrupted.getRetcode());
        assertTrue(interrupted.getOFKFKNJHDIOList().isEmpty());
        assertEquals(0, musicCoins());
    }

    @Test
    void anIdenticalSettlementReplaysTheConfirmedDisplayWithoutAnotherGrant() throws Exception {
        start(3);
        var request = report(3, 4000, false);
        var first = settle(request);
        assertEquals(List.of(4120011, 4120012, 4120013), first.getOFKFKNJHDIOList());
        var before = document();
        assertEquals(first, settle(request));
        assertEquals(before, document());
        assertEquals(120, musicCoins());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void uncertainWritesReturnRewardIdsOnlyAfterTheOriginalGrantIsConfirmed(boolean committed)
            throws Exception {
        start(9991);
        if (committed) failAfterWrite.set(true);
        else failWrite.set(true);
        var request = report(9991, 300, false);
        var failed = settle(request);
        assertNotEquals(0, failed.getRetcode());
        assertTrue(
                failed.getOFKFKNJHDIOList().isEmpty(),
                "An error cannot advertise an unconfirmed reward");
        failWrite.set(false);
        var confirmed = settle(request);
        assertEquals(0, confirmed.getRetcode());
        assertEquals(List.of(4120001), confirmed.getOFKFKNJHDIOList());
        var before = document();
        assertEquals(confirmed, settle(request));
        assertEquals(before, document());
        assertEquals(20, actual.getPrimogems());
        assertEquals(20, musicCoins());
    }

    @Test
    void aLostResponseReplaysItsConfirmedRewardIdsWithoutPayingAgain() throws Exception {
        start(3);
        var request = report(3, 4000, false);
        packets.clear();
        failSettleReply.set(true);
        dispatcher.handle(
                actual.getSession(),
                PacketOpcodes.MusicGameBookSettleReq,
                new byte[0],
                request.toByteArray());
        assertTrue(
                packets.stream()
                        .noneMatch(p -> p.getOpcode() == PacketOpcodes.MusicGameBookSettleRsp));
        var before = document();
        var replay = settle(request);
        assertEquals(0, replay.getRetcode());
        assertEquals(List.of(4120011, 4120012, 4120013), replay.getOFKFKNJHDIOList());
        assertEquals(before, document());
        assertEquals(120, musicCoins());
    }

    @Test
    void aLostCurrencyHintDoesNotRemoveTheConfirmedRewardDisplay() throws Exception {
        start(9991);
        failRewardHint.set(true);
        var request = report(9991, 300, false);
        var result = settle(request);
        assertEquals(0, result.getRetcode());
        assertEquals(List.of(4120001), result.getOFKFKNJHDIOList());
        var before = document();
        assertEquals(result, settle(request));
        assertEquals(before, document());
        assertEquals(20, musicCoins());
    }

    @Test
    void reusedRewardDefinitionsStayRepeatedWhenSeparateFirstClearsEachPayThem(
            @org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        copyRewardResources(directory);
        var file = directory.resolve("MusicGameBookBeatmapExcelConfigData.json");
        var rows = JsonParser.parseString(Files.readString(file)).getAsJsonArray();
        for (var value : rows) {
            var row = value.getAsJsonObject();
            if (row.get("id").getAsInt() == 2)
                row.add("rewardIdList", JsonParser.parseString("[4120011]"));
        }
        Files.writeString(file, rows.toString());
        start(2);
        replaceField(
                MusicGameBook.class,
                actual.getMusicGameBook(),
                "activeCatalog",
                MusicGameBookCatalog.load(directory));
        var result = settle(report(2, maximums.get(2), false));
        assertEquals(0, result.getRetcode());
        assertEquals(List.of(4120011, 4120011), result.getOFKFKNJHDIOList());
        assertEquals(40, musicCoins());
    }
}
