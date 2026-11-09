package emu.grasscutter.data;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import emu.grasscutter.GameConstants;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.config.ConfigEntityGadget;
import emu.grasscutter.data.binout.config.ConfigGlobalCombat;
import emu.grasscutter.data.binout.routes.Route;
import emu.grasscutter.data.excels.GadgetData;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.data.server.GadgetMapping;
import emu.grasscutter.game.ability.AbilityManager;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityWeapon;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.TeamManager;
import emu.grasscutter.game.props.EntityIdType;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.game.world.World;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.server.scheduler.ServerTaskScheduler;
import emu.grasscutter.utils.JsonUtils;
import emu.grasscutter.utils.Utils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadPoolExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * E-01/R3 LC-01/LC-02: current Java Metal construction and local lifecycle only.
 * Run in an isolated worker with its version-14 config/R/S/D/cache/packets/plugins bootstrap
 * prepared before Grasscutter class initialization. This does not approve target resources,
 * aliases, empty definitions, ordinary entry packets, persistence, or client behavior.
 */
class D02MetalWeaponLifecycleTest {
    private static final int GADGET = 50011101;
    private static final String KEY = "Weapon_Sword_Metal";
    private static final String METAL = "Avatar_Weapon_Metal";
    private static final String OWNED_OVERRIDE = "D02_Owned_Override";
    private static Input configInput;
    private static Input abilityInput;
    private static Input gadgetInput;
    private static Input globalInput;

    @TempDir Path directory;
    private Map<String, ConfigEntityGadget> previousConfigs;
    private Map<String, AbilityData> previousAbilities;
    private Int2ObjectMap<String> previousHashes;
    private Int2ObjectMap<GadgetData> previousGadgets;
    private Int2ObjectMap<GadgetMapping> previousMappings;
    private ConfigGlobalCombat previousGlobal;
    private boolean previousScriptFlag;
    private boolean previousLoadedAll;
    private boolean hadScene3Route;
    private Int2ObjectMap<Route> previousScene3Route;
    private ConfigEntityGadget config;
    private AbilityData definition;
    private GadgetData gadget;
    private TestPlayer player;
    private TestAvatar caster;
    private OwnedScene scene;
    private EntityWeapon weapon;
    private final List<BasePacket> packets = new ArrayList<>();
    private ExecutorSnapshot executorBefore;
    private JsonObject rawUnknown;

    @BeforeAll
    static void pinInputsAndInitialize() throws Exception {
        configInput = pin("d02.gadgetAbility.config", 361168,
                "700d2c4cb1ea11d5b3036002159326250b3817c127de087b697515321eda75a8");
        abilityInput = pin("d02.gadgetAbility.abilities", 3745,
                "b3f7f417c47ff4f2d726728450ca2092e07bc060458d874f7b919c57c2c564d2");
        gadgetInput = pin("d02.weaponLifecycle.gadget", 50,
                "bcf1316b7dc1840e71d1820b37f92770eb69363a58f7892eb97781400ae351af");
        globalInput = pin("d02.weaponLifecycle.global", 79,
                "4875c7dd1885fa1f798f2c16f123ddf4be9623f1bea79fcddd77a4f495658a85");
        Grasscutter.getLogger();
    }

