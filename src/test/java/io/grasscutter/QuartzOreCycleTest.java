package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.ResourceLoader.AbilityConfigData;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.config.ConfigEntityGadget;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.data.server.*;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.AbilityCooldownInvocation;
import emu.grasscutter.game.ability.QuartzOreCycle;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.systems.InventorySystem;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.scripts.*;
import emu.grasscutter.scripts.data.controller.EntityController;
import emu.grasscutter.server.game.*;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** Native quartz material reports, actual loot mappings and the original ore controller. */
class QuartzOreCycleTest {
    private static final int ORE = 70590025;
    private static final String ROOT = "SceneObj_Area_Ljxs_Property_Prop_OreDulinsBlood_01";
    private static final Map<Integer, GadgetData> GADGETS = new HashMap<>();
    private static final Map<String, ConfigEntityGadget> CONFIGS = new HashMap<>();
    private static final Map<String, AbilityData> ABILITIES = new HashMap<>();
    private static SubfieldMapping subfields;
    private static DropSubfieldMapping mapping;
    private static DropTableExcelConfigData drop;
    private static ItemData quartz;
    private static BuffData buff;
    private PhlogistonMutationTest env;
    private EntityGadget ore;
    private Ability ability;
    private final List<Runnable> restores = new ArrayList<>();
    private final List<Integer> lua = new CopyOnWriteArrayList<>();
    private final AtomicLong nanos = new AtomicLong();

    @BeforeAll
    static void resources() throws Exception {
        Path root = Path.of("resources");
        for (var row :
                JsonUtils.loadToList(
                        root.resolve("ExcelBinOutput/GadgetExcelConfigData.json"),
                        GadgetData.class))
            if (Set.of(ORE, 70950017).contains(row.getId())) GADGETS.put(row.getId(), row);
        for (String file :
                List.of(
                        "ConfigGadget_Scene_Gather.json",
                        "ConfigGadget_Scene_Gear_SnowMountain.json")) {
            var json =
                    JsonParser.parseString(
                                    Files.readString(
                                            root.resolve("BinOutput/Gadget").resolve(file)))
                            .getAsJsonObject();
            for (var row : GADGETS.values())
                if (json.has(row.getJsonName()))
                    CONFIGS.put(
                            row.getJsonName(),
                            JsonUtils.decode(
                                    json.get(row.getJsonName()), ConfigEntityGadget.class));
        }
        for (String file :
                List.of(
                        "BinOutput/Ability/Temp/GadgetAbilities/ConfigAbility_Scene_Gather.json",
                        "BinOutput/Ability/Temp/GadgetAbilities/ConfigAbility_Scene_Gear_SnowMountain.json",
                        "BinOutput/Ability/Temp/ItemAbilities.json"))
            for (var row : JsonUtils.loadToList(root.resolve(file), AbilityConfigData.class))
                ABILITIES.put(row.Default.abilityName, row.Default);
        subfields =
                JsonUtils.loadToList(
                                root.resolve("Server/SubfieldMapping.json"), SubfieldMapping.class)
                        .stream()
                        .filter(r -> r.getEntityId() == ORE)
                        .findFirst()
                        .orElseThrow();
        mapping =
                JsonUtils.loadToList(
                                root.resolve("Server/DropSubfieldMapping.json"),
                                DropSubfieldMapping.class)
                        .stream()
                        .filter(r -> r.getDropId() == 106)
                        .findFirst()
                        .orElseThrow();
        drop =
                JsonUtils.loadToList(
                                root.resolve("Server/DropTableExcelConfigData.json"),
                                DropTableExcelConfigData.class)
                        .stream()
                        .filter(r -> r.getId() == mapping.getItemId())
                        .findFirst()
                        .orElseThrow();
        quartz =
                JsonUtils.loadToList(
                                root.resolve("ExcelBinOutput/MaterialExcelConfigData.json"),
                                ItemData.class)
                        .stream()
                        .filter(r -> r.getId() == 101005)
                        .findFirst()
                        .orElseThrow();
        buff =
                JsonUtils.loadToList(
                                root.resolve("ExcelBinOutput/BuffExcelConfigData.json"),
                                BuffData.class)
                        .stream()
                        .filter(r -> r.getId() == 500101)
                        .findFirst()
                        .orElseThrow();
        quartz.onLoad();
        buff.onLoad();
        assertEquals(1, drop.getDropVec().length);
        assertEquals(10000, drop.getDropVec()[0].getWeight());
        assertEquals(101005, drop.getDropVec()[0].getItemId());
        assertEquals("1", drop.getDropVec()[0].getCountRange());
    }

