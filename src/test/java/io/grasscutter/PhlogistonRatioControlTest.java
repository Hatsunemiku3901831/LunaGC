package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;

import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.actions.ActionChangePhlogiston;
import emu.grasscutter.game.entity.EntityVehicle;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.Test;

/** Resource ratio switches must not be interpreted as one absolute fuel point. */
class PhlogistonRatioControlTest extends PhlogistonMixinInvocationTest {
    boolean action(String config) {
        return new ActionChangePhlogiston()
                .execute(
                        env.ability,
                        JsonUtils.decode(
                                "{$type:'ChangePhlogiston'," + config + "}",
                                AbilityModifierAction.class),
                        ByteString.EMPTY,
                        owner);
    }

    @Test
    void resourceFullRefillReplenishesAnEmptyTeam() {
        env.fixture.player.setPhlogistonValue(0);
        assertTrue(action("value:1,costType:'Add',JDIMELLBMFO:true"));
        assertEquals(100, fuel());
    }

    @Test
    void fractionalAddUsesCapacity() {
        assertTrue(action("value:0.1,costType:'Add',JDIMELLBMFO:true"));
        assertEquals(60, fuel());
    }

    @Test
    void fractionalSetUsesCapacity() {
        assertTrue(action("value:0.25,costType:'Set',JDIMELLBMFO:true"));
        assertEquals(25, fuel());
    }

    @Test
    void fractionalActionCostUsesCurrentPool() {
        assertTrue(action("value:0.1,costType:'Cost',JDIMELLBMFO:true"));
        assertEquals(45, fuel());
    }

    @Test
    void negativeRatioCannotReverseCost() {
        assertFalse(action("value:-0.1,costType:'Cost',JDIMELLBMFO:true"));
        assertEquals(50, fuel());
    }

    @Test
    void ratioAboveOneRemainsBounded() {
        assertTrue(action("value:2,costType:'Set',JDIMELLBMFO:true"));
        assertEquals(100, fuel());
    }

    @Test
    void explicitFalseWinsOverRawTrue() {
        assertTrue(action("value:1,costType:'Add',useRatio:false,JDIMELLBMFO:true"));
        assertEquals(51, fuel());
    }

    @Test
    void canonicalRatioWithoutRawFlagWorks() {
        assertTrue(action("value:0.1,costType:'Add',useRatio:true"));
        assertEquals(60, fuel());
    }

    @Test
    void zeroRatioStaysZero() {
        assertTrue(action("value:0,costType:'Add',JDIMELLBMFO:true"));
        assertEquals(50, fuel());
    }

    @Test
    void fractionalVehicleAddUsesItsCapacity() {
        owner = env.environment.candle(NatsaurusPossessionTest.DRILL);
        ((EntityVehicle) owner).setCurPhlogiston(0);
        env.ability =
                new Ability(
                        JsonUtils.decode(
                                "{abilityName:'RatioVehicle',modifiers:{}}", AbilityData.class),
                        owner,
                        env.fixture.player);
        assertTrue(action("value:1,costType:'Add',JDIMELLBMFO:true"));
        assertEquals(50, ((EntityVehicle) owner).getCurPhlogiston());
        assertEquals(50, fuel());
    }

    @Test
    void periodicRatioCostUsesCurrentFuel() {
        install("{$type:'ChangePhlogistonMixin',speed:0.1,interval:1,JDIMELLBMFO:true}", true);
        tick();
        assertEquals(45, fuel());
        tick();
        assertEquals(40.5, fuel());
    }

    @Test
    void periodicRatioCanonicalFalseWins() {
        install(
                "{$type:'ChangePhlogistonMixin',speed:0.1,interval:1,useRatio:false,JDIMELLBMFO:true}",
                true);
        tick();
        assertEquals(49.9, fuel(), 0.00001);
    }

    @Test
    void periodicRatioAddUsesCapacity() {
        install(
                "{$type:'ChangePhlogistonMixin',speed:0.1,interval:1,costType:'Add',JDIMELLBMFO:true}",
                true);
        tick();
        assertEquals(60, fuel());
    }