    @BeforeEach
    void loadFreshInputsAndOwnedContainer() throws Exception {
        previousConfigs = new HashMap<>(GameData.getGadgetConfigData());
        previousAbilities = new HashMap<>(GameData.getAbilityDataMap());
        previousHashes = new Int2ObjectOpenHashMap<>(GameData.getAbilityHashes());
        previousGadgets = new Int2ObjectOpenHashMap<>(GameData.getGadgetDataMap());
        previousMappings = new Int2ObjectOpenHashMap<>(GameData.getGadgetMappingMap());
        previousGlobal = GameData.getConfigGlobalCombat();
        previousScriptFlag = Grasscutter.getConfig().server.game.enableScriptInBigWorld;
        previousLoadedAll = ResourceLoader.isLoadedAll();
        hadScene3Route = GameData.getSceneRouteData().containsKey(3);
        previousScene3Route = GameData.getSceneRouteData().get(3);
        GameData.getGadgetConfigData().clear();
        GameData.getAbilityDataMap().clear();
        GameData.getAbilityHashes().clear();
        GameData.getGadgetDataMap().clear();
        GameData.getGadgetMappingMap().clear();

        Path configPath = copy(configInput, "R/BinOutput/Gadget/ConfigGadget_Avatar_Equip.json");
        Path abilityPath = copy(abilityInput,
                "R/BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_Weapon.json");
        Path gadgetPath = copy(gadgetInput, "R/ExcelBinOutput/GadgetExcelConfigData.json");
        Path globalPath = copy(globalInput, "R/BinOutput/Common/ConfigGlobalCombat.json");
        Files.createDirectories(directory.resolve("S"));
        Files.createDirectories(directory.resolve("D"));

        var loadConfig = ResourceLoader.class.getDeclaredMethod(
                "loadConfigMapFile", Map.class, Path.class, Class.class);
        loadConfig.setAccessible(true);
        loadConfig.invoke(null, GameData.getGadgetConfigData(), configPath, ConfigEntityGadget.class);
        var loadAbilities = ResourceLoader.class.getDeclaredMethod("loadAbilityModifiers", Path.class);
        loadAbilities.setAccessible(true);
        loadAbilities.invoke(null, abilityPath);
        ResourceLoader.loadFromResource(GadgetData.class, gadgetPath, GameData.getGadgetDataMap());
        GameData.setConfigGlobalCombat(JsonUtils.loadToClass(globalPath, ConfigGlobalCombat.class));
        assertEquals(34, GameData.getGadgetConfigData().size());
        assertEquals(3, GameData.getAbilityDataMap().size());
        assertEquals(3, GameData.getAbilityHashes().size());
        assertEquals(1, GameData.getGadgetDataMap().size());
        gadget = GameData.getGadgetDataMap().get(GADGET);
        config = GameData.getGadgetConfigData().get(KEY);
        definition = GameData.getAbilityData(METAL);
        assertNotNull(gadget);
        assertNotNull(config);
        assertNotNull(definition);
        assertEquals(KEY, gadget.getJsonName());
        assertEquals(1, config.getAbilities().size());
        var reference = config.getAbilities().iterator().next();
        assertEquals("Metal", reference.getAbilityID());
        assertEquals(METAL, reference.getAbilityName());
        assertSame(definition, GameData.getAbilityData(reference.getAbilityName()));
        assertEquals(METAL, GameData.getAbilityHashes().get(Utils.abilityHash(METAL)));
        assertFalse(initialized(definition));
        assertNull(definition.modifiers);
        assertNull(definition.onAdded);
        assertNull(definition.abilityMixins);
        assertNull(definition.abilitySpecials);
        rawUnknown = unknownObject();
        assertRawUnknown(rawUnknown);
        assertTrue(GameData.getConfigGlobalCombat().getDefaultAbilities().getDefaultMPLevelAbilities().isEmpty());
        assertTrue(GameData.getConfigGlobalCombat().getDefaultAbilities().getLevelElementAbilities().isEmpty());

        executorBefore = executorSnapshot();
        assertEquals(new ExecutorSnapshot(0, 0, 0, 0, 0), executorBefore);
        Grasscutter.getConfig().server.game.enableScriptInBigWorld = false;
        player = new TestPlayer(packets);
        var world = new OwnedWorld(player, packets);
        player.setWorld(world);
        SceneData data = JsonUtils.decode("{\"id\":3,\"type\":\"SCENE_WORLD\"}", SceneData.class);
        scene = new OwnedScene(world, data, packets);
        player.setScene(scene);
        caster = new TestAvatar(player);
        assertTrue(scene.getScriptManager().isInitAttempted());
        assertTrue(scene.getEntities().isEmpty());
        assertTrue(scene.getWeaponEntities().isEmpty());
        assertSchedulerEmpty();
    }

