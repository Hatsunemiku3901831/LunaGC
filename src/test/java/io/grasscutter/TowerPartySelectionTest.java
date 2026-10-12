package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.avatar.AvatarData;
import emu.grasscutter.data.excels.tower.*;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.EntityTeam;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.tower.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ProtEntityTypeOuterClass.ProtEntityType;
import emu.grasscutter.net.proto.SceneEntityInfoOuterClass.SceneEntityInfo;
import emu.grasscutter.net.proto.TowerTeamOuterClass.TowerTeam;
import emu.grasscutter.net.proto.TowerTeamSelectReqOuterClass.TowerTeamSelectReq;
import emu.grasscutter.net.proto.TowerTeamSelectRspOuterClass.TowerTeamSelectRsp;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.recv.HandlerTowerTeamSelectReq;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

/** Owned GUID validation, unchanged previous parties and actual tower request dispatch. */
class TowerPartySelectionTest extends QuestTestFixture {
    private final long first = 4294967301L,
            second = 8589934597L,
            third = 12884901893L,
            fourth = 17179869189L;
    private Map<Integer, TowerFloorData> oldFloors;
    private Map<Integer, TowerLevelData> oldLevels;

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

    @BeforeEach
    void parties() throws Exception {
        oldFloors = new HashMap<>(GameData.getTowerFloorDataMap());
        oldLevels = new HashMap<>(GameData.getTowerLevelDataMap());
        for (var pair :
                Map.of(10000021, first, 10000022, second, 10000023, third, 10000024, fourth)
                        .entrySet())
            assertTrue(
                    player.getAvatars().addAvatar(new OwnedAvatar(pair.getKey(), pair.getValue())));
        GameData.getTowerFloorDataMap()
                .put(
                        6001,
                        gson.fromJson(
                                "{floorId:6001,floorIndex:1,levelGroupId:6101,teamNum:1}",
                                TowerFloorData.class));
        GameData.getTowerFloorDataMap()
                .put(
                        6002,
                        gson.fromJson(
                                "{floorId:6002,floorIndex:5,levelGroupId:6201,teamNum:2}",
                                TowerFloorData.class));
        GameData.getTowerLevelDataMap()
                .put(
                        6101,
                        gson.fromJson(
                                "{levelId:6101,levelIndex:1,levelGroupId:6101,dungeonId:9001}",
                                TowerLevelData.class));
        GameData.getTowerLevelDataMap()
                .put(
                        6201,
                        gson.fromJson(
                                "{levelId:6201,levelIndex:1,levelGroupId:6201,dungeonId:9002}",
                                TowerLevelData.class));
        player.getTeamManager().setupTemporaryTeam(List.of(List.of(first), List.of(second)));
        replaceField(TeamManager.class, player.getTeamManager(), "useTemporarilyTeamIndex", 0);
        player.setScene(null);
        player.packets.clear();
    }

    @AfterEach
    void resources() {
        GameData.getTowerFloorDataMap().clear();
        GameData.getTowerFloorDataMap().putAll(oldFloors);
        GameData.getTowerLevelDataMap().clear();
        GameData.getTowerLevelDataMap().putAll(oldLevels);
    }

    private List<List<Integer>> slots() throws Exception {
        var field = TeamManager.class.getDeclaredField("temporaryTeam");
        field.setAccessible(true);
        var values = (List<TeamInfo>) field.get(player.getTeamManager());
        return values == null
                ? null
                : values.stream().map(team -> List.copyOf(team.getAvatars())).toList();
    }

    private int index() throws Exception {
        var field = TeamManager.class.getDeclaredField("useTemporarilyTeamIndex");
        field.setAccessible(true);
        return field.getInt(player.getTeamManager());
    }

