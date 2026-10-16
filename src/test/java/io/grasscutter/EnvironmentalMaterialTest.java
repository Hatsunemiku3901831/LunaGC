package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.ResourceLoader.AbilityConfigData;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.config.ConfigEntityGadget;
import emu.grasscutter.data.excels.GadgetData;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.gadget.GadgetChest;
import emu.grasscutter.game.entity.gadget.chest.ChestInteractHandler;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ElementType;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.game.world.WorldDataSystem;
import emu.grasscutter.net.proto.AbilityInvokeArgumentOuterClass.AbilityInvokeArgument;
import emu.grasscutter.net.proto.AbilityInvokeEntryHeadOuterClass.AbilityInvokeEntryHead;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.net.proto.AbilityMetaModifierChangeOuterClass.AbilityMetaModifierChange;
import emu.grasscutter.net.proto.AbilityStringOuterClass.AbilityString;
import emu.grasscutter.net.proto.GadgetInteractReqOuterClass.GadgetInteractReq;
import emu.grasscutter.net.proto.ModifierActionOuterClass.ModifierAction;
import emu.grasscutter.scripts.SceneScriptManager;
import emu.grasscutter.scripts.ScriptLoader;
import emu.grasscutter.scripts.constants.ScriptGadgetState;
import emu.grasscutter.scripts.data.ScriptArgs;
import emu.grasscutter.scripts.data.controller.EntityController;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Current resource materials through actual modifier confirmations and the original chest Lua. */
class EnvironmentalMaterialTest {
    private static final Map<Integer, GadgetData> GADGETS = new HashMap<>();
    private static final Map<String, ConfigEntityGadget> CONFIGS = new HashMap<>();
    private static final Map<String, AbilityData> ABILITIES = new HashMap<>();
    private AbilityTestFixture fixture;
    private final List<Runnable> restores = new ArrayList<>();
    private final List<ScriptArgs> events = new ArrayList<>();
    private int grants, luaCalls;

