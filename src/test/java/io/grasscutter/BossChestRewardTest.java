package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.config.Configuration;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.game.activity.ActivityManager;
import emu.grasscutter.game.battlepass.*;
import emu.grasscutter.game.drop.*;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.gadget.GadgetChest;
import emu.grasscutter.game.entity.gadget.chest.BossChestInteractHandler;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.PlayerProperty;
import emu.grasscutter.game.systems.InventorySystem;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.BossChestInfoOuterClass.BossChestInfo;
import emu.grasscutter.net.proto.GadgetInteractReqOuterClass.GadgetInteractReq;
import emu.grasscutter.net.proto.InterOpTypeOuterClass.InterOpType;
import emu.grasscutter.net.proto.SceneGadgetInfoOuterClass.SceneGadgetInfo;
import emu.grasscutter.scripts.constants.ScriptGadgetState;
import emu.grasscutter.scripts.data.*;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.server.game.GameSession;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Script boss claims through the actual chest, resin manager, drop tree and inventory. */
class BossChestRewardTest extends QuestTestFixture {
    private final Map<Integer, ItemData> oldItems = new HashMap<>();
    private final Map<Integer, DropTableData> oldTables = new HashMap<>();
    private final AtomicInteger legacyGrants = new AtomicInteger();
    private GadgetChest chest;
    private SceneGadget meta;
    private boolean oldResinUsage;

    @BeforeEach
    void boss() throws Exception {
        oldResinUsage = Configuration.GAME_OPTIONS.resinOptions.resinUsage;
        Configuration.GAME_OPTIONS.resinOptions.resinUsage = true;
        replaceField(
                GameServer.class,
                player.server,
                "battlePassSystem",
                new BattlePassSystem(player.server));
        replaceField(
                GameServer.class,
                player.server,
                "inventorySystem",
                new InventorySystem(player.server));
        replaceField(
                GameServer.class,
                player.server,
                "worldDataSystem",
                new WorldDataSystem(player.server));
        var drops = new DropSystem(player.server);
        replaceField(GameServer.class, player.server, "dropSystem", drops);
        var tags = DropSystem.class.getDeclaredField("chestReward");
        tags.setAccessible(true);
        @SuppressWarnings("unchecked")
        var rows = (Map<String, List<BaseDropData>>) tags.get(drops);
        rows.put(
                "BossRewardTest",
                List.of(
                        gson.fromJson("{minLevel:1,dropId:880101}", BaseDropData.class),
                        gson.fromJson("{minLevel:100,dropId:880102}", BaseDropData.class)));
        for (int id : List.of(880101, 880102))
            oldTables.put(id, GameData.getDropTableDataMap().get(id));
        table(880101, "[{itemId:202,countRange:'5',weight:10000}]");
        table(880102, "[{itemId:202,countRange:'9',weight:10000}]");
        item(202, "ITEM_VIRTUAL", "MATERIAL_NONE", 0);
        item(104003, "ITEM_MATERIAL", "MATERIAL_EXP_FRUIT", 9999);
        savedGadget = GameData.getGadgetDataMap().get(70211001);
        GameData.getGadgetDataMap()
                .put(
                        70211001,
                        gson.fromJson(
                                "{id:70211001,jsonName:'BossRewardTestChest',type:'Chest'}",
                                GadgetData.class));
        player.server
                .getWorldDataSystem()
                .getChestInteractHandlerMap()
                .put(
                        "BossRewardTestChest",
                        new BossChestInteractHandler() {
                            @Override
                            public boolean onInteract(
                                    GadgetChest current, Player member, boolean condensed) {
                                legacyGrants.incrementAndGet();
                                return true;
                            }
                        });
        meta = new SceneGadget();
        meta.drop_tag = "BossRewardTest";
        meta.boss_chest = new SceneBossChest();
        meta.boss_chest.resin = 40;
        meta.boss_chest.monster_config_id = 99;
        var entity = new EntityGadget(player.getScene(), 70211001, new Position());
        entity.setMetaGadget(meta);
        chest = new GadgetChest(entity);
        player.setProperty(PlayerProperty.PROP_PLAYER_WORLD_LEVEL, 0);
        player.setProperty(PlayerProperty.PROP_PLAYER_RESIN, 80);
        replaceField(Player.class, player, "battlePassManager", new BattlePassManager(player));
        player.packets.clear();
    }

