package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Transient;
import dev.morphia.query.experimental.filters.Filters;

import emu.grasscutter.data.*;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.data.common.PointData;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.inventory.Inventory;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.quest.enums.QuestExec;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GetScenePointRspOuterClass.GetScenePointRsp;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.ScenePointUnlockNotifyOuterClass.ScenePointUnlockNotify;
import emu.grasscutter.net.proto.SceneTransToPointReqOuterClass.SceneTransToPointReq;
import emu.grasscutter.net.proto.SceneTransToPointRspOuterClass.SceneTransToPointRsp;
import emu.grasscutter.net.proto.UnlockTransPointReqOuterClass.UnlockTransPointReq;
import emu.grasscutter.scripts.ScriptLoader;
import emu.grasscutter.server.event.player.PlayerTeleportEvent.TeleportType;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.recv.*;
import emu.grasscutter.server.packet.send.PacketGetScenePointRsp;

import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Native point data, quest dispatch, point packets and actual player-model Mongo round trips. */
class QuestPointLockTest extends QuestTestFixture {
    private Map<Integer, ScenePointEntry> oldPoints;
    private List<Integer> oldIds;
    private Map<Integer, List<Integer>> oldScenes;
    private RecordingInventory inventory;

    @Entity(value = "players", useDiscriminator = false)
    static class SavedPointPlayer extends Player {
        @Transient final List<BasePacket> packets = new CopyOnWriteArrayList<>();
        @Transient final TeamManager team = new TeamManager(this);
        @Transient Consumer<BasePacket> beforeSend = packet -> {};

        public SavedPointPlayer() {
            super();
        }

        @Override
        public TeamManager getTeamManager() {
            return team;
        }

        @Override
        public void sendPacket(BasePacket packet) {
            beforeSend.accept(packet);
            packets.add(packet);
        }
    }

    private static class RecordingInventory extends Inventory {
        final List<String> rewards = new ArrayList<>();

        RecordingInventory(Player player) {
            super(player);
        }

        @Override
        public boolean addItem(int item, int count, ActionReason reason) {
            rewards.add(item + ":" + count);
            return true;
        }
    }

    @BeforeAll
    static void lua() throws Exception {
        if (ScriptLoader.getEngine() == null) ScriptLoader.init();
    }

    @BeforeEach
    void points() throws Exception {
        oldPoints = new HashMap<>(GameData.getScenePointEntryMap());
        oldIds = new ArrayList<>(GameData.getScenePointIdList());
        oldScenes = new HashMap<>(GameData.getScenePointsPerScene());
        GameData.getScenePointEntryMap().clear();
        GameData.getScenePointIdList().clear();
        GameData.getScenePointsPerScene().clear();
        var json =
                com.google.gson.JsonParser.parseString(
                                Files.readString(
                                        Path.of(
                                                "resources/BinOutput/Scene/Point/scene3_point.json")))
                        .getAsJsonObject()
                        .getAsJsonObject("points");
        for (int id : new int[] {7, 31, 32, 1720}) {
            var data = gson.fromJson(json.get(Integer.toString(id)), PointData.class);
            assertNotNull(data);
            data.setId(id);
            GameData.getScenePointEntryMap().put((3 << 16) + id, new ScenePointEntry(3, data));
            GameData.getScenePointIdList().add(id);
        }
        GameData.getScenePointsPerScene().put(3, List.of(7, 31, 32, 1720));
        inventory = new RecordingInventory(player);
        replaceField(Player.class, player, "inventory", inventory);
        for (var row :
                gson.fromJson(
                        Files.readString(
                                Path.of("resources/ExcelBinOutput/QuestExcelConfigData.json")),
                        QuestData[].class)) {
            if (row.getMainId() == 376) {
                row.onLoad();
                GameData.getQuestDataMap().put(row.getId(), row);
            }
        }
        var main =
                gson.fromJson(
                        Files.readString(Path.of("resources/BinOutput/Quest/376.json")),
                        MainQuestData.class);
        main.onLoad();
        GameData.getMainQuestDataMap().put(376, main);
        GameData.getBeginCondQuestMap().clear();
        player.packets.clear();
    }