    private <K, V> void put(Map<K, V> map, K key, V value) {
        var before = map.put(key, value);
        restores.add(
                () -> {
                    if (before == null) map.remove(key);
                    else map.put(key, before);
                });
    }

    @BeforeEach
    void setup() throws Exception {
        env = new PhlogistonMutationTest();
        env.setup();
        var player = env.fixture.player;
        player.setUid(10001);
        player.setWorld(env.fixture.scene.getWorld());
        var server = env.fixture.scene.getWorld().getServer();
        player.setSession(new GameSession(server));
        var field = GameServer.class.getDeclaredField("inventorySystem");
        field.setAccessible(true);
        field.set(
                server,
                new InventorySystem(
                        server,
                        () -> 100000,
                        ignored -> fail("Quartz pickup must not write material inventory")));
        GADGETS.forEach((id, row) -> put(GameData.getGadgetDataMap(), id, row));
        CONFIGS.forEach((name, row) -> put(GameData.getGadgetConfigData(), name, row));
        ABILITIES.forEach((name, row) -> put(GameData.getAbilityDataMap(), name, row));
        put(GameData.getSubfieldMappingMap(), ORE, subfields);
        put(GameData.getDropSubfieldMappingMap(), 106, mapping);
        put(GameData.getDropTableExcelConfigDataMap(), drop.getId(), drop);
        put(GameData.getItemDataMap(), 101005, quartz);
        put(GameData.getBuffDataMap(), 500101, buff);
        ore = new EntityGadget(env.fixture.scene, ORE, new Position());
        env.fixture.scene.getEntities().put(ore.getId(), ore);
        var cycle = EntityGadget.class.getDeclaredField("quartzOreCycle");
        cycle.setAccessible(true);
        cycle.set(ore, new QuartzOreCycle(ore, nanos::get));
        ability =
                ore.getInstancedAbilities().stream()
                        .filter(a -> ROOT.equals(a.getData().abilityName))
                        .findFirst()
                        .orElseThrow();
        var script = ScriptLoader.getScript("Gadget/SubfieldDrop_Dulins_Blood.lua");
        assertNotNull(script);
        var bindings = ScriptLoader.getEngine().createBindings();
        ScriptLoader.eval(script, bindings);
        ore.setEntityController(
                new EntityController(script, bindings) {
                    @Override
                    public int onClientExecuteRequest(GameEntity entity, int p1, int p2, int p3) {
                        lua.add(p1);
                        return super.onClientExecuteRequest(entity, p1, p2, p3);
                    }
                });
    }

    @AfterEach
    void close() {
        Collections.reverse(restores);
        restores.forEach(Runnable::run);
        env.close();
    }

    private AbilityInvokeEntryOuterClass.AbilityInvokeEntry message(
            String modifier, ModifierActionOuterClass.ModifierAction action) {
        int index = new ArrayList<>(ability.getData().modifiers.keySet()).indexOf(modifier);
        assertTrue(index >= 0);
        var body =
                AbilityMetaModifierChangeOuterClass.AbilityMetaModifierChange.newBuilder()
                        .setAction(action)
                        .setModifierLocalId(index)
                        .setParentAbilityName(
                                AbilityStringOuterClass.AbilityString.newBuilder().setStr(ROOT));
        return AbilityInvokeEntryOuterClass.AbilityInvokeEntry.newBuilder()
                .setEntityId(ore.getId())
                .setArgumentType(
                        AbilityInvokeArgumentOuterClass.AbilityInvokeArgument
                                .AbilityInvokeArgument_ABILITY_META_MODIFIER_CHANGE)
                .setHead(
                        AbilityInvokeEntryHeadOuterClass.AbilityInvokeEntryHead.newBuilder()
                                .setTargetId(ore.getId())
                                .setInstancedAbilityId(
                                        action
                                                        == ModifierActionOuterClass.ModifierAction
                                                                .MODIFIER_ACTION_ADDED
                                                ? 1
                                                : 0)
                                .setInstancedModifierId(modifier.equals("Appear") ? 71 : 72))
                .setAbilityData(body.build().toByteString())
                .build();
    }

