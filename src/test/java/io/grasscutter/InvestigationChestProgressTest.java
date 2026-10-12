package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.game.drop.*;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.gadget.GadgetChest;
import emu.grasscutter.game.entity.gadget.chest.*;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.PlayerProperty;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.GadgetInteractReqOuterClass.GadgetInteractReq;
import emu.grasscutter.net.proto.InterOpTypeOuterClass.InterOpType;
import emu.grasscutter.net.proto.PlayerInvestigationTargetNotifyOuterClass.PlayerInvestigationTargetNotify;
import emu.grasscutter.plugin.PluginManager;
import emu.grasscutter.scripts.constants.ScriptGadgetState;
import emu.grasscutter.scripts.data.SceneGadget;
import emu.grasscutter.server.event.EventHandler;
import emu.grasscutter.server.event.HandlerPriority;
import emu.grasscutter.server.event.player.PlayerOpenChestEvent;
import emu.grasscutter.server.game.GameServer;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Actual chest interaction paths with controlled drop rows, an empty plugin directory and no
 * listener.
 */
class InvestigationChestProgressTest {
    @TempDir Path plugins;
    private AbilityTestFixture environment;
    private ChestPlayer player, guest;
    private AbilityTestFixture.TestScene scene;
    private GameServer server;
    private EntityGadget entity;
    private GadgetChest chest;
    private final Gson gson = new Gson();
    private Map<Integer, InvestigationData> oldChapters;
    private Map<Integer, InvestigationTargetData> oldTargets;
    private Map<Integer, GadgetData> oldGadgets;
    private Map<Integer, DropTableData> oldDrops;
    private Map<Integer, ItemData> oldItems;
    private PluginManager previousPlugins, pluginManager;
    private String previousPluginPath;
    private Map<String, ChestInteractHandler> handlers;
    private final AtomicInteger grants = new AtomicInteger();

    static class ChestPlayer extends InvestigationRewardTest.ClaimPlayer {
        GameServer server;
        final AtomicInteger saves = new AtomicInteger();

        @Override
        public GameServer getServer() {
            return server;
        }

        @Override
        public void save() {
            saves.incrementAndGet();
        }
    }

    private static void set(Object instance, Class<?> owner, String name, Object value)
            throws Exception {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }

    @BeforeEach
    void prepare() throws Exception {
        environment = new AbilityTestFixture();
        server = environment.scene.getWorld().getServer();
        player = new ChestPlayer();
        guest = new ChestPlayer();
        player.server = server;
        guest.server = server;
        for (var member : List.of(player, guest)) {
            member.setProperty(PlayerProperty.PROP_PLAYER_LEVEL, 45);
            member.getOpenStates().put(1100, 1);
            member.getOpenStates().put(1101, 1);
        }
        var world = new AbilityTestFixture.TestWorld(server, player);
        scene =
                new AbilityTestFixture.TestScene(
                        world, gson.fromJson("{id:3,type:'SCENE_WORLD'}", SceneData.class));
        set(
                scene,
                Scene.class,
                "scriptManager",
                new emu.grasscutter.scripts.SceneScriptManager(scene, false) {
                    @Override
                    public Future<?> callEvent(emu.grasscutter.scripts.data.ScriptArgs args) {
                        return CompletableFuture.completedFuture(null);
                    }
                });
        player.setWorld(world);
        guest.setWorld(world);
        player.setScene(scene);
        guest.setScene(scene);
        world.getPlayers().addAll(List.of(player, guest));
        scene.getPlayers().addAll(List.of(player, guest));
        oldChapters = new HashMap<>(GameData.getInvestigationDataMap());
        oldTargets = new HashMap<>(GameData.getInvestigationTargetDataMap());
        oldGadgets = new HashMap<>(GameData.getGadgetDataMap());
        oldDrops = new HashMap<>(GameData.getDropTableDataMap());
        oldItems = new HashMap<>(GameData.getItemDataMap());
        GameData.getInvestigationDataMap().clear();
        GameData.getInvestigationTargetDataMap().clear();
        GameData.getGadgetDataMap().clear();
        GameData.getDropTableDataMap().clear();
        GameData.getItemDataMap().clear();
        GameData.getInvestigationDataMap()
                .put(
                        10001,
                        gson.fromJson(
                                "{id:10001,investigationType:'INVESTIGATION_ADVENTURE',unlockLevel:1,unlockOpenStateType:'OPEN_ADVENTURE_MANUAL_CITY_MENGDE'}",
                                InvestigationData.class));
        target("70211001", 10);
        GameData.getGadgetDataMap()
                .put(
                        70211001,
                        gson.fromJson(
                                "{id:70211001,jsonName:'HandbookTestChest',type:'Chest'}",
                                GadgetData.class));
        for (int id : List.of(201, 202))
            GameData.getItemDataMap()
                    .put(
                            id,
                            gson.fromJson(
                                    "{id:" + id + ",itemType:'ITEM_VIRTUAL'}", ItemData.class));
        set(server, GameServer.class, "worldDataSystem", new WorldDataSystem(server));
        set(server, GameServer.class, "dropSystem", new DropSystem(server));
        handlers = server.getWorldDataSystem().getChestInteractHandlerMap();
        handler(true);
        entity = new EntityGadget(scene, 70211001, new Position());
        chest = new GadgetChest(entity);
        previousPlugins = Grasscutter.getPluginManager();
        previousPluginPath = Grasscutter.getConfig().folderStructure.plugins;
        Grasscutter.getConfig().folderStructure.plugins = plugins.toString();
        pluginManager = new PluginManager();
        set(null, Grasscutter.class, "pluginManager", pluginManager);
    }

