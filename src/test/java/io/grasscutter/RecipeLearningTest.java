package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;

import emu.grasscutter.data.*;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.database.*;
import emu.grasscutter.game.combine.CombineManger;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.managers.cooking.CookingManager;
import emu.grasscutter.game.managers.forging.ForgingManager;
import emu.grasscutter.game.props.*;
import emu.grasscutter.game.props.ItemUseAction.*;
import emu.grasscutter.game.systems.InventorySystem;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

/**
 * Recipe learning must validate eligibility and real item consumption through the inventory entry.
 */
class RecipeLearningTest {
    private emu.grasscutter.game.inventory.ObservedItemTestWrites itemWrites;
    private emu.grasscutter.game.player.GuardedPlayerTestStore playerStore;

    enum Kind {
        COMBINE(ItemUseOp.ITEM_USE_UNLOCK_COMBINE, PacketOpcodes.CombineFormulaDataNotify),
        FORGE(ItemUseOp.ITEM_USE_UNLOCK_FORGE, PacketOpcodes.ForgeFormulaDataNotify),
        COOK(ItemUseOp.ITEM_USE_UNLOCK_COOK_RECIPE, PacketOpcodes.CookRecipeDataNotify);
        final ItemUseOp op;
        final int opcode;

        Kind(ItemUseOp op, int opcode) {
            this.op = op;
            this.opcode = opcode;
        }
    }

    static class LearningPlayer extends InvestigationRewardTest.ClaimPlayer {
        transient boolean failLearningNotification;

        @Override
        public void sendPacket(emu.grasscutter.net.packet.BasePacket packet) {
            if (failLearningNotification && LearningPersistenceChecks.unlockNotification(packet))
                throw new IllegalStateException("Learning notification unavailable");
            super.sendPacket(packet);
        }

        transient boolean refuseRemoval, pretendRemoval;
        final transient ForgingManager forging = new ForgingManager(this);
        final transient CookingManager cooking = new CookingManager(this);
        final transient Inventory learningInventory =
                new Inventory(this) {
                    @Override
                    public boolean removeItem(GameItem item, int count) {
                        if (refuseRemoval) return false;
                        if (pretendRemoval) return true;
                        return super.removeItem(item, count);
                    }
                };

        @Override
        public Inventory getInventory() {
            return learningInventory;
        }

        @Override
        public ForgingManager getForgingManager() {
            return forging;
        }

        @Override
        public CookingManager getCookingManager() {
            return cooking;
        }
    }

    private AbilityTestFixture environment;
    private LearningPlayer player;
    private CombineManger combine;
    private InventorySystem inventory;
    private Field datastore;
    private Object oldDatastore;
    private final Gson gson = new Gson();
    private final Map<Map<?, ?>, Map<?, ?>> oldMaps = new IdentityHashMap<>();

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void preserveAndClear(Map map) {
        oldMaps.put(map, new HashMap<>(map));
        map.clear();
    }

