package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.CodedOutputStream;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.server.packet.recv.*;
import emu.grasscutter.server.packet.send.*;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;

import java.io.ByteArrayOutputStream;
import java.util.List;

/** Real requests must preserve reusable items and the client's two separate widget slots. */
class WidgetEquipmentRequestTest {
    NatsaurusPossessionTest env;

    @BeforeEach
    void setup() throws Exception {
        env = new NatsaurusPossessionTest();
        env.setup();
        for (int id : new int[] {220003, 220005, 220023, 220015, 103}) {
            var data =
                    JsonUtils.decode(
                            "{id:"
                                    + id
                                    + ",itemType:'ITEM_MATERIAL',materialType:'"
                                    + (id == 103 ? "MATERIAL_MATERIAL" : "MATERIAL_WIDGET")
                                    + "',stackLimit:99}",
                            ItemData.class);
            data.onLoad();
            env.put(GameData.getItemDataMap(), id, data);
        }
    }

    @AfterEach
    void close() {
        env.close();
    }

    GameItem own(int id, int count) {
        var item =
                new GameItem(GameData.getItemDataMap().get(id), count) {
                    @Override
                    public void save() {}
                };
        item.setOwner(env.fixture.player);
        env.fixture.player.getInventory().getItems().put(item.getGuid(), item);
        env.fixture.player.getInventory().getInventoryTab(ItemType.ITEM_MATERIAL).onAddItem(item);
        return item;
    }

    SetWidgetSlotRspOuterClass.SetWidgetSlotRsp equip(int id, int op, int... tags) {
        var bytes = new ByteArrayOutputStream();
        assertDoesNotThrow(
                () -> {
                    var wire = CodedOutputStream.newInstance(bytes);
                    wire.writeUInt32(12, id);
                    wire.writeEnum(2, op);
                    for (int tag : tags) wire.writeEnum(4, tag);
                    wire.flush();
                    new HandlerSetWidgetSlotReq()
                            .handle(
                                    env.fixture.player.getSession(),
                                    new byte[0],
                                    bytes.toByteArray());
                });
        return assertDoesNotThrow(
                () ->
                        SetWidgetSlotRspOuterClass.SetWidgetSlotRsp.parseFrom(
                                env.fixture.player.packets.stream()
                                        .filter(
                                                p ->
                                                        p.getOpcode()
                                                                == PacketOpcodes.SetWidgetSlotRsp)
                                        .reduce((a, b) -> b)
                                        .orElseThrow()
                                        .getData()));
    }

    List<WidgetSlotDataOuterClass.WidgetSlotData> slots() {
        return assertDoesNotThrow(
                () ->
                        GetWidgetSlotRspOuterClass.GetWidgetSlotRsp.parseFrom(
                                        new PacketGetWidgetSlotRsp(env.fixture.player).getData())
                                .getSlotListList());
    }

    QuickUseWidgetRspOuterClass.QuickUseWidgetRsp quick() {
        assertDoesNotThrow(
                () ->
                        new HandlerQuickUseWidgetReq()
                                .handle(env.fixture.player.getSession(), new byte[0], new byte[0]));
        return assertDoesNotThrow(
                () ->
                        QuickUseWidgetRspOuterClass.QuickUseWidgetRsp.parseFrom(
                                env.fixture.player.packets.stream()
                                        .filter(
                                                p ->
                                                        p.getOpcode()
                                                                == PacketOpcodes.QuickUseWidgetRsp)
                                        .reduce((a, b) -> b)
                                        .orElseThrow()
                                        .getData()));
    }

    @Test
    void detachClearsPersistedQuickSlotAndReturnsActualOperation() {
        own(220003, 1);
        env.fixture.player.setWidgetId(220003);
        var rsp = equip(220003, 1, 0);
        assertEquals(0, rsp.getRetcode());
        assertEquals(1, rsp.getOpValue());
        assertEquals(List.of(0), rsp.getTagListValueList());
        assertEquals(0, env.fixture.player.getWidgetId());
        assertTrue(slots().stream().allMatch(s -> s.getMaterialId() == 0));
    }

    @Test
    void unownedWidgetCannotBeEquipped() {
        assertNotEquals(0, equip(220023, 0, 1).getRetcode());
        assertEquals(0, env.fixture.player.getWidgetId());
        assertTrue(slots().stream().allMatch(s -> s.getMaterialId() == 0));
    }

    @Test
    void ordinaryMaterialCannotBecomeAWidget() {
        own(103, 10);
        assertNotEquals(0, equip(103, 0, 0).getRetcode());
        assertEquals(0, env.fixture.player.getWidgetId());
    }

    @Test
    void unknownOperationCannotEquip() {
        own(220023, 1);
        assertNotEquals(0, equip(220023, 99, 1).getRetcode());
        assertTrue(slots().stream().allMatch(s -> s.getMaterialId() == 0));
    }

