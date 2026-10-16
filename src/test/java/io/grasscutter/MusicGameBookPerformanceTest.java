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
                .setDGBEKHBEHFN(fullCombo)
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
        assertEquals(1, saved.size());
        assertEquals(id, saved.get(0).getIHPMDKPGJLB());
        assertEquals(maximums.get(id).intValue(), saved.get(0).getMCPDKDGGBDD());
        assertTrue(saved.get(0).getNFHCCJALOAJ());
        assertEquals(
                0,
                saved.get(0).getJNJDOLEBBOOCount(),
                "Reward claims require their own verified workflow");
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
}
