package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.AvatarDieAnimationEndReqOuterClass.AvatarDieAnimationEndReq;
import emu.grasscutter.net.proto.AvatarDieAnimationEndRspOuterClass.AvatarDieAnimationEndRsp;
import emu.grasscutter.net.proto.PlayerDieTypeOuterClass.PlayerDieType;
import emu.grasscutter.net.proto.SceneEntityInfoOuterClass.SceneEntityInfo;
import emu.grasscutter.net.proto.WorldPlayerDieNotifyOuterClass.WorldPlayerDieNotify;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.recv.HandlerAvatarDieAnimationEndReq;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

/** Actual request parsing and party switching, without a network listener or player writes. */
class AvatarDeathRequestTest {
    private AbilityTestFixture fixture;
    private GameSession session;
    private AvatarSkillDepotData depot;
    private final HandlerAvatarDieAnimationEndReq handler = new HandlerAvatarDieAnimationEndReq();

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        depot =
                new Gson()
                        .fromJson(
                                "{id:7301,energySkillData:{id:7308,costElemType:'Grass',costElemVal:50},elementType:'Grass'}",
                                AvatarSkillDepotData.class);
        session =
                new GameSession(null) {
                    @Override
                    public Player getPlayer() {
                        return fixture.player;
                    }
                };
        fixture.player.getTeamManager().getActiveTeam().clear();
        fixture.player.getTeamManager().setCurrentCharacterIndex(0);
    }

    @AfterEach
    void close() {
        if (fixture != null) fixture.close();
    }

    private EntityAvatar avatar(long guid, int entityId) {
        var value =
                new Avatar() {
                    @Override
                    public Player getPlayer() {
                        return fixture.player;
                    }

                    @Override
                    public long getGuid() {
                        return guid;
                    }

                    @Override
                    public int getAvatarId() {
                        return 10000073;
                    }

                    @Override
                    public AvatarSkillDepotData getSkillDepot() {
                        return depot;
                    }

                    @Override
                    public void save() {}
                };
        value.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 100);
        value.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 100);
        var entity =
                new EntityAvatar(fixture.scene, value) {
                    @Override
                    public SceneEntityInfo toProto() {
                        return SceneEntityInfo.getDefaultInstance();
                    }
                };
        entity.setId(entityId);
        fixture.player.getTeamManager().getActiveTeam().add(entity);
        return entity;
    }

    private void die(EntityAvatar entity, PlayerDieType type) {
        entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 0);
        entity.onDeath(type, 112233);
        assertFalse(entity.isAlive());
        fixture.player.packets.clear();
        fixture.scene.packets.clear();
    }

    private void request(long key) throws Exception {
        handler.handle(
                session,
                new byte[0],
                AvatarDieAnimationEndReq.newBuilder()
                        .setDieGuid(key)
                        .setSkillId(999)
                        .build()
                        .toByteArray());
    }

    private List<AvatarDieAnimationEndRsp> responses() throws Exception {
        var result = new ArrayList<AvatarDieAnimationEndRsp>();
        for (var packet : fixture.player.packets)
            if (packet.getOpcode() == PacketOpcodes.AvatarDieAnimationEndRsp)
                result.add(AvatarDieAnimationEndRsp.parseFrom(packet.getData()));
        return result;
    }

    private List<WorldPlayerDieNotify> worldDeaths() throws Exception {
        var result = new ArrayList<WorldPlayerDieNotify>();
        for (var packet : fixture.player.packets)
            if (packet.getOpcode() == PacketOpcodes.WorldPlayerDieNotify)
                result.add(WorldPlayerDieNotify.parseFrom(packet.getData()));
        return result;
    }

    private void assertAcknowledged(EntityAvatar entity) throws Exception {
        assertEquals(1, responses().size());
        assertEquals(
                entity.getId(),
                responses().get(0).getDieGuid(),
                "Keep the existing response identifier contract");
        assertEquals(0, responses().get(0).getSkillId());
        assertEquals(0, responses().get(0).getRetcode());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void activeDeathKeySwitchesToLivingReplacement(boolean persistentGuid) throws Exception {
        var dead = avatar(0x2_00000064L, 0x01000064);
        var next = avatar(0x2_00000065L, 0x01000065);
        die(dead, PlayerDieType.PlayerDieType_PLAYER_DIE_KILL_BY_MONSTER);
        request(persistentGuid ? dead.getAvatar().getGuid() : dead.getId());
        assertEquals(1, fixture.player.getTeamManager().getCurrentCharacterIndex());
        assertSame(next, fixture.scene.getEntityById(next.getId()));
        assertTrue(worldDeaths().isEmpty());
        assertAcknowledged(dead);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void allDeadPartyPublishesWorldDeathAndAcknowledges(boolean persistentGuid) throws Exception {
        var dead = avatar(0x2_00000064L, 0x01000064);
        var other = avatar(0x2_00000065L, 0x01000065);
        die(other, PlayerDieType.PlayerDieType_PLAYER_DIE_KILL_BY_MONSTER);
        die(dead, PlayerDieType.PlayerDieType_PLAYER_DIE_KILL_BY_MONSTER);
        request(persistentGuid ? dead.getAvatar().getGuid() : dead.getId());
        assertEquals(0, fixture.player.getTeamManager().getCurrentCharacterIndex());
        assertEquals(1, worldDeaths().size());
        assertEquals(
                PlayerDieType.PlayerDieType_PLAYER_DIE_KILL_BY_MONSTER,
                worldDeaths().get(0).getDieType());
        assertEquals(112233, worldDeaths().get(0).getMonsterId());
        assertAcknowledged(dead);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void drowningKeepsCurrentSlotAndOriginalWorldDeathFlow(boolean persistentGuid)
            throws Exception {
        var dead = avatar(0x2_00000064L, 0x01000064);
        avatar(0x2_00000065L, 0x01000065);
        die(dead, PlayerDieType.PlayerDieType_PLAYER_DIE_DRAWN);
        request(persistentGuid ? dead.getAvatar().getGuid() : dead.getId());
        assertEquals(0, fixture.player.getTeamManager().getCurrentCharacterIndex());
        assertEquals(1, worldDeaths().size());
        assertEquals(
                PlayerDieType.PlayerDieType_PLAYER_DIE_DRAWN, worldDeaths().get(0).getDieType());
        assertAcknowledged(dead);
    }

    @Test
    void anotherDeadPartyMemberCannotFinishTheActiveAnimation() throws Exception {
        var current = avatar(0x2_00000064L, 0x01000064);
        var other = avatar(0x2_00000065L, 0x01000065);
        die(other, PlayerDieType.PlayerDieType_PLAYER_DIE_KILL_BY_MONSTER);
        die(current, PlayerDieType.PlayerDieType_PLAYER_DIE_KILL_BY_MONSTER);
        request(other.getAvatar().getGuid());
        assertEquals(0, fixture.player.getTeamManager().getCurrentCharacterIndex());
        assertTrue(fixture.player.packets.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 0x3_00000064L, 0x1_01000064L})
    void unknownOrTruncatedIdentifierCannotTargetTheActiveAvatar(long key) throws Exception {
        var dead = avatar(0x2_00000064L, 0x01000064);
        avatar(0x2_00000065L, 0x01000065);
        die(dead, PlayerDieType.PlayerDieType_PLAYER_DIE_KILL_BY_MONSTER);
        request(key);
        assertEquals(0, fixture.player.getTeamManager().getCurrentCharacterIndex());
        assertTrue(fixture.player.packets.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void livingAvatarCannotBeSwitchedByADeathAnimationRequest(boolean persistentGuid)
            throws Exception {
        var current = avatar(0x2_00000064L, 0x01000064);
        avatar(0x2_00000065L, 0x01000065);
        request(persistentGuid ? current.getAvatar().getGuid() : current.getId());
        assertEquals(0, fixture.player.getTeamManager().getCurrentCharacterIndex());
        assertTrue(fixture.player.packets.isEmpty());
        assertNull(fixture.scene.getEntityById(current.getId()));
    }

    @Test
    void revivedAvatarIgnoresItsOldEntityAnimationRequest() throws Exception {
        var current = avatar(0x2_00000064L, 0x01000064);
        avatar(0x2_00000065L, 0x01000065);
        die(current, PlayerDieType.PlayerDieType_PLAYER_DIE_KILL_BY_MONSTER);
        assertTrue(current.revive(50));
        fixture.player.packets.clear();
        request(current.getId());
        assertTrue(current.isAlive());
        assertTrue(fixture.player.packets.isEmpty());
    }

    @Test
    void emptyTeamDoesNotCreateAnAvatarFromAnAnimationRequest() throws Exception {
        request(0x01000064);
        assertTrue(fixture.player.getTeamManager().getActiveTeam().isEmpty());
        assertTrue(fixture.player.packets.isEmpty());
    }

    @Test
    void missingSceneCannotCompleteAnOldAnimation() throws Exception {
        var current = avatar(0x2_00000064L, 0x01000064);
        avatar(0x2_00000065L, 0x01000065);
        die(current, PlayerDieType.PlayerDieType_PLAYER_DIE_KILL_BY_MONSTER);
        fixture.player.setScene(null);
        request(current.getId());
        assertEquals(0, fixture.player.getTeamManager().getCurrentCharacterIndex());
        assertTrue(fixture.player.packets.isEmpty());
    }

    @Test
    void repeatingARequestAfterReplacementDoesNotSwitchAgain() throws Exception {
        var current = avatar(0x2_00000064L, 0x01000064);
        var next = avatar(0x2_00000065L, 0x01000065);
        die(current, PlayerDieType.PlayerDieType_PLAYER_DIE_KILL_BY_MONSTER);
        request(current.getAvatar().getGuid());
        assertEquals(1, fixture.player.getTeamManager().getCurrentCharacterIndex());
        fixture.player.packets.clear();
        request(current.getAvatar().getGuid());
        assertEquals(1, fixture.player.getTeamManager().getCurrentCharacterIndex());
        assertSame(next, fixture.player.getTeamManager().getCurrentAvatarEntity());
        assertTrue(fixture.player.packets.isEmpty());
    }

    @Test
    void zeroRequestCannotMatchAnUnassignedAvatarGuid() throws Exception {
        var current = avatar(0, 0x01000064);
        avatar(0x2_00000065L, 0x01000065);
        die(current, PlayerDieType.PlayerDieType_PLAYER_DIE_KILL_BY_MONSTER);
        request(0);
        assertEquals(0, fixture.player.getTeamManager().getCurrentCharacterIndex());
        assertTrue(fixture.player.packets.isEmpty());
    }

    @Test
    void avatarFromAPreviousSceneCannotCompleteTheCurrentAnimation() throws Exception {
        var current = avatar(0x2_00000064L, 0x01000064);
        avatar(0x2_00000065L, 0x01000065);
        die(current, PlayerDieType.PlayerDieType_PLAYER_DIE_KILL_BY_MONSTER);
        var other =
                new emu.grasscutter.game.world.Scene(
                        fixture.scene.getWorld(),
                        new Gson()
                                .fromJson(
                                        "{id:4,type:'SCENE_WORLD'}",
                                        emu.grasscutter.data.excels.scene.SceneData.class));
        fixture.player.setScene(other);
        request(current.getAvatar().getGuid());
        assertEquals(0, fixture.player.getTeamManager().getCurrentCharacterIndex());
        assertTrue(fixture.player.packets.isEmpty());
        assertFalse(other.getEntities().containsKey(current.getId()));
    }
}
