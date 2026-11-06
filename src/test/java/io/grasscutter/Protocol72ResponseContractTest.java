package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.DynamicMessage;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.config.Configuration;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.packet.PacketOpcodesUtils;
import emu.grasscutter.net.proto.GetPlayerTokenRspOuterClass.GetPlayerTokenRsp;
import emu.grasscutter.net.proto.PlayerLoginRspOuterClass.PlayerLoginRsp;
import emu.grasscutter.net.proto.QueryCurrRegionHttpRspOuterClass.QueryCurrRegionHttpRsp;
import emu.grasscutter.net.proto.RegionInfoOuterClass.RegionInfo;
import emu.grasscutter.net.proto.ResVersionConfigOuterClass.ResVersionConfig;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketGetPlayerTokenRsp;
import emu.grasscutter.server.packet.send.PacketPlayerLoginRsp;
import java.net.InetSocketAddress;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Fixed target wire and bounded native consumers, not real-client acceptance. */
class Protocol72ResponseContractTest {
    @BeforeAll
    static void initializeConfiguration() {
        Grasscutter.getConfig();
    }

    @Test
    void threeLoginCommandsHaveVerifiedCanonicalBindings() {
        assertEquals(27808, PacketOpcodes.GetPlayerTokenRsp);
        assertEquals(23172, PacketOpcodes.PlayerLoginReq);
        assertEquals(20835, PacketOpcodes.PlayerLoginRsp);
        assertEquals("GetPlayerTokenRsp", PacketOpcodesUtils.getOpcodeName(27808));
        assertEquals("PlayerLoginReq", PacketOpcodesUtils.getOpcodeName(23172));
        assertEquals("PlayerLoginRsp", PacketOpcodesUtils.getOpcodeName(20835));
    }

    @Test
    void clearTransportSuccessContainsOnlyVerifiedIdentityFields() throws Exception {
        boolean old = Configuration.GAME_INFO.useXorEncryption;
        Configuration.GAME_INFO.useXorEncryption = false;
        try {
            BasePacket packet = construct(new Class<?>[] {GameSession.class}, session());
            assertArrayEquals(hex("32017448ac02"), packet.getData());
            assertEquals(Set.of(6, 9), tags(GetPlayerTokenRsp.getDescriptor(), packet));
            assertTrue(packet.useDispatchKey());
        } finally {
            Configuration.GAME_INFO.useXorEncryption = old;
        }
    }

    @Test
    void rsaSuccessHasFourVerifiedFieldsAndNoGuessedKeyOrVersionField() throws Exception {
        BasePacket packet = construct(new Class<?>[] {GameSession.class, String.class, String.class}, session(), "r", "s");
        assertArrayEquals(hex("32017448ac02820d0173ea1d0172"), packet.getData());
        assertEquals(Set.of(6, 9, 208, 477), tags(GetPlayerTokenRsp.getDescriptor(), packet));
        assertTrue(packet.useDispatchKey());
    }

    @Test
    void refusalHasOnlyVerifiedRetcode() throws Exception {
        BasePacket packet = construct(new Class<?>[] {GameSession.class, int.class}, session(), 21);
        assertArrayEquals(hex("7015"), packet.getData());
        assertEquals(Set.of(14), tags(GetPlayerTokenRsp.getDescriptor(), packet));
        var negative = DynamicMessage.parseFrom(GetPlayerTokenRsp.getDescriptor(), hex("70ffffffffffffffffff01"));
        assertEquals(-1, negative.getField(negative.getDescriptorForType().findFieldByNumber(14)));
    }

    @Test
    void loginResourceConfigProjectsOnlyFourNativeConsumers() throws Exception {
        var cache = PacketPlayerLoginRsp.class.getDeclaredField("regionCache");
        cache.setAccessible(true);
        var mode = Grasscutter.class.getDeclaredField("runModeOverride");
        mode.setAccessible(true);
        Object oldCache = cache.get(null), oldMode = mode.get(null);
        try {
            mode.set(null, Grasscutter.ServerRunMode.GAME_ONLY);
            var source = ResVersionConfig.newBuilder().setVersion(-1).setMd5("M")
                    .setReleaseTotalSize("S").setVersionSuffix("V")
                    .setRelogin(true).setBranch("B").setNextScriptVersion("N").build();
            cache.set(null, QueryCurrRegionHttpRsp.newBuilder().setRegionInfo(
                    RegionInfo.newBuilder().setResVersionConfig(source)).build());
            var packet = new PacketPlayerLoginRsp(session());
            assertArrayEquals(hex("ea030f08ffffffff0f1a014d2201532a0156"), packet.getData());
            assertEquals(Set.of(61), tags(PlayerLoginRsp.getDescriptor(), packet));
            var parsed = DynamicMessage.parseFrom(PlayerLoginRsp.getDescriptor(), packet.getData());
            var nested = (DynamicMessage) parsed.getField(parsed.getDescriptorForType().findFieldByNumber(61));
            assertEquals(Set.of(1, 3, 4, 5), nested.getAllFields().keySet().stream()
                    .map(f -> f.getNumber()).collect(Collectors.toSet()));
        } finally {
            cache.set(null, oldCache);
            mode.set(null, oldMode);
        }
    }

    private static BasePacket construct(Class<?>[] parameters, Object... arguments) throws Exception {
        return PacketGetPlayerTokenRsp.class.getConstructor(parameters).newInstance(arguments);
    }

    private static Set<Integer> tags(com.google.protobuf.Descriptors.Descriptor descriptor, BasePacket packet) throws Exception {
        var parsed = DynamicMessage.parseFrom(descriptor, packet.getData());
        assertTrue(parsed.getUnknownFields().asMap().isEmpty());
        return parsed.getAllFields().keySet().stream().map(f -> f.getNumber()).collect(Collectors.toSet());
    }

    private static GameSession session() {
        var player = new Player();
        player.setUid(300);
        var account = new Account();
        account.setId("9007199254740993");
        account.setToken("t");
        var session = new GameSession(null) {
            @Override public Player getPlayer() { return player; }
            @Override public InetSocketAddress getAddress() { return new InetSocketAddress("127.0.0.1", 22101); }
        };
        session.setAccount(account);
        return session;
    }

    private static byte[] hex(String value) {
        return HexFormat.of().parseHex(value);
    }
}
