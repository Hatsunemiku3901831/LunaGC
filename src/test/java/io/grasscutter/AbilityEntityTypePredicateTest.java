package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import com.google.protobuf.ByteString;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.data.excels.GadgetData;
import emu.grasscutter.data.excels.monster.MonsterData;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.ability.actions.ActionAddGlobalValue;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.*;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.proto.EvtCreateGadgetNotifyOuterClass.EvtCreateGadgetNotify;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

class AbilityEntityTypePredicateTest {
    private AbilityTestFixture fixture;
    private Ability ability;
    private EntityAvatar avatar;
    private final AtomicLong now = new AtomicLong();
    private final Map<Integer, GadgetData> previous = new HashMap<>();

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        fixture.player.setWorld(fixture.scene.getWorld());
        avatar = avatar(fixture.player, new Position());
        fixture.player.getTeamManager().getActiveTeam().add(avatar);
        ability =
                new Ability(
                        JsonUtils.decode("{\"abilityName\":\"EntityTypes\"}", AbilityData.class),
                        fixture.entity,
                        fixture.player);
    }

    @AfterEach
    void close() {
        previous.forEach(
                (id, data) -> {
                    if (data == null) GameData.getGadgetDataMap().remove(id);
                    else GameData.getGadgetDataMap().put(id.intValue(), data);
                });
        fixture.close();
    }

    private EntityAvatar avatar(Player player, Position position) {
        var entity =
                new EntityAvatar(
                        fixture.scene,
                        new Avatar() {
                            @Override
                            public Player getPlayer() {
                                return player;
                            }

                            @Override
                            public void save() {}
                        }) {
                    @Override
                    public void initAbilities() {}

                    @Override
                    public Position getPosition() {
                        return position;
                    }
                };
        entity.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 1000);
        entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 100);
        fixture.scene.getEntities().put(entity.getId(), entity);
        return entity;
    }

    private EntityClientGadget gadget(Player owner, int id, String type) {
        int config = 710000 + id;
        previous.put(
                config,
                GameData.getGadgetDataMap()
                        .put(
                                config,
                                JsonUtils.decode(
                                        "{\"id\":" + config + ",\"type\":\"" + type + "\"}",
                                        GadgetData.class)));
        var entity =
                new EntityClientGadget(
                        fixture.scene,
                        owner,
                        EvtCreateGadgetNotify.newBuilder()
                                .setEntityId(id)
                                .setConfigId(config)
                                .build());
        fixture.scene.getEntities().put(id, entity);
        return entity;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> predicate(String types, String fields) {
        return JsonUtils.decode(
                "{\"$type\":\"DEOFBICNFHF\",\"entityTypes\":" + types + fields + "}", Map.class);
    }

    private boolean evaluate(String types, String fields, GameEntity target) {
        return PredicateEvaluator.evaluate(
                predicate(types, fields), ability, fixture.entity, target, null);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Gadget",
                "Chest",
                "Field",
                "Bullet",
                "Gear",
                "Platform",
                "GatherObject",
                "MonsterEquip",
                "Equip"
            })
    void configuredGadgetKindsMatchTheirConcreteTypeWithoutTreatingEveryKindAsGadget(String type) {
        var entity = gadget(fixture.player, 991, type);
        assertTrue(evaluate("[\"" + type + "\"]", "", entity));
        assertFalse(evaluate("[\"Avatar\"]", "", entity));
        if (!"Gadget".equals(type)) assertFalse(evaluate("[\"Gadget\"]", "", entity));
    }

    @Test
    void roleFiltersSupportListsAndRejectRatherThanPassingEveryEntity() {
        assertTrue(evaluate("[\"Avatar\",\"Monster\"]", "", avatar));
        assertFalse(evaluate("[\"Monster\"]", "", avatar));
        assertFalse(evaluate("[\"Avatar\"]", ",\"reject\":true", avatar));
        assertTrue(evaluate("[\"Monster\"]", ",\"reject\":true", avatar));
        assertTrue(evaluate("[]", ",\"reject\":true", avatar));
        assertFalse(evaluate("[]", "", avatar));
        assertFalse(evaluate("[]", ",\"reject\":true", null));
    }

    @Test
    void environmentAnimalsAreNotRegularMonsterTargets() {
        var data =
                JsonUtils.decode(
                        "{\"id\":21001,\"type\":\"MONSTER_ENV_ANIMAL\",\"equips\":[]}",
                        MonsterData.class);
        var animal =
                new EntityMonster(fixture.scene, data, new Position(), new Position(), 1) {
                    @Override
                    public void initAbilities() {}

                    @Override
                    public void recalcStats() {}
                };
        assertTrue(evaluate("[\"EnvAnimal\"]", "", animal));
        assertFalse(evaluate("[\"Monster\"]", "", animal));
        assertTrue(evaluate("[\"Monster\",\"EnvAnimal\"]", "", animal));
    }

    @Test
    void nativeWritesRespectTheEntityCondition() {
        var action =
                JsonUtils.decode(
                        "{\"$type\":\"AddGlobalValue\",\"key\":\"hits\",\"value\":1,\"predicates\":[{\"$type\":\"DEOFBICNFHF\",\"entityTypes\":[\"Avatar\"]}]}",
                        AbilityModifierAction.class);
        var handler = new ActionAddGlobalValue();
        var gadget = gadget(fixture.player, 991, "Gadget");
        assertTrue(handler.execute(ability, action, ByteString.EMPTY, gadget));
        assertFalse(gadget.getGlobalAbilityValues().containsKey("hits"));
        assertTrue(handler.execute(ability, action, ByteString.EMPTY, avatar));
        assertEquals(1f, avatar.getGlobalAbilityValues().get("hits"));
    }

    @Test
    void anAuthorityRequirementDistinguishesTheExecutingPlayersAvatarAndPartner() {
        var partner = avatar(new AbilityTestFixture.TestPlayer(), new Position());
        assertTrue(evaluate("[\"Avatar\"]", ",\"isAuthority\":1", avatar));
        assertFalse(evaluate("[\"Avatar\"]", ",\"isAuthority\":0", avatar));
        assertFalse(evaluate("[\"Avatar\"]", ",\"isAuthority\":1", partner));
        assertTrue(evaluate("[\"Avatar\"]", ",\"isAuthority\":0", partner));
        assertTrue(evaluate("[\"Avatar\"]", ",\"isAuthority\":-1", partner));
        assertFalse(evaluate("[\"Avatar\"]", ",\"isAuthority\":0,\"reject\":true", partner));
    }

    @Test
    void gadgetAuthorityUsesItsAnnouncedOwnerAndUnknownAuthorityIsNotFalseAuthority() {
        var own = gadget(fixture.player, 991, "Gadget");
        var other = gadget(new AbilityTestFixture.TestPlayer(), 992, "Gadget");
        var missing = gadget(null, 993, "Gadget");
        assertTrue(evaluate("[\"Gadget\"]", ",\"isAuthority\":1", own));
        assertTrue(evaluate("[\"Gadget\"]", ",\"isAuthority\":0", other));
        assertFalse(evaluate("[\"Gadget\"]", ",\"isAuthority\":0", missing));
        assertFalse(evaluate("[\"Gadget\"]", ",\"isAuthority\":0,\"reject\":true", missing));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                ",\"useEventSource\":true",
                ",\"NHCLCBDKANP\":true",
                ",\"isAuthority\":2",
                ",\"isAuthority\":0.5",
                ",\"target\":\"SelfAttackTarget\"",
                ",\"reject\":\"true\"",
                ",\"value\":1"
            })
    void unresolvedContextsAndMalformedOptionsDoNotPassEvenWithReject(String fields) {
        assertFalse(evaluate("[\"Avatar\"]", fields, avatar));
        var predicate = predicate("[\"Avatar\"]", fields);
        assertFalse(AbilityEntityTypePredicate.supported(predicate));
    }

    @Test
    void legacyDefaultsAndExplicitCanonicalFieldsAreIndependentOfMemberOrder() {
        assertTrue(
                evaluate(
                        "[\"Avatar\"]",
                        ",\"MLHNOINPLBG\":-1,\"NHCLCBDKANP\":false,\"value\":-1",
                        avatar));
        assertTrue(
                evaluate(
                        "[\"Avatar\"]",
                        ",\"NHCLCBDKANP\":true,\"useEventSource\":false,\"MLHNOINPLBG\":0,\"isAuthority\":1",
                        avatar));
        assertTrue(
                evaluate(
                        "[\"Avatar\"]",
                        ",\"useEventSource\":false,\"NHCLCBDKANP\":true,\"isAuthority\":1,\"MLHNOINPLBG\":0",
                        avatar));
    }

    @Test
    void unknownAndMalformedTypesDoNotPassThroughAnInverseFilter() {
        assertFalse(evaluate("[\"UnknownEntityType\"]", ",\"reject\":true", avatar));
        assertFalse(evaluate("[\"Avatar\",17]", ",\"reject\":true", avatar));
        var unsupported = gadget(fixture.player, 991, "UnknownEntityType");
        assertFalse(evaluate("[\"Gadget\"]", ",\"reject\":true", unsupported));
        assertFalse(evaluate("[\"Avatar\"]", ",\"predicates\":[]", avatar));
    }

    @Test
    void targetSelectionUsesTheExistingCasterAndSelfContexts() {
        var casterAbility = new Ability(ability.getData(), avatar, fixture.player);
        var self = gadget(fixture.player, 991, "Gadget");
        assertTrue(
                PredicateEvaluator.evaluate(
                        predicate("[\"Avatar\"]", ",\"target\":\"Caster\""),
                        casterAbility,
                        avatar,
                        self,
                        null));
        assertFalse(
                PredicateEvaluator.evaluate(
                        predicate("[\"Avatar\"]", ",\"target\":\"Self\""),
                        casterAbility,
                        avatar,
                        self,
                        null));
    }

    @Test
    void aNoneGroupCanComposeWithATypeFilter() {
        var child = predicate("[\"Avatar\"]", "");
        var none = Map.<String, Object>of("$type", "GKGBIPDLMMG", "predicates", List.of(child));
        assertFalse(PredicateEvaluator.evaluate(none, ability, fixture.entity, avatar, null));
        assertTrue(
                PredicateEvaluator.evaluate(
                        none,
                        ability,
                        fixture.entity,
                        gadget(fixture.player, 991, "Gadget"),
                        null));
    }

    private AbilityData resource(String name, String abilityName) throws Exception {
        var entries =
                JsonParser.parseString(
                                Files.readString(
                                        Path.of(
                                                "resources/BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_"
                                                        + name
                                                        + ".json")))
                        .getAsJsonArray();
        for (var entry : entries) {
            var data = entry.getAsJsonObject().getAsJsonObject("Default");
            if (abilityName.equals(data.get("abilityName").getAsString()))
                return JsonUtils.decode(data.toString(), AbilityData.class);
        }
        throw new IllegalArgumentException("Missing resource ability " + abilityName);
    }

    @Test
    void realGorouHealingRunsTheUnmodifiedReadConvertAndNearbyRoleSequence() throws Exception {
        var data = resource("Gorou", "SkillObj_Gorou_BurstFieldLand_Halo");
        data.abilitySpecials.put("Constellation_4_HealRatio", 0.5f);
        var field =
                new AbilityTestFixture.TestEntity(fixture.scene) {
                    final AbilityPeriodicHealing timer = new AbilityPeriodicHealing(this, now::get);

                    @Override
                    public AbilityPeriodicHealing getAbilityPeriodicHealing() {
                        return timer;
                    }
                };
        field.setId(991);
        fixture.scene.getEntities().put(991, field);
        var nearby = avatar(fixture.player, new Position(4, 0, 0));
        var distant = avatar(fixture.player, new Position(8.1f, 0, 0));
        var above = avatar(fixture.player, new Position(1, 17, 0));
        var gadget = gadget(fixture.player, 992, "Gadget");
        avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_DEFENSE, 200);
        var ability = new Ability(data, field, fixture.player);
        var modifier = data.modifiers.get("Avatar_Gorou_ElementalBurst_Heal_Handler");
        ability.registerModifier(field, new AbilityModifierController(ability, data, modifier));
        assertTrue(
                field.getAbilityPeriodicHealing()
                        .ownsAction(ability, 0, modifier.onThinkInterval[0]));
        now.set(1_500_000_000L);
        field.onTick(0);
        assertEquals(200, avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertEquals(200, nearby.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertEquals(100, distant.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertEquals(100, above.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertFalse(gadget.getGlobalAbilityValues().containsKey("_ABILITY_Gorou_CurDefense"));
        assertEquals(200, ability.getAbilitySpecials().getFloat("_ABILITY_Gorou_CurDefense"));
        ability.removeModifier(field, "Avatar_Gorou_ElementalBurst_Heal_Handler");
        now.set(3_000_000_000L);
        field.onTick(0);
        assertEquals(200, avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
    }

    @Test
    void realBennettHealingTypeFilterExcludesGadgets() throws Exception {
        var data = resource("Bennett", "Avatar_Bennett_ElementalBurst_Gadget");
        var rows = data.modifiers.get("WarmField_Handler").onThinkInterval;
        @SuppressWarnings("unchecked")
        var predicate = (Map<String, Object>) rows[2].predicates.get(0);
        assertEquals("DEOFBICNFHF", predicate.get("$type"));
        assertTrue(
                PredicateEvaluator.evaluate(predicate, ability, fixture.entity, avatar, rows[2]));
        assertFalse(
                PredicateEvaluator.evaluate(
                        predicate,
                        ability,
                        fixture.entity,
                        gadget(fixture.player, 991, "Gadget"),
                        rows[2]));
    }

    @Test
    void aSequenceRequiringAnEventSourceRetainsNativeOwnership() {
        var data =
                JsonUtils.decode(
                        "{\"abilityName\":\"EventSource\",\"modifiers\":{\"Pulse\":{\"thinkInterval\":1,\"onThinkInterval\":[{\"$type\":\"AddGlobalValue\",\"key\":\"hits\",\"value\":1,\"predicates\":[{\"$type\":\"DEOFBICNFHF\",\"entityTypes\":[\"Avatar\"],\"useEventSource\":true}]}]}}}",
                        AbilityData.class);
        var ability = new Ability(data, avatar, fixture.player);
        ability.registerModifier(
                avatar, new AbilityModifierController(ability, data, data.modifiers.get("Pulse")));
        assertFalse(
                avatar.getAbilityPeriodicHealing()
                        .ownsAction(ability, 0, data.modifiers.get("Pulse").onThinkInterval[0]));
    }
}
