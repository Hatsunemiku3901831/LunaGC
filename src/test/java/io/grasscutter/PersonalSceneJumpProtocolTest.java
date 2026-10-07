package io.grasscutter;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.PersonalSceneJumpReqOuterClass.PersonalSceneJumpReq;
import emu.grasscutter.server.packet.recv.HandlerPersonalSceneJumpReq;
import org.junit.jupiter.api.Test;

/** 7.1 building doors send one point id on command 13. */
public final class PersonalSceneJumpProtocolTest {

    @Test
    public void jumpRequestIsRegistered() {
        // EJKBEPMLFBH packet getter 0x14bcffb60 returns 13.
        assertEquals(13, PacketOpcodes.PersonalSceneJumpReq);
        assertEquals(13, HandlerPersonalSceneJumpReq.class.getAnnotation(Opcodes.class).value());
    }

    @Test
    public void jumpRequestReadsTheKnightsDoorPoint() throws Exception {
        // Writer 0x14bcffa40 stores the uint32 at object +0x18 under tag 0x58.
        // Scene 3 point 27 is the Knights of Favonius door.
        PersonalSceneJumpReq req = PersonalSceneJumpReq.parseFrom(new byte[] {0x58, 27});

        assertEquals(27, req.getPointId());
        assertArrayEquals(
                new byte[] {0x58, 27},
                PersonalSceneJumpReq.newBuilder().setPointId(27).build().toByteArray());
    }
}
