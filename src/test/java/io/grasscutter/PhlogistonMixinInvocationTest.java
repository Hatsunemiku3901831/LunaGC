package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;

import emu.grasscutter.data.binout.*;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

class PhlogistonMixinInvocationTest {
    PhlogistonMutationTest env;
    AbilityData data;
    Ability ability;
    GameEntity owner;
    AbilityMixinData mixin;

    @BeforeEach
    void setup() throws Exception {
        env = new PhlogistonMutationTest();
        env.setup();
        owner = env.caster;
    }

    @AfterEach
    void close() {
        env.close();
    }

    void install(String config, boolean parent) {
        data =
                JsonUtils.decode(
                        "{abilityName:'PhlogistonMixin_Test',"
                                + (parent ? "" : "abilityMixins:[" + config + "],")
                                + "modifiers:{Parent:{"
                                + (parent ? "modifierMixins:[" + config + "]" : "")
                                + "}}}",
                        AbilityData.class);
        ability = new Ability(data, owner, env.fixture.player);
        owner.getInstancedAbilities().add(ability);
        mixin = parent ? data.modifiers.get("Parent").modifierMixins[0] : data.abilityMixins[0];
        if (parent) {
            var controller =
                    new AbilityModifierController(ability, data, data.modifiers.get("Parent"));
            owner.getInstancedModifiers().put(101, controller);
            ability.registerModifier(owner, controller);
        }
    }

    void tick(int parent, ByteString payload, int type) {
        int local =
                data.localIdToMixin.entrySet().stream()
                        .filter(e -> e.getValue() == mixin)
                        .findFirst()
                        .orElseThrow()
                        .getKey();
        assertDoesNotThrow(
                () ->
                        env.fixture
                                .player
                                .getAbilityManager()
                                .onAbilityInvoke(
                                        AbilityInvokeEntryOuterClass.AbilityInvokeEntry.newBuilder()
                                                .setEntityId(owner.getId())
                                                .setArgumentTypeValue(type)
                                                .setHead(
                                                        AbilityInvokeEntryHeadOuterClass
                                                                .AbilityInvokeEntryHead.newBuilder()
                                                                .setInstancedAbilityId(
                                                                        owner.getInstancedAbilities()
                                                                                        .indexOf(
                                                                                                ability)
                                                                                + 1)
                                                                .setInstancedModifierId(parent)
                                                                .setLocalId(local))
                                                .setAbilityData(payload)
                                                .build()));
    }

    void tick() {
        tick(101, ByteString.EMPTY, 135);
    }

    float fuel() {
        return env.fixture.player.getPhlogistonValue();
    }

    @Test
    void defaultIntervalChargesSpeedTimesPointThree() {
        install("{$type:'ChangePhlogistonMixin',speed:10}", true);
        tick();
        assertEquals(47, fuel());
    }

    @Test
    void eachTickUsesConfiguredIntervalWithoutServerTimerDoubleCharge() {
        install("{$type:'ChangePhlogistonMixin',speed:10,LANIGKLMJHP:0.1}", true);
        tick();
        tick();
        assertEquals(48, fuel());
        owner.onAbilityTick();
        assertEquals(48, fuel());
    }

    @Test
    void canonicalZeroSpeedWinsOverRawSpeed() {
        install("{$type:'ChangePhlogistonMixin',speed:0,CIFADBDOAFI:100}", true);
        tick();
        assertEquals(50, fuel());
    }

    @Test
    void rawSpeedAndNewIntervalAliasWork() {
        install("{$type:'ChangePhlogistonMixin',CIFADBDOAFI:10,JLJLIKPJADO:0.2}", true);
        tick();
        assertEquals(48, fuel());
    }

    @Test
    void intervalCanonicalWinsOverRawAliases() {
        install(
                "{$type:'ChangePhlogistonMixin',speed:10,interval:0.1,LANIGKLMJHP:0.3,JLJLIKPJADO:0.2}",
                true);
        tick();
        assertEquals(49, fuel());
    }

    @Test
    void removedParentCannotFallbackToRootAbility() {
        install("{$type:'ChangePhlogistonMixin',speed:10}", true);
        ability.removeModifier(owner, "Parent");
        tick();
        assertEquals(50, fuel());
    }

    @Test
    void parentMixinCannotBeInvokedWithoutItsParentId() {
        install("{$type:'ChangePhlogistonMixin',speed:10}", true);
        tick(0, ByteString.EMPTY, 135);
        assertEquals(50, fuel());
    }

    @Test
    void rootMixinUsesItsRootAbilityScope() {
        install("{$type:'ChangePhlogistonMixin',speed:10}", false);
        tick(0, ByteString.EMPTY, 135);
        assertEquals(47, fuel());
    }

    @Test
    void wrongArgumentCannotSpendFuel() {
        install("{$type:'ChangePhlogistonMixin',speed:10}", true);
        tick(101, ByteString.EMPTY, 117);
        assertEquals(50, fuel());
    }

