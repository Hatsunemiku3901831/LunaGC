package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.database.*;
import emu.grasscutter.game.combine.CombineManger;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ReliquaryDecomposeRspOuterClass.ReliquaryDecomposeRsp;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.game.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.*;
import java.time.Duration;
import java.util.*;

/** Strongbox input identities must be validated as a complete batch before any removal. */
class StrongboxInputTest {
    private AbilityTestFixture environment;
    private InvestigationRewardTest.ClaimPlayer player;
    private CombineManger manager;
    private Field datastore;
    private Object oldDatastore;
    private Map<Integer, ItemData> oldItems;
    private Map<Integer, List<Integer>> definitions;
    private Map<Integer, List<Integer>> oldDefinitions;
    private final Gson gson = new Gson();
    private List<GameItem> inputs;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() throws Exception {
        environment = new AbilityTestFixture();
        player = new InvestigationRewardTest.ClaimPlayer();
        manager = new CombineManger(environment.scene.getWorld().getServer());
        player.setSession(
                new InvestigationRewardTest.ClaimSession(
                        environment.scene.getWorld().getServer(), player));
        datastore = DatabaseManager.class.getDeclaredField("gameDatastore");
        datastore.setAccessible(true);
        oldDatastore = datastore.get(null);
        datastore.set(
                null,
                Proxy.newProxyInstance(
                        dev.morphia.Datastore.class.getClassLoader(),
                        new Class<?>[] {dev.morphia.Datastore.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("save")) return args[0];
                            if (method.getName().equals("delete"))
                                return com.mongodb.client.result.DeleteResult.acknowledged(1);
                            return null;
                        }));
        oldItems = new HashMap<>(GameData.getItemDataMap());
        item(75513, "ITEM_RELIQUARY", 5);
        item(77513, "ITEM_RELIQUARY", 5);
        item(75503, "ITEM_RELIQUARY", 4);
        item(11101, "ITEM_WEAPON", 5);
        item(100001, "ITEM_MATERIAL", 5);
        var field = CombineManger.class.getDeclaredField("reliquaryDecomposeData");
        field.setAccessible(true);
        definitions = (Map<Integer, List<Integer>>) field.get(null);
        oldDefinitions = new HashMap<>(definitions);
        definitions.clear();
        definitions.put(1, List.of(77513));
        inputs = new ArrayList<>();
        for (int i = 0; i < 3; i++) inputs.add(add(75513));
        player.packets.clear();
    }

    @AfterEach
    void cleanup() throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        datastore.set(null, oldDatastore);
        definitions.clear();
        definitions.putAll(oldDefinitions);
        GameData.getItemDataMap().clear();
        GameData.getItemDataMap().putAll(oldItems);
        environment.close();
    }

    private void item(int id, String type, int rank) {
        GameData.getItemDataMap()
                .put(
                        id,
                        gson.fromJson(
                                "{id:"
                                        + id
                                        + ",itemType:'"
                                        + type
                                        + "',rankLevel:"
                                        + rank
                                        + ",equipType:'EQUIP_RING',stackLimit:999,appendPropNum:0}",
                                ItemData.class));
    }

    private GameItem add(int id) {
        var item = new GameItem(GameData.getItemDataMap().get(id), 1);
        assertTrue(player.getInventory().addItem(item));
        return item;
    }

    private List<Long> ids() {
        return inputs.stream().map(GameItem::getGuid).toList();
    }

    private ReliquaryDecomposeRsp response() throws Exception {
        var packet =
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.ReliquaryDecomposeRsp)
                        .reduce((a, b) -> b)
                        .orElseThrow();
        return ReliquaryDecomposeRsp.parseFrom(packet.getData());
    }

    private void rejects(int config, int count, List<Long> ids) throws Exception {
        var before = new HashMap<>(player.getInventory().getItems());
        var counts = new HashMap<Long, Integer>();
        before.forEach((guid, item) -> counts.put(guid, item.getCount()));
        assertDoesNotThrow(() -> manager.decomposeReliquaries(player, config, count, ids));
        assertEquals(Retcode.RET_RELIQUARY_DECOMPOSE_PARAM_ERROR_VALUE, response().getRetcode());
        assertEquals(0, response().getGuidListCount());
        assertEquals(before, player.getInventory().getItems());
        counts.forEach(
                (guid, countBefore) ->
                        assertEquals(
                                countBefore, player.getInventory().getItemByGuid(guid).getCount()));
    }

    @Test
    void threeOwnedFiveStarRelicsProduceOneOwnedOutput() throws Exception {
        manager.decomposeReliquaries(player, 1, 1, ids());
        var reply = response();
        assertEquals(0, reply.getRetcode());
        assertEquals(1, reply.getGuidListCount());
        var result = player.getInventory().getItemByGuid(reply.getGuidList(0));
        assertNotNull(result);
        assertEquals(77513, result.getItemId());
        assertEquals(1, result.getCount());
        assertEquals(player.getUid(), result.getOwnerId());
        inputs.forEach(input -> assertNull(player.getInventory().getItemByGuid(input.getGuid())));
    }

    @Test
    void repeatingTheSameGuidCannotPayThreeTimes() throws Exception {
        long guid = inputs.get(0).getGuid();
        rejects(1, 1, List.of(guid, guid, guid));
    }

    @Test
    void duplicatingOneOfThreeInputSlotsIsRejected() throws Exception {
        rejects(
                1,
                1,
                List.of(inputs.get(0).getGuid(), inputs.get(1).getGuid(), inputs.get(0).getGuid()));
    }

    @ParameterizedTest
    @ValueSource(ints = {11101, 100001, 75503})
    void weaponsMaterialsAndFourStarRelicsCannotBeIngredients(int id) throws Exception {
        inputs.set(2, add(id));
        rejects(1, 1, ids());
    }

    @Test
    void lockedRelicIsKept() throws Exception {
        inputs.get(1).setLocked(true);
        rejects(1, 1, ids());
    }

    @Test
    void equippedRelicIsKept() throws Exception {
        inputs.get(1).setEquipCharacter(10000003);
        rejects(1, 1, ids());
    }

    @Test
    void staleForeignOwnerCannotBeConsumed() throws Exception {
        var owner = GameItem.class.getDeclaredField("ownerId");
        owner.setAccessible(true);
        owner.setInt(inputs.get(2), 10002);
        rejects(1, 1, ids());
    }

    @Test
    void zeroCountItemCannotBeConsumed() throws Exception {
        inputs.get(0).setCount(0);
        rejects(1, 1, ids());
    }

    @Test
    void unknownGuidDoesNotRemoveEarlierInputs() throws Exception {
        rejects(1, 1, List.of(inputs.get(0).getGuid(), inputs.get(1).getGuid(), 999999L));
    }

    @Test
    void unknownConfigurationDoesNotConsumeInputs() throws Exception {
        rejects(999, 1, ids());
    }

    @Test
    void zeroCountIsNotAnEmptySuccess() throws Exception {
        rejects(1, 0, List.of());
    }

    @Test
    void nullInputGetsATypedFailure() throws Exception {
        rejects(1, 1, null);
    }

    @Test
    void nullGuidGetsATypedFailure() throws Exception {
        rejects(1, 1, Arrays.asList(inputs.get(0).getGuid(), null, inputs.get(2).getGuid()));
    }

    @Test
    void countMultiplicationCannotWrapToZero() throws Exception {
        rejects(1, Integer.MIN_VALUE, List.of());
    }

    @Test
    void mismatchedInputCountDoesNotConsumeAnything() throws Exception {
        rejects(1, 2, ids());
    }

    @Test
    void resourceMaximumRejectsFortyRequestedOutputs() throws Exception {
        for (int i = 3; i < 120; i++) inputs.add(add(75513));
        rejects(1, 40, ids());
    }

    @Test
    void emptyOutputDefinitionCannotDeleteMaterials() throws Exception {
        definitions.put(1, List.of());
        rejects(1, 1, ids());
    }

    @Test
    void unknownOutputDefinitionCannotDeleteMaterials() throws Exception {
        definitions.put(1, List.of(999999));
        rejects(1, 1, ids());
    }

    @ParameterizedTest
    @ValueSource(ints = {11101, 100001, 75503})
    void allConfiguredOutputsMustBeFiveStarRelics(int id) throws Exception {
        definitions.put(1, List.of(77513, id));
        rejects(1, 1, ids());
    }

    @Test
    void malformedHandlerPayloadGetsAFailureResponse() throws Exception {
        var server = environment.scene.getWorld().getServer();
        var field = GameServer.class.getDeclaredField("combineSystem");
        field.setAccessible(true);
        field.set(server, manager);
        var session = new InvestigationRewardTest.ClaimSession(server, player);
        assertDoesNotThrow(
                () ->
                        new emu.grasscutter.server.packet.recv.HandlerReliquaryDecomposeReq()
                                .handle(session, new byte[0], new byte[] {(byte) 0x80}));
        var replies =
                session.responses.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.ReliquaryDecomposeRsp)
                        .toList();
        assertEquals(1, replies.size());
        assertEquals(
                Retcode.RET_RELIQUARY_DECOMPOSE_PARAM_ERROR_VALUE,
                ReliquaryDecomposeRsp.parseFrom(replies.get(0).getData()).getRetcode());
    }

    @Test
    void fullBagCanUseTheThreeFreedIngredientSlots() throws Exception {
        var tab = new EquipInventoryTab(3);
        inputs.forEach(tab::onAddItem);
        player.getInventory().getInventoryTypes().put(ItemType.ITEM_RELIQUARY.getValue(), tab);
        manager.decomposeReliquaries(player, 1, 1, ids());
        assertEquals(0, response().getRetcode());
        assertEquals(1, tab.getSize());
    }
}
