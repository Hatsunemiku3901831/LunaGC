package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import com.google.protobuf.ByteString;

import emu.grasscutter.data.*;
import emu.grasscutter.data.ResourceLoader.OpenConfigData;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.data.excels.ShareCDData;
import emu.grasscutter.data.excels.avatar.*;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.ability.actions.*;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.net.proto.AbilityInvokeArgumentOuterClass.AbilityInvokeArgument;
import emu.grasscutter.net.proto.AbilityInvokeEntryHeadOuterClass.AbilityInvokeEntryHead;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.net.proto.AbilityMetaModifierChangeOuterClass.AbilityMetaModifierChange;
import emu.grasscutter.net.proto.AbilityStringOuterClass.AbilityString;
import emu.grasscutter.net.proto.AvatarSkillInfoNotifyOuterClass.AvatarSkillInfoNotify;
import emu.grasscutter.net.proto.AvatarSkillInfoOuterClass.AvatarSkillInfo;
import emu.grasscutter.net.proto.ModifierActionOuterClass.ModifierAction;
import emu.grasscutter.server.game.*;
import emu.grasscutter.server.packet.send.PacketAvatarSkillInfoNotify;
import emu.grasscutter.utils.JsonUtils;

import it.unimi.dsi.fastutil.ints.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

class AbilitySkillCooldownTest {
    private static List<AvatarSkillData> skills;
    private static List<ShareCDData> sharedGroups;
    private static final Map<String, OpenConfigEntry> configs = new TreeMap<>();
    private AbilityTestFixture fixture;
    private TestAvatar avatar;
    private EntityAvatar entity;
    private Ability ability;
    private AbilitySkillCooldown cooldown;
    private PlayerTimeClock clock;
    private final AtomicLong nanos = new AtomicLong();
    private final List<Runnable> restore = new ArrayList<>();

