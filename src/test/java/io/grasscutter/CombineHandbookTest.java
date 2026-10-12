package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.database.*;
import emu.grasscutter.game.combine.*;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.CombineReqOuterClass.CombineReq;
import emu.grasscutter.net.proto.CombineRspOuterClass.CombineRsp;
import emu.grasscutter.net.proto.PlayerInvestigationTargetNotifyOuterClass.PlayerInvestigationTargetNotify;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.game.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Actual costs, inventory grants and handler dispatch; datastore writes are isolated doubles. */
class CombineHandbookTest {
    private AbilityTestFixture environment;
    private CraftPlayer player;
    private CombineManger manager;
    private final Gson gson = new Gson();
    private Map<Integer, CombineData> oldRecipes;
    private Map<Integer, ItemData> oldItems;
    private Map<Integer, InvestigationData> oldChapters;
    private Map<Integer, InvestigationTargetData> oldTargets;
    private Object oldDatastore;
    private java.lang.reflect.Field datastore;

    static class CraftInventory extends Inventory {
        boolean refuseOutput, throwOutput;
        Runnable afterOutput;
        final List<ActionReason> reasons = new ArrayList<>();

        CraftInventory(Player player) {
            super(player);
        }

        @Override
        public boolean addItem(int id, int count, ActionReason reason) {
            if (id == 112003) {
                if (throwOutput) throw new IllegalStateException("Delivery fault");
                if (refuseOutput) return false;
            }
            boolean result = super.addItem(id, count, reason);
            if (id == 112003 && result) {
                reasons.add(reason);
                var callback = afterOutput;
                afterOutput = null;
                if (callback != null) callback.run();
            }
            return result;
        }
    }

    static class CraftPlayer extends InvestigationRewardTest.ClaimPlayer {
        final CraftInventory crafting = new CraftInventory(this);

        @Override
        public Inventory getInventory() {
            return crafting;
        }
    }

    @BeforeEach
    void prepare() throws Exception {
        environment = new AbilityTestFixture();
        player = new CraftPlayer();
        manager = new CombineManger(environment.scene.getWorld().getServer());
        player.setProperty(PlayerProperty.PROP_PLAYER_LEVEL, 45);
        player.setMora(100);
        player.getOpenStates().put(1100, 1);
        player.getOpenStates().put(1101, 1);
        oldRecipes = new HashMap<>(GameData.getCombineDataMap());
        oldItems = new HashMap<>(GameData.getItemDataMap());
        oldChapters = new HashMap<>(GameData.getInvestigationDataMap());
        oldTargets = new HashMap<>(GameData.getInvestigationTargetDataMap());
        GameData.getCombineDataMap().clear();
        GameData.getItemDataMap().clear();
        GameData.getInvestigationDataMap().clear();
        GameData.getInvestigationTargetDataMap().clear();
        GameData.getInvestigationDataMap()
                .put(
                        10004,
                        gson.fromJson(
                                "{id:10004,investigationType:'INVESTIGATION_ADVENTURE',unlockLevel:1,unlockOpenStateType:'OPEN_ADVENTURE_MANUAL_CITY_MENGDE'}",
                                InvestigationData.class));
        GameData.getInvestigationTargetDataMap()
                .put(
                        60027,
                        gson.fromJson(
                                "{id:60027,investigationId:10004,progress:5,triggerConfig:{triggerType:'TRIGGER_DO_COMBINE',paramList:['','','']}}",
                                InvestigationTargetData.class));
        for (int id : List.of(112002, 112003))
            GameData.getItemDataMap()
                    .put(
                            id,
                            gson.fromJson(
                                    "{id:"
                                            + id
                                            + ",itemType:'ITEM_MATERIAL',materialType:'MATERIAL_NONE',stackLimit:1000}",
                                    ItemData.class));
        GameData.getItemDataMap()
                .put(202, gson.fromJson("{id:202,itemType:'ITEM_VIRTUAL'}", ItemData.class));
        datastore = DatabaseManager.class.getDeclaredField("gameDatastore");
        datastore.setAccessible(true);
        oldDatastore = datastore.get(null);
        datastore.set(
                null,
                Proxy.newProxyInstance(
                        dev.morphia.Datastore.class.getClassLoader(),
                        new Class<?>[] {dev.morphia.Datastore.class},
                        (proxy, method, args) -> method.getName().equals("save") ? args[0] : null));
        recipe(3, 1, 25);
        player.getInventory().addItem(112002, 6);
        player.packets.clear();
    }