    private void add(String modifier) {
        assertTrue(
                assertDoesNotThrow(
                        () ->
                                env.fixture
                                        .player
                                        .getAbilityManager()
                                        .acceptAbilityInvoke(
                                                message(
                                                        modifier,
                                                        ModifierActionOuterClass.ModifierAction
                                                                .MODIFIER_ACTION_ADDED))));
    }

    private void remove(String modifier) {
        assertTrue(
                assertDoesNotThrow(
                        () ->
                                env.fixture
                                        .player
                                        .getAbilityManager()
                                        .acceptAbilityInvoke(
                                                message(
                                                        modifier,
                                                        ModifierActionOuterClass.ModifierAction
                                                                .MODIFIER_ACTION_REMOVED))));
    }

    private List<EntityItem> drops() {
        return env.fixture.scene.getEntities().values().stream()
                .filter(e -> e instanceof EntityItem)
                .map(e -> (EntityItem) e)
                .toList();
    }

    private void mine() {
        add("Appear");
        remove("Appear");
    }

    private void confirmDrop(int instance) throws Exception {
        var drop = ability.getData().modifiers.get("Appear").onRemoved[0];
        int local =
                ability.getData().localIdToAction.entrySet().stream()
                        .filter(e -> e.getValue() == drop)
                        .findFirst()
                        .orElseThrow()
                        .getKey();
        ability.getManager()
                .handleServerInvoke(
                        AbilityInvokeEntryOuterClass.AbilityInvokeEntry.newBuilder()
                                .setEntityId(ore.getId())
                                .setHead(
                                        AbilityInvokeEntryHeadOuterClass.AbilityInvokeEntryHead
                                                .newBuilder()
                                                .setTargetId(ore.getId())
                                                .setInstancedAbilityId(1)
                                                .setInstancedModifierId(instance)
                                                .setLocalId(local))
                                .build());
    }

    @Test
    void explicitDropConfirmationsAndRemovalShareOnePickup() throws Exception {
        confirmDrop(71);
        assertTrue(drops().isEmpty());
        add("Appear");
        confirmDrop(71);
        confirmDrop(71);
        assertEquals(1, drops().size());
        remove("Appear");
        assertEquals(1, drops().size());
        assertEquals(201, ore.getState());
        nanos.set(15_000_000_000L);
        ore.onTick(1);
        confirmDrop(71);
        assertEquals(1, drops().size());
        assertEquals(0, ore.getState());
    }

    @Test
    void anIdlessDropNeedsOneLiveApplicationAndCannotReplayAfterReset() throws Exception {
        confirmDrop(0);
        assertTrue(drops().isEmpty());
        add("Appear");
        confirmDrop(0);
        confirmDrop(0);
        remove("Appear");
        assertEquals(1, drops().size());
        assertEquals(201, ore.getState());
        nanos.set(15_000_000_000L);
        ore.onTick(1);
        confirmDrop(0);
        assertEquals(1, drops().size());
        assertEquals(0, ore.getState());
    }

    @Test
    void materialCompletionDropsOneRealPickupAndKeepsOreAlive() {
        mine();
        assertEquals(1, drops().size());
        assertEquals(101005, drops().get(0).getItemData().getId());
        assertEquals(1, drops().get(0).getCount());
        assertTrue(ore.isAlive());
        assertSame(ore, env.fixture.scene.getEntities().get(ore.getId()));
        assertEquals(201, ore.getState());
        assertEquals(List.of(201), lua);
        assertFalse(env.fixture.player.getBuffManager().hasBuff(51));
    }

