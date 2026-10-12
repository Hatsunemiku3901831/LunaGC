package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.dungeon.*;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.game.dungeons.*;
import emu.grasscutter.game.entity.EntityTeam;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.plugin.PluginManager;
import emu.grasscutter.server.event.EventHandler;
import emu.grasscutter.server.event.player.PlayerTeleportEvent;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.recv.HandlerDungeonRestartReq;
import emu.grasscutter.utils.FileUtils;

import it.unimi.dsi.fastutil.ints.IntSet;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Real World/Scene transfers and a real cancellable event; claim state is controlled here. */
class DungeonRestartSafetyTest extends QuestTestFixture {
    private Map<Integer, SceneData> oldScenes;
    private Map<Integer, DungeonData> oldDungeons;
    private Map<Integer, DungeonPassConfigData> oldPass;
    private World world;
    private Scene source;
    private DungeonManager manager;
    private FixtureDungeonData data;
    private DungeonSystem system;
    private PluginManager plugins;

    private class RestartWorld extends World {
        volatile Thread delayedJoin;
        final CountDownLatch selected = new CountDownLatch(1);
        final CountDownLatch proceed = new CountDownLatch(1);

        RestartWorld() {
            super(player.server, player);
        }

        @Override
        public Scene getSceneById(int id) {
            var scene = super.getSceneById(id);
            if (Thread.currentThread() == delayedJoin) {
                selected.countDown();
                try {
                    assertTrue(proceed.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(error);
                }
            }
            return scene;
        }
    }

    static class FixtureDungeonData extends DungeonData {
        boolean throwPosition;

        @Override
        public Position getStartPosition() {
            if (throwPosition) throw new IllegalStateException("Controlled birth position failure");
            return new Position(1, 2, 3);
        }

        @Override
        public Position getStartRotation() {
            return new Position(0, 90, 0);
        }
    }

    @BeforeEach
    void setupDungeon() throws Exception {
        oldScenes = new HashMap<>(GameData.getSceneDataMap());
        oldDungeons = new HashMap<>(GameData.getDungeonDataMap());
        oldPass = new HashMap<>(GameData.getDungeonPassConfigDataMap());
        GameData.getSceneDataMap()
                .put(9, gson.fromJson("{id:9,type:'SCENE_DUNGEON'}", SceneData.class));
        data =
                gson.fromJson(
                        "{id:9001,sceneId:9,subType:'DUNGEON_SUB_WEAPON',passCond:9001}",
                        FixtureDungeonData.class);
        GameData.getDungeonDataMap().put(9001, data);
        GameData.getDungeonPassConfigDataMap()
                .put(
                        9001,
                        gson.fromJson(
                                "{id:9001,logicType:'LOGIC_AND',conds:[{}]}",
                                DungeonPassConfigData.class));
        Grasscutter.getConfig().server.game.enableScriptInBigWorld = false;
        world = new RestartWorld();
        world.getPlayers().add(player);
        player.setWorld(world);
        player.getTeamManager().setEntity(new EntityTeam(player));
        source = world.getSceneById(9);
        source.addPlayer(player);
        source.setPrevScene(4);
        source.setPrevScenePoint(32);
        manager = new DungeonManager(source, data);
        manager.getFinishedConditions()[0] = 1;
        source.setDungeonManager(manager);
        claimed(true);
        system = new DungeonSystem(player.server);
        replaceField(
                emu.grasscutter.server.game.GameServer.class,
                player.server,
                "dungeonSystem",
                system);
        var pluginPath = FileUtils.getPluginPath("");
        if (Files.isDirectory(pluginPath)) {
            try (var paths = Files.list(pluginPath)) {
                assertTrue(paths.noneMatch(path -> path.toString().endsWith(".jar")));
            }
        }
        plugins = new PluginManager();
        replaceField(Grasscutter.class, null, "pluginManager", plugins);
        player.packets.clear();
    }

    @AfterEach
    void restoreDungeon() throws Exception {
        for (var scene : List.copyOf(world.getScenes().values()))
            scene.getScriptManager().onDestroy();
        source.getScriptManager().onDestroy();
        GameData.getSceneDataMap().clear();
        GameData.getSceneDataMap().putAll(oldScenes);
        GameData.getDungeonDataMap().clear();
        GameData.getDungeonDataMap().putAll(oldDungeons);
        GameData.getDungeonPassConfigDataMap().clear();
        GameData.getDungeonPassConfigDataMap().putAll(oldPass);
    }

    private void claimed(boolean value) throws Exception {
        var field = DungeonManager.class.getDeclaredField("rewardedPlayers");
        field.setAccessible(true);
        var recipients = (IntSet) field.get(manager);
        if (value) recipients.add(player.getUid());
        else recipients.remove(player.getUid());
    }

    private GameSession session(Player actor) {
        return new GameSession(player.server) {
            @Override
            public Player getPlayer() {
                return actor;
            }

            @Override
            public void send(BasePacket packet) {
                player.packets.add(packet);
            }
        };
    }

