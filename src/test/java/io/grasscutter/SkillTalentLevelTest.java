package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;

import emu.grasscutter.data.*;
import emu.grasscutter.data.ResourceLoader.OpenConfigData;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.data.excels.ProudSkillData;
import emu.grasscutter.data.excels.avatar.*;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.AbilityInvocationsNotifyOuterClass.AbilityInvocationsNotify;
import emu.grasscutter.net.proto.AbilityScalarValueEntryOuterClass.AbilityScalarValueEntry;
import emu.grasscutter.net.proto.AvatarSkillChangeNotifyOuterClass.AvatarSkillChangeNotify;
import emu.grasscutter.net.proto.AvatarSkillUpgradeRspOuterClass.AvatarSkillUpgradeRsp;
import emu.grasscutter.net.proto.EvtCreateGadgetNotifyOuterClass.EvtCreateGadgetNotify;
import emu.grasscutter.utils.JsonUtils;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.util.*;

/** Real talent rows, actual avatar level changes and serialized ability updates. */
class SkillTalentLevelTest {
    private static final Path EXCEL = Path.of("resources/ExcelBinOutput");
    private static List<AvatarSkillData> skills;
    private static List<ProudSkillData> proudSkills;
    private static List<AvatarTalentData> constellations;
    private static AvatarSkillDepotData depot;
    private static AvatarSkillDepotData arlecchinoDepot;
    private static final Map<String, OpenConfigEntry> configs = new LinkedHashMap<>();
    private final List<Runnable> restore = new ArrayList<>();
    private AbilityTestFixture fixture;
    private RecordingPlayer player;
    private RecordingAvatar avatar;
    private EntityAvatar entity;
    private static final String ART = "Avatar_Nahida_ElementalArt_Click";
    private static final String BURST = "Avatar_Nahida_ElementalBurst_Hollow_Gadget";

