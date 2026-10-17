package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;

import dev.morphia.Datastore;

import emu.grasscutter.data.*;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.database.*;
import emu.grasscutter.game.home.GameHome;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.props.*;
import emu.grasscutter.game.props.ItemUseAction.*;
import emu.grasscutter.game.systems.InventorySystem;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

class HomeLearningTest {
    private emu.grasscutter.game.inventory.ObservedItemTestWrites itemWrites;
    private emu.grasscutter.game.player.GuardedPlayerTestStore playerStore;

    enum Kind {
        FURNITURE(ItemUseOp.ITEM_USE_UNLOCK_FURNITURE_FORMULA, 360101),
        SUITE(ItemUseOp.ITEM_USE_UNLOCK_FURNITURE_SUITE, 1),
        BGM(ItemUseOp.ITEM_USE_UNLOCK_HOME_BGM, 101);
        final ItemUseOp op;
        final int target;

        Kind(ItemUseOp op, int target) {
            this.op = op;
            this.target = target;
        }
    }

    static class HomePlayer extends InvestigationRewardTest.ClaimPlayer {
        transient boolean failLearningNotification;

        @Override
        public void sendPacket(emu.grasscutter.net.packet.BasePacket packet) {
            if (failLearningNotification && LearningPersistenceChecks.unlockNotification(packet))
                throw new IllegalStateException("Learning notification unavailable");
            super.sendPacket(packet);
        }

        transient boolean refuseRemoval, pretendRemoval;
        transient GameHome home;
        final transient Inventory inventory =
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
            return inventory;
        }

