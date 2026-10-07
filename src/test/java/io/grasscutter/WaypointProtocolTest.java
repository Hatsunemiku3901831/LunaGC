package io.grasscutter;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.ScenePointUnlockNotifyOuterClass.ScenePointUnlockNotify;
import emu.grasscutter.net.proto.UnlockTransPointReqOuterClass.UnlockTransPointReq;
import emu.grasscutter.server.packet.recv.HandlerUnlockTransPointReq;
import org.junit.jupiter.api.Test;

/** 7.1 waypoint activation uses the tags the client writer and reader actually emit. */
public final class WaypointProtocolTest {

    @Test
    public void unlockRequestIsRegistered() {
        // DMMJNICDOHM packet getter 0x14c898520 returns 9369.
        assertEquals(9369, PacketOpcodes.UnlockTransPointReq);
        assertEquals(9369, HandlerUnlockTransPointReq.class.getAnnotation(Opcodes.class).value());
    }

    @Test
    public void unlockRequestReadsSceneAndPoint() throws Exception {
        // Writer 0x14c898170: point tag 0x08, scene tag 0x60.
        UnlockTransPointReq req = UnlockTransPointReq.parseFrom(new byte[] {0x08, 7, 0x60, 3});

        assertEquals(7, req.getPointId());
        assertEquals(3, req.getSceneId());
    }

    @Test
    public void unlockNotifyUsesTheNativeSceneAndPointTags() {
        // Reader 0x15255c3e0: scene tag 0x10, packed point_list tag 0x7a.
        byte[] encoded =
                ScenePointUnlockNotify.newBuilder().setSceneId(3).addPointList(7).build().toByteArray();

        assertArrayEquals(new byte[] {0x10, 3, 0x7a, 1, 7}, encoded);
    }
}