    private GadgetData savedGadget;

    @AfterEach
    void restoreBoss() throws Exception {
        assertTrue(emu.grasscutter.database.DatabaseHelper.awaitIdle(Duration.ofSeconds(5)));
        Configuration.GAME_OPTIONS.resinOptions.resinUsage = oldResinUsage;
        oldItems.forEach(
                (id, old) -> {
                    if (old == null) GameData.getItemDataMap().remove(id.intValue());
                    else GameData.getItemDataMap().put(id.intValue(), old);
                });
        oldTables.forEach(
                (id, old) -> {
                    if (old == null) GameData.getDropTableDataMap().remove(id.intValue());
                    else GameData.getDropTableDataMap().put(id.intValue(), old);
                });
        if (savedGadget == null) GameData.getGadgetDataMap().remove(70211001);
        else GameData.getGadgetDataMap().put(70211001, savedGadget);
    }

    private void item(int id, String type, String material, int limit) {
        var data =
                gson.fromJson(
                        "{id:"
                                + id
                                + ",itemType:'"
                                + type
                                + "',materialType:'"
                                + material
                                + "',stackLimit:"
                                + limit
                                + "}",
                        ItemData.class);
        oldItems.put(id, GameData.getItemDataMap().put(id, data));
    }

    private void table(int id, String entries) {
        GameData.getDropTableDataMap()
                .put(
                        id,
                        gson.fromJson(
                                "{id:" + id + ",randomType:1,dropVec:" + entries + "}",
                                DropTableData.class));
    }

    private boolean open() {
        return chest.onInteract(
                player,
                GadgetInteractReq.newBuilder()
                        .setOpType(InterOpType.InterOpType_INTER_OP_FINISH)
                        .build());
    }