    private void request(byte[] payload) throws Exception {
        new HandlerDungeonRestartReq().handle(session(player), new byte[0], payload);
    }

    private void kept() {
        assertSame(source, player.getScene());
        assertSame(source, world.getScenes().get(9));
        assertTrue(source.getPlayers().contains(player));
        assertFalse(source.getScriptManager().isDestroyed());
        assertSame(manager, source.getDungeonManager());
        assertEquals(4, source.getPrevScene());
        assertEquals(32, source.getPrevScenePoint());
    }

    private long replies() {
        return player.packets.stream()
                .filter(packet -> packet.getOpcode() == PacketOpcodes.DungeonRestartRsp)
                .count();
    }

    private void teleportListener(
            emu.grasscutter.utils.objects.EventConsumer<PlayerTeleportEvent> listener) {
        new EventHandler<>(PlayerTeleportEvent.class).listener(listener).register(null);
    }

    @Test
    void canceledTransferPreservesTheActiveDungeon() {
        teleportListener(PlayerTeleportEvent::cancel);
        system.restartDungeon(player);
        kept();
    }

    @Test
    void missingDestinationResourceDoesNotDetachThePlayer() {
        GameData.getSceneDataMap().remove(9);
        system.restartDungeon(player);
        kept();
    }

    @Test
    void missingPassConfigurationFailsBeforeDestroyingTheCurrentScene() {
        GameData.getDungeonPassConfigDataMap().remove(9001);
        assertThrows(NullPointerException.class, () -> system.restartDungeon(player));
        kept();
    }

    @Test
    void thrownBirthPositionLookupDoesNotDestroyTheCurrentScene() {
        data.throwPosition = true;
        assertThrows(IllegalStateException.class, () -> system.restartDungeon(player));
        kept();
    }

    @Test
    void successfulRestartRebuildsSceneManagerAndPreservesTheExit() {
        system.restartDungeon(player);
        var fresh = player.getScene();
        assertNotSame(source, fresh);
        assertSame(fresh, world.getScenes().get(9));
        assertTrue(source.getScriptManager().isDestroyed());
        assertTrue(source.getPlayers().isEmpty());
        assertEquals(List.of(player), fresh.getPlayers());
        assertNotSame(manager, fresh.getDungeonManager());
        assertFalse(fresh.getDungeonManager().isFinishedSuccessfully());
        assertEquals(4, fresh.getPrevScene());
        assertEquals(32, fresh.getPrevScenePoint());
        assertEquals(1, fresh.getDungeonSettleListeners().size());
        assertTrue(new Position(1, 2, 3).equal3d(player.getPosition()));
    }

    @Test
    void legacyTowerReplayKeepsItsTowerManagerAndSettleObserver() {
        manager.setTowerDungeon(true);
        system.restartDungeon(player);
        var fresh = player.getScene();
        assertNotSame(source, fresh);
        assertTrue(fresh.getDungeonManager().isTowerDungeon());
        assertEquals(1, fresh.getDungeonSettleListeners().size());
        assertInstanceOf(
                TowerDungeonSettleListener.class, fresh.getDungeonSettleListeners().get(0));
    }

    @Test
    void multipleSceneParticipantsAreLeftIntact() {
        var guest = new Player();
        guest.setUid(10002);
        source.getPlayers().add(guest);
        world.getPlayers().add(guest);
        system.restartDungeon(player);
        kept();
        assertTrue(source.getPlayers().contains(guest));
    }

    @Test
    void anotherWorldMemberAlsoPreventsARebuild() {
        var guest = new Player();
        guest.setUid(10002);
        world.getPlayers().add(guest);
        system.restartDungeon(player);
        kept();
    }

    @Test
    void sceneResourceChangedToWorldIsNotRebuiltAsADungeon() {
        GameData.getSceneDataMap()
                .put(9, gson.fromJson("{id:9,type:'SCENE_WORLD'}", SceneData.class));
        system.restartDungeon(player);
        kept();
    }

    @Test
    void sceneMembershipChangedDuringTeleportEventIsRechecked() {
        teleportListener(
                event -> {
                    var guest = new Player();
                    guest.setUid(10002);
                    source.getPlayers().add(guest);
                    world.getPlayers().add(guest);
                });
        system.restartDungeon(player);
        kept();
        assertEquals(2, source.getPlayers().size());
    }

    @Test
    void aManagerRemovedDuringTeleportDoesNotCrashOrDestroyTheSource() {
        teleportListener(
                event -> {
                    source.setDungeonManager(null);
                    manager = null;
                });
        assertDoesNotThrow(() -> system.restartDungeon(player));
        kept();
    }

    @Test
    void aDifferentDungeonInstalledDuringTeleportCannotReuseTheOldRequest() {
        teleportListener(
                event -> {
                    var replacement =
                            gson.fromJson(
                                    "{id:9002,sceneId:9,subType:'DUNGEON_SUB_WEAPON',passCond:9001}",
                                    FixtureDungeonData.class);
                    GameData.getDungeonDataMap().put(9002, replacement);
                    manager = new DungeonManager(source, replacement);
                    manager.getFinishedConditions()[0] = 1;
                    source.setDungeonManager(manager);
                });
        system.restartDungeon(player);
        kept();
    }

