package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.ScenePointEntry;
import emu.grasscutter.data.common.PointData;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.DungeonDieOptionReqOuterClass.DungeonDieOptionReq;
import emu.grasscutter.net.proto.PlayerDieOptionOuterClass.PlayerDieOption;
import emu.grasscutter.net.proto.PlayerEnterSceneNotifyOuterClass.PlayerEnterSceneNotify;
import emu.grasscutter.net.proto.WorldPlayerReviveRspOuterClass.WorldPlayerReviveRsp;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.recv.HandlerDungeonDieOptionReq;
import emu.grasscutter.server.packet.recv.HandlerWorldPlayerReviveReq;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

/** Real point resources, quest locks, revival packets and the existing team death lifecycle. */
class WorldRespawnPointTest {
    AbilityTestFixture fixture;
    RecordingPlayer player;
    Map<Integer, ScenePointEntry> previousPoints;

    static final class RecordingPlayer extends AbilityTestFixture.TestPlayer {
        Consumer<BasePacket> onSend = packet -> {};
        GameServer server;

        @Override
        public GameServer getServer() {
            return server;
        }

        @Override
        public void sendPacket(BasePacket packet) {
            onSend.accept(packet);
            super.sendPacket(packet);
        }
    }

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        previousPoints = new HashMap<>(GameData.getScenePointEntryMap());
        GameData.getScenePointEntryMap().clear();
        player = new RecordingPlayer();
        player.setUid(10002);
        player.setWorld(fixture.scene.getWorld());
        player.server = fixture.scene.getWorld().getServer();
        player.setScene(fixture.scene);
        player.setSceneId(3);
        player.getPosition().set(10, 20, 30);
        var session =
                new GameSession(null) {
                    @Override
                    public void send(BasePacket packet) {
                        WorldRespawnPointTest.this.player.sendPacket(packet);
                    }
                };
        session.setPlayer(player);
        player.setSession(session);
    }

    @AfterEach
    void restore() {
        GameData.getScenePointEntryMap().clear();
        GameData.getScenePointEntryMap().putAll(previousPoints);
        fixture.close();
    }

    PointData point(int id, String json) {
        var data = JsonUtils.decode(json, PointData.class);
        data.setId(id);
        put(3, id, new ScenePointEntry(3, data));
        return data;
    }

    void put(int scene, int id, ScenePointEntry entry) {
        GameData.getScenePointEntryMap().put((scene << 16) + id, entry);
        player.getUnlockedScenePoints(scene).add(id);
    }

    PointData waypoint(int id, float x) {
        return point(id, "{$type:'TransPointNormal',tranPos:{x:" + x + ",y:20,z:30}}");
    }

    PointData actualPoint(int scene, int id) throws Exception {
        var json =
                JsonParser.parseString(
                                Files.readString(
                                        Path.of(
                                                "resources/BinOutput/Scene/Point/scene"
                                                        + scene
                                                        + "_point.json")))
                        .getAsJsonObject()
                        .getAsJsonObject("points")
                        .getAsJsonObject(Integer.toString(id));
        var point = JsonUtils.decode(json.toString(), PointData.class);
        point.setId(id);
        assertEquals("TransPointNormal", point.getType());
        put(scene, id, new ScenePointEntry(scene, point));
        return point;
    }

    Position find() {
        return player.getTeamManager().getRespawnPosition();
    }

    void position(Position expected, Position actual) {
        assertEquals(expected.toProto(), actual.toProto());
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 5, 6})
    void currentWorldResourcesAreRecognized(int scene) throws Exception {
        player.setSceneId(scene);
        var point = actualPoint(scene, scene == 3 ? 10 : 1);
        player.getPosition().set(point.getTranPos()).addX(15);
        position(point.getTranPos(), find());
    }

    @ParameterizedTest
    @ValueSource(strings = {"SceneTransPoint", "TransPoint"})
    void olderWaypointNamesStillWork(String type) {
        var point = point(1, "{$type:'" + type + "',tranPos:{x:11,y:20,z:30}}");
        position(point.getTranPos(), find());
    }

    @Test
    void onlyPermanentlyUnlockedPointsAreCandidates() {
        waypoint(1, 11);
        waypoint(2, 18);
        player.getUnlockedScenePoints(3).remove(1);
        assertEquals(18, find().getX());
    }

    @Test
    void questLockedNearestPointIsExcludedWithoutErasingItsUnlock() {
        waypoint(1, 11);
        waypoint(2, 18);
        player.getForceLockedScenePoints(3).add(1);
        assertEquals(18, find().getX());
        assertTrue(player.getUnlockedScenePoints(3).contains(1));
        assertTrue(player.isScenePointForceLocked(3, 1));
    }

    @Test
    void anUnavailableSetFallsBackToADetachedDeathPosition() {
        waypoint(1, 11);
        player.getForceLockedScenePoints(3).add(1);
        Position result = assertDoesNotThrow(this::find);
        position(player.getPosition(), result);
        assertNotSame(player.getPosition(), result);
        result.set(999, 999, 999);
        position(new Position(10, 20, 30), player.getPosition());
    }

    @Test
    void anEmptyUnlockSetDoesNotSendThePlayerToTheStartBeach() {
        position(player.getPosition(), assertDoesNotThrow(this::find));
    }

    @Test
    void missingResourceRowsDoNotHideTheNextValidPoint() {
        player.getUnlockedScenePoints(3).add(7);
        waypoint(8, 18);
        assertEquals(18, assertDoesNotThrow(this::find).getX());
    }

    @Test
    void missingPointDataDoesNotHideTheNextValidPoint() {
        put(3, 7, new ScenePointEntry(3, null));
        waypoint(8, 18);
        assertEquals(18, assertDoesNotThrow(this::find).getX());
    }

    @Test
    void aResourceRowFromAnotherSceneCannotBecomeTheRespawnPoint() {
        var foreign =
                JsonUtils.decode(
                        "{$type:'SceneTransPoint',tranPos:{x:10,y:20,z:30}}", PointData.class);
        foreign.setId(1);
        put(3, 1, new ScenePointEntry(5, foreign));
        waypoint(2, 18);
        assertEquals(18, find().getX());
    }

    @Test
    void aWaypointWithACrossSceneDestinationIsExcluded() {
        var foreign = point(1, "{$type:'SceneTransPoint',tranPos:{x:10,y:20,z:30}}");
        foreign.setTranSceneId(5);
        waypoint(2, 18);
        assertEquals(18, find().getX());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"DungeonEntry", "DungeonExit", "PersonalSceneJumpPoint", "FutureTransPoint"})
    void unrelatedPointTypesAreNotWaypoints(String type) {
        point(1, "{$type:'" + type + "',tranPos:{x:10,y:20,z:30}}");
        waypoint(2, 18);
        assertEquals(18, find().getX());
    }

    @Test
    void aMissingTransferPositionCanUseThePointPosition() {
        var point = point(1, "{$type:'TransPointNormal',pos:{x:11,y:20,z:30}}");
        position(point.getPos(), find());
    }

    @Test
    void theNearestDestinationUsesTransferCoordinatesAndAllThreeAxes() {
        point(1, "{$type:'TransPointNormal',pos:{x:10,y:20,z:30},tranPos:{x:10,y:120,z:30}}");
        waypoint(2, 18);
        assertEquals(18, find().getX());
    }

    @Test
    void invalidTransferCoordinatesCanUseAValidPointPosition() {
        var point =
                point(
                        1,
                        "{$type:'TransPointNormal',pos:{x:11,y:20,z:30},tranPos:{x:12,y:20,z:30}}");
        point.getTranPos().setX(Float.NaN);
        position(point.getPos(), find());
    }

    @Test
    void nonFiniteAndAbsentDestinationsAreSkipped() {
        waypoint(1, 11).getTranPos().setZ(Float.POSITIVE_INFINITY);
        point(2, "{$type:'TransPointNormal'}");
        waypoint(3, 18);
        assertEquals(18, find().getX());
    }

    @Test
    void aChosenDestinationCannotModifyTheSharedPointResource() {
        var point = point(1, "{$type:'SceneTransPoint',tranPos:{x:11,y:20,z:30}}");
        var chosen = find();
        assertNotSame(point.getTranPos(), chosen);
        chosen.set(999, 999, 999);
        position(new Position(11, 20, 30), point.getTranPos());
    }

    @Test
    void equalDistancesUsePointIdRatherThanSaveInsertionOrder() {
        waypoint(90, 8);
        waypoint(2, 12);
        assertEquals(12, find().getX());
    }

    void reviveRequest() throws Exception {
        new HandlerWorldPlayerReviveReq().handle(player.getSession(), new byte[0], new byte[0]);
    }

    List<BasePacket> packets(int opcode) {
        return player.packets.stream().filter(p -> p.getOpcode() == opcode).toList();
    }

    @Test
    void actualRequestSendsOneReplyAndRestoresTheExistingDeathLifecycle() throws Exception {
        waypoint(1, 18);
        var avatar =
                new Avatar() {
                    @Override
                    public emu.grasscutter.game.player.Player getPlayer() {
                        return WorldRespawnPointTest.this.player;
                    }

                    @Override
                    public void save() {}
                };
        var entity = new EntityAvatar(fixture.scene, avatar);
        entity.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 100);
        entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 0);
        entity.checkIfDead();
        assertTrue(entity.tryBeginDeath());
        player.getTeamManager().getActiveTeam().add(entity);
        reviveRequest();
        assertEquals(1, packets(PacketOpcodes.WorldPlayerReviveRsp).size());
        assertEquals(
                0,
                WorldPlayerReviveRsp.parseFrom(
                                packets(PacketOpcodes.WorldPlayerReviveRsp).get(0).getData())
                        .getRetcode());
        assertTrue(entity.isAlive());
        assertEquals(40, entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        entity.damage(10);
        assertEquals(30, entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        position(new Position(18, 20, 30), player.getPosition());
        var enter =
                PlayerEnterSceneNotify.parseFrom(
                        packets(PacketOpcodes.PlayerEnterSceneNotify).get(0).getData());
        assertEquals(player.getPosition().toProto(), enter.getPos());
    }

    @Test
    void noWaypointKeepsTheActualSceneAndDeathLocation() throws Exception {
        var scene =
                new AbilityTestFixture.TestScene(
                        (AbilityTestFixture.TestWorld) player.getWorld(),
                        JsonUtils.decode("{id:5,type:'SCENE_WORLD'}", SceneData.class));
        player.setScene(scene);
        player.setSceneId(5);
        reviveRequest();
        position(new Position(10, 20, 30), player.getPosition());
        assertEquals(5, player.getSceneId());
        assertSame(scene, player.getScene());
        assertEquals(1, packets(PacketOpcodes.PlayerEnterSceneNotify).size());
        var enter =
                PlayerEnterSceneNotify.parseFrom(
                        packets(PacketOpcodes.PlayerEnterSceneNotify).get(0).getData());
        assertEquals(5, (enter.getSceneId() ^ 11523) + 49379);
        assertEquals(player.getPosition().toProto(), enter.getPos());
    }

    @Test
    void revivalUsesOneDetachedDestinationForTheNotificationAndPlayer() throws Exception {
        var point = point(1, "{$type:'SceneTransPoint',tranPos:{x:18,y:20,z:30}}");
        player.onSend =
                packet -> {
                    if (packet.getOpcode() == PacketOpcodes.PlayerEnterSceneNotify)
                        point.getTranPos().setX(999);
                };
        reviveRequest();
        var enter =
                PlayerEnterSceneNotify.parseFrom(
                        packets(PacketOpcodes.PlayerEnterSceneNotify).get(0).getData());
        assertEquals(18, enter.getPos().getX());
        assertEquals(18, player.getPosition().getX());
    }

    @Test
    void dungeonRevivalHandlerSendsItsOwnResponseWithoutAWorldReviveReply() throws Exception {
        waypoint(1, 18);
        var request =
                DungeonDieOptionReq.newBuilder()
                        .setDieOption(PlayerDieOption.DIE_OPT_REVIVE)
                        .build();
        new HandlerDungeonDieOptionReq()
                .handle(player.getSession(), new byte[0], request.toByteArray());
        assertEquals(1, packets(PacketOpcodes.DungeonDieOptionRsp).size());
        assertTrue(packets(PacketOpcodes.WorldPlayerReviveRsp).isEmpty());
        position(new Position(18, 20, 30), player.getPosition());
    }

    @Test
    void notificationFailureDoesNotTriggerAnotherEnterSceneOrAWorldStartFallback() {
        player.setSceneId(5);
        int[] attempts = {0};
        player.onSend =
                packet -> {
                    if (packet.getOpcode() == PacketOpcodes.PlayerEnterSceneNotify
                            && ++attempts[0] == 1)
                        throw new IllegalStateException("Controlled notification failure");
                };
        assertThrows(IllegalStateException.class, this::reviveRequest);
        assertEquals(1, attempts[0]);
        position(new Position(10, 20, 30), player.getPosition());
        assertEquals(5, player.getSceneId());
        assertTrue(packets(PacketOpcodes.WorldPlayerReviveRsp).isEmpty());
    }
}
