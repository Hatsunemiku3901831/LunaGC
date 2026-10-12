package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.database.*;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.managers.cooking.*;
import emu.grasscutter.game.props.PlayerProperty;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.PlayerCompoundMaterialReqOuterClass.PlayerCompoundMaterialReq;
import emu.grasscutter.net.proto.PlayerInvestigationTargetNotifyOuterClass.PlayerInvestigationTargetNotify;
import emu.grasscutter.net.proto.TakeCompoundOutputReqOuterClass.TakeCompoundOutputReq;
import emu.grasscutter.net.proto.TakeCompoundOutputRspOuterClass.TakeCompoundOutputRsp;
import emu.grasscutter.utils.*;

import org.junit.jupiter.api.*;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

/** Completed, delivered processing units advance the actual handbook target. */
class CompoundHandbookTest {
    private AbilityTestFixture environment;
    private CompoundQueueFlowTest.ProcessPlayer player;
    private final Gson gson = new Gson();
    private Map<Integer, CompoundData> oldRecipes;
    private Map<Integer, ItemData> oldItems;
    private Map<Integer, InvestigationData> oldChapters;
    private Map<Integer, InvestigationTargetData> oldTargets;
    private Object oldDatastore;
    private java.lang.reflect.Field datastore;
    private int confirmed, failSave;
    private final List<Integer> confirmedProgress = new ArrayList<>();

    @BeforeEach
    void setup() throws Exception {
        environment = new AbilityTestFixture();
        oldRecipes = new HashMap<>(GameData.getCompoundDataMap());
        oldItems = new HashMap<>(GameData.getItemDataMap());
        oldChapters = new HashMap<>(GameData.getInvestigationDataMap());
        oldTargets = new HashMap<>(GameData.getInvestigationTargetDataMap());
        GameData.getCompoundDataMap().clear();
        GameData.getItemDataMap().clear();
        GameData.getInvestigationDataMap().clear();
        GameData.getInvestigationTargetDataMap().clear();
        chapter();
        target(60017, "");
        recipe(1001, 1, 1);
        recipe(1002, 2, 1);
        for (int id : List.of(100017, 110001, 110002))
            GameData.getItemDataMap()
                    .put(
                            id,
                            gson.fromJson(
                                    "{id:"
                                            + id
                                            + ",itemType:'ITEM_MATERIAL',materialType:'MATERIAL_NONE',stackLimit:1000}",
                                    ItemData.class));
        CookingCompoundManager.initialize();
        player = new CompoundQueueFlowTest.ProcessPlayer();
        player.setProperty(PlayerProperty.PROP_PLAYER_LEVEL, 45);
        player.getOpenStates().put(1100, 1);
        player.getOpenStates().put(1101, 1);
        datastore = DatabaseManager.class.getDeclaredField("gameDatastore");
        datastore.setAccessible(true);
        oldDatastore = datastore.get(null);
        datastore.set(
                null,
                Proxy.newProxyInstance(
                        dev.morphia.Datastore.class.getClassLoader(),
                        new Class<?>[] {dev.morphia.Datastore.class},
                        (proxy, method, args) -> {
                            if (!method.getName().equals("save")) return null;
                            if (args.length == 2) {
                                if (++confirmed == failSave)
                                    throw new IllegalStateException("Confirmation fault");
                                if (args[0] == player) confirmedProgress.add(progress());
                            }
                            return args[0];
                        }));
        assertTrue(player.getInventory().addItem(100017, 20));
        player.packets.clear();
    }

