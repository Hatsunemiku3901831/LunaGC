package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import com.google.protobuf.ByteString;

import emu.grasscutter.data.*;
import emu.grasscutter.data.ResourceLoader.OpenConfigData;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.data.excels.avatar.*;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.ability.actions.*;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.net.proto.AvatarSkillInfoNotifyOuterClass.AvatarSkillInfoNotify;
import emu.grasscutter.net.proto.AvatarSkillInfoOuterClass.AvatarSkillInfo;
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
        skills =
                JsonUtils.loadToList(
                        Path.of("resources/ExcelBinOutput/AvatarSkillExcelConfigData.json"),
                        AvatarSkillData.class);
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