    @Test
    void aNewManagerForTheSameDataCannotBeOverwrittenByAnOlderRestart() {
        teleportListener(
                event -> {
                    manager = new DungeonManager(source, data);
                    manager.setTowerDungeon(true);
                    source.setDungeonManager(manager);
                });
        system.restartDungeon(player);
        kept();
    }

    @Test
    void aJoinThatSelectedTheRetiredSceneIsRejectedBeforeChangingMembership() throws Exception {
        var joining = new TestPlayer();
        joining.setUid(10002);
        joining.server = player.server;
        joining.setSceneId(9);
        var delayed = (RestartWorld) world;
        var executor = Executors.newSingleThreadExecutor();
        try {
            var entry =
                    executor.submit(
                            () -> {
                                delayed.delayedJoin = Thread.currentThread();
                                return world.tryAddPlayer(joining);
                            });
            assertTrue(delayed.selected.await(5, TimeUnit.SECONDS));
            system.restartDungeon(player);
            var fresh = player.getScene();
            assertNotSame(source, fresh);
            delayed.proceed.countDown();
            assertFalse(entry.get(5, TimeUnit.SECONDS));
            assertNull(joining.getWorld());
            assertEquals(List.of(player), world.getPlayers());
            assertEquals(List.of(player), fresh.getPlayers());
            assertTrue(source.getPlayers().isEmpty());
        } finally {
            delayed.proceed.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "unfinished",
                "unclaimed",
                "tower",
                "boss",
                "plot",
                "payload",
                "missing-manager",
                "outsider"
            })
    void clientRetryRequiresAnEligibleCompletedAndClaimedSoloDomain(String rejected)
            throws Exception {
        byte[] payload = new byte[0];
        switch (rejected) {
            case "unfinished" -> manager.getFinishedConditions()[0] = 0;
            case "unclaimed" -> claimed(false);
            case "tower" -> manager.setTowerDungeon(true);
            case "boss", "plot" -> {
                String subType = rejected.equals("boss") ? "DUNGEON_SUB_BOSS" : "DUNGEON_SUB_NONE";
                var other =
                        gson.fromJson(
                                "{id:9001,sceneId:9,subType:'" + subType + "',passCond:9001}",
                                FixtureDungeonData.class);
                GameData.getDungeonDataMap().put(9001, other);
                manager = new DungeonManager(source, other);
                manager.getFinishedConditions()[0] = 1;
                source.setDungeonManager(manager);
                claimed(true);
            }
            case "payload" -> payload = new byte[] {1};
            case "missing-manager" -> {
                source.setDungeonManager(null);
                manager = null;
            }
            case "outsider" -> source.getPlayers().clear();
        }
        request(payload);
        assertSame(source, player.getScene());
        assertFalse(source.getScriptManager().isDestroyed());
        assertEquals(0, replies());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void absentActorOrSceneCannotCrashTheRequest(boolean actorAbsent) {
        if (!actorAbsent) player.setScene(null);
        assertDoesNotThrow(
                () ->
                        new HandlerDungeonRestartReq()
                                .handle(
                                        session(actorAbsent ? null : player),
                                        new byte[0],
                                        new byte[0]));
        assertEquals(0, replies());
    }

    @Test
    void anActorWithoutAWorldCannotCrashOrReportSuccess() {
        player.setWorld(null);
        assertDoesNotThrow(() -> request(new byte[0]));
        kept();
        assertEquals(0, replies());
    }

    @Test
    void cancellationCannotSendASuccessfulRetryResponse() throws Exception {
        teleportListener(PlayerTeleportEvent::cancel);
        request(new byte[0]);
        kept();
        assertEquals(0, replies());
    }

    @Test
    void successfulClientRetryClearsTheClaimAndCannotImmediatelyRepeat() throws Exception {
        request(new byte[0]);
        var fresh = player.getScene();
        assertNotSame(source, fresh);
        assertEquals(1, replies());
        var method = DungeonManager.class.getMethod("hasClaimedDomainReward", int.class);
        assertFalse((boolean) method.invoke(fresh.getDungeonManager(), player.getUid()));
        request(new byte[0]);
        assertSame(fresh, player.getScene());
        assertEquals(1, replies());
    }

    @Test
    void retryRequestUsesTheReferenced71OpcodeWithoutCollision() throws Exception {
        assertEquals(9381, PacketOpcodes.DungeonRestartReq);
        assertEquals(9381, HandlerDungeonRestartReq.class.getAnnotation(Opcodes.class).value());
        long count = 0;
        for (var field : PacketOpcodes.class.getDeclaredFields())
            if (field.getType() == int.class && field.getInt(null) == 9381) count++;
        assertEquals(1, count);
    }
}
