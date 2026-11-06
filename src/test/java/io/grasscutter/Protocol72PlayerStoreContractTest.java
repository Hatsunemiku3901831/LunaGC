package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import com.google.protobuf.UnknownFieldSet;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.inventory.ItemType;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.packet.PacketOpcodesUtils;
import emu.grasscutter.net.proto.PlayerStoreNotifyOuterClass.PlayerStoreNotify;
import emu.grasscutter.server.packet.send.PacketPlayerStoreNotify;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

/** Independent F-03d2b1/b2 contracts; typed fixture getters do not save or mutate inventory. */
class Protocol72PlayerStoreContractTest {
    @BeforeAll static void initializeConfiguration() { Grasscutter.getConfig(); }

    @Test
    void canonicalRouteIsTheNativeInventoryReplacementCommand() {
        assertEquals(20892, PacketOpcodes.PlayerStoreNotify);
        assertEquals("PlayerStoreNotify", PacketOpcodesUtils.getOpcodeName(20892));
    }

    @Test
    void rootKeepsTheCompleteRawFieldSetAndSeparateItemAdapter() {
        var descriptor = PlayerStoreNotify.getDescriptor();
        assertEquals(Set.of(2, 8, 14), descriptor.getFields().stream()
                .map(Descriptors.FieldDescriptor::getNumber).collect(Collectors.toSet()));
        assertEquals("item_list", descriptor.findFieldByNumber(2).getName());
        assertEquals("PAKLDABINMP", descriptor.findFieldByNumber(8).getName());
        assertEquals("store_type", descriptor.findFieldByNumber(14).getName());
        assertEquals("cn72.inventorywire.NDGHFPMJFIM", descriptor.findFieldByNumber(2).getMessageType().getFullName());
    }

