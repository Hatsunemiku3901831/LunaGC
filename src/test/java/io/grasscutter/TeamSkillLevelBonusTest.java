package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;

import emu.grasscutter.data.*;
import emu.grasscutter.data.ResourceLoader.OpenConfigData;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.data.excels.avatar.*;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.EvtCreateGadgetNotifyOuterClass.EvtCreateGadgetNotify;
import emu.grasscutter.net.proto.ProudSkillExtraLevelNotifyOuterClass.ProudSkillExtraLevelNotify;
import emu.grasscutter.net.proto.SceneTeamUpdateNotifyOuterClass.SceneTeamUpdateNotify;
import emu.grasscutter.utils.JsonUtils;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.util.*;

class TeamSkillLevelBonusTest {
    private static final Path EXCEL = Path.of("resources/ExcelBinOutput");
    private static final Map<Integer, Integer> DEPOTS =
            Map.of(10000033, 3301, 10000073, 7301, 10000114, 11401, 10000015, 1501);
    private static List<AvatarSkillDepotData> depots;
    private static List<AvatarSkillData> skills;
    private static List<ProudSkillData> proud;
    private static List<AvatarTalentData> talents;
    private static final Map<String, OpenConfigEntry> configs = new LinkedHashMap<>();
    private final List<Runnable> restore = new ArrayList<>();
    private AbilityTestFixture fixture;
    private RecordingPlayer player;
    private RecordingWorld world;
    private AbilityTestFixture.TestScene scene;
    private final Map<Integer, EntityAvatar> entities = new LinkedHashMap<>();
    private final List<BasePacket> events = new ArrayList<>();

    @BeforeAll
    static void resources() throws Exception {
        depots =
                JsonUtils.loadToList(
                                EXCEL.resolve("AvatarSkillDepotExcelConfigData.json"),
                                AvatarSkillDepotData.class)
                        .stream()
                        .filter(d -> DEPOTS.containsValue(d.getId()))
                        .toList();
        var ids = new HashSet<Integer>();
        var groups = new HashSet<Integer>();
        for (var depot : depots) {
            depot.getSkillsAndEnergySkill().forEach(ids::add);
            if (depot.getInherentProudSkillOpens() != null)
                depot.getInherentProudSkillOpens()
                        .forEach(p -> groups.add(p.getProudSkillGroupId()));
        }
        skills =
                JsonUtils.loadToList(
                                EXCEL.resolve("AvatarSkillExcelConfigData.json"),
                                AvatarSkillData.class)
                        .stream()
                        .filter(d -> ids.contains(d.getId()))
                        .toList();
        skills.forEach(s -> groups.add(s.getProudSkillGroupId()));
        proud =
                JsonUtils.loadToList(
                                EXCEL.resolve("ProudSkillExcelConfigData.json"),
                                ProudSkillData.class)
                        .stream()
                        .filter(d -> groups.contains(d.getProudSkillGroupId()))
                        .toList();
        talents =
                JsonUtils.loadToList(
                                EXCEL.resolve("AvatarTalentExcelConfigData.json"),
                                AvatarTalentData.class)
                        .stream()
                        .filter(
                                t ->
                                        depots.stream()
                                                .anyMatch(d -> d.getTalents().contains(t.getId())))
                        .toList();
        for (var name : List.of("Tartaglia", "Nahida", "SkirkNew", "Kaeya")) {
            var root =
                    JsonParser.parseString(
                                    Files.readString(
                                            Path.of(
                                                    "resources/BinOutput/Talent/AvatarTalents/ConfigTalent_"
                                                            + name
                                                            + ".json")))
                            .getAsJsonObject();
            root.entrySet()
                    .forEach(
                            e ->
                                    configs.put(
                                            e.getKey(),
                                            new OpenConfigEntry(
                                                    e.getKey(),
                                                    JsonUtils.decode(
                                                            e.getValue().toString(),
                                                            OpenConfigData[].class))));
        }
    }

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        configs.forEach((key, value) -> put(GameData.getOpenConfigEntries(), key, value));
        skills.forEach(s -> put(GameData.getAvatarSkillDataMap(), s.getId(), s));
        proud.forEach(
                p -> {
                    p.onLoad();
                    put(GameData.getProudSkillDataMap(), p.getId(), p);
                });
        talents.forEach(t -> put(GameData.getAvatarTalentDataMap(), t.getId(), t));
        depots.forEach(
                d -> {
                    d.onLoad();
                    put(GameData.getAvatarSkillDepotDataMap(), d.getId(), d);
                });
        for (var skill : skills) {
            var levels = new IntOpenHashSet();
            proud.stream()
                    .filter(p -> p.getProudSkillGroupId() == skill.getProudSkillGroupId())
                    .forEach(p -> levels.add(p.getLevel()));
            put(GameData.getAvatarSkillLevels(), skill.getId(), levels);
            if (!levels.isEmpty())
                put(
                        GameData.getProudSkillGroupMaxLevels(),
                        skill.getProudSkillGroupId(),
                        levels.intStream().max().orElseThrow());
        }
        player = new RecordingPlayer(73555);
        world = new RecordingWorld(fixture.scene.getWorld().getServer(), player);
        scene =
                new AbilityTestFixture.TestScene(
                        world,
                        JsonUtils.decode(
                                "{\"id\":3,\"type\":\"SCENE_WORLD\"}",
                                emu.grasscutter.data.excels.scene.SceneData.class));
        attach(player);
        for (int id : DEPOTS.keySet()) entities.put(id, addAvatar(player, id));
        events.clear();
        player.packets.clear();
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

