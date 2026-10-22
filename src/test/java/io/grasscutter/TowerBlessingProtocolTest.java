package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketOpcodes;
import org.junit.jupiter.api.Test;

class TowerBlessingProtocolTest {
    @Test
    void selectionUsesTheNativeSevenOneWireTags() throws Exception {
        var request =
                emu.grasscutter.net.proto.TowerBuffSelectReqOuterClass.TowerBuffSelectReq.parseFrom(
                        new byte[] {0x60, 28});
        assertEquals(28, request.getTowerBuffId());
        var response = new emu.grasscutter.server.packet.send.PacketTowerBuffSelectRsp(28, false);
        var fields = com.google.protobuf.UnknownFieldSet.parseFrom(response.getData());
        assertEquals(java.util.List.of(28L), fields.getField(4).getVarintList());
        assertEquals(java.util.List.of(-1L), fields.getField(15).getVarintList());
        assertEquals(9661, response.getOpcode());
    }

    @Test
    void nativeBlessingSelectionHasARegisteredHandler() throws Exception {
        var handler = Class.forName("emu.grasscutter.server.packet.recv.HandlerTowerBuffSelectReq");
        assertEquals(PacketOpcodes.TowerBuffSelectReq, handler.getAnnotation(Opcodes.class).value());
        assertEquals(4779, handler.getAnnotation(Opcodes.class).value());
    }

    @Test
    void nativeSelectionReachesTheActualDispatcherAndResponseFrame() throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var server = fixture.scene.getWorld().getServer();
            var frames = new java.util.ArrayList<byte[]>();
            var session = new emu.grasscutter.server.game.GameSession(server);
            session.onConnected(
                    new emu.grasscutter.server.game.GameSessionManager.KcpTunnel() {
                        @Override
                        public java.net.InetSocketAddress getAddress() {
                            return new java.net.InetSocketAddress("127.0.0.1", 1);
                        }

                        @Override
                        public void writeData(byte[] bytes) {
                            frames.add(bytes);
                        }

                        @Override
                        public void close() {}

                        @Override
                        public int getSrtt() {
                            return 0;
                        }
                    });
            session.setPlayer(fixture.player);
            session.setState(emu.grasscutter.server.game.GameSession.SessionState.ACTIVE);
            var registry =
                    new emu.grasscutter.server.game.GameServerPacketHandler(
                            emu.grasscutter.net.packet.PacketHandler.class);
            var config = emu.grasscutter.Grasscutter.getConfig().server.game;
            boolean encryption = config.useXorEncryption;
            try {
                config.useXorEncryption = false;
                registry.handle(session, 4779, new byte[0], new byte[] {0x60, 28});
            } finally {
                config.useXorEncryption = encryption;
            }
            assertEquals(1, frames.size());
            var buffer = java.nio.ByteBuffer.wrap(frames.get(0));
            assertEquals(17767, Short.toUnsignedInt(buffer.getShort()));
            assertEquals(9661, Short.toUnsignedInt(buffer.getShort()));
            int header = Short.toUnsignedInt(buffer.getShort());
            int size = buffer.getInt();
            buffer.position(10 + header);
            byte[] payload = new byte[size];
            buffer.get(payload);
            var reply =
                    emu.grasscutter.net.proto.TowerBuffSelectRspOuterClass.TowerBuffSelectRsp.parseFrom(
                            payload);
            assertEquals(28, reply.getTowerBuffId());
            assertEquals(
                    -1,
                    reply.getRetcode(),
                    "out-of-dungeon selection must be rejected on the real send path");
        }
    }
}