    @Test
    void actualPickupCanArmTheAlreadyIntegratedIceSmash() {
        mine();
        assertEquals(1, drops().size());
        var pickup = drops().get(0);
        pickup.onInteract(
                env.fixture.player,
                GadgetInteractReqOuterClass.GadgetInteractReq.getDefaultInstance());
        assertTrue(env.fixture.player.getBuffManager().hasBuff(51));
        assertTrue(drops().isEmpty());
        var ice = new EntityGadget(env.fixture.scene, 70950017, new Position());
        env.fixture.scene.getEntities().put(ice.getId(), ice);
        env.fixture.scene.handleAttack(
                AttackResultOuterClass.AttackResult.newBuilder()
                        .setAttackerId(env.caster.getId())
                        .setDefenseId(ice.getId())
                        .build());
        assertFalse(env.fixture.scene.getEntities().containsKey(ice.getId()));
        assertFalse(env.fixture.player.getBuffManager().hasBuff(51));
    }

    @Test
    void repeatedMaterialRemovalDoesNotDropTwice() {
        mine();
        remove("Appear");
        assertEquals(1, drops().size());
        assertEquals(List.of(201), lua);
    }

    @Test
    void nativeLuaBeforeMaterialRemovalStillDropsOnce() {
        add("Appear");
        ore.onClientExecuteRequest(201, 0, 0);
        remove("Appear");
        assertEquals(1, drops().size());
        assertEquals(201, ore.getState());
        assertEquals(List.of(201), lua);
    }

    @Test
    void nativeLuaAfterMaterialRemovalDoesNotRepeatTheDrop() {
        mine();
        ore.onClientExecuteRequest(201, 0, 0);
        assertEquals(1, drops().size());
    }

    @Test
    void anUnregisteredRemovalCannotGenerateAPickup() {
        remove("Appear");
        assertTrue(drops().isEmpty());
        assertEquals(0, ore.getState());
    }

    @Test
    void aDifferentGadgetStateDoesNotAwardMaterialCompletion() {
        ore.updateState(902);
        mine();
        assertTrue(drops().isEmpty());
        assertEquals(902, ore.getState());
    }

    @Test
    void removingTheRootInvalidatesMaterialCompletion() {
        add("Appear");
        ore.getInstancedAbilities().clear();
        remove("Appear");
        assertTrue(drops().isEmpty());
        assertEquals(0, ore.getState());
    }

    @Test
    void aMissingLootMappingDoesNotHideTheOre() {
        var original = GameData.getDropSubfieldMappingMap().remove(106);
        try {
            mine();
            assertTrue(drops().isEmpty());
            assertEquals(0, ore.getState());
        } finally {
            GameData.getDropSubfieldMappingMap().put(106, original);
        }
    }

