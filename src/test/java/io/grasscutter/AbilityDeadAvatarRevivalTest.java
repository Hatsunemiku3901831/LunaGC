package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import com.google.protobuf.ByteString;

import emu.grasscutter.data.*;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.data.excels.avatar.AvatarSkillData;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.ability.actions.ActionReviveAvatar;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.world.*;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.*;
import java.util.*;

class AbilityDeadAvatarRevivalTest {
    AbilityTestFixture fixture;
    Ability ability;
    AvatarSkillData previousSkill;

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        fixture.player.setWorld(fixture.scene.getWorld());
        ability =
                new Ability(
                        JsonUtils.decode("{abilityName:'DeadAvatarRevival'}", AbilityData.class),
                        fixture.entity,
                        fixture.player);
        previousSkill = GameData.getAvatarSkillDataMap().get(10355);
    }

    @AfterEach
    void close() {
        if (previousSkill == null) GameData.getAvatarSkillDataMap().remove(10355);
        else GameData.getAvatarSkillDataMap().put(10355, previousSkill);
        fixture.close();
    }

    EntityAvatar member(AbilityTestFixture where, Player player, float hp, float x) {
        long guid = player.getTeamManager().getActiveTeam().size() + 3000;
        var avatar =
                new Avatar() {
                    @Override
                    public Player getPlayer() {
                        return player;
                    }

                    @Override
                    public long getGuid() {
                        return guid;
                    }

                    @Override
                    public void save() {}
                };
        var entity =
                new EntityAvatar(where.scene, avatar) {
                    final Position position = new Position(x, 0, 0);

                    @Override
                    public Position getPosition() {
                        return position;
                    }
                };
        entity.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 100);
        entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, hp);
        entity.checkIfDead();
        player.getTeamManager().getActiveTeam().add(entity);
        where.scene.getEntities().put(entity.getId(), entity);
        return entity;
    }

    EntityAvatar member(float hp) {
        return member(fixture, fixture.player, hp, 0);
    }

    AbilityModifierAction action(String text) {
        return JsonUtils.decode(text, AbilityModifierAction.class);
    }

    float hp(EntityAvatar entity) {
        return entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
    }

    void invoke(AbilityModifierAction config) {
        fixture.player
                .getAbilityManager()
                .executeActionNow(ability, config, ByteString.EMPTY, ability.getOwner());
    }

    AbilityModifierAction dead(String extra) {
        return action("{$type:'ReviveDeadAvatar',amountByTargetMaxHPRatio:0.5" + extra + "}");
    }

    @Test
    void deadAvatarActionIsRegisteredAndUsesThePartyWhenTargetIsAbsent() {
        var first = member(0);
        var second = member(0);
        var alive = member(80);
        invoke(dead(""));
        assertEquals(50, hp(first));
        assertEquals(50, hp(second));
        assertEquals(80, hp(alive));
    }

    @Test
    void actualMistTrialRevivalReadsAbilitySpecialsAndRestoresAllDeadMembers() throws Exception {
        var rows =
                JsonUtils.loadToList(
                        Path.of(
                                "resources/BinOutput/Ability/Temp/ConfigAbility_Activity_MistTrial.json"),
                        ResourceLoader.AbilityConfigData.class);
        var data =
                rows.stream()
                        .map(r -> r.Default)
                        .filter(
                                d ->
                                        "ActivityAbility_SuperTrial_RandomRoomRevive"
                                                .equals(d.abilityName))
                        .findFirst()
                        .orElseThrow();
        ability = new Ability(data, fixture.entity, fixture.player);
        var config =
                data.modifiers.get("UNIQUE_ActivityAbility_SuperTrial_RandomRoomRoomRevive")
                        .onAdded[0];
        var first = member(0);
        var second = member(0);
        invoke(config);
        assertEquals(100, hp(first));
        assertEquals(100, hp(second));
    }

    @Test
    void currentTeamTargetWorksForDeadAvatarActions() {
        var first = member(0);
        var second = member(0);
        invoke(dead(",target:'CurTeamAvatars'"));
        assertEquals(50, hp(first));
        assertEquals(50, hp(second));
    }

    @Test
    void legacyRevivalStillRestoresOnlyItsExplicitTarget() {
        var first = member(0);
        var second = member(0);
        new ActionReviveAvatar()
                .execute(
                        ability,
                        action("{$type:'ReviveAvatar',target:'Target',amountByTargetMaxHPRatio:1}"),
                        ByteString.EMPTY,
                        second);
        assertEquals(0, hp(first));
        assertEquals(100, hp(second));
    }

    @Test
    void anAlreadyResolvedPartyLeafDoesNotFanOutAgain() {
        var first = member(0);
        var second = member(0);
        new ActionReviveAvatar()
                .executeResolved(
                        ability,
                        action(
                                "{$type:'ReviveAvatar',target:'AllPlayerAvatars',amountByTargetMaxHPRatio:1}"),
                        ByteString.EMPTY,
                        first);
        assertEquals(100, hp(first));
        assertEquals(0, hp(second));
    }

    @Test
    void explicitTargetMustBelongToThePlayersActiveParty() {
        var target = member(0);
        fixture.player.getTeamManager().getActiveTeam().remove(target);
        new ActionReviveAvatar()
                .execute(
                        ability,
                        action("{$type:'ReviveAvatar',target:'Target',amountByTargetMaxHPRatio:1}"),
                        ByteString.EMPTY,
                        target);
        assertEquals(0, hp(target));
    }

    @Test
    void anotherPlayersAvatarCannotBeRevivedThroughAnExplicitLocalTarget() {
        var other = new AbilityTestFixture.TestPlayer();
        other.setScene(fixture.scene);
        var target = member(fixture, other, 0, 0);
        new ActionReviveAvatar()
                .execute(
                        ability,
                        action("{$type:'ReviveAvatar',target:'Target',amountByTargetMaxHPRatio:1}"),
                        ByteString.EMPTY,
                        target);
        assertEquals(0, hp(target));
    }

    @Test
    void aPartyMemberInAnotherSceneDoesNotReceiveRevival() throws Exception {
        try (var elsewhere = new AbilityTestFixture()) {
            var target = member(elsewhere, fixture.player, 0, 0);
            new ActionReviveAvatar()
                    .execute(
                            ability,
                            action(
                                    "{$type:'ReviveAvatar',target:'AllPlayerAvatars',amountByTargetMaxHPRatio:1}"),
                            ByteString.EMPTY,
                            fixture.entity);
            assertEquals(0, hp(target));
        }
    }

    @Test
    void removalOfTheAbilityOwnerPreventsLateRevival() {
        var target = member(0);
        fixture.entity.invalidateAbilityLifecycle();
        new ActionReviveAvatar()
                .execute(
                        ability,
                        action(
                                "{$type:'ReviveAvatar',target:'AllPlayerAvatars',amountByTargetMaxHPRatio:1}"),
                        ByteString.EMPTY,
                        fixture.entity);
        assertEquals(0, hp(target));
    }

    @Test
    void unknownTargetDoesNotFallbackToWhateverAvatarWasSupplied() {
        var target = member(0);
        new ActionReviveAvatar()
                .execute(
                        ability,
                        action(
                                "{$type:'ReviveAvatar',target:'FutureTarget',amountByTargetMaxHPRatio:1}"),
                        ByteString.EMPTY,
                        target);
        assertEquals(0, hp(target));
    }

    @Test
    void inactiveHiddenPartyMemberStaysDeadUntilItsLifetimeIsReactivated() {
        var target = member(0);
        fixture.scene.getEntities().remove(target.getId());
        target.invalidateAbilityLifecycle();
        new ActionReviveAvatar()
                .execute(
                        ability,
                        action(
                                "{$type:'ReviveAvatar',target:'AllPlayerAvatars',amountByTargetMaxHPRatio:1}"),
                        ByteString.EMPTY,
                        fixture.entity);
        assertEquals(0, hp(target));
    }

    @Test
    void hiddenButLivePartyMemberCanStillBeRevived() {
        var target = member(0);
        fixture.scene.getEntities().remove(target.getId());
        invoke(dead(""));
        assertEquals(50, hp(target));
    }

    @Test
    void deadAvatarRangeIncludesBoundaryAndExcludesDistantPartyMembers() {
        var near = member(fixture, fixture.player, 0, 5);
        var boundary = member(fixture, fixture.player, 0, 10);
        var far = member(fixture, fixture.player, 0, 11);
        invoke(dead(",range:10"));
        assertEquals(50, hp(near));
        assertEquals(50, hp(boundary));
        assertEquals(0, hp(far));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "'ten'", "{}", "[]"})
    void invalidRangeIsPreservedAndDoesNotRevive(String range) {
        var config = dead(",range:" + range);
        assertEquals(
                JsonParser.parseString(range),
                JsonUtils.toJson(config).getAsJsonObject().get("range"));
        var target = member(0);
        invoke(config);
        assertEquals(0, hp(target));
    }

    @Test
    void canonicalRatioWinsRegardlessOfAliasMemberOrder() {
        for (var text :
                List.of(
                        "{$type:'ReviveAvatar',target:'AllPlayerAvatars',amountByTargetMaxHPRatio:0.5,FPOCDLCHDPE:1}",
                        "{$type:'ReviveAvatar',target:'AllPlayerAvatars',FPOCDLCHDPE:1,amountByTargetMaxHPRatio:0.5}")) {
            var target = member(0);
            new ActionReviveAvatar()
                    .execute(ability, action(text), ByteString.EMPTY, fixture.entity);
            assertEquals(50, hp(target));
        }
    }

    @Test
    void flatRevivalHealthIsAddedToTheConfiguredMaximumHealthRatio() {
        var target = member(0);
        new ActionReviveAvatar()
                .execute(
                        ability,
                        action(
                                "{$type:'ReviveAvatar',target:'AllPlayerAvatars',amount:10,amountByTargetMaxHPRatio:0.25}"),
                        ByteString.EMPTY,
                        fixture.entity);
        assertEquals(35, hp(target));
    }

    @Test
    void missingOrUnknownRatioIsNotSubstitutedWithHalfHealth() {
        var target = member(0);
        invoke(
                action(
                        "{$type:'ReviveDeadAvatar',target:'AllPlayerAvatars',amountByTargetMaxHPRatio:'MissingParameter'}"));
        assertEquals(0, hp(target));
    }

    @Test
    void conditionsCanPreventADeadAvatarRevivalWithoutNotifications() {
        var target = member(0);
        fixture.player.packets.clear();
        fixture.scene.packets.clear();
        invoke(
                dead(
                        ",predicates:[{$type:'ByTargetGlobalValue',key:'ready',value:1,compareType:'Equal'}]"));
        assertEquals(0, hp(target));
        assertTrue(fixture.player.packets.isEmpty());
        assertTrue(fixture.scene.packets.isEmpty());
    }

    @Test
    void callbackTreeAcceptsKnownDeadAvatarRevivalAndRejectsUnknownRecipient() throws Exception {
        var type = Class.forName("emu.grasscutter.game.ability.AbilityActionSequence");
        var method =
                type.getDeclaredMethod(
                        "callbacks", AbilityData.class, AbilityModifierAction[].class);
        method.setAccessible(true);
        assertNotNull(
                method.invoke(
                        null,
                        ability.getData(),
                        new AbilityModifierAction[] {dead(",target:'CurTeamAvatars'")}));
        assertNull(
                method.invoke(
                        null,
                        ability.getData(),
                        new AbilityModifierAction[] {dead(",target:'FutureTarget'")}));
    }

    @Test
    void realQiqiRevivalControlFlagSurvivesDecoding() throws Exception {
        var rows =
                JsonParser.parseString(
                                Files.readString(
                                        Path.of(
                                                "resources/BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_Qiqi.json")))
                        .getAsJsonArray();
        JsonObject config = null;
        for (var row : rows) {
            var value = row.getAsJsonObject().getAsJsonObject("Default");
            if ("Avatar_Qiqi_ElementalBurst".equals(value.get("abilityName").getAsString()))
                config = value.getAsJsonArray("onAbilityStart").get(3).getAsJsonObject();
        }
        assertNotNull(config);
        var decoded = action(config.toString());
        assertEquals(config.get("range"), JsonUtils.toJson(decoded).getAsJsonObject().get("range"));
        assertEquals(
                config.get("KGHOMBBGBDI"),
                JsonUtils.toJson(decoded).getAsJsonObject().get("KGHOMBBGBDI"));
    }

    @Test
    void unknownRevivalConditionDoesNotBecomeTrue() {
        var target = member(0);
        new ActionReviveAvatar()
                .execute(
                        ability,
                        action(
                                "{$type:'ReviveAvatar',target:'AllPlayerAvatars',amountByTargetMaxHPRatio:1,predicates:[{$type:'FutureRevivalCondition'}]}"),
                        ByteString.EMPTY,
                        fixture.entity);
        assertEquals(0, hp(target));
    }

    @Test
    void queuedPartyRevivalCannotReadAnUnknownPartyPredicateAsASingleTarget() throws Exception {
        var type = Class.forName("emu.grasscutter.game.ability.AbilityActionSequence");
        var method =
                type.getDeclaredMethod(
                        "callbacks", AbilityData.class, AbilityModifierAction[].class);
        method.setAccessible(true);
        var config =
                dead(
                        ",predicates:[{$type:'ByTargetHPRatio',target:'CurTeamAvatars',HPRatio:0.5,logic:'Greater'}]");
        assertNull(method.invoke(null, ability.getData(), new AbilityModifierAction[] {config}));
    }

    @Test
    void periodicDeadAvatarRevivalRunsThroughTheActualControllerLifecycle() {
        var target = member(0);
        var now = new java.util.concurrent.atomic.AtomicLong();
        var carrier =
                new AbilityTestFixture.TestEntity(fixture.scene) {
                    final AbilityPeriodicHealing periodic =
                            new AbilityPeriodicHealing(this, now::get);

                    @Override
                    public AbilityPeriodicHealing getAbilityPeriodicHealing() {
                        return periodic;
                    }
                };
        carrier.setId(46);
        fixture.scene.getEntities().put(46, carrier);
        var data =
                JsonUtils.decode(
                        "{abilityName:'PeriodicRevival',modifiers:{Recovery:{thinkInterval:1,onThinkInterval:[{$type:'ReviveDeadAvatar',target:'CurTeamAvatars',amountByTargetMaxHPRatio:0.5}]}}}",
                        AbilityData.class);
        data.initialize();
        var source = new Ability(data, carrier, fixture.player);
        var controller =
                new AbilityModifierController(source, data, data.modifiers.get("Recovery"));
        carrier.getInstancedModifiers().put(3, controller);
        source.registerModifier(carrier, controller);
        assertTrue(
                carrier.getAbilityPeriodicHealing()
                        .ownsAction(source, 3, data.modifiers.get("Recovery").onThinkInterval[0]));
        now.set(1_000_000_000L);
        carrier.getAbilityPeriodicHealing().tick();
        assertEquals(50, hp(target));
    }

    @Test
    void deadAvatarWithNegativeStoredHealthStillReceivesRatioOnlyRevival() {
        var target = member(-1);
        new ActionReviveAvatar()
                .execute(
                        ability,
                        action(
                                "{$type:'ReviveAvatar',target:'AllPlayerAvatars',amountByTargetMaxHPRatio:0.5}"),
                        ByteString.EMPTY,
                        fixture.entity);
        assertEquals(50, hp(target));
    }
}
