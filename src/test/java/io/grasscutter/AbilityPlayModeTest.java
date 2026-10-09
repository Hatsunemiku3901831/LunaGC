package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;

import emu.grasscutter.data.binout.*;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.ability.AbilityPlayMode.Type;
import emu.grasscutter.game.ability.actions.ActionChangePlayMode;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.EntityTeam;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;

import java.util.*;

class AbilityPlayModeTest {
    private AbilityTestFixture fixture;
    private EntityAvatar entity;
    private Ability ability;
    private final ActionChangePlayMode handler = new ActionChangePlayMode();

    @BeforeEach
    void scene() throws Exception {
        fixture = new AbilityTestFixture();
        fixture.player.setWorld(fixture.scene.getWorld());
        fixture.player.getTeamManager().setEntity(new EntityTeam(fixture.player));
        entity = avatar(fixture, fixture.player);
        fixture.player.getTeamManager().getActiveTeam().add(entity);
        ability =
                new Ability(
                        JsonUtils.decode("{\"abilityName\":\"Modes\"}", AbilityData.class),
                        entity,
                        fixture.player);
        fixture.player.packets.clear();
    }

    @AfterEach
    void close() {
        fixture.close();
    }

    static EntityAvatar avatar(AbilityTestFixture fixture, Player player) {
        var depot =
                JsonUtils.decode(
                        "{\"id\":7301,\"skills\":[],\"talents\":[],\"inherentProudSkillOpens\":[]}",
                        AvatarSkillDepotData.class);
        depot.onLoad();
        var result =
                new EntityAvatar(
                        fixture.scene,
                        new SkillTalentLevelTest.RecordingAvatar(player, 10000073, depot)) {
                    @Override
                    public void initAbilities() {}
                };
        fixture.scene.getEntities().put(result.getId(), result);
        return result;
    }

    private boolean execute(String fields) {
        return handler.execute(ability, action(fields), ByteString.EMPTY, entity);
    }

    private static AbilityModifierAction action(String fields) {
        return JsonUtils.decode(
                "{\"$type\":\"ChangePlayMode\"" + fields + "}", AbilityModifierAction.class);
    }

    private Set<Type> modes() {
        return entity.getAbilityPlayMode().snapshot();
    }

    @Test
    void enterAndExitAreIndependentOfTheResourcesEnableFlag() {
        assertTrue(execute(",\"toPlayMode\":\"NyxState\",\"enable\":true"));
        assertEquals(Set.of(Type.NyxState), modes());
        assertTrue(execute(",\"FPFLACICBBN\":\"NyxState\",\"enable\":true"));
        assertTrue(modes().isEmpty());
        assertTrue(entity.getGlobalAbilityValues().isEmpty());
        assertTrue(fixture.player.packets.isEmpty());
    }

    @Test
    void aDestinationTakesPrecedenceAndAnUnspecifiedActionDoesNotClearOtherModes() {
        execute(",\"toPlayMode\":\"NyxState\"");
        execute(",\"fromPlayMode\":\"NyxState\",\"toPlayMode\":\"Avatar_Chasca\"");
        execute("");
        assertEquals(Set.of(Type.NyxState, Type.Avatar_Chasca), modes());
        execute(",\"fromPlayMode\":\"Avatar_Chasca\"");
        assertEquals(Set.of(Type.NyxState), modes());
    }

    @Test
    void duplicateEntryIsIdempotentAndOneExitRemovesTheMode() {
        execute(",\"toPlayMode\":\"Avatar_Ifa\"");
        execute(",\"toPlayMode\":\"Avatar_Ifa\"");
        execute(",\"fromPlayMode\":\"Avatar_Ifa\"");
        assertTrue(modes().isEmpty());
    }

    @Test
    void canonicalFieldsWinAndAmbiguousAliasesAreRejected() {
        assertTrue(execute(",\"toPlayMode\":\"NyxState\",\"JGNOHILKBNI\":\"Unknown\""));
        assertTrue(execute(",\"fromPlayMode\":\"NyxState\",\"FPFLACICBBN\":\"Unknown\""));
        assertFalse(execute(",\"FPFLACICBBN\":\"NyxState\",\"DLEICBJFEKN\":\"Avatar_Chasca\""));
        assertFalse(execute(",\"toPlayMode\":\"Unknown\""));
        assertFalse(execute(",\"fromPlayMode\":null"));
        assertFalse(execute(",\"toPlayMode\":0"));
        assertTrue(modes().isEmpty());
    }