    @AfterEach
    void close() throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        datastore.set(null, oldDatastore);
        GameData.getCombineDataMap().clear();
        GameData.getCombineDataMap().putAll(oldRecipes);
        GameData.getItemDataMap().clear();
        GameData.getItemDataMap().putAll(oldItems);
        GameData.getInvestigationDataMap().clear();
        GameData.getInvestigationDataMap().putAll(oldChapters);
        GameData.getInvestigationTargetDataMap().clear();
        GameData.getInvestigationTargetDataMap().putAll(oldTargets);
        environment.close();
    }

    private void recipe(int input, int output, int mora) {
        GameData.getCombineDataMap()
                .put(
                        11001,
                        gson.fromJson(
                                "{combineId:11001,playerLevel:1,resultItemId:112003,resultItemCount:"
                                        + output
                                        + ",scoinCost:"
                                        + mora
                                        + ",materialItems:[{id:112002,count:"
                                        + input
                                        + "}],randomItems:[]}",
                                CombineData.class));
    }

    private void definition(String json) {
        GameData.getCombineDataMap().put(11001, gson.fromJson(json, CombineData.class));
    }

    private int count(int id) {
        var item = player.getInventory().getItemById(id);
        return item == null ? 0 : item.getCount();
    }

    private int progress() {
        return player.getPlayerProgress().getInvestigation().get(60027);
    }

    private int error() throws Exception {
        var packet =
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.CombineRsp)
                        .reduce((a, b) -> b)
                        .orElseThrow();
        return CombineRsp.parseFrom(packet.getData()).getRetcode();
    }

    private void capacity(int size) {
        var old = player.getInventory().getInventoryTab(ItemType.ITEM_MATERIAL);
        var replacement = new MaterialInventoryTab(size);
        for (var item : player.getInventory())
            if (item.getItemType() == ItemType.ITEM_MATERIAL) replacement.onAddItem(item);
        player.getInventory()
                .getInventoryTypes()
                .put(ItemType.ITEM_MATERIAL.getValue(), replacement);
    }

    @Test
    void successUsesOriginalQuantitiesAndReturnsActualScaledCosts() {
        var result = manager.combineItem(player, 11001, 2);
        assertNotNull(result);
        assertEquals(0, count(112002));
        assertEquals(2, count(112003));
        assertEquals(50, player.getMora());
        var costs = new HashMap<Integer, Integer>();
        result.getMaterial().forEach(item -> costs.put(item.getId(), item.getCount()));
        assertEquals(Map.of(112002, 6, 202, 50), costs);
        assertEquals(2, result.getResult().get(0).getCount());
        assertEquals(List.of(ActionReason.Combine), player.crafting.reasons);
    }

    @Test
    void successAdvancesTheHandbookAndSendsTheCanonicalTarget() throws Exception {
        assertNotNull(manager.combineItem(player, 11001, 2));
        assertEquals(2, progress());
        var packet =
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.PlayerInvestigationTargetNotify)
                        .findFirst()
                        .orElseThrow();
        var message = PlayerInvestigationTargetNotify.parseFrom(packet.getData());
        assertEquals(60027, message.getInvestigationTargetList(0).getQuestId());
        assertEquals(2, message.getInvestigationTargetList(0).getProgress());
    }

    @Test
    void repeatedRequestsPayForEachSuccessfulCraft() {
        assertNotNull(manager.combineItem(player, 11001, 1));
        assertNotNull(manager.combineItem(player, 11001, 1));
        assertNull(manager.combineItem(player, 11001, 1));
        assertEquals(2, count(112003));
        assertEquals(50, player.getMora());
        assertEquals(2, progress());
    }

    @Test
    void concurrentRequestsCannotSpendTheSameMaterialsOrOvercountProgress() throws Exception {
        player.getInventory().addItem(112002, 3);
        player.setMora(1000);
        var pool = Executors.newFixedThreadPool(4);
        try {
            var jobs = new ArrayList<Future<CombineResult>>();
            for (int i = 0; i < 8; i++)
                jobs.add(pool.submit(() -> manager.combineItem(player, 11001, 1)));
            int successes = 0;
            for (var job : jobs) if (job.get(5, TimeUnit.SECONDS) != null) successes++;
            assertEquals(3, successes);
            assertEquals(3, progress());
            assertEquals(3, count(112003));
            assertEquals(925, player.getMora());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void insufficientMaterialsDoNotGrantOrCount() {
        assertNull(manager.combineItem(player, 11001, 3));
        assertEquals(6, count(112002));
        assertEquals(100, player.getMora());
        assertEquals(0, progress());
    }

    @Test
    void insufficientMoraDoesNotConsumeMaterialsOrCount() {
        player.setMora(1);
        assertNull(manager.combineItem(player, 11001, 1));
        assertEquals(6, count(112002));
        assertEquals(0, count(112003));
        assertEquals(0, progress());
    }

    @Test
    void aMissingRecipeRepliesToTheRequesterWithoutRequiringAWorldHost() throws Exception {
        assertNull(manager.combineItem(player, 99999, 1));
        assertNotEquals(0, error());
        assertEquals(6, count(112002));
    }

    @Test
    void insufficientRankRepliesWithAnError() throws Exception {
        player.setProperty(PlayerProperty.PROP_PLAYER_LEVEL, 0);
        assertNull(manager.combineItem(player, 11001, 1));
        assertEquals(Retcode.RET_PLAYER_LEVEL_LESS_THAN_VALUE, error());
        assertEquals(0, progress());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0})
    void nonpositiveAmountsDoNotChangeStock(int amount) {
        assertNull(manager.combineItem(player, 11001, amount));
        assertEquals(6, count(112002));
        assertEquals(100, player.getMora());
        assertEquals(0, progress());
    }

    @Test
    void duplicateMaterialRowsAreChargedAsOneTotal() {
        definition(
                "{combineId:11001,resultItemId:112003,resultItemCount:1,scoinCost:0,materialItems:[{id:112002,count:4},{id:112002,count:4}],randomItems:[]}");
        assertNull(manager.combineItem(player, 11001, 1));
        assertEquals(6, count(112002));
        assertEquals(0, count(112003));
        assertEquals(0, progress());
    }

    @Test
    void moraInMaterialRowsAndTheSeparatePriceAreAddedBeforePayment() {
        definition(
                "{combineId:11001,resultItemId:112003,resultItemCount:1,scoinCost:25,materialItems:[{id:202,count:100}],randomItems:[]}");
        assertNull(manager.combineItem(player, 11001, 1));
        assertEquals(100, player.getMora());
        assertEquals(0, count(112003));
    }

    @Test
    void costMultiplicationCannotWrapToFreeMaterials() {
        recipe(4, 1, 0);
        assertNull(manager.combineItem(player, 11001, 1 << 30));
        assertEquals(6, count(112002));
        assertEquals(0, count(112003));
    }

    @Test
    void outputMultiplicationCannotConsumeCostsForAnInvalidCount() {
        recipe(3, 1 << 30, 25);
        assertNull(manager.combineItem(player, 11001, 2));
        assertEquals(6, count(112002));
        assertEquals(100, player.getMora());
    }

    @Test
    void aNegativePriceCannotRefundMora() {
        recipe(3, 1, -25);
        assertNull(manager.combineItem(player, 11001, 1));
        assertEquals(6, count(112002));
        assertEquals(100, player.getMora());
    }

    @Test
    void aMissingOutputDefinitionDoesNotEatIngredients() {
        definition(
                "{combineId:11001,resultItemId:999999,resultItemCount:1,scoinCost:25,materialItems:[{id:112002,count:3}],randomItems:[]}");
        assertNull(manager.combineItem(player, 11001, 1));
        assertEquals(6, count(112002));
        assertEquals(100, player.getMora());
        assertEquals(0, progress());
    }

    @Test
    void randomOnlyRecipesRemainUnclaimedBeforeAnyCostsAreSpent() {
        definition(
                "{combineId:11001,resultItemId:0,resultItemCount:1,scoinCost:0,materialItems:[{id:112002,count:3}],randomItems:[{id:112003,count:1}]}");
        assertNull(manager.combineItem(player, 11001, 1));
        assertEquals(6, count(112002));
        assertEquals(0, progress());
    }

    @Test
    void anUnexpectedRefusedGrantDoesNotReportSuccessOrAdvanceTheHandbook() throws Exception {
        player.crafting.refuseOutput = true;
        assertNull(manager.combineItem(player, 11001, 1));
        assertEquals(0, count(112003));
        assertEquals(0, progress());
        assertNotEquals(0, error());
    }

    @Test
    void fullInventoryIsRejectedBeforePaymentWhenNoSlotWillBeFreed() {
        capacity(1);
        assertNull(manager.combineItem(player, 11001, 1));
        assertEquals(6, count(112002));
        assertEquals(100, player.getMora());
        assertEquals(0, progress());
    }

    @Test
    void consumingTheLastInputStackCanFreeTheOutputSlot() {
        capacity(1);
        assertNotNull(manager.combineItem(player, 11001, 2));
        assertEquals(0, count(112002));
        assertEquals(2, count(112003));
        assertEquals(2, progress());
    }

    @Test
    void anExistingStackCannotSilentlyClampThePromisedOutput() {
        player.getInventory().addItem(112003, 999);
        player.crafting.reasons.clear();
        assertNull(manager.combineItem(player, 11001, 2));
        assertEquals(999, count(112003));
        assertEquals(6, count(112002));
        assertEquals(100, player.getMora());
    }

    @Test
    void aClosedHandbookDoesNotPreventOrdinaryCrafting() {
        player.getOpenStates().put(1101, 0);
        assertNotNull(manager.combineItem(player, 11001, 1));
        assertEquals(1, count(112003));
        assertEquals(0, progress());
    }

    private InvestigationRewardTest.ClaimSession request(byte[] payload) throws Exception {
        var server = environment.scene.getWorld().getServer();
        var field = GameServer.class.getDeclaredField("combineSystem");
        field.setAccessible(true);
        field.set(server, manager);
        var session = new InvestigationRewardTest.ClaimSession(server, player);
        new GameServerPacketHandler(PacketHandler.class)
                .handle(session, PacketOpcodes.CombineReq, new byte[0], payload);
        return session;
    }

    @Test
    void theRegisteredHandlerReportsTheCostsAndProgressOfTheSuccessfulBatch() throws Exception {
        var session =
                request(
                        CombineReq.newBuilder()
                                .setCombineId(11001)
                                .setCombineCount(2)
                                .build()
                                .toByteArray());
        var result = CombineRsp.parseFrom(session.responses.get(0).getData());
        assertEquals(0, result.getRetcode());
        assertEquals(2, result.getCombineCount());
        assertEquals(2, result.getCostItemListCount());
        assertEquals(1, result.getResultItemList(0).getCount()); // Native UI multiplies by count.
        assertEquals(2, progress());
        assertEquals(2, count(112003));
    }

    @Test
    void aTruncatedRequestReceivesAnErrorWithoutTouchingStock() throws Exception {
        var session = request(new byte[] {8, (byte) 0x80});
        assertEquals(1, session.responses.size());
        assertNotEquals(0, CombineRsp.parseFrom(session.responses.get(0).getData()).getRetcode());
        assertEquals(6, count(112002));
        assertEquals(0, progress());
    }

    @Test
    void anOutputCallbackCannotReenterTheSameCraft() {
        player.crafting.afterOutput = () -> assertNull(manager.combineItem(player, 11001, 1));
        assertNotNull(manager.combineItem(player, 11001, 1));
        assertEquals(3, count(112002));
        assertEquals(1, count(112003));
        assertEquals(1, progress());
    }

    @Test
    void aDeliveryExceptionKeepsThePlayerPendingForReconciliation() throws Exception {
        player.crafting.throwOutput = true;
        assertNull(manager.combineItem(player, 11001, 1));
        assertNotEquals(0, error());
        assertEquals(0, progress());
        player.crafting.throwOutput = false;
        assertNull(manager.combineItem(player, 11001, 1));
        assertEquals(0, progress());
        assertEquals(0, count(112003));
        assertEquals(3, count(112002));
    }

    @Test
    void anActualResourceRecipeAdvancesThroughTheInventoryAndHandbookEntry() throws Exception {
        var recipes =
                gson.fromJson(
                        Files.readString(
                                Path.of("resources/ExcelBinOutput/CombineExcelConfigData.json")),
                        CombineData[].class);
        var actual =
                Arrays.stream(recipes)
                        .filter(row -> row.getId() == 11001)
                        .findFirst()
                        .orElseThrow();
        actual.onLoad();
        GameData.getCombineDataMap().put(11001, actual);
        assertNotNull(manager.combineItem(player, 11001, 1));
        assertEquals(1, count(112003));
        assertEquals(3, count(112002));
        assertEquals(75, player.getMora());
        assertEquals(1, progress());
    }

    @Test
    void aFilteredRecipeTargetDoesNotAdvanceForAnotherRecipe() {
        GameData.getInvestigationTargetDataMap()
                .put(
                        60027,
                        gson.fromJson(
                                "{id:60027,investigationId:10004,progress:5,triggerConfig:{triggerType:'TRIGGER_DO_COMBINE',paramList:['11002','','']}}",
                                InvestigationTargetData.class));
        assertNotNull(manager.combineItem(player, 11001, 1));
        assertEquals(1, count(112003));
        assertEquals(0, progress());
    }

    @Test
    void aZeroPriceRecipeDoesNotRequireOrReportMora() {
        recipe(3, 1, 0);
        player.setMora(0);
        var result = manager.combineItem(player, 11001, 1);
        assertNotNull(result);
        assertEquals(1, result.getMaterial().size());
        assertEquals(112002, result.getMaterial().get(0).getId());
        assertEquals(0, player.getMora());
        assertEquals(1, progress());
    }

    @Test
    void outputCapacityIsCheckedAfterTheSameStackIsConsumed() {
        player.getInventory().addItem(112003, 1000);
        definition(
                "{combineId:11001,resultItemId:112003,resultItemCount:3,scoinCost:0,materialItems:[{id:112003,count:3}],randomItems:[]}");
        capacity(2);
        assertNotNull(manager.combineItem(player, 11001, 1));
        assertEquals(1000, count(112003));
        assertEquals(1, progress());
    }

    @Test
    void equipmentOutputsRemainDistinctInstancesInsteadOfLosingTheBatchQuantity() {
        GameData.getItemDataMap()
                .put(11101, gson.fromJson("{id:11101,itemType:'ITEM_WEAPON'}", ItemData.class));
        definition(
                "{combineId:11001,resultItemId:11101,resultItemCount:2,scoinCost:25,materialItems:[{id:112002,count:3}],randomItems:[]}");
        assertNotNull(manager.combineItem(player, 11001, 1));
        var weapons = new ArrayList<GameItem>();
        for (var item : player.getInventory())
            if (item.getItemType() == ItemType.ITEM_WEAPON) weapons.add(item);
        assertEquals(2, weapons.size());
        assertNotEquals(weapons.get(0).getGuid(), weapons.get(1).getGuid());
        assertTrue(
                weapons.stream()
                        .allMatch(
                                item ->
                                        item.getCount() == 1
                                                && item.getOwnerId() == player.getUid()));
        assertEquals(1, progress());
    }
}