    @Test
    void unknownTagCannotEquip() {
        own(220023, 1);
        assertNotEquals(0, equip(220023, 0, 9).getRetcode());
        assertTrue(slots().stream().allMatch(s -> s.getMaterialId() == 0));
    }

    @Test
    void tagMustBelongToTheActualWidgetConfiguration() {
        own(220023, 1);
        assertNotEquals(0, equip(220023, 0, 0).getRetcode());
        assertTrue(slots().stream().allMatch(s -> s.getMaterialId() == 0));
    }

    @Test
    void emptyQuickSlotStillAnswersWithoutException() {
        assertNotEquals(0, quick().getRetcode());
    }

    @Test
    void missingEquippedItemStillAnswersWithoutException() {
        env.fixture.player.setWidgetId(220003);
        assertNotEquals(0, quick().getRetcode());
    }

    @Test
    void reusableWidgetIsNeverConsumedByUnsupportedQuickUse() {
        var item = own(220003, 1);
        env.fixture.player.setWidgetId(220003);
        assertNotEquals(0, quick().getRetcode());
        assertEquals(1, item.getCount());
        assertSame(item, env.fixture.player.getInventory().getItemById(220003));
    }

    @Test
    void unsupportedEffectsCannotConsumeAStackOrPretendSuccess() {
        var item = own(220005, 2);
        env.fixture.player.setWidgetId(220005);
        assertNotEquals(0, quick().getRetcode());
        assertEquals(2, item.getCount());
    }

    @Test
    void petAndQuickUseWidgetKeepIndependentSlots() {
        own(220003, 1);
        own(220023, 1);
        assertEquals(0, equip(220003, 0, 0).getRetcode());
        assertEquals(0, equip(220023, 0, 1).getRetcode());
        assertEquals(220003, env.fixture.player.getWidgetId());
        assertTrue(
                slots().stream()
                        .anyMatch(s -> s.getTagValue() == 0 && s.getMaterialId() == 220003));
        assertTrue(
                slots().stream()
                        .anyMatch(s -> s.getTagValue() == 1 && s.getMaterialId() == 220023));
        var all =
                assertDoesNotThrow(
                        () ->
                                AllWidgetDataNotifyOuterClass.AllWidgetDataNotify.parseFrom(
                                        new PacketAllWidgetDataNotify(env.fixture.player)
                                                .getData()));
        assertEquals(slots(), all.getSlotListList());
    }

    @Test
    void oldClientRequestInfersTheRealPetTag() {
        own(220023, 1);
        assertEquals(0, equip(220023, 0).getRetcode());
        assertEquals(0, env.fixture.player.getWidgetId());
        assertTrue(
                slots().stream()
                        .anyMatch(s -> s.getTagValue() == 1 && s.getMaterialId() == 220023));
    }

    @Test
    void removingPetPreservesQuickUseSlot() {
        own(220003, 1);
        own(220023, 1);
        equip(220003, 0, 0);
        equip(220023, 0, 1);
        assertEquals(0, equip(220023, 1, 1).getRetcode());
        assertEquals(220003, env.fixture.player.getWidgetId());
        assertTrue(slots().stream().noneMatch(s -> s.getTagValue() == 1 && s.getMaterialId() != 0));
    }

    @Test
    void requestCannotDetachADifferentMaterial() {
        own(220003, 1);
        own(220005, 1);
        equip(220003, 0, 0);
        assertNotEquals(0, equip(220005, 1, 0).getRetcode());
        assertEquals(220003, env.fixture.player.getWidgetId());
    }

    @Test
    void requestDescriptorPreservesNativePackedTagList() {
        var f = SetWidgetSlotReqOuterClass.SetWidgetSlotReq.getDescriptor().findFieldByNumber(4);
        assertNotNull(f);
        assertTrue(f.isRepeated());
        assertEquals(com.google.protobuf.Descriptors.FieldDescriptor.Type.ENUM, f.getType());
    }

    @Test
    void nativePackedTagEncodingSelectsFollowerSlot() {
        own(220023, 1);
        var bytes = new ByteArrayOutputStream();
        assertDoesNotThrow(
                () -> {
                    var output = CodedOutputStream.newInstance(bytes);
                    output.writeUInt32(12, 220023);
                    output.writeByteArray(4, new byte[] {1});
                    output.flush();
                    new HandlerSetWidgetSlotReq()
                            .handle(
                                    env.fixture.player.getSession(),
                                    new byte[0],
                                    bytes.toByteArray());
                });
        assertEquals(0, env.fixture.player.getWidgetId());
        assertTrue(
                slots().stream()
                        .anyMatch(s -> s.getTagValue() == 1 && s.getMaterialId() == 220023));
    }
}