    private void kept() throws Exception {
        assertEquals(List.of(List.of(10000021), List.of(10000022)), slots());
        assertEquals(0, index());
        assertEquals(List.of(10000021), player.getTeamManager().getCurrentTeamInfo().getAvatars());
        assertTrue(player.packets.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "null",
                "empty",
                "null-slot",
                "empty-slot",
                "null-guid",
                "unknown-guid",
                "within-duplicate",
                "across-duplicate",
                "oversize"
            })
    void invalidSetupCannotReplaceEitherPreviousParty(String invalid) throws Exception {
        List<List<Long>> request =
                switch (invalid) {
                    case "null" -> null;
                    case "empty" -> List.of();
                    case "null-slot" -> Arrays.asList(List.of(third), null);
                    case "empty-slot" -> List.of(List.of(third), List.of());
                    case "null-guid" -> List.of(List.of(third), Arrays.asList((Long) null));
                    case "unknown-guid" -> List.of(List.of(third), List.of(999999999999L));
                    case "within-duplicate" -> List.of(List.of(third), List.of(fourth, fourth));
                    case "across-duplicate" -> List.of(List.of(third), List.of(third));
                    default -> List.of(List.of(first, second, third, fourth, first));
                };
        assertDoesNotThrow(() -> player.getTeamManager().setupTemporaryTeam(request));
        kept();
    }

    @Test
    void validSetupPreservesOrderAndFullWidthGuids() throws Exception {
        var request = new ArrayList<List<Long>>();
        request.add(new ArrayList<>(List.of(third, first)));
        request.add(new ArrayList<>(List.of(fourth, second)));
        player.getTeamManager().setupTemporaryTeam(request);
        request.get(0).clear();
        request.clear();
        assertEquals(List.of(List.of(10000023, 10000021), List.of(10000024, 10000022)), slots());
        assertEquals(0, index());
    }

    @ParameterizedTest
    @ValueSource(ints = {-2, -1, 2, Integer.MAX_VALUE})
    void invalidSwitchCannotChangeTheSelectedTemporaryParty(int invalid) throws Exception {
        assertDoesNotThrow(() -> player.getTeamManager().useTemporaryTeam(invalid));
        kept();
    }

    @Test
    void anEmptyLegacySlotCannotReplaceTheActiveParty() throws Exception {
        replaceField(
                TeamManager.class,
                player.getTeamManager(),
                "temporaryTeam",
                new ArrayList<>(List.of(new TeamInfo(List.of(10000021)), new TeamInfo())));
        assertDoesNotThrow(() -> player.getTeamManager().useTemporaryTeam(1));
        assertEquals(0, index());
        assertEquals(List.of(10000021), player.getTeamManager().getCurrentTeamInfo().getAvatars());
    }

    @Test
    void validSwitchStillUsesTheSelectedOwnedEntityAndRemovesThePreviousOne() throws Exception {
        var scene =
                new emu.grasscutter.game.world.Scene(
                        player.getWorld(),
                        gson.fromJson(
                                "{id:4,type:'SCENE_WORLD'}",
                                emu.grasscutter.data.excels.scene.SceneData.class));
        player.setScene(scene);
        player.setSceneId(4);
        player.getTeamManager().setEntity(new EntityTeam(player));
        var firstEntity = entity(10000021);
        var secondEntity = entity(10000022);
        player.getTeamManager().getActiveTeam().addAll(List.of(firstEntity, secondEntity));
        player.getTeamManager().setCurrentCharacterIndex(0);
        scene.getEntities().put(firstEntity.getId(), firstEntity);
        try {
            player.getTeamManager().useTemporaryTeam(1);
            assertEquals(1, index());
            assertEquals(
                    List.of(10000022), player.getTeamManager().getCurrentTeamInfo().getAvatars());
            assertSame(secondEntity, player.getTeamManager().getCurrentAvatarEntity());
            assertFalse(scene.getEntities().containsKey(firstEntity.getId()));
            assertSame(secondEntity, scene.getEntities().get(secondEntity.getId()));
        } finally {
            scene.getScriptManager().onDestroy();
        }
    }