    @Test
    void rawTargetAliasSelectsTeam() {
        owner = env.environment.candle(NatsaurusPossessionTest.DRILL);
        ((EntityVehicle) owner).setCurPhlogiston(20);
        install(
                "{$type:'ChangePhlogistonMixin',speed:0.1,interval:1,JDIMELLBMFO:true,BBIPIGDBGFJ:'Team'}",
                true);
        tick();
        assertEquals(45, fuel());
        assertEquals(20, ((EntityVehicle) owner).getCurPhlogiston());
    }

    @Test
    void canonicalTargetWinsOverRaw() {
        owner = env.environment.candle(NatsaurusPossessionTest.DRILL);
        ((EntityVehicle) owner).setCurPhlogiston(20);
        install(
                "{$type:'ChangePhlogistonMixin',speed:0.1,interval:1,JDIMELLBMFO:true,targetType:'Vehicle',BBIPIGDBGFJ:'Team'}",
                true);
        tick();
        assertEquals(18, ((EntityVehicle) owner).getCurPhlogiston());
        assertEquals(50, fuel());
    }

    @Test
    void periodicVehicleRatioReadsSelectedVehicle() {
        owner = env.environment.candle(NatsaurusPossessionTest.DRILL);
        ((EntityVehicle) owner).setCurPhlogiston(20);
        install(
                "{$type:'ChangePhlogistonMixin',speed:0.1,interval:1,JDIMELLBMFO:true,targetType:'Vehicle'}",
                true);
        tick();
        assertEquals(18, ((EntityVehicle) owner).getCurPhlogiston());
        assertEquals(50, fuel());
    }

    @Test
    void automaticRatioReadsTeamAfterEmptyVehicleFallback() {
        owner = env.environment.candle(NatsaurusPossessionTest.DRILL);
        ((EntityVehicle) owner).setCurPhlogiston(0);
        install("{$type:'ChangePhlogistonMixin',speed:0.1,interval:1,JDIMELLBMFO:true}", true);
        tick();
        assertEquals(45, fuel());
    }

    @Test
    void explicitTeamRatioReadsTeamWhileRiding() {
        owner = env.environment.candle(NatsaurusPossessionTest.DRILL);
        ((EntityVehicle) owner).setCurPhlogiston(20);
        install(
                "{$type:'ChangePhlogistonMixin',speed:0.1,interval:1,JDIMELLBMFO:true,targetType:'Team'}",
                true);
        tick();
        assertEquals(45, fuel());
        assertEquals(20, ((EntityVehicle) owner).getCurPhlogiston());
    }

    @Test
    void emptyExplicitVehicleRatioDoesNotSpendTeam() {
        owner = env.environment.candle(NatsaurusPossessionTest.DRILL);
        ((EntityVehicle) owner).setCurPhlogiston(0);
        install(
                "{$type:'ChangePhlogistonMixin',speed:0.1,interval:1,JDIMELLBMFO:true,targetType:'Vehicle'}",
                true);
        tick();
        assertEquals(50, fuel());
    }

    @Test
    void realInfiniteActivityRefillUsesRatioFlag() throws Exception {
        var rows =
                com.google.gson.JsonParser.parseString(
                                java.nio.file.Files.readString(
                                        java.nio.file.Path.of(
                                                "resources/BinOutput/Ability/Temp/GadgetAbilities/ConfigAbility_Scene_V5_8_SportsZone.json")))
                        .getAsJsonArray();
        for (var row : rows) {
            var d = row.getAsJsonObject().getAsJsonObject("Default");
            var mods = d.getAsJsonObject("modifiers");
            if (mods == null || !mods.has("InfinitePhlogiston")) continue;
            var a =
                    JsonUtils.decode(
                            mods.getAsJsonObject("InfinitePhlogiston")
                                    .getAsJsonArray("onAdded")
                                    .get(0),
                            AbilityModifierAction.class);
            env.fixture.player.setPhlogistonValue(0);
            assertTrue(
                    new ActionChangePhlogiston().execute(env.ability, a, ByteString.EMPTY, owner));
            assertEquals(100, fuel());
            return;
        }
        fail("real infinite fuel refill not found");
    }
}
