package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;

import emu.grasscutter.data.*;
import emu.grasscutter.data.ResourceLoader.OpenConfigData;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.data.excels.avatar.*;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.avatar.*;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AbilityInvocationsNotifyOuterClass.AbilityInvocationsNotify;
import emu.grasscutter.net.proto.AbilityScalarValueEntryOuterClass.AbilityScalarValueEntry;
import emu.grasscutter.utils.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.*;
import java.util.*;

/**
 * Current resource parameters, actual roster changes, installed properties and wire instance IDs.
 */
class TeamResonanceTest {
    private static final Map<ElementType, int[]> IDS =
            Map.of(
                    ElementType.Fire,
                    new int[] {10000016, 10000021},
                    ElementType.Water,
                    new int[] {10000014, 10000033},
                    ElementType.Wind,
                    new int[] {10000022, 10000003},
                    ElementType.Electric,
                    new int[] {10000006, 10000020},
                    ElementType.Grass,
                    new int[] {10000073, 10000067},
                    ElementType.Ice,
                    new int[] {10000015, 10000036},
                    ElementType.Rock,
                    new int[] {10000034, 10000027});
    private static final String SKILL = "Resonance_Test_Skill";
    private AbilityTestFixture fixture;
    private SkillTalentLevelTest.RecordingPlayer player;
    private AbilityTestFixture.TestWorld world;
    private final Map<Integer, EntityAvatar> entities = new LinkedHashMap<>();
    private final List<Runnable> restore = new ArrayList<>();

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        player = new SkillTalentLevelTest.RecordingPlayer();
        player.setUid(73557);
        world = (AbilityTestFixture.TestWorld) fixture.scene.getWorld();
        attach(player);
        for (var map :
                List.<Map<?, ?>>of(
                        GameData.getTeamResonanceDataMap(),
                        GameData.getAvatarSkillDataMap(),
                        GameData.getAvatarSkillDepotDataMap(),
                        GameData.getAbilityDataMap(),
                        GameData.getAbilityHashes(),
                        GameData.getOpenConfigEntries())) snapshot(map);
        var excel = Path.of("resources/ExcelBinOutput");
        for (var d :
                JsonUtils.loadToList(
                        excel.resolve("TeamResonanceExcelConfigData.json"),
                        TeamResonanceData.class))
            GameData.getTeamResonanceDataMap().put(d.getId(), d);
        for (var d :
                JsonUtils.loadToList(
                        excel.resolve("AvatarSkillExcelConfigData.json"), AvatarSkillData.class))
            GameData.getAvatarSkillDataMap().put(d.getId(), d);
        for (var d :
                JsonUtils.loadToList(
                        excel.resolve("AvatarSkillDepotExcelConfigData.json"),
                        AvatarSkillDepotData.class)) {
            GameData.getAvatarSkillDepotDataMap().put(d.getId(), d);
            d.onLoad();
            d.setAbilities(new AbilityEmbryoEntry("resonance-test", new String[] {SKILL}));
        }
        var configs =
                JsonParser.parseString(
                                Files.readString(
                                        Path.of(
                                                "resources/BinOutput/Talent/TeamTalents/ConfigTalent_TeamResonance.json")))
                        .getAsJsonObject();
        configs.entrySet()
                .forEach(
                        e ->
                                GameData.getOpenConfigEntries()
                                        .put(
                                                e.getKey(),
                                                new OpenConfigEntry(
                                                        e.getKey(),
                                                        JsonUtils.decode(
                                                                e.getValue().toString(),
                                                                OpenConfigData[].class))));
        var abilities =
                JsonParser.parseString(
                                Files.readString(
                                        Path.of(
                                                "resources/BinOutput/Ability/Temp/TeamAbilities/ConfigAbility_TeamResonance.json")))
                        .getAsJsonArray();
        abilities.forEach(
                row ->
                        abilityData(
                                JsonUtils.decode(
                                        row.getAsJsonObject().get("Default").toString(),
                                        AbilityData.class)));
        abilityData(
                JsonUtils.decode(
                        "{\"abilityName\":\"" + SKILL + "\",\"abilitySpecials\":{\"runtime\":9}}",
                        AbilityData.class));
        for (var ids : IDS.values()) for (int id : ids) addAvatar(player, id);
        party(ElementType.Fire, ElementType.Fire, ElementType.Water, ElementType.Ice);
        world.packets.clear();
        player.packets.clear();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void snapshot(Map map) {
        var old = new HashMap(map);
        map.clear();
        restore.add(
                () -> {
                    map.clear();
                    map.putAll(old);
                });
    }

