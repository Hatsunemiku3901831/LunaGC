package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.UnknownFieldSet;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.TowerAllDataReqOuterClass.TowerAllDataReq;
import emu.grasscutter.net.proto.TowerAllDataRspOuterClass.TowerAllDataRsp;
import emu.grasscutter.net.proto.TowerCurLevelRecordOuterClass.TowerCurLevelRecord;
import emu.grasscutter.server.packet.recv.HandlerTowerAllDataReq;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Native 7.1 reader/writer tags; see docs/SPIRAL_ABYSS_ENTRY_PROTOCOL_71.md. */
class TowerEntryProtocol71Test {
    @Test
    void openingTheNativePageHasARegisteredRequest() throws Exception {
        assertEquals(27826, PacketOpcodes.TowerAllDataReq);
        assertEquals(27826, HandlerTowerAllDataReq.class.getAnnotation(Opcodes.class).value());
        assertTrue(TowerAllDataReq.parseFrom(new byte[] {0x70, 1}).getIsInteract());
    }

    @Test
    void rotationAndOpenTimesUseTheClientReaderTags() throws Exception {
        var reply = TowerAllDataRsp.newBuilder().setTowerScheduleId(62)
                .setNextScheduleChangeTime(1909015200).setScheduleStartTime(1650000000)
                .putFloorOpenTimeMap(1053, 1650000000).build();
        var fields = UnknownFieldSet.parseFrom(reply.toByteArray());
        assertEquals(List.of(62L), fields.getField(4).getVarintList());
        assertEquals(List.of(1909015200L), fields.getField(6).getVarintList());
        assertEquals(List.of(1650000000L), fields.getField(1330).getVarintList());
        var open = UnknownFieldSet.parseFrom(fields.getField(14).getLengthDelimitedList().get(0));
        assertEquals(List.of(1053L), open.getField(1).getVarintList());
        assertEquals(List.of(1650000000L), open.getField(2).getVarintList());
        assertTrue(fields.asMap().keySet().stream().allMatch(tag -> tag < 50000));
    }

    @Test
    void currentFloorAndChamberSurviveTheNativeReader() throws Exception {
        var record = TowerCurLevelRecord.parseFrom(new byte[] {0x48, (byte) 0x9d, 8, 0x50, 2});
        assertEquals(1053, record.getCurFloorId());
        assertEquals(2, record.getCurLevelIndex());
        assertTrue(record.getUnknownFields().asMap().isEmpty());
    }

    @Test
    void nativeRequestReachesTheRealDispatcherAndOutgoingFrame() throws Exception {
        try (var environment = new AbilityTestFixture()) {
            var server = environment.scene.getWorld().getServer();
            var tower = new emu.grasscutter.game.tower.TowerSystem(server) {
                @Override public emu.grasscutter.data.excels.tower.TowerScheduleData getCurrentTowerScheduleData() {
                    return new com.google.gson.Gson().fromJson("{scheduleId:62}",
                            emu.grasscutter.data.excels.tower.TowerScheduleData.class);
                }
                @Override public List<Integer> getScheduleFloors() { return List.of(1044, 1045, 1052, 1053); }
                @Override public java.util.Date getScheduleStartTime() { return new java.util.Date(1650000000000L); }
                @Override public java.util.Date getNextScheduleChangeTime() { return new java.util.Date(1909015200000L); }
            };
            var field = emu.grasscutter.server.game.GameServer.class.getDeclaredField("towerSystem");
            field.setAccessible(true);
            field.set(server, tower);
            var player = new emu.grasscutter.game.player.Player() {
                @Override public emu.grasscutter.game.tower.TowerManager getTowerManager() {
                    return new emu.grasscutter.game.tower.TowerManager(this) {
                        @Override public java.util.Map<Integer, emu.grasscutter.game.tower.TowerLevelRecord> getRecordMap() {
                            return java.util.Map.of(1008, new emu.grasscutter.game.tower.TowerLevelRecord(1008).setLevelStars(22, 3));
                        }
                        @Override public boolean canEnterScheduleFloor() { return true; }
                    };
                }
            };
            var frames = new java.util.ArrayList<byte[]>();
            var session = new emu.grasscutter.server.game.GameSession(server);
            session.onConnected(new emu.grasscutter.server.game.GameSessionManager.KcpTunnel() {
                @Override public java.net.InetSocketAddress getAddress() { return new java.net.InetSocketAddress("127.0.0.1", 1); }
                @Override public void writeData(byte[] bytes) { frames.add(bytes); }
                @Override public void close() {}
                @Override public int getSrtt() { return 0; }
            });
            session.setPlayer(player);
            session.setState(emu.grasscutter.server.game.GameSession.SessionState.ACTIVE);
            var registry = new emu.grasscutter.server.game.GameServerPacketHandler(emu.grasscutter.net.packet.PacketHandler.class);
            var game = emu.grasscutter.Grasscutter.getConfig().server.game;
            boolean encrypted = game.useXorEncryption;
            try {
                // This fixture has no token exchange and therefore no dispatch/session key.
                game.useXorEncryption = false;
                registry.handle(session, 27826, new byte[0], new byte[] {0x70, 1});
            } finally {
                game.useXorEncryption = encrypted;
            }
            assertEquals(1, frames.size(), "registered handler must actually write a response");
            var frame = frames.get(0);
            var buffer = java.nio.ByteBuffer.wrap(frame);
            assertEquals(17767, Short.toUnsignedInt(buffer.getShort()));
            assertEquals(21818, Short.toUnsignedInt(buffer.getShort()));
            int headerLength = Short.toUnsignedInt(buffer.getShort());
            int payloadLength = buffer.getInt();
            buffer.position(10 + headerLength);
            var payload = new byte[payloadLength];
            buffer.get(payload);
            var reply = TowerAllDataRsp.parseFrom(payload);
            assertEquals(62, reply.getTowerScheduleId());
            assertEquals(4, reply.getFloorOpenTimeMapCount());
            assertEquals(1, reply.getTowerFloorRecordListCount());
            assertTrue(reply.getIsFinishedEntranceFloor());
            assertTrue(UnknownFieldSet.parseFrom(payload).hasField(14));

            // CFHJLLMBCPO getter 0x14af7d740; an empty notify must still reach the wire.
            var swap = new emu.grasscutter.server.packet.send.PacketTowerMiddleLevelChangeTeamNotify();
            assertEquals(28798, swap.getOpcode());
            swap.shouldEncrypt = false;
            session.send(swap);
            assertEquals(2, frames.size());
            assertEquals(28798, Short.toUnsignedInt(java.nio.ByteBuffer.wrap(frames.get(1)).getShort(2)));
        }
    }
}