    @BeforeAll
    static void resources() throws Exception {
        skills =
                JsonUtils.loadToList(
                                EXCEL.resolve("AvatarSkillExcelConfigData.json"),
                                AvatarSkillData.class)
                        .stream()
                        .filter(
                                s ->
                                        Set.of(10731, 10732, 10735, 10961, 10962, 10965)
                                                .contains(s.getId()))
                        .toList();
        proudSkills =
                JsonUtils.loadToList(
                                EXCEL.resolve("ProudSkillExcelConfigData.json"),
                                ProudSkillData.class)
                        .stream()
                        .filter(
                                s ->
                                        Set.of(7331, 7332, 7339, 9631, 9632, 9639)
                                                .contains(s.getProudSkillGroupId()))
                        .toList();
        constellations =
                JsonUtils.loadToList(
                                EXCEL.resolve("AvatarTalentExcelConfigData.json"),
                                AvatarTalentData.class)
                        .stream()
                        .filter(
                                s ->
                                        (s.getId() >= 731 && s.getId() <= 736)
                                                || (s.getId() >= 961 && s.getId() <= 966))
                        .toList();
        depot =
                JsonUtils.loadToList(
                                EXCEL.resolve("AvatarSkillDepotExcelConfigData.json"),
                                AvatarSkillDepotData.class)
                        .stream()
                        .filter(s -> s.getId() == 7301)
                        .findFirst()
                        .orElseThrow();
        arlecchinoDepot =
                JsonUtils.loadToList(
                                EXCEL.resolve("AvatarSkillDepotExcelConfigData.json"),
                                AvatarSkillDepotData.class)
                        .stream()
                        .filter(s -> s.getId() == 9601)
                        .findFirst()
                        .orElseThrow();
        for (String character : List.of("Nahida", "Arlecchino")) {
            var root =
                    JsonParser.parseString(
                                    Files.readString(
                                            Path.of(
                                                    "resources/BinOutput/Talent/AvatarTalents/ConfigTalent_"
                                                            + character
                                                            + ".json")))
                            .getAsJsonObject();
            for (var entry : root.entrySet())
                configs.put(
                        entry.getKey(),
                        new OpenConfigEntry(
                                entry.getKey(),
                                JsonUtils.decode(
                                        entry.getValue().toString(), OpenConfigData[].class)));
        }
    }

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        player = new RecordingPlayer();
        player.setUid(73554);
        player.setScene(fixture.scene);
        configs.forEach((key, value) -> put(GameData.getOpenConfigEntries(), key, value));
        skills.forEach(s -> put(GameData.getAvatarSkillDataMap(), s.getId(), s));
        proudSkills.forEach(s -> put(GameData.getProudSkillDataMap(), s.getId(), s));
        constellations.forEach(s -> put(GameData.getAvatarTalentDataMap(), s.getId(), s));
        put(GameData.getAvatarSkillDepotDataMap(), depot.getId(), depot);
        put(GameData.getAvatarSkillDepotDataMap(), arlecchinoDepot.getId(), arlecchinoDepot);
        depot.onLoad();
        arlecchinoDepot.onLoad();
        for (var skill : skills) {
            var levels = new IntOpenHashSet();
            proudSkills.stream()
                    .filter(p -> p.getProudSkillGroupId() == skill.getProudSkillGroupId())
                    .forEach(p -> levels.add(p.getLevel()));
            put(GameData.getAvatarSkillLevels(), skill.getId(), levels);
            put(
                    GameData.getProudSkillGroupMaxLevels(),
                    skill.getProudSkillGroupId(),
                    levels.intStream().max().orElseThrow());
        }
        avatar = new RecordingAvatar(player);
        entity = addAvatar(avatar);
        player.packets.clear();
        fixture.scene.packets.clear();
    }

    @AfterEach
    void cleanup() {
        Collections.reverse(restore);
        restore.forEach(Runnable::run);
        fixture.close();
    }

    private <K, V> void put(Map<K, V> map, K key, V value) {
        var old = map.put(key, value);
        restore.add(
                () -> {
                    if (old == null) map.remove(key);
                    else map.put(key, old);
                });
    }

    static class RecordingPlayer extends AbilityTestFixture.TestPlayer {
        boolean loggedIn = true;

        @Override
        public boolean hasSentLoginPackets() {
            return loggedIn;
        }

        @Override
        public void save() {}
    }

    static class RecordingAvatar extends Avatar {
        private final Player player;
        int saves;

        RecordingAvatar(Player player) {
            this(player, 10000073, depot);
        }

        RecordingAvatar(Player player, int id, AvatarSkillDepotData skillDepot) {
            super(
                    new AvatarData() {
                        @Override
                        public int getId() {
                            return id;
                        }

                        @Override
                        public AvatarSkillDepotData getSkillDepot() {
                            return skillDepot;
                        }

                        @Override
                        public float getBaseHp(int level) {
                            return 100;
                        }

                        @Override
                        public float getBaseAttack(int level) {
                            return 10;
                        }

                        @Override
                        public float getBaseDefense(int level) {
                            return 10;
                        }
                    });
            this.player = player;
            setOwner(player);
        }

        @Override
        public Player getPlayer() {
            return player;
        }

        @Override
        public void save() {
            saves++;
        }
    }

    private EntityAvatar addAvatar(RecordingAvatar value) {
        var result =
                new EntityAvatar(fixture.scene, value) {
                    @Override
                    public void initAbilities() {}
                };
        fixture.scene.getEntities().put(result.getId(), result);
        player.getTeamManager().getActiveTeam().add(result);
        return result;
    }

    private Ability ability(String name, GameEntity owner) {
        return new Ability(
                JsonUtils.decode(
                        "{\"abilityName\":\""
                                + name
                                + "\",\"abilitySpecials\":{\"Click_Damage\":8,\"Hollow_Fire_Lv1\":8}}",
                        AbilityData.class),
                owner,
                player);
    }

    private Ability activeArt() {
        var active = ability(ART, entity);
        entity.getInstancedAbilities().add(active);
        return active;
    }

    private float artDamage(int level) {
        return GameData.getProudSkillDataMap().get(733200 + level).getParamList()[0];
    }

    private float burstBonus(int level) {
        return GameData.getProudSkillDataMap().get(733900 + level).getParamList()[0];
    }

    private long overrideCount() {
        return player.packets.stream()
                .filter(p -> p.getOpcode() == PacketOpcodes.AbilityInvocationsNotify)
                .count();
    }

    @Test
    void realC3UsesTheFourthSkillRowWithoutChangingBaseLevel() {
        assertTrue(avatar.unlockConstellation(733, true));
        assertEquals(1, avatar.getSkillLevelMap().get(10732));
        assertEquals(3, avatar.getProudSkillBonusMap().get(7332));
        assertEquals(
                artDamage(4), ability(ART, entity).getAbilitySpecials().getFloat("Click_Damage"));
    }

    @Test
    void realC5ChangesBurstAndLeavesTheElementalSkillAtItsOwnLevel() {
        assertTrue(avatar.unlockConstellation(735, true));
        assertEquals(
                burstBonus(4),
                ability(BURST, entity).getAbilitySpecials().getFloat("Hollow_Fire_Lv1"));
        assertEquals(
                artDamage(1), ability(ART, entity).getAbilitySpecials().getFloat("Click_Damage"));
    }

    @Test
    void c3AndC5UseIndependentGroupsAfterBaseLevelUpgrades() {
        assertTrue(avatar.setSkillLevel(10732, 10));
        assertTrue(avatar.setSkillLevel(10735, 6));
        avatar.unlockConstellation(733, true);
        avatar.unlockConstellation(735, true);
        assertEquals(
                artDamage(13), ability(ART, entity).getAbilitySpecials().getFloat("Click_Damage"));
        assertEquals(
                burstBonus(9),
                ability(BURST, entity).getAbilitySpecials().getFloat("Hollow_Fire_Lv1"));
        assertEquals(Map.of(10731, 1, 10732, 10, 10735, 6), avatar.getSkillLevelMap());
    }

    @Test
    void normalAttackBonusUsesTheNormalAttackGroup() {
        avatar.calcConstellation(
                new OpenConfigEntry(
                        "NormalBonus",
                        JsonUtils.decode(
                                "[{\"$type\":\"AddTalentExtraLevel\",\"talentIndex\":1,\"extraLevel\":3}]",
                                OpenConfigData[].class)),
                false);
        assertEquals(
                GameData.getProudSkillDataMap().get(733104).getParamList()[0],
                ability("Avatar_Nahida_Attack01", entity)
                        .getAbilitySpecials()
                        .getFloat("NormalAttack_01_Damage"));
        assertEquals(
                artDamage(1), ability(ART, entity).getAbilitySpecials().getFloat("Click_Damage"));
    }

    @Test
    void effectiveLevelIsCappedToTheLastResourceRow() {
        avatar.setSkillLevel(10732, 14);
        avatar.unlockConstellation(733, true);
        assertEquals(1, avatar.getProudSkillBonusMap().get(7332));
        assertEquals(
                artDamage(15), ability(ART, entity).getAbilitySpecials().getFloat("Click_Damage"));
        assertEquals(14, avatar.getSkillLevelMap().get(10732));
    }

    @Test
    void constellationRemovalRestoresTheRunningSkillWithoutTouchingRuntimeCounters() {
        avatar.unlockConstellation(733, true);
        var active = activeArt();
        assertEquals(artDamage(4), active.getAbilitySpecials().getFloat("Click_Damage"));
        active.getAbilitySpecials().put("RuntimeCounter", 77f);
        avatar.forceConstellationLevel(0);
        avatar.recalcConstellations();
        avatar.recalcStats(false, false);
        assertEquals(artDamage(1), active.getAbilitySpecials().getFloat("Click_Damage"));
        assertEquals(77f, active.getAbilitySpecials().getFloat("RuntimeCounter"));
    }

    @Test
    void regularUpgradeRefreshesTheAlreadyRunningSkill() {
        var original = GameData.getProudSkillDataMap().get(733202);
        put(
                GameData.getProudSkillDataMap(),
                733202,
                JsonUtils.decode(
                        "{\"proudSkillId\":733202,\"proudSkillGroupId\":7332,\"level\":2,"
                                + "\"openConfig\":\""
                                + original.getOpenConfig()
                                + "\",\"costItems\":[],"
                                + "\"paramList\":"
                                + JsonUtils.encode(original.getParamList())
                                + "}",
                        ProudSkillData.class));
        var active = activeArt();
        assertTrue(avatar.upgradeSkill(10732));
        assertEquals(artDamage(2), active.getAbilitySpecials().getFloat("Click_Damage"));
        assertEquals(2, avatar.getSkillLevelMap().get(10732));
        assertEquals(1, avatar.saves);
    }

    @Test
    void upgradeKeepsRuntimeValuesAndChangesOnlyDeclaredSkillParameters() {
        var active = activeArt();
        active.getAbilitySpecials().put("RuntimeCounter", 77f);
        avatar.setSkillLevel(10732, 2);
        assertEquals(artDamage(2), active.getAbilitySpecials().getFloat("Click_Damage"));
        assertEquals(77f, active.getAbilitySpecials().getFloat("RuntimeCounter"));
    }

    @Test
    void serializedUpdateUsesTheCorrectEntityAndInstancedAbilityAfterBaseResponses()
            throws Exception {
        entity.getInstancedAbilities().add(ability("Unrelated", entity));
        var active = activeArt();
        avatar.unlockConstellation(733, true);
        player.packets.clear();
        avatar.setSkillLevel(10732, 2);
        assertEquals(PacketOpcodes.AvatarSkillChangeNotify, player.packets.get(0).getOpcode());
        var change = AvatarSkillChangeNotify.parseFrom(player.packets.get(0).getData());
        assertEquals(1, change.getOldLevel());
        assertEquals(2, change.getCurLevel());
        assertEquals(entity.getId(), change.getEntityId());
        var response = AvatarSkillUpgradeRsp.parseFrom(player.packets.get(1).getData());
        assertEquals(2, response.getCurLevel());
        var update =
                AbilityInvocationsNotify.parseFrom(
                        player.packets.stream()
                                .filter(
                                        p ->
                                                p.getOpcode()
                                                        == PacketOpcodes.AbilityInvocationsNotify)
                                .findFirst()
                                .orElseThrow()
                                .getData());
        var value =
                update.getInvokesList().stream()
                        .filter(
                                v -> {
                                    try {
                                        return AbilityScalarValueEntry.parseFrom(v.getAbilityData())
                                                        .getKey()
                                                        .getHash()
                                                == emu.grasscutter.utils.Utils.abilityHash(
                                                        "Click_Damage");
                                    } catch (Exception e) {
                                        throw new AssertionError(e);
                                    }
                                })
                        .findFirst()
                        .orElseThrow();
        assertEquals(entity.getId(), value.getEntityId());
        assertEquals(2, value.getHead().getInstancedAbilityId());
        assertEquals(
                artDamage(5),
                AbilityScalarValueEntry.parseFrom(value.getAbilityData()).getFloatValue());
        assertEquals(artDamage(5), active.getAbilitySpecials().getFloat("Click_Damage"));
    }

    @Test
    void preLoginUpdatesServerParametersWithoutSendingAbilityOverrides() {
        var active = activeArt();
        player.loggedIn = false;
        avatar.setSkillLevel(10732, 2);
        assertEquals(artDamage(2), active.getAbilitySpecials().getFloat("Click_Damage"));
        assertEquals(0, overrideCount());
    }

    @Test
    void settingTheSameLevelDoesNotReplaceAnExistingRuntimeOverride() {
        var active = activeArt();
        active.getAbilitySpecials().put("Click_Damage", 123f);
        assertTrue(avatar.setSkillLevel(10732, 1));
        assertEquals(123f, active.getAbilitySpecials().getFloat("Click_Damage"));
        assertEquals(0, overrideCount());
    }

    @Test
    void invalidLevelDoesNotSaveNotifyOrRefresh() {
        var active = activeArt();
        assertFalse(avatar.setSkillLevel(10732, 16));
        assertFalse(avatar.setSkillLevel(10732, -1));
        assertFalse(avatar.setSkillLevel(10732, 0));
        assertEquals(1, avatar.getSkillLevelMap().get(10732));
        assertEquals(artDamage(1), active.getAbilitySpecials().getFloat("Click_Damage"));
        assertEquals(0, avatar.saves);
        assertTrue(player.packets.isEmpty());
    }

    @Test
    void offPartyAvatarUsesNewLevelsWhenItsAbilityIsCreatedLater() {
        player.getTeamManager().getActiveTeam().remove(entity);
        avatar.setSkillLevel(10732, 4);
        assertEquals(
                artDamage(4), ability(ART, entity).getAbilitySpecials().getFloat("Click_Damage"));
        assertEquals(0, overrideCount());
    }

    @Test
    void upgradeRefreshesWeaponAbilitiesAndPreservesLaunchedProjectiles() {
        var weapon = new GameItem();
        weapon.setCount(1);
        weapon.setWeaponEntity(new EntityWeapon(fixture.scene, 50016001, avatar));
        avatar.getEquips().put(EquipType.EQUIP_WEAPON.getValue(), weapon);
        var weaponAbility = ability(ART, weapon.getWeaponEntity());
        weapon.getWeaponEntity().getInstancedAbilities().add(weaponAbility);
        var bullet =
                new EntityClientGadget(
                        fixture.scene,
                        player,
                        EvtCreateGadgetNotify.newBuilder()
                                .setEntityId(73599)
                                .setOwnerEntityId(entity.getId())
                                .build());
        fixture.scene.getEntities().put(bullet.getId(), bullet);
        var launched = ability(ART, bullet);
        bullet.getInstancedAbilities().add(launched);
        avatar.setSkillLevel(10732, 2);
        assertEquals(artDamage(2), weaponAbility.getAbilitySpecials().getFloat("Click_Damage"));
        assertEquals(artDamage(1), launched.getAbilitySpecials().getFloat("Click_Damage"));
    }

    @Test
    void upgradingOneAvatarDoesNotRefreshAnotherAvatarsSkill() {
        var other = addAvatar(new RecordingAvatar(player));
        var unchanged = ability(ART, other);
        other.getInstancedAbilities().add(unchanged);
        var active = activeArt();
        avatar.setSkillLevel(10732, 3);
        assertEquals(artDamage(3), active.getAbilitySpecials().getFloat("Click_Damage"));
        assertEquals(artDamage(1), unchanged.getAbilitySpecials().getFloat("Click_Damage"));
    }

    @Test
    void missingEffectiveLevelRowKeepsTheResourceDefaultInsteadOfAnotherAvatarsParameters() {
        avatar.unlockConstellation(733, true);
        var row = GameData.getProudSkillDataMap().remove(733204);
        restore.add(() -> GameData.getProudSkillDataMap().put(733204, row));
        assertEquals(8f, ability(ART, entity).getAbilitySpecials().getFloat("Click_Damage"));
    }

    @Test
    void legacyParameterLookupAlsoUsesArlecchinosActualC3Level() throws Exception {
        var arlecchino = new RecordingAvatar(player, 10000096, arlecchinoDepot);
        arlecchino.unlockConstellation(963, true);
        var lookup =
                AbilityManager.class.getDeclaredMethod(
                        "getArlecchinoBoLRatio", Avatar.class, String.class);
        lookup.setAccessible(true);
        // Exercise the shared legacy lookup with a real level-dependent attack parameter.
        // This is not a claim that Arlecchino's bond ratios increase at C3.
        assertEquals(
                GameData.getProudSkillDataMap().get(963104).getParamList()[0],
                (float)
                        lookup.invoke(
                                player.getAbilityManager(),
                                arlecchino,
                                "NormalAttack_01_DamagePercentage"));
        assertEquals(1, arlecchino.getSkillLevelMap().get(10961));
    }
}
