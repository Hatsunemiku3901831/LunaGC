package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import com.google.protobuf.ByteString;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.ResourceLoader.*;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.data.excels.avatar.*;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.PlayerDieTypeOuterClass.PlayerDieType;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.utils.JsonUtils;

import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

class BarbaraAutomaticRevivalTest {
    AbilityTestFixture fixture;
    Member fallen, barbara;
    AvatarSkillDepotData depot;
    AbilityData pre, revive;
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
        fixture.player.getSession().setPlayer(fixture.player);
        var field = AbilityManager.class.getDeclaredField("skillCooldown");
        field.setAccessible(true);
        field.set(
                fixture.player.getAbilityManager(),
                new AbilitySkillCooldown(fixture.player, now::get));
        var excel = Path.of("resources/ExcelBinOutput");
        for (var skill :
                JsonUtils.loadToList(
                        excel.resolve("AvatarSkillExcelConfigData.json"), AvatarSkillData.class))
            if (skill.getId() == 10076 || skill.getId() == 10072)
                put(GameData.getAvatarSkillDataMap(), skill.getId(), skill);
        depot =
                JsonUtils.loadToList(
                                excel.resolve("AvatarSkillDepotExcelConfigData.json"),
                                AvatarSkillDepotData.class)
                        .stream()
                        .filter(row -> row.getId() == 1401)
                        .findFirst()
                        .orElseThrow();
        depot.onLoad();
        put(GameData.getAvatarSkillDepotDataMap(), depot.getId(), depot);
        var talent =
                JsonUtils.loadToList(
                                excel.resolve("AvatarTalentExcelConfigData.json"),
                                AvatarTalentData.class)
                        .stream()
                        .filter(row -> row.getId() == 146)
                        .findFirst()
                        .orElseThrow();
        put(GameData.getAvatarTalentDataMap(), talent.getId(), talent);
        var configs =
                JsonParser.parseString(
                                Files.readString(
                                        Path.of(
                                                "resources/BinOutput/Talent/AvatarTalents/ConfigTalent_Barbara.json")))
                        .getAsJsonObject();
        put(
                GameData.getOpenConfigEntries(),
                talent.getOpenConfig(),
                new OpenConfigEntry(
                        talent.getOpenConfig(),
                        JsonUtils.decode(
                                configs.get(talent.getOpenConfig()), OpenConfigData[].class)));
        for (var row :
                JsonUtils.loadToList(
                        Path.of(
                                "resources/BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_Barbara.json"),
                        AbilityConfigData.class)) {
            var data = row.Default;
            if (data.abilityName.startsWith("Avatar_Barbara_ReBorn"))
                put(GameData.getAbilityDataMap(), data.abilityName, data);
        }
        pre = GameData.getAbilityData("Avatar_Barbara_ReBorn_Pre");
        revive = GameData.getAbilityData("Avatar_Barbara_ReBorn");
        fallen = member(70, false);
        barbara = member(80, true);
        talents(barbara).add(146);
    }

    @AfterEach
    void close() {
        Collections.reverse(restore);
        restore.forEach(Runnable::run);
        fixture.close();
    }

    @SuppressWarnings("unchecked")
    Set<Integer> talents(Member entity) throws Exception {
        var field = emu.grasscutter.game.avatar.Avatar.class.getDeclaredField("talentIdList");
        field.setAccessible(true);
        return (Set<Integer>) field.get(entity.getAvatar());
    }

    class Member extends EntityAvatar {
        int deaths;
        Runnable onDeathCallback;

        Member(emu.grasscutter.game.avatar.Avatar avatar) {
            super(fixture.scene, avatar);
        }

        @Override
        public Position getPosition() {
            return new Position();
        }

        @Override
        public void onDeath(int id) {
            deaths++;
            super.onDeath(id);
            if (onDeathCallback != null) onDeathCallback.run();
        }

        @Override
        public void onDeath(PlayerDieType type, int id) {
            deaths++;
            super.onDeath(type, id);
            if (onDeathCallback != null) onDeathCallback.run();
        }
    }

    Member member(float hp, boolean isBarbara) {
        var selected =
                isBarbara
                        ? depot
                        : JsonUtils.decode(
                                "{id:71001,skills:[71001],energySkill:71002,subSkills:[],talents:[],inherentProudSkillOpens:[]}",
                                AvatarSkillDepotData.class);
        selected.onLoad();
        var avatar =
                new SkillTalentLevelTest.RecordingAvatar(
                        fixture.player, isBarbara ? 10000014 : 10000073, selected);
        avatar.getFightProperties().put(FightProperty.FIGHT_PROP_MAX_HP.getId(), 100f);
        avatar.getFightProperties().put(FightProperty.FIGHT_PROP_CUR_HP.getId(), hp);
        var result = new Member(avatar);
        fixture.player.getTeamManager().getActiveTeam().add(result);
        fixture.scene.getEntities().put(result.getId(), result);
        return result;
    }

    float hp(Member entity) {
        return entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
    }

    void kill(Member entity) {
        entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 0);
        fixture.scene.killEntity(entity);
    }

    int cooldowns() {
        return fixture.player
                .getAbilityManager()
                .getSkillCooldown()
                .state(barbara.getAvatar(), 10076, 1)
                .getFullCdTimeListCount();
    }

    void nativeAction(int index) {
        var source = new Ability(revive, barbara, fixture.player);
        fixture.player
                .getAbilityManager()
                .executeActionNow(source, revive.onAbilityStart[index], ByteString.EMPTY, barbara);
    }

    @Test
    void actualSixthConstellationRevivesOnlyTheJustFallenMemberAtFullHealth() {
        var previous = member(0, false);
        var living = member(37, false);
        kill(fallen);
        assertTrue(fallen.isAlive());
        assertEquals(100, hp(fallen));
        assertEquals(0, hp(previous));
        assertEquals(37, hp(living));
        assertEquals(80, hp(barbara));
        assertEquals(1, cooldowns());
        assertEquals(
                900000,
                fixture.player
                        .getAbilityManager()
                        .getSkillCooldown()
                        .state(barbara.getAvatar(), 10076, 1)
                        .getFullCdTimeList(0));
    }

    @Test
    void settlesDeathAndClearsEnergyOnceWithoutRemovingTheRevivedEntity() {
        fallen.setFightProperty(fallen.GetEnergyProp(fallen.getAvatar()), 60);
        kill(fallen);
        assertTrue(fallen.isAlive());
        assertEquals(1, fallen.deaths);
        assertSame(fallen, fixture.scene.getEntities().get(fallen.getId()));
        assertEquals(0, fallen.getFightProperty(fallen.GetEnergyProp(fallen.getAvatar())));
        assertEquals(PlayerDieType.PlayerDieType_PLAYER_DIE_NONE, fallen.getKilledType());
        assertEquals(0, fallen.getKilledBy());
        assertFalse(
                fixture.scene.packets.stream()
                        .anyMatch(p -> p.getOpcode() == PacketOpcodes.SceneEntityDisappearNotify));
    }

    @Test
    void cannotTriggerAgainUntilTheExactResourceCooldownBoundary() {
        kill(fallen);
        assertEquals(100, hp(fallen));
        now.set(900999);
        kill(fallen);
        assertEquals(0, hp(fallen));
        assertEquals(1, cooldowns());
        // A new fallen party member at the boundary is eligible, without reviving old deaths.
        var later = member(20, false);
        now.set(901000);
        kill(later);
        assertEquals(100, hp(later));
        assertEquals(0, hp(fallen));
    }

    @Test
    void ordinaryUnrelatedSkillCooldownDoesNotBlockTheRevivalSkill() {
        fixture.player.getAbilityManager().getSkillCooldown().start(barbara.getAvatar(), 10072, 60);
        kill(fallen);
        assertEquals(100, hp(fallen));
        assertEquals(1, cooldowns());
    }

    @Test
    void missingConstellationDoesNotReviveOrSpendCooldown() throws Exception {
        talents(barbara).clear();
        kill(fallen);
        assertEquals(0, hp(fallen));
        assertEquals(0, cooldowns());
        assertEquals(1, fallen.deaths);
    }

    @Test
    void onFieldBarbaraCannotRescueAnotherPartyMember() {
        fixture.player.getTeamManager().setCurrentCharacterIndex(1);
        kill(fallen);
        assertEquals(0, hp(fallen));
        assertEquals(0, cooldowns());
    }

    @Test
    void barbaraCannotReviveHerself() {
        kill(barbara);
        assertEquals(0, hp(barbara));
        assertEquals(0, cooldowns());
    }

    @Test
    void zeroHealthBarbaraCannotRescueBeforeHerDeathFlagIsProcessed() {
        barbara.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 0);
        assertTrue(barbara.isAlive());
        kill(fallen);
        assertEquals(0, hp(fallen));
        assertEquals(0, cooldowns());
    }

    @Test
    void unselectedBarbaraCannotRevive() {
        fixture.player.getTeamManager().getActiveTeam().remove(barbara);
        kill(fallen);
        assertEquals(0, hp(fallen));
        assertEquals(0, cooldowns());
    }

    @Test
    void removedSourceCannotRevive() {
        fixture.scene.removeEntity(barbara);
        kill(fallen);
        assertEquals(0, hp(fallen));
        assertEquals(0, cooldowns());
    }

    @Test
    void unselectedFallenAvatarCannotConsumeSelectedPartyRevival() {
        fixture.player.getTeamManager().getActiveTeam().remove(fallen);
        kill(fallen);
        assertEquals(0, hp(fallen));
        assertEquals(0, cooldowns());
    }

    @Test
    void invalidMaximumHealthDoesNotConsumeCooldown() {
        fallen.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 0);
        kill(fallen);
        assertEquals(0, hp(fallen));
        assertEquals(0, cooldowns());
    }

    @Test
    void missingHealSpecialDoesNotConsumeCooldown() {
        revive.abilitySpecials.remove("HealHP");
        kill(fallen);
        assertEquals(0, hp(fallen));
        assertEquals(0, cooldowns());
    }

    @Test
    void sharedCooldownProfileIsNotGuessed() {
        var skill =
                JsonUtils.decode(
                        "{id:10076,abilityName:'Avatar_Barbara_ReBorn',cdTime:900,shareCDID:1,maxChargeNum:1}",
                        AvatarSkillData.class);
        put(GameData.getAvatarSkillDataMap(), 10076, skill);
        kill(fallen);
        assertEquals(0, hp(fallen));
        assertEquals(0, cooldowns());
    }

    @Test
    void deathCallbackChangingPartyInvalidatesPendingAutomaticRevival() {
        fallen.onDeathCallback =
                () -> fixture.player.getTeamManager().getActiveTeam().remove(barbara);
        kill(fallen);
        assertEquals(0, hp(fallen));
        assertEquals(0, cooldowns());
        assertEquals(1, fallen.deaths);
    }

    @Test
    void deathCallbackRevivalDoesNotSpendBarbaraCooldown() {
        fallen.onDeathCallback = () -> fallen.revive(20);
        kill(fallen);
        assertEquals(20, hp(fallen));
        assertEquals(0, cooldowns());
        assertEquals(1, fallen.deaths);
    }

    @Test
    void unsolicitedNativeRevivalCannotBypassConstellation() throws Exception {
        talents(barbara).clear();
        var previous = member(0, false);
        nativeAction(0);
        nativeAction(2);
        assertEquals(0, hp(previous));
        assertEquals(0, cooldowns());
    }

    @Test
    void delayedNativeConfirmationCannotReviveNewDeathsOrExtendCooldown() {
        kill(fallen);
        assertEquals(100, hp(fallen));
        var previous = member(0, false);
        now.set(2000);
        nativeAction(0);
        nativeAction(2);
        assertEquals(0, hp(previous));
        assertEquals(
                1000,
                fixture.player
                        .getAbilityManager()
                        .getSkillCooldown()
                        .state(barbara.getAvatar(), 10076, 1)
                        .getPassCdTime());
    }

    @Test
    void drowningUsesTheSameAutomaticRevivalAndSettlesDeathOnce() {
        fixture.player
                .getStaminaManager()
                .killAvatar(
                        fixture.player.getSession(),
                        fallen,
                        PlayerDieType.PlayerDieType_PLAYER_DIE_DRAWN);
        assertEquals(100, hp(fallen));
        assertTrue(fallen.isAlive());
        assertEquals(1, fallen.deaths);
        assertEquals(1, cooldowns());
        assertSame(fallen, fixture.scene.getEntities().get(fallen.getId()));
    }

    @Test
    void canonicalAndObfuscatedRevivalCallbacksArePreservedSeparately() {
        var tree = JsonParser.parseString(JsonUtils.encode(pre.abilityMixins[0])).getAsJsonObject();
        assertTrue(tree.has("$type"), "revival mixin type must survive decoding");
        assertEquals("DoReviveMixin", tree.get("$type").getAsString());
        assertEquals("OffStage", tree.get("type").getAsString());
        assertEquals(2, tree.getAsJsonArray("onKillActions").size());
        assertEquals(2, tree.getAsJsonArray("HCFAFEALFCI").size());
        assertEquals(3, tree.getAsJsonArray("onReviveActions").size());
        assertEquals(3, tree.getAsJsonArray("MMLFABGAGIK").size());
    }

    @Test
    void unknownRevivalModeRemainsDormant() {
        pre.abilityMixins[0].eventExecutionType = "Unknown";
        kill(fallen);
        assertEquals(0, hp(fallen));
        assertEquals(0, cooldowns());
    }

    @Test
    void unmatchedUnlockedSkillRemainsDormant() {
        pre.onAdded[0].skillID = 10079;
        kill(fallen);
        assertEquals(0, hp(fallen));
        assertEquals(0, cooldowns());
    }

    @Test
    void missingGrantedPreparationAbilityRemainsDormant() {
        put(
                GameData.getOpenConfigEntries(),
                "Barbara_Constellation_6",
                new OpenConfigEntry(
                        "Barbara_Constellation_6",
                        JsonUtils.decode(
                                "[{$type:'AddAbility',abilityName:'Avatar_Barbara_ReBorn'}]",
                                OpenConfigData[].class)));
        kill(fallen);
        assertEquals(0, hp(fallen));
        assertEquals(0, cooldowns());
    }

    @Test
    void cooldownDurationIsReadFromSkillResources() {
        put(
                GameData.getAvatarSkillDataMap(),
                10076,
                JsonUtils.decode(
                        "{id:10076,abilityName:'Avatar_Barbara_ReBorn',cdTime:60,maxChargeNum:1,ignoreCDMinusRatio:true}",
                        AvatarSkillData.class));
        kill(fallen);
        assertEquals(100, hp(fallen));
        assertEquals(
                60000,
                fixture.player
                        .getAbilityManager()
                        .getSkillCooldown()
                        .state(barbara.getAvatar(), 10076, 1)
                        .getFullCdTimeList(0));
    }

    @Test
    void sessionReplacementInDeathCallbackInvalidatesPendingRevival() {
        fallen.onDeathCallback = () -> fixture.player.setSession(new GameSession(null));
        kill(fallen);
        assertEquals(0, hp(fallen));
        assertEquals(0, cooldowns());
        assertEquals(1, fallen.deaths);
    }
}