    @Test
    void onlyTheOwnersCurrentAvatarReceivesALocalMode() {
        var other = avatar(fixture, fixture.player);
        assertFalse(
                handler.execute(
                        ability, action(",\"toPlayMode\":\"NyxState\""), ByteString.EMPTY, other));
        assertTrue(execute(",\"target\":\"Team\",\"toPlayMode\":\"NyxState\""));
        assertEquals(Set.of(Type.NyxState), modes());
        assertTrue(other.getAbilityPlayMode().snapshot().isEmpty());
        assertFalse(
                handler.executeResolved(
                        ability,
                        action(",\"target\":\"Unknown\",\"toPlayMode\":\"Avatar_Ifa\""),
                        ByteString.EMPTY,
                        entity));
    }

    @Test
    void foreignTargetsAndFailedConditionsDoNotEnterModes() {
        var foreignPlayer = new AbilityTestFixture.TestPlayer();
        foreignPlayer.setScene(fixture.scene);
        var foreign = avatar(fixture, foreignPlayer);
        assertFalse(
                handler.execute(
                        ability,
                        action(",\"toPlayMode\":\"NyxState\""),
                        ByteString.EMPTY,
                        foreign));
        assertTrue(
                AbilityGlobalValues.isCurrent(ability, entity),
                "owner="
                        + entity.getId()
                        + " version="
                        + entity.getAbilityLifecycleVersion()
                        + " registered="
                        + (fixture.scene.getEntities().get(entity.getId()) == entity));
        assertTrue(
                execute(
                        ",\"toPlayMode\":\"NyxState\",\"predicates\":[{\"$type\":\"ByTargetGlobalValue\",\"key\":\"SGV_UGCDungeon_ForbidSkill\",\"value\":1,\"target\":\"Target\"}]"));
        assertTrue(modes().isEmpty());
    }

    @Test
    void removalAndReAddCannotRetainAPreviousLifetimeMode() {
        execute(",\"toPlayMode\":\"NyxState_Xilonen\"");
        entity.invalidateAbilityLifecycle();
        assertFalse(execute(",\"toPlayMode\":\"NyxState\""));
        assertTrue(modes().isEmpty());
        entity.activateAbilityLifecycle();
        assertTrue(modes().isEmpty());
        assertTrue(execute(",\"toPlayMode\":\"NyxState_Iansan\""));
        assertEquals(Set.of(Type.NyxState_Iansan), modes());
    }

    @Test
    void all227RealActionsDecodeTheirNativeSourceAndDestinationFields() throws Exception {
        AbilityModeResourceCorpus.load();
        assertEquals(227, AbilityModeResourceCorpus.modes.size());
        for (var row : AbilityModeResourceCorpus.modes) {
            var source = row.data();
            var decoded = JsonUtils.decode(source.toString(), AbilityModifierAction.class);
            var from =
                    source.has("fromPlayMode")
                            ? source.get("fromPlayMode")
                            : source.has("FPFLACICBBN")
                                    ? source.get("FPFLACICBBN")
                                    : source.get("DLEICBJFEKN");
            var to =
                    source.has("toPlayMode") ? source.get("toPlayMode") : source.get("JGNOHILKBNI");
            assertEquals(
                    from == null ? Type.Normal : Type.valueOf(from.getAsString()),
                    decoded.fromPlayMode,
                    row.path());
            assertEquals(
                    to == null ? Type.Normal : Type.valueOf(to.getAsString()),
                    decoded.toPlayMode,
                    row.path());
            entity.invalidateAbilityLifecycle();
            entity.activateAbilityLifecycle();
            assertTrue(handler.execute(ability, decoded, ByteString.EMPTY, entity), row.path());
            @SuppressWarnings("unchecked")
            var predicates = (List<Map<String, Object>>) (List<?>) decoded.predicates;
            boolean applies = PredicateEvaluator.all(predicates, ability, entity, entity, decoded);
            if (applies && decoded.toPlayMode != Type.Normal)
                assertEquals(Set.of(decoded.toPlayMode), modes(), row.path());
            else assertTrue(modes().isEmpty(), row.path());
        }
    }
}
