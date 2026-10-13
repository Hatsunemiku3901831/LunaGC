package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import com.google.protobuf.ByteString;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.util.*;

class ModifierAttachmentMixinTest {
    AbilityTestFixture fixture;
    Ability ability;
    AbilityData data;
    AbilityData previous;
    static final String TYPE = "AttachActionToModifierMixin";

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
    }

    @AfterEach
    void close() {
        if (data != null) {
            if (previous == null) GameData.getAbilityDataMap().remove(data.abilityName);
            else GameData.getAbilityDataMap().put(data.abilityName, previous);
        }
        fixture.close();
    }

    void install(String callbacks) {
        data =
                JsonUtils.decode(
                        "{abilityName:'Attachment_Test',modifiers:{Focus:{modifierMixins:["
                                + callbacks
                                + "]}}}",
                        AbilityData.class);
        previous = GameData.getAbilityDataMap().put(data.abilityName, data);
        ability = new Ability(data, fixture.entity, fixture.player);
        fixture.entity.getInstancedAbilities().add(ability);
    }

    String mixin(String added, String removed) {
        return "{$type:'" + TYPE + "',onAdded:[" + added + "],onRemoved:[" + removed + "]}";
    }

    String mark(int value) {
        return "{$type:'AddGlobalValue',key:'mark',value:" + value + ",target:'Self'}";
    }

    float value(AbilityTestFixture.TestEntity target) {
        return target.getGlobalAbilityValues().getOrDefault("mark", 0f);
    }

    AbilityModifierController register(AbilityTestFixture.TestEntity target) {
        var controller = new AbilityModifierController(ability, data, data.modifiers.get("Focus"));
        ability.registerModifier(target, controller);
        return controller;
    }

    void client(int instance, boolean added) {
        assertDoesNotThrow(
                () ->
                        fixture.player
                                .getAbilityManager()
                                .onAbilityInvoke(
                                        AbilityInvokeEntryOuterClass.AbilityInvokeEntry.newBuilder()
                                                .setEntityId(42)
                                                .setArgumentType(
                                                        AbilityInvokeArgumentOuterClass
                                                                .AbilityInvokeArgument
                                                                .AbilityInvokeArgument_ABILITY_META_MODIFIER_CHANGE)
                                                .setHead(
                                                        AbilityInvokeEntryHeadOuterClass
                                                                .AbilityInvokeEntryHead.newBuilder()
                                                                .setInstancedAbilityId(1)
                                                                .setInstancedModifierId(instance))
                                                .setAbilityData(
                                                        AbilityMetaModifierChangeOuterClass
                                                                .AbilityMetaModifierChange
                                                                .newBuilder()
                                                                .setModifierLocalId(0)
                                                                .setAction(
                                                                        added
                                                                                ? ModifierActionOuterClass
                                                                                        .ModifierAction
                                                                                        .MODIFIER_ACTION_ADDED
                                                                                : ModifierActionOuterClass
                                                                                        .ModifierAction
                                                                                        .MODIFIER_ACTION_REMOVED)
                                                                .build()
                                                                .toByteString())
                                                .build()));
    }

    @Test
    void registrationAndRemovalRunTheMatchingCallbacksOnce() {
        install(mixin(mark(1), mark(-1)));
        var controller = register(fixture.entity);
        assertEquals(1, value(fixture.entity));
        ability.registerModifier(fixture.entity, controller);
        assertEquals(1, value(fixture.entity));
        assertTrue(ability.removeModifier(fixture.entity, "Focus"));
        assertEquals(0, value(fixture.entity));
        assertFalse(ability.removeModifier(fixture.entity, "Focus"));
        assertEquals(0, value(fixture.entity));
    }

    @Test
    void clientDuplicateConfirmationDoesNotRepeatAddedOrRemoved() {
        install(mixin(mark(1), mark(-1)));
        client(101, true);
        assertEquals(1, value(fixture.entity));
        client(101, true);
        assertEquals(1, value(fixture.entity));
        client(101, false);
        assertEquals(0, value(fixture.entity));
        client(101, false);
        assertEquals(0, value(fixture.entity));
    }

    @Test
    void separateClientInstancesHaveSeparateLifetimes() {
        install(mixin(mark(1), mark(-1)));
        client(101, true);
        client(102, true);
        assertEquals(2, value(fixture.entity));
        client(101, false);
        assertEquals(1, value(fixture.entity));
        client(102, false);
        assertEquals(0, value(fixture.entity));
    }

    @Test
    void separateTargetsDoNotShareCallbackMarkers() {
        install(mixin(mark(1), mark(-1)));
        var other = new AbilityTestFixture.TestEntity(fixture.scene);
        other.setId(43);
        fixture.scene.getEntities().put(43, other);
        register(fixture.entity);
        register(other);
        assertEquals(1, value(fixture.entity));
        assertEquals(1, value(other));
        ability.removeModifier(other, "Focus");
        assertEquals(1, value(fixture.entity));
        assertEquals(0, value(other));
    }

    @Test
    void supportedMixinRunsBesideUnsupportedSiblingWithoutPartialSiblingExecution() {
        install(
                mixin(mark(1), mark(-1))
                        + ","
                        + mixin(mark(100), "{$type:'Unknown_Render_Operation'}"));
        register(fixture.entity);
        assertEquals(1, value(fixture.entity));
        ability.removeModifier(fixture.entity, "Focus");
        assertEquals(0, value(fixture.entity));
    }

    @Test
    void unsupportedCleanupDoesNotLeaveAnUnpairedAddedEffect() {
        install(mixin(mark(100), "{$type:'Unknown_Render_Operation'}"));
        register(fixture.entity);
        assertEquals(0, value(fixture.entity));
    }

    @Test
    void addedActionsKeepResourceOrder() {
        install(
                mixin(
                        "{$type:'SetGlobalValue',key:'mark',value:2}," + mark(3),
                        "{$type:'SetGlobalValue',key:'mark',value:0}"));
        register(fixture.entity);
        assertEquals(5, value(fixture.entity));
        ability.removeModifier(fixture.entity, "Focus");
        assertEquals(0, value(fixture.entity));
    }

    @Test
    void differentMixinGroupsKeepDeclarationOrder() {
        install(
                mixin("{$type:'SetGlobalValue',key:'mark',value:2}", "")
                        + ","
                        + mixin(mark(3), ""));
        register(fixture.entity);
        assertEquals(5, value(fixture.entity));
    }

    @Test
    void queuedOldRemovalCannotChangeAReusedEntityLifetime() {
        install(mixin(mark(1), mark(-1)));
        var controller = register(fixture.entity);
        assertEquals(1, value(fixture.entity));
        fixture.entity.invalidateAbilityLifecycle();
        ability.unregisterModifier(fixture.entity, controller);
        assertEquals(1, value(fixture.entity));
    }

    @Test
    void explicitUnregistrationRunsCleanup() {
        install(mixin(mark(1), mark(-1)));
        var controller = register(fixture.entity);
        ability.unregisterModifier(fixture.entity, controller);
        assertEquals(0, value(fixture.entity));
        ability.unregisterModifier(fixture.entity, controller);
        assertEquals(0, value(fixture.entity));
    }

    @Test
    void unsupportedPredicateBlocksTheWholeAddedCleanupPair() {
        install(
                mixin(
                        "{$type:'AddGlobalValue',key:'mark',value:1,predicates:[{$type:'Unknown_Condition'}]}",
                        mark(-1)));
        register(fixture.entity);
        assertEquals(0, value(fixture.entity));
        ability.removeModifier(fixture.entity, "Focus");
        assertEquals(0, value(fixture.entity));
    }

    @Test
    void regularModifierCallbacksAndAttachmentCallbacksBothRun() {
        install(mixin(mark(1), mark(-1)));
        data.modifiers.get("Focus").onAdded =
                new AbilityModifier.AbilityModifierAction[] {
                    JsonUtils.decode(mark(5), AbilityModifier.AbilityModifierAction.class)
                };
        fixture.player
                .getAbilityManager()
                .executeActionNow(
                        ability,
                        JsonUtils.decode(
                                "{$type:'ApplyModifier',modifierName:'Focus'}",
                                AbilityModifier.AbilityModifierAction.class),
                        ByteString.EMPTY,
                        fixture.entity);
        assertEquals(6, value(fixture.entity));
        ability.removeModifier(fixture.entity, "Focus");
        assertEquals(5, value(fixture.entity));
    }

    @Test
    void serverAttachmentConfirmationDoesNotDuplicateItsEffects() {
        install(mixin(mark(1), mark(-1)));
        fixture.player
                .getAbilityManager()
                .executeActionNow(
                        ability,
                        JsonUtils.decode(
                                "{$type:'AttachModifier',modifierName:'Focus'}",
                                AbilityModifier.AbilityModifierAction.class),
                        ByteString.EMPTY,
                        fixture.entity);
        assertEquals(1, value(fixture.entity));
        client(101, true);
        assertEquals(1, value(fixture.entity));
        client(101, false);
        assertEquals(0, value(fixture.entity));
    }

    @Test
    void actualClorindeGunExistenceMarkUsesBothResourcePhases() throws Exception {
        var rows =
                JsonParser.parseString(
                                Files.readString(
                                        Path.of(
                                                "resources/BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_Clorinde.json")))
                        .getAsJsonArray();
        JsonObject node = null;
        for (var row : rows) {
            var modifiers =
                    row.getAsJsonObject().getAsJsonObject("Default").getAsJsonObject("modifiers");
            if (modifiers != null
                    && modifiers.has("Clorinde_ElementalArt_Skillobj_Ani_ShotGun_Start"))
                node =
                        modifiers
                                .getAsJsonObject("Clorinde_ElementalArt_Skillobj_Ani_ShotGun_Start")
                                .getAsJsonArray("modifierMixins")
                                .get(0)
                                .getAsJsonObject();
        }
        assertNotNull(node);
        install(node.toString());
        register(fixture.entity);
        assertEquals(
                1f,
                fixture.entity
                        .getGlobalAbilityValues()
                        .get("_ABILITY_Clorinde_Skillobj_Gun_ExistMark"));
        ability.removeModifier(fixture.entity, "Focus");
        assertEquals(
                0f,
                fixture.entity
                        .getGlobalAbilityValues()
                        .get("_ABILITY_Clorinde_Skillobj_Gun_ExistMark"));
    }

    @Test
    void clearingAnAbilityRunsAllAttachmentCleanups() {
        install(mixin(mark(1), mark(-1)));
        register(fixture.entity);
        assertEquals(1, value(fixture.entity));
        ability.removeInstalledModifiers();
        assertEquals(0, value(fixture.entity));
    }

    @Test
    void removingTheBearerDuringAddedStopsTheRemainingActions() {
        install(
                mixin(
                        mark(1) + ",{$type:'RemoveModifier',modifierName:'Focus'}," + mark(100),
                        mark(-1)));
        register(fixture.entity);
        assertEquals(0, value(fixture.entity));
        assertFalse(ability.removeModifier(fixture.entity, "Focus"));
    }

    @Test
    void replacingAnUnconfirmedNamedControllerCleansItsOldEffects() {
        install(mixin(mark(1), mark(-1)));
        var first = register(fixture.entity);
        register(fixture.entity);
        assertEquals(1, value(fixture.entity));
        first.runServerOnRemoved();
        assertEquals(1, value(fixture.entity));
        ability.removeModifier(fixture.entity, "Focus");
        assertEquals(0, value(fixture.entity));
    }

    @Test
    void modifierWithoutExecutableAttachmentsCanKeepExistingSharedControllerUsage() {
        install(mixin("{$type:'UnimplementedVisual'}", "{$type:'UnimplementedVisual'}"));
        var controller = register(fixture.entity);
        var other = new AbilityTestFixture.TestEntity(fixture.scene);
        other.setId(43);
        fixture.scene.getEntities().put(43, other);
        assertDoesNotThrow(() -> ability.registerModifier(other, controller));
        assertEquals(0, value(fixture.entity));
        assertEquals(0, value(other));
    }

    @Test
    void naturalExpiryRunsAttachmentCleanupWithoutAClientRemoval() throws Exception {
        var time = new java.util.concurrent.atomic.AtomicLong();
        var timers = new AbilityModifierDuration(fixture.entity, time::get);
        var field =
                emu.grasscutter.game.entity.GameEntity.class.getDeclaredField(
                        "abilityModifierDuration");
        field.setAccessible(true);
        field.set(fixture.entity, timers);
        install(mixin(mark(1), mark(-1)));
        data.modifiers.get("Focus").duration = new emu.grasscutter.data.common.DynamicFloat(1f);
        register(fixture.entity);
        assertEquals(1, value(fixture.entity));
        time.set(2_000_000_000L);
        timers.tick();
        assertEquals(0, value(fixture.entity));
        timers.tick();
        assertEquals(0, value(fixture.entity));
    }

    @Test
    void structuredEventConfigurationIsRetainedInsteadOfCoercedIntoAName() {
        var raw =
                JsonParser.parseString(
                        "{$type:'DoActionByEventMixin',onEvent:[{$type:'AddGlobalValue',key:'mark',value:1}]}");
        var mixin = JsonUtils.decode(raw, AbilityMixinData.class);
        assertNull(mixin.onEvent);
        assertEquals(raw.getAsJsonObject().get("onEvent"), mixin.structuredOnEvent);
        var json = JsonParser.parseString(JsonUtils.encode(mixin));
        assertEquals(raw.getAsJsonObject().get("onEvent"), json.getAsJsonObject().get("onEvent"));
    }
}