    @AfterEach
    void cleanup() throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        if (datastore != null) datastore.set(null, oldDatastore);
        GameData.getCompoundDataMap().clear();
        GameData.getCompoundDataMap().putAll(oldRecipes);
        GameData.getItemDataMap().clear();
        GameData.getItemDataMap().putAll(oldItems);
        GameData.getInvestigationDataMap().clear();
        GameData.getInvestigationDataMap().putAll(oldChapters);
        GameData.getInvestigationTargetDataMap().clear();
        GameData.getInvestigationTargetDataMap().putAll(oldTargets);
        CookingCompoundManager.initialize();
        environment.close();
    }

    private void chapter() {
        GameData.getInvestigationDataMap()
                .put(
                        10003,
                        gson.fromJson(
                                "{id:10003,investigationType:'INVESTIGATION_ADVENTURE',unlockLevel:1,unlockOpenStateType:'OPEN_ADVENTURE_MANUAL_CITY_MENGDE'}",
                                InvestigationData.class));
    }

    private void target(int id, String filter) {
        GameData.getInvestigationTargetDataMap()
                .put(
                        id,
                        gson.fromJson(
                                "{id:"
                                        + id
                                        + ",investigationId:10003,progress:3,triggerConfig:{triggerType:'TRIGGER_DO_COMPOUND',paramList:['"
                                        + filter
                                        + "','','']}}",
                                InvestigationTargetData.class));
    }

    private void recipe(int id, int group, int output) {
        GameData.getCompoundDataMap()
                .put(
                        id,
                        gson.fromJson(
                                "{id:"
                                        + id
                                        + ",groupID:"
                                        + group
                                        + ",isDefaultUnlocked:true,costTime:60,queueSize:99,inputVec:[{id:100017,count:1}],outputVec:[{id:"
                                        + (id == 1001 ? 110001 : 110002)
                                        + ",count:"
                                        + output
                                        + "}]}",
                                CompoundData.class));
    }

    private int progress() {
        return player.getPlayerProgress().getInvestigation().get(60017);
    }

    private void ready(int id, int count) {
        player.getActiveCookCompounds()
                .put(
                        id,
                        new ActiveCookCompoundData(
                                id, 60, count, Utils.getCurrentSeconds() - count * 60 - 1));
    }

    private int take(int group, int id, boolean all) throws Exception {
        player.getCookingCompoundManager()
                .handleTakeCompoundOutputReq(
                        TakeCompoundOutputReq.newBuilder()
                                .setCompoundGroupId(group)
                                .setCompoundId(id)
                                .setIsClaimAll(all)
                                .build());
        var packet =
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.TakeCompoundOutputRsp)
                        .reduce((a, b) -> b)
                        .orElseThrow();
        return TakeCompoundOutputRsp.parseFrom(packet.getData()).getRetcode();
    }

    @Test
    void enqueueDoesNotAdvanceCompletedProcessing() {
        player.getCookingCompoundManager()
                .handlePlayerCompoundMaterialReq(
                        PlayerCompoundMaterialReq.newBuilder()
                                .setCompoundId(1001)
                                .setCount(3)
                                .build());
        assertEquals(3, player.getActiveCookCompounds().get(1001).getTotalCount());
        assertEquals(0, progress());
    }

    @Test
    void unfinishedQueueDoesNotAdvance() throws Exception {
        player.getActiveCookCompounds()
                .put(1001, new ActiveCookCompoundData(1001, 60, 3, Utils.getCurrentSeconds()));
        assertNotEquals(0, take(1, 0, false));
        assertEquals(0, progress());
    }

    @Test
    void successfulClaimCountsProcessingUnitsAndSendsProgress() throws Exception {
        ready(1001, 2);
        assertEquals(0, take(1, 0, false));
        assertEquals(2, progress());
        var packet =
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.PlayerInvestigationTargetNotify)
                        .reduce((a, b) -> b)
                        .orElseThrow();
        var target =
                PlayerInvestigationTargetNotify.parseFrom(packet.getData())
                        .getInvestigationTargetListList()
                        .stream()
                        .filter(t -> t.getQuestId() == 60017)
                        .findFirst()
                        .orElseThrow();
        assertEquals(2, target.getProgress());
        assertEquals(2, confirmedProgress.get(confirmedProgress.size() - 1));
    }

    @Test
    void actualFlourAndHandbookResourcesCompleteThreeUnits() throws Exception {
        var root = Path.of("resources/ExcelBinOutput");
        var compound =
                JsonUtils.loadToList(
                                root.resolve("CompoundExcelConfigData.json"), CompoundData.class)
                        .stream()
                        .filter(d -> d.getId() == 1001)
                        .findFirst()
                        .orElseThrow();
        var chapter =
                JsonUtils.loadToList(
                                root.resolve("InvestigationConfigData.json"),
                                InvestigationData.class)
                        .stream()
                        .filter(d -> d.getId() == 10003)
                        .findFirst()
                        .orElseThrow();
        var target =
                JsonUtils.loadToList(
                                root.resolve("InvestigationTargetConfigData.json"),
                                InvestigationTargetData.class)
                        .stream()
                        .filter(d -> d.getId() == 60017)
                        .findFirst()
                        .orElseThrow();
        assertEquals(3, target.getProgress());
        assertEquals("TRIGGER_DO_COMPOUND", target.getTriggerConfig().getTriggerType());
        GameData.getCompoundDataMap().put(1001, compound);
        GameData.getInvestigationDataMap().put(10003, chapter);
        GameData.getInvestigationTargetDataMap().put(60017, target);
        ready(1001, 3);
        assertEquals(0, take(1, 0, false));
        assertEquals(3, progress());
        assertEquals(3, player.getInventory().getItemCountById(110001));
    }

    @Test
    void multipleOutputItemsDoNotInflateProcessingCount() throws Exception {
        recipe(1001, 1, 5);
        ready(1001, 1);
        assertEquals(0, take(1, 0, false));
        assertEquals(1, progress());
        assertEquals(5, player.getInventory().getItemCountById(110001));
    }

    @Test
    void claimAllCountsFinishedUnitsAcrossGroups() throws Exception {
        ready(1001, 1);
        ready(1002, 2);
        assertEquals(0, take(0, 0, true));
        assertEquals(3, progress());
    }

    @Test
    void partialClaimCountsOnlyReadyPart() throws Exception {
        player.getActiveCookCompounds()
                .put(
                        1001,
                        new ActiveCookCompoundData(1001, 60, 3, Utils.getCurrentSeconds() - 121));
        assertEquals(0, take(1, 0, false));
        assertEquals(2, progress());
        assertEquals(1, player.getActiveCookCompounds().get(1001).getTotalCount());
    }

    @Test
    void repeatedClaimCannotCountAgain() throws Exception {
        ready(1001, 1);
        assertEquals(0, take(1, 0, false));
        assertNotEquals(0, take(1, 0, false));
        assertEquals(1, progress());
    }

    @Test
    void fullBagDoesNotAdvance() throws Exception {
        ready(1001, 2);
        player.getInventory()
                .getInventoryTypes()
                .put(ItemType.ITEM_MATERIAL.getValue(), new MaterialInventoryTab(0));
        assertNotEquals(0, take(1, 0, false));
        assertEquals(0, progress());
    }

    @Test
    void refusedOutputDoesNotAdvance() throws Exception {
        ready(1001, 2);
        player.refuse = true;
        assertNotEquals(0, take(1, 0, false));
        assertEquals(0, progress());
    }

    @Test
    void thrownOutputDoesNotAdvance() throws Exception {
        ready(1001, 2);
        player.throwGrant = true;
        assertNotEquals(0, take(1, 0, false));
        assertEquals(0, progress());
    }

    @Test
    void failedReservationDoesNotAdvance() throws Exception {
        ready(1001, 2);
        failSave = 1;
        assertNotEquals(0, take(1, 0, false));
        assertEquals(0, progress());
    }

    @Test
    void failedReceiptConfirmationCannotCountOnRetry() throws Exception {
        ready(1001, 1);
        failSave = 4;
        assertNotEquals(0, take(1, 0, false));
        assertEquals(1, progress());
        failSave = 0;
        assertNotEquals(0, take(1, 0, false));
        assertEquals(1, progress());
        assertEquals(1, player.getInventory().getItemCountById(110001));
    }

    @Test
    void reentrantClaimCannotCountTwice() throws Exception {
        ready(1001, 1);
        player.afterGrant =
                () -> {
                    try {
                        assertNotEquals(0, take(1, 0, false));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                };
        assertEquals(0, take(1, 0, false));
        assertEquals(1, progress());
    }

    @Test
    void closedHandbookDoesNotAdvance() throws Exception {
        player.getOpenStates().put(1100, 0);
        ready(1001, 2);
        assertEquals(0, take(1, 0, false));
        assertEquals(0, progress());
    }

    @Test
    void closedRegionDoesNotAdvance() throws Exception {
        player.getOpenStates().put(1101, 0);
        ready(1001, 2);
        assertEquals(0, take(1, 0, false));
        assertEquals(0, progress());
    }

    @Test
    void recipeFiltersReceiveOnlyMatchingCompletedUnits() throws Exception {
        target(60017, "1001");
        target(99002, "1002");
        ready(1001, 1);
        ready(1002, 2);
        assertEquals(0, take(0, 0, true));
        assertEquals(1, progress());
        assertEquals(2, player.getPlayerProgress().getInvestigation().get(99002));
    }

    @Test
    void targetProgressStopsAtResourceTotal() throws Exception {
        ready(1001, 5);
        assertEquals(0, take(1, 0, false));
        assertEquals(3, progress());
    }
}
