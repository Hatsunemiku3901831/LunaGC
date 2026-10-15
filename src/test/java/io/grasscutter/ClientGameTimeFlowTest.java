package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.quest.enums.QuestState;
import emu.grasscutter.game.world.World;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ClientSetGameTimeReqOuterClass.ClientSetGameTimeReq;
import emu.grasscutter.net.proto.ClientSetGameTimeRspOuterClass.ClientSetGameTimeRsp;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import emu.grasscutter.net.proto.PlayerGameTimeNotifyOuterClass.PlayerGameTimeNotify;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.scripts.ScriptLoader;
import emu.grasscutter.server.game.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

/** Real request dispatch, world/player clocks, peer packets and persisted time-wait objectives. */
class ClientGameTimeFlowTest extends QuestTestFixture {
    static GameServerPacketHandler dispatcher;
    World world;

    @dev.morphia.annotations.Entity(value = "players", useDiscriminator = false)
    static class SavedClockPlayer extends emu.grasscutter.game.player.Player {
        @dev.morphia.annotations.Transient dev.morphia.Datastore target;
        @dev.morphia.annotations.Transient final List<BasePacket> packets = new ArrayList<>();

        @dev.morphia.annotations.Transient
        final emu.grasscutter.game.player.TeamManager team =
                new emu.grasscutter.game.player.TeamManager(this);

        @Override
        public emu.grasscutter.game.player.TeamManager getTeamManager() {
            return team;
        }

        @Override
        public void save() {
            if (target != null) target.save(this);
        }

        @Override
        public void sendPacket(BasePacket packet) {
            packets.add(packet);
        }
    }

    @BeforeAll
    static void handlers() throws Exception {
        if (ScriptLoader.getEngine() == null) ScriptLoader.init();
        dispatcher = new GameServerPacketHandler(PacketHandler.class);
    }

    @BeforeEach
    void clock() throws Exception {
        world = player.getWorld();
        world.getPlayers().add(player);
        replaceField(World.class, world, "isPaused", true);
        player.updatePlayerGameTime(720000L);
        player.getSession().setPlayer(player);
        player.getSession().setState(GameSession.SessionState.ACTIVE);
        player.packets.clear();
    }

    byte[] request(long time, int client, boolean force) {
        return ClientSetGameTimeReq.newBuilder()
                .setGameTime((int) time)
                .setClientGameTime(client)
                .setIsForceSet(force)
                .build()
                .toByteArray();
    }

    void dispatch(long time, int client, boolean force, int sequence) throws Exception {
        dispatcher.handle(
                player.getSession(),
                PacketOpcodes.ClientSetGameTimeReq,
                PacketHead.newBuilder().setClientSequenceId(sequence).build().toByteArray(),
                request(time, client, force));
        drain();
    }

