package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.CombatInvocationsNotifyOuterClass.CombatInvocationsNotify;
import emu.grasscutter.net.proto.CombatInvokeEntryOuterClass.CombatInvokeEntry;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.game.GameSessionManager.KcpTunnel;
import emu.grasscutter.server.packet.send.PacketCombatInvocationsNotify;
import emu.grasscutter.server.packet.send.PacketEvtBeingHealedNotify;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class CombatSessionSequenceTest {
    private static final class Wire implements KcpTunnel {
        final List<byte[]> frames = new CopyOnWriteArrayList<>();
        @Override public InetSocketAddress getAddress() { return new InetSocketAddress("127.0.0.1", 1); }
        @Override public void writeData(byte[] bytes) { frames.add(bytes); }
        @Override public void close() {}
        @Override public int getSrtt() { return 0; }
        GameSession connect() {
            emu.grasscutter.Grasscutter.getConfig();
            var session = new GameSession(null);
            session.onConnected(this);
            return session;
        }
    }

    private static CombatInvocationsNotify body(byte[] frame) throws Exception {
        var buffer = ByteBuffer.wrap(frame);
        buffer.position(4);
        int headerLength = Short.toUnsignedInt(buffer.getShort());
        int bodyLength = buffer.getInt();
        var payload = new byte[bodyLength];
        buffer.position(10 + headerLength);
        buffer.get(payload);
        return CombatInvocationsNotify.parseFrom(payload);
    }

    @Test
    void everyCombatConstructionPathIsStampedAtTheWire() throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var entry = CombatInvokeEntry.getDefaultInstance();
            var packets = List.of(new PacketCombatInvocationsNotify(entry),
                    new PacketCombatInvocationsNotify(List.of(entry)),
                    new PacketEvtBeingHealedNotify(fixture.entity, fixture.entity, 3, 2));
            var wire = new Wire();
            var session = wire.connect();
            for (var packet : packets) {
                packet.shouldEncrypt = false;
                session.send(packet);
            }
            assertEquals(3, wire.frames.size());
            for (int i = 0; i < 3; i++) {
                assertEquals(i + 1, body(wire.frames.get(i)).getClientSequenceId());
                assertEquals(1, body(wire.frames.get(i)).getInvokeListCount());
                assertEquals(0, CombatInvocationsNotify.parseFrom(packets.get(i).getData()).getClientSequenceId());
                assertNull(packets.get(i).getHeader(), "broadcast source must remain reusable");
            }
        }
    }

    @Test
    void twoRecipientsKeepIndependentSequencesForSharedBroadcasts() throws Exception {
        var packet = new PacketCombatInvocationsNotify(CombatInvokeEntry.getDefaultInstance());
        packet.shouldEncrypt = false;
        var first = new Wire();
        var second = new Wire();
        var a = first.connect();
        var b = second.connect();
        a.send(packet); a.send(packet); b.send(packet); a.send(packet); b.send(packet);
        assertEquals(List.of(1, 2, 3), first.frames.stream().map(frame -> {
            try { return body(frame).getClientSequenceId(); } catch (Exception e) { throw new AssertionError(e); }
        }).toList());
        assertEquals(1, body(second.frames.get(0)).getClientSequenceId());
        assertEquals(2, body(second.frames.get(1)).getClientSequenceId());
    }

    @Test
    void concurrentSendsReachTheTunnelInSequenceOrder() throws Exception {
        var wire = new Wire();
        var session = wire.connect();
        var packet = new PacketCombatInvocationsNotify(CombatInvokeEntry.getDefaultInstance());
        packet.shouldEncrypt = false;
        var pool = Executors.newFixedThreadPool(8);
        try {
            var start = new CountDownLatch(1);
            var pending = new ArrayList<Future<?>>();
            for (int i = 0; i < 40; i++) pending.add(pool.submit(() -> {
                try { start.await(); session.send(packet); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
            }));
            start.countDown();
            for (var task : pending) task.get(5, TimeUnit.SECONDS);
            assertEquals(40, wire.frames.size());
            for (int i = 0; i < 40; i++) assertEquals(i + 1, body(wire.frames.get(i)).getClientSequenceId());
            assertEquals(40, session.getPacketsSent());
        } finally { pool.shutdownNow(); }
    }
}
