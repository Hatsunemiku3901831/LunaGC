package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.avatar.AvatarData;
import emu.grasscutter.data.excels.dungeon.DungeonData;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.data.excels.tower.*;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.dungeons.DungeonSystem;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ProtEntityTypeOuterClass.ProtEntityType;
import emu.grasscutter.net.proto.SceneEntityInfoOuterClass.SceneEntityInfo;
import emu.grasscutter.net.proto.TowerEnterLevelReqOuterClass.TowerEnterLevelReq;
import emu.grasscutter.net.proto.TowerEnterLevelRspOuterClass.TowerEnterLevelRsp;
import emu.grasscutter.plugin.PluginManager;
import emu.grasscutter.server.event.EventHandler;
import emu.grasscutter.server.event.player.PlayerTeleportEvent;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.recv.HandlerTowerEnterLevelReq;
import emu.grasscutter.utils.FileUtils;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.*;
import java.util.*;

/** Actual tower/system calls with controlled transfer outcomes and owned avatar serialization. */
class TowerEntryOutcomeTest extends QuestTestFixture {
    private Map<Integer, TowerFloorData> oldFloors;
    private Map<Integer, TowerLevelData> oldLevels;
    private Map<Integer, DungeonData> oldDungeons;
    private Map<Integer, SceneData> oldScenes;
    private TransferWorld world;
    private Scene source, destination;
    private EntityAvatar firstEntity, secondEntity;

    static class OwnedAvatar extends Avatar {
        final int id;
        final long guid;
        final ObjectId oid = new ObjectId();

        OwnedAvatar(int id, long guid) {
            this.id = id;
            this.guid = guid;
        }

        @Override
        public int getAvatarId() {
            return id;
        }

        @Override
        public long getGuid() {
            return guid;
        }

        @Override
        public ObjectId getObjectId() {
            return oid;
        }

        @Override
        public AvatarData getAvatarData() {
            return new AvatarData();
        }

        @Override
        public void setCurrentEnergy() {}

        @Override
        public void recalcStats(boolean notify) {}

        @Override
        public void save() {}
    }

    static class EntryDungeonData extends DungeonData {
        @Override
        public Position getStartPosition() {
            return new Position(1, 2, 3);
        }

        @Override
        public Position getStartRotation() {
            return new Position(0, 90, 0);
        }
    }

    private class TransferWorld extends World {
        boolean success, throwTransfer, nativeTransfer;
        int attempts;
        int selectedAtTransfer = -99, avatarAtTransfer = -99;

        TransferWorld() {
            super(player.server, player);
        }

        @Override
        public boolean transferPlayerToScene(Player target, int scene, DungeonData data) {
            attempts++;
            try {
                selectedAtTransfer = index();
            } catch (Exception error) {
                throw new AssertionError(error);
            }
            avatarAtTransfer = target.getTeamManager().getCurrentCharacterIndex();
            if (nativeTransfer) return super.transferPlayerToScene(target, scene, data);
            if (throwTransfer) throw new IllegalStateException("Controlled pre-transfer failure");
            if (!success) return false;
            source.removePlayer(target);
            destination.addPlayer(target);
            return true;
        }
    }

    @BeforeEach
    void dungeon() throws Exception {
        oldFloors = new HashMap<>(GameData.getTowerFloorDataMap());
        oldLevels = new HashMap<>(GameData.getTowerLevelDataMap());
        oldDungeons = new HashMap<>(GameData.getDungeonDataMap());
        oldScenes = new HashMap<>(GameData.getSceneDataMap());
        GameData.getSceneDataMap()
                .put(4, gson.fromJson("{id:4,type:'SCENE_WORLD'}", SceneData.class));
        GameData.getSceneDataMap()
                .put(9, gson.fromJson("{id:9,type:'SCENE_DUNGEON'}", SceneData.class));
        GameData.getTowerFloorDataMap()
                .put(
                        6002,
                        gson.fromJson(
                                "{floorId:6002,floorIndex:5,levelGroupId:6201,teamNum:2}",
                                TowerFloorData.class));
        GameData.getTowerLevelDataMap()
                .put(
                        6201,
                        gson.fromJson(
                                "{levelId:6201,levelIndex:1,levelGroupId:6201,dungeonId:9001}",
                                TowerLevelData.class));
        GameData.getDungeonDataMap()
                .put(9001, gson.fromJson("{id:9001,sceneId:9,passCond:0}", EntryDungeonData.class));
        for (int i = 0; i < 3; i++)
            assertTrue(
                    player.getAvatars().addAvatar(new OwnedAvatar(10000021 + i, 4294967301L + i)));
        world = new TransferWorld();
        player.setWorld(world);
        boolean enabled = Grasscutter.getConfig().server.game.enableScriptInBigWorld;
        Grasscutter.getConfig().server.game.enableScriptInBigWorld = false;
        try {
            source = new Scene(world, gson.fromJson("{id:4,type:'SCENE_WORLD'}", SceneData.class));
            destination =
                    new Scene(world, gson.fromJson("{id:9,type:'SCENE_DUNGEON'}", SceneData.class));
        } finally {
            Grasscutter.getConfig().server.game.enableScriptInBigWorld = enabled;
        }
        player.setScene(source);
        player.setSceneId(4);
        source.getPlayers().add(player);
        source.setPrevScene(77);
        source.setPrevScenePoint(88);
        player.getTeamManager()
                .getCurrentSinglePlayerTeamInfo()
                .getAvatars()
                .addAll(List.of(10000021, 10000022));
        player.getTeamManager().setEntity(new EntityTeam(player));
        firstEntity = entity(10000021);
        secondEntity = entity(10000022);
        player.getTeamManager().getActiveTeam().addAll(List.of(firstEntity, secondEntity));
        player.getTeamManager().setCurrentCharacterIndex(1);
        source.getEntities().put(secondEntity.getId(), secondEntity);
        assertTrue(
                player.getTowerManager()
                        .tryTeamSelect(
                                6002,
                                List.of(List.of(4294967301L, 4294967302L), List.of(4294967303L))));
        replaceField(
                emu.grasscutter.server.game.GameServer.class,
                player.server,
                "dungeonSystem",
                new DungeonSystem(player.server));
        player.packets.clear();
    }