    BasePacket reply() {
        var responses =
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.ClientSetGameTimeRsp)
                        .toList();
        assertEquals(1, responses.size(), "The request must receive exactly one response");
        return responses.get(0);
    }

    ClientSetGameTimeRsp response() throws Exception {
        return ClientSetGameTimeRsp.parseFrom(reply().getData());
    }

    @Test
    void setsAbsoluteTotalMinutesAndEchoesClientTimeAndSequence() throws Exception {
        dispatch(3000, 45, false, 37);
        assertEquals(3000, world.getTotalGameTimeMinutes());
        assertEquals(3000000L, player.getPlayerGameTime());
        var rsp = response();
        assertEquals(0, rsp.getRetcode());
        assertEquals(3000, rsp.getGameTime());
        assertEquals(45, rsp.getClientGameTime());
        assertEquals(37, PacketHead.parseFrom(reply().getHeader()).getClientSequenceId());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 4294967295L})
    void unsignedBoundaryTimesDoNotBecomeNegativeOrOverflow(long minutes) throws Exception {
        dispatch(minutes, -1, false, 4);
        assertEquals(minutes, world.getTotalGameTimeMinutes());
        assertEquals(minutes * 1000L, player.getPlayerGameTime());
        assertEquals(minutes, Integer.toUnsignedLong(response().getGameTime()));
        assertEquals(4294967295L, Integer.toUnsignedLong(response().getClientGameTime()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void timeLockRejectsBothNormalAndForcedRequests(boolean force) throws Exception {
        world.lockTime(true);
        player.packets.clear();
        dispatch(3000, 45, force, 9);
        assertEquals(720, world.getTotalGameTimeMinutes());
        assertEquals(720000L, player.getPlayerGameTime());
        assertEquals(Retcode.RET_PLAYER_TIME_LOCKED_VALUE, response().getRetcode());
        assertEquals(45, response().getClientGameTime());
        assertEquals(720, response().getGameTime());
        assertEquals(1, player.packets.size());
    }

    @Test
    void emptyHeaderStillGetsAResponse() throws Exception {
        dispatcher.handle(
                player.getSession(),
                PacketOpcodes.ClientSetGameTimeReq,
                new byte[0],
                request(3000, 45, false));
        drain();
        assertEquals(0, response().getRetcode());
        assertEquals(0, PacketHead.parseFrom(reply().getHeader()).getClientSequenceId());
    }

    @Test
    void departedPlayerCannotChangeTheWorld() throws Exception {
        world.getPlayers().remove(player);
        dispatch(3000, 45, false, 9);
        assertEquals(Retcode.RET_FORBIDDEN_VALUE, response().getRetcode());
        assertEquals(720, world.getTotalGameTimeMinutes());
        assertEquals(720000L, player.getPlayerGameTime());
    }

    @Test
    void missingWorldReturnsFailureWithoutChangingSavedSoloTime() throws Exception {
        player.setWorld(null);
        try {
            dispatch(3000, 45, false, 9);
            assertEquals(Retcode.RET_FAIL_VALUE, response().getRetcode());
            assertEquals(720000L, player.getPlayerGameTime());
        } finally {
            player.setWorld(world);
        }
    }

    TestPlayer guest(int uid) {
        var guest = new TestPlayer();
        guest.setUid(uid);
        guest.server = player.server;
        guest.setWorld(world);
        guest.setScene(player.getScene());
        guest.updatePlayerGameTime(123000L);
        guest.setSession(
                new GameSession(player.server) {
                    @Override
                    public void send(BasePacket packet) {
                        guest.packets.add(packet);
                    }
                });
        guest.getSession().setPlayer(guest);
        guest.getSession().setState(GameSession.SessionState.ACTIVE);
        world.getPlayers().add(guest);
        return guest;
    }

    @Test
    void allCurrentPeersSeeTheWorldTimeWithoutOverwritingTheirSoloClocks() throws Exception {
        var peer = guest(10002);
        dispatch(3000, 45, false, 9);
        var updates =
                peer.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.PlayerGameTimeNotify)
                        .toList();
        assertEquals(1, updates.size());
        var update = PlayerGameTimeNotify.parseFrom(updates.get(0).getData());
        assertEquals(3000, update.getGameTime());
        assertEquals(10002, update.getUid());
        assertEquals(123000L, peer.getPlayerGameTime());
        assertEquals(3000000L, player.getPlayerGameTime());
    }

    @Test
    void aGuestRequestUpdatesTheHostClockAndOnlyRepliesToTheRequester() throws Exception {
        var peer = guest(10002);
        dispatcher.handle(
                peer.getSession(),
                PacketOpcodes.ClientSetGameTimeReq,
                new byte[0],
                request(3000, 45, false));
        drain();
        assertEquals(3000, world.getTotalGameTimeMinutes());
        assertEquals(3000000L, player.getPlayerGameTime());
        assertEquals(123000L, peer.getPlayerGameTime());
        assertTrue(
                player.packets.stream()
                        .noneMatch(p -> p.getOpcode() == PacketOpcodes.ClientSetGameTimeRsp));
        assertEquals(
                1,
                peer.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.ClientSetGameTimeRsp)
                        .count());
    }

    @Test
    void aStalePeerEntryCannotPreventTheHostFromReceivingAResponse() throws Exception {
        var peer = guest(10002);
        peer.setWorld(null);
        dispatch(3000, 45, false, 9);
        assertEquals(0, response().getRetcode());
        assertEquals(3000, world.getTotalGameTimeMinutes());
        assertTrue(peer.packets.isEmpty());
        assertEquals(123000L, peer.getPlayerGameTime());
    }

    @Test
    void updatedHostClockSurvivesARealMongoRoundTrip() throws Exception {
        store.getMapper().map(SavedClockPlayer.class);
        var host = new SavedClockPlayer();
        host.setUid(10003);
        host.setSession(
                new GameSession(player.server) {
                    @Override
                    public void send(BasePacket packet) {
                        host.packets.add(packet);
                    }
                });
        host.getSession().setPlayer(host);
        host.getSession().setState(GameSession.SessionState.ACTIVE);
        var other = new World(player.server, host);
        host.setWorld(other);
        other.getPlayers().add(host);
        replaceField(World.class, other, "isPaused", true);
        host.target = store;
        dispatcher.handle(
                host.getSession(),
                PacketOpcodes.ClientSetGameTimeReq,
                new byte[0],
                request(3000, 45, false));
        drain();
        var restored =
                store.find(SavedClockPlayer.class)
                        .filter(dev.morphia.query.experimental.filters.Filters.eq("id", 10003))
                        .first();
        assertNotNull(restored);
        assertEquals(3000000L, restored.getPlayerGameTime());
        assertEquals(
                1,
                host.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.ClientSetGameTimeRsp)
                        .count());
    }

    @Test
    void waitingForGameTimeObjectiveIsCompletedAndPersisted() throws Exception {
        var quest =
                controlled(
                        "[{type:'QUEST_CONTENT_GAME_TIME_TICK',param:[0],param_str:'18,20'}]",
                        "[]",
                        "LOGIC_AND");
        player.packets.clear();
        dispatch(19 * 60, 45, false, 9);
        assertEquals(QuestState.QUEST_STATE_FINISHED, quest.getState());
        assertEquals(QuestState.QUEST_STATE_FINISHED, stored(quest).getState());
        assertEquals(0, response().getRetcode());
    }

    @Test
    void malformedBodyOrHeaderDoesNotChangeTime() {
        dispatcher.handle(
                player.getSession(),
                PacketOpcodes.ClientSetGameTimeReq,
                new byte[0],
                new byte[] {0x28, (byte) 0x80});
        dispatcher.handle(
                player.getSession(),
                PacketOpcodes.ClientSetGameTimeReq,
                new byte[] {(byte) 0x80},
                request(3000, 45, false));
        assertEquals(720, world.getTotalGameTimeMinutes());
        assertEquals(720000L, player.getPlayerGameTime());
        assertTrue(player.packets.isEmpty());
    }
}
