package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.data.excels.ShareCDData;
import emu.grasscutter.data.excels.avatar.*;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

class AbilitySkillSlotTest {
    static final int BASE = 72001, REPLACEMENT = 72002, OTHER = 72003, BURST = 72004;
    static final int SHARED = 72005, GROUP = 72010;
    AbilityTestFixture fixture;
    EntityAvatar entity;
    Ability evaluator;
    final AtomicLong now = new AtomicLong(1000);
    final List<Runnable> restores = new ArrayList<>();

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        fixture.player.setWorld(fixture.scene.getWorld());
        fixture.player.setSession(new GameSession(null));
        for (int id : List.of(BASE, REPLACEMENT, OTHER, BURST, SHARED))
            put(GameData.getAvatarSkillDataMap(), id, skill(id));
        put(
                GameData.getShareCDDataMap(),
                GROUP,
                JsonUtils.decode(
                        "{id:" + GROUP + ",maxChargeNum:1,cdSlots:[{coolDownTime:5}]}",
                        ShareCDData.class));
        var manager = fixture.player.getAbilityManager();
        var field = AbilityManager.class.getDeclaredField("skillCooldown");
        field.setAccessible(true);
        field.set(manager, new AbilitySkillCooldown(fixture.player, now::get));
        var depot =
                JsonUtils.decode(
                        "{id:72001,skills:["
                                + BASE
                                + ",0,0,0],energySkill:"
                                + BURST
                                + ",subSkills:["
                                + REPLACEMENT
                                + ","
                                + OTHER
                                + ","
                                + SHARED
                                + "],talents:[],inherentProudSkillOpens:[]}",
                        AvatarSkillDepotData.class);
        depot.onLoad();
        entity =
                new EntityAvatar(
                        fixture.scene,
                        new SkillTalentLevelTest.RecordingAvatar(fixture.player, 10000073, depot)) {
                    @Override
                    public void initAbilities() {}
                };
        fixture.scene.getEntities().put(entity.getId(), entity);
        fixture.player.getTeamManager().getActiveTeam().add(entity);
        evaluator =
                new Ability(
                        JsonUtils.decode("{abilityName:'Slot_Evaluator'}", AbilityData.class),
                        entity,
                        fixture.player);
        fixture.player.packets.clear();
    }

    static AvatarSkillData skill(int id) {
        return JsonUtils.decode(
                "{id:"
                        + id
                        + ",maxChargeNum:1,cdTime:5,costElemType:'Grass',shareCDID:"
                        + (id == SHARED ? GROUP : 0)
                        + "}",
                AvatarSkillData.class);
    }

    <K, V> void put(Map<K, V> map, K key, V value) {
        var old = map.put(key, value);
        restores.add(
                () -> {
                    if (old == null) map.remove(key);
                    else map.put(key, old);
                });
    }

    @AfterEach
    void close() {
        Collections.reverse(restores);
        restores.forEach(Runnable::run);
        fixture.close();
    }

    static String mixin(int id, String extra) {
        return "{$type:'SwitchSkillIDMixin',skillID:" + id + extra + "}";
    }

    Ability install(int id, String extra) {
        return install(mixin(id, extra));
    }

    Ability install(String mixin) {
        var data =
                JsonUtils.decode(
                        "{abilityName:'Slot_Source_"
                                + entity.getInstancedAbilities().size()
                                + "',abilityMixins:["
                                + mixin
                                + "]}",
                        AbilityData.class);
        assertNotNull(data);
        var source = new Ability(data, entity, fixture.player);
        entity.getInstancedAbilities().add(source);
        return source;
    }

    boolean ready(int slot) {
        @SuppressWarnings("unchecked")
        var predicate =
                (Map<String, Object>)
                        JsonUtils.decode(
                                "{$type:'BAEGOCNMDPJ',skillSlot:[" + slot + "]}", Map.class);
        return PredicateEvaluator.evaluate(predicate, evaluator, entity, entity, null);
    }

    void use(int id) {
        assertTrue(
                fixture.player
                        .getAbilityManager()
                        .getSkillCooldown()
                        .start(entity.getAvatar(), id, 5));
    }

    @Test
    void selectedTemporarySlotChecksTheReplacementCooldown() {
        install(REPLACEMENT, ",skillIndex:0,priority:'AvatarSpecial'");
        use(REPLACEMENT);
        assertFalse(ready(1));
    }

    @Test
    void anUnusedReplacementDoesNotInheritTheDefaultsCooldown() {
        use(BASE);
        install(REPLACEMENT, ",skillIndex:0,priority:'AvatarSpecial'");
        assertTrue(ready(1));
    }

    @Test
    void higherPriorityWinsRegardlessOfRegistrationOrder() {
        install(OTHER, ",skillIndex:0,priority:'Quest'");
        install(REPLACEMENT, ",skillIndex:0,priority:'AvatarSpecial'");
        use(OTHER);
        assertFalse(ready(1));
    }

    @Test
    void aReadyHigherPrioritySkillHidesTheLowerPriorityRecovery() {
        install(REPLACEMENT, ",skillIndex:0,priority:'Playmode'");
        install(OTHER, ",skillIndex:0,priority:'Quest'");
        use(REPLACEMENT);
        assertTrue(ready(1));
    }

    @Test
    void equalPriorityUsesTheLaterSource() {
        install(REPLACEMENT, ",skillIndex:0,priority:'AvatarSpecial'");
        install(OTHER, ",skillIndex:0,priority:'AvatarSpecial'");
        use(OTHER);
        assertFalse(ready(1));
    }

    @Test
    void removingTheWinnerRestoresTheStillActiveLowerPriority() {
        install(REPLACEMENT, ",skillIndex:0,priority:'AvatarSpecial'");
        var winner = install(OTHER, ",skillIndex:0,priority:'Quest'");
        use(REPLACEMENT);
        assertTrue(ready(1));
        winner.removeInstalledModifiers();
        entity.getInstancedAbilities().remove(winner);
        assertFalse(ready(1));
    }

    @Test
    void removingTheOnlyOverrideRestoresTheDefaultSlot() {
        var source = install(REPLACEMENT, ",skillIndex:0,priority:'AvatarSpecial'");
        use(REPLACEMENT);
        assertFalse(ready(1));
        entity.getInstancedAbilities().remove(source);
        assertTrue(ready(1));
    }

    @Test
    void defaultPriorityIsAvatarCommonAndExceedsBase() {
        install(REPLACEMENT, ",skillIndex:0,priority:'Base'");
        install(OTHER, ",skillIndex:0");
        use(OTHER);
        assertFalse(ready(1));
    }

    @Test
    void burstStaysInItsFifthSlotWhenOverridden() {
        install(REPLACEMENT, ",skillIndex:4,priority:'AvatarSpecial'");
        use(REPLACEMENT);
        assertFalse(ready(5));
        assertTrue(ready(1));
    }

    @Test
    void aReplacementCanFillAnEmptyOrdinarySlot() {
        install(REPLACEMENT, ",skillIndex:2,priority:'AvatarSpecial'");
        use(REPLACEMENT);
        assertFalse(ready(3));
    }

    @Test
    void namedSwitchIndicesAndPredicateSlotsUseDifferentBases() {
        install(REPLACEMENT, ",skillIndex:1,priority:'AvatarSpecial'");
        use(REPLACEMENT);
        assertTrue(ready(1));
        assertFalse(ready(2));
    }

    @Test
    void explicitSkillIndexTakesPrecedenceOverAnAddedDefaultAlias() {
        install(REPLACEMENT, ",skillIndex:0,actorIceSubHurtDelta:-1,priority:'AvatarSpecial'");
        use(REPLACEMENT);
        assertFalse(ready(1));
    }

    @Test
    void missingIndexDoesNotMoveTheSkillIntoTheFirstSlot() {
        install(REPLACEMENT, ",priority:'AvatarSpecial'");
        use(REPLACEMENT);
        assertTrue(ready(1));
    }

    @Test
    void aLegacyIndexCanBeReadWhenTheCanonicalFieldIsAbsent() {
        install(REPLACEMENT, ",actorIceSubHurtDelta:0,priority:'AvatarSpecial'");
        use(REPLACEMENT);
        assertFalse(ready(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "0.5", "-1", "6", "4294967296", "null", "[]"})
    void invalidIndicesDoNotInventASlot(String index) {
        install(REPLACEMENT, ",skillIndex:" + index + ",priority:'AvatarSpecial'");
        use(REPLACEMENT);
        assertTrue(ready(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"'Unknown'", "null", "true", "3", "4.5"})
    void invalidPrioritiesCannotOverrideTheDefault(String priority) {
        install(REPLACEMENT, ",skillIndex:0,priority:" + priority);
        use(BASE);
        assertFalse(ready(1));
    }

    @Test
    void exactNativeNumericPriorityIsAccepted() {
        install(REPLACEMENT, ",skillIndex:0,priority:101");
        use(REPLACEMENT);
        assertFalse(ready(1));
    }

    @Test
    void aLiveModifierCanProvideTheSlotWithoutARootMixin() {
        var data =
                JsonUtils.decode(
                        "{abilityName:'Modifier_Slot',modifiers:{Mode:{modifierMixins:["
                                + mixin(REPLACEMENT, ",skillIndex:0,priority:'AvatarSpecial'")
                                + "]}}}",
                        AbilityData.class);
        var source = new Ability(data, entity, fixture.player);
        source.registerModifier(
                entity, new AbilityModifierController(source, data, data.modifiers.get("Mode")));
        use(REPLACEMENT);
        assertFalse(ready(1));
        source.removeModifier(entity, "Mode");
        assertTrue(ready(1));
    }

    @Test
    void aDetachedSourceCannotKeepItsSlot() {
        install(REPLACEMENT, ",skillIndex:0,priority:'AvatarSpecial'");
        use(BASE);
        assertTrue(ready(1));
        entity.invalidateAbilityLifecycle();
        assertFalse(ready(1));
    }

    @Test
    void anUnknownReplacementDoesNotHideTheDefaultsRecovery() {
        install(79999, ",skillIndex:0,priority:'Quest'");
        use(BASE);
        assertFalse(ready(1));
    }

    @Test
    void aKnownButUnownedSkillCannotCreateAnInstance() {
        put(GameData.getAvatarSkillDataMap(), 79999, skill(79999));
        install(79999, ",skillIndex:0,priority:'Quest'");
        use(BASE);
        assertFalse(ready(1));
    }

    @Test
    void slotReadinessDoesNotConsumeChargesOrSendNotifications() {
        install(REPLACEMENT, ",skillIndex:0,priority:'AvatarSpecial'");
        use(REPLACEMENT);
        int sent = fixture.player.packets.size();
        for (int i = 0; i < 10; i++) assertFalse(ready(1));
        assertEquals(sent, fixture.player.packets.size());
        now.addAndGet(5000);
        assertTrue(ready(1));
    }

    static Stream<String> realSwitches() throws Exception {
        var result = new ArrayList<String>();
        try (var files = Files.walk(Path.of("resources/BinOutput/Ability"))) {
            for (var file : files.filter(path -> path.toString().endsWith(".json")).toList())
                collect(JsonParser.parseString(Files.readString(file)), result);
        }
        assertEquals(426, result.size(), "Keep the real resource inventory explicit");
        return result.stream();
    }

    @Test
    void aLaterRootWinsAnEqualPriorityModifierRegardlessOfCollectionOrder() {
        var data =
                JsonUtils.decode(
                        "{abilityName:'Earlier_Modifier',modifiers:{Mode:{modifierMixins:["
                                + mixin(REPLACEMENT, ",skillIndex:0,priority:'AvatarSpecial'")
                                + "]}}}",
                        AbilityData.class);
        var source = new Ability(data, entity, fixture.player);
        source.registerModifier(
                entity, new AbilityModifierController(source, data, data.modifiers.get("Mode")));
        install(OTHER, ",skillIndex:0,priority:'AvatarSpecial'");
        use(OTHER);
        assertFalse(ready(1));
    }

    @Test
    void theSelectedSkillUsesItsSharedRecoveryQueue() {
        install(SHARED, ",skillIndex:0,priority:'AvatarSpecial'");
        assertTrue(
                fixture.player.getAbilityManager().getSharedCooldown().startAvatarSkill(SHARED, 0));
        assertFalse(ready(1));
    }

    @Test
    void aFailingSupportedConditionKeepsTheDefault() {
        install(
                REPLACEMENT,
                ",skillIndex:0,priority:'AvatarSpecial',predicates:[{$type:'ByTargetGlobalValue',key:'allowed',value:1}]");
        use(BASE);
        assertFalse(ready(1));
        entity.getGlobalAbilityValues().put("allowed", 1f);
        assertTrue(ready(1));
    }

    @Test
    void anUnknownConditionCannotStealASlot() {
        install(
                REPLACEMENT,
                ",skillIndex:0,priority:'AvatarSpecial',predicates:[{$type:'Future_Condition'}]");
        use(BASE);
        assertFalse(ready(1));
    }

    static void collect(JsonElement value, List<String> result) {
        if (value.isJsonObject()) {
            var object = value.getAsJsonObject();
            if (object.has("$type")
                    && object.get("$type").isJsonPrimitive()
                    && "SwitchSkillIDMixin".equals(object.get("$type").getAsString()))
                result.add(object.toString());
            for (var child : object.entrySet()) collect(child.getValue(), result);
        } else if (value.isJsonArray())
            for (var child : value.getAsJsonArray()) collect(child, result);
    }

    @ParameterizedTest
    @MethodSource("realSwitches")
    void actualResourceMixinsResolveTheirConfiguredSlotAndPriority(String json) {
        var raw = JsonParser.parseString(json).getAsJsonObject();
        int id = raw.get("skillID").getAsInt();
        put(GameData.getAvatarSkillDataMap(), id, skill(id));
        assertTrue(entity.getAbilityAvatarSkillInfo().add(evaluator, id));
        var source = install(json);
        assertNotNull(source.getData().abilityMixins[0].type);
        assertEquals("SwitchSkillIDMixin", source.getData().abilityMixins[0].type.name());
        use(id);
        if (raw.has("skillIndex") && raw.get("skillIndex").getAsInt() >= 0) {
            int index = raw.get("skillIndex").getAsInt();
            assertFalse(ready(index + 1));
        } else assertTrue(ready(1));
    }
}