    @AfterEach
    void cleanup() {
        Collections.reverse(restore);
        restore.forEach(Runnable::run);
        fixture.close();
    }

    private void abilityData(AbilityData d) {
        GameData.getAbilityDataMap().put(d.abilityName, d);
        GameData.getAbilityHashes().put(Utils.abilityHash(d.abilityName), d.abilityName);
    }

    private void attach(SkillTalentLevelTest.RecordingPlayer p) {
        p.setWorld(world);
        p.setScene(fixture.scene);
        world.getPlayers().add(p);
        p.getTeamManager().setEntity(new EntityTeam(p));
    }

    private EntityAvatar addAvatar(Player owner, int id) {
        int depotId = (id - 10000000) * 100 + 1;
        var avatar =
                new SkillTalentLevelTest.RecordingAvatar(
                        owner, id, GameData.getAvatarSkillDepotDataMap().get(depotId));
        assertTrue(owner.getAvatars().addAvatar(avatar));
        var weapon =
                new GameItem(
                        JsonUtils.decode(
                                "{\"id\":11499,\"itemType\":\"ITEM_WEAPON\",\"equipType\":\"EQUIP_WEAPON\",\"gadgetId\":50016001}",
                                ItemData.class));
        weapon.setOwner(owner);
        avatar.getEquips().put(EquipType.EQUIP_WEAPON.getValue(), weapon);
        var result = new EntityAvatar(fixture.scene, avatar);
        fixture.scene.getEntities().put(result.getId(), result);
        if (owner == player) entities.put(id, result);
        return result;
    }

    private void select(Player p, List<EntityAvatar> chosen, boolean updateEntities) {
        var team = p.getTeamManager().getCurrentTeamInfo();
        team.getAvatars().clear();
        chosen.forEach(e -> team.getAvatars().add(e.getAvatar().getAvatarId()));
        if (updateEntities) {
            p.getTeamManager().getActiveTeam().clear();
            p.getTeamManager().getActiveTeam().addAll(chosen);
        }
    }

    private void party(ElementType... elements) {
        var counts = new EnumMap<ElementType, Integer>(ElementType.class);
        var chosen = new ArrayList<EntityAvatar>();
        for (var element : elements)
            chosen.add(entities.get(IDS.get(element)[counts.merge(element, 1, Integer::sum) - 1]));
        select(player, chosen, true);
    }

    private EntityAvatar first() {
        return player.getTeamManager().getActiveTeam().get(0);
    }

    private void refresh() {
        player.getTeamManager().refreshTeamResonances(true);
    }

    private List<Integer> active() {
        return TeamResonances.active(player).stream().map(TeamResonanceData::getId).toList();
    }

    private void initialize(EntityAvatar entity) {
        for (var embryo : entity.getAbilityControlBlock().getAbilityEmbryoListList()) {
            String name = GameData.getAbilityHashes().get(embryo.getAbilityNameHash());
            var data = name == null ? null : GameData.getAbilityData(name);
            entity.getInstancedAbilities()
                    .add(data == null ? null : new Ability(data, entity, entity.getPlayer()));
        }
    }