    @Test
    void lc01RealMetalConstructionRootsOverridesAndInvalidation() throws Exception {
        weapon = new EntityWeapon(scene, GADGET, caster);
        assertWeaponIdentityAndCombat();
        assertEquals(1, weapon.getInstancedAbilities().size());
        var ability = weapon.getInstancedAbilities().get(0);
        assertNotNull(ability);
        assertSame(definition, ability.getData());
        assertSame(weapon, ability.getOwner());
        assertSame(player, ability.getPlayerOwner());
        assertSame(player.getAbilityManager(), ability.getManager());
        assertTrue(initialized(definition));
        assertNotNull(definition.modifiers);
        assertTrue(definition.modifiers.isEmpty());
        assertTrue(definition.localIdToAction.isEmpty());
        assertTrue(definition.localIdToMixin.isEmpty());
        assertTrue(definition.shieldBarCallbacks.isEmpty());
        assertNull(definition.onAdded);
        assertNull(definition.abilityMixins);
        // Real fireAbilityOnAdded reaches its empty-sequence return; target raw semantics stay unknown.
        assertTrue(ability.getAbilitySpecials().isEmpty());
        assertTrue(weapon.getAbilityRoots().snapshot().isEmpty());
        scene.getWeaponEntities().put(weapon.getId(), weapon);
        assertSame(weapon, scene.getEntityById(weapon.getId()));
        var roots = weapon.getAbilityRoots().snapshot();
        assertEquals(1, roots.size());
        assertEquals(1, roots.get(0).getInstancedAbilityId());
        assertEquals(METAL, roots.get(0).getAbilityName().getStr());
        assertEquals(GameConstants.DEFAULT_ABILITY_NAME, roots.get(0).getAbilityOverride().getHash());
        assertEquals(0, weapon.getAbilityRoots().toSyncState().getAppliedAbilitiesCount());

        ability.getAbilitySpecials().put(OWNED_OVERRIDE, 7.25f);
        var projected = weapon.getAbilityRoots().toSyncState();
        assertFalse(projected.getIsInited());
        assertEquals(1, projected.getAppliedAbilitiesCount());
        var applied = projected.getAppliedAbilities(0);
        assertEquals(1, applied.getInstancedAbilityId());
        assertEquals(METAL, applied.getAbilityName().getStr());
        assertEquals(1, applied.getOverrideMapCount());
        assertEquals(OWNED_OVERRIDE, applied.getOverrideMap(0).getKey().getStr());
        assertEquals(7.25f, applied.getOverrideMap(0).getFloatValue());
        ability.getAbilitySpecials().removeFloat(OWNED_OVERRIDE);
        assertTrue(ability.getAbilitySpecials().isEmpty());
        assertNull(definition.abilitySpecials);
        assertEquals(0, weapon.getAbilityRoots().toSyncState().getAppliedAbilitiesCount());
        assertControlBlock();

        long lifetime = weapon.getAbilityLifecycleVersion();
        weapon.invalidateAbilityLifecycle();
        assertFalse(weapon.isAbilityLifecycleCurrent(lifetime));
        assertSame(weapon, scene.getWeaponEntities().remove(weapon.getId()));
        assertTrue(weapon.getAbilityRoots().snapshot().isEmpty());
        assertEquals(0, weapon.getAbilityRoots().toSyncState().getAppliedAbilitiesCount());
    }

    @Test
    void lc02OnlyPrivateDefinitionRemovalLeavesRealNullSlotAndControlBlock() throws Exception {
        assertSame(definition, GameData.getAbilityDataMap().remove(METAL));
        assertEquals(2, GameData.getAbilityDataMap().size());
        assertNull(GameData.getAbilityData(METAL));
        assertEquals(METAL, GameData.getAbilityHashes().get(Utils.abilityHash(METAL)));
        weapon = new EntityWeapon(scene, GADGET, caster);
        assertWeaponIdentityAndCombat();
        assertEquals(1, weapon.getInstancedAbilities().size());
        assertNull(weapon.getInstancedAbilities().get(0));
        assertFalse(initialized(definition));
        scene.getWeaponEntities().put(weapon.getId(), weapon);
        assertSame(weapon, scene.getEntityById(weapon.getId()));
        assertControlBlock();
        assertTrue(weapon.getAbilityRoots().snapshot().isEmpty());
        assertEquals(0, weapon.getAbilityRoots().toSyncState().getAppliedAbilitiesCount());
        weapon.invalidateAbilityLifecycle();
        assertSame(weapon, scene.getWeaponEntities().remove(weapon.getId()));
        assertTrue(weapon.getAbilityRoots().snapshot().isEmpty());
        // This is Java null-slot alignment behavior, never permission to omit the target definition.
    }

