package emu.grasscutter.server.game;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.config.Configuration;
import java.net.InetSocketAddress;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import emu.grasscutter.net.proto.PingReqOuterClass.PingReq;
import emu.grasscutter.net.proto.PingRspOuterClass.PingRsp;
import java.util.*;

/** Run in the explicitly prepared isolated CWD; never binds a network transport. */
class GameServerFiniteBootTest {
    @Test
    void fullConstructorProbeRecordsTheActualBundledDataFallback() throws Exception {
        var config = Grasscutter.getConfig();
        assertTrue(Path.of(config.folderStructure.data).toAbsolutePath().startsWith(Path.of("").toAbsolutePath()));
        Configuration.GAME_INFO.m0.enabled = false;
        Configuration.GAME_INFO.oldSnapshot.enabled = false;
        Configuration.GAME_OPTIONS.watchGachaConfig = false;
        Grasscutter.setRunModeOverride(Grasscutter.ServerRunMode.HYBRID);
        try {
            var server = new GameServer(new InetSocketAddress("127.0.0.1", 0), false);
            assertNotNull(server.getDungeonSystem());
            assertTrue(server.getTaskMap().getHandlersAsList().size() > 0);
            System.out.println("FULL_BOOT_PROBE constructor=true dungeon=true periodicTasks="
                    + server.getTaskMap().getHandlersAsList().size() + " transportBound=false");
        } finally {
            for (var scheduler : org.quartz.impl.SchedulerRepository.getInstance().lookupAll())
                scheduler.shutdown(true);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void finiteConstructorDoesNotInitializeUnrelatedGameplaySystemsOrTasks(boolean old) throws Exception {
        var config = Grasscutter.getConfig();
        assertTrue(Path.of(config.folderStructure.data).toAbsolutePath().startsWith(Path.of("").toAbsolutePath()));
        Configuration.GAME_INFO.m0.enabled = !old;
        Configuration.GAME_INFO.oldSnapshot.enabled = old;
        Configuration.GAME_OPTIONS.watchGachaConfig = false;
        Grasscutter.setRunModeOverride(Grasscutter.ServerRunMode.HYBRID);
        try {
            var server = new GameServer(new InetSocketAddress("127.0.0.1", 0), false);
            assertNull(server.getDungeonSystem());
            assertNull(server.getGachaSystem());
            assertNull(server.getShopSystem());
            assertNull(server.getTaskMap());
            assertNull(server.getScheduler());
            assertNull(server.getTowerSystem());
            assertNull(server.getCrucibleMatchSystem());
            assertNull(server.getChatSystem());
            assertNotNull(server.getPacketHandler());
            assertNotNull(server.getPlayers());
            assertNotNull(server.getWorlds());
            assertTrue(org.quartz.impl.SchedulerRepository.getInstance().lookupAll().isEmpty());
            assertFalse(server.isTransportBound());
            server.onTick();
            assertEquals(1, server.tickStatus().completed());
            server.onServerShutdown();
        } finally {
            for (var scheduler : org.quartz.impl.SchedulerRepository.getInstance().lookupAll())
                scheduler.shutdown(true);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void finitePrefixEchoesKnownPingAndClosesUnsupportedRequestsBeforeParsing(boolean old) throws Exception {
        Grasscutter.getConfig();
        Configuration.GAME_INFO.m0.enabled = !old;
        Configuration.GAME_INFO.oldSnapshot.enabled = old;
        Grasscutter.setRunModeOverride(Grasscutter.ServerRunMode.HYBRID);
        var server = FiniteGameServerTestSupport.unbound();
        var session = new CapturingSession(server);
        long previousPing = session.getLastPingTime();
        server.getPacketHandler().handle(session, PacketOpcodes.PingReq,
                PacketHead.newBuilder().setClientSequenceId(7).build().toByteArray(),
                PingReq.newBuilder().setOILKPHNHCHB(0x1357).setAMLHAGPHMHN(11).build().toByteArray());
        assertEquals(1, session.sent.size());
        assertEquals(PacketOpcodes.PingRsp, session.sent.get(0).getOpcode());
        var response = PingRsp.parseFrom(session.sent.get(0).getData());
        assertEquals(0x1357, response.getOILKPHNHCHB());
        assertEquals(11, response.getAMLHAGPHMHN());
        assertEquals(0x1357, session.getClientTime());
        assertTrue(session.getLastPingTime() >= previousPing);
        assertFalse(session.isLoginRevoked());
        server.getPacketHandler().handle(session, 0xffff, new byte[] {0}, new byte[] {0});
        assertTrue(session.isLoginRevoked());
        assertEquals(1, session.sent.size(), "unsupported traffic cannot emit a legacy success response");
        server.onServerShutdown();
    }

    private static final class CapturingSession extends GameSession {
        final List<BasePacket> sent = new ArrayList<>();
        CapturingSession(GameServer server) { super(server); }
        @Override public void send(BasePacket packet) { sent.add(packet); }
    }
}