    class RecordingPlayer extends AbilityTestFixture.TestPlayer {
        boolean loggedIn = true;

        RecordingPlayer(int uid) {
            setUid(uid);
        }

        @Override
        public boolean hasSentLoginPackets() {
            return loggedIn;
        }

        @Override
        public void sendPacket(BasePacket packet) {
            super.sendPacket(packet);
            events.add(packet);
        }

        @Override
        public void save() {}
    }

    class RecordingWorld extends AbilityTestFixture.TestWorld {
        RecordingWorld(emu.grasscutter.server.game.GameServer server, Player host) {
            super(server, host);
        }

        @Override
        public void broadcastPacket(BasePacket packet) {
            super.broadcastPacket(packet);
            events.add(packet);
        }
    }

    static class RecordingAvatar extends Avatar {
        private final Player player;

        RecordingAvatar(Player owner, int id) {
            super(
                    new AvatarData() {
                        @Override
                        public int getId() {
                            return id;
                        }

                        @Override
                        public AvatarSkillDepotData getSkillDepot() {
                            return GameData.getAvatarSkillDepotDataMap().get((int) DEPOTS.get(id));
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
            this.player = owner;
        }

        @Override
        public Player getPlayer() {
            return player;
        }

        @Override
        public void save() {}
    }

    private void attach(RecordingPlayer target) {
        target.setScene(scene);
        target.setWorld(world);
        world.getPlayers().add(target);
        target.getTeamManager().setEntity(new EntityTeam(target));
    }

    private EntityAvatar addAvatar(RecordingPlayer owner, int id) {
        var avatar = new RecordingAvatar(owner, id);
        assertTrue(owner.getAvatars().addAvatar(avatar));
        var weapon =
                new GameItem(
                        JsonUtils.decode(
                                "{\"id\":11499,\"itemType\":\"ITEM_WEAPON\",\"equipType\":\"EQUIP_WEAPON\",\"gadgetId\":50016001}",
                                ItemData.class));
        weapon.setOwner(owner);
        avatar.getEquips().put(EquipType.EQUIP_WEAPON.getValue(), weapon);
        var result =
                new EntityAvatar(scene, avatar) {
                    @Override
                    public void initAbilities() {}
                };
        scene.getEntities().put(result.getId(), result);
        owner.getTeamManager().getActiveTeam().add(result);
        return result;
    }

    private Avatar avatar(int id) {
        return entities.get(id).getAvatar();
    }

    private void party(int... ids) {
        var team = player.getTeamManager().getCurrentTeamInfo();
        team.getAvatars().clear();
        for (int id : ids) team.getAvatars().add(id);
    }

    private void refresh() {
        player.getTeamManager().updateTeamProperties();
    }

    private int extra(int avatarId, int slot) {
        return extra(avatar(avatarId), slot);
    }

    private int extra(Avatar value, int slot) {
        int id =
                slot == 9
                        ? value.getSkillDepot().getEnergySkill()
                        : value.getSkillDepot().getSkills().get(slot);
        return value.getProudSkillBonusMap()
                .getOrDefault(GameData.getAvatarSkillDataMap().get(id).getProudSkillGroupId(), 0);
    }

    private Ability ability(String name, GameEntity owner) {
        return new Ability(
                JsonUtils.decode(
                        "{\"abilityName\":\""
                                + name
                                + "\",\"abilitySpecials\":{\"NormalAttack_01_Damage\":8,\"Click_Damage\":8}}",
                        AbilityData.class),
                owner,
                player);
    }

    private Ability normal() {
        var result = ability("Avatar_Nahida_Attack01", entities.get(10000073));
        entities.get(10000073).getInstancedAbilities().add(result);
        return result;
    }

    private float normalDamage(int level) {
        return GameData.getProudSkillDataMap().get(733100 + level).getParamList()[0];
    }

    private long count(int opcode) {
        return events.stream().filter(p -> p.getOpcode() == opcode).count();
    }

    private List<ProudSkillExtraLevelNotify> extraPackets() {
        return events.stream()
                .filter(p -> p.getOpcode() == PacketOpcodes.ProudSkillExtraLevelNotify)
                .map(
                        p -> {
                            try {
                                return ProudSkillExtraLevelNotify.parseFrom(p.getData());
                            } catch (Exception e) {
                                throw new AssertionError(e);
                            }
                        })
                .toList();
    }

    @Test
    void tartagliaRaisesOwnPartyNormalAttacksIncludingHimself() {
        party(10000033, 10000073);
        refresh();
        assertEquals(1, extra(10000033, 0));
        assertEquals(1, extra(10000073, 0));
        assertEquals(0, extra(10000073, 1));
        assertEquals(1, avatar(10000073).getSkillLevelMap().get(10731));
    }

    @Test
    void tartagliaInStorageAndTheOldEntityListDoesNotAffectTheSelectedParty() {
        party(10000073);
        refresh();
        assertEquals(0, extra(10000073, 0));
        assertEquals(0, extra(10000033, 0));
    }

    @Test
    void lockedPassiveDoesNotGrantTheBonus() {
        avatar(10000033).getProudSkillList().remove(332301);
        party(10000033, 10000073);
        refresh();
        assertEquals(0, extra(10000073, 0));
    }

    @Test
    void unavailablePassiveResourceDoesNotGrantAStoredUnlock() {
        var old = GameData.getProudSkillDataMap().remove(332301);
        restore.add(() -> GameData.getProudSkillDataMap().put(332301, old));
        party(10000033, 10000073);
        refresh();
        assertEquals(0, extra(10000073, 0));
    }

    @Test
    void runningAttackChangesOnJoiningAndLeavingThePartyWithoutLosingCounters() {
        var active = normal();
        active.getAbilitySpecials().put("RuntimeCounter", 77f);
        party(10000033, 10000073);
        refresh();
        assertEquals(
                normalDamage(2), active.getAbilitySpecials().getFloat("NormalAttack_01_Damage"));
        party(10000073);
        events.clear();
        refresh();
        assertEquals(
                normalDamage(1), active.getAbilitySpecials().getFloat("NormalAttack_01_Damage"));
        assertEquals(77f, active.getAbilitySpecials().getFloat("RuntimeCounter"));
        assertTrue(
                extraPackets().stream()
                        .anyMatch(
                                p ->
                                        p.getAvatarGuid() == avatar(10000073).getGuid()
                                                && p.getExtraLevel() == 0));
    }

    @Test
    void duplicateRosterEntriesDoNotStackTheBonus() {
        party(10000033, 10000033, 10000073, 10000073);
        refresh();
        assertEquals(1, extra(10000073, 0));
    }

    @Test
    void repeatedTeamRefreshDoesNotResendSkillParameterChanges() {
        normal();
        party(10000033, 10000073);
        refresh();
        events.clear();
        refresh();
        assertEquals(0, count(PacketOpcodes.ProudSkillExtraLevelNotify));
        assertEquals(0, count(PacketOpcodes.AbilityInvocationsNotify));
    }

    @Test
    void constellationRecalculationPreservesTheIndependentTeamContribution() {
        party(10000033, 10000073);
        refresh();
        avatar(10000073).recalcConstellations();
        assertEquals(1, extra(10000073, 0));
    }

    @Test
    void intrinsicAndTeamBonusesAreReportedAsTheirTotal() {
        party(10000033, 10000073);
        refresh();
        events.clear();
        avatar(10000073)
                .calcConstellation(
                        new OpenConfigEntry(
                                "NormalConstellation",
                                JsonUtils.decode(
                                        "[{\"$type\":\"AddTalentExtraLevel\",\"talentIndex\":1,\"extraLevel\":3}]",
                                        OpenConfigData[].class)),
                        true);
        assertEquals(4, extra(10000073, 0));
        assertTrue(
                extraPackets().stream()
                        .anyMatch(
                                p ->
                                        p.getAvatarGuid() == avatar(10000073).getGuid()
                                                && p.getTalentIndex() == 1
                                                && p.getExtraLevel() == 4));
    }

    @Test
    void changingTheBaseLevelAtTheResourceCapReportsTheNewExtraLevel() {
        party(10000033, 10000073);
        refresh();
        avatar(10000073).setSkillLevel(10731, 14);
        events.clear();
        avatar(10000073).setSkillLevel(10731, 15);
        assertEquals(0, extra(10000073, 0));
        assertTrue(
                extraPackets().stream()
                        .anyMatch(
                                p ->
                                        p.getAvatarGuid() == avatar(10000073).getGuid()
                                                && p.getTalentIndex() == 1
                                                && p.getExtraLevel() == 0));
        events.clear();
        avatar(10000073).setSkillLevel(10731, 14);
        assertEquals(1, extra(10000073, 0));
        assertTrue(
                extraPackets().stream()
                        .anyMatch(
                                p ->
                                        p.getAvatarGuid() == avatar(10000073).getGuid()
                                                && p.getExtraLevel() == 1));
    }

    @Test
    void teamAnnouncementPrecedesChangesToAlreadyRunningSkills() throws Exception {
        normal();
        party(10000033, 10000073);
        refresh();
        var relevant =
                events.stream()
                        .filter(
                                p ->
                                        p.getOpcode() == PacketOpcodes.SceneTeamUpdateNotify
                                                || p.getOpcode()
                                                        == PacketOpcodes.AbilityInvocationsNotify)
                        .toList();
        assertEquals(PacketOpcodes.SceneTeamUpdateNotify, relevant.get(0).getOpcode());
        assertTrue(
                relevant.stream()
                        .anyMatch(p -> p.getOpcode() == PacketOpcodes.AbilityInvocationsNotify));
        var packet = SceneTeamUpdateNotify.parseFrom(relevant.get(0).getData());
        var row =
                packet.getSceneTeamAvatarListList().stream()
                        .filter(p -> p.getAvatarGuid() == avatar(10000073).getGuid())
                        .findFirst()
                        .orElseThrow();
        assertEquals(
                1,
                row.getSceneEntityInfo().getAvatar().getProudSkillExtraLevelMapOrDefault(7331, 0));
    }

    @Test
    void preLoginSeedsBonusesWithoutSendingExtraLevelOrAbilityPackets() {
        party(10000033, 10000073);
        player.loggedIn = false;
        player.getTeamManager().onPlayerLogin();
        assertEquals(1, extra(10000073, 0));
        assertEquals(0, count(PacketOpcodes.ProudSkillExtraLevelNotify));
        assertEquals(0, count(PacketOpcodes.AbilityInvocationsNotify));
    }

    @Test
    void skirkRequiresBothIceAndWaterAndAddsOnlyElementalSkillLevels() {
        party(10000114, 10000033, 10000015);
        refresh();
        for (int id : new int[] {10000114, 10000033, 10000015}) {
            assertEquals(1, extra(id, 1));
            assertEquals(1, extra(id, 0));
            assertEquals(0, extra(id, 9));
        }
    }

    @Test
    void allIcePartyDoesNotActivateSkirk() {
        party(10000114, 10000015);
        refresh();
        assertEquals(0, extra(10000015, 1));
    }

    @Test
    void waterPartyWithoutSkirkDoesNotGainAnElementalSkillBonus() {
        party(10000033);
        refresh();
        assertEquals(0, extra(10000033, 1));
    }

    @Test
    void anotherElementDisablesSkirkWithoutRemovingTartagliasNormalAttackBonus() {
        party(10000114, 10000033, 10000073);
        refresh();
        assertEquals(0, extra(10000114, 1));
        assertEquals(1, extra(10000073, 0));
    }

    @Test
    void lockedSkirkPassiveDoesNotGrantTheElementalSkillBonus() {
        avatar(10000114).getProudSkillList().remove(1142301);
        party(10000114, 10000033, 10000015);
        refresh();
        assertEquals(0, extra(10000015, 1));
    }

    @Test
    void unresolvedSelectedMemberFailsTheIceWaterCondition() {
        party(10000114, 10000033, 99999999);
        refresh();
        assertEquals(0, extra(10000114, 1));
    }

    @Test
    void skirkAndARealSkillConstellationCombineWithoutChangingTheBaseLevel() {
        party(10000114, 10000033, 10000015);
        refresh();
        avatar(10000015).unlockConstellation(153, true);
        assertEquals(4, extra(10000015, 1));
        assertEquals(1, avatar(10000015).getSkillLevelMap().get(10074));
    }

    @Test
    void skillDepotElementChangeRechecksSkirksPartyCondition() {
        party(10000114, 10000033, 10000015);
        refresh();
        assertEquals(1, extra(10000114, 1));
        avatar(10000015).setSkillDepotData(GameData.getAvatarSkillDepotDataMap().get(7301));
        assertEquals(0, extra(10000114, 1));
    }

    @Test
    void trialPartyDoesNotReceiveBonusesFromTheSavedOverworldParty() {
        party(10000033, 10000073);
        refresh();
        player.getTeamManager().setUsingTrialTeam(true);
        party(10000073);
        refresh();
        assertEquals(0, extra(10000073, 0));
    }

    @Test
    void tpsOverlayRemovesTheOverworldContributionAndRestoresItOnExit() {
        party(10000033, 10000073);
        refresh();
        var overlay = new RecordingAvatar(player, 10000073);
        overlay.setOwner(player);
        player.getTeamManager().beginTpsSceneTeam(scene, overlay);
        refresh();
        assertEquals(0, extra(10000033, 0));
        assertEquals(0, extra(10000073, 0));
        player.getTeamManager().endTpsSceneTeam(scene);
        player.getTeamManager().onPlayerLogin();
        assertEquals(1, extra(10000073, 0));
    }

    @Test
    void anotherPlayersTartagliaDoesNotChangeTheHostsOwnParty() throws Exception {
        var guest = new RecordingPlayer(73556);
        attach(guest);
        var tartaglia = addAvatar(guest, 10000033);
        var multiplayer = emu.grasscutter.game.world.World.class.getDeclaredField("isMultiplayer");
        multiplayer.setAccessible(true);
        multiplayer.setBoolean(world, true);
        player.getTeamManager().getMpTeam().getAvatars().add(10000073);
        guest.getTeamManager().getMpTeam().getAvatars().add(10000033);
        player.getTeamManager().updateTeamProperties();
        guest.getTeamManager().updateTeamProperties();
        assertEquals(0, extra(10000073, 0));
        assertEquals(1, extra(tartaglia.getAvatar(), 0));
    }

    @Test
    void weaponAbilitiesRefreshWhileLaunchedProjectilesKeepTheirOriginalParameters() {
        party(10000033, 10000073);
        refresh();
        var source = entities.get(10000073);
        var weapon = avatar(10000073).getWeapon().getWeaponEntity();
        var weaponAbility = ability("Avatar_Nahida_Attack01", weapon);
        weapon.getInstancedAbilities().add(weaponAbility);
        var bullet =
                new EntityClientGadget(
                        scene,
                        player,
                        EvtCreateGadgetNotify.newBuilder()
                                .setEntityId(73699)
                                .setOwnerEntityId(source.getId())
                                .build());
        scene.getEntities().put(bullet.getId(), bullet);
        var launched = ability("Avatar_Nahida_Attack01", bullet);
        bullet.getInstancedAbilities().add(launched);
        party(10000073);
        refresh();
        assertEquals(
                normalDamage(1),
                weaponAbility.getAbilitySpecials().getFloat("NormalAttack_01_Damage"));
        assertEquals(
                normalDamage(2), launched.getAbilitySpecials().getFloat("NormalAttack_01_Damage"));
    }
}