    @AfterEach
    void verifyNoWorkAndRestoreOriginalState() throws Exception {
        try {
            if (weapon != null) {
                weapon.invalidateAbilityLifecycle();
                scene.getWeaponEntities().remove(weapon.getId(), weapon);
            }
            if (scene != null) {
                assertTrue(scene.getEntities().isEmpty());
                assertTrue(scene.getWeaponEntities().isEmpty());
                assertSchedulerEmpty();
            }
            if (executorBefore != null) {
                var after = executorSnapshot();
                System.out.println("D02 Metal executor before=" + executorBefore + " after=" + after);
                assertEquals(executorBefore, after, "no callback task, completion, active worker, queue or pool creation");
            }
            assertTrue(packets.isEmpty(), "unexpected local send/broadcast");
            assertEquals(rawUnknown, unknownObject());
            assertRawUnknown(unknownObject());
            for (var input : List.of(configInput, abilityInput, gadgetInput, globalInput)) {
                byte[] after = Files.readAllBytes(input.path());
                assertArrayEquals(input.bytes(), after, "original input bytes changed");
                assertEquals(input.sha256(), HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(after)), "original input SHA-256 changed");
            }
            assertEquals(previousLoadedAll, ResourceLoader.isLoadedAll());
        } finally {
            GameData.getGadgetConfigData().clear();
            GameData.getGadgetConfigData().putAll(previousConfigs);
            GameData.getAbilityDataMap().clear();
            GameData.getAbilityDataMap().putAll(previousAbilities);
            GameData.getAbilityHashes().clear();
            GameData.getAbilityHashes().putAll(previousHashes);
            GameData.getGadgetDataMap().clear();
            GameData.getGadgetDataMap().putAll(previousGadgets);
            GameData.getGadgetMappingMap().clear();
            GameData.getGadgetMappingMap().putAll(previousMappings);
            GameData.setConfigGlobalCombat(previousGlobal);
            Grasscutter.getConfig().server.game.enableScriptInBigWorld = previousScriptFlag;
            if (hadScene3Route) {
                GameData.getSceneRouteData().put(3, previousScene3Route);
            } else {
                GameData.getSceneRouteData().remove(3);
            }
        }
        assertEquals(hadScene3Route, GameData.getSceneRouteData().containsKey(3));
        assertSame(previousScene3Route, GameData.getSceneRouteData().get(3));
        assertSame(previousGlobal, GameData.getConfigGlobalCombat());
        assertEquals(previousLoadedAll, ResourceLoader.isLoadedAll());
    }

    private void assertWeaponIdentityAndCombat() {
        assertSame(gadget, weapon.getGadgetData());
        assertSame(config, weapon.getConfigGadget());
        assertSame(caster, weapon.getCasterAvatar());
        assertSame(scene, weapon.getScene());
        assertEquals(GADGET, weapon.getGadgetId());
        assertNotEquals(0, weapon.getId());
        assertEquals(EntityIdType.WEAPON.getId(), weapon.getId() >> GameConstants.ENTITY_ID_BIT_SHIFT);
        assertNotEquals(weapon.getId(), scene.getWorld().getNextEntityId(EntityIdType.WEAPON));
        assertEquals(9999999f, weapon.getFightProperty(FightProperty.FIGHT_PROP_BASE_HP));
        assertEquals(9999999f, weapon.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP));
        assertEquals(Float.POSITIVE_INFINITY, weapon.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertTrue(weapon.isLockHP());
        assertEquals(0f, weapon.getFightProperty(FightProperty.FIGHT_PROP_BASE_ATTACK));
        assertEquals(0f, weapon.getFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK));
        assertEquals(0f, weapon.getFightProperty(FightProperty.FIGHT_PROP_BASE_DEFENSE));
        assertEquals(0f, weapon.getFightProperty(FightProperty.FIGHT_PROP_CUR_DEFENSE));
        var raw = JsonParser.parseString(new String(configInput.bytes(), StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject(KEY);
        assertEquals(999999, raw.getAsJsonObject("combat").getAsJsonObject("property").get("defense").getAsInt());
        assertEquals(0f, config.getCombat().getProperty().getDefence());
        assertNull(config.getConfigCommon());
    }

    private void assertControlBlock() {
        // Direct Java block inspection; ordinary D-02 entry is not claimed to send this block.
        var block = weapon.getAbilityControlBlock();
        assertEquals(1, block.getAbilityEmbryoListCount());
        var embryo = block.getAbilityEmbryoList(0);
        assertEquals(1, embryo.getAbilityId());
        assertEquals(Utils.abilityHash(METAL), embryo.getAbilityNameHash());
        assertEquals(GameConstants.DEFAULT_ABILITY_NAME, embryo.getAbilityOverrideNameHash());
    }

    private void assertSchedulerEmpty() throws Exception {
        for (String name : List.of("tasks", "asyncTasks")) {
            var field = ServerTaskScheduler.class.getDeclaredField(name);
            field.setAccessible(true);
            assertTrue(((Map<?, ?>) field.get(scene.getScheduler())).isEmpty(), name);
        }
    }

    private static boolean initialized(AbilityData data) throws Exception {
        var field = AbilityData.class.getDeclaredField("_initialized");
        field.setAccessible(true);
        return field.getBoolean(data);
    }

    private record ExecutorSnapshot(long tasks, long completed, int active, int queued, int pool) {}

    private static ExecutorSnapshot executorSnapshot() {
        var executor = (ThreadPoolExecutor) AbilityManager.eventExecutor;
        return new ExecutorSnapshot(executor.getTaskCount(), executor.getCompletedTaskCount(),
                executor.getActiveCount(), executor.getQueue().size(), executor.getPoolSize());
    }

    private record Input(Path path, byte[] bytes, String sha256) {}

    private static Input pin(String property, int size, String sha256) throws Exception {
        String value = System.getProperty(property);
        assertNotNull(value, "required system property " + property);
        assertFalse(value.isBlank(), "required system property " + property);
        Path path = Path.of(value);
        byte[] bytes = Files.readAllBytes(path);
        assertEquals(size, bytes.length, property);
        assertEquals(sha256, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)), property);
        return new Input(path, bytes, sha256);
    }

    private Path copy(Input input, String relative) throws Exception {
        Path path = directory.resolve(relative);
        Files.createDirectories(path.getParent());
        Files.write(path, input.bytes());
        return path;
    }

    private static JsonObject unknownObject() {
        JsonObject result = null;
        for (var item : JsonParser.parseString(new String(abilityInput.bytes(), StandardCharsets.UTF_8)).getAsJsonArray()) {
            var value = item.getAsJsonObject().getAsJsonObject("Default");
            if (METAL.equals(value.get("abilityName").getAsString())) {
                assertNull(result);
                result = value.getAsJsonObject("LKLPDIMKLKI");
            }
        }
        assertNotNull(result);
        return result;
    }

    private static void assertRawUnknown(JsonObject value) {
        assertEquals(Set.of("elementDurability", "minus_one", "IAKPBCLBNKF", "isLimitedProperties"), value.keySet());
        assertTrue(value.getAsJsonPrimitive("elementDurability").isNumber());
        assertTrue(value.getAsJsonPrimitive("minus_one").isNumber());
        assertTrue(value.getAsJsonPrimitive("IAKPBCLBNKF").isNumber());
        assertTrue(value.getAsJsonPrimitive("isLimitedProperties").isBoolean());
        assertEquals(100, value.get("elementDurability").getAsInt());
        assertEquals(-1, value.get("minus_one").getAsInt());
        assertEquals(-1, value.get("IAKPBCLBNKF").getAsInt());
        assertTrue(value.get("isLimitedProperties").getAsBoolean());
    }

    private static final class TestPlayer extends Player {
        private final TeamManager ownedTeam = new TeamManager(this);
        private final List<BasePacket> packets;

        private TestPlayer(List<BasePacket> packets) {
            super();
            this.packets = packets;
        }

        @Override public TeamManager getTeamManager() { return ownedTeam; }
        @Override public void sendPacket(BasePacket packet) { packets.add(packet); }
        @Override public void save() { throw new AssertionError("D02 lifecycle fixture must not save Player"); }
    }

    private static final class TestAvatar extends Avatar {
        private final Player player;
        private TestAvatar(Player player) { super(); this.player = player; }
        @Override public Player getPlayer() { return player; }
        @Override public void save() { throw new AssertionError("D02 lifecycle fixture must not save Avatar"); }
    }

    private static final class OwnedWorld extends World {
        private final List<BasePacket> packets;
        private OwnedWorld(Player player, List<BasePacket> packets) {
            super((GameServer) null, player);
            this.packets = packets;
        }
        @Override public void broadcastPacket(BasePacket packet) { packets.add(packet); }
    }

    private static final class OwnedScene extends Scene {
        private final List<BasePacket> packets;
        private OwnedScene(World world, SceneData data, List<BasePacket> packets) {
            super(world, data, false);
            this.packets = packets;
        }
        @Override public void broadcastPacket(BasePacket packet) { packets.add(packet); }
        @Override public void broadcastPacketToOthers(Player excluded, BasePacket packet) { packets.add(packet); }
    }
}
