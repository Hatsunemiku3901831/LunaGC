package emu.grasscutter.server.packet.recv;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.TakePlayerLevelRewardReqOuterClass.TakePlayerLevelRewardReq;
import org.junit.jupiter.api.Test;

class TakePlayerLevelRewardReqTest {
    @Test
    void claimUsesClientCommandAndLevelField() throws Exception {
        // Client type OMHILMLENCC, getter 0x1493bc7f0: mov ax, 0x1c99; ret.
        // Writer 0x1493bc580 writes tag 0x78 for the uint32 at object + 0x18.
        assertEquals(7321, PacketOpcodes.TakePlayerLevelRewardReq);
        byte[] wire = new byte[] {0x78, 10};
        assertEquals(10, TakePlayerLevelRewardReq.parseFrom(wire).getLevel());
        assertArrayEquals(wire, TakePlayerLevelRewardReq.newBuilder().setLevel(10).build().toByteArray());
    }
}
