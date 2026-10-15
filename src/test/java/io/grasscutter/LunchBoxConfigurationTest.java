package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.server.packet.recv.HandlerSetUpLunchBoxWidgetReq;
import emu.grasscutter.server.packet.send.PacketAllWidgetDataNotify;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;

import java.nio.file.Path;
import java.util.Map;

/** Actual requests must store the two native food slots without consuming the food or widget. */
class LunchBoxConfigurationTest extends WidgetEquipmentRequestTest {
    static Map<Integer, ItemData> resources;

    @BeforeAll
    static void resources() throws Exception {
        resources =
                JsonUtils.loadToList(
                                Path.of("resources/ExcelBinOutput/MaterialExcelConfigData.json"),
                                ItemData.class)
                        .stream()
                        .filter(
                                row ->
                                        java.util.Set.of(220008, 108033, 108005, 108035, 108511)
                                                .contains(row.getId()))
                        .collect(java.util.stream.Collectors.toMap(ItemData::getId, row -> row));
        assertEquals(5, resources.size());
        resources.values().forEach(ItemData::onLoad);
    }

    @BeforeEach
    void food() {
        resources.forEach((id, row) -> env.put(GameData.getItemDataMap(), id, row));
    }

    SetUpLunchBoxWidgetRspOuterClass.SetUpLunchBoxWidgetRsp configure(
            Map<Integer, Integer> choices) {
        var request =
                SetUpLunchBoxWidgetReqOuterClass.SetUpLunchBoxWidgetReq.newBuilder()
                        .setLunchBoxData(
                                LunchBoxDataOuterClass.LunchBoxData.newBuilder()
                                        .putAllSlotMaterialMap(choices))
                        .build();
        assertDoesNotThrow(
                () ->
                        new HandlerSetUpLunchBoxWidgetReq()
                                .handle(
                                        env.fixture.player.getSession(),
                                        new byte[0],
                                        request.toByteArray()));
        return assertDoesNotThrow(
                () ->
                        SetUpLunchBoxWidgetRspOuterClass.SetUpLunchBoxWidgetRsp.parseFrom(
                                env.fixture
                                        .player
                                        .packets
                                        .get(env.fixture.player.packets.size() - 1)
                                        .getData()));
    }

    Map<Integer, Integer> loginChoices() {
        return assertDoesNotThrow(
                        () ->
                                AllWidgetDataNotifyOuterClass.AllWidgetDataNotify.parseFrom(
                                        new PacketAllWidgetDataNotify(env.fixture.player)
                                                .getData()))
                .getLunchBoxData()
                .getSlotMaterialMapMap();
    }

    void stock() {
        own(220008, 1);
        own(108033, 3);
        own(108005, 2);
    }

    @Test
    void validSlotsAreIncludedInSubsequentWidgetSync() {
        stock();
        var choices = Map.of(1, 108033, 2, 108005);
        assertEquals(0, configure(choices).getRetcode());
        assertEquals(choices, loginChoices());
        assertEquals(1, env.fixture.player.getInventory().getItemById(220008).getCount());
        assertEquals(3, env.fixture.player.getInventory().getItemById(108033).getCount());
    }

    @Test
    void updatingOneSlotPreservesTheOther() {
        stock();
        configure(Map.of(1, 108033, 2, 108005));
        assertEquals(
                Map.of(1, 108033, 2, 108005),
                configure(Map.of(1, 108033)).getLunchBoxData().getSlotMaterialMapMap());
    }

    @Test
    void zeroClearsOnlyTheSpecifiedSlot() {
        stock();
        configure(Map.of(1, 108033, 2, 108005));
        assertEquals(
                Map.of(2, 108005),
                configure(Map.of(1, 0)).getLunchBoxData().getSlotMaterialMapMap());
        assertEquals(Map.of(2, 108005), loginChoices());
    }

    @Test
    void missingWidgetRejectsTheConfiguration() {
        own(108033, 2);
        assertEquals(
                RetcodeOuterClass.Retcode.RET_ITEM_NOT_EXIST.getNumber(),
                configure(Map.of(1, 108033)).getRetcode());
        assertTrue(loginChoices().isEmpty());
    }

    @Test
    void wrongFoodForSlotIsRejectedAtomically() {
        stock();
        configure(Map.of(1, 108033));
        assertEquals(
                RetcodeOuterClass.Retcode.RET_LUNCH_BOX_DATA_ERROR.getNumber(),
                configure(Map.of(1, 108005, 2, 108033)).getRetcode());
        assertEquals(Map.of(1, 108033), loginChoices());
    }

    @Test
    void missingFoodRejectsWithoutReplacingSavedChoice() {
        own(220008, 1);
        assertEquals(
                RetcodeOuterClass.Retcode.RET_ITEM_NOT_EXIST.getNumber(),
                configure(Map.of(1, 108033)).getRetcode());
        assertTrue(loginChoices().isEmpty());
    }

    @Test
    void noneIsNotARealFoodSlot() {
        stock();
        assertEquals(
                RetcodeOuterClass.Retcode.RET_LUNCH_BOX_DATA_ERROR.getNumber(),
                configure(Map.of(0, 108033)).getRetcode());
    }

