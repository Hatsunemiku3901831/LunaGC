package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;

import emu.grasscutter.data.*;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.game.props.ItemUseAction.*;
import emu.grasscutter.game.props.ItemUseTarget;
import emu.grasscutter.game.systems.InventorySystem;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.CodexDataFullNotifyOuterClass.CodexDataFullNotify;
import emu.grasscutter.net.proto.CodexDataUpdateNotifyOuterClass.CodexDataUpdateNotify;
import emu.grasscutter.server.packet.send.PacketCodexDataFullNotify;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;

import java.util.*;

class BookCodexUseTest {
    private AbilityTestFixture environment;
    private InvestigationRewardTest.ClaimPlayer player;
    private final Gson gson = new Gson();
    private final Map<Map<?, ?>, Map<?, ?>> oldMaps = new IdentityHashMap<>();
    private ItemData book;

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void clear(Map map) {
        oldMaps.put(map, new HashMap<>(map));
        map.clear();
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, GameResource> map(String name) throws Exception {
        try {
            return (Map<Integer, GameResource>) GameData.class.getMethod(name).invoke(null);
        } catch (NoSuchMethodException baseline) {
            return null;
        }
    }

    private void definition(int id, int item) throws Exception {
        var primary = map("getCodexBookDataMap");
        if (primary != null) {
            var type = Class.forName("emu.grasscutter.data.excels.codex.CodexBookData");
            var data =
                    (GameResource) gson.fromJson("{Id:" + id + ",materialId:" + item + "}", type);
            primary.put(id, data);
            data.onLoad();
        }
    }

    @BeforeEach
    void setup() throws Exception {
        environment = new AbilityTestFixture();
        player = new InvestigationRewardTest.ClaimPlayer();
        var server = environment.scene.getWorld().getServer();
        player.setSession(new InvestigationRewardTest.ClaimSession(server, player));
        clear(GameData.getItemDataMap());
        for (var name : List.of("getCodexBookDataMap", "getCodexBookDataItemIdMap")) {
            var map = map(name);
            if (map != null) clear(map);
        }
        var field = server.getClass().getDeclaredField("inventorySystem");
        field.setAccessible(true);
        field.set(server, new InventorySystem(server));
        definition(50001001, 100178);
        book =
                JsonUtils.decode(
                        JsonParser.parseString(
                                        "{id:100178,itemType:'ITEM_MATERIAL',materialType:'MATERIAL_QUEST',stackLimit:1,useOnGain:true,useTarget:'ITEM_USE_TARGET_NONE',itemUse:[{useOp:'ITEM_USE_UNLOCK_CODEX',useParam:['50001001']}]}")
                                .getAsJsonObject(),
                        ItemData.class);
        book.onLoad();
        GameData.getItemDataMap().put(book.getId(), book);
        player.getCodex().getUnlockedBook().clear();
        player.packets.clear();
    }

    @AfterEach
    @SuppressWarnings({"rawtypes", "unchecked"})
    void cleanup() {
        oldMaps.forEach(
                (map, old) -> {
                    ((Map) map).clear();
                    ((Map) map).putAll(old);
                });
        environment.close();
    }

    private boolean grant() {
        return player.getInventory().addItem(new GameItem(book, 1), ActionReason.Shop);
    }

    @Test
    void automaticBookUnlockCountsAsASuccessfulGrant() {
        assertTrue(grant());
        assertTrue(player.getCodex().getUnlockedBook().contains(100178));
        assertEquals(0, player.getInventory().getItemCountById(100178));
    }

    @Test
    void automaticBookSendsItsRealCodexIdAndType() throws Exception {
        grant();
        var packet =
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.CodexDataUpdateNotify)
                        .findFirst()
                        .orElseThrow();
        var reply = CodexDataUpdateNotify.parseFrom(packet.getData());
        assertEquals(5, reply.getTypeValue());
        assertEquals(50001001, reply.getId());
    }

    @Test
    void duplicateBookDoesNotGrantOrNotifyAgain() {
        player.getCodex().getUnlockedBook().add(100178);
        assertFalse(grant());
        assertTrue(
                player.packets.stream()
                        .noneMatch(p -> p.getOpcode() == PacketOpcodes.CodexDataUpdateNotify));
    }

    @Test
    void unknownCodexDoesNotUnlockAnItem() {
        var action = new ItemUseUnlockCodex(new String[] {"59999999"});
        var params = new UseItemParams(player, ItemUseTarget.ITEM_USE_TARGET_NONE);
        params.usedItemId = 100178;
        assertFalse(action.useItem(params));
        assertTrue(player.getCodex().getUnlockedBook().isEmpty());
    }

    @Test
    void wrongSourceCannotUnlockADifferentBook() {
        var action = new ItemUseUnlockCodex(new String[] {"50001001"});
        var params = new UseItemParams(player, ItemUseTarget.ITEM_USE_TARGET_NONE);
        params.usedItemId = 100179;
        assertFalse(action.useItem(params));
        assertTrue(player.getCodex().getUnlockedBook().isEmpty());
    }

    @Test
    void nullParamsReturnFailureWithoutThrowing() {
        assertFalse(
                assertDoesNotThrow(
                        () -> new ItemUseUnlockCodex(new String[] {"50001001"}).useItem(null)));
    }

    @Test
    void fullNotifyRetainsPreviouslyUnlockedBooks() throws Exception {
        player.getCodex().getUnlockedBook().add(100178);
        var reply = CodexDataFullNotify.parseFrom(new PacketCodexDataFullNotify(player).getData());
        var books =
                reply.getTypeDataListList().stream()
                        .filter(row -> row.getTypeValue() == 5)
                        .findFirst()
                        .orElseThrow();
        assertEquals(List.of(50001001), books.getCodexIdListList());
        assertEquals(List.of(true), books.getHaveViewedListList());
    }

    @Test
    void staleUnknownBookDoesNotLeakIntoFullNotify() throws Exception {
        player.getCodex().getUnlockedBook().add(999999);
        var reply = CodexDataFullNotify.parseFrom(new PacketCodexDataFullNotify(player).getData());
        var books =
                reply.getTypeDataListList().stream()
                        .filter(row -> row.getTypeValue() == 5)
                        .findFirst()
                        .orElseThrow();
        assertTrue(books.getCodexIdListList().isEmpty());
    }

    @Test
    void codexAndMaterialMapsAreCanonical() throws Exception {
        var primary = map("getCodexBookDataMap");
        var secondary = map("getCodexBookDataItemIdMap");
        assertNotNull(primary);
        assertNotNull(secondary);
        assertSame(primary.get(50001001), secondary.get(100178));
    }

    @Test
    void aMismatchedDefinitionCannotUnlockTheRequestedBook() throws Exception {
        definition(50001001, 100179);
        assertFalse(grant());
        assertTrue(player.getCodex().getUnlockedBook().isEmpty());
    }
}