    @BeforeAll
    static void resources() throws Exception {
        if (ScriptLoader.getEngine() == null) ScriptLoader.init();
        for (var row :
                JsonUtils.loadToList(
                        Path.of("resources/ExcelBinOutput/GadgetExcelConfigData.json"),
                        GadgetData.class))
            if (Set.of(70210001, 70210013, 70210014, 70210063, 70220003, 70220020)
                    .contains(row.getId())) GADGETS.put(row.getId(), row);
        for (String file :
                List.of(
                        "ConfigGadget_Scene_Chest.json",
                        "ConfigGadget_Scene_Environment_WoodenMaterial.json")) {
            var json =
                    JsonParser.parseString(
                                    Files.readString(Path.of("resources/BinOutput/Gadget", file)))
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
                        "ConfigAbility_Scene_Chest.json",
                        "ConfigAbility_Scene_Environment_WoodenMaterial.json"))
            for (var row :
                    JsonUtils.loadToList(
                            Path.of("resources/BinOutput/Ability/Temp/GadgetAbilities", file),
                            AbilityConfigData.class))
                ABILITIES.put(row.Default.abilityName, row.Default);
    }

    private <K, V> void put(Map<K, V> map, K key, V value) {
        V before = map.put(key, value);
        restores.add(
                () -> {
                    if (before == null) map.remove(key);
                    else map.put(key, before);
                });
    }

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        fixture.player.setWorld(fixture.scene.getWorld());
        GADGETS.forEach((id, data) -> put(GameData.getGadgetDataMap(), id, data));
        CONFIGS.forEach((name, data) -> put(GameData.getGadgetConfigData(), name, data));
        ABILITIES.forEach((name, data) -> put(GameData.getAbilityDataMap(), name, data));
        var field = fixture.scene.getClass().getSuperclass().getDeclaredField("scriptManager");
        field.setAccessible(true);
        field.set(
                fixture.scene,
                new SceneScriptManager(fixture.scene, false) {
                    @Override
                    public Future<?> callEvent(ScriptArgs args) {
                        events.add(args);
                        return CompletableFuture.completedFuture(null);
                    }
                });
        var server = fixture.scene.getWorld().getServer();
        fixture.player.setSession(new emu.grasscutter.server.game.GameSession(server));
        var worldData = new WorldDataSystem(server);
        var worldField = GameServer.class.getDeclaredField("worldDataSystem");
        worldField.setAccessible(true);
        worldField.set(server, worldData);
        for (var data : GADGETS.values())
            worldData
                    .getChestInteractHandlerMap()
                    .put(
                            data.getJsonName(),
                            new ChestInteractHandler() {
                                @Override
                                public boolean isTwoStep() {
                                    return false;
                                }

                                @Override
                                public boolean onInteract(GadgetChest chest, Player player) {
                                    grants++;
                                    return true;
                                }
                            });
    }

    @AfterEach
    void close() {
        Collections.reverse(restores);
        restores.forEach(Runnable::run);
        fixture.close();
    }

    private EntityGadget gadget(int id, int state) throws Exception {
        var gadget = new EntityGadget(fixture.scene, id, new Position());
        fixture.scene.getEntities().put(gadget.getId(), gadget);
        gadget.setState(state);
        gadget.buildContent();
        if (gadget.getContent() instanceof GadgetChest) {
            var script = ScriptLoader.getScript("Gadget/Chest_Interact.lua");
            assertNotNull(script);
            var bindings = ScriptLoader.getEngine().createBindings();
            ScriptLoader.eval(script, bindings);
            gadget.setEntityController(
                    new EntityController(script, bindings) {
                        @Override
                        public int onClientExecuteRequest(
                                emu.grasscutter.game.entity.GameEntity entity,
                                int p1,
                                int p2,
                                int p3) {
                            luaCalls++;
                            return super.onClientExecuteRequest(entity, p1, p2, p3);
                        }
                    });
        }
        return gadget;
    }

    private boolean confirm(EntityGadget gadget, String material, ModifierAction action)
            throws Exception {
        return confirm(gadget, gadget, material, action);
    }

    private boolean confirm(
            EntityGadget gadget, EntityGadget rootOwner, String material, ModifierAction action)
            throws Exception {
        return fixture.player
                .getAbilityManager()
                .acceptAbilityInvoke(notification(gadget, rootOwner, material, action));
    }

    private AbilityInvokeEntry notification(
            EntityGadget gadget, EntityGadget rootOwner, String material, ModifierAction action) {
        var ability = rootOwner.getInstancedAbilities().get(0);
        int index = new ArrayList<>(ability.getData().modifiers.keySet()).indexOf(material);
        assertTrue(index >= 0);
        var body =
                AbilityMetaModifierChange.newBuilder()
                        .setAction(action)
                        .setModifierLocalId(index)
                        .setParentAbilityName(
                                AbilityString.newBuilder().setStr(ability.getData().abilityName));
        return AbilityInvokeEntry.newBuilder()
                .setEntityId(gadget.getId())
                .setArgumentType(
                        AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_MODIFIER_CHANGE)
                .setHead(
                        AbilityInvokeEntryHead.newBuilder()
                                .setTargetId(rootOwner.getId())
                                .setInstancedAbilityId(
                                        action == ModifierAction.MODIFIER_ACTION_ADDED ? 1 : 0)
                                .setInstancedModifierId(71))
                .setAbilityData(body.build().toByteString())
                .build();
    }

    private int seal(int id) {
        return id == 70210013 ? 104 : id == 70210014 ? 105 : 106;
    }

    private String material(int id) {
        return id == 70210013 ? "GrassMaterial" : id == 70210014 ? "IceMaterial" : "Chest_Rock";
    }

    @ParameterizedTest
    @ValueSource(ints = {70210013, 70210014, 70210063})
    void native_zero_unseals_the_material_and_later_removal_does_not_repeat_lua(int id)
            throws Exception {
        var entity = gadget(id, seal(id));
        assertTrue(confirm(entity, material(id), ModifierAction.MODIFIER_ACTION_ADDED));
        var zero =
                AbilityInvokeEntry.newBuilder()
                        .setEntityId(entity.getId())
                        .setArgumentType(
                                AbilityInvokeArgument
                                        .AbilityInvokeArgument_ABILITY_META_DURABILITY_IS_ZERO)
                        .setHead(
                                AbilityInvokeEntryHead.newBuilder()
                                        .setTargetId(entity.getId())
                                        .setInstancedAbilityId(1)
                                        .setInstancedModifierId(71))
                        .build();
        assertTrue(fixture.player.getAbilityManager().acceptAbilityInvoke(zero));
        assertEquals(ScriptGadgetState.Default, entity.getState());
        assertTrue(entity.isAlive());
        assertEquals(1, luaCalls);
        assertEquals(0, grants);
        assertTrue(fixture.player.getAbilityManager().acceptAbilityInvoke(zero));
        assertTrue(confirm(entity, material(id), ModifierAction.MODIFIER_ACTION_REMOVED));
        assertEquals(1, luaCalls);
        var chest = (GadgetChest) entity.getContent();
        assertTrue(chest.onInteract(fixture.player, GadgetInteractReq.getDefaultInstance()));
        assertFalse(chest.onInteract(fixture.player, GadgetInteractReq.getDefaultInstance()));
        assertEquals(1, grants);
    }

    @ParameterizedTest
    @ValueSource(ints = {70220003, 70220020})
    void native_zero_finishes_the_two_resource_wood_materials(int id) throws Exception {
        var entity = gadget(id, 0);
        String material = id == 70220003 ? "Bramble_Burning" : "Burning";
        assertTrue(confirm(entity, material, ModifierAction.MODIFIER_ACTION_ADDED));
        var zero =
                AbilityInvokeEntry.newBuilder()
                        .setEntityId(entity.getId())
                        .setArgumentType(
                                AbilityInvokeArgument
                                        .AbilityInvokeArgument_ABILITY_META_DURABILITY_IS_ZERO)
                        .setHead(
                                AbilityInvokeEntryHead.newBuilder()
                                        .setTargetId(entity.getId())
                                        .setInstancedAbilityId(1)
                                        .setInstancedModifierId(71))
                        .build();
        assertTrue(fixture.player.getAbilityManager().acceptAbilityInvoke(zero));
        assertNull(fixture.scene.getEntities().get(entity.getId()));
        assertTrue(entity.isDead());
        assertSame(fixture.entity, fixture.scene.getEntities().get(fixture.entity.getId()));
        assertTrue(fixture.player.getAbilityManager().acceptAbilityInvoke(zero));
        assertEquals(0, grants);
    }

    @ParameterizedTest
    @ValueSource(ints = {70210001, 70210013, 70210014, 70210063})
    void a_lethal_hit_cannot_destroy_a_chest_or_grant_its_rewards(int id) throws Exception {
        var chest = gadget(id, id == 70210001 ? 0 : seal(id));
        float hp = chest.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
        chest.damage(1_000_000, 0, ElementType.Fire);
        assertTrue(chest.isAlive());
        assertSame(chest, fixture.scene.getEntities().get(chest.getId()));
        assertEquals(hp, chest.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertEquals(0, grants);
        assertEquals(0, luaCalls);
    }

    @ParameterizedTest
    @ValueSource(ints = {70210013, 70210014, 70210063})
    void sealed_chests_do_not_grant_rewards_before_material_completion(int id) throws Exception {
        var entity = gadget(id, seal(id));
        assertFalse(
                ((GadgetChest) entity.getContent())
                        .onInteract(fixture.player, GadgetInteractReq.getDefaultInstance()));
        assertEquals(0, grants);
        assertEquals(seal(id), entity.getState());
    }

    @ParameterizedTest
    @ValueSource(ints = {70210013, 70210014, 70210063})
    void material_completion_runs_original_lua_then_allows_one_reward(int id) throws Exception {
        var entity = gadget(id, seal(id));
        assertTrue(confirm(entity, material(id), ModifierAction.MODIFIER_ACTION_ADDED));
        var ability = entity.getInstancedAbilities().get(0);
        assertEquals(
                30f, ability.getData().modifiers.get(material(id)).elementDurability.get(ability));
        var durability =
                emu.grasscutter.net.proto.AbilityMetaModifierDurabilityChangeOuterClass
                        .AbilityMetaModifierDurabilityChange.newBuilder()
                        .setRemainingDurability(0)
                        .build();
        assertTrue(
                fixture.player
                        .getAbilityManager()
                        .acceptAbilityInvoke(
                                AbilityInvokeEntry.newBuilder()
                                        .setEntityId(entity.getId())
                                        .setArgumentType(
                                                AbilityInvokeArgument
                                                        .AbilityInvokeArgument_ABILITY_META_MODIFIER_DURABILITY_CHANGE)
                                        .setHead(
                                                AbilityInvokeEntryHead.newBuilder()
                                                        .setInstancedAbilityId(1)
                                                        .setInstancedModifierId(71))
                                        .setAbilityData(durability.toByteString())
                                        .build()));
        assertEquals(
                0,
                entity.getAbilityDurability()
                        .snapshot()
                        .modifiers()
                        .get(0)
                        .update()
                        .getRemainingDurability());
        assertEquals(seal(id), entity.getState());
        assertTrue(confirm(entity, material(id), ModifierAction.MODIFIER_ACTION_REMOVED));
        assertEquals(ScriptGadgetState.Default, entity.getState());
        assertEquals(1, luaCalls);
        assertTrue(entity.getAbilityDurability().snapshot().modifiers().isEmpty());
        assertTrue(entity.isAlive());
        assertTrue(confirm(entity, material(id), ModifierAction.MODIFIER_ACTION_REMOVED));
        assertEquals(1, luaCalls);
        var chest = (GadgetChest) entity.getContent();
        assertTrue(chest.onInteract(fixture.player, GadgetInteractReq.getDefaultInstance()));
        assertFalse(chest.onInteract(fixture.player, GadgetInteractReq.getDefaultInstance()));
        assertEquals(1, grants);
    }

    @ParameterizedTest
    @ValueSource(ints = {70220003, 70220020})
    void burning_material_completion_removes_only_its_own_wall(int id) throws Exception {
        var entity = gadget(id, 0);
        String material = id == 70220003 ? "Bramble_Burning" : "Burning";
        assertTrue(confirm(entity, material, ModifierAction.MODIFIER_ACTION_ADDED));
        assertTrue(confirm(entity, material, ModifierAction.MODIFIER_ACTION_REMOVED));
        assertNull(fixture.scene.getEntities().get(entity.getId()));
        assertTrue(entity.isDead());
        assertSame(fixture.entity, fixture.scene.getEntities().get(fixture.entity.getId()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void ability_hp_loss_cannot_destroy_a_chest_even_when_bypassing_modifier_hp_locks(
            boolean respectLockHp) throws Exception {
        var chest = gadget(70210013, 104);
        float hp = chest.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
        chest.loseHpByAbility(1_000_000, respectLockHp);
        assertTrue(chest.isAlive());
        assertEquals(hp, chest.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
    }

    @Test
    void an_already_unsealed_bramble_prefab_remains_openable() throws Exception {
        var entity = gadget(70210013, 0);
        assertTrue(
                ((GadgetChest) entity.getContent())
                        .onInteract(fixture.player, GadgetInteractReq.getDefaultInstance()));
        assertEquals(1, grants);
        assertEquals(0, luaCalls);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void expired_materials_cannot_run_a_controller_after_their_owner_changes(int invalid)
            throws Exception {
        var entity = gadget(70210013, 104);
        assertTrue(confirm(entity, "GrassMaterial", ModifierAction.MODIFIER_ACTION_ADDED));
        switch (invalid) {
            case 0 -> entity.invalidateAbilityLifecycle();
            case 1 -> entity.onDeath(0);
            case 2 -> fixture.player.setWorld(null);
            case 3 -> {
                var config = EntityGadget.class.getDeclaredField("configGadget");
                config.setAccessible(true);
                config.set(entity, null);
            }
        }
        assertTrue(confirm(entity, "GrassMaterial", ModifierAction.MODIFIER_ACTION_REMOVED));
        assertEquals(104, entity.getState());
        assertEquals(0, luaCalls);
        assertEquals(0, grants);
    }

    @Test
    void a_missing_or_replaced_entity_cannot_be_unsealed_by_an_old_confirmation() throws Exception {
        var old = gadget(70210013, 104);
        assertTrue(confirm(old, "GrassMaterial", ModifierAction.MODIFIER_ACTION_ADDED));
        var replacement = gadget(70210013, 104);
        fixture.scene.getEntities().remove(replacement.getId());
        replacement.setId(old.getId());
        fixture.scene.getEntities().put(old.getId(), replacement);
        assertTrue(confirm(old, "GrassMaterial", ModifierAction.MODIFIER_ACTION_REMOVED));
        assertEquals(104, old.getState());
        assertEquals(104, replacement.getState());
        assertEquals(0, luaCalls);
    }

    @Test
    void a_new_material_application_can_unseal_the_same_chest_after_a_script_reseals_it()
            throws Exception {
        var entity = gadget(70210013, 104);
        for (int expected = 1; expected <= 2; expected++) {
            entity.setState(104);
            assertTrue(confirm(entity, "GrassMaterial", ModifierAction.MODIFIER_ACTION_ADDED));
            assertTrue(confirm(entity, "GrassMaterial", ModifierAction.MODIFIER_ACTION_REMOVED));
            assertEquals(0, entity.getState());
            assertEquals(expected, luaCalls);
        }
    }

    @Test
    void a_later_native_lua_request_is_idempotent_for_an_already_unsealed_chest() throws Exception {
        var entity = gadget(70210014, 105);
        assertTrue(confirm(entity, "IceMaterial", ModifierAction.MODIFIER_ACTION_ADDED));
        assertTrue(confirm(entity, "IceMaterial", ModifierAction.MODIFIER_ACTION_REMOVED));
        int before = events.size();
        assertEquals(0, entity.onClientExecuteRequest(0, 0, 0));
        assertEquals(0, entity.getState());
        assertEquals(before, events.size());
    }

    @Test
    void one_chests_modifier_cannot_unseal_another_owner() throws Exception {
        var source = gadget(70210013, 104);
        var target = gadget(70210013, 104);
        assertTrue(confirm(target, source, "GrassMaterial", ModifierAction.MODIFIER_ACTION_ADDED));
        assertSame(source, target.getInstancedModifiers().get(71).getAbility().getOwner());
        assertTrue(
                confirm(target, source, "GrassMaterial", ModifierAction.MODIFIER_ACTION_REMOVED));
        assertEquals(104, source.getState());
        assertEquals(104, target.getState());
        assertEquals(0, luaCalls);
    }

    @Test
    void a_native_lua_request_before_the_removal_confirmation_does_not_repeat_the_state_change()
            throws Exception {
        var entity = gadget(70210063, 106);
        assertTrue(confirm(entity, "Chest_Rock", ModifierAction.MODIFIER_ACTION_ADDED));
        assertEquals(0, entity.onClientExecuteRequest(0, 0, 0));
        int before = events.size();
        assertTrue(confirm(entity, "Chest_Rock", ModifierAction.MODIFIER_ACTION_REMOVED));
        assertEquals(0, entity.getState());
        assertEquals(1, luaCalls);
        assertEquals(before, events.size());
    }

    @Test
    void a_retired_root_cannot_complete_its_old_material() throws Exception {
        var entity = gadget(70210013, 104);
        assertTrue(confirm(entity, "GrassMaterial", ModifierAction.MODIFIER_ACTION_ADDED));
        var removal =
                notification(
                        entity, entity, "GrassMaterial", ModifierAction.MODIFIER_ACTION_REMOVED);
        entity.getInstancedAbilities().clear();
        assertTrue(fixture.player.getAbilityManager().acceptAbilityInvoke(removal));
        assertEquals(104, entity.getState());
        assertEquals(0, luaCalls);
    }
}
