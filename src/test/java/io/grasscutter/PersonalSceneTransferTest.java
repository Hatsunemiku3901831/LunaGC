package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.ScenePointEntry;
import emu.grasscutter.data.common.PointData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.EnterReason;
import emu.grasscutter.game.world.data.TeleportProperties;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.PersonalSceneJumpReqOuterClass.PersonalSceneJumpReq;
import emu.grasscutter.net.proto.PersonalSceneJumpRspOuterClass.PersonalSceneJumpRsp;
import emu.grasscutter.net.proto.EnterTypeOuterClass.EnterType;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.recv.HandlerPersonalSceneJumpReq;
import emu.grasscutter.utils.JsonUtils;
import org.junit.jupiter.api.Test;

class PersonalSceneTransferTest {
    private void check(boolean success) throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var point = JsonUtils.decode("{\"tranSceneId\":2001,\"tranPos\":{\"x\":1,\"y\":2,\"z\":3},"
                    + "\"tranRot\":{\"x\":0,\"y\":90,\"z\":0}}", PointData.class);
            class TransferWorld extends AbilityTestFixture.TestWorld {
                TeleportProperties received;
                TransferWorld() { super(fixture.scene.getWorld().getServer(), fixture.player); }
                @Override public boolean transferPlayerToScene(Player player, TeleportProperties properties) {
                    received = properties;
                    return success;
                }
            }
            var world = new TransferWorld();
            fixture.player.setWorld(world);
            var session = new GameSession(null) {
                @Override public Player getPlayer() { return fixture.player; }
                @Override public void send(BasePacket packet) { fixture.player.packets.add(packet); }
            };
            int key = (fixture.player.getSceneId() << 16) + 123;
            var previous = GameData.getScenePointEntryMap().put(key, new ScenePointEntry(fixture.player.getSceneId(), point));
            try {
                new HandlerPersonalSceneJumpReq().handle(session, new byte[0],
                        PersonalSceneJumpReq.newBuilder().setPointId(123).build().toByteArray());
                assertNotNull(world.received);
                assertEquals(EnterReason.PersonalScene, world.received.getEnterReason());
                assertEquals(EnterType.EnterType_ENTER_JUMP, world.received.getEnterType());
                assertNotNull(world.received.getTeleportRot());
                assertEquals(90, world.received.getTeleportRot().getY());
                world.received.getTeleportTo().setX(99);
                assertEquals(1, point.getTranPos().getX(), "resource positions must be cloned");
                var response = PersonalSceneJumpRsp.parseFrom(fixture.player.packets.get(0).getData());
                assertEquals(success ? 0 : Retcode.RET_ENTER_SCENE_FAIL.getNumber(), response.getRetcode());
            } finally {
                if (previous == null) GameData.getScenePointEntryMap().remove(key);
                else GameData.getScenePointEntryMap().put(key, previous);
            }
        }
    }
    @Test void entryUsesPersonalSceneReasonAndResourceRotation() throws Exception { check(true); }
    @Test void canceledTransferDoesNotReportSuccess() throws Exception { check(false); }
}