    @Test
    void arbitrarySlotIsRejected() {
        stock();
        assertEquals(
                RetcodeOuterClass.Retcode.RET_LUNCH_BOX_DATA_ERROR.getNumber(),
                configure(Map.of(99, 108033)).getRetcode());
    }

    @Test
    void emptyPatchKeepsBothSavedSlots() {
        stock();
        var choices = Map.of(1, 108033, 2, 108005);
        configure(choices);
        assertEquals(choices, configure(Map.of()).getLunchBoxData().getSlotMaterialMapMap());
    }

    @Test
    void foodAtZeroCountCannotBeNewlySelected() {
        stock();
        env.fixture.player.getInventory().getItemById(108033).setCount(0);
        assertEquals(
                RetcodeOuterClass.Retcode.RET_ITEM_NOT_EXIST.getNumber(),
                configure(Map.of(1, 108033)).getRetcode());
    }

    @Test
    void recoveryFoodsBeyondTheExplicitIdListUseResourceEffectTags() {
        stock();
        own(108511, 2);
        assertEquals(0, configure(Map.of(1, 108511)).getRetcode());
        assertEquals(Map.of(1, 108511), loginChoices());
    }

    @Test
    void staminaFoodCannotBePutIntoTheHealingSlot() {
        stock();
        own(108035, 2);
        assertEquals(
                RetcodeOuterClass.Retcode.RET_LUNCH_BOX_DATA_ERROR.getNumber(),
                configure(Map.of(1, 108035)).getRetcode());
    }

    @Test
    void foodOwnedByAnotherPlayerCannotBeSelected() {
        stock();
        var other = new AbilityTestFixture.TestPlayer();
        other.setUid(20001);
        env.fixture.player.getInventory().getItemById(108033).setOwner(other);
        assertEquals(
                RetcodeOuterClass.Retcode.RET_ITEM_NOT_EXIST.getNumber(),
                configure(Map.of(1, 108033)).getRetcode());
    }

    @Test
    void canonicalEffectWinsOverBothExportAliases() {
        var item =
                JsonUtils.decode(
                        "{id:9999,itemType:'ITEM_MATERIAL',materialType:'MATERIAL_FOOD',"
                            + "foodEffect:'Revive',PANBBKJOBOL:'Recovery',KNOIOAPAFIH:'Recovery'}",
                        ItemData.class);
        var catalog = emu.grasscutter.game.player.WidgetCatalog.current();
        assertFalse(catalog.acceptsFood(1, item));
        assertTrue(catalog.acceptsFood(2, item));
    }

    @Test
    void currentFoodExportWinsOverLegacyExport() {
        var item =
                JsonUtils.decode(
                        "{id:9999,itemType:'ITEM_MATERIAL',materialType:'MATERIAL_FOOD',"
                                + "PANBBKJOBOL:'Recovery',KNOIOAPAFIH:'Revive'}",
                        ItemData.class);
        var catalog = emu.grasscutter.game.player.WidgetCatalog.current();
        assertTrue(catalog.acceptsFood(1, item));
        assertFalse(catalog.acceptsFood(2, item));
    }

    @Test
    void malformedRequestHasAnErrorResponse() {
        stock();
        assertDoesNotThrow(
                () ->
                        new HandlerSetUpLunchBoxWidgetReq()
                                .handle(
                                        env.fixture.player.getSession(),
                                        new byte[0],
                                        new byte[] {0x22, (byte) 0x80}));
        var response =
                assertDoesNotThrow(
                        () ->
                                SetUpLunchBoxWidgetRspOuterClass.SetUpLunchBoxWidgetRsp.parseFrom(
                                        env.fixture
                                                .player
                                                .packets
                                                .get(env.fixture.player.packets.size() - 1)
                                                .getData()));
        assertNotEquals(0, response.getRetcode());
    }

    @Test
    void nativeFieldNumbersRoundTripThroughRequestResponseAndLoginSync() {
        stock();
        // Request field 4, lunch-box map field 9, Hp slot 1, material 108033.
        byte[] request = {0x22, 0x08, 0x4a, 0x06, 0x08, 0x01, 0x10, (byte) 0x81, (byte) 0xcc, 0x06};
        assertDoesNotThrow(
                () ->
                        new HandlerSetUpLunchBoxWidgetReq()
                                .handle(env.fixture.player.getSession(), new byte[0], request));
        byte[] response = {
            0x32, 0x08, 0x4a, 0x06, 0x08, 0x01, 0x10, (byte) 0x81, (byte) 0xcc, 0x06
        };
        assertArrayEquals(
                response,
                env.fixture.player.packets.get(env.fixture.player.packets.size() - 1).getData());
        byte[] login = {0x62, 0x08, 0x4a, 0x06, 0x08, 0x01, 0x10, (byte) 0x81, (byte) 0xcc, 0x06};
        assertArrayEquals(login, new PacketAllWidgetDataNotify(env.fixture.player).getData());
    }
}