        @Override
        public GameHome getHome() {
            return home;
        }
    }

    private AbilityTestFixture environment;
    private HomePlayer player;
    private InventorySystem inventory;
    private Field datastore;
    private Object previousDatastore;
    private final Gson gson = new Gson();
    private final Map<Map<?, ?>, Map<?, ?>> oldMaps = new IdentityHashMap<>();

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void clear(Map map) {
        oldMaps.put(map, new HashMap<>(map));
        map.clear();
    }

    /** Keep the old-JAR comparison executable before the new suite resource map exists. */
    @SuppressWarnings("unchecked")
    private Map<Integer, GameResource> suiteMap() throws Exception {
        try {
            return (Map<Integer, GameResource>)
                    GameData.class.getMethod("getFurnitureSuiteDataMap").invoke(null);
        } catch (NoSuchMethodException absentInBaseline) {
            return null;
        }
    }

    @BeforeEach
    void setup() throws Exception {
        environment = new AbilityTestFixture();
        player = new HomePlayer();
        player.home =
                GameHome.of()
                        .ownerUid(player.getUid())
                        .player(player)
                        .unlockedHomeBgmList(new HashSet<>())
                        .build();
        var server = environment.scene.getWorld().getServer();
        player.setSession(new InvestigationRewardTest.ClaimSession(server, player));
        inventory = new InventorySystem(server);
        clear(GameData.getItemDataMap());
        clear(GameData.getFurnitureMakeConfigDataMap());
        clear(GameData.getHomeWorldBgmDataMap());
        var suites = suiteMap();
        if (suites != null) clear(suites);
        player.getUnlockedFurniture().clear();
        player.getUnlockedFurnitureSuite().clear();
        addDefinition(Kind.FURNITURE, 360101);
        addDefinition(Kind.SUITE, 1);
        addDefinition(Kind.BGM, 101);
        datastore = DatabaseManager.class.getDeclaredField("gameDatastore");
        datastore.setAccessible(true);
        previousDatastore = datastore.get(null);
        playerStore = new emu.grasscutter.game.player.GuardedPlayerTestStore(player, () -> {});
        datastore.set(
                null,
                Proxy.newProxyInstance(
                        Datastore.class.getClassLoader(),
                        new Class<?>[] {Datastore.class},
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
        if (datastore != null) datastore.set(null, previousDatastore);
        oldMaps.forEach(
                (map, old) -> {
                    ((Map) map).clear();
                    ((Map) map).putAll(old);
                });
        environment.close();
    }

    private void addDefinition(Kind kind, int target) throws Exception {
        switch (kind) {
            case FURNITURE -> {
                var item =
                        gson.fromJson(
                                "{id:" + target + ",itemType:'ITEM_FURNITURE',stackLimit:99}",
                                ItemData.class);
                item.onLoad();
                GameData.getItemDataMap().put(target, item);
                GameData.getFurnitureMakeConfigDataMap()
                        .put(
                                target,
                                gson.fromJson(
                                        "{configID:"
                                                + target
                                                + ",furnitureItemID:"
                                                + target
                                                + ",count:1,materialItems:[]}",
                                        FurnitureMakeConfigData.class));
            }
            case BGM -> GameData.getHomeWorldBgmDataMap()
                    .put(target, gson.fromJson("{bgmID:" + target + "}", HomeWorldBgmData.class));
            case SUITE -> {
                var map = suiteMap();
                if (map != null) {
                    var type = Class.forName("emu.grasscutter.data.excels.FurnitureSuiteData");
                    map.put(
                            target,
                            (GameResource)
                                    gson.fromJson(
                                            "{suiteID:" + target + ",jsonName:'Home_Suite_Test'}",
                                            type));
                }
            }
        }
    }

    private Set<Integer> known(Kind kind) {
        return switch (kind) {
            case FURNITURE -> player.getUnlockedFurniture();
            case SUITE -> player.getUnlockedFurnitureSuite();
            case BGM -> player.getHome().getUnlockedHomeBgmList();
        };
    }

    private GameItem book(Kind kind, String parameter, int amount) {
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
        assertTrue(player.getInventory().addItem(data.getId(), amount));
        player.packets.clear();
        return player.getInventory().getItemById(data.getId());
    }

    private GameItem use(GameItem book, int count) {
        return inventory.useItem(player, 0, book.getGuid(), count, 0, false);
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void realDefinitionUnlocksAfterOneConfirmedConsumption(Kind kind) {
        var source = book(kind, String.valueOf(kind.target), 2);
        assertSame(source, use(source, 1));
        assertEquals(1, source.getCount());
        assertTrue(known(kind).contains(kind.target));
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void learningPersistsItsFlagAndExactBookDebit(Kind kind) throws Exception {
        var source = book(kind, String.valueOf(kind.target), 1);
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
        var document =
                kind == Kind.BGM
                        ? playerStore
                                .datastore()
                                .getMapper()
                                .getCollection(GameHome.class)
                                .withDocumentClass(org.bson.Document.class)
                                .find(new org.bson.Document("_id", player.home.getId()))
                                .first()
                        : profile;
        assertNotNull(document);
        String field =
                switch (kind) {
                    case FURNITURE -> "unlockedFurniture";
                    case SUITE -> "unlockedFurnitureSuite";
                    case BGM -> "unlockedHomeBgmList";
                };
        assertTrue(
                document.getList(field, Integer.class, List.of()).contains(kind.target),
                "The learned blueprint must survive a cold read");
        var item =
                playerStore
                        .datastore()
                        .getMapper()
                        .getCollection(GameItem.class)
                        .withDocumentClass(org.bson.Document.class)
                        .find(new org.bson.Document("_id", source.getObjectId()))
                        .first();
        assertNotNull(item);
        assertTrue(item.getBoolean("deleted", false));
        assertEquals(0, item.getInteger("count"));
        assertNull(profile.get("pendingItemUse"));
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void alreadyLearnedBookIsPreserved(Kind kind) {
        known(kind).add(kind.target);
        var source = book(kind, String.valueOf(kind.target), 2);
        assertNull(use(source, 1));
        assertEquals(2, source.getCount());
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void unknownDefinitionDoesNotConsumeOrUnlock(Kind kind) {
        var source = book(kind, "999999", 2);
        assertNull(use(source, 1));
        assertEquals(2, source.getCount());
        assertFalse(known(kind).contains(999999));
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void malformedParameterDoesNotConsumeOrUnlock(Kind kind) {
        var source = book(kind, "invalid", 2);
        assertNull(use(source, 1));
        assertEquals(2, source.getCount());
        assertFalse(known(kind).contains(-1));
        assertFalse(known(kind).contains(0));
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void refusedDeletionDoesNotUnlock(Kind kind) {
        var source = book(kind, String.valueOf(kind.target), 2);
        player.refuseRemoval = true;
        assertNull(use(source, 1));
        assertEquals(2, source.getCount());
        assertFalse(known(kind).contains(kind.target));
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void pretendDeletionDoesNotUnlock(Kind kind) {
        var source = book(kind, String.valueOf(kind.target), 2);
        player.pretendRemoval = true;
        assertNull(use(source, 1));
        assertEquals(2, source.getCount());
        assertFalse(known(kind).contains(kind.target));
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void directPreflightRejectsBatchLearning(Kind kind) {
        var source = book(kind, String.valueOf(kind.target), 2);
        var params = new UseItemParams(player, ItemUseTarget.ITEM_USE_TARGET_NONE);
        params.count = 2;
        assertFalse(source.getItemData().getItemUseActions().get(0).useItem(params));
        assertEquals(2, source.getCount());
        assertFalse(known(kind).contains(kind.target));
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void nullParamsAreRejectedWithoutThrowing(Kind kind) {
        var source = book(kind, String.valueOf(kind.target), 2);
        assertFalse(
                assertDoesNotThrow(
                        () -> source.getItemData().getItemUseActions().get(0).useItem(null)));
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void usingLastBookDeletesItAndThenUnlocks(Kind kind) {
        var source = book(kind, String.valueOf(kind.target), 1);
        assertSame(source, use(source, 1));
        assertNull(player.getInventory().getItemByGuid(source.getGuid()));
        assertTrue(known(kind).contains(kind.target));
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void secondUseKeepsTheRemainingBook(Kind kind) {
        var source = book(kind, String.valueOf(kind.target), 2);
        assertSame(source, use(source, 1));
        assertNull(use(source, 1));
        assertEquals(1, source.getCount());
    }

    @Test
    void directFurnitureManagerRejectsUnknownIds() {
        assertFalse(player.getFurnitureManager().unlockFurnitureFormula(999999));
        assertFalse(player.getUnlockedFurniture().contains(999999));
    }

    @Test
    void directSuiteManagerRejectsUnknownIds() {
        assertFalse(player.getFurnitureManager().unlockFurnitureSuite(999999));
        assertFalse(player.getUnlockedFurnitureSuite().contains(999999));
    }

    @Test
    void directHomeBgmRejectsUnknownIds() {
        assertFalse(player.getHome().addUnlockedHomeBgm(999999));
        assertFalse(player.getHome().getUnlockedHomeBgmList().contains(999999));
    }

    @Test
    void defaultBgmDoesNotConsumeAScore() {
        GameData.getHomeWorldBgmDataMap()
                .put(
                        101,
                        gson.fromJson("{bgmID:101,isDefaultUnlock:true}", HomeWorldBgmData.class));
        var source = book(Kind.BGM, "101", 2);
        assertNull(use(source, 1));
        assertEquals(2, source.getCount());
    }

    @Test
    void missingHomeDoesNotConsumeAScore() {
        var source = book(Kind.BGM, "101", 2);
        player.home = null;
        assertNull(assertDoesNotThrow(() -> use(source, 1)));
        assertEquals(2, source.getCount());
    }

    @Test
    void wrongFurnitureDefinitionDoesNotUnlock() {
        GameData.getItemDataMap()
                .put(360101, gson.fromJson("{id:360101,itemType:'ITEM_MATERIAL'}", ItemData.class));
        var source = book(Kind.FURNITURE, "360101", 2);
        assertNull(use(source, 1));
        assertEquals(2, source.getCount());
    }

    @Test
    void wrongBgmMapKeyDoesNotUnlock() {
        GameData.getHomeWorldBgmDataMap()
                .put(101, gson.fromJson("{bgmID:102}", HomeWorldBgmData.class));
        var source = book(Kind.BGM, "101", 2);
        assertNull(use(source, 1));
        assertEquals(2, source.getCount());
    }

    @Test
    void furnitureWithoutMakingDefinitionDoesNotUnlock() {
        GameData.getFurnitureMakeConfigDataMap().clear();
        var source = book(Kind.FURNITURE, "360101", 2);
        assertNull(use(source, 1));
        assertEquals(2, source.getCount());
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void preflightHasNoUnlockEffect(Kind kind) {
        var source = book(kind, String.valueOf(kind.target), 2);
        var params = new UseItemParams(player, ItemUseTarget.ITEM_USE_TARGET_NONE);
        assertTrue(source.getItemData().getItemUseActions().get(0).useItem(params));
        assertFalse(known(kind).contains(kind.target));
        assertEquals(2, source.getCount());
    }

    @Test
    void wrongSuiteMapKeyDoesNotUnlock() throws Exception {
        var map = suiteMap();
        if (map != null)
            map.put(
                    1,
                    (GameResource)
                            gson.fromJson(
                                    "{suiteID:2,jsonName:'Home_Suite_Test'}",
                                    Class.forName(
                                            "emu.grasscutter.data.excels.FurnitureSuiteData")));
        var source = book(Kind.SUITE, "1", 2);
        assertNull(use(source, 1));
        assertEquals(2, source.getCount());
    }

    @Test
    void suiteWithoutItsLayoutDoesNotUnlock() throws Exception {
        var map = suiteMap();
        if (map != null)
            map.put(
                    1,
                    (GameResource)
                            gson.fromJson(
                                    "{suiteID:1,jsonName:''}",
                                    Class.forName(
                                            "emu.grasscutter.data.excels.FurnitureSuiteData")));
        var source = book(Kind.SUITE, "1", 2);
        assertNull(use(source, 1));
        assertEquals(2, source.getCount());
    }

    @Test
    void mismatchedMakingMapKeyDoesNotUnlock() {
        GameData.getFurnitureMakeConfigDataMap()
                .put(
                        360101,
                        gson.fromJson(
                                "{configID:360102,furnitureItemID:360101,count:1}",
                                FurnitureMakeConfigData.class));
        var source = book(Kind.FURNITURE, "360101", 2);
        assertNull(use(source, 1));
        assertEquals(2, source.getCount());
    }

    @Test
    void suiteResourceHasItsOwnCanonicalMap() throws Exception {
        assertNotNull(suiteMap());
        assertEquals(1, suiteMap().get(1).getId());
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void everyDeclaredResourceBookUnlocksItsActualTarget(Kind kind) throws Exception {
        var directory =
                Path.of(
                        System.getenv().getOrDefault("LUNAGC_RESOURCES", "/resources"),
                        "ExcelBinOutput");
        var materials =
                JsonParser.parseString(
                                Files.readString(directory.resolve("MaterialExcelConfigData.json")))
                        .getAsJsonArray();
        if (kind == Kind.FURNITURE) {
            GameData.getFurnitureMakeConfigDataMap().clear();
            for (var row :
                    JsonParser.parseString(
                                    Files.readString(
                                            directory.resolve("FurnitureMakeExcelConfigData.json")))
                            .getAsJsonArray()) {
                var data = gson.fromJson(row, FurnitureMakeConfigData.class);
                GameData.getFurnitureMakeConfigDataMap().put(data.getId(), data);
            }
            for (var row :
                    JsonParser.parseString(
                                    Files.readString(
                                            directory.resolve(
                                                    "HomeWorldFurnitureExcelConfigData.json")))
                            .getAsJsonArray()) {
                var data = JsonUtils.decode(row.getAsJsonObject(), ItemData.class);
                data.onLoad();
                GameData.getItemDataMap().put(data.getId(), data);
            }
        } else if (kind == Kind.BGM) {
            GameData.getHomeWorldBgmDataMap().clear();
            for (var row :
                    JsonParser.parseString(
                                    Files.readString(
                                            directory.resolve("HomeWorldBgmExcelConfigData.json")))
                            .getAsJsonArray()) {
                var data = gson.fromJson(row, HomeWorldBgmData.class);
                GameData.getHomeWorldBgmDataMap().put(data.getId(), data);
            }
        } else if (suiteMap() != null) {
            suiteMap().clear();
            var type = Class.forName("emu.grasscutter.data.excels.FurnitureSuiteData");
            for (var row :
                    JsonParser.parseString(
                                    Files.readString(
                                            directory.resolve(
                                                    "FurnitureSuiteExcelConfigData.json")))
                            .getAsJsonArray()) {
                var data = (GameResource) gson.fromJson(row, type);
                suiteMap().put(data.getId(), data);
            }
        }
        int checked = 0;
        for (var row : materials) {
            var data = JsonUtils.decode(row.getAsJsonObject(), ItemData.class);
            data.onLoad();
            if (data.getItemUseActions().size() != 1
                    || data.getItemUseActions().get(0).getItemUseOp() != kind.op) continue;
            int target =
                    Integer.parseInt(
                            row.getAsJsonObject()
                                    .getAsJsonArray("itemUse")
                                    .get(0)
                                    .getAsJsonObject()
                                    .getAsJsonArray("useParam")
                                    .get(0)
                                    .getAsString());
            known(kind).clear();
            GameData.getItemDataMap().put(data.getId(), data);
            assertTrue(player.getInventory().addItem(data.getId(), 1));
            var source = player.getInventory().getItemById(data.getId());
            assertSame(source, use(source, 1), "resource book " + data.getId());
            assertTrue(known(kind).contains(target), "target " + target);
            assertNull(player.getInventory().getItemByGuid(source.getGuid()));
            player.packets.clear();
            checked++;
        }
        assertEquals(
                switch (kind) {
                    case FURNITURE -> 1366;
                    case SUITE -> 109;
                    case BGM -> 206;
                },
                checked);
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
        var source = book(kind, String.valueOf(kind.target), 1);
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
        var fresh = new HomePlayer();
        fresh.setSession(
                new InvestigationRewardTest.ClaimSession(
                        environment.scene.getWorld().getServer(), fresh));
        fresh.home = LearningPersistenceChecks.restore(fresh, playerStore.datastore());
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
        assertTrue(known(kind).contains(kind.target));
        assertEquals(1 - 1, player.getInventory().getItemCountById(source.getItemId()));
        assertEquals(
                1 - 1,
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
        assertTrue(known(kind).contains(kind.target));
        assertEquals(1 - 1 + 5, player.getInventory().getItemCountById(source.getItemId()));
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
        assertFalse(known(kind).contains(kind.target));
        assertEquals(1, player.getInventory().getItemCountById(source.getItemId()));
        assertNull(
                LearningPersistenceChecks.receipt(
                        LearningPersistenceChecks.profile(playerStore.datastore(), player)));
        assertNotNull(use(player.getInventory().getItemById(source.getItemId()), 1));
        assertTrue(known(kind).contains(kind.target));
        assertEquals(1 - 1, player.getInventory().getItemCountById(source.getItemId()));
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
        assertFalse(known(kind).contains(kind.target));
        assertEquals(1 - 1, player.getInventory().getItemCountById(source.getItemId()));
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void nativeManagementCommandChecksPermissionsThenResumesTheSameLearning(Kind kind)
            throws Exception {
        var source = interruptLearning(kind, "paid", true);
        coldLearningPlayer();
        LearningPersistenceChecks.commandResume(player, playerStore.datastore());
        assertTrue(known(kind).contains(kind.target));
        assertEquals(1 - 1, player.getInventory().getItemCountById(source.getItemId()));
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
        assertFalse(known(kind).contains(kind.target));
        new ItemUseRecovery(player).resolve(operation, "cancel");
        assertNull(player.getPendingItemUse());
        assertEquals(1, player.getInventory().getItemCountById(source.getItemId()));
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void aLostUnlockNotificationCannotTurnPersistedLearningIntoAnotherCost(Kind kind)
            throws Exception {
        var source = book(kind, String.valueOf(kind.target), 1);
        assertTrue(DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        DatabaseHelper.saveGameSync(player);
        player.failLearningNotification = true;
        player.setSession(
                new LearningPersistenceChecks.MissingUnlockNotification(
                        environment.scene.getWorld().getServer(), player));
        assertNotNull(use(source, 1));
        assertTrue(known(kind).contains(kind.target));
        assertNull(player.getPendingItemUse());
        assertNull(
                LearningPersistenceChecks.receipt(
                        LearningPersistenceChecks.profile(playerStore.datastore(), player)));
        assertEquals(1 - 1, player.getInventory().getItemCountById(source.getItemId()));
        assertNull(use(source, 1));
        assertEquals(1 - 1, player.getInventory().getItemCountById(source.getItemId()));
    }

    @Test
    void aDifferentHomeCannotReceiveThePendingBgmUnlock() throws Exception {
        interruptLearning(Kind.BGM, "paid", true);
        coldLearningPlayer();
        String before = LearningPersistenceChecks.profile(playerStore.datastore(), player).toJson();
        player.home.setOwnerUid(10002);
        assertThrows(
                IllegalStateException.class,
                () ->
                        new ItemUseRecovery(player)
                                .resolve(player.getPendingItemUse().getOperationId(), "resume"));
        assertEquals(
                before,
                LearningPersistenceChecks.profile(playerStore.datastore(), player).toJson());
        assertFalse(known(Kind.BGM).contains(Kind.BGM.target));
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
        var source = book(kind, String.valueOf(kind.target), 1);
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
        assertTrue(known(kind).contains(kind.target));
        assertEquals(1 - 1, player.getInventory().getItemCountById(source.getItemId()));
        LearningPersistenceChecks.assertOneUnlockNotification(player, kind == Kind.BGM);
    }
}