    private EntityAvatar entity(int id) {
        var avatar = player.getAvatars().getAvatarById(id);
        avatar.getFightProperties().put(FightProperty.FIGHT_PROP_MAX_HP.getId(), 100);
        avatar.getFightProperties().put(FightProperty.FIGHT_PROP_CUR_HP.getId(), 100);
        return new EntityAvatar(player.getScene(), avatar) {
            @Override
            public SceneEntityInfo toProto() {
                return SceneEntityInfo.newBuilder()
                        .setEntityId(getId())
                        .setEntityType(ProtEntityType.ProtEntityType_PROT_ENTITY_AVATAR)
                        .build();
            }
        };
    }

    @Test
    void errorResponseUsesTheNativeRetcodeFieldThree() throws Exception {
        select(999999, List.of(List.of(third), List.of(fourth)));
        var packet =
                player.packets.stream()
                        .filter(value -> value.getOpcode() == PacketOpcodes.TowerTeamSelectRsp)
                        .findFirst()
                        .orElseThrow();
        var input = com.google.protobuf.CodedInputStream.newInstance(packet.getData());
        assertEquals(24, input.readTag());
        assertEquals(-1, input.readInt32());
        assertTrue(input.isAtEnd());
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

    private TowerTeamSelectRsp select(int floor, List<List<Long>> teams) throws Exception {
        var builder = TowerTeamSelectReq.newBuilder().setFloorId(floor);
        for (int i = 0; i < teams.size(); i++)
            builder.addTowerTeamList(
                    TowerTeam.newBuilder()
                            .setTowerTeamId(i + 1)
                            .addAllAvatarGuidList(teams.get(i)));
        new HandlerTowerTeamSelectReq()
                .handle(session(), new byte[0], builder.build().toByteArray());
        var reply =
                player.packets.stream()
                        .filter(packet -> packet.getOpcode() == PacketOpcodes.TowerTeamSelectRsp)
                        .findFirst()
                        .orElseThrow();
        return TowerTeamSelectRsp.parseFrom(reply.getData());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "unknown-floor",
                "too-few",
                "too-many",
                "invalid-lower",
                "shared-avatar",
                "missing-level"
            })
    void rejectedTowerSelectionKeepsTheFloorAndRepliesWithFailure(String invalid) throws Exception {
        var tower = player.getTowerManager();
        int floor = invalid.equals("unknown-floor") ? 999999 : 6002;
        List<List<Long>> teams =
                switch (invalid) {
                    case "too-few" -> List.of(List.of(third));
                    case "too-many" -> List.of(List.of(first), List.of(second), List.of(third));
                    case "invalid-lower" -> List.of(List.of(third), List.of(999999999999L));
                    case "shared-avatar" -> List.of(List.of(third), List.of(third));
                    default -> List.of(List.of(third), List.of(fourth));
                };
        if (invalid.equals("missing-level")) GameData.getTowerLevelDataMap().remove(6201);
        var response = select(floor, teams);
        assertNotEquals(0, response.getRetcode());
        assertEquals(0, tower.getCurrentFloorId());
        assertEquals(List.of(List.of(10000021), List.of(10000022)), slots());
        assertEquals(0, index());
    }

    @ParameterizedTest
    @ValueSource(ints = {6001, 6002})
    void acceptedTowerSelectionUsesTheResourceTeamCount(int floor) throws Exception {
        var teams =
                floor == 6001
                        ? List.of(List.of(third, fourth))
                        : List.of(List.of(third), List.of(fourth));
        assertEquals(0, select(floor, teams).getRetcode());
        assertEquals(floor, player.getTowerManager().getCurrentFloorId());
        assertEquals(floor == 6001 ? 6101 : 6201, player.getTowerManager().getCurrentLevelId());
        assertEquals(
                floor == 6001
                        ? List.of(List.of(10000023, 10000024))
                        : List.of(List.of(10000023), List.of(10000024)),
                slots());
    }
}
