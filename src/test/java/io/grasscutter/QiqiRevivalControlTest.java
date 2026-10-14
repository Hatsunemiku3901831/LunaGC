package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import com.google.protobuf.ByteString;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.ResourceLoader.*;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.data.excels.avatar.*;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

class QiqiRevivalControlTest {
    AbilityTestFixture fixture;
    EntityAvatar caster;
    Ability ability;
    AbilityModifierAction action;
    AvatarSkillDepotData depot;
    final AtomicLong now = new AtomicLong(1000);
    final List<Runnable> restore = new ArrayList<>();

    <K, V> void put(Map<K, V> map, K key, V value) {
        var old = map.put(key, value);
        restore.add(
                () -> {
                    if (old == null) map.remove(key);
                    else map.put(key, old);
                });
    }

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        fixture.player.setWorld(fixture.scene.getWorld());
        fixture.player.setSession(new GameSession(null));
        var cooldown =
                fixture.player.getAbilityManager().getClass().getDeclaredField("skillCooldown");
        cooldown.setAccessible(true);
        cooldown.set(
                fixture.player.getAbilityManager(),
                new AbilitySkillCooldown(fixture.player, now::get));
        Path excel = Path.of("resources/ExcelBinOutput");
        var skill =
                JsonUtils.loadToList(
                                excel.resolve("AvatarSkillExcelConfigData.json"),
                                AvatarSkillData.class)
                        .stream()
                        .filter(row -> row.getId() == 10355)
                        .findFirst()
                        .orElseThrow();
        put(GameData.getAvatarSkillDataMap(), skill.getId(), skill);
        depot =
                JsonUtils.loadToList(
                                excel.resolve("AvatarSkillDepotExcelConfigData.json"),
                                AvatarSkillDepotData.class)
                        .stream()
                        .filter(row -> row.getId() == 3501)
                        .findFirst()
                        .orElseThrow();
        depot.onLoad();
        put(GameData.getAvatarSkillDepotDataMap(), depot.getId(), depot);
        var talent =
                JsonUtils.loadToList(
                                excel.resolve("AvatarTalentExcelConfigData.json"),
                                AvatarTalentData.class)
                        .stream()
                        .filter(row -> row.getId() == 356)
                        .findFirst()
                        .orElseThrow();
        put(GameData.getAvatarTalentDataMap(), talent.getId(), talent);
        var configs =
                JsonParser.parseString(
                                Files.readString(
                                        Path.of(
                                                "resources/BinOutput/Talent/AvatarTalents/ConfigTalent_Qiqi.json")))
                        .getAsJsonObject();
        put(
                GameData.getOpenConfigEntries(),
                "Qiqi_Constellation_6",
                new OpenConfigEntry(
                        "Qiqi_Constellation_6",
                        JsonUtils.decode(
                                configs.get("Qiqi_Constellation_6"), OpenConfigData[].class)));
        var rows =
                JsonUtils.loadToList(
                        Path.of(
                                "resources/BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_Qiqi.json"),
                        AbilityConfigData.class);
        var burst =
                rows.stream()
                        .map(row -> row.Default)
                        .filter(row -> row.abilityName.equals("Avatar_Qiqi_ElementalBurst"))
                        .findFirst()
                        .orElseThrow();
        var revive =
                rows.stream()
                        .map(row -> row.Default)
                        .filter(row -> row.abilityName.equals(skill.getAbilityName()))
                        .findFirst()
                        .orElseThrow();
        put(GameData.getAbilityDataMap(), burst.abilityName, burst);
        put(GameData.getAbilityDataMap(), revive.abilityName, revive);
        caster = member(100, 0, true);
        talents(caster).add(356);
        ability = new Ability(burst, caster, fixture.player);
        action = burst.onAbilityStart[3];
    }

    @AfterEach
    void close() {
        Collections.reverse(restore);
        restore.forEach(Runnable::run);
        fixture.close();
    }

    @SuppressWarnings("unchecked")
    Set<Integer> talents(EntityAvatar entity) {
        try {
            var field = emu.grasscutter.game.avatar.Avatar.class.getDeclaredField("talentIdList");
            field.setAccessible(true);
            return (Set<Integer>) field.get(entity.getAvatar());
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    EntityAvatar member(float health, float x, boolean qiqi) {
        var selectedDepot =
                qiqi
                        ? depot
                        : JsonUtils.decode(
                                "{id:71001,skills:[71001],energySkill:71002,subSkills:[],talents:[],inherentProudSkillOpens:[]}",
                                AvatarSkillDepotData.class);
        selectedDepot.onLoad();
        var avatar =
                new SkillTalentLevelTest.RecordingAvatar(
                        fixture.player, qiqi ? 10000035 : 10000073, selectedDepot);
        avatar.getFightProperties().put(FightProperty.FIGHT_PROP_MAX_HP.getId(), 100f);
        avatar.getFightProperties().put(FightProperty.FIGHT_PROP_CUR_HP.getId(), health);
        var entity =
                new EntityAvatar(fixture.scene, avatar) {
                    final Position position = new Position(x, 0, 0);

                    @Override
                    public void initAbilities() {}

                    @Override
                    public Position getPosition() {
                        return position;
                    }
                };
        entity.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 100);
        entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, health);
        entity.checkIfDead();
        fixture.player.getTeamManager().getActiveTeam().add(entity);
        fixture.scene.getEntities().put(entity.getId(), entity);
        return entity;
    }

    void invoke() {
        fixture.player
                .getAbilityManager()
                .executeActionNow(ability, action, ByteString.EMPTY, caster);
    }

    int cooldowns() {
        return fixture.player
                .getAbilityManager()
                .getSkillCooldown()
                .state(caster.getAvatar(), 10355, 1)
                .getFullCdTimeListCount();
    }

    float health(EntityAvatar entity) {
        return entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
    }

    void sequence() throws Exception {
        var clazz = Class.forName("emu.grasscutter.game.ability.AbilityActionSequence");
        var method =
                clazz.getDeclaredMethod(
                        "run",
                        Ability.class,
                        AbilityModifierAction[].class,
                        GameEntity.class,
                        java.util.function.BooleanSupplier.class);
        method.setAccessible(true);
        method.invoke(
                null,
                ability,
                new AbilityModifierAction[] {action},
                caster,
                (java.util.function.BooleanSupplier) () -> true);
    }

    @Test
    void actualSixthConstellationRevivesAllNearbyFallenMembersAtHalfHealth() {
        assertTrue(caster.isAlive());
        assertTrue(AbilityAvatarRevival.supported(action), "resource control support");
        assertTrue(
                AbilitySkillCooldown.ownsSkill(caster.getAvatar(), 10355),
                "caster skill ownership");
        assertTrue(
                AbilityAvatarRevival.eligible(ability, action, caster),
                "caster scene and lifetime");
        assertTrue(
                AbilityTalentResolver.unlocked(
                        caster.getAvatar(), ability.getData().abilityName, "Qiqi_Constellation_6"),
                "resource constellation binding");
        var first = member(0, 1, false);
        var second = member(0, 2, false);
        var alive = member(73, 3, false);
        invoke();
        assertEquals(50, health(first));
        assertEquals(50, health(second));
        assertEquals(73, health(alive));
        assertEquals(1, cooldowns());
        assertEquals(
                900000,
                fixture.player
                        .getAbilityManager()
                        .getSkillCooldown()
                        .state(caster.getAvatar(), 10355, 1)
                        .getFullCdTimeList(0));
    }

    @Test
    void aSecondCastCannotUseTheDeadRecipientsMissingSkillInstanceToBypassCasterCooldown() {
        var dead = member(0, 1, false);
        invoke();
        assertEquals(50, health(dead));
        var later = member(0, 1, false);
        invoke();
        assertEquals(0, health(later));
    }

    @Test
    void reviveCooldownExpiresAtExactlyFifteenMinutes() {
        member(0, 1, false);
        invoke();
        var later = member(0, 1, false);
        now.set(900999);
        invoke();
        assertEquals(0, health(later));
        now.set(901000);
        invoke();
        assertEquals(50, health(later));
        assertEquals(1, cooldowns());
    }

    @Test
    void noFallenMembersDoesNotSpendRevivalCooldown() {
        invoke();
        assertEquals(0, cooldowns());
    }

    @Test
    void noSixthConstellationPreventsRevivalAndCooldown() {
        talents(caster).clear();
        var dead = member(0, 1, false);
        invoke();
        assertEquals(0, health(dead));
        assertEquals(0, cooldowns());
    }

    @Test
    void recipientsConstellationCannotUnlockTheCaster() {
        talents(caster).clear();
        var dead = member(0, 1, true);
        talents(dead).add(356);
        invoke();
        assertEquals(0, health(dead));
        assertEquals(0, cooldowns());
    }

    @Test
    void exactResourceRangeIncludesBoundaryAndExcludesDistantAvatar() {
        var boundary = member(0, 40, false);
        var far = member(0, 40.1f, false);
        invoke();
        assertEquals(50, health(boundary));
        assertEquals(0, health(far));
        assertEquals(1, cooldowns());
    }

    @Test
    void onlyDistantDeadMembersDoesNotSpendCooldown() {
        member(0, 41, false);
        invoke();
        assertEquals(0, cooldowns());
    }

    @Test
    void cooldownDurationComesFromResourceSkillData() {
        var changed =
                JsonUtils.decode(
                        "{id:10355,abilityName:'Avatar_Qiqi_Revive',cdTime:123.5,maxChargeNum:1,ignoreCDMinusRatio:true}",
                        AvatarSkillData.class);
        put(GameData.getAvatarSkillDataMap(), 10355, changed);
        member(0, 1, false);
        invoke();
        assertEquals(
                123500,
                fixture.player
                        .getAbilityManager()
                        .getSkillCooldown()
                        .state(caster.getAvatar(), 10355, 1)
                        .getFullCdTimeList(0));
    }

    @Test
    void configuredCooldownRatioIsResolvedOnceForTheWholeParty() {
        action.legacyCdRatio = new emu.grasscutter.data.common.DynamicFloat(0.5f);
        member(0, 1, false);
        member(0, 2, false);
        invoke();
        assertEquals(
                450000,
                fixture.player
                        .getAbilityManager()
                        .getSkillCooldown()
                        .state(caster.getAvatar(), 10355, 1)
                        .getFullCdTimeList(0));
    }

    @Test
    void controlledCallbackKeepsTheDefaultPartyScope() throws Exception {
        var first = member(0, 1, false);
        var second = member(0, 2, false);
        sequence();
        assertEquals(50, health(first));
        assertEquals(50, health(second));
        assertEquals(1, cooldowns());
    }

    @Test
    void explicitTeamCallbackDoesNotStopAfterRevivingTheFirstMember() throws Exception {
        action.target = "CurTeamAvatars";
        var first = member(0, 1, false);
        var second = member(0, 2, false);
        sequence();
        assertEquals(50, health(first));
        assertEquals(50, health(second));
        assertEquals(1, cooldowns());
    }

    @Test
    void directAndControlledConfirmationsDoNotRefreshTheCooldown() throws Exception {
        var dead = member(0, 1, false);
        invoke();
        assertEquals(50, health(dead));
        now.addAndGet(5000);
        sequence();
        invoke();
        assertEquals(
                5000,
                fixture.player
                        .getAbilityManager()
                        .getSkillCooldown()
                        .state(caster.getAvatar(), 10355, 1)
                        .getPassCdTime());
    }

    @Test
    void genericRevivalDoesNotSpendQiqisCooldown() {
        var dead = member(0, 1, false);
        fixture.player
                .getAbilityManager()
                .executeActionNow(
                        ability,
                        JsonUtils.decode(
                                "{$type:'ReviveDeadAvatar',amountByTargetMaxHPRatio:1}",
                                AbilityModifierAction.class),
                        ByteString.EMPTY,
                        caster);
        assertEquals(100, health(dead));
        assertEquals(0, cooldowns());
    }

    @Test
    void malformedHealthDoesNotSpendCooldown() {
        action.amountByTargetMaxHPRatio =
                new emu.grasscutter.data.common.DynamicFloat("MissingHeal");
        var dead = member(0, 1, false);
        invoke();
        assertEquals(0, health(dead));
        assertEquals(0, cooldowns());
    }

    @Test
    void missingSkillOwnershipPreventsControlledRevival() {
        var foreign = member(100, 0, false);
        talents(foreign).add(356);
        ability = new Ability(ability.getData(), foreign, fixture.player);
        var dead = member(0, 1, false);
        invoke();
        assertEquals(0, health(dead));
        assertEquals(0, cooldowns());
    }

    @Test
    void missingSkillMetadataKeepsTheActionDormant() {
        GameData.getAvatarSkillDataMap().remove(10355);
        var dead = member(0, 1, false);
        invoke();
        assertEquals(0, health(dead));
        assertEquals(0, cooldowns());
    }

    @Test
    void removedCasterCannotReviveOrSpendCooldown() {
        var dead = member(0, 1, false);
        caster.invalidateAbilityLifecycle();
        invoke();
        assertEquals(0, health(dead));
        assertEquals(0, cooldowns());
    }

    @Test
    void invalidatedRecipientDoesNotSpendCooldown() {
        var dead = member(0, 1, false);
        dead.invalidateAbilityLifecycle();
        invoke();
        assertEquals(0, health(dead));
        assertEquals(0, cooldowns());
    }

    @Test
    void repeatedNativeSkillStartDoesNotExtendTheAlreadyRecordedCooldown() {
        member(0, 1, false);
        invoke();
        now.addAndGet(5000);
        var skill = GameData.getAbilityData("Avatar_Qiqi_Revive");
        fixture.player
                .getAbilityManager()
                .executeActionNow(
                        new Ability(skill, caster, fixture.player),
                        skill.onAbilityStart[0],
                        ByteString.EMPTY,
                        caster);
        assertEquals(
                5000,
                fixture.player
                        .getAbilityManager()
                        .getSkillCooldown()
                        .state(caster.getAvatar(), 10355, 1)
                        .getPassCdTime());
    }

    @Test
    void newSessionClearsThePreviousSessionsRevivalCooldown() {
        member(0, 1, false);
        invoke();
        fixture.player.setSession(new GameSession(null));
        var dead = member(0, 1, false);
        invoke();
        assertEquals(50, health(dead));
        assertEquals(1, cooldowns());
    }

    @Test
    void recreatedCasterKeepsTheSameAvatarCooldown() {
        member(0, 1, false);
        invoke();
        var dead = member(0, 1, false);
        var replacement =
                new EntityAvatar(fixture.scene, caster.getAvatar()) {
                    @Override
                    public void initAbilities() {}

                    @Override
                    public Position getPosition() {
                        return new Position();
                    }
                };
        fixture.player.getTeamManager().getActiveTeam().set(0, replacement);
        fixture.scene.getEntities().remove(caster.getId());
        fixture.scene.getEntities().put(replacement.getId(), replacement);
        ability = new Ability(ability.getData(), replacement, fixture.player);
        invoke();
        assertEquals(0, health(dead));
        assertEquals(1, cooldowns());
    }

    @Test
    void parallelCastConfirmationsOnlyReviveOnce() throws Exception {
        var dead = member(0, 1, false);
        fixture.player.packets.clear();
        var pool = Executors.newFixedThreadPool(2);
        var gate = new CountDownLatch(1);
        try {
            var first =
                    pool.submit(
                            () -> {
                                gate.await();
                                invoke();
                                return null;
                            });
            var second =
                    pool.submit(
                            () -> {
                                gate.await();
                                invoke();
                                return null;
                            });
            gate.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(50, health(dead));
        assertEquals(1, cooldowns());
        assertEquals(
                1,
                fixture.player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.AvatarFightPropUpdateNotify)
                        .count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "'true'", "1", "{}"})
    void malformedControlDoesNotReviveOrSpendCooldown(String value) {
        action.rawReviveSkillControl = JsonParser.parseString(value);
        var dead = member(0, 1, false);
        invoke();
        assertEquals(0, health(dead));
        assertEquals(0, cooldowns());
    }

    @Test
    void nonzeroSkillWithoutVerifiedControlStaysDormant() {
        action.rawReviveSkillControl = null;
        var dead = member(0, 1, false);
        invoke();
        assertEquals(0, health(dead));
        assertEquals(0, cooldowns());
    }

    @Test
    void ordinarySkillMustNotBeSubstitutedForTheRevivalSkill() {
        action.skillID = 10353;
        var dead = member(0, 1, false);
        invoke();
        assertEquals(0, health(dead));
        assertEquals(0, cooldowns());
    }

    @Test
    void actionUpdatesTheCastersCooldownWithoutChangingRecipientsSkills() {
        var dead = member(0, 1, false);
        var before = dead.getAvatar().getSkillInfoCharges();
        invoke();
        assertEquals(50, health(dead));
        assertEquals(before, dead.getAvatar().getSkillInfoCharges());
        assertEquals(1, cooldowns());
    }

    Ability nativeReviveAbility() {
        return new Ability(GameData.getAbilityData("Avatar_Qiqi_Revive"), caster, fixture.player);
    }

    void nativeAction(Ability source, int index) {
        fixture.player
                .getAbilityManager()
                .executeActionNow(
                        source, source.getData().onAbilityStart[index], ByteString.EMPTY, caster);
    }

    @Test
    void nativeSecondaryAbilityUsesTheSameRangeAndOneCasterCooldown() {
        var dead = member(0, 1, false);
        var far = member(0, 41, false);
        var source = nativeReviveAbility();
        nativeAction(source, 0);
        nativeAction(source, 2);
        assertEquals(50, health(dead));
        assertEquals(0, health(far));
        assertEquals(1, cooldowns());
    }

    @Test
    void nativeSecondaryAbilityCannotReviveWithoutSixthConstellation() {
        talents(caster).clear();
        var dead = member(0, 1, false);
        var source = nativeReviveAbility();
        nativeAction(source, 0);
        nativeAction(source, 2);
        assertEquals(0, health(dead));
        assertEquals(0, cooldowns());
    }

    @Test
    void nativeSecondaryConfirmationCannotHealANewlyFallenAvatarDuringCooldown() {
        var dead = member(0, 1, false);
        invoke();
        assertEquals(50, health(dead));
        var later = member(0, 1, false);
        var source = nativeReviveAbility();
        nativeAction(source, 0);
        nativeAction(source, 2);
        assertEquals(0, health(later));
        assertEquals(1, cooldowns());
    }

    @Test
    void nativeSecondaryAbilityWithNoDeadMembersDoesNotConsumeCooldown() {
        var source = nativeReviveAbility();
        nativeAction(source, 0);
        nativeAction(source, 2);
        assertEquals(0, cooldowns());
    }

    @Test
    void nativeSecondaryCallbacksCanArriveInEitherOrder() {
        var dead = member(0, 1, false);
        var source = nativeReviveAbility();
        nativeAction(source, 2);
        assertEquals(0, health(dead));
        nativeAction(source, 0);
        assertEquals(50, health(dead));
        assertEquals(1, cooldowns());
    }

    @Test
    void nativeSecondaryRevivalRequiresTheMatchingSkillStart() {
        var dead = member(0, 1, false);
        nativeAction(nativeReviveAbility(), 2);
        assertEquals(0, health(dead));
        assertEquals(0, cooldowns());
    }

    @Test
    void nativeSecondaryIntentDoesNotSurviveSessionReplacement() {
        var dead = member(0, 1, false);
        var source = nativeReviveAbility();
        nativeAction(source, 0);
        fixture.player.setSession(new GameSession(null));
        nativeAction(source, 2);
        assertEquals(0, health(dead));
        assertEquals(0, cooldowns());
    }

    @Test
    void zeroHealthCasterCannotReviveBeforeTheDeathFlagIsProcessed() {
        var dead = member(0, 1, false);
        caster.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 0);
        assertTrue(caster.isAlive());
        invoke();
        assertEquals(0, health(dead));
        assertEquals(0, cooldowns());
    }
}