    @BeforeEach
    void setup() throws Exception {
        environment = new AbilityTestFixture();
        player = new LearningPlayer();
        var server = environment.scene.getWorld().getServer();
        player.setSession(new InvestigationRewardTest.ClaimSession(server, player));
        combine = new CombineManger(server);
        inventory = new InventorySystem(server);
        var field = server.getClass().getDeclaredField("combineSystem");
        field.setAccessible(true);
        field.set(server, combine);
        preserveAndClear(GameData.getItemDataMap());
        preserveAndClear(GameData.getCombineDataMap());
        preserveAndClear(GameData.getForgeDataMap());
        preserveAndClear(GameData.getCookRecipeDataMap());
        player.getUnlockedCombines().clear();
        player.getUnlockedForgingBlueprints().clear();
        player.getUnlockedRecipies().clear();
        recipe(Kind.COMBINE, 200, false);
        recipe(Kind.FORGE, 200, false);
        recipe(Kind.COOK, 200, false);
        datastore = DatabaseManager.class.getDeclaredField("gameDatastore");
        datastore.setAccessible(true);
        oldDatastore = datastore.get(null);
        playerStore = new emu.grasscutter.game.player.GuardedPlayerTestStore(player, () -> {});
        datastore.set(
                null,
                Proxy.newProxyInstance(
                        dev.morphia.Datastore.class.getClassLoader(),
                        new Class<?>[] {dev.morphia.Datastore.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("getMapper"))
                                return playerStore.datastore().getMapper();
                            if (method.getName().equals("save"))
                                return method.invoke(playerStore.datastore(), args);
                            if (method.getName().equals("delete"))
                                return com.mongodb.client.result.DeleteResult.acknowledged(1);
                            return null;
                        }));
        itemWrites =
                new emu.grasscutter.game.inventory.ObservedItemTestWrites(
                        player,
                        playerStore.datastore(),
                        DatabaseManager.getGameDatastore(),
                        doc -> {},
                        doc -> {});
    }

    @AfterEach
    @SuppressWarnings({"rawtypes", "unchecked"})
    void cleanup() throws Exception {
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        if (itemWrites != null) itemWrites.close();
        if (playerStore != null) playerStore.close();
        if (datastore != null) datastore.set(null, oldDatastore);
        oldMaps.forEach(
                (map, old) -> {
                    ((Map) map).clear();
                    ((Map) map).putAll(old);
                });
        environment.close();
    }

    private void recipe(Kind kind, int id, boolean defaultAvailable) {
        switch (kind) {
            case COMBINE -> GameData.getCombineDataMap()
                    .put(
                            id,
                            gson.fromJson(
                                    "{combineId:" + id + ",isDefaultShow:" + defaultAvailable + "}",
                                    CombineData.class));
            case FORGE -> GameData.getForgeDataMap()
                    .put(
                            id,
                            gson.fromJson(
                                    "{id:" + id + ",isDefaultShow:" + defaultAvailable + "}",
                                    ForgeData.class));
            case COOK -> GameData.getCookRecipeDataMap()
                    .put(
                            id,
                            gson.fromJson(
                                    "{id:" + id + ",isDefaultUnlocked:" + defaultAvailable + "}",
                                    CookRecipeData.class));
        }
    }

    private GameItem book(Kind kind, String parameter) {
        var data =
                JsonUtils.decode(
                        JsonParser.parseString(
                                        "{id:60001,itemType:'ITEM_MATERIAL',materialType:'MATERIAL_CONSUME',stackLimit:99,maxUseCount:1,useTarget:'ITEM_USE_TARGET_NONE',itemUse:[{useOp:'"
                                                + kind.op.name()
                                                + "',useParam:['"
                                                + parameter
                                                + "']}]}")
                                .getAsJsonObject(),
                        ItemData.class);
        data.onLoad();
        GameData.getItemDataMap().put(data.getId(), data);
        assertTrue(player.getInventory().addItem(data.getId(), 2));
        player.packets.clear();
        return player.getInventory().getItemById(data.getId());
    }

    private boolean known(Kind kind, int id) {
        return switch (kind) {
            case COMBINE -> player.getUnlockedCombines().contains(id);
            case FORGE -> player.getUnlockedForgingBlueprints().contains(id);
            case COOK -> player.getUnlockedRecipies().containsKey(id);
        };
    }

    private void markKnown(Kind kind, int id) {
        switch (kind) {
            case COMBINE -> player.getUnlockedCombines().add(id);
            case FORGE -> player.getUnlockedForgingBlueprints().add(id);
            case COOK -> player.getUnlockedRecipies().put(id, 0);
        }
    }

    private GameItem use(GameItem item, int count) {
        return assertDoesNotThrow(
                () -> inventory.useItem(player, 0, item.getGuid(), count, 0, false));
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void learningConsumesExactlyOneBookAndNotifiesOnce(Kind kind) {
        var item = book(kind, "200");
        assertSame(item, use(item, 1));
        assertEquals(1, item.getCount());
        assertTrue(known(kind, 200));
        assertEquals(
                1,
                player.packets.stream()
                        .filter(packet -> packet.getOpcode() == kind.opcode)
                        .count());
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void learningPersistsItsFlagAndExactBookDebit(Kind kind) throws Exception {
        var source = book(kind, "200");
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        DatabaseHelper.saveGameSync(player);
        assertSame(source, use(source, 1));
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        var profile =
                playerStore
                        .datastore()
                        .getMapper()
                        .getCollection(emu.grasscutter.game.player.Player.class)
                        .withDocumentClass(org.bson.Document.class)
                        .find(new org.bson.Document("_id", player.getUid()))
                        .first();
        assertNotNull(profile);
        boolean saved =
                switch (kind) {
                    case COMBINE -> profile.getList("unlockedCombines", Integer.class, List.of())
                            .contains(200);
                    case FORGE -> profile.getList(
                                    "unlockedForgingBlueprints", Integer.class, List.of())
                            .contains(200);
                    case COOK -> Optional.ofNullable(
                                    profile.get("unlockedRecipies", org.bson.Document.class))
                            .orElseGet(org.bson.Document::new)
                            .containsKey("200");
                };
        assertTrue(saved, "The learned recipe must be persisted with its acknowledged consumption");
        var item =
                playerStore
                        .datastore()
                        .getMapper()
                        .getCollection(GameItem.class)
                        .withDocumentClass(org.bson.Document.class)
                        .find(new org.bson.Document("_id", source.getObjectId()))
                        .first();
        assertNotNull(item);
        assertEquals(1, item.getInteger("count"));
        assertNull(profile.get("pendingItemUse"));
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void alreadyLearnedRecipePreservesTheBook(Kind kind) {
        var item = book(kind, "200");
        markKnown(kind, 200);
        assertNull(use(item, 1));
        assertEquals(2, item.getCount());
        assertTrue(known(kind, 200));
        assertTrue(player.packets.stream().noneMatch(packet -> packet.getOpcode() == kind.opcode));
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void unknownRecipePreservesTheBookAndDoesNotPolluteUnlocks(Kind kind) {
        var item = book(kind, "999999");
        assertNull(use(item, 1));
        assertEquals(2, item.getCount());
        assertFalse(known(kind, 999999));
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void defaultRecipeDoesNotConsumeALearningBook(Kind kind) {
        recipe(kind, 200, true);
        var item = book(kind, "200");
        assertNull(use(item, 1));
        assertEquals(2, item.getCount());
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void invalidCountsDoNotLearnOrConsume(Kind kind) {
        var item = book(kind, "200");
        for (int count : List.of(0, -1, 2)) {
            assertNull(use(item, count));
            assertEquals(2, item.getCount());
            assertFalse(known(kind, 200));
        }
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void refusedRemovalDoesNotUnlock(Kind kind) {
        var item = book(kind, "200");
        player.refuseRemoval = true;
        assertNull(use(item, 1));
        assertFalse(known(kind, 200));
        assertEquals(2, item.getCount());
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void pretendRemovalDoesNotUnlock(Kind kind) {
        var item = book(kind, "200");
        player.pretendRemoval = true;
        assertNull(use(item, 1));
        assertFalse(known(kind, 200));
        assertEquals(2, item.getCount());
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void malformedRecipeParameterDoesNotLearnZero(Kind kind) {
        var item = book(kind, "not-a-number");
        assertNull(use(item, 1));
        assertFalse(known(kind, 0));
        assertEquals(2, item.getCount());
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void definitionStoredUnderTheWrongKeyCannotBeLearned(Kind kind) {
        recipe(kind, 201, false);
        switch (kind) {
            case COMBINE -> GameData.getCombineDataMap()
                    .put(200, GameData.getCombineDataMap().get(201));
            case FORGE -> GameData.getForgeDataMap().put(200, GameData.getForgeDataMap().get(201));
            case COOK -> GameData.getCookRecipeDataMap()
                    .put(200, GameData.getCookRecipeDataMap().get(201));
        }
        var item = book(kind, "200");
        assertNull(use(item, 1));
        assertEquals(2, item.getCount());
        assertFalse(known(kind, 200));
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void directUnlockAlsoRejectsUnknownRecipes(Kind kind) {
        boolean result =
                switch (kind) {
                    case COMBINE -> combine.unlockCombineDiagram(player, 999999);
                    case FORGE -> player.getForgingManager().unlockForgingBlueprint(999999);
                    case COOK -> player.getCookingManager().unlockRecipe(999999);
                };
        assertFalse(result);
        assertFalse(known(kind, 999999));
    }

    @Test
    void foreignInventoryOwnerCannotUseTheBook() throws Exception {
        var item = book(Kind.COMBINE, "200");
        var field = GameItem.class.getDeclaredField("ownerId");
        field.setAccessible(true);
        field.setInt(item, 10002);
        assertNull(use(item, 1));
        assertEquals(2, item.getCount());
        assertFalse(known(Kind.COMBINE, 200));
    }

    @Test
    void malformedNullParameterArraysAreHarmless() {
        var actions =
                assertDoesNotThrow(
                        () ->
                                List.of(
                                        new ItemUseUnlockCombine(null),
                                        new ItemUseUnlockForge(null),
                                        new ItemUseUnlockCookRecipe(null)));
        var params = new UseItemParams(player, ItemUseTarget.ITEM_USE_TARGET_NONE);
        actions.forEach(action -> assertFalse(action.useItem(params)));
    }

    @Test
    void nullPlayerOrParametersAreRejectedWithoutThrowing() {
        var action = new ItemUseUnlockCombine(new String[] {"200"});
        assertFalse(assertDoesNotThrow(() -> action.useItem(null)));
        assertFalse(
                assertDoesNotThrow(
                        () ->
                                action.useItem(
                                        new UseItemParams(
                                                null, ItemUseTarget.ITEM_USE_TARGET_NONE))));
    }

    @Test
    void realAlchemyDiagramLearnsItsActualResourceRecipe() throws Exception {
        var materials =
                JsonParser.parseString(
                                Files.readString(
                                        Path.of(
                                                "resources/ExcelBinOutput/MaterialExcelConfigData.json")))
                        .getAsJsonArray();
        var recipes =
                JsonParser.parseString(
                                Files.readString(
                                        Path.of(
                                                "resources/ExcelBinOutput/CombineExcelConfigData.json")))
                        .getAsJsonArray();
        var rawBook =
                java.util.stream.StreamSupport.stream(materials.spliterator(), false)
                        .map(JsonElement::getAsJsonObject)
                        .filter(row -> row.get("id").getAsInt() == 221001)
                        .findFirst()
                        .orElseThrow();
        var rawRecipe =
                java.util.stream.StreamSupport.stream(recipes.spliterator(), false)
                        .map(JsonElement::getAsJsonObject)
                        .filter(row -> row.get("combineId").getAsInt() == 22001)
                        .findFirst()
                        .orElseThrow();
        var data = JsonUtils.decode(rawBook, ItemData.class);
        data.onLoad();
        GameData.getItemDataMap().put(data.getId(), data);
        GameData.getCombineDataMap().put(22001, JsonUtils.decode(rawRecipe, CombineData.class));
        assertTrue(player.getInventory().addItem(221001, 1));
        var item = player.getInventory().getItemById(221001);
        assertSame(item, use(item, 1));
        assertTrue(player.getUnlockedCombines().contains(22001));
        assertNull(player.getInventory().getItemByGuid(item.getGuid()));
    }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments>
            learningFailureCases() {
        return java.util.Arrays.stream(Kind.values())
                .flatMap(
                        kind ->
                                java.util.stream.Stream.of(
                                                "intent",
                                                "debit",
                                                "paid",
                                                "flag",
                                                "confirmed",
                                                "clear")
                                        .flatMap(
                                                phase ->
                                                        java.util.stream.Stream.of(false, true)
                                                                .map(
                                                                        after ->
                                                                                org.junit.jupiter
                                                                                        .params
                                                                                        .provider
                                                                                        .Arguments
                                                                                        .of(
                                                                                                kind,
                                                                                                phase,
                                                                                                after))));
    }

    private GameItem interruptLearning(Kind kind, String phase, boolean after) throws Exception {
        var source = book(kind, "200");
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        DatabaseHelper.saveGameSync(player);
        var fired = new java.util.concurrent.atomic.AtomicBoolean();
        inventory =
                new InventorySystem(
                        environment.scene.getWorld().getServer(),
                        () -> 100000,
                        LearningPersistenceChecks.failure(player, phase, after, fired));
        assertNull(use(source, 1));
        assertTrue(fired.get(), "The requested business acknowledgement must have been reached");
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        assertNotNull(player.getPendingItemUse());
        return source;
    }

    private void coldLearningPlayer() throws Exception {
        var fresh = new LearningPlayer();
        fresh.setSession(
                new InvestigationRewardTest.ClaimSession(
                        environment.scene.getWorld().getServer(), fresh));
        LearningPersistenceChecks.restore(fresh, playerStore.datastore());
        player = fresh;
        inventory = new InventorySystem(environment.scene.getWorld().getServer());
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("learningFailureCases")
    void interruptedLearningResumesWithoutAnotherBook(Kind kind, String phase, boolean after)
            throws Exception {
        var source = interruptLearning(kind, phase, after);
        String operation = player.getPendingItemUse().getOperationId();
        int beforeRetry = player.getInventory().getItemCountById(source.getItemId());
        assertNull(use(source, 1));
        assertEquals(beforeRetry, player.getInventory().getItemCountById(source.getItemId()));
        if (phase.equals("clear") && after)
            new ItemUseRecovery(player).resolve(operation, "resume");
        coldLearningPlayer();
        LearningPersistenceChecks.finishOrRetry(
                player,
                playerStore.datastore(),
                source.getObjectId(),
                () -> assertNotNull(use(player.getInventory().getItemById(source.getItemId()), 1)));
        assertTrue(known(kind, 200));
        assertEquals(2 - 1, player.getInventory().getItemCountById(source.getItemId()));
        assertEquals(
                2 - 1,
                LearningPersistenceChecks.item(playerStore.datastore(), source.getObjectId())
                        .getInteger("count"));
        assertThrows(
                IllegalStateException.class,
                () -> new ItemUseRecovery(player).resolve(operation, "resume"));
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void paidLearningProofSurvivesLaterBookRefills(Kind kind) throws Exception {
        var source = interruptLearning(kind, "paid", true);
        coldLearningPlayer();
        String operation = player.getPendingItemUse().getOperationId();
        assertTrue(player.getInventory().addItem(source.getItemId(), 5));
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        var recovery = new ItemUseRecovery(player);
        assertThrows(
                IllegalStateException.class, () -> recovery.resolve("another-operation", "resume"));
        String profileBefore =
                LearningPersistenceChecks.profile(playerStore.datastore(), player).toJson();
        String itemBefore =
                LearningPersistenceChecks.item(playerStore.datastore(), source.getObjectId())
                        .toJson();
        assertTrue(recovery.inspect().stream().anyMatch(row -> row.contains(operation)));
        assertEquals(
                profileBefore,
                LearningPersistenceChecks.profile(playerStore.datastore(), player).toJson());
        assertEquals(
                itemBefore,
                LearningPersistenceChecks.item(playerStore.datastore(), source.getObjectId())
                        .toJson());
        recovery.resolve(operation, "resume");
        assertTrue(known(kind, 200));
        assertEquals(2 - 1 + 5, player.getInventory().getItemCountById(source.getItemId()));
        assertEquals(
                operation,
                LearningPersistenceChecks.item(playerStore.datastore(), source.getObjectId())
                        .getString("recipeUseOperationId"));
        assertNull(player.getPendingItemUse());
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void unspentLearningCanBeCancelledBeforeTryingAgain(Kind kind) throws Exception {
        var source = interruptLearning(kind, "intent", true);
        coldLearningPlayer();
        String operation = player.getPendingItemUse().getOperationId();
        new ItemUseRecovery(player).resolve(operation, "cancel");
        assertFalse(known(kind, 200));
        assertEquals(2, player.getInventory().getItemCountById(source.getItemId()));
        assertNull(
                LearningPersistenceChecks.receipt(
                        LearningPersistenceChecks.profile(playerStore.datastore(), player)));
        assertNotNull(use(player.getInventory().getItemById(source.getItemId()), 1));
        assertTrue(known(kind, 200));
        assertEquals(2 - 1, player.getInventory().getItemCountById(source.getItemId()));
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void missingDebitProofCannotBeReplacedByAQuantityGuess(Kind kind) throws Exception {
        var source = interruptLearning(kind, "paid", true);
        var rows =
                playerStore
                        .datastore()
                        .getMapper()
                        .getCollection(GameItem.class)
                        .withDocumentClass(org.bson.Document.class);
        rows.updateOne(
                new org.bson.Document("_id", source.getObjectId()),
                new org.bson.Document("$unset", new org.bson.Document("recipeUseOperationId", "")));
        coldLearningPlayer();
        String operation = player.getPendingItemUse().getOperationId();
        String beforeProfile =
                LearningPersistenceChecks.profile(playerStore.datastore(), player).toJson();
        assertThrows(
                IllegalStateException.class,
                () -> new ItemUseRecovery(player).resolve(operation, "resume"));
        assertEquals(
                beforeProfile,
                LearningPersistenceChecks.profile(playerStore.datastore(), player).toJson());
        assertFalse(known(kind, 200));
        assertEquals(2 - 1, player.getInventory().getItemCountById(source.getItemId()));
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void nativeManagementCommandChecksPermissionsThenResumesTheSameLearning(Kind kind)
            throws Exception {
        var source = interruptLearning(kind, "paid", true);
        coldLearningPlayer();
        LearningPersistenceChecks.commandResume(player, playerStore.datastore());
        assertTrue(known(kind, 200));
        assertEquals(2 - 1, player.getInventory().getItemCountById(source.getItemId()));
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void changedBookDefinitionCannotRedirectTheStoredLearningPlan(Kind kind) throws Exception {
        var source = interruptLearning(kind, "intent", true);
        var changed =
                JsonUtils.decode(
                        "{id:60001,itemType:'ITEM_MATERIAL',materialType:'MATERIAL_CONSUME',stackLimit:99,useTarget:'ITEM_USE_TARGET_NONE',itemUse:[{useOp:'"
                                + kind.op.name()
                                + "',useParam:['999999']}]}",
                        ItemData.class);
        changed.onLoad();
        GameData.getItemDataMap().put(source.getItemId(), changed);
        coldLearningPlayer();
        String operation = player.getPendingItemUse().getOperationId();
        String beforeProfile =
                LearningPersistenceChecks.profile(playerStore.datastore(), player).toJson();
        String beforeItem =
                LearningPersistenceChecks.item(playerStore.datastore(), source.getObjectId())
                        .toJson();
        assertThrows(
                IllegalStateException.class,
                () -> new ItemUseRecovery(player).resolve(operation, "resume"));
        assertEquals(
                beforeProfile,
                LearningPersistenceChecks.profile(playerStore.datastore(), player).toJson());
        assertEquals(
                beforeItem,
                LearningPersistenceChecks.item(playerStore.datastore(), source.getObjectId())
                        .toJson());
        assertFalse(known(kind, 200));
        new ItemUseRecovery(player).resolve(operation, "cancel");
        assertNull(player.getPendingItemUse());
        assertEquals(2, player.getInventory().getItemCountById(source.getItemId()));
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void aLostUnlockNotificationCannotTurnPersistedLearningIntoAnotherCost(Kind kind)
            throws Exception {
        var source = book(kind, "200");
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        DatabaseHelper.saveGameSync(player);
        player.failLearningNotification = true;
        player.setSession(
                new LearningPersistenceChecks.MissingUnlockNotification(
                        environment.scene.getWorld().getServer(), player));
        assertNotNull(use(source, 1));
        assertTrue(known(kind, 200));
        assertNull(player.getPendingItemUse());
        assertNull(
                LearningPersistenceChecks.receipt(
                        LearningPersistenceChecks.profile(playerStore.datastore(), player)));
        assertEquals(2 - 1, player.getInventory().getItemCountById(source.getItemId()));
        assertNull(use(source, 1));
        assertEquals(2 - 1, player.getInventory().getItemCountById(source.getItemId()));
    }

    @Test
    void resumingAnAlreadyLearnedCookRecipePreservesItsLaterProficiency() throws Exception {
        interruptLearning(Kind.COOK, "flag", true);
        player.getUnlockedRecipies().put(200, 17);
        DatabaseHelper.saveGameSync(player);
        coldLearningPlayer();
        new ItemUseRecovery(player).resolve(player.getPendingItemUse().getOperationId(), "resume");
        assertEquals(17, player.getUnlockedRecipies().get(200));
    }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments>
            actualAckLossCases() {
        return java.util.Arrays.stream(Kind.values())
                .flatMap(
                        kind ->
                                java.util.stream.Stream.of(false, true)
                                        .map(
                                                clearing ->
                                                        org.junit.jupiter.params.provider.Arguments
                                                                .of(kind, clearing)));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("actualAckLossCases")
    void ownUncertainPlayerWriteIsRecognizedBeforeRecipeRecovery(Kind kind, boolean clearing)
            throws Exception {
        var source = book(kind, "200");
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        DatabaseHelper.saveGameSync(player);
        var fired = new java.util.concurrent.atomic.AtomicBoolean();
        LearningPersistenceChecks.realAcknowledgementLoss(
                player, playerStore.datastore(), clearing, fired);
        assertNull(use(source, 1));
        assertTrue(fired.get());
        String operation = player.getPendingItemUse().getOperationId();
        new ItemUseRecovery(player).resolve(operation, "resume");
        assertNull(player.getPendingItemUse());
        assertNull(
                LearningPersistenceChecks.receipt(
                        LearningPersistenceChecks.profile(playerStore.datastore(), player)));
        assertTrue(known(kind, 200));
        assertEquals(2 - 1, player.getInventory().getItemCountById(source.getItemId()));
        LearningPersistenceChecks.assertOneUnlockNotification(player, false);
    }
}