    private EntityAvatar entity(int id) {
        var avatar = player.getAvatars().getAvatarById(id);
        avatar.getFightProperties().put(FightProperty.FIGHT_PROP_MAX_HP.getId(), 100);
        avatar.getFightProperties().put(FightProperty.FIGHT_PROP_CUR_HP.getId(), 100);
        return new EntityAvatar(source, avatar) {
            @Override
            public SceneEntityInfo toProto() {
                return SceneEntityInfo.newBuilder()
                        .setEntityId(getId())
                        .setEntityType(ProtEntityType.ProtEntityType_PROT_ENTITY_AVATAR)
                        .build();
            }
        };
    }

    @AfterEach
    void restore() {
        source.getScriptManager().onDestroy();
        destination.getScriptManager().onDestroy();
        GameData.getTowerFloorDataMap().clear();
        GameData.getTowerFloorDataMap().putAll(oldFloors);
        GameData.getTowerLevelDataMap().clear();
        GameData.getTowerLevelDataMap().putAll(oldLevels);
        GameData.getDungeonDataMap().clear();
        GameData.getDungeonDataMap().putAll(oldDungeons);
        GameData.getSceneDataMap().clear();
        GameData.getSceneDataMap().putAll(oldScenes);
    }

    private int index() throws Exception {
        var field = TeamManager.class.getDeclaredField("useTemporarilyTeamIndex");
        field.setAccessible(true);
        return field.getInt(player.getTeamManager());
    }

    private TowerEnterLevelRsp response() throws Exception {
        var packets =
                player.packets.stream()
                        .filter(packet -> packet.getOpcode() == PacketOpcodes.TowerEnterLevelRsp)
                        .toList();
        assertEquals(1, packets.size());
        return TowerEnterLevelRsp.parseFrom(packets.get(0).getData());
    }

    private void kept() throws Exception {
        assertSame(source, player.getScene());
        assertEquals(4, player.getSceneId());
        assertEquals(-1, index());
        assertEquals(1, player.getTeamManager().getCurrentCharacterIndex());
        assertEquals(List.of(firstEntity, secondEntity), player.getTeamManager().getActiveTeam());
        assertSame(secondEntity, source.getEntities().get(secondEntity.getId()));
        assertEquals(77, source.getPrevScene());
        assertEquals(88, source.getPrevScenePoint());
        assertNull(destination.getDungeonManager());
    }

    @Test
    void canceledHandoffRestoresTheOriginalSelectionAndDoesNotRewriteItsScene() throws Exception {
        player.getTowerManager().enterLevel(123);
        kept();
        assertEquals(1, world.attempts);
        assertEquals(0, world.selectedAtTransfer);
        assertEquals(0, world.avatarAtTransfer);
        assertNotEquals(0, response().getRetcode());
    }

    @Test
    void cancellationCannotEmitSuccessOrSkillAndStarNotifications() throws Exception {
        player.getTowerManager().enterLevel(123);
        assertEquals(
                List.of(PacketOpcodes.TowerEnterLevelRsp),
                player.packets.stream().map(BasePacket::getOpcode).toList());
        assertEquals(-1, response().getRetcode());
        assertEquals(0, response().getTowerBuffIdListCount());
    }