    private Ability find(EntityAvatar entity, String name) {
        return entity.getInstancedAbilities().stream()
                .filter(Objects::nonNull)
                .filter(a -> name.equals(a.getData().abilityName))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void tableIsRegisteredForNormalResourceLoading() {
        assertSame(
                GameData.getTeamResonanceDataMap(),
                GameData.getMapByResourceDef(TeamResonanceData.class));
        assertEquals(
                "TeamResonanceExcelConfigData.json",
                TeamResonanceData.class.getAnnotation(ResourceType.class).name()[0]);
        assertEquals(8, GameData.getTeamResonanceDataMap().size());
    }

    @ParameterizedTest
    @EnumSource(
            value = ElementType.class,
            names = {"Fire", "Water", "Wind", "Electric", "Grass", "Ice", "Rock", "Default"})
    void allEighteenParametersComeFromCurrentResources(ElementType type) {
        if (type == ElementType.Default)
            party(ElementType.Fire, ElementType.Water, ElementType.Ice, ElementType.Grass);
        else {
            var other = IDS.keySet().stream().filter(t -> t != type).limit(2).toList();
            party(type, type, other.get(0), other.get(1));
        }
        var row = GameData.getTeamResonanceDataMap().get(type.getTeamResonanceId());
        assertEquals(List.of(row.getId()), active());
        var config = GameData.getOpenConfigEntries().get(row.getOpenConfig());
        var actual = AbilityTalentResolver.overrides(first().getAvatar(), row.getOpenConfig());
        assertEquals(config.getAbilityVarSetters().length, actual.size());
        var a = new Ability(GameData.getAbilityData(row.getOpenConfig()), first(), player);
        for (var setter : config.getAbilityVarSetters()) {
            assertEquals(0f, a.getData().abilitySpecials.get(setter.getVarName()));
            float expected = setter.resolve(row.getParamList());
            assertEquals(expected, actual.get(setter.getVarName()));
            assertEquals(expected, a.getAbilitySpecials().getFloat(setter.getVarName()));
        }
        assertEquals(
                18,
                GameData.getTeamResonanceDataMap().values().stream()
                        .mapToInt(
                                d ->
                                        GameData.getOpenConfigEntries()
                                                .get(d.getOpenConfig())
                                                .getAbilityVarSetters()
                                                .length)
                        .sum());
    }

    @Test
    void incompletePartyDoesNotActivateResonance() {
        party(ElementType.Fire, ElementType.Fire, ElementType.Water);
        assertTrue(active().isEmpty());
    }

    @Test
    void selectedPartyWinsOverStaleEntities() {
        select(
                player,
                List.of(
                        entities.get(10000014),
                        entities.get(10000033),
                        entities.get(10000015),
                        entities.get(10000073)),
                false);
        assertEquals(List.of(10201), active());
        assertTrue(
                AbilityTalentResolver.overrides(first().getAvatar(), "TeamResonance_Fire_Lv2")
                        .isEmpty());
    }

    @Test
    void duplicateAndUnresolvedRosterEntriesDoNotEnableResonance() {
        var ids = player.getTeamManager().getCurrentTeamInfo().getAvatars();
        ids.set(1, ids.get(0));
        assertTrue(active().isEmpty());
        ids.set(1, 99999999);
        assertTrue(active().isEmpty());
    }

    @Test
    void unrelatedCharactersDoNotReceivePartyTalents() {
        var outsider = entities.get(10000073).getAvatar();
        assertTrue(AbilityTalentResolver.overrides(outsider, "TeamResonance_Fire_Lv2").isEmpty());
        assertFalse(
                TeamResonances.active(first().getAvatar()).stream()
                        .anyMatch(d -> d.getOpenConfig().contains("WhiteBox")));
    }

    @Test
    void twoResonancesCanCoexist() {
        party(ElementType.Fire, ElementType.Fire, ElementType.Water, ElementType.Water);
        assertEquals(List.of(10101, 10201), active());
    }

    @Test
    void unknownAndEmptyConditionsDoNotInventAnEffect() {
        for (var json :
                List.of(
                        "{}",
                        "{\"cond\":\"UNKNOWN\",\"fireAvatarCount\":2}",
                        "{\"fireAvatarCount\":-2}"))
            assertFalse(
                    JsonUtils.decode(json, TeamResonanceData.class)
                            .matches(Map.of(ElementType.Fire, 2, ElementType.Water, 2)));
    }

    @Test
    void changingRosterRemovesPropertiesAndKeepsLiveSkillsAndDynamicTail() throws Exception {
        refresh();
        var entity = first();
        initialize(entity);
        assertEquals(12.5f, entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK), 0.0001f);
        var skill = find(entity, SKILL);
        skill.getAbilitySpecials().put("runtime", 123f);
        var dynamic =
                new Ability(
                        JsonUtils.decode(
                                "{\"abilityName\":\"Resonance_Test_Dynamic\"}", AbilityData.class),
                        entity,
                        player);
        entity.getInstancedAbilities().add(dynamic);
        party(ElementType.Fire, ElementType.Water, ElementType.Water, ElementType.Ice);
        world.packets.clear();
        player.packets.clear();
        refresh();
        assertEquals(10f, entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK), 0.0001f);
        assertEquals(125f, entity.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP), 0.0001f);
        assertSame(skill, find(entity, SKILL));
        assertEquals(123f, skill.getAbilitySpecials().getFloat("runtime"));
        assertSame(
                dynamic,
                entity.getInstancedAbilities().get(entity.getInstancedAbilities().size() - 1));
        assertTrue(
                entity.getAppliedAbilityModifiers().keySet().stream()
                        .noneMatch(a -> a.getData().abilityName.equals("TeamResonance_Fire_Lv2")));
        var block = entity.getAbilityControlBlock().getAbilityEmbryoListList();
        for (var embryo : block) {
            var ability = entity.getInstancedAbilities().get(embryo.getAbilityId() - 1);
            if (ability != null) assertEquals(embryo.getAbilityNameHash(), ability.getHash());
        }
        assertTrue(
                world.packets.stream()
                        .anyMatch(p -> p.getOpcode() == PacketOpcodes.SceneTeamUpdateNotify));
        boolean synchronizedParameter = false;
        for (var packet : player.packets)
            if (packet.getOpcode() == PacketOpcodes.AbilityInvocationsNotify)
                for (var invoke :
                        AbilityInvocationsNotify.parseFrom(packet.getData()).getInvokesList()) {
                    var scalar = AbilityScalarValueEntry.parseFrom(invoke.getAbilityData());
                    if (invoke.getEntityId() == entity.getId()
                            && scalar.getKey().hasHash()
                            && scalar.getKey().getHash()
                                    == Utils.abilityHash("TeamResonance_Water_Lv2_MaxHPRatio")) {
                        var bound =
                                entity.getInstancedAbilities()
                                        .get(invoke.getHead().getInstancedAbilityId() - 1);
                        assertEquals("TeamResonance_Water_Lv2", bound.getData().abilityName);
                        assertEquals(.25f, scalar.getFloatValue());
                        synchronizedParameter = true;
                    }
                }
        assertTrue(synchronizedParameter);
        int size = entity.getInstancedAbilities().size();
        refresh();
        assertEquals(size, entity.getInstancedAbilities().size());
        assertEquals(125f, entity.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP), 0.0001f);
    }

    @Test
    void grassMasteryUsesActualFiftyAndRemovesItOnRosterChange() {
        party(ElementType.Grass, ElementType.Grass, ElementType.Fire, ElementType.Water);
        refresh();
        var entity = first();
        initialize(entity);
        assertEquals(50f, entity.getFightProperty(FightProperty.FIGHT_PROP_ELEMENT_MASTERY));
        party(ElementType.Grass, ElementType.Fire, ElementType.Water, ElementType.Ice);
        refresh();
        assertEquals(0f, entity.getFightProperty(FightProperty.FIGHT_PROP_ELEMENT_MASTERY));
    }

    @Test
    void departedAvatarLosesResonanceEvenWhenRemainingRosterKeepsIt() {
        refresh();
        var departing = entities.get(10000015);
        initialize(departing);
        assertEquals(
                12.5f, departing.getFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK), .0001f);
        party(ElementType.Fire, ElementType.Fire, ElementType.Water, ElementType.Grass);
        refresh();
        assertEquals(List.of(10101), active());
        assertEquals(10f, departing.getFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK), .0001f);
        assertTrue(departing.getAppliedAbilityModifiers().isEmpty());
    }

    @Test
    void coOpUsesSharedRosterAndUpdatesBothOwners() throws Exception {
        var multiplayer = emu.grasscutter.game.world.World.class.getDeclaredField("isMultiplayer");
        multiplayer.setAccessible(true);
        multiplayer.setBoolean(world, true);
        var guest = new SkillTalentLevelTest.RecordingPlayer();
        guest.setUid(73558);
        attach(guest);
        var fire = addAvatar(guest, 10000016);
        var ice = addAvatar(guest, 10000015);
        select(player, List.of(entities.get(10000016), entities.get(10000014)), true);
        select(guest, List.of(fire, ice), true);
        refresh();
        assertEquals(List.of(10101), active());
        assertEquals(
                player.getTeamManager().getTeamResonances(),
                guest.getTeamManager().getTeamResonances());
        initialize(first());
        initialize(fire);
        assertEquals(
                .25f,
                find(fire, "TeamResonance_Fire_Lv2")
                        .getAbilitySpecials()
                        .getFloat("TeamResonance_Fire_Lv2_AttackRatio"));
        select(guest, List.of(addAvatar(guest, 10000033), ice), true);
        refresh();
        assertEquals(List.of(10201), active());
        assertEquals(
                player.getTeamManager().getTeamResonances(),
                guest.getTeamManager().getTeamResonances());
        assertEquals(10f, first().getFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK), 0.0001f);
        assertEquals(125f, first().getFightProperty(FightProperty.FIGHT_PROP_MAX_HP), 0.0001f);
        world.getPlayers().remove(guest);
        guest.setWorld(null);
        assertTrue(active().isEmpty());
    }

    @Test
    void actualTravelerElementChangeRefreshesResonanceAndKeepsOtherSkills() {
        var traveler = addAvatar(player, 10000005);
        traveler.getAvatar()
                .setSkillDepotData(GameData.getAvatarSkillDepotDataMap().get(504), false);
        select(
                player,
                List.of(
                        traveler,
                        entities.get(10000003),
                        entities.get(10000014),
                        entities.get(10000015)),
                true);
        refresh();
        initialize(traveler);
        var skill = find(traveler, SKILL);
        assertEquals(List.of(10301), active());
        assertEquals(
                -.15f,
                find(traveler, "TeamResonance_Wind_Lv2")
                        .getAbilitySpecials()
                        .getFloat("TeamResonance_Wind_Lv2_CostStaminaRatio"),
                .00001f);
        traveler.getAvatar()
                .setSkillDepotData(GameData.getAvatarSkillDepotDataMap().get(506), false);
        assertEquals(List.of(10801), active());
        assertSame(skill, find(traveler, SKILL));
        assertTrue(
                traveler.getAppliedAbilityModifiers().keySet().stream()
                        .noneMatch(a -> a.getData().abilityName.equals("TeamResonance_Wind_Lv2")));
        assertEquals(
                .15f,
                find(traveler, "TeamResonance_AllDifferent")
                        .getAbilitySpecials()
                        .getFloat("TeamResonance_AllDifferent_SubHurtDelta"),
                .00001f);
    }

    @Test
    void loginBeforeEntityConstructionAnnouncesSelectedResonance() {
        player.getTeamManager().getActiveTeam().clear();
        player.loggedIn = false;
        player.getTeamManager().onPlayerLogin();
        assertTrue(player.getTeamManager().getTeamResonances().contains(10101));
        assertTrue(
                player.packets.stream()
                        .noneMatch(p -> p.getOpcode() == PacketOpcodes.AbilityInvocationsNotify));
    }
}