    @Test
    void emptyInventoryStillExplicitlySelectsPackWithoutAnUnprovenWeightField() {
        var packet = new PacketPlayerStoreNotify(new Player());
        assertArrayEquals(HexFormat.of().parseHex("7001"), packet.getData());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 17, Integer.MAX_VALUE})
    void actualSenderRetainsMaterialIdHighGuidAndUnscaledCount(int count) throws Exception {
        var player = new Player();
        long guid = 9007199254740993L;
        var item = new MaterialFixture(guid, count);
        player.getInventory().getItems().put(guid, item);
        var packet = new PacketPlayerStoreNotify(player);
        var fields = UnknownFieldSet.parseFrom(packet.getData()).asMap();
        assertEquals(Set.of(2, 14), fields.keySet());
        assertEquals(1L, fields.get(14).getVarintList().get(0));
        var itemWire = UnknownFieldSet.parseFrom(fields.get(2).getLengthDelimitedList().get(0)).asMap();
        assertEquals(Set.of(1, 2, 5), itemWire.keySet());
        assertEquals(42L, itemWire.get(1).getVarintList().get(0));
        assertEquals(guid, itemWire.get(2).getVarintList().get(0));
        Message parsedItem = PlayerStoreNotify.parseFrom(packet.getData()).getItemList(0);
        Message material = (Message) parsedItem.getField(parsedItem.getDescriptorForType().findFieldByNumber(5));
        assertEquals(count, material.getField(material.getDescriptorForType().findFieldByNumber(1)));
        assertTrue(material.getUnknownFields().asMap().isEmpty());
        assertEquals(count, item.getCount());
        assertSame(item, player.getInventory().getItems().get(guid));
        assertEquals(1, player.getInventory().getItems().size());
        System.out.printf("CN72_STORE_MATERIAL count=%d wire=%s%n", count,
                HexFormat.of().formatHex(packet.getData()));
    }

    @ParameterizedTest
    @CsvSource({"true,false,false", "true,false,true", "true,true,false", "true,true,true",
                "false,false,false", "false,false,true", "false,true,false", "false,true,true"})
    void actualEquipSenderOnlyProjectsApprovedFieldsAndKeepsServerRefinement(boolean weapon,
                                                    boolean locked, boolean favourite) throws Exception {
        var player = new Player();
        var item = new EquipFixture(weapon, locked, favourite);
        player.getInventory().getItems().put(item.getGuid(), item);
        var packet = new PacketPlayerStoreNotify(player);
        var raw = UnknownFieldSet.parseFrom(packet.getData()).asMap();
        assertEquals(Set.of(2, 14), raw.keySet());
        Message value = PlayerStoreNotify.parseFrom(packet.getData()).getItemList(0);
        Message equip = (Message) value.getField(value.getDescriptorForType().findFieldByNumber(6));
        assertEquals(locked, equip.getField(equip.getDescriptorForType().findFieldByNumber(3)));
        assertTrue(equip.hasField(equip.getDescriptorForType().findFieldByNumber(weapon ? 2 : 1)));
        assertFalse(equip.hasField(equip.getDescriptorForType().findFieldByNumber(weapon ? 1 : 2)));
        Message detail = (Message) equip.getField(equip.getDescriptorForType().findFieldByNumber(weapon ? 2 : 1));
        var fields = detail.getAllFields().keySet().stream().map(Descriptors.FieldDescriptor::getNumber)
                .collect(Collectors.toSet());
        assertEquals(weapon ? Set.of(1, 2, 3) : Set.of(1, 6), fields);
        assertEquals(3, detail.getField(detail.getDescriptorForType().findFieldByNumber(1)));
        if (weapon) {
            assertEquals(250, detail.getField(detail.getDescriptorForType().findFieldByNumber(2)));
            assertEquals(2, detail.getField(detail.getDescriptorForType().findFieldByNumber(3)));
        } else {
            assertEquals(favourite, detail.getField(detail.getDescriptorForType().findFieldByNumber(6)));
        }
        assertEquals(4, item.getRefinement());
        assertEquals(List.of(77, 88), item.getAffixes());
        assertSame(item, player.getInventory().getItems().get(item.getGuid()));
        System.out.printf("CN72_STORE_EQUIP kind=%s locked=%s favourite=%s wire=%s%n",
                weapon ? "weapon" : "reliquary", locked, favourite,
                HexFormat.of().formatHex(packet.getData()));
    }

    @Test
    void unverifiedItemKindsRemainInServerInventoryWithoutAnInventedWireBranch() throws Exception {
        var player = new Player();
        var item = new MaterialFixture(71, 17) {
            @Override public ItemType getItemType() { return ItemType.ITEM_FURNITURE; }
        };
        player.getInventory().getItems().put(item.getGuid(), item);
        assertArrayEquals(HexFormat.of().parseHex("7001"), new PacketPlayerStoreNotify(player).getData());
        assertSame(item, player.getInventory().getItems().get(71));
        assertEquals(17, item.getCount());
    }

    private static class MaterialFixture extends GameItem {
        private final long fixtureGuid;
        private final int fixtureCount;
        MaterialFixture(long guid, int count) { this.fixtureGuid = guid; this.fixtureCount = count; }
        @Override public int getItemId() { return 42; }
        @Override public long getGuid() { return fixtureGuid; }
        @Override public int getCount() { return fixtureCount; }
        @Override public ItemType getItemType() { return ItemType.ITEM_MATERIAL; }
    }

    private static final class EquipFixture extends GameItem {
        private final boolean weapon, locked, favourite;
        EquipFixture(boolean weapon, boolean locked, boolean favourite) {
            this.weapon = weapon; this.locked = locked; this.favourite = favourite;
        }
        @Override public int getItemId() { return weapon ? 1001 : 2001; }
        @Override public long getGuid() { return Long.MIN_VALUE + 1; }
        @Override public ItemType getItemType() { return weapon ? ItemType.ITEM_WEAPON : ItemType.ITEM_RELIQUARY; }
        @Override public int getLevel() { return 3; }
        @Override public int getExp() { return 250; }
        @Override public int getPromoteLevel() { return 2; }
        @Override public boolean isLocked() { return locked; }
        @Override public boolean isFavourite() { return favourite; }
        @Override public int getRefinement() { return 4; }
        @Override public List<Integer> getAffixes() { return List.of(77, 88); }
    }
}