    @BeforeAll
    static void resources() throws Exception {
        AbilityManager.registerHandlers();
        skills =
                JsonUtils.loadToList(
                        Path.of("resources/ExcelBinOutput/AvatarSkillExcelConfigData.json"),
                        AvatarSkillData.class);
        sharedGroups =
                JsonUtils.loadToList(
                        Path.of("resources/ExcelBinOutput/ShareCDExcelConfigData.json"),
                        ShareCDData.class);
        try (var files = Files.walk(Path.of("resources/BinOutput/Talent"))) {
            for (var file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                var root = JsonParser.parseString(Files.readString(file));
                if (!root.isJsonObject()) continue;
                for (var row : root.getAsJsonObject().entrySet()) {
                    if (!row.getValue().isJsonArray()) continue;
                    var entry =
                            new OpenConfigEntry(
                                    row.getKey(),
                                    JsonUtils.decode(row.getValue(), OpenConfigData[].class));
                    if (entry.getSkillCooldownModifiers().length > 0)
                        configs.put(row.getKey(), entry);
                }
            }
        }
    }

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        fixture.player.setUid(76801);
        fixture.player.setWorld(fixture.scene.getWorld());
        clock = new PlayerTimeClock(nanos::get);
        fixture.player.setSession(new GameSession(null, clock));
        for (var skill : skills) put(GameData.getAvatarSkillDataMap(), skill.getId(), skill);
        for (var group : sharedGroups) put(GameData.getShareCDDataMap(), group.getId(), group);
        configs.forEach((name, data) -> put(GameData.getOpenConfigEntries(), name, data));
        var depot =
                JsonUtils.decode(
                        "{\"id\":76801,\"skills\":[10022,10032,10071,10077,10202,10322,10332,10472,10473,10642],\"talents\":[],\"inherentProudSkillOpens\":[]}",
                        AvatarSkillDepotData.class);
        depot.onLoad();
        avatar = new TestAvatar(depot);
        entity = entity(avatar);
        ability =
                new Ability(
                        JsonUtils.decode(
                                "{\"abilityName\":\"SkillCooldownTest\"}", AbilityData.class),
                        entity,
                        fixture.player);
        cooldown = fixture.player.getAbilityManager().getSkillCooldown();
        fixture.player.packets.clear();
    }

    final class TestAvatar extends SkillTalentLevelTest.RecordingAvatar {
        final IntSet talents = new IntOpenHashSet();

        TestAvatar(AvatarSkillDepotData depot) {
            super(fixture.player, 10000073, depot);
        }

        @Override
        public IntSet getTalentIdList() {
            return talents == null ? IntSets.EMPTY_SET : talents;
        }
    }

    private EntityAvatar entity(TestAvatar value) {
        var result =
                new EntityAvatar(fixture.scene, value) {
                    private final AbilityPeriodicHealing periodic =
                            new AbilityPeriodicHealing(this, nanos::get);
                    private final AbilityModifierDuration duration =
                            new AbilityModifierDuration(this, nanos::get);

                    @Override
                    public AbilityPeriodicHealing getAbilityPeriodicHealing() {
                        return periodic;
                    }

                    @Override
                    public AbilityModifierDuration getAbilityModifierDuration() {
                        return duration;
                    }

                    @Override
                    public void initAbilities() {}
                };
        fixture.scene.getEntities().put(result.getId(), result);
        fixture.player.getTeamManager().getActiveTeam().add(result);
        return result;
    }

    private <K, V> void put(Map<K, V> map, K key, V value) {
        V old = map.put(key, value);
        restore.add(
                () -> {
                    if (old == null) map.remove(key);
                    else map.put(key, old);
                });
    }

    @AfterEach
    void close() {
        Collections.reverse(restore);
        restore.forEach(Runnable::run);
        fixture.close();
    }

    private void activate(String name, float... parameters) {
        int id = 768010 + avatar.talents.size();
        put(
                GameData.getAvatarTalentDataMap(),
                id,
                JsonUtils.decode(
                        "{\"talentId\":"
                                + id
                                + ",\"openConfig\":\""
                                + name
                                + "\",\"paramList\":"
                                + JsonUtils.encode(parameters)
                                + "}",
                        AvatarTalentData.class));
        avatar.talents.add(id);
    }

    private void config(String name, String json, float... parameters) {
        put(
                GameData.getOpenConfigEntries(),
                name,
                new OpenConfigEntry(name, JsonUtils.decode(json, OpenConfigData[].class)));
        activate(name, parameters);
    }

    private void advance(long milliseconds) {
        nanos.addAndGet(milliseconds * 1_000_000);
    }

    private AvatarSkillInfo state(int id) {
        return avatar.getSkillInfoStates().get(id);
    }

    private void startAction(int id, String fields) {
        var action =
                JsonUtils.decode(
                        "{\"$type\":\"AvatarSkillStart\",\"skillID\":" + id + fields + "}",
                        AbilityModifierAction.class);
        AbilityManager.runServerOwned(
                () ->
                        assertTrue(
                                new ActionAvatarSkillStart()
                                        .execute(ability, action, ByteString.EMPTY, entity)));
    }

    @ParameterizedTest
    @CsvSource({
        "Ambor_Talent_211,10032,12", "Barbara_Constellation_2,10071,27.2",
        "Bennett_PermanentSkill_1,10322,4", "Kazuha_Constellation_1,10472,5.4",
        "Kazuha_Constellation_1,10473,5.4", "Player_Rock_PermanentSkill_1,10077,6",
        "Razor_PermanentSkill_1,10202,4.92", "Tartaglia_Constellation_1,10332,4.8",
        "Yunjin_Constellation_1,10642,7.38"
    })
    void realTalentResourcesChangeOnlyTheirSkillAndRemovalRestoresBaseline(
            String name, int id, float expected) {
        assertEquals(
                9,
                configs.values().stream()
                        .mapToInt(c -> c.getSkillCooldownModifiers().length)
                        .sum());
        var data = GameData.getAvatarSkillDataMap().get(id);
        assertEquals(0, data.getShareCDID());
        float baseline = data.getCdTime();
        activate(name);
        for (int i = 0; i < 3; i++)
            assertEquals(
                    expected,
                    AbilityTalentResolver.cooldownSeconds(avatar, id, baseline).orElseThrow(),
                    .00001);
        assertEquals(5, AbilityTalentResolver.cooldownSeconds(avatar, 10022, 5).orElseThrow());
        startAction(id, ",\"BGNJBEPEJBO\":1");
        assertEquals(Math.round(expected * 1000), state(id).getFullCdTimeList(0));
        avatar.talents.clear();
        assertEquals(
                baseline,
                AbilityTalentResolver.cooldownSeconds(avatar, id, baseline).orElseThrow());
        assertEquals(Math.round(expected * 1000), state(id).getFullCdTimeList(0));
        assertTrue(fixture.player.packets.isEmpty());
        assertEquals(0, avatar.saves);
    }

    @Test
    void deltasAndRatiosSumIndependentlyAndOnlyUnconditionalValidValuesApply() {
        config(
                "First",
                "[{\"$type\":\"ModifySkillCD\",\"skillID\":10022,\"cdDelta\":\"%1\",\"cdRatio\":-0.2}]",
                2);
        config(
                "Second",
                "[{\"$type\":\"ModifySkillCD\",\"skillID\":10022,\"cdDelta\":-1,\"cdRatio\":\"-%1\"}]",
                .1f);
        config(
                "Conditional",
                "[{\"$type\":\"ModifySkillCD\",\"skillID\":10022,\"cdDelta\":100,\"talentFilter\":\"Hexerei\"}]");
        config(
                "Invalid",
                "[{\"$type\":\"ModifySkillCD\",\"skillID\":10022,\"cdRatio\":\"%2\"}]",
                .8f);
        assertEquals(
                4.2, AbilityTalentResolver.cooldownSeconds(avatar, 10022, 5).orElseThrow(), .00001);
        avatar.talents.clear();
        config("Clamp", "[{\"$type\":\"ModifySkillCD\",\"skillID\":10022,\"cdDelta\":-20}]");
        assertEquals(0, AbilityTalentResolver.cooldownSeconds(avatar, 10022, 5).orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "[]",
                "{}",
                "true",
                "\"%0\"",
                "\"%999999999999999\"",
                "{\"a\":1,\"b\":2}",
                "1e1000"
            })
    void malformedTalentOperandsAreRejected(String value) {
        var config =
                new OpenConfigEntry(
                        "Invalid",
                        JsonUtils.decode(
                                "[{\"$type\":\"ModifySkillCD\",\"skillID\":10022,\"cdRatio\":"
                                        + value
                                        + "}]",
                                OpenConfigData[].class));
        assertEquals(0, config.getSkillCooldownModifiers().length);
        assertTrue(AbilityTalentResolver.cooldownSeconds(avatar, 10022, Float.NaN).isEmpty());
    }

    @Test
    void talentCacheRoundTripRetainsCooldownOperandsAndOldCacheRemainsReadable() {
        var saved =
                JsonUtils.decode(
                        JsonUtils.encode(configs.get("Barbara_Constellation_2")),
                        OpenConfigEntry.class);
        put(GameData.getOpenConfigEntries(), "Barbara_Constellation_2", saved);
        activate("Barbara_Constellation_2");
        assertEquals(
                27.2,
                AbilityTalentResolver.cooldownSeconds(avatar, 10071, 32).orElseThrow(),
                .00001);
        var old = JsonUtils.decode("{\"name\":\"Old\"}", OpenConfigEntry.class);
        assertEquals(0, old.getSkillCooldownModifiers().length);
    }

    @Test
    void holdMultiplierAppliesAfterTheTalentReductionAndStaminaIsNotUsedAsCooldown() {
        activate("Kazuha_Constellation_1");
        startAction(10473, ",\"BGNJBEPEJBO\":1.5,\"costStaminaRatio\":7");
        assertEquals(8100, state(10473).getFullCdTimeList(0));
        assertTrue(fixture.player.packets.isEmpty());
    }

    @Test
    void stanceExpressionUsesTheLiveAbilityParameterAndReservedBooleanIsNotAdded() {
        activate("Tartaglia_Constellation_1");
        ability.getAbilitySpecials().put("Gandalfr_Cooldown_Count", 10f);
        startAction(
                10332,
                ",\"BGNJBEPEJBO\":[1,\"Gandalfr_Cooldown_Count\",0.167,\"MUL\",\"ADD\"],\"cdDelta\":true");
        assertEquals(12816, state(10332).getFullCdTimeList(0));
        advance(13000);
        startAction(10332, ",\"BGNJBEPEJBO\":7.5");
        assertEquals(36000, state(10332).getFullCdTimeList(0));
    }

    @Test
    void canonicalStartMultiplierHasPriorityRegardlessOfJsonOrder() {
        startAction(10472, ",\"cdTimeRatio\":2,\"BGNJBEPEJBO\":3");
        assertEquals(12000, state(10472).getFullCdTimeList(0));
        advance(12000);
        startAction(10472, ",\"BGNJBEPEJBO\":3,\"cdTimeRatio\":2");
        assertEquals(12000, state(10472).getFullCdTimeList(0));
    }

    @Test
    void consecutiveChargesKeepTheirOwnDurationsAndRecoverSerially() {
        assertTrue(cooldown.start(avatar, 10022, 5));
        advance(1000);
        assertTrue(cooldown.start(avatar, 10022, 7));
        assertEquals(List.of(5000, 7000), state(10022).getFullCdTimeListList());
        assertEquals(1000, state(10022).getPassCdTime());
        assertTrue(cooldown.start(avatar, 10022, 99));
        assertEquals(List.of(5000, 7000), state(10022).getFullCdTimeListList());
        advance(6000);
        assertEquals(List.of(7000), state(10022).getFullCdTimeListList());
        assertEquals(2000, state(10022).getPassCdTime());
        advance(5000);
        assertEquals(0, state(10022).getFullCdTimeListCount());
    }

    @Test
    void resettingOneRecoveryDoesNotRefundAllQueuedCharges() {
        cooldown.start(avatar, 10022, 5);
        cooldown.start(avatar, 10022, 7);
        advance(1000);
        assertTrue(cooldown.modifyRemaining(avatar, 10022, 0, -1));
        assertEquals(List.of(7000), state(10022).getFullCdTimeListList());
        assertEquals(0, state(10022).getPassCdTime());
    }

    @Test
    void changingRemainingCooldownDoesNotChangeLaterChargesAndSupportsExtensions() {
        cooldown.start(avatar, 10022, 5);
        cooldown.start(avatar, 10022, 7);
        advance(1000);
        assertTrue(cooldown.modifyRemaining(avatar, 10022, -1, -.5f));
        assertEquals(3500, state(10022).getPassCdTime());
        assertTrue(cooldown.modifyRemaining(avatar, 10022, 10, 0));
        assertEquals(List.of(11500, 7000), state(10022).getFullCdTimeListList());
        assertEquals(0, state(10022).getPassCdTime());
        advance(11500);
        assertEquals(List.of(7000), state(10022).getFullCdTimeListList());
    }

    @Test
    void pauseAndSceneEntityReplacementKeepRemainingCooldown() {
        cooldown.start(avatar, 10022, 5);
        advance(1000);
        clock.setPaused(true);
        advance(90000);
        assertEquals(1000, state(10022).getPassCdTime());
        fixture.player.getTeamManager().getActiveTeam().remove(entity);
        fixture.scene.getEntities().remove(entity.getId());
        entity = entity(avatar);
        assertEquals(1000, avatar.toProto().getSkillMapOrThrow(10022).getPassCdTime());
        cooldown.synchronize();
        assertEquals(1000, lastPacket().getSkillMapOrThrow(10022).getPassCdTime());
        clock.setPaused(false);
        advance(1000);
        assertEquals(2000, state(10022).getPassCdTime());
    }

    private AvatarSkillInfoNotify lastPacket() {
        try {
            return AvatarSkillInfoNotify.parseFrom(
                    fixture.player.packets.get(fixture.player.packets.size() - 1).getData());
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void temporaryInformationAndTalentChargeNotificationsPreserveTheLiveQueue() {
        cooldown.start(avatar, 10022, 5);
        advance(1000);
        assertTrue(entity.getAbilityAvatarSkillInfo().add(ability, 10022));
        assertEquals(1000, lastPacket().getSkillMapOrThrow(10022).getPassCdTime());
        assertEquals(List.of(5000), lastPacket().getSkillMapOrThrow(10022).getFullCdTimeListList());
        avatar.getSkillExtraChargeMap().put(10022, 3);
        assertTrue(avatar.sendSkillExtraChargeMap());
        assertEquals(3, lastPacket().getSkillMapOrThrow(10022).getMaxChargeCount());
        assertEquals(1000, lastPacket().getSkillMapOrThrow(10022).getPassCdTime());
    }

    @Test
    void decreasingMaximumChargesKeepsTheFirstRecovery() {
        cooldown.start(avatar, 10022, 5);
        cooldown.start(avatar, 10022, 7);
        advance(1000);
        avatar.getSkillExtraChargeMap().put(10022, 1);
        assertEquals(List.of(5000), state(10022).getFullCdTimeListList());
        assertEquals(1000, state(10022).getPassCdTime());
    }

    @Test
    void sessionAndPlayerClockResetsCannotImportOldQueues() {
        cooldown.start(avatar, 10022, 5);
        fixture.player.setSession(new GameSession(null, new PlayerTimeClock(nanos::get)));
        assertFalse(avatar.getSkillInfoStates().containsKey(10022));
        var time = new AtomicLong(1000);
        var local = new AbilitySkillCooldown(fixture.player, time::get);
        assertTrue(local.start(avatar, 10022, 5));
        time.set(500);
        assertTrue(local.snapshot(avatar, Map.of()).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(doubles = {-1, Double.NaN, Double.POSITIVE_INFINITY, 4294968})
    void invalidDurationsNeverCreateARecovery(double seconds) {
        assertFalse(cooldown.start(avatar, 10022, seconds));
        assertFalse(avatar.getSkillInfoStates().containsKey(10022));
    }

    @Test
    void unknownForeignAndSharedSkillsNeverEnterOrdinaryRecovery() {
        assertFalse(cooldown.start(avatar, 999999, 5));
        assertFalse(cooldown.start(avatar, 20050, 5));
        var other = new AbilitySkillCooldown(new AbilityTestFixture.TestPlayer());
        assertFalse(other.start(avatar, 10022, 5));
        avatar.getSkillExtraChargeMap().put(10022, 65);
        assertFalse(cooldown.start(avatar, 10022, 5));
        assertTrue(cooldown.snapshot(avatar, Map.of()).isEmpty());
    }

    @Test
    void explicitRuntimeActionUsesRatioDeltaButRejectsUnverifiedSlotAndAbsoluteBranches() {
        cooldown.start(avatar, 10022, 5);
        advance(1000);
        var handler = new ActionModifyAvatarSkillCooldown();
        var action =
                JsonUtils.decode(
                        "{\"$type\":\"ModifyAvatarSkillCD\",\"skillID\":10022,\"cdDelta\":-1,\"BGNJBEPEJBO\":-0.5}",
                        AbilityModifierAction.class);
        assertTrue(handler.execute(ability, action, ByteString.EMPTY, entity));
        assertEquals(3500, state(10022).getPassCdTime());
        for (var field : List.of("\"skillSlot\":[2]", "\"FBGLBGFMOHC\":1", "\"cdMin\":0")) {
            var unsupported =
                    JsonUtils.decode(
                            "{\"$type\":\"ModifyAvatarSkillCD\",\"skillID\":10022,\"cdRatio\":-1,"
                                    + field
                                    + "}",
                            AbilityModifierAction.class);
            assertFalse(handler.execute(ability, unsupported, ByteString.EMPTY, entity));
            assertEquals(3500, state(10022).getPassCdTime());
        }
        assertTrue(fixture.player.packets.isEmpty());
    }

    @Test
    void serverOwnedCallbackDispatchRecordsSingleChargeSkillsWithoutRepeatingSideEffects() {
        var action =
                JsonUtils.decode(
                        "{\"$type\":\"AvatarSkillStart\",\"skillID\":10472,\"BGNJBEPEJBO\":1.5}",
                        AbilityModifierAction.class);
        assertTrue(AbilityManager.isAllowedInServerOwnedChain(action.type));
        for (int i = 0; i < 3; i++)
            AbilityManager.runServerOwned(
                    () ->
                            assertTrue(
                                    fixture.player
                                            .getAbilityManager()
                                            .executeCallbackActionsNow(
                                                    ability,
                                                    new AbilityModifierAction[] {action},
                                                    ByteString.EMPTY,
                                                    entity,
                                                    entity)));
        assertEquals(List.of(9000), state(10472).getFullCdTimeListList());
        advance(1000);
        assertEquals(1000, state(10472).getPassCdTime());
        assertTrue(fixture.player.packets.isEmpty());
        assertEquals(0, avatar.saves);
    }

    @Test
    void autonomousCallbacksDoNotInventMultipleChargeConsumptionWithoutInstanceDeduplication() {
        startAction(10022, ",\"BGNJBEPEJBO\":1");
        assertNull(state(10022));
        assertTrue(cooldown.start(avatar, 10022, 5));
        assertEquals(List.of(5000), state(10022).getFullCdTimeListList());
    }

    private static final String START = "{\"$type\":\"AvatarSkillStart\",\"skillID\":10022}";

    private void modifierAbility(String fields) {
        ability =
                new Ability(
                        JsonUtils.decode(
                                "{\"abilityName\":\"Cooldown_Instances\",\"modifiers\":{\"Buff\":{"
                                        + fields
                                        + "}}}",
                                AbilityData.class),
                        entity,
                        fixture.player);
        entity.getInstancedAbilities().clear();
        entity.getInstancedAbilities().add(ability);
    }

    private void clientModifier(boolean add, int id) throws Exception {
        var change =
                AbilityMetaModifierChange.newBuilder()
                        .setAction(
                                add
                                        ? ModifierAction.MODIFIER_ACTION_ADDED
                                        : ModifierAction.MODIFIER_ACTION_REMOVED)
                        .setParentAbilityName(
                                AbilityString.newBuilder().setStr(ability.getData().abilityName))
                        .setModifierLocalId(0);
        fixture.player
                .getAbilityManager()
                .onAbilityInvoke(
                        AbilityInvokeEntry.newBuilder()
                                .setEntityId(entity.getId())
                                .setHead(
                                        AbilityInvokeEntryHead.newBuilder()
                                                .setTargetId(entity.getId())
                                                .setInstancedAbilityId(1)
                                                .setInstancedModifierId(id))
                                .setArgumentType(
                                        AbilityInvokeArgument
                                                .AbilityInvokeArgument_ABILITY_META_MODIFIER_CHANGE)
                                .setAbilityData(change.build().toByteString())
                                .build());
    }

    private void confirm(AbilityModifierAction action, int instance, EntityAvatar recipient)
            throws Exception {
        int local =
                ability.getData().localIdToAction.entrySet().stream()
                        .filter(row -> row.getValue() == action)
                        .findFirst()
                        .orElseThrow()
                        .getKey();
        ability.getManager()
                .handleServerInvoke(
                        AbilityInvokeEntry.newBuilder()
                                .setEntityId(entity.getId())
                                .setHead(
                                        AbilityInvokeEntryHead.newBuilder()
                                                .setTargetId(recipient.getId())
                                                .setInstancedAbilityId(1)
                                                .setInstancedModifierId(instance)
                                                .setLocalId(local))
                                .build());
    }

    private AbilityModifierAction added() {
        return ability.getData().modifiers.get("Buff").onAdded[0];
    }

    @Test
    void repeatedAddedAndActionConfirmationsConsumeOneChargeButANewInstanceConsumesAnother()
            throws Exception {
        modifierAbility("\"stacking\":\"Multiple\",\"onAdded\":[" + START + "]");
        clientModifier(true, 71);
        advance(1000);
        clientModifier(true, 71);
        confirm(added(), 71, entity);
        assertEquals(List.of(5000), state(10022).getFullCdTimeListList());
        assertEquals(1000, state(10022).getPassCdTime());
        clientModifier(true, 72);
        confirm(added(), 72, entity);
        assertEquals(List.of(5000, 5000), state(10022).getFullCdTimeListList());
        assertTrue(fixture.player.packets.isEmpty());
        assertEquals(0, avatar.saves);
    }

    @Test
    void expiredOrRemovedConfirmationsCannotStartANewRecoveryAndReusedIdsAreFresh()
            throws Exception {
        modifierAbility("\"onAdded\":[" + START + "]");
        clientModifier(true, 71);
        advance(6000);
        confirm(added(), 71, entity);
        assertEquals(0, state(10022).getFullCdTimeListCount());
        clientModifier(false, 71);
        confirm(added(), 71, entity);
        assertEquals(0, state(10022).getFullCdTimeListCount());
        clientModifier(true, 71);
        assertEquals(List.of(5000), state(10022).getFullCdTimeListList());
    }

    @Test
    void ratioChangeAndClientConfirmationApplyOnlyOnce() throws Exception {
        cooldown.start(avatar, 10022, 5);
        advance(1000);
        modifierAbility(
                "\"onAdded\":[{\"$type\":\"ModifyAvatarSkillCD\",\"skillID\":10022,\"cdRatio\":-0.5}]");
        clientModifier(true, 71);
        assertEquals(3000, state(10022).getPassCdTime());
        clientModifier(true, 71);
        confirm(added(), 71, entity);
        assertEquals(3000, state(10022).getPassCdTime());
        clientModifier(true, 72);
        assertEquals(4000, state(10022).getPassCdTime());
    }

    @Test
    void removalStartsItsOwnRecoveryOnceAndDoesNotRepeatOtherClientEffects() throws Exception {
        modifierAbility(
                "\"onRemoved\":[{\"$type\":\"SetGlobalValue\",\"key\":\"unrelated\",\"value\":99},"
                        + START
                        + "]");
        var removed = ability.getData().modifiers.get("Buff").onRemoved[1];
        clientModifier(true, 71);
        assertNull(state(10022));
        confirm(removed, 71, entity);
        clientModifier(false, 71);
        assertEquals(List.of(5000), state(10022).getFullCdTimeListList());
        assertFalse(entity.getGlobalAbilityValues().containsKey("unrelated"));
        advance(6000);
        clientModifier(false, 71);
        confirm(removed, 71, entity);
        assertEquals(0, state(10022).getFullCdTimeListCount());
    }

    @Test
    void modifierRemovalWithoutAnActionConfirmationStillRecordsItsCooldown() throws Exception {
        modifierAbility("\"onRemoved\":[" + START + "]");
        clientModifier(true, 71);
        clientModifier(false, 71);
        assertEquals(List.of(5000), state(10022).getFullCdTimeListList());
    }

    @Test
    void rootAbilityStartInvocationsRemainIndependentUsesOfThePersistentAbility() throws Exception {
        ability =
                new Ability(
                        JsonUtils.decode(
                                "{\"abilityName\":\"RootCooldown\",\"onAbilityStart\":["
                                        + START
                                        + "]}",
                                AbilityData.class),
                        entity,
                        fixture.player);
        entity.getInstancedAbilities().add(ability);
        confirm(ability.getData().onAbilityStart[0], 0, entity);
        confirm(ability.getData().onAbilityStart[0], 0, entity);
        assertEquals(List.of(5000, 5000), state(10022).getFullCdTimeListList());
    }

    @Test
    void missingInstanceMatchesOnlyOneLiveModifierAndAmbiguousConfirmationsAreIgnored()
            throws Exception {
        modifierAbility(
                "\"stacking\":\"Multiple\",\"onAdded\":[{\"$type\":\"ModifyAvatarSkillCD\",\"skillID\":10022,\"cdRatio\":-0.5}]");
        clientModifier(true, 71); // No recovery yet; a later confirmation cannot modify a new cast.
        cooldown.start(avatar, 10022, 5);
        confirm(added(), 0, entity);
        assertEquals(0, state(10022).getPassCdTime());
        clientModifier(true, 72);
        assertEquals(2500, state(10022).getPassCdTime());
        confirm(added(), 0, entity);
        confirm(added(), 999, entity);
        assertEquals(2500, state(10022).getPassCdTime());
    }

    @Test
    void eachRecipientHasItsOwnDecisionWithinOneModifierApplication() throws Exception {
        modifierAbility("\"onAdded\":[" + START + "]");
        var second = new TestAvatar(avatar.getSkillDepot());
        var secondEntity = entity(second);
        clientModifier(true, 71);
        confirm(added(), 71, secondEntity);
        confirm(added(), 71, secondEntity);
        assertEquals(List.of(5000), state(10022).getFullCdTimeListList());
        assertEquals(List.of(5000), second.getSkillInfoStates().get(10022).getFullCdTimeListList());
    }

    @Test
    void failedOperandsDoNotMarkAnApplicationBeforeAValidRetry() throws Exception {
        modifierAbility(
                "\"onAdded\":[{\"$type\":\"AvatarSkillStart\",\"skillID\":10022,\"cdTimeRatio\":\"Multiplier\"}]");
        ability.getAbilitySpecials().put("Multiplier", Float.NaN);
        clientModifier(true, 71);
        assertNull(state(10022));
        ability.getAbilitySpecials().put("Multiplier", 1f);
        confirm(added(), 71, entity);
        confirm(added(), 71, entity);
        assertEquals(List.of(5000), state(10022).getFullCdTimeListList());
    }

    @Test
    void directApplicationAndItsLaterClientAssignmentShareTheCooldownDecision() throws Exception {
        modifierAbility("\"onAdded\":[" + START + "]");
        var apply =
                JsonUtils.decode(
                        "{\"$type\":\"ApplyModifier\",\"modifierName\":\"Buff\"}",
                        AbilityModifierAction.class);
        assertTrue(new ActionApplyModifier().execute(ability, apply, ByteString.EMPTY, entity));
        clientModifier(true, 71);
        confirm(added(), 71, entity);
        assertEquals(List.of(5000), state(10022).getFullCdTimeListList());
    }

    @Test
    void twoUnassignedDirectApplicationsBindToTwoDistinctClientInstances() throws Exception {
        modifierAbility("\"stacking\":\"Multiple\",\"onAdded\":[" + START + "]");
        var apply =
                JsonUtils.decode(
                        "{\"$type\":\"ApplyModifier\",\"modifierName\":\"Buff\"}",
                        AbilityModifierAction.class);
        var handler = new ActionApplyModifier();
        assertTrue(handler.execute(ability, apply, ByteString.EMPTY, entity));
        var first = entity.getAppliedAbilityModifiers().get(ability).get("Buff");
        assertTrue(handler.execute(ability, apply, ByteString.EMPTY, entity));
        var second = entity.getAppliedAbilityModifiers().get(ability).get("Buff");
        assertNotSame(first, second);
        clientModifier(true, 71);
        clientModifier(true, 72);
        assertSame(first, entity.getInstancedModifiers().get(71));
        assertSame(second, entity.getInstancedModifiers().get(72));
        confirm(added(), 71, entity);
        confirm(added(), 72, entity);
        assertEquals(List.of(5000, 5000), state(10022).getFullCdTimeListList());
    }

    @Test
    void conditionalRemovalPreservesItsPredicateAndSkipsUnrelatedBranches() throws Exception {
        modifierAbility(
                "\"onRemoved\":[{\"$type\":\"Predicated\",\"targetPredicates\":[{\"$type\":\"ByTargetGlobalValue\",\"key\":\"Release\",\"value\":1,\"compareType\":\"Equal\"}],"
                    + "\"successActions\":["
                        + START
                        + ",{\"$type\":\"SetGlobalValue\",\"key\":\"unrelated\",\"value\":99}]}]");
        clientModifier(true, 71);
        clientModifier(false, 71);
        assertNull(state(10022));
        entity.getGlobalAbilityValues().put("Release", 1f);
        clientModifier(true, 72);
        clientModifier(false, 72);
        assertEquals(List.of(5000), state(10022).getFullCdTimeListList());
        assertFalse(entity.getGlobalAbilityValues().containsKey("unrelated"));
    }

    private void periodicAbility(String child) {
        ability =
                new Ability(
                        JsonUtils.decode(
                                "{\"abilityName\":\"PeriodicCooldown\",\"modifiers\":{"
                                        + "\"Buff\":{"
                                        + child
                                        + "},\"Pulse\":{\"thinkInterval\":1,\"duration\":30,"
                                        + "\"onThinkInterval\":[{\"$type\":\"ApplyModifier\",\"modifierName\":\"Buff\"}]}}}",
                                AbilityData.class),
                        entity,
                        fixture.player);
        entity.getInstancedAbilities().add(ability);
        ability.registerModifier(
                entity,
                new AbilityModifierController(
                        ability, ability.getData(), ability.getData().modifiers.get("Pulse")));
    }

    @Test
    void serverGeneratedCallbacksAndTheirClientConfirmationShareOneApplication() throws Exception {
        periodicAbility(
                "\"stacking\":\"MultipleRefreshNoRemove\",\"stackingOption\":{\"maxModifierNumForMultipleType\":1},"
                    + "\"duration\":20,\"onAdded\":["
                        + START
                        + "]");
        advance(1000);
        entity.getAbilityPeriodicHealing().tick();
        assertEquals(List.of(5000), state(10022).getFullCdTimeListList());
        advance(1000);
        entity.getAbilityPeriodicHealing().tick(); // Refreshing the same slot is not another cast.
        clientModifier(true, 71);
        assertTrue(entity.getInstancedModifiers().get(71).hasServerCallbacks());
        confirm(added(), 71, entity);
        assertEquals(List.of(5000), state(10022).getFullCdTimeListList());
        assertEquals(1000, state(10022).getPassCdTime());
    }

    @Test
    void repeatedCooldownLeavesAreNotClaimedAsOneServerOwnedApplication() {
        periodicAbility(
                "\"onAdded\":[{\"$type\":\"Repeated\",\"repeatCount\":2,\"actions\":["
                        + START
                        + "]}]");
        advance(1000);
        entity.getAbilityPeriodicHealing().tick();
        assertNull(state(10022));
        assertFalse(entity.getAppliedAbilityModifiers().get(ability).containsKey("Buff"));
    }

    @Test
    void unsupportedCooldownShapesDoNotClaimTheContainingServerCallback() {
        periodicAbility(
                "\"onAdded\":[{\"$type\":\"ModifyAvatarSkillCD\",\"skillID\":10022,\"cdMin\":0,\"cdRatio\":-1}]");
        cooldown.start(avatar, 10022, 5);
        advance(1000);
        entity.getAbilityPeriodicHealing().tick();
        assertEquals(1000, state(10022).getPassCdTime());
        assertFalse(entity.getAppliedAbilityModifiers().get(ability).containsKey("Buff"));
    }

    @Test
    void sharedChargeStartsAreAlsoDeduplicatedByModifierInstance() throws Exception {
        int skill =
                skills.stream()
                        .filter(s -> s.getShareCDID() == 20050)
                        .findFirst()
                        .orElseThrow()
                        .getId();
        modifierAbility(
                "\"stacking\":\"Multiple\",\"onAdded\":[{\"$type\":\"AvatarShareCDSkillStart\",\"skillID\":"
                        + skill
                        + ",\"shareCDType\":0}]");
        clientModifier(true, 71);
        confirm(added(), 71, entity);
        var other = entity(new TestAvatar(avatar.getSkillDepot()));
        confirm(added(), 71, other);
        clientModifier(true, 71);
        var shared = fixture.player.getAbilityManager().getSharedCooldown();
        assertEquals(1, shared.snapshot().get(20050).size());
        clientModifier(true, 72);
        assertEquals(2, shared.snapshot().get(20050).size());
        assertEquals(2, fixture.player.packets.size());
    }

    @Test
    void anOldSharedClearConfirmationCannotEraseALaterCast() throws Exception {
        int skill =
                skills.stream()
                        .filter(s -> s.getShareCDID() == 20050)
                        .findFirst()
                        .orElseThrow()
                        .getId();
        var shared = fixture.player.getAbilityManager().getSharedCooldown();
        assertTrue(shared.startAvatarSkill(skill, 0));
        modifierAbility("\"onAdded\":[{\"$type\":\"ClearShareCD\",\"shareCDID\":20050}]");
        clientModifier(true, 71);
        assertTrue(shared.snapshot().isEmpty());
        assertTrue(shared.startAvatarSkill(skill, 0));
        confirm(added(), 71, entity);
        confirm(added(), 71, entity(new TestAvatar(avatar.getSkillDepot())));
        assertEquals(1, shared.snapshot().get(20050).size());
    }

    @Test
    void nestedInvocationScopesRestoreAfterFailureAndDoNotLeakIntoOtherActions() throws Exception {
        modifierAbility("\"onAdded\":[" + START + "]");
        clientModifier(true, 71);
        var modifier = entity.getInstancedModifiers().get(71);
        var outer = new AbilityCooldownInvocation.Scope(modifier, true);
        AbilityCooldownInvocation.run(
                outer,
                () -> {
                    assertSame(outer, AbilityCooldownInvocation.capture());
                    assertThrows(
                            IllegalStateException.class,
                            () ->
                                    AbilityCooldownInvocation.run(
                                            modifier,
                                            () -> {
                                                throw new IllegalStateException("test");
                                            }));
                    assertSame(outer, AbilityCooldownInvocation.capture());
                    var write =
                            JsonUtils.decode(
                                    "{\"$type\":\"SetGlobalValue\",\"key\":\"unrelated\",\"value\":99}",
                                    AbilityModifierAction.class);
                    ability.getManager().executeActionNow(ability, write, ByteString.EMPTY, entity);
                    ability.getManager().executeAction(ability, write, ByteString.EMPTY, entity);
                });
        assertNull(AbilityCooldownInvocation.capture());
        assertFalse(entity.getGlobalAbilityValues().containsKey("unrelated"));
    }

    @Test
    void nativeMillisecondsAndWireTagsAgreeWithTheExistingProtocol() {
        var info =
                AvatarSkillInfo.newBuilder()
                        .setPassCdTime(1000)
                        .addFullCdTimeList(5000)
                        .addFullCdTimeList(7000)
                        .setMaxChargeCount(2)
                        .build();
        assertArrayEquals(HexFormat.of().parseHex("08e80712048827d8361802"), info.toByteArray());
        var packet = new PacketAvatarSkillInfoNotify(1234, Map.of(10022, info));
        assertEquals(24619, packet.getOpcode());
        try {
            assertEquals(
                    info,
                    AvatarSkillInfoNotify.parseFrom(packet.getData()).getSkillMapOrThrow(10022));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