    @AfterEach
    void close() throws Exception {
        set(null, Grasscutter.class, "pluginManager", previousPlugins);
        Grasscutter.getConfig().folderStructure.plugins = previousPluginPath;
        GameData.getInvestigationDataMap().clear();
        GameData.getInvestigationDataMap().putAll(oldChapters);
        GameData.getInvestigationTargetDataMap().clear();
        GameData.getInvestigationTargetDataMap().putAll(oldTargets);
        GameData.getGadgetDataMap().clear();
        GameData.getGadgetDataMap().putAll(oldGadgets);
        GameData.getDropTableDataMap().clear();
        GameData.getDropTableDataMap().putAll(oldDrops);
        GameData.getItemDataMap().clear();
        GameData.getItemDataMap().putAll(oldItems);
        environment.close();
    }

    private void target(String ids, int total) {
        GameData.getInvestigationTargetDataMap()
                .put(
                        60001,
                        gson.fromJson(
                                "{id:60001,investigationId:10001,progress:"
                                        + total
                                        + ",triggerConfig:{triggerType:'TRIGGER_OPEN_CHEST_WITH_GADGET_ID',paramList:['"
                                        + ids
                                        + "','','']}}",
                                InvestigationTargetData.class));
    }

    private void handler(boolean success) {
        handlers.put(
                "HandbookTestChest",
                new ChestInteractHandler() {
                    @Override
                    public boolean isTwoStep() {
                        return false;
                    }

                    @Override
                    public boolean onInteract(GadgetChest current, Player member) {
                        grants.incrementAndGet();
                        return success;
                    }
                });
    }

    private void scriptDrop(boolean success) {
        Grasscutter.getConfig().server.game.enableScriptInBigWorld = true;
        var meta = new SceneGadget();
        meta.chest_drop_id = 880001;
        meta.drop_count = 1;
        meta.config_id = 11;
        entity.setMetaGadget(meta);
        if (success)
            GameData.getDropTableDataMap()
                    .put(
                            880001,
                            gson.fromJson(
                                    "{id:880001,randomType:0,dropVec:[{itemId:202,countRange:'5',weight:10000}]}",
                                    DropTableData.class));
    }

    private boolean open(Player member) {
        return chest.onInteract(
                member,
                GadgetInteractReq.newBuilder()
                        .setOpType(InterOpType.InterOpType_INTER_OP_FINISH)
                        .build());
    }

    private int progress(Player member) {
        return member.getPlayerProgress().getInvestigation().get(60001);
    }