    @Test
    void parallelMaterialRemovalsProduceOnePickup() throws Exception {
        add("Appear");
        var gate = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var jobs = new ArrayList<Future<?>>();
            for (int i = 0; i < 2; i++)
                jobs.add(
                        executor.submit(
                                () -> {
                                    gate.await(5, TimeUnit.SECONDS);
                                    remove("Appear");
                                    return null;
                                }));
            for (var job : jobs) job.get(6, TimeUnit.SECONDS);
            assertEquals(1, drops().size());
            assertEquals(List.of(201), lua);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void aServerDropAndTheOriginalDeathLuaShareTheSamePickup() {
        ability.getManager()
                .executeActionNow(
                        ability,
                        ability.getData().modifiers.get("Appear").onRemoved[0],
                        com.google.protobuf.ByteString.EMPTY,
                        ore);
        assertEquals(1, drops().size());
        ore.onDeath(0);
        assertEquals(1, drops().size());
    }

    @Test
    void theOriginalLuaRestoresAtFifteenSecondsWithoutAClientRemoval() {
        mine();
        add("Disppear");
        nanos.set(14_999_999_999L);
        ore.onTick(1);
        assertEquals(201, ore.getState());
        assertEquals(List.of(201), lua);
        nanos.incrementAndGet();
        ore.onTick(2);
        ore.onTick(3);
        assertEquals(0, ore.getState());
        assertEquals(List.of(201, 0), lua);
        assertEquals(1, drops().size());
    }

    @Test
    void anEarlyDisappearanceRemovalCannotResetTheOre() {
        mine();
        add("Disppear");
        remove("Disppear");
        assertEquals(201, ore.getState());
        nanos.set(15_000_000_000L);
        ore.onTick(1);
        assertEquals(0, ore.getState());
        assertEquals(List.of(201, 0), lua);
    }

    @Test
    void neitherAnEarlyNativeLuaNorALeafRestoreCanResetTheOre() {
        mine();
        ore.onClientExecuteRequest(0, 0, 0);
        ability.getManager()
                .executeActionNow(
                        ability,
                        ability.getData().modifiers.get("Disppear").onRemoved[0],
                        com.google.protobuf.ByteString.EMPTY,
                        ore);
        assertEquals(201, ore.getState());
        assertEquals(List.of(201), lua);
        nanos.set(15_000_000_000L);
        ore.onClientExecuteRequest(0, 0, 0);
        ore.onTick(1);
        assertEquals(0, ore.getState());
        assertEquals(List.of(201, 0), lua);
    }

    @Test
    void nativeLuaFirstDoesNotRestartItsDeadlineAtTheLaterMaterialReport() {
        add("Appear");
        ore.onClientExecuteRequest(201, 0, 0);
        nanos.set(10_000_000_000L);
        remove("Appear");
        nanos.set(15_000_000_000L);
        ore.onTick(1);
        assertEquals(0, ore.getState());
        assertEquals(List.of(201, 0), lua);
        assertEquals(1, drops().size());
    }

    @Test
    void aFreshMaterialAfterRestorationHasANewPickup() {
        mine();
        nanos.set(15_000_000_000L);
        ore.onTick(1);
        mine();
        assertEquals(2, drops().size());
        assertEquals(201, ore.getState());
        assertEquals(List.of(201, 0, 201), lua);
        nanos.set(29_999_999_999L);
        ore.onTick(2);
        assertEquals(201, ore.getState());
        nanos.incrementAndGet();
        ore.onTick(3);
        assertEquals(0, ore.getState());
    }

    @Test
    void aKnownOldAppearCannotAwardTheNextCycle() {
        add("Appear");
        var old = ore.getInstancedModifiers().get(71);
        ore.onClientExecuteRequest(201, 0, 0);
        nanos.set(15_000_000_000L);
        ore.onTick(1);
        AbilityCooldownInvocation.run(
                old,
                () ->
                        ability.getManager()
                                .executeActionNow(
                                        ability,
                                        ability.getData().modifiers.get("Appear").onRemoved[0],
                                        com.google.protobuf.ByteString.EMPTY,
                                        ore));
        assertTrue(drops().isEmpty());
        assertEquals(0, ore.getState());
        // Reusing the numeric ID for an actual new application gets a new ledger.
        add("Appear");
        assertNotSame(old, ore.getInstancedModifiers().get(71));
        remove("Appear");
        assertEquals(1, drops().size());
        assertEquals(201, ore.getState());
    }

    @Test
    void aKnownOldDisappearCannotRestoreANewerCycle() {
        mine();
        add("Disppear");
        var old = ore.getInstancedModifiers().get(72);
        nanos.set(15_000_000_000L);
        ore.onTick(1);
        mine();
        AbilityCooldownInvocation.run(
                old,
                () ->
                        ability.getManager()
                                .executeActionNow(
                                        ability,
                                        ability.getData().modifiers.get("Disppear").onRemoved[0],
                                        com.google.protobuf.ByteString.EMPTY,
                                        ore));
        assertEquals(201, ore.getState());
        assertEquals(List.of(201, 0, 201), lua);
        assertEquals(2, drops().size());
    }

    @Test
    void anUnrelatedStateCancelsTheOldTimer() {
        mine();
        ore.updateState(902);
        nanos.set(30_000_000_000L);
        ore.onTick(1);
        assertEquals(902, ore.getState());
        assertEquals(List.of(201), lua);
        ore.updateState(0);
        mine();
        assertEquals(2, drops().size());
        assertEquals(201, ore.getState());
    }

    @Test
    void aRemovedRootCannotRestoreTheOldOre() {
        mine();
        ore.getInstancedAbilities().clear();
        nanos.set(15_000_000_000L);
        ore.onTick(1);
        assertEquals(201, ore.getState());
        assertEquals(List.of(201), lua);
    }

    @Test
    void leavingTheSceneCannotRestoreTheOldOre() {
        mine();
        env.fixture.player.setScene(null);
        nanos.set(15_000_000_000L);
        ore.onTick(1);
        assertEquals(201, ore.getState());
        assertEquals(List.of(201), lua);
    }

    @Test
    void aRemovedOreCannotRestoreItselfOrAReplacement() {
        mine();
        env.fixture.scene.removeEntity(ore);
        nanos.set(15_000_000_000L);
        ore.onTick(1);
        assertEquals(201, ore.getState());
        assertEquals(List.of(201), lua);
        assertFalse(env.fixture.scene.getEntities().containsKey(ore.getId()));
    }

    @Test
    void missingItemDataDoesNotHideOrConsumeTheOre() {
        var original = GameData.getItemDataMap().remove(101005);
        try {
            mine();
            assertTrue(drops().isEmpty());
            assertEquals(0, ore.getState());
        } finally {
            GameData.getItemDataMap().put(101005, original);
        }
        mine();
        assertEquals(1, drops().size());
        assertEquals(201, ore.getState());
    }

    @Test
    void waitingForTheApplicationLedgerDoesNotBlockTheScene() throws Exception {
        add("Appear");
        var controller = ore.getInstancedModifiers().get(71);
        var action = ability.getData().modifiers.get("Appear").onRemoved[0];
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var callbackThread = new java.util.concurrent.atomic.AtomicReference<Thread>();
        var executor = Executors.newFixedThreadPool(3);
        var holding =
                executor.submit(
                        () ->
                                AbilityCooldownInvocation.run(
                                        controller,
                                        () ->
                                                AbilityCooldownInvocation.recordApplication(
                                                        ability,
                                                        action,
                                                        () -> {
                                                            entered.countDown();
                                                            assertTrue(
                                                                    assertDoesNotThrow(
                                                                            () ->
                                                                                    release.await(
                                                                                            5,
                                                                                            TimeUnit
                                                                                                    .SECONDS)));
                                                            return false;
                                                        })));
        Future<?> callback = null;
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            callback =
                    executor.submit(
                            () -> {
                                callbackThread.set(Thread.currentThread());
                                AbilityCooldownInvocation.run(
                                        controller,
                                        () ->
                                                ability.getManager()
                                                        .executeActionNow(
                                                                ability,
                                                                action,
                                                                com.google.protobuf.ByteString
                                                                        .EMPTY,
                                                                ore));
                            });
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while ((callbackThread.get() == null
                            || callbackThread.get().getState() != Thread.State.BLOCKED)
                    && System.nanoTime() < until) Thread.sleep(5);
            assertNotNull(callbackThread.get());
            assertEquals(Thread.State.BLOCKED, callbackThread.get().getState());
            var scene =
                    executor.submit(
                            () -> {
                                synchronized (ore.getScene()) {
                                    return true;
                                }
                            });
            assertTrue(
                    scene.get(500, TimeUnit.MILLISECONDS),
                    "A ledger waiter must release the scene");
        } finally {
            release.countDown();
            holding.get(3, TimeUnit.SECONDS);
            if (callback != null) callback.get(3, TimeUnit.SECONDS);
            executor.shutdownNow();
        }
        assertEquals(1, drops().size());
    }
}