    @AfterEach
    void restorePoints() throws Exception {
        drain();
        GameData.getScenePointEntryMap().clear();
        GameData.getScenePointEntryMap().putAll(oldPoints);
        GameData.getScenePointIdList().clear();
        GameData.getScenePointIdList().addAll(oldIds);
        GameData.getScenePointsPerScene().clear();
        GameData.getScenePointsPerScene().putAll(oldScenes);
    }

    private boolean lock(Player target, int scene, int point) throws Exception {
        return (boolean)
                PlayerProgressManager.class
                        .getMethod("lockTransPoint", int.class, int.class)
                        .invoke(target.getProgressManager(), scene, point);
    }

    private boolean forced(Player target, int scene, int point) throws Exception {
        return (boolean)
                Player.class
                        .getMethod("isScenePointForceLocked", int.class, int.class)
                        .invoke(target, scene, point);
    }

    private GetScenePointRsp points(Player target, int scene) throws Exception {
        return GetScenePointRsp.parseFrom(new PacketGetScenePointRsp(target, scene).getData());
    }

    private ScenePointUnlockNotify lastNotify(List<BasePacket> packets) throws Exception {
        var list =
                packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.ScenePointUnlockNotify)
                        .toList();
        assertFalse(list.isEmpty());
        return ScenePointUnlockNotify.parseFrom(list.get(list.size() - 1).getData());
    }

    private emu.grasscutter.game.quest.handlers.QuestExecHandler handler() throws Exception {
        var member = QuestSystem.class.getDeclaredField("execHandlers");
        member.setAccessible(true);
        var map =
                (it.unimi.dsi.fastutil.ints.Int2ObjectMap<?>)
                        member.get(player.server.getQuestSystem());
        var value = (emu.grasscutter.game.quest.handlers.QuestExecHandler) map.get(17);
        assertNotNull(value, "LOCK_POINT type 17 has no registered handler");
        return value;
    }

    @Test
    void native37601LockExecsUseTheirResourceParametersAndRealQuestDispatch() throws Exception {
        var quest = active(37601);
        var actions =
                quest.getQuestData().getFinishExec().stream()
                        .filter(e -> e.getType() == QuestExec.QUEST_EXEC_LOCK_POINT)
                        .toList();
        assertEquals(2, actions.size());
        assertArrayEquals(new String[] {"3", "31"}, actions.get(0).getParam());
        assertArrayEquals(new String[] {"3", "32"}, actions.get(1).getParam());
        assertEquals(
                QuestExec.QUEST_EXEC_LOCK_POINT,
                handler().getClass().getAnnotation(QuestValueExec.class).value());
        player.getUnlockedScenePoints(3).addAll(List.of(31, 32));
        for (var action : actions)
            player.server.getQuestSystem().triggerExec(quest, action, action.getParam());
        drain();
        assertTrue(forced(player, 3, 31));
        assertTrue(forced(player, 3, 32));
        assertTrue(player.getUnlockedScenePoints(3).containsAll(List.of(31, 32)));
        assertEquals(List.of(32), lastNotify(player.packets).getLockedPointListList());
        assertFalse(points(player, 3).getUnlockedPointListList().contains(31));
        assertFalse(points(player, 3).getUnlockedPointListList().contains(32));
        assertTrue(inventory.rewards.isEmpty());
    }

    @Test
    void lockNotificationUsesNativeFieldFourAndDoesNotUnlockTheSamePoint() throws Exception {
        assertTrue(lock(player, 3, 31));
        var notify = lastNotify(player.packets);
        assertEquals(3, notify.getSceneId());
        assertEquals(List.of(31), notify.getLockedPointListList());
        assertEquals(List.of(31), notify.getHidePointListList());
        assertTrue(notify.getPointListList().isEmpty());
        assertTrue(notify.getUnhidePointListList().isEmpty());
        assertArrayEquals(new byte[] {0x10, 3, 0x22, 1, 31, 0x32, 1, 31}, notify.toByteArray());
    }

    @Test
    void temporaryLockPreservesPermanentDiscoveryAndReunlockNeverRepeatsRewards() throws Exception {
        player.getUnlockedScenePoints(3).add(31);
        assertTrue(lock(player, 3, 31));
        assertTrue(forced(player, 3, 31));
        assertTrue(player.getUnlockedScenePoints(3).contains(31));
        assertTrue(player.getProgressManager().unlockTransPoint(3, 31, false));
        drain();
        assertFalse(forced(player, 3, 31));
        assertTrue(inventory.rewards.isEmpty());
        var notify = lastNotify(player.packets);
        assertEquals(List.of(31), notify.getPointListList());
        assertTrue(notify.getLockedPointListList().isEmpty());
        assertEquals(List.of(31), notify.getUnhidePointListList());
        assertArrayEquals(new byte[] {0x10, 3, 0x42, 1, 31, 0x7a, 1, 31}, notify.toByteArray());
        assertFalse(player.getProgressManager().unlockTransPoint(3, 31, false));
        assertTrue(inventory.rewards.isEmpty());
    }

    @Test
    void firstDiscoveryAfterAQuestLockUsesExistingRewardsExactlyOnce() throws Exception {
        assertTrue(lock(player, 3, 31));
        assertTrue(player.getProgressManager().unlockTransPoint(3, 31, false));
        drain();
        assertEquals(List.of("201:5", "102:10"), inventory.rewards);
        assertTrue(lock(player, 3, 31));
        assertTrue(player.getProgressManager().unlockTransPoint(3, 31, false));
        drain();
        assertEquals(List.of("201:5", "102:10"), inventory.rewards);
    }

    @Test
    void repeatedLockIsIdempotentAndDifferentSceneRemainsIndependent() throws Exception {
        assertTrue(lock(player, 3, 31));
        assertTrue(lock(player, 3, 31));
        assertTrue(forced(player, 3, 31));
        assertFalse(forced(player, 4, 31));
        assertFalse(forced(player, 3, 32));
        assertTrue(inventory.rewards.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0,31", "3,0", "-1,31", "3,-1", "3,9999", "65539,31"})
    void invalidOrMismatchedPointDataCannotCreateLockState(String pair) throws Exception {
        var values = pair.split(",");
        assertFalse(lock(player, Integer.parseInt(values[0]), Integer.parseInt(values[1])));
        assertTrue(player.packets.isEmpty());
        assertFalse(forced(player, 3, 31));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "bad", "-1", "2147483648"})
    void invalidQuestArgumentsReturnFailureWithoutMutations(String value) throws Exception {
        var quest = active(1100509);
        var handler = handler();
        assertFalse(handler.execute(quest, null, value, "31"));
        assertFalse(handler.execute(quest, null, "3", value));
        assertTrue(player.packets.isEmpty());
    }

    @Test
    void absentQuestAndArgumentsAreRejected() throws Exception {
        var quest = active(1100509);
        var handler = handler();
        assertFalse(handler.execute(null, null, "3", "31"));
        assertFalse(handler.execute(quest, null, (String[]) null));
        assertFalse(handler.execute(quest, null, "3"));
        assertFalse(handler.execute(quest, null, "3", (String) null));
    }

    @Test
    void pointResponseUsesOnlyDiscoveredPointsFromItsSceneAndActualAreas() throws Exception {
        player.getUnlockedScenePoints(3).add(31);
        player.getUnlockedScenePoints(3).add(9999);
        player.getUnlockedSceneAreas(3).add(2);
        var response = points(player, 3);
        assertEquals(player.getUid(), response.getBelongUid());
        assertEquals(List.of(31), response.getUnlockedPointListList());
        assertEquals(List.of(2), response.getUnlockAreaListList());
        assertTrue(
                response.getUnhidePointListList().isEmpty(),
                "Unknown placeholder fields must not be populated");
        assertTrue(points(player, 4).getUnlockedPointListList().isEmpty());
    }

    @Test
    void emptyDiscoveryOrMissingCatalogNeverFabricatesOneThousandUnlocks() throws Exception {
        assertTrue(points(player, 3).getUnlockedPointListList().isEmpty());
        GameData.getScenePointEntryMap().clear();
        GameData.getScenePointsPerScene().clear();
        GameData.getScenePointIdList().clear();
        assertTrue(points(player, 3).getUnlockedPointListList().isEmpty());
    }

    private SavedPointPlayer model(int uid) throws Exception {
        store.getMapper().map(SavedPointPlayer.class);
        var model = new SavedPointPlayer();
        model.setUid(uid);
        model.setSession(player.getSession());
        model.setScene(player.getScene());
        model.setWorld(player.getWorld());
        return model;
    }

    private SavedPointPlayer cold(int uid) throws Exception {
        drain();
        var restored = store.find(SavedPointPlayer.class).filter(Filters.eq("id", uid)).first();
        assertNotNull(restored);
        return restored;
    }

    @Test
    void lockAndUnlockAreSavedThroughRealPlayerSaveAndSurviveColdReads() throws Exception {
        var model = model(20001);
        model.getUnlockedScenePoints(3).add(31);
        assertTrue(lock(model, 3, 31));
        var restored = cold(model.getUid());
        assertTrue(forced(restored, 3, 31));
        assertTrue(restored.getUnlockedScenePoints(3).contains(31));
        assertFalse(points(restored, 3).getUnlockedPointListList().contains(31));
        restored.setSession(player.getSession());
        restored.setScene(player.getScene());
        restored.setWorld(player.getWorld());
        var rewards = new RecordingInventory(restored);
        replaceField(Player.class, restored, "inventory", rewards);
        assertTrue(restored.getProgressManager().unlockTransPoint(3, 31, false));
        var released = cold(model.getUid());
        assertFalse(forced(released, 3, 31));
        assertTrue(points(released, 3).getUnlockedPointListList().contains(31));
        assertTrue(rewards.rewards.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void olderPlayersWithMissingOrNullLockMapsCanBeReadAndSaved(boolean explicitNull)
            throws Exception {
        var model = model(20002);
        model.save();
        drain();
        store.getDatabase()
                .getCollection("players")
                .updateOne(
                        new Document("_id", model.getUid()),
                        new Document(
                                explicitNull ? "$set" : "$unset",
                                new Document("forceLockedScenePoints", explicitNull ? null : "")));
        var restored = cold(model.getUid());
        assertFalse(forced(restored, 3, 31));
        assertTrue(lock(restored, 3, 31));
        assertTrue(forced(cold(model.getUid()), 3, 31));
    }

    private class TrackingWorld extends World {
        int transfers;

        TrackingWorld() {
            super(player.server, player);
        }

        @Override
        public boolean transferPlayerToScene(
                Player owner, int scene, TeleportType type, Position pos) {
            transfers++;
            return true;
        }
    }

    private GameSession session() {
        return new GameSession(player.server) {
            @Override
            public Player getPlayer() {
                return player;
            }

            @Override
            public void send(BasePacket packet) {
                player.packets.add(packet);
            }
        };
    }

    @Test
    void forcedLockRejectsClientWaypointTransfersBeforeWorldMutation() throws Exception {
        var world = new TrackingWorld();
        player.setWorld(world);
        player.getUnlockedScenePoints(3).add(31);
        assertTrue(lock(player, 3, 31));
        player.packets.clear();
        new HandlerSceneTransToPointReq()
                .handle(
                        session(),
                        new byte[0],
                        SceneTransToPointReq.newBuilder()
                                .setSceneId(3)
                                .setPointId(31)
                                .build()
                                .toByteArray());
        assertEquals(0, world.transfers);
        var packet =
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.SceneTransToPointRsp)
                        .findFirst()
                        .orElseThrow();
        var response = SceneTransToPointRsp.parseFrom(packet.getData());
        assertEquals(Retcode.RET_POINT_NOT_UNLOCKED.getNumber(), response.getRetcode());
        assertEquals(3, response.getSceneId());
        assertEquals(31, response.getPointId());
    }

    @Test
    void clientActivationCannotReleaseAQuestLock() throws Exception {
        player.getUnlockedScenePoints(3).add(31);
        assertTrue(lock(player, 3, 31));
        player.packets.clear();
        new HandlerUnlockTransPointReq()
                .handle(
                        session(),
                        new byte[0],
                        UnlockTransPointReq.newBuilder()
                                .setSceneId(3)
                                .setPointId(31)
                                .build()
                                .toByteArray());
        assertTrue(forced(player, 3, 31));
        assertTrue(inventory.rewards.isEmpty());
        assertTrue(player.packets.isEmpty());
    }

    @Test
    void releasedPermanentPointCanTransferWithTheExistingWaypointHandler() throws Exception {
        var world = new TrackingWorld();
        player.setWorld(world);
        player.getUnlockedScenePoints(3).add(31);
        assertTrue(lock(player, 3, 31));
        assertTrue(player.getProgressManager().unlockTransPoint(3, 31, false));
        drain();
        player.packets.clear();
        new HandlerSceneTransToPointReq()
                .handle(
                        session(),
                        new byte[0],
                        SceneTransToPointReq.newBuilder()
                                .setSceneId(3)
                                .setPointId(31)
                                .build()
                                .toByteArray());
        assertEquals(1, world.transfers);
        var packet =
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.SceneTransToPointRsp)
                        .findFirst()
                        .orElseThrow();
        assertEquals(0, SceneTransToPointRsp.parseFrom(packet.getData()).getRetcode());
    }

    @Test
    void concurrentUnlockCannotPublishBeforeAnEarlierLockNotification() throws Exception {
        var model = model(20003);
        model.getUnlockedScenePoints(3).add(31);
        var sendingLock = new CountDownLatch(1);
        var releaseLock = new CountDownLatch(1);
        var unlockStarted = new CountDownLatch(1);
        var unlockThread = new AtomicReference<Thread>();
        var lockThread = new AtomicReference<Thread>();
        model.beforeSend =
                packet -> {
                    if (packet.getOpcode() != PacketOpcodes.ScenePointUnlockNotify) return;
                    try {
                        var notify = ScenePointUnlockNotify.parseFrom(packet.getData());
                        if (!notify.getLockedPointListList().isEmpty()) {
                            lockThread.set(Thread.currentThread());
                            sendingLock.countDown();
                            assertTrue(releaseLock.await(5, TimeUnit.SECONDS));
                        }
                    } catch (Exception error) {
                        throw new AssertionError(error);
                    }
                };
        var pool = Executors.newFixedThreadPool(2);
        try {
            var locked = pool.submit(() -> lock(model, 3, 31));
            assertTrue(sendingLock.await(5, TimeUnit.SECONDS));
            var unlocked =
                    pool.submit(
                            () -> {
                                unlockThread.set(Thread.currentThread());
                                unlockStarted.countDown();
                                return model.getProgressManager().unlockTransPoint(3, 31, false);
                            });
            assertTrue(unlockStarted.await(5, TimeUnit.SECONDS));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            var threadInfo = java.lang.management.ManagementFactory.getThreadMXBean();
            boolean blockedByLockSender = false;
            while (!unlocked.isDone() && System.nanoTime() < deadline) {
                var info = threadInfo.getThreadInfo(unlockThread.get().getId());
                if (info != null && info.getLockOwnerId() == lockThread.get().getId()) {
                    blockedByLockSender = true;
                    break;
                }
                Thread.sleep(5);
            }
            assertTrue(unlocked.isDone() || blockedByLockSender);
            releaseLock.countDown();
            assertTrue(locked.get(5, TimeUnit.SECONDS));
            assertTrue(unlocked.get(5, TimeUnit.SECONDS));
            var notifications =
                    model.packets.stream()
                            .filter(p -> p.getOpcode() == PacketOpcodes.ScenePointUnlockNotify)
                            .toList();
            assertEquals(2, notifications.size());
            assertEquals(
                    List.of(31),
                    ScenePointUnlockNotify.parseFrom(notifications.get(0).getData())
                            .getLockedPointListList());
            assertEquals(
                    List.of(31),
                    ScenePointUnlockNotify.parseFrom(notifications.get(1).getData())
                            .getPointListList());
            assertFalse(forced(model, 3, 31));
        } finally {
            releaseLock.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
            drain();
        }
    }
}