    @Test
    void legacySuccessfulOpenUpdatesOnlyTheHostAndPushesTheCanonicalTarget() throws Exception {
        assertTrue(open(player));
        assertEquals(1, progress(player));
        assertEquals(0, progress(guest));
        assertTrue(player.saves.get() > 0);
        var packet =
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.PlayerInvestigationTargetNotify)
                        .findFirst()
                        .orElseThrow();
        var update = PlayerInvestigationTargetNotify.parseFrom(packet.getData());
        assertEquals(60001, update.getInvestigationTargetList(0).getQuestId());
        assertEquals(1, update.getInvestigationTargetList(0).getProgress());
    }

    @Test
    void nativeDropIdOpenUpdatesProgressAfterRealMoraIsAdded() {
        scriptDrop(true);
        assertTrue(open(player));
        assertEquals(5, player.getMora());
        assertEquals(1, progress(player));
        assertEquals(0, grants.get());
    }

    @Test
    void nativeDropTagOpenAlsoUpdatesProgress() throws Exception {
        scriptDrop(true);
        entity.getMetaGadget().drop_tag = "HandbookTestDrop";
        entity.getMetaGadget().chest_drop_id = 0;
        entity.getMetaGadget().level = 2;
        var field = DropSystem.class.getDeclaredField("chestReward");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var rewards = (Map<String, List<BaseDropData>>) field.get(server.getDropSystem());
        rewards.put(
                "HandbookTestDrop",
                List.of(
                        gson.fromJson(
                                "{index:'HandbookTestDrop',minLevel:1,dropId:880001}",
                                BaseDropData.class)));
        assertTrue(open(player));
        assertEquals(5, player.getMora());
        assertEquals(1, progress(player));
    }

    @Test
    void failedNativeDropAndSuccessfulFallbackCountOnce() {
        scriptDrop(false);
        assertTrue(open(player));
        assertEquals(1, grants.get());
        assertEquals(1, progress(player));
    }

    @Test
    void failedNativeDropAndFailedFallbackDoNotCount() {
        scriptDrop(false);
        handler(false);
        assertFalse(open(player));
        assertEquals(0, progress(player));
        assertNotEquals(ScriptGadgetState.ChestOpened, entity.getState());
    }

    @Test
    void failedLegacyOpenDoesNotCountAndCanBeRetried() {
        handler(false);
        assertFalse(open(player));
        assertEquals(0, progress(player));
        handler(true);
        assertTrue(open(player));
        assertEquals(1, progress(player));
    }

    @Test
    void guestCannotOpenTheHostsLegacyChest() {
        assertFalse(open(guest));
        assertEquals(0, grants.get());
        assertEquals(0, progress(guest));
        assertTrue(open(player));
        assertEquals(1, progress(player));
    }

    @Test
    void guestCannotOpenTheHostsScriptChest() {
        scriptDrop(true);
        assertFalse(open(guest));
        assertEquals(0, player.getMora());
        assertEquals(0, progress(guest));
        assertTrue(open(player));
        assertEquals(1, progress(player));
    }

    @Test
    void repeatingALegacyInteractionDoesNotGrantOrCountTwice() {
        assertTrue(open(player));
        assertFalse(open(player));
        assertEquals(1, grants.get());
        assertEquals(1, progress(player));
    }

    @Test
    void repeatingAScriptInteractionDoesNotGrantOrCountTwice() {
        scriptDrop(true);
        assertTrue(open(player));
        assertFalse(open(player));
        assertEquals(5, player.getMora());
        assertEquals(1, progress(player));
    }

    @Test
    void parallelOpenRequestsDeliverAndCountOnce() throws Exception {
        var pool = Executors.newFixedThreadPool(4);
        try {
            var results = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < 8; i++) results.add(pool.submit(() -> open(player)));
            int successes = 0;
            for (var result : results) if (result.get(5, TimeUnit.SECONDS)) successes++;
            assertEquals(1, successes);
            assertEquals(1, grants.get());
            assertEquals(1, progress(player));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void anEventCallbackCannotReenterTheSameChest() {
        var calls = new AtomicInteger();
        handlers.put(
                "HandbookTestChest",
                new ChestInteractHandler() {
                    @Override
                    public boolean isTwoStep() {
                        return false;
                    }

                    @Override
                    public boolean onInteract(GadgetChest current, Player member) {
                        if (calls.incrementAndGet() == 1) assertFalse(open(member));
                        return true;
                    }
                });
        assertTrue(open(player));
        assertEquals(1, calls.get());
        assertEquals(1, progress(player));
    }

    @Test
    void aPluginCancelledLegacyOpenDoesNotConsumeTheChest() {
        var reward = new ChestReward();
        reward.setContent(List.of());
        reward.setRandomContent(List.of());
        reward.setMora(10);
        handlers.put("HandbookTestChest", new NormalChestInteractHandler(reward));
        var canceled = new java.util.concurrent.atomic.AtomicBoolean(true);
        pluginManager.registerListener(
                new EventHandler<>(PlayerOpenChestEvent.class)
                        .priority(HandlerPriority.NORMAL)
                        .listener(
                                event -> {
                                    if (canceled.get()) event.cancel();
                                }));
        assertFalse(open(player));
        assertEquals(0, player.getMora());
        assertEquals(0, progress(player));
        assertNotEquals(ScriptGadgetState.ChestOpened, entity.getState());
        canceled.set(false);
        assertTrue(open(player));
        assertEquals(5, player.getMora());
        assertEquals(1, progress(player));
    }

    @Test
    void legacyBossChestClaimsStayPerPlayerAndDoNotCountAsTreasure() {
        handlers.put(
                "HandbookTestChest",
                new BossChestInteractHandler() {
                    @Override
                    public boolean onInteract(
                            GadgetChest current, Player member, boolean condensed) {
                        grants.incrementAndGet();
                        return true;
                    }
                });
        assertTrue(open(player));
        assertTrue(open(guest));
        assertEquals(2, grants.get());
        assertEquals(0, progress(player));
        assertEquals(0, progress(guest));
    }

    @Test
    void restoredOpenedLegacyChestDoesNotGrantOrAdvance() {
        entity.setState(ScriptGadgetState.ChestOpened);
        assertFalse(open(player));
        assertEquals(0, grants.get());
        assertEquals(0, progress(player));
    }

    @Test
    void restoredOpenedScriptChestDoesNotGrantOrAdvance() {
        scriptDrop(true);
        entity.setState(ScriptGadgetState.ChestOpened);
        assertFalse(open(player));
        assertEquals(0, player.getMora());
        assertEquals(0, progress(player));
    }

    @Test
    void anotherChestIdDoesNotSatisfyThisTarget() {
        target("70211002;70211003", 10);
        assertTrue(open(player));
        assertEquals(0, progress(player));
    }

    @Test
    void progressStopsAtTheResourceTargetTotal() {
        target("70211001", 1);
        assertTrue(open(player));
        chest = new GadgetChest(new EntityGadget(scene, 70211001, new Position()));
        assertTrue(open(player));
        assertEquals(1, progress(player));
    }

    @Test
    void aHandlerExceptionReleasesTheInteractionForARetry() {
        handlers.put(
                "HandbookTestChest",
                new ChestInteractHandler() {
                    @Override
                    public boolean isTwoStep() {
                        return false;
                    }

                    @Override
                    public boolean onInteract(GadgetChest current, Player member) {
                        throw new IllegalStateException("No delivery");
                    }
                });
        assertThrows(IllegalStateException.class, () -> open(player));
        assertEquals(0, progress(player));
        handler(true);
        assertTrue(open(player));
        assertEquals(1, progress(player));
    }

    @Test
    void aTwoStepChestCountsOnlyAfterTheFinishInteraction() {
        handlers.put(
                "HandbookTestChest",
                new ChestInteractHandler() {
                    @Override
                    public boolean isTwoStep() {
                        return true;
                    }

                    @Override
                    public boolean onInteract(GadgetChest current, Player member) {
                        grants.incrementAndGet();
                        return true;
                    }
                });
        assertFalse(
                chest.onInteract(
                        player,
                        GadgetInteractReq.newBuilder()
                                .setOpType(InterOpType.InterOpType_INTER_OP_START)
                                .build()));
        assertEquals(0, grants.get());
        assertEquals(0, progress(player));
        assertTrue(open(player));
        assertEquals(1, grants.get());
        assertEquals(1, progress(player));
    }

    @Test
    void scriptBossWithoutATagUsesTheBossFallbackAndDoesNotCountAsTreasure() {
        scriptDrop(true);
        entity.getMetaGadget().boss_chest = new emu.grasscutter.scripts.data.SceneBossChest();
        handlers.put(
                "HandbookTestChest",
                new BossChestInteractHandler() {
                    @Override
                    public boolean onInteract(
                            GadgetChest current, Player member, boolean condensed) {
                        grants.incrementAndGet();
                        return true;
                    }
                });
        assertTrue(open(player));
        assertEquals(1, grants.get());
        assertEquals(0, player.getMora());
        assertEquals(0, progress(player));
        assertTrue(open(guest));
        assertEquals(2, grants.get());
        assertEquals(0, progress(guest));
    }

    @Test
    void openingAnUnrelatedChestDoesNotRecomputeSnapshotTargets() {
        GameData.getInvestigationTargetDataMap()
                .put(
                        60002,
                        gson.fromJson(
                                "{id:60002,investigationId:10001,progress:1,triggerConfig:{triggerType:'TRIGGER_UNLOCK_AREA',paramList:['1','','']}}",
                                InvestigationTargetData.class));
        player.getUnlockedSceneAreas().put(3, new HashSet<>(List.of(1)));
        assertTrue(open(player));
        assertEquals(1, progress(player));
        assertEquals(0, player.getPlayerProgress().getInvestigation().get(60002));
    }
}
