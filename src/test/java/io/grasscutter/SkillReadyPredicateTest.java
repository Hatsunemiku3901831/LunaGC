package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import com.google.protobuf.ByteString;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.excels.ShareCDData;
import emu.grasscutter.data.excels.avatar.*;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

class SkillReadyPredicateTest {
    static final int SINGLE = 71001, MULTI = 71002, SHARED = 71003, BURST = 71004, MODE = 71005;
    static final int GROUP = 71010;
    AbilityTestFixture fixture;
    EntityAvatar entity;
    Ability ability;
    final AtomicLong now = new AtomicLong(1000), real = new AtomicLong(1791504000000L);
    final List<Runnable> restore = new ArrayList<>();

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        fixture.player.setWorld(fixture.scene.getWorld());
        fixture.player.setSession(new GameSession(null));
        for (int id : List.of(SINGLE, MULTI, SHARED, BURST, MODE)) {
            var data =
                    JsonUtils.decode(
                            "{id:"
                                    + id
                                    + ",maxChargeNum:"
                                    + (id == MULTI ? 3 : 1)
                                    + ",shareCDID:"
                                    + (id == SHARED ? GROUP : 0)
                                    + ",cdTime:5,costElemType:'Grass'}",
                            AvatarSkillData.class);
            put(GameData.getAvatarSkillDataMap(), id, data);
        }
        put(
                GameData.getShareCDDataMap(),
                GROUP,
                JsonUtils.decode(
                        "{id:" + GROUP + ",maxChargeNum:2,cdSlots:[{coolDownTime:5}]}",
                        ShareCDData.class));
        var manager = fixture.player.getAbilityManager();
        setField(manager, "skillCooldown", new AbilitySkillCooldown(fixture.player, now::get));
        setField(
                manager,
                "sharedCooldown",
                new AbilitySharedCooldown(fixture.player, now::get, real::get));
        entity = member();
        ability =
                new Ability(
                        JsonUtils.decode("{abilityName:'Skill_Ready_Test'}", AbilityData.class),
                        entity,
                        fixture.player);
        fixture.player.packets.clear();
    }

    void setField(Object owner, String name, Object value) throws Exception {
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(owner, value);
    }

    <K, V> void put(Map<K, V> map, K key, V value) {
        var old = map.put(key, value);
        restore.add(
                () -> {
                    if (old == null) map.remove(key);
                    else map.put(key, old);
                });
    }

    EntityAvatar member() {
        var depot =
                JsonUtils.decode(
                        "{id:71001,skills:["
                                + SINGLE
                                + ","
                                + MULTI
                                + ",0],energySkill:"
                                + BURST
                                + ",attackModeSkill:"
                                + MODE
                                + ",subSkills:["
                                + SHARED
                                + "],talents:[],inherentProudSkillOpens:[]}",
                        AvatarSkillDepotData.class);
        depot.onLoad();
        var avatar = new SkillTalentLevelTest.RecordingAvatar(fixture.player, 10000073, depot);
        var result =
                new EntityAvatar(fixture.scene, avatar) {
                    @Override
                    public void initAbilities() {}
                };
        fixture.scene.getEntities().put(result.getId(), result);
        fixture.player.getTeamManager().getActiveTeam().add(result);
        return result;
    }

    @AfterEach
    void close() {
        Collections.reverse(restore);
        restore.forEach(Runnable::run);
        fixture.close();
    }

    boolean predicate(String fields, emu.grasscutter.game.entity.GameEntity target) {
        @SuppressWarnings("unchecked")
        var pred =
                (Map<String, Object>)
                        JsonUtils.decode("{$type:'BAEGOCNMDPJ'," + fields + "}", Map.class);
        return PredicateEvaluator.evaluate(pred, ability, entity, target, null);
    }

    boolean ready(int id) {
        return predicate("skillID:" + id, entity);
    }

    void use(EntityAvatar target, int id, double seconds) {
        assertTrue(
                fixture.player
                        .getAbilityManager()
                        .getSkillCooldown()
                        .start(target.getAvatar(), id, seconds));
    }

    void use(int id, double seconds) {
        use(entity, id, seconds);
    }

    void shared() {
        assertTrue(
                fixture.player.getAbilityManager().getSharedCooldown().startAvatarSkill(SHARED, 0));
    }

    @Test
    void ordinarySkillIsReadyBeforeItsFirstUse() {
        assertTrue(ready(SINGLE));
    }

    @Test
    void ordinarySkillIsBlockedDuringRecovery() {
        use(SINGLE, 5);
        assertFalse(ready(SINGLE));
    }

    @Test
    void ordinarySkillBecomesReadyAtTheExactEndpoint() {
        use(SINGLE, 5);
        now.addAndGet(4999);
        assertFalse(ready(SINGLE));
        now.incrementAndGet();
        assertTrue(ready(SINGLE));
    }

    @Test
    void oneRemainingChargeDoesNotMeanFullyRecovered() {
        use(MULTI, 5);
        assertFalse(ready(MULTI));
    }

    @Test
    void everyPendingOrdinaryChargeMustRecover() {
        for (int i = 0; i < 3; i++) use(MULTI, 5);
        now.addAndGet(10000);
        assertFalse(ready(MULTI));
        now.addAndGet(5000);
        assertTrue(ready(MULTI));
    }

    @Test
    void zeroDurationDoesNotCreateAPendingCharge() {
        use(SINGLE, 0);
        assertTrue(ready(SINGLE));
    }

    @Test
    void modifyingTheFirstRecoveryStillLeavesLaterChargesPending() {
        use(MULTI, 5);
        use(MULTI, 5);
        assertTrue(
                fixture.player
                        .getAbilityManager()
                        .getSkillCooldown()
                        .modifyRemaining(entity.getAvatar(), MULTI, -5, 0));
        assertFalse(ready(MULTI));
        now.addAndGet(5000);
        assertTrue(ready(MULTI));
    }

    @Test
    void ordinaryRecoveryIsIsolatedBetweenCharacters() {
        var other = member();
        use(SINGLE, 5);
        assertFalse(ready(SINGLE));
        assertTrue(predicate("skillID:" + SINGLE, other));
    }

    @Test
    void casterSelectionDoesNotFollowTheCurrentlySelectedCharacter() {
        var other = member();
        use(SINGLE, 5);
        assertFalse(predicate("skillID:" + SINGLE + ",target:'Caster'", other));
        assertTrue(predicate("skillID:" + SINGLE, other));
    }

    @Test
    void forceByCasterReadsTheCasterRecovery() {
        var other = member();
        use(SINGLE, 5);
        assertFalse(predicate("skillID:" + SINGLE + ",forceByCaster:true", other));
    }

    @Test
    void sharedRecoveryIsVisibleToOtherCharactersWithTheSameGroup() {
        var other = member();
        shared();
        assertFalse(ready(SHARED));
        assertFalse(predicate("skillID:" + SHARED, other));
    }

    @Test
    void partialSharedRecoveryIsNotFullRecovery() {
        shared();
        shared();
        now.addAndGet(5000);
        assertFalse(ready(SHARED));
        now.addAndGet(5000);
        assertTrue(ready(SHARED));
    }

    @Test
    void ordinaryAndSharedRecoveryRemainSeparate() {
        shared();
        assertTrue(ready(SINGLE));
        assertFalse(ready(SHARED));
    }

    @Test
    void readingThePredicateDoesNotConsumeChargesOrSendPackets() {
        use(SINGLE, 5);
        var cooldown = fixture.player.getAbilityManager().getSkillCooldown();
        var before = cooldown.state(entity.getAvatar(), SINGLE, 1);
        fixture.player.packets.clear();
        for (int i = 0; i < 3; i++) assertFalse(ready(SINGLE));
        assertEquals(before, cooldown.state(entity.getAvatar(), SINGLE, 1));
        assertTrue(fixture.player.packets.isEmpty());
    }

    @Test
    void pausedPlayerTimeDoesNotRecoverTheSkill() {
        use(SINGLE, 5);
        real.addAndGet(10000);
        assertFalse(ready(SINGLE));
    }

    @Test
    void sessionReplacementDiscardsOldOrdinaryRecovery() {
        use(SINGLE, 5);
        assertFalse(ready(SINGLE));
        fixture.player.setSession(new GameSession(null));
        assertTrue(ready(SINGLE));
    }

    @Test
    void invalidOrdinaryClockCannotPassAsReady() {
        now.set(-1);
        assertFalse(ready(SINGLE));
    }

    @Test
    void invalidSharedClockCannotPassAsReady() {
        now.set(-1);
        assertFalse(ready(SHARED));
    }

    @Test
    void unknownSkillInstanceIsIgnoredLikeTheNativeEvaluator() {
        assertTrue(ready(99999));
    }

    @Test
    void registeredButUnownedSkillHasNoInstanceToRecover() {
        put(
                GameData.getAvatarSkillDataMap(),
                99999,
                JsonUtils.decode("{id:99999,maxChargeNum:1,cdTime:5}", AvatarSkillData.class));
        assertTrue(ready(99999));
    }

    @Test
    void zeroIdAndAnEmptySlotSelectionPassOnAnAvatar() {
        assertTrue(predicate("skillID:0,skillSlot:[]", entity));
    }

    @Test
    void nullTargetHasNoSkillComponent() {
        assertFalse(predicate("skillID:" + SINGLE, null));
    }

    @Test
    void aGenericEntityHasNoAvatarSkillComponent() {
        assertFalse(predicate("skillID:" + SINGLE, fixture.entity));
    }

    @Test
    void malformedSkillIdsAreRejected() {
        for (String value : List.of("-1", "1.5", "4294967296", "'wrong'", "true", "null"))
            assertFalse(predicate("skillID:" + value, entity));
    }

    @Test
    void malformedSlotSelectionsAreRejected() {
        for (String value : List.of("null", "1", "{}", "[1.5]", "[-1]", "['wrong']", "[true]"))
            assertFalse(predicate("skillID:0,skillSlot:" + value, entity));
    }

    @Test
    void slotsAreOneBasedAndRetainGaps() {
        use(MULTI, 5);
        assertFalse(predicate("skillID:0,skillSlot:[2]", entity));
        assertTrue(predicate("skillID:0,skillSlot:[3]", entity));
        assertTrue(predicate("skillID:0,skillSlot:[0,999]", entity));
    }

    @Test
    void explicitIdAndSlotsBothParticipate() {
        use(MULTI, 5);
        assertFalse(predicate("skillID:" + SINGLE + ",skillSlot:[2]", entity));
        assertTrue(predicate("skillID:" + SINGLE + ",skillSlot:[1]", entity));
    }

    @Test
    void theFifthSlotSelectsTheEnergySkill() {
        use(BURST, 5);
        assertFalse(predicate("skillID:0,skillSlot:[5]", entity));
    }

    @Test
    void theSixthSlotSelectsTheAttackModeSkill() {
        use(MODE, 5);
        assertFalse(predicate("skillID:0,skillSlot:[6]", entity));
    }

    @Test
    void theFourthEmptySlotDoesNotShiftTheEnergySkill() {
        use(BURST, 5);
        assertTrue(predicate("skillID:0,skillSlot:[4]", entity));
    }

    @Test
    void invalidMaximumChargesAreRejected() {
        put(
                GameData.getAvatarSkillDataMap(),
                SINGLE,
                JsonUtils.decode("{id:" + SINGLE + ",maxChargeNum:0}", AvatarSkillData.class));
        assertFalse(ready(SINGLE));
    }

    @Test
    void anInvalidSharedGroupCannotBeReportedReady() {
        put(
                GameData.getShareCDDataMap(),
                GROUP,
                JsonUtils.decode(
                        "{id:" + GROUP + ",maxChargeNum:0,cdSlots:[{coolDownTime:5}]}",
                        ShareCDData.class));
        assertFalse(ready(SHARED));
    }

    @Test
    void eventCallbacksRespectSkillRecovery() {
        var data =
                JsonUtils.decode(
                        "{abilityName:'Skill_Ready_Event',abilityMixins:[{$type:'DoActionByEventMixin',predicates:[{$type:'BAEGOCNMDPJ',skillID:"
                                + SINGLE
                                + "}],actions:[{$type:'SetGlobalValue',key:'ready',value:1}]}]}",
                        AbilityData.class);
        var callback = new Ability(data, entity, fixture.player);
        use(SINGLE, 5);
        assertTrue(
                callback.getManager()
                        .executeMixinActionsNow(
                                callback, data.abilityMixins[0], ByteString.EMPTY, entity));
        assertFalse(entity.getGlobalAbilityValues().containsKey("ready"));
        now.addAndGet(5000);
        assertTrue(
                callback.getManager()
                        .executeMixinActionsNow(
                                callback, data.abilityMixins[0], ByteString.EMPTY, entity));
        assertEquals(1f, entity.getGlobalAbilityValues().get("ready"));
    }

    void collect(JsonElement value, List<JsonObject> result) {
        if (value.isJsonObject()) {
            var object = value.getAsJsonObject();
            if (object.has("$type") && "BAEGOCNMDPJ".equals(object.get("$type").getAsString()))
                result.add(object);
            for (var member : object.entrySet()) collect(member.getValue(), result);
        } else if (value.isJsonArray())
            for (var member : value.getAsJsonArray()) collect(member, result);
    }

    void actual(String name, Set<Integer> expected) throws Exception {
        var found = new ArrayList<JsonObject>();
        collect(
                JsonParser.parseString(
                        Files.readString(
                                Path.of(
                                        "/resources/BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_"
                                                + name
                                                + ".json"))),
                found);
        var ids = new HashSet<Integer>();
        var skills =
                JsonUtils.loadToList(
                        Path.of("/resources/ExcelBinOutput/AvatarSkillExcelConfigData.json"),
                        AvatarSkillData.class);
        for (var node : found) ids.add(node.get("skillID").getAsInt());
        assertEquals(expected, ids);
        for (int id : ids) {
            var data = skills.stream().filter(row -> row.getId() == id).findFirst().orElseThrow();
            assertEquals(0, data.getShareCDID());
            assertEquals(1, data.getMaxChargeNum());
            put(GameData.getAvatarSkillDataMap(), id, data);
            entity.getAvatar().getSkillExtraChargeMap().put(id, 1);
            assertTrue(ready(id));
            use(id, data.getCdTime());
            assertFalse(ready(id));
        }
        for (var node : found) {
            @SuppressWarnings("unchecked")
            var predicate = (Map<String, Object>) JsonUtils.decode(node, Map.class);
            assertFalse(PredicateEvaluator.evaluate(predicate, ability, entity, entity, null));
        }
        now.addAndGet(120000);
        for (int id : ids) assertTrue(ready(id));
    }

    @Test
    void actualHutaoSixthConstellationCooldownConditionIsEnforced() throws Exception {
        actual("Hutao", Set.of(10464));
    }

    @Test
    void actualShinobuSixthConstellationCooldownConditionsAreEnforced() throws Exception {
        actual("Shinobu", Set.of(10656, 10657));
    }
}