    @Test
    void nonemptyPayloadCannotInventAnAmount() {
        install("{$type:'ChangePhlogistonMixin',speed:10}", true);
        tick(101, ByteString.copyFrom(new byte[] {8, 1}), 135);
        assertEquals(50, fuel());
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "0", "[1,0,'DIV']"})
    void invalidIntervalsCannotSpend(String interval) {
        install("{$type:'ChangePhlogistonMixin',speed:10,interval:" + interval + "}", true);
        tick();
        assertEquals(50, fuel());
    }

    @Test
    void addModeRestoresByItsConfiguredRate() {
        install("{$type:'ChangePhlogistonMixin',speed:10,costType:'Add',LANIGKLMJHP:0.1}", true);
        tick();
        assertEquals(51, fuel());
    }

    @Test
    void explicitTeamTargetDoesNotSpendVehicleFuel() {
        owner = env.environment.candle(NatsaurusPossessionTest.DRILL);
        install("{$type:'ChangePhlogistonMixin',speed:10,targetType:'Team'}", true);
        tick();
        assertEquals(47, fuel());
        assertEquals(50, ((EntityVehicle) owner).getCurPhlogiston());
    }

    @Test
    void explicitVehicleTargetStaysOnEmptyVehicle() {
        owner = env.environment.candle(NatsaurusPossessionTest.DRILL);
        ((EntityVehicle) owner).setCurPhlogiston(0);
        install("{$type:'ChangePhlogistonMixin',speed:10,targetType:'Vehicle'}", true);
        tick();
        assertEquals(50, fuel());
        assertEquals(0, ((EntityVehicle) owner).getCurPhlogiston());
    }

    @Test
    void automaticTargetFallsBackToTeamWhenOwnPoolEmpty() {
        owner = env.environment.candle(NatsaurusPossessionTest.DRILL);
        ((EntityVehicle) owner).setCurPhlogiston(0);
        install("{$type:'ChangePhlogistonMixin',speed:10}", true);
        tick();
        assertEquals(47, fuel());
    }

    @Test
    void unpairedTimerSetModeCannotInventASetAmount() {
        install("{$type:'ChangePhlogistonMixin',speed:10,costType:'Set'}", true);
        tick();
        assertEquals(50, fuel());
    }

    @Test
    void unknownTargetDoesNotDefaultToParty() {
        install("{$type:'ChangePhlogistonMixin',speed:10,targetType:'Unknown'}", true);
        tick();
        assertEquals(50, fuel());
    }

    @Test
    void disabledMixinDoesNotSpend() {
        install("{$type:'ChangePhlogistonMixin',speed:10,enable:false}", true);
        tick();
        assertEquals(50, fuel());
    }

    @Test
    void malformedSpeedExpressionDoesNotSpend() {
        install("{$type:'ChangePhlogistonMixin',speed:[1,0,'DIV']}", true);
        tick();
        assertEquals(50, fuel());
    }

    @Test
    void failedResourcePredicateDoesNotSpend() {
        install(
                "{$type:'ChangePhlogistonMixin',speed:10,predicates:[{$type:'ByTargetGlobalValue',key:'notReady',value:1}]}",
                true);
        tick();
        assertEquals(50, fuel());
    }

    @Test
    void removingOwnerInvalidatesTick() {
        install("{$type:'ChangePhlogistonMixin',speed:10}", true);
        env.fixture.scene.removeEntity(owner);
        tick();
        assertEquals(50, fuel());
    }

    @Test
    void anotherPlayersSessionCannotDriveCurrentAbility() {
        install("{$type:'ChangePhlogistonMixin',speed:10}", true);
        var other = new AbilityTestFixture.TestPlayer();
        other.setUid(20001);
        other.setScene(env.fixture.scene);
        other.setWorld(env.fixture.scene.getWorld());
        int local =
                data.localIdToMixin.entrySet().stream()
                        .filter(e -> e.getValue() == mixin)
                        .findFirst()
                        .orElseThrow()
                        .getKey();
        assertDoesNotThrow(
                () ->
                        other.getAbilityManager()
                                .onAbilityInvoke(
                                        AbilityInvokeEntryOuterClass.AbilityInvokeEntry.newBuilder()
                                                .setEntityId(owner.getId())
                                                .setArgumentTypeValue(135)
                                                .setHead(
                                                        AbilityInvokeEntryHeadOuterClass
                                                                .AbilityInvokeEntryHead.newBuilder()
                                                                .setInstancedModifierId(101)
                                                                .setLocalId(local))
                                                .build()));
        assertEquals(50, fuel());
    }

    @Test
    void realKachinaRateUsesNativeDefaultInterval() throws Exception {
        var path =
                java.nio.file.Path.of(
                        "resources/BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_Kachina.json");
        org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.Files.exists(path));
        var rows =
                com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(path))
                        .getAsJsonArray();
        for (var row : rows) {
            var mods =
                    row.getAsJsonObject().getAsJsonObject("Default").getAsJsonObject("modifiers");
            if (mods == null || !mods.has("Avatar_Kachina_RidingMove_Cost_01_PhlogistonCost"))
                continue;
            install(
                    mods.getAsJsonObject("Avatar_Kachina_RidingMove_Cost_01_PhlogistonCost")
                            .getAsJsonArray("modifierMixins")
                            .get(0)
                            .toString(),
                    true);
            tick();
            assertEquals(50 - 7.7f * .3f, fuel(), .00001f);
            return;
        }
        fail("Kachina fuel resource not found");
    }
}
