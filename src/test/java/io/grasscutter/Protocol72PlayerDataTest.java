package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.UnknownFieldSet;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.packet.PacketOpcodesUtils;
import emu.grasscutter.net.proto.PlayerDataNotifyOuterClass.PlayerDataNotify;
import emu.grasscutter.server.packet.send.PacketPlayerDataNotify;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Native reader/consumer vectors, separate from generated encoders and live acceptance. */
class Protocol72PlayerDataTest {
    @BeforeAll static void initializeConfiguration() { Grasscutter.getConfig(); }

    @Test
    void canonicalCommandIsTheVerifiedPlayerInitializationRoute() {
        assertEquals(7193, PacketOpcodes.PlayerDataNotify);
        assertEquals("PlayerDataNotify", PacketOpcodesUtils.getOpcodeName(7193));
    }

    @Test
    void completeTwoTypeRawClosurePreservesUnknownNamesAndOneof() {
        var root = PlayerDataNotify.getDescriptor();
        Map<Integer, String> names = Map.of(5, "PLMNNCGEMIN", 6, "prop_map", 7, "OMFPDOKCLNL",
                8, "nickname", 13, "server_time");
        assertEquals(names.keySet(), root.getFields().stream().map(Descriptors.FieldDescriptor::getNumber)
                .collect(Collectors.toSet()));
        names.forEach((tag, name) -> assertEquals(name, root.findFieldByNumber(tag).getName()));
        var value = valueDescriptor();
        assertEquals(4, value.getFields().size());
        assertEquals("property_type", value.findFieldByNumber(1).getName());
        assertEquals("int64_value", value.findFieldByNumber(2).getName());
        assertEquals("BNJKCHONGGE_float", value.findFieldByNumber(3).getName());
        assertEquals("AEJAFPNBBHO", value.findFieldByNumber(4).getName());
        assertSame(value.findFieldByNumber(2).getContainingOneof(), value.findFieldByNumber(3).getContainingOneof());
        assertNotNull(value.findFieldByNumber(2).getContainingOneof());
        assertNull(value.findFieldByNumber(4).getContainingOneof());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 60})
    void actualSenderOnlyProjectsNicknameTimeAndVerifiedLevel(int level) throws Exception {
        var player = new NamedPlayer("Tester");
        player.getProperties().put(10013, level);
        player.getProperties().put(10035, 123456789);
        player.getProperties().put(424242, 777);
        long before = System.currentTimeMillis();
        var packet = new PacketPlayerDataNotify(player);
        long after = System.currentTimeMillis();
        assertEquals(7193, packet.getOpcode());
        var raw = UnknownFieldSet.parseFrom(packet.getData()).asMap();
        assertEquals(Set.of(6, 8, 13), raw.keySet());
        assertEquals("Tester", raw.get(8).getLengthDelimitedList().get(0).toStringUtf8());
        long time = raw.get(13).getVarintList().get(0);
        assertTrue(time >= before && time <= after);
        var parsed = PlayerDataNotify.parseFrom(packet.getData());
        assertEquals(Set.of(10013), parsed.getPropMapMap().keySet());
        Message value = parsed.getPropMapOrThrow(10013);
        assertEquals(10013, value.getField(value.getDescriptorForType().findFieldByNumber(1)));
        assertEquals((long) level, value.getField(value.getDescriptorForType().findFieldByNumber(2)));
        var childWire = raw.get(6).getLengthDelimitedList().get(0);
        var entry = UnknownFieldSet.parseFrom(childWire).asMap();
        var child = UnknownFieldSet.parseFrom(entry.get(2).getLengthDelimitedList().get(0)).asMap();
        assertEquals(Set.of(1, 2), child.keySet());
        assertEquals((long) level, child.get(2).getVarintList().get(0));
        assertEquals(777, player.getProperties().get(424242));
        assertEquals(123456789, player.getProperties().get(10035));
        System.out.printf("CN72_PLAYER_DATA level=%d time=%d wire=%s%n", level, time,
                HexFormat.of().formatHex(packet.getData()));
    }

    @Test
    void independentNativeMapVectorKeepsNegativeInt64() throws Exception {
        var parsed = PlayerDataNotify.parseFrom(hex("32110807120d082a10ffffffffffffffffff01"));
        Message value = parsed.getPropMapOrThrow(7);
        assertEquals(42, value.getField(value.getDescriptorForType().findFieldByNumber(1)));
        assertEquals(-1L, value.getField(value.getDescriptorForType().findFieldByNumber(2)));
    }

    @Test
    void independentOuterVectorRetainsHigh64AndRawUnknownScalars() throws Exception {
        var parsed = DynamicMessage.parseFrom(PlayerDataNotify.getDescriptor(),
                hex("280138014203416263688180808080808010"));
        var descriptor = parsed.getDescriptorForType();
        assertEquals(1, parsed.getField(descriptor.findFieldByNumber(5)));
        assertEquals(true, parsed.getField(descriptor.findFieldByNumber(7)));
        assertEquals("Abc", parsed.getField(descriptor.findFieldByNumber(8)));
        assertEquals(9007199254740993L, parsed.getField(descriptor.findFieldByNumber(13)));
        assertTrue(parsed.getUnknownFields().asMap().isEmpty());
    }

    @Test
    void oneofExplicitZeroAndFloatSwitchNeverUseIndependentTag4() throws Exception {
        var value = valueDescriptor();
        var zero = DynamicMessage.parseFrom(value, hex("1000"));
        assertTrue(zero.hasField(value.findFieldByNumber(2)));
        assertArrayEquals(hex("1000"), zero.toByteArray());
        var switched = DynamicMessage.parseFrom(value, hex("10011d0000a0bf2063"));
        assertFalse(switched.hasField(value.findFieldByNumber(2)));
        assertEquals(-1.25f, switched.getField(value.findFieldByNumber(3)));
        assertEquals(99L, switched.getField(value.findFieldByNumber(4)));
    }

    @Test
    void duplicateMapKeyReplacesTheChildRatherThanMergingOldFields() throws Exception {
        var parsed = PlayerDataNotify.parseFrom(hex(
                "32110807120d082a10ffffffffffffffffff01" + "3209080712051d00002040"));
        Message value = parsed.getPropMapOrThrow(7);
        var descriptor = value.getDescriptorForType();
        assertEquals(0, value.getField(descriptor.findFieldByNumber(1)));
        assertFalse(value.hasField(descriptor.findFieldByNumber(2)));
        assertEquals(2.5f, value.getField(descriptor.findFieldByNumber(3)));
    }

    @Test
    void unicodeNicknameUsesTheVerifiedStringWireSlot() throws Exception {
        var player = new NamedPlayer("入场🌙\0");
        var packet = new PacketPlayerDataNotify(player);
        var raw = UnknownFieldSet.parseFrom(packet.getData()).asMap();
        assertEquals("入场🌙\0", raw.get(8).getLengthDelimitedList().get(0).toStringUtf8());
        assertFalse(raw.containsKey(5));
        assertFalse(raw.containsKey(7));
    }

    @Test
    void truncatedNestedPayloadIsRejectedAndWrongWireIsRetainedAsUnknown() throws Exception {
        assertThrows(InvalidProtocolBufferException.class,
                () -> PlayerDataNotify.parseFrom(hex("32050801")));
        var parsed = PlayerDataNotify.parseFrom(hex("3001"));
        assertTrue(parsed.getUnknownFields().hasField(6));
        assertEquals(0, parsed.getPropMapCount());
    }

    private static Descriptors.Descriptor valueDescriptor() {
        return PlayerDataNotify.getDescriptor().findFieldByNumber(6).getMessageType()
                .findFieldByNumber(2).getMessageType();
    }

    private static byte[] hex(String wire) { return HexFormat.of().parseHex(wire); }

    private static final class NamedPlayer extends Player {
        private final String nickname;
        NamedPlayer(String nickname) { this.nickname = nickname; }
        @Override public String getNickname() { return nickname; }
    }
}
