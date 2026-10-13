package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.ResourceLoader.OpenConfigData;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.data.excels.avatar.*;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

class SixthConstellationHealthTest {
    static List<AvatarSkillData> skills;
    static List<AvatarSkillDepotData> depots;
    static List<AvatarTalentData> talents;
    AbilityTestFixture fixture;
    EntityAvatar entity;
    Ability main;
    final AtomicLong nanos = new AtomicLong(), playerMs = new AtomicLong(1000);
    final List<Runnable> restore = new ArrayList<>();

    @BeforeAll
    static void resources() throws Exception {
        var root = Path.of("/resources/ExcelBinOutput");
        skills =
                JsonUtils.loadToList(
                        root.resolve("AvatarSkillExcelConfigData.json"), AvatarSkillData.class);
        depots =
                JsonUtils.loadToList(
                        root.resolve("AvatarSkillDepotExcelConfigData.json"),
                        AvatarSkillDepotData.class);
        talents =
                JsonUtils.loadToList(
                        root.resolve("AvatarTalentExcelConfigData.json"), AvatarTalentData.class);
        AbilityManager.registerHandlers();
    }

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        fixture.player.setWorld(fixture.scene.getWorld());
        fixture.player.setSession(new GameSession(null));
        for (var data : skills) put(GameData.getAvatarSkillDataMap(), data.getId(), data);
        var manager = fixture.player.getAbilityManager();
        var field = AbilityManager.class.getDeclaredField("skillCooldown");
        field.setAccessible(true);
        field.set(manager, new AbilitySkillCooldown(fixture.player, playerMs::get));
    }

    <K, V> void put(Map<K, V> map, K key, V value) {
        var old = map.put(key, value);
        restore.add(
                () -> {
                    if (old == null) map.remove(key);
                    else map.put(key, old);
                });
    }

    @AfterEach
    void close() throws Exception {
        drain();
        Collections.reverse(restore);
        restore.forEach(Runnable::run);
        fixture.close();
    }

    void drain() throws Exception {
        var barrier = new CyclicBarrier(4);
        var pending = new ArrayList<Future<?>>();
        for (int i = 0; i < 4; i++)
            pending.add(
                    AbilityManager.eventExecutor.submit(
                            () -> {
                                barrier.await(5, TimeUnit.SECONDS);
                                return null;
                            }));
        for (var future : pending) future.get(6, TimeUnit.SECONDS);
    }

    void character(String name, boolean c6) throws Exception {
        int id = "Hutao".equals(name) ? 10000046 : 10000065;
        int depotId = "Hutao".equals(name) ? 4601 : 6501;
        int talentId = "Hutao".equals(name) ? 466 : 656;
        var depot = depots.stream().filter(row -> row.getId() == depotId).findFirst().orElseThrow();
        depot.onLoad();
        var avatar = new SkillTalentLevelTest.RecordingAvatar(fixture.player, id, depot);
        if (c6) {
            var unlocked =
                    emu.grasscutter.game.avatar.Avatar.class.getDeclaredField("talentIdList");
            unlocked.setAccessible(true);
            @SuppressWarnings("unchecked")
            var stored = (Set<Integer>) unlocked.get(avatar);
            stored.add(talentId);
            assertTrue(avatar.getTalentIdList().contains(talentId));
        }
        put(
                GameData.getAvatarTalentDataMap(),
                talentId,
                talents.stream().filter(row -> row.getId() == talentId).findFirst().orElseThrow());
        var config =
                JsonParser.parseString(
                                Files.readString(
                                        Path.of(
                                                "/resources/BinOutput/Talent/AvatarTalents/ConfigTalent_"
                                                        + name
                                                        + ".json")))
                        .getAsJsonObject();
        for (var entry : config.entrySet())
            if (entry.getValue().isJsonArray())
                put(
                        GameData.getOpenConfigEntries(),
                        entry.getKey(),
                        new OpenConfigEntry(
                                entry.getKey(),
                                JsonUtils.decode(entry.getValue(), OpenConfigData[].class)));
        var data =
                JsonParser.parseString(
                                Files.readString(
                                        Path.of(
                                                "/resources/BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_"
                                                        + name
                                                        + ".json")))
                        .getAsJsonArray();
        for (var row : data) {
            var ability = JsonUtils.decode(row.getAsJsonObject().get("Default"), AbilityData.class);
            put(GameData.getAbilityDataMap(), ability.abilityName, ability);
        }
        entity =
                new EntityAvatar(fixture.scene, avatar) {
                    final AbilityPeriodicHealing periodic =
                            new AbilityPeriodicHealing(this, nanos::get);
                    final AbilityModifierDuration durations =
                            new AbilityModifierDuration(this, nanos::get);

                    @Override
                    public void initAbilities() {}

                    @Override
                    public AbilityPeriodicHealing getAbilityPeriodicHealing() {
                        return periodic;
                    }

                    @Override
                    public AbilityModifierDuration getAbilityModifierDuration() {
                        return durations;
                    }
                };
        fixture.scene.getEntities().put(entity.getId(), entity);
        fixture.player.getTeamManager().getActiveTeam().add(entity);
        entity.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 100);
        entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 100);
        if (c6) {
            String mainName =
                    "Hutao".equals(name)
                            ? "Avatar_Hutao_Constellation_Limbo"
                            : "Avatar_Shinobu_Constellation6_Limbo";
            fixture.player.getAbilityManager().addAbilityToEntity(entity, mainName);
            drain();
            main =
                    entity.getInstancedAbilities().stream()
                            .filter(a -> mainName.equals(a.getData().abilityName))
                            .findFirst()
                            .orElseThrow();
            assertEquals(
                    0.25f,
                    main.getAbilitySpecials()
                            .getFloat(
                                    "Hutao".equals(name)
                                            ? "Hutao_Constellation_Limbo_HPRatio"
                                            : "Shinobu_Constellation_6_HpRate_P1"));
        }
    }

    float hp() {
        return entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
    }

    float critical() {
        return entity.getFightProperty(FightProperty.FIGHT_PROP_CRITICAL);
    }

    float mastery() {
        return entity.getFightProperty(FightProperty.FIGHT_PROP_ELEMENT_MASTERY);
    }

    int pending(int id) {
        return fixture.player
                .getAbilityManager()
                .getSkillCooldown()
                .state(entity.getAvatar(), id, 1)
                .getFullCdTimeListCount();
    }

    void advance(long milliseconds) {
        playerMs.addAndGet(milliseconds);
        nanos.addAndGet(milliseconds * 1_000_000);
        entity.getAbilityModifierDuration().tick();
        entity.getAbilityPeriodicHealing().tick();
    }

    @Test
    void hutaoLowHealthEventAppliesTheResourceBuffAndStartsCooldown() throws Exception {
        character("Hutao", true);
        float baseline = critical();
        entity.damage(80);
        assertEquals(20, hp());
        assertEquals(baseline + 1, critical(), 0.0001f);
        assertEquals(1, pending(10464));
        assertFalse(AbilityCombatState.read(entity).limbo());
    }

    @Test
    void hutaoSurvivesTheFirstLethalHitWithoutWaitingForAPulse() throws Exception {
        character("Hutao", true);
        float baseline = critical();
        entity.damage(120);
        assertEquals(1, hp());
        assertTrue(entity.isAlive());
        assertEquals(baseline + 1, critical(), 0.0001f);
        assertEquals(1, pending(10464));
    }

    @Test
    void hutaoCooldownDoesNotRefreshTheBuffOnAnotherHealthDrop() throws Exception {
        character("Hutao", true);
        float baseline = critical();
        entity.damage(80);
        advance(5000);
        entity.damage(1);
        assertEquals(baseline + 1, critical(), 0.0001f);
        advance(5000);
        assertEquals(baseline, critical(), 0.0001f);
        assertEquals(1, pending(10464));
    }

    @Test
    void hutaoDoesNotTriggerAtExactlyTwentyFivePercent() throws Exception {
        character("Hutao", true);
        float baseline = critical();
        entity.damage(75);
        assertEquals(25, hp());
        assertEquals(baseline, critical());
        assertEquals(0, pending(10464));
        entity.damage(1);
        assertEquals(baseline + 1, critical(), 0.0001f);
    }

    @Test
    void hutaoResistanceLayersExpireWithoutErasingTheBaseline() throws Exception {
        character("Hutao", true);
        entity.setFightProperty(FightProperty.FIGHT_PROP_FIRE_SUB_HURT, 0.1f);
        entity.damage(80);
        assertEquals(
                2.1f, entity.getFightProperty(FightProperty.FIGHT_PROP_FIRE_SUB_HURT), 0.0001f);
        advance(10000);
        assertEquals(
                0.1f, entity.getFightProperty(FightProperty.FIGHT_PROP_FIRE_SUB_HURT), 0.0001f);
    }

    @Test
    void hutaoCanTriggerAgainAfterSixtySeconds() throws Exception {
        character("Hutao", true);
        float baseline = critical();
        entity.damage(80);
        advance(60000);
        entity.damage(1);
        assertEquals(baseline + 1, critical(), 0.0001f);
        assertEquals(1, pending(10464));
    }

    @Test
    void hutaoWithoutTheConstellationDoesNotGainItsBuff() throws Exception {
        character("Hutao", false);
        float baseline = critical();
        entity.damage(80);
        assertEquals(20, hp());
        assertEquals(baseline, critical());
    }

    @Test
    void shinobuLowHealthStartsOnlyItsMasteryCooldown() throws Exception {
        character("Shinobu", true);
        float baseline = mastery();
        entity.damage(80);
        assertEquals(20, hp());
        assertEquals(baseline + 150, mastery(), 0.0001f);
        assertEquals(0, pending(10656));
        assertEquals(1, pending(10657));
    }

    @Test
    void shinobuLethalHitStartsTheTwoIndependentCooldowns() throws Exception {
        character("Shinobu", true);
        float baseline = mastery();
        entity.damage(120);
        assertEquals(1, hp());
        assertTrue(entity.isAlive());
        assertEquals(baseline + 150, mastery(), 0.0001f);
        assertEquals(1, pending(10656));
        assertEquals(1, pending(10657));
        assertFalse(AbilityCombatState.read(entity).limbo());
    }

    @Test
    void shinobuMasteryExpiresAtTheResourceDuration() throws Exception {
        character("Shinobu", true);
        float baseline = mastery();
        entity.damage(80);
        advance(14999);
        assertEquals(baseline + 150, mastery(), 0.0001f);
        advance(1);
        assertEquals(baseline, mastery(), 0.0001f);
    }

    @Test
    void shinobuProtectionStillWorksWhileMasteryIsOnCooldown() throws Exception {
        character("Shinobu", true);
        float baseline = mastery();
        entity.damage(80);
        advance(1000);
        entity.damage(100);
        assertEquals(1, hp());
        assertTrue(entity.isAlive());
        assertEquals(baseline + 150, mastery(), 0.0001f);
        assertEquals(1, pending(10656));
        assertEquals(1, pending(10657));
        advance(14000);
        assertEquals(baseline, mastery(), 0.0001f);
    }

    @Test
    void removingTheMainAbilityStopsFurtherEventsAndProtection() throws Exception {
        character("Hutao", true);
        main.removeInstalledModifiers();
        entity.getInstancedAbilities().remove(main);
        entity.damage(80);
        assertEquals(20, hp());
        assertEquals(0, pending(10464));
        assertFalse(AbilityCombatState.read(entity).limbo());
    }

    @Test
    void periodicProtectionIsRemovedWhenTheRealTriggerRuns() throws Exception {
        character("Hutao", true);
        advance(2000);
        assertTrue(AbilityCombatState.read(entity).limbo());
        entity.damage(80);
        assertEquals(1, pending(10464));
        assertFalse(AbilityCombatState.read(entity).limbo());
    }

    @Test
    void hutaoRepeatedEventsDoNotCreateExtraTriggeredAbilityInstances() throws Exception {
        character("Hutao", true);
        entity.damage(80);
        advance(60000);
        entity.damage(1);
        assertEquals(
                1,
                entity.getInstancedAbilities().stream()
                        .filter(
                                a ->
                                        "Avatar_Hutao_Constellation_Limbo_Trigger"
                                                .equals(a.getData().abilityName))
                        .count());
    }
}
