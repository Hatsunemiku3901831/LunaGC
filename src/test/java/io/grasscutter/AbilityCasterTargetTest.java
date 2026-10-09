package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import com.google.protobuf.ByteString;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.proto.EvtCreateGadgetNotifyOuterClass.EvtCreateGadgetNotify;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.util.concurrent.atomic.AtomicLong;

class AbilityCasterTargetTest {
    private AbilityTestFixture fixture;
    private EntityAvatar caster, selected;
    private static final Path RESOURCES =
            Path.of("resources/BinOutput/Ability/Temp/AvatarAbilities");

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        caster = avatar(fixture.player);
        selected = avatar(fixture.player);
        fixture.player.getTeamManager().getActiveTeam().add(caster);
        fixture.player.getTeamManager().getActiveTeam().add(selected);
        fixture.player.getTeamManager().setCurrentCharacterIndex(1);
    }

    @AfterEach
    void close() {
        fixture.close();
    }

    private EntityAvatar avatar(Player player) {
        var avatar =
                new Avatar() {
                    @Override
                    public Player getPlayer() {
                        return player;
                    }

                    @Override
                    public void save() {}
                };
        var entity =
                new EntityAvatar(fixture.scene, avatar) {
                    @Override
                    public void initAbilities() {}
                };
        fixture.scene.getEntities().put(entity.getId(), entity);
        return entity;
    }

    private EntityClientGadget gadget(int ownerId, int id) {
        var entity =
                new EntityClientGadget(
                        fixture.scene,
                        fixture.player,
                        EvtCreateGadgetNotify.newBuilder()
                                .setEntityId(id)
                                .setOwnerEntityId(ownerId)
                                .build());
        fixture.scene.getEntities().put(id, entity);
        return entity;
    }

    private Ability ability(GameEntity owner) {
        return new Ability(
                JsonUtils.decode("{\"abilityName\":\"CasterTarget\"}", AbilityData.class),
                owner,
                fixture.player);
    }

    private AbilityModifierAction write() {
        return JsonUtils.decode(
                "{\"$type\":\"AddGlobalValue\",\"key\":\"GV_count\",\"value\":1,\"target\":\"CasterOriginOwner\"}",
                AbilityModifierAction.class);
    }

    private void callback(Ability ability, AbilityModifierAction action, GameEntity bearer) {
        assertTrue(
                ability.getManager()
                        .executeCallbackActionsNow(
                                ability,
                                new AbilityModifierAction[] {action},
                                ByteString.EMPTY,
                                bearer,
                                selected));
    }

    private float value(GameEntity entity, String key) {
        return entity.getGlobalAbilityValues().getOrDefault(key, 0f);
    }

    private JsonObject resource(String avatar, int index) throws Exception {
        return JsonParser.parseString(
                        Files.readString(
                                RESOURCES.resolve("ConfigAbility_Avatar_" + avatar + ".json")))
                .getAsJsonArray()
                .get(index)
                .getAsJsonObject()
                .getAsJsonObject("Default");
    }

    private AbilityModifierAction decode(JsonElement row) {
        return JsonUtils.decode(row.toString(), AbilityModifierAction.class);
    }

    @Test
    void anAvatarCallbackUsesItsCasterWhileAnotherMemberIsSelected() {
        callback(ability(caster), write(), selected);
        assertEquals(1, value(caster, "GV_count"));
        assertEquals(0, value(selected, "GV_count"));
    }

    @Test
    void aSummonCallbackDoesNotWriteToTheSummonOrSelectedAvatar() {
        var summon = gadget(caster.getId(), 991);
        callback(ability(summon), write(), summon);
        assertEquals(1, value(caster, "GV_count"));
        assertEquals(0, value(summon, "GV_count"));
        assertEquals(0, value(selected, "GV_count"));
    }

    @Test
    void aNestedSummonKeepsTheLaunchCasterAfterSwitchingCharacters() {
        var parent = gadget(caster.getId(), 991);
        var child = gadget(parent.getId(), 992);
        var ability = ability(child);
        fixture.player.getTeamManager().setCurrentCharacterIndex(0);
        callback(ability, write(), child);
        fixture.player.getTeamManager().setCurrentCharacterIndex(1);
        callback(ability, write(), child);
        assertEquals(2, value(caster, "GV_count"));
        assertEquals(0, value(selected, "GV_count"));
    }

    @Test
    void anotherPlayersCasterDoesNotUseTheExecutingPlayersCurrentAvatar() {
        var remote = avatar(new AbilityTestFixture.TestPlayer());
        callback(ability(gadget(remote.getId(), 991)), write(), selected);
        assertEquals(1, value(remote, "GV_count"));
        assertEquals(0, value(caster, "GV_count"));
        assertEquals(0, value(selected, "GV_count"));
    }

    @Test
    void aMissingOwnerDoesNotFallBackToTheSelectedAvatar() {
        var summon = gadget(9999, 991);
        callback(ability(summon), write(), summon);
        assertEquals(0, value(selected, "GV_count"));
        assertEquals(0, value(summon, "GV_count"));
    }

    @Test
    void aCycleDoesNotFallBackToTheSelectedAvatar() {
        var first = gadget(992, 991);
        gadget(991, 992);
        callback(ability(first), write(), first);
        assertEquals(0, value(selected, "GV_count"));
        assertEquals(0, value(first, "GV_count"));
    }

    @Test
    void anInvalidatedCasterCannotReceiveAnOldSummonsCallback() {
        var summon = gadget(caster.getId(), 991);
        var ability = ability(summon);
        caster.invalidateAbilityLifecycle();
        callback(ability, write(), summon);
        assertEquals(0, value(caster, "GV_count"));
        assertEquals(0, value(selected, "GV_count"));
    }

    @Test
    void realFischlCrowFlagsAreSetAndClearedOnTheirOriginalCaster() throws Exception {
        var resource = resource("Fischl", 37);
        var summon = gadget(caster.getId(), 991);
        var ability = ability(summon);
        for (var row : resource.getAsJsonArray("onAdded")) {
            var obj = row.getAsJsonObject();
            if (obj.has("target") && "CasterOriginOwner".equals(obj.get("target").getAsString()))
                callback(ability, decode(row), summon);
        }
        assertEquals(1, value(caster, "_Fischl_Crow_Flag"));
        assertEquals(1, value(caster, "_Fischl_Crow_Flag_Hexenzirkel"));
        assertEquals(0, value(selected, "_Fischl_Crow_Flag"));
        for (var row : resource.getAsJsonArray("onKill")) callback(ability, decode(row), summon);
        assertEquals(0, value(caster, "_Fischl_Crow_Flag"));
        assertEquals(0, value(caster, "_Fischl_Crow_Flag_Hexenzirkel"));
    }

    @Test
    void realKaeyaBurstCounterUsesItsCasterAndResourceLimit() throws Exception {
        var action =
                decode(
                        resource("Kaeya", 4)
                                .getAsJsonObject("modifiers")
                                .getAsJsonObject("UNIQUE_HoldOnPlease_TeamHandler")
                                .getAsJsonArray("modifierMixins")
                                .get(0)
                                .getAsJsonObject()
                                .getAsJsonArray("onKill")
                                .get(0)
                                .getAsJsonObject()
                                .getAsJsonArray("successActions")
                                .get(0));
        var summon = gadget(caster.getId(), 991);
        var ability = ability(summon);
        for (int i = 0; i < 5; i++) callback(ability, action, summon);
        assertEquals(3, value(caster, "_ABILITY_Kaeya_FrozenTrap_HoldOnPlease"));
        assertEquals(0, value(selected, "_ABILITY_Kaeya_FrozenTrap_HoldOnPlease"));
    }

    @Test
    void realChioriFollowUpActionTriggersOnItsCaster() throws Exception {
        var action =
                decode(
                        resource("Chiori", 3)
                                .getAsJsonObject("modifiers")
                                .getAsJsonObject("Turret_CheckRockGadget")
                                .getAsJsonArray("onAdded")
                                .get(0)
                                .getAsJsonObject()
                                .getAsJsonArray("successActions")
                                .get(1));
        // Keep the real target/name; the child marks its recipient without spawning a full turret.
        var child =
                JsonUtils.decode(
                        "{\"abilityName\":\""
                                + action.abilityName
                                + "\",\"onAbilityStart\":[{\"$type\":\"AddGlobalValue\",\"key\":\"GV_triggered\",\"value\":1}]}",
                        AbilityData.class);
        var previous = GameData.getAbilityDataMap().put(action.abilityName, child);
        try {
            var summon = gadget(caster.getId(), 991);
            callback(ability(summon), action, summon);
            assertEquals(1, value(caster, "GV_triggered"));
            assertEquals(0, value(selected, "GV_triggered"));
            assertEquals(0, value(summon, "GV_triggered"));
        } finally {
            if (previous == null) GameData.getAbilityDataMap().remove(action.abilityName);
            else GameData.getAbilityDataMap().put(action.abilityName, previous);
        }
    }

    @Test
    void anInitialModifierAppliesToTheCasterAndCanBeRemoved() {
        caster.setFightProperty(FightProperty.FIGHT_PROP_BASE_ATTACK, 100);
        caster.setFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK, 100);
        selected.setFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK, 80);
        var summon = gadget(caster.getId(), 991);
        var data =
                JsonUtils.decode(
                        "{\"abilityName\":\"InitialCasterBuff\",\"onAdded\":[{\"$type\":\"ApplyModifier\",\"target\":\"CasterOriginOwner\",\"modifierName\":\"Buff\"}],\"modifiers\":{\"Buff\":{\"properties\":{\"Actor_AttackDelta\":10}}}}",
                        AbilityData.class);
        var ability = new Ability(data, summon, fixture.player);
        assertEquals(110, caster.getFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK));
        assertEquals(80, selected.getFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK));
        ability.removeInstalledModifiers();
        assertEquals(100, caster.getFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK));
    }

    @Test
    void aServerTimerClaimsCasterCallbacksAndStopsWhenRemoved() {
        var now = new AtomicLong();
        var bearer =
                new AbilityTestFixture.TestEntity(fixture.scene) {
                    final AbilityPeriodicHealing timer = new AbilityPeriodicHealing(this, now::get);

                    @Override
                    public AbilityPeriodicHealing getAbilityPeriodicHealing() {
                        return timer;
                    }
                };
        bearer.setId(991);
        fixture.scene.getEntities().put(991, bearer);
        var data =
                JsonUtils.decode(
                        "{\"abilityName\":\"TimedCaster\",\"modifiers\":{\"Pulse\":{\"thinkInterval\":1,\"onThinkInterval\":[{\"$type\":\"AddGlobalValue\",\"key\":\"GV_count\",\"value\":1,\"target\":\"CasterOriginOwner\"}]}}}",
                        AbilityData.class);
        var ability = new Ability(data, caster, fixture.player);
        var controller = new AbilityModifierController(ability, data, data.modifiers.get("Pulse"));
        ability.registerModifier(bearer, controller);
        now.set(1_000_000_000L);
        bearer.onTick(0);
        assertEquals(1, value(caster, "GV_count"));
        assertEquals(0, value(bearer, "GV_count"));
        ability.removeModifier(bearer, "Pulse");
        now.set(2_000_000_000L);
        bearer.onTick(0);
        assertEquals(1, value(caster, "GV_count"));
    }
}