    @Test
    void thrownTransferRestoresTheOriginalSelectionBeforePropagating() throws Exception {
        world.throwTransfer = true;
        assertThrows(IllegalStateException.class, () -> player.getTowerManager().enterLevel(123));
        kept();
        assertTrue(player.packets.isEmpty());
    }

    @Test
    void missingDungeonDataCannotChangeThePartyOrReportSuccess() throws Exception {
        GameData.getDungeonDataMap().remove(9001);
        player.getTowerManager().enterLevel(123);
        kept();
        assertEquals(0, world.attempts);
        assertEquals(-1, response().getRetcode());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void absentWorldOrSceneReturnsFailureBeforeChangingParty(boolean missingWorld)
            throws Exception {
        if (missingWorld) player.setWorld(null);
        else player.setScene(null);
        assertDoesNotThrow(() -> player.getTowerManager().enterLevel(123));
        assertEquals(-1, index());
        assertEquals(1, player.getTeamManager().getCurrentCharacterIndex());
        assertEquals(0, world.attempts);
        assertEquals(-1, response().getRetcode());
    }

    @Test
    void successfulEntryUsesUpperTeamAndLeadSlotBeforeTheTransfer() throws Exception {
        world.success = true;
        player.getTowerManager().enterLevel(123);
        assertSame(destination, player.getScene());
        assertEquals(0, index());
        assertEquals(0, world.avatarAtTransfer);
        assertEquals(0, response().getRetcode());
        assertTrue(destination.getDungeonManager().isTowerDungeon());
        assertEquals(4, destination.getPrevScene());
        assertEquals(123, destination.getPrevScenePoint());
        assertEquals(77, source.getPrevScene());
        assertEquals(88, source.getPrevScenePoint());
        assertEquals(
                List.of(
                        PacketOpcodes.TowerCurLevelRecordChangeNotify,
                        PacketOpcodes.TowerEnterLevelRsp,
                        PacketOpcodes.CanUseSkillNotify,
                        PacketOpcodes.TowerLevelStarCondNotify),
                player.packets.stream().map(BasePacket::getOpcode).toList());
    }

    @Test
    void failedRequestUsesTheActualTowerEnterHandler() throws Exception {
        var session =
                new GameSession(player.server) {
                    @Override
                    public Player getPlayer() {
                        return player;
                    }

                    @Override
                    public void send(BasePacket packet) {
                        player.packets.add(packet);
                    }
                };
        new HandlerTowerEnterLevelReq()
                .handle(
                        session,
                        new byte[0],
                        TowerEnterLevelReq.newBuilder().setEnterPointId(123).build().toByteArray());
        kept();
        assertEquals(-1, response().getRetcode());
    }

    @Test
    void successfulEntryCapturesTheWholeRosterBeforeTheChallengeStarts() throws Exception {
        world.success = true;
        for (var avatar : player.getAvatars()) {
            avatar.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 100);
            avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 37);
            avatar.setCurrentHp(37);
        }
        player.getTowerManager().enterLevel(123);
        assertSame(destination, player.getScene());
        for (var avatar : player.getAvatars()) {
            avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 0);
            avatar.setCurrentHp(0);
        }
        assertEquals(
                true,
                player.getTowerManager()
                        .getClass()
                        .getMethod("restoreChamberState")
                        .invoke(player.getTowerManager()));
        for (var avatar : player.getAvatars()) assertEquals(37, avatar.getCurrentHp());
    }

    @Test
    void actualTeleportCancellationKeepsTheOriginalEntitiesAndSelection() throws Exception {
        var pluginPath = FileUtils.getPluginPath("");
        if (Files.isDirectory(pluginPath))
            try (var entries = Files.list(pluginPath)) {
                assertTrue(entries.noneMatch(path -> path.toString().endsWith(".jar")));
            }
        var plugins = new PluginManager();
        replaceField(Grasscutter.class, null, "pluginManager", plugins);
        boolean[] called = {false};
        new EventHandler<>(PlayerTeleportEvent.class)
                .listener(
                        event -> {
                            called[0] = true;
                            assertEquals(
                                    List.of(firstEntity, secondEntity),
                                    player.getTeamManager().getActiveTeam());
                            assertSame(
                                    secondEntity, source.getEntities().get(secondEntity.getId()));
                            assertFalse(source.getEntities().containsKey(firstEntity.getId()));
                            event.cancel();
                        })
                .register(null);
        world.nativeTransfer = true;
        player.getTowerManager().enterLevel(123);
        assertTrue(called[0]);
        kept();
        assertEquals(-1, response().getRetcode());
        assertFalse(source.getScriptManager().isDestroyed());
        assertEquals(
                List.of(PacketOpcodes.TowerEnterLevelRsp),
                player.packets.stream().map(BasePacket::getOpcode).toList());
    }
}