    private void rejected() {
        assertFalse(assertDoesNotThrow(this::open));
        assertEquals(80, player.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertEquals(0, player.getMora());
        assertEquals(0, legacyGrants.get());
        assertNotEquals(ScriptGadgetState.ChestOpened, chest.getGadget().getState());
        assertTrue(
                player.packets.stream()
                        .noneMatch(
                                p -> p.getOpcode() == PacketOpcodes.GadgetAutoPickDropInfoNotify));
    }

    @Test
    void aMissingRootDoesNotSpendOrFallThrough() {
        GameData.getDropTableDataMap().remove(880101);
        rejected();
    }

    @Test
    void anUnknownTagDoesNotSpendOrFallThrough() {
        meta.drop_tag = "UnknownBossReward";
        rejected();
    }

    @Test
    void anEmptyRootDoesNotSpendOrAnnounceAPickup() {
        table(880101, "[]");
        rejected();
    }

    @Test
    void aZeroRollDoesNotSpendOrAnnounceAPickup() {
        table(880101, "[{itemId:202,countRange:'0',weight:10000}]");
        rejected();
    }

    @Test
    void anUnknownRewardDoesNotSpendOrFallThrough() {
        table(880101, "[{itemId:999999999,countRange:'1',weight:10000}]");
        rejected();
    }

    @Test
    void invalidRandomConfigurationDoesNotSpend() {
        table(880101, "[{itemId:202,countRange:'5;1',weight:10000}]");
        rejected();
    }

    @Test
    void aNegativePriceCannotMintResin() {
        meta.boss_chest.resin = -40;
        rejected();
    }

    @Test
    void insufficientResinCannotUseTheLegacyGrant() {
        player.setProperty(PlayerProperty.PROP_PLAYER_RESIN, 39);
        assertFalse(open());
        assertEquals(39, player.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertEquals(0, legacyGrants.get());
        assertEquals(0, player.getMora());
    }

    @Test
    void aMaterialOverflowRejectsTheWholeBatchBeforePayment() {
        table(
                880101,
                "[{itemId:202,countRange:'5',weight:10000},{itemId:104003,countRange:'10000',weight:10000}]");
        rejected();
        assertEquals(0, player.getInventory().getItemCountById(104003));
    }

    @Test
    void aFullMaterialTabRejectsBeforePayment() {
        player.getInventory()
                .createInventoryTab(ItemType.ITEM_MATERIAL, new MaterialInventoryTab(0));
        table(880101, "[{itemId:104003,countRange:'1',weight:10000}]");
        rejected();
    }

    @Test
    void aCurrencyOverflowRejectsBeforePayment() {
        player.setProperty(PlayerProperty.PROP_PLAYER_SCOIN, Integer.MAX_VALUE - 1);
        assertFalse(open());
        assertEquals(80, player.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertEquals(Integer.MAX_VALUE - 1, player.getMora());
        assertEquals(0, legacyGrants.get());
    }

    @Test
    void successfulClaimsPayTheConfiguredPriceAndUseTheOriginalRoll() {
        assertTrue(open());
        assertEquals(40, player.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertEquals(5, player.getMora());
        assertEquals(0, legacyGrants.get());
    }

    @Test
    void disablingResinCostsPreservesTheReward() {
        Configuration.GAME_OPTIONS.resinOptions.resinUsage = false;
        assertTrue(open());
        assertEquals(80, player.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertEquals(5, player.getMora());
    }

    @Test
    void aClaimCannotBeRepeatedForTheSamePlayer() {
        assertTrue(open());
        assertFalse(open());
        assertEquals(40, player.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertEquals(5, player.getMora());
    }

    @Test
    void aFailedClaimCanBeRetriedAfterRepair() {
        GameData.getDropTableDataMap().remove(880101);
        rejected();
        table(880101, "[{itemId:202,countRange:'5',weight:10000}]");
        assertTrue(open());
        assertEquals(40, player.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertEquals(5, player.getMora());
    }

    @Test
    void aClaimedPlayerIsOmittedFromTheRemainingList() {
        player.getScene().getPlayers().add(player);
        assertTrue(open());
        var info = SceneGadgetInfo.newBuilder();
        chest.onBuildProto(info);
        BossChestInfo boss = info.getBossChest();
        assertEquals(List.of(player.getUid()), boss.getQualifyUidListList());
        assertTrue(boss.getRemainUidListList().isEmpty());
    }

    @Test
    void theStartStepDoesNotPayOrGrant() {
        assertFalse(
                chest.onInteract(
                        player,
                        GadgetInteractReq.newBuilder()
                                .setOpType(InterOpType.InterOpType_INTER_OP_START)
                                .build()));
        assertEquals(80, player.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertEquals(0, player.getMora());
        assertTrue(open());
        assertEquals(5, player.getMora());
    }

    @Test
    void anotherQualifiedPlayerCanClaimTheSameFlowerOnce() throws Exception {
        var guest = guest();
        player.getScene().getPlayers().addAll(List.of(player, guest));
        // The content return value requests entity deletion, rather than reporting grant success.
        assertFalse(open());
        var info = SceneGadgetInfo.newBuilder();
        chest.onBuildProto(info);
        assertEquals(List.of(guest.getUid()), info.getBossChest().getRemainUidListList());
        var request =
                GadgetInteractReq.newBuilder()
                        .setOpType(InterOpType.InterOpType_INTER_OP_FINISH)
                        .build();
        assertTrue(chest.onInteract(guest, request));
        assertFalse(chest.onInteract(guest, request));
        assertFalse(open());
        assertEquals(5, guest.getMora());
        assertEquals(40, guest.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        assertEquals(5, player.getMora());
        assertEquals(40, player.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
    }

    private TestPlayer guest() throws Exception {
        var guest = new TestPlayer();
        guest.server = player.server;
        guest.setUid(10002);
        guest.setWorld(player.getWorld());
        guest.setScene(player.getScene());
        guest.activity = new ActivityManager(guest);
        var session =
                new GameSession(player.server) {
                    @Override
                    public void send(BasePacket packet) {
                        guest.packets.add(packet);
                    }
                };
        session.setPlayer(guest);
        guest.setSession(session);
        guest.setProperty(PlayerProperty.PROP_PLAYER_RESIN, 80);
        replaceField(Player.class, guest, "battlePassManager", new BattlePassManager(guest));
        return guest;
    }

    @Test
    void theEntityStaysUntilTheOtherQualifiedPlayerHasClaimed() throws Exception {
        var guest = guest();
        player.getScene().getPlayers().addAll(List.of(player, guest));
        var entity = chest.getGadget();
        replaceField(EntityGadget.class, entity, "content", chest);
        player.getScene().getEntities().put(entity.getId(), entity);
        var request =
                GadgetInteractReq.newBuilder()
                        .setOpType(InterOpType.InterOpType_INTER_OP_FINISH)
                        .build();
        entity.onInteract(player, request);
        assertSame(entity, player.getScene().getEntityById(entity.getId()));
        assertNotEquals(ScriptGadgetState.ChestOpened, entity.getState());
        assertEquals(5, player.getMora());
        assertEquals(40, player.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        entity.onInteract(player, request);
        assertEquals(5, player.getMora());
        assertEquals(40, player.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
        entity.onInteract(guest, request);
        assertNull(player.getScene().getEntityById(entity.getId()));
        assertEquals(5, guest.getMora());
        assertEquals(40, guest.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
    }

    @Test
    void differentFlowersCannotOverfillTheSameInventory() throws Exception {
        item(104002, "ITEM_MATERIAL", "MATERIAL_EXP_FRUIT", 9999);
        player.getInventory()
                .createInventoryTab(ItemType.ITEM_MATERIAL, new MaterialInventoryTab(1));
        table(880101, "[{itemId:104003,countRange:'1',weight:10000}]");
        table(880102, "[{itemId:104002,countRange:'1',weight:10000}]");
        var field = DropSystem.class.getDeclaredField("chestReward");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var rows = (Map<String, List<BaseDropData>>) field.get(player.server.getDropSystem());
        rows.put(
                "OtherBossRewardTest",
                List.of(gson.fromJson("{minLevel:1,dropId:880102}", BaseDropData.class)));
        var otherMeta = new SceneGadget();
        otherMeta.drop_tag = "OtherBossRewardTest";
        otherMeta.boss_chest = new SceneBossChest();
        otherMeta.boss_chest.resin = 40;
        var entity = new EntityGadget(player.getScene(), 70211001, new Position());
        entity.setMetaGadget(otherMeta);
        var other = new GadgetChest(entity);
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first =
                    pool.submit(
                            () -> {
                                assertTrue(start.await(5, TimeUnit.SECONDS));
                                return open();
                            });
            var second =
                    pool.submit(
                            () -> {
                                assertTrue(start.await(5, TimeUnit.SECONDS));
                                return other.onInteract(
                                        player,
                                        GadgetInteractReq.newBuilder()
                                                .setOpType(InterOpType.InterOpType_INTER_OP_FINISH)
                                                .build());
                            });
            start.countDown();
            assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertEquals(40, player.getProperty(PlayerProperty.PROP_PLAYER_RESIN));
            assertEquals(
                    1,
                    player.getInventory().getItemCountById(104003)
                            + player.getInventory().getItemCountById(104002));
            assertEquals(0, legacyGrants.get());
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9})
    void supportedWorldLevelsRetainTheirDropTier(int level) {
        player.setProperty(PlayerProperty.PROP_PLAYER_WORLD_LEVEL, level);
        assertTrue(player.server.getDropSystem().handleBossChestDrop("BossRewardTest", player));
        assertEquals(level < 9 ? 5 : 9, player.getMora());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, Integer.MIN_VALUE, 10, Integer.MAX_VALUE})
    void outOfRangeWorldLevelsUseTheNearestSupportedDropTier(int level) {
        player.setProperty(PlayerProperty.PROP_PLAYER_WORLD_LEVEL, level);
        assertTrue(
                assertDoesNotThrow(
                        () ->
                                player.server
                                        .getDropSystem()
                                        .handleBossChestDrop("BossRewardTest", player)));
        assertEquals(level < 0 ? 5 : 9, player.getMora());
    }
}
