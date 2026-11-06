package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.config.Configuration;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.packet.PacketOpcodesUtils;
import emu.grasscutter.net.proto.StoreWeightLimitNotifyOuterClass.StoreWeightLimitNotify;
import emu.grasscutter.server.packet.send.PacketStoreWeightLimitNotify;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/** Independent target consumer/wire vectors; no client acceptance claim. */
class Protocol72InventoryCapacityTest {
    @BeforeAll
    static void initializeConfiguration() {
        Grasscutter.getConfig();
    }

    @Test
    void canonicalCommandMatchesTargetDispatch() {
        assertEquals(22609, PacketOpcodes.StoreWeightLimitNotify);
        assertEquals("StoreWeightLimitNotify", PacketOpcodesUtils.getOpcodeName(22609));
    }

    @Test
    void actualSenderProjectsOnlyFiveConsumedFields() throws Throwable {
        withLimits(11, 55, 99, 141, () -> {
            var packet = new PacketStoreWeightLimitNotify();
            // Independent wire: reliquary11/furniture55/StorePack1/weapon99/material141.
            assertArrayEquals(hex("080b283730014863708d01"), packet.getData());
            var parsed = DynamicMessage.parseFrom(StoreWeightLimitNotify.getDescriptor(), packet.getData());
            assertTrue(parsed.getUnknownFields().asMap().isEmpty());
            assertEquals(Set.of(1, 5, 6, 9, 14), tags(parsed));
            assertEquals(1, parsed.getField(parsed.getDescriptorForType().findFieldByNumber(6)));
        });
    }

    @Test
    void zeroLimitsRemainAbsentAndPackSelectionRemainsNonzero() throws Throwable {
        withLimits(0, 0, 0, 0, () ->
                assertArrayEquals(hex("3001"), new PacketStoreWeightLimitNotify().getData()));
    }

    @Test
    void uint32UpperBoundReachesAllFourCapacityTags() throws Throwable {
        withLimits(-1, -1, -1, -1, () -> {
            var packet = new PacketStoreWeightLimitNotify();
            assertArrayEquals(hex("08ffffffff0f28ffffffff0f300148ffffffff0f70ffffffff0f"), packet.getData());
            var parsed = DynamicMessage.parseFrom(StoreWeightLimitNotify.getDescriptor(), packet.getData());
            for (int tag : new int[] {1, 5, 9, 14}) {
                assertEquals(-1, parsed.getField(parsed.getDescriptorForType().findFieldByNumber(tag)));
            }
        });
    }

    @Test
    void semanticAndRawFieldsKeepTheirSeparateEvidenceBoundaries() {
        var descriptor = StoreWeightLimitNotify.getDescriptor();
        Map<Integer, String> names = Map.of(1, "reliquary_count_limit", 2, "PAKLDABINMP",
                4, "ADCHFHMCOJG", 5, "furniture_count_limit", 6, "store_type",
                7, "DECHDEGPBOE", 9, "weapon_count_limit", 14, "material_count_limit");
        assertEquals(names.keySet(), descriptor.getFields().stream()
                .map(Descriptors.FieldDescriptor::getNumber).collect(Collectors.toSet()));
        names.forEach((tag, name) -> assertEquals(name, descriptor.findFieldByNumber(tag).getName()));
        assertEquals(Descriptors.FieldDescriptor.Type.INT32, descriptor.findFieldByNumber(6).getType());
        for (int tag : new int[] {1, 2, 4, 5, 7, 9, 14}) {
            assertEquals(Descriptors.FieldDescriptor.Type.UINT32, descriptor.findFieldByNumber(tag).getType());
        }
    }

    @Test
    void independentDuplicateAndWrongWireRetainProtobufSemantics() throws Exception {
        var parsed = DynamicMessage.parseFrom(StoreWeightLimitNotify.getDescriptor(), hex("080b08072a0178"));
        assertEquals(7, parsed.getField(parsed.getDescriptorForType().findFieldByNumber(1)));
        assertEquals(0, parsed.getField(parsed.getDescriptorForType().findFieldByNumber(5)));
        assertTrue(parsed.getUnknownFields().asMap().containsKey(5));
    }

    private static void withLimits(int relics, int furniture, int weapons, int materials, Executable check)
            throws Throwable {
        var limits = Configuration.INVENTORY_LIMITS;
        int oldRelics = limits.relics, oldFurniture = limits.furniture, oldWeapons = limits.weapons;
        int oldMaterials = limits.materials, oldAll = limits.all;
        try {
            limits.relics = relics; limits.furniture = furniture;
            limits.weapons = weapons; limits.materials = materials;
            limits.all = 123456789; // Unproven tag2 must not be sent as the total limit.
            check.execute();
        } finally {
            limits.relics = oldRelics; limits.furniture = oldFurniture; limits.weapons = oldWeapons;
            limits.materials = oldMaterials; limits.all = oldAll;
        }
    }

    private static Set<Integer> tags(DynamicMessage message) {
        return message.getAllFields().keySet().stream().map(Descriptors.FieldDescriptor::getNumber)
                .collect(Collectors.toSet());
    }

    private static byte[] hex(String value) {
        return HexFormat.of().parseHex(value);
    }
}
