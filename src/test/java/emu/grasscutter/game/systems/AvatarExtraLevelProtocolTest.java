package emu.grasscutter.game.systems;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto._AvatarExtraLevelNotifyOuterClass._AvatarExtraLevelNotify;
import emu.grasscutter.net.proto._AvatarExtraLevelUpgradeReqOuterClass._AvatarExtraLevelUpgradeReq;
import emu.grasscutter.net.proto._AvatarExtraLevelUpgradeRspOuterClass._AvatarExtraLevelUpgradeRsp;
import emu.grasscutter.server.game.GameServerPacketHandler;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AvatarExtraLevelProtocolTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
    }

    @Test
    void nativeExtraLevelRequestHasARegisteredHandler() throws Exception {
        // BJFOLACFLPH's literal getter 0x150885070 returns 0x1eeb.
        assertEquals(7915, PacketOpcodes._AvatarExtraLevelUpgradeReq);
        var registry = new GameServerPacketHandler(PacketHandler.class);
        var field = GameServerPacketHandler.class.getDeclaredField("handlers");
        field.setAccessible(true);
        var handlers = (Int2ObjectMap<?>) field.get(registry);
        assertNotNull(handlers.get(7915), "The native cap breakthrough request must be routed");
    }

    @Test
    void nativeRequestContainsTheRequiredCurrentLevelAndAvatarGuid() throws Exception {
        // Writer 0x150884e20 emits tags 0x40 / 0x60; UI sender uses row DEJBOJMHHJB.
        var request =
                _AvatarExtraLevelUpgradeReq.parseFrom(new byte[] {0x40, 90, 0x60, (byte) 0xac, 2});
        assertEquals(300, request.getAvatarGuid());
        assertEquals(90, request.getField(request.getDescriptorForType().findFieldByNumber(8)));
    }

    @Test
    void nativeResponseAndNotificationFieldsArePreserved() throws Exception {
        // Readers 0x147eacbc0 / 0x14d42ffe0; packet getters return 5633 / 3528.
        assertEquals(5633, PacketOpcodes._AvatarExtraLevelUpgradeRsp);
        assertEquals(3528, PacketOpcodes._AvatarExtraLevelNotify);
        var response =
                _AvatarExtraLevelUpgradeRsp.parseFrom(
                        new byte[] {0x10, 95, 0x18, 90, 0x78, (byte) 0xac, 2});
        assertEquals(0, response.getRetcode());
        assertEquals(300, response.getAvatarGuid());
        assertEquals(90, response.getOldLevel());
        assertEquals(95, response.getCurLevel());
        var notify =
                _AvatarExtraLevelNotify.parseFrom(new byte[] {8, 95, 0x10, (byte) 0xac, 2, 0x58, 42});
        assertEquals(95, notify.getExtraLevel());
        assertEquals(300, notify.getAvatarGuid());
        assertEquals(42, notify.getEntityId());
    }
}
