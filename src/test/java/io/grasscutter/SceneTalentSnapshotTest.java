package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.command.commands.DebugCommand;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.ResourceLoader.OpenConfigData;
import emu.grasscutter.data.binout.OpenConfigEntry;
import emu.grasscutter.data.excels.ProudSkillData;
import emu.grasscutter.data.excels.avatar.AvatarSkillData;
import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.chat.ChatSystem;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.EntityTeam;
import emu.grasscutter.net.proto.AbilitySyncStateInfoOuterClass.AbilitySyncStateInfo;
import emu.grasscutter.net.proto.PlayerEnterSceneInfoNotifyOuterClass.PlayerEnterSceneInfoNotify;
import emu.grasscutter.server.packet.send.PacketPlayerEnterSceneInfoNotify;
import emu.grasscutter.utils.JsonUtils;
import emu.grasscutter.utils.Utils;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/** The actual scene snapshot must not index ModifyAbility's numeric operand as a parameter. */
class SceneTalentSnapshotTest extends QuestTestFixture {
    private static final Map<Integer, ProudSkillData> PROUDS = new HashMap<>();
    private static final Map<Integer, AvatarSkillData> SKILLS = new HashMap<>();
    private static final Map<Integer, AvatarSkillDepotData> DEPOTS = new HashMap<>();
    private static final Map<String, OpenConfigEntry> CONFIGS = new HashMap<>();

    @BeforeAll
    static void resources() throws Exception {
        for (var row :
                JsonUtils.loadToList(
                        Path.of("resources/ExcelBinOutput/ProudSkillExcelConfigData.json"),
                        ProudSkillData.class)) PROUDS.put(row.getId(), row);
        for (var row :
                JsonUtils.loadToList(
                        Path.of("resources/ExcelBinOutput/AvatarSkillExcelConfigData.json"),
                        AvatarSkillData.class)) SKILLS.put(row.getId(), row);
        for (var row :
                JsonUtils.loadToList(
                        Path.of("resources/ExcelBinOutput/AvatarSkillDepotExcelConfigData.json"),
                        AvatarSkillDepotData.class)) DEPOTS.put(row.getId(), row);
        try (var files =
                Files.newDirectoryStream(
                        Path.of("resources/BinOutput/Talent/AvatarTalents"), "*.json")) {
            for (var file : files)
                for (var row :
                        com.google.gson.JsonParser.parseString(Files.readString(file))
                                .getAsJsonObject()
                                .entrySet())
                    CONFIGS.put(
                            row.getKey(),
                            new OpenConfigEntry(
                                    row.getKey(),
                                    JsonUtils.decode(row.getValue(), OpenConfigData[].class)));
        }
    }

    private Avatar model(boolean cold) throws Exception {
        var avatar = new Avatar();
        replaceField(Avatar.class, avatar, "avatarId", 10000073);
        replaceField(Avatar.class, avatar, "skillDepotId", 7301);
        replaceField(Avatar.class, avatar, "skillLevelMap", new HashMap<>(Map.of(10731, 1)));
        replaceField(Avatar.class, avatar, "talentIdList", new HashSet<Integer>());
        replaceField(Avatar.class, avatar, "proudSkillList", new HashSet<>(Set.of(733101)));
        if (cold) {
            store.getMapper().map(Avatar.class);
            store.save(avatar);
            avatar = store.find(Avatar.class).first();
            assertNotNull(avatar);
            assertNull(avatar.getSkillDepot());
        }
        var depot =
                JsonUtils.decode(
                        "{id:7301,skills:[10731],energySkill:0,talents:[]}",
                        AvatarSkillDepotData.class);
        put(GameData.getAvatarSkillDepotDataMap(), 7301, depot);
        put(
                GameData.getAvatarSkillDataMap(),
                10731,
                JsonUtils.decode("{id:10731,proudSkillGroupId:7331}", AvatarSkillData.class));
        replaceField(Avatar.class, avatar, "skillDepot", depot);
        // Morphia's null collection handling is independent of the scene-variable failure.
        if (avatar.getProudSkillList() == null)
            replaceField(Avatar.class, avatar, "proudSkillList", new HashSet<>(Set.of(733101)));
        avatar.setOwner(player);
        player.getAvatars().getAvatars().put(avatar.getAvatarId(), avatar);
        player.getTeamManager().getActiveTeam().add(new EntityAvatar(player.getScene(), avatar));
        player.getTeamManager().setEntity(new EntityTeam(player));
        player.setEnterSceneToken(731);
        return avatar;
    }

    private <K, V> void put(Map<K, V> map, K key, V value) throws Exception {
        var previous = map.put(key, value);
        restores.add(
                () -> {
                    if (previous == null) map.remove(key);
                    else map.put(key, previous);
                });
    }

    private final List<Runnable> restores = new ArrayList<>();

    @org.junit.jupiter.api.AfterEach
    void restoreResources() {
        Collections.reverse(restores);
        restores.forEach(Runnable::run);
    }

    private void config(String rows, float... parameters) throws Exception {
        put(
                GameData.getOpenConfigEntries(),
                "SceneSnapshot",
                new OpenConfigEntry(
                        "SceneSnapshot", JsonUtils.decode(rows, OpenConfigData[].class)));
        put(
                GameData.getProudSkillDataMap(),
                733101,
                JsonUtils.decode(
                        "{proudSkillId:733101,proudSkillGroupId:7331,level:1,"
                                + "openConfig:'SceneSnapshot',paramList:"
                                + JsonUtils.encode(parameters)
                                + "}",
                        ProudSkillData.class));
    }

    private static String absolute(String value) {
        return "{\"$type\":\"SetAbilityVar\",\"abilityName\":\"Avatar_Test\","
                + "\"varName\":\"Absolute\",\"varValue\":\""
                + value
                + "\"}";
    }

    private static String modify(String delta) {
        return "{\"$type\":\"ModifyAbility\",\"abilityName\":\"Avatar_Test\","
                + "\"varName\":\"Delta\",\"varValue\":"
                + delta
                + ",\"paramRatio\":0}";
    }

    private AbilitySyncStateInfo snapshot() {
        var info = AbilitySyncStateInfo.newBuilder();
        assertDoesNotThrow(
                () ->
                        PacketPlayerEnterSceneInfoNotify.buildAvatarAbilityVars(
                                player.getTeamManager().getCurrentAvatarEntity(), info));
        return info.build();
    }

    private Map<String, Float> values(AbilitySyncStateInfo info) {
        return info.getDynamicValueMapList().stream()
                .collect(
                        Collectors.toMap(
                                e -> e.getKey().getStr(),
                                e -> {
                                    assertTrue(e.getKey().hasStr());
                                    assertTrue(Float.isFinite(e.getFloatValue()));
                                    return e.getFloatValue();
                                },
                                (first, last) -> last));
    }

    @Test
    void actual_scene_packets_include_live_root_and_weapon_overrides() throws Exception {
        config(
                "[{\"$type\":\"UnlockTalentParam\",\"abilityName\":\"Avatar_Test\",\"talentParam\":\"Unlocked\"}]");
        var avatar = model(false);
        replaceField(
                Avatar.class,
                avatar,
                "avatarData",
                JsonUtils.decode(
                        "{id:10000073,abilities:[]}",
                        emu.grasscutter.data.excels.avatar.AvatarData.class));
        var entity = player.getTeamManager().getCurrentAvatarEntity();
        player.getScene().getEntities().put(entity.getId(), entity);
        if (!player.getWorld().getPlayers().contains(player))
            player.getWorld().getPlayers().add(player);
        var data =
                JsonUtils.decode(
                        "{abilityName:'Avatar_Test',abilitySpecials:{rate:7}}",
                        emu.grasscutter.data.binout.AbilityData.class);
        var root = new emu.grasscutter.game.ability.Ability(data, entity, player);
        entity.getInstancedAbilities().add(root);
        root.getAbilitySpecials().put("rate", 4);
        var item =
                new emu.grasscutter.game.inventory.GameItem(
                        JsonUtils.decode(
                                "{id:11101,itemType:'ITEM_WEAPON',equipType:'EQUIP_WEAPON',gadgetId:5001,skillAffix:[123]}",
                                emu.grasscutter.data.excels.ItemData.class));
        avatar.getEquips()
                .put(emu.grasscutter.game.inventory.EquipType.EQUIP_WEAPON.getValue(), item);
        var weapon = new emu.grasscutter.game.entity.EntityWeapon(player.getScene(), 5001, avatar);
        player.getScene().getWeaponEntities().put(weapon.getId(), weapon);
        item.setWeaponEntity(weapon);
        var weaponRoot = new emu.grasscutter.game.ability.Ability(data, weapon, player);
        weapon.getInstancedAbilities().add(weaponRoot);
        weaponRoot.getAbilitySpecials().put("rate", 3);
        var enter =
                PlayerEnterSceneInfoNotify.parseFrom(
                                new PacketPlayerEnterSceneInfoNotify(player).getData())
                        .getAvatarEnterInfo(0);
        assertEquals(4, rootValue(enter.getAvatarAbilityInfo(), "rate"));
        assertEquals(1, rootValue(enter.getAvatarAbilityInfo(), "Unlocked"));
        assertEquals(3, rootValue(enter.getWeaponAbilityInfo(), "rate"));
        assertEquals(
                4, rootValue(entity.toProto().getEntityAuthorityInfo().getAbilityInfo(), "rate"));
        assertEquals(3, rootValue(item.createSceneWeaponInfo().getAbilityInfo(), "rate"));
        var team =
                emu.grasscutter.net.proto.SceneTeamUpdateNotifyOuterClass.SceneTeamUpdateNotify
                        .parseFrom(
                                new emu.grasscutter.server.packet.send.PacketSceneTeamUpdateNotify(
                                                player)
                                        .getData())
                        .getSceneTeamAvatarList(0);
        assertEquals(4, rootValue(team.getAvatarAbilityInfo(), "rate"));
        assertEquals(3, rootValue(team.getWeaponAbilityInfo(), "rate"));
        assertFalse(enter.getAvatarAbilityInfo().getIsInited());
        assertFalse(enter.getWeaponAbilityInfo().getIsInited());
    }

    private float rootValue(AbilitySyncStateInfo info, String name) {
        return info.getAppliedAbilitiesList().stream()
                .flatMap(a -> a.getOverrideMapList().stream())
                .filter(
                        v ->
                                v.getKey().hasStr()
                                        ? v.getKey().getStr().equals(name)
                                        : v.getKey().getHash() == Utils.abilityHash(name))
                .reduce((first, last) -> last)
                .orElseThrow()
                .getFloatValue();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void a_numeric_modify_operand_cannot_abort_the_full_scene_packet(boolean cold)
            throws Exception {
        config("[" + modify("2.5") + "," + absolute("%1") + "]", 7f);
        var avatar = model(cold);
        var packet = assertDoesNotThrow(() -> new PacketPlayerEnterSceneInfoNotify(player));
        var proto = PlayerEnterSceneInfoNotify.parseFrom(packet.getData());
        assertEquals(731, proto.getEnterSceneToken());
        assertEquals(1, proto.getAvatarEnterInfoCount());
        assertEquals(avatar.getGuid(), proto.getAvatarEnterInfo(0).getAvatarGuid());
        assertEquals(
                Map.of("Absolute", 7f), values(proto.getAvatarEnterInfo(0).getAvatarAbilityInfo()));
        // Round-tripping through the same generated schema hid the old 50000 placeholder.
        var nativeEntry =
                com.google.protobuf.CodedInputStream.newInstance(
                        proto.getAvatarEnterInfo(0).toByteArray());
        AbilitySyncStateInfo nativeAvatarInfo = null;
        for (int tag; (tag = nativeEntry.readTag()) != 0; ) {
            if (tag == 42)
                nativeAvatarInfo = AbilitySyncStateInfo.parseFrom(nativeEntry.readBytes());
            else nativeEntry.skipField(tag);
        }
        assertNotNull(nativeAvatarInfo, "Current client reads avatar ability snapshot at field 5");
        assertEquals(Map.of("Absolute", 7f), values(nativeAvatarInfo));
    }

    @Test
    void parameter_based_modify_operands_are_not_sent_as_absolute_values() throws Exception {
        config("[" + modify("\"%1\"") + "," + absolute("%2") + "]", 4f, 9f);
        model(false);
        assertEquals(Map.of("Absolute", 9f), values(snapshot()));
    }

    @Test
    void signed_parameter_references_keep_their_sign() throws Exception {
        config("[" + absolute("-%1") + "]", 7f);
        model(false);
        assertEquals(Map.of("Absolute", -7f), values(snapshot()));
    }

    @Test
    void a_zero_parameter_is_still_a_valid_absolute_value() throws Exception {
        config("[" + absolute("%1") + "]", 0f);
        model(false);
        assertEquals(Map.of("Absolute", 0f), values(snapshot()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"%2", "%2147483647"})
    void missing_parameters_do_not_overwrite_defaults(String value) throws Exception {
        config("[" + absolute(value) + "]", 3f);
        model(false);
        assertTrue(values(snapshot()).isEmpty());
    }

    @Test
    void invalid_parameter_values_do_not_enter_the_protocol() throws Exception {
        config("[" + absolute("%1") + "]", 1f);
        GameData.getProudSkillDataMap().get(733101).getParamList()[0] = Float.NaN;
        model(false);
        assertTrue(values(snapshot()).isEmpty());
    }

    @Test
    void talent_unlocks_survive_a_numeric_modify_with_no_parameter_array() throws Exception {
        config(
                "["
                        + modify("1")
                        + ","
                        + "{\"$type\":\"UnlockTalentParam\",\"abilityName\":\"Avatar_Test\","
                        + "\"talentParam\":\"Unlocked\"}]");
        model(false);
        var info = snapshot();
        assertTrue(values(info).isEmpty());
        assertFalse(info.getAppliedAbilitiesList().isEmpty());
        var applied = info.getAppliedAbilities(0);
        assertEquals(Utils.abilityHash("Avatar_Test"), applied.getAbilityName().getHash());
        assertEquals(Utils.abilityHash("Unlocked"), applied.getOverrideMap(0).getKey().getHash());
        assertEquals(1f, applied.getOverrideMap(0).getFloatValue());
    }

    @Test
    void the_debug_command_can_inspect_the_same_numeric_operand() throws Exception {
        config("[" + modify("2.5") + "]", 7f);
        model(false);
        var previousChat = player.server.getChatSystem();
        var feedback = new ArrayList<String>();
        player.server.setChatSystem(
                new ChatSystem(player.server) {
                    @Override
                    public void sendPrivateMessageFromServer(int uid, String message) {
                        assertEquals(player.getUid(), uid);
                        feedback.add(message);
                    }
                });
        restores.add(() -> player.server.setChatSystem(previousChat));
        assertDoesNotThrow(
                () ->
                        new DebugCommand()
                                .execute(player, player, new ArrayList<>(List.of("dynamicmap"))));
        assertEquals(1, feedback.size());
        assertTrue(feedback.get(0).contains("dynamicValueMap"));
    }

    @ParameterizedTest
    @ValueSource(ints = {10000087, 10000096, 10000089, 10000073})
    void the_affected_party_uses_real_skill_and_passive_resources(int avatarId) throws Exception {
        var avatar = model(false);
        var depot = DEPOTS.get((avatarId - 10000000) * 100 + 1);
        assertNotNull(depot);
        replaceField(Avatar.class, avatar, "avatarId", avatarId);
        replaceField(Avatar.class, avatar, "skillDepotId", depot.getId());
        replaceField(Avatar.class, avatar, "skillDepot", depot);
        put(GameData.getAvatarSkillDepotDataMap(), depot.getId(), depot);
        var proudIds = new HashSet<Integer>();
        var levels = new HashMap<Integer, Integer>();
        for (int skillId : depot.getSkillsAndEnergySkill().toArray()) {
            var skill = SKILLS.get(skillId);
            assertNotNull(skill);
            put(GameData.getAvatarSkillDataMap(), skillId, skill);
            levels.put(skillId, 1);
            if (skill.getProudSkillGroupId() > 0)
                proudIds.add(skill.getProudSkillGroupId() * 100 + 1);
        }
        var passives = new HashSet<Integer>();
        if (depot.getInherentProudSkillOpens() != null)
            for (var passive : depot.getInherentProudSkillOpens())
                if (passive.getProudSkillGroupId() > 0)
                    passives.add(passive.getProudSkillGroupId() * 100 + 1);
        proudIds.addAll(passives);
        for (int proudId : proudIds) {
            var proud = PROUDS.get(proudId);
            assertNotNull(proud);
            put(GameData.getProudSkillDataMap(), proudId, proud);
            if (proud.getOpenConfig() != null && !proud.getOpenConfig().isEmpty()) {
                var config = CONFIGS.get(proud.getOpenConfig());
                assertNotNull(config);
                put(GameData.getOpenConfigEntries(), proud.getOpenConfig(), config);
            }
        }
        replaceField(Avatar.class, avatar, "skillLevelMap", levels);
        replaceField(Avatar.class, avatar, "proudSkillList", passives);
        var packet = assertDoesNotThrow(() -> new PacketPlayerEnterSceneInfoNotify(player));
        var proto = PlayerEnterSceneInfoNotify.parseFrom(packet.getData());
        assertEquals(1, proto.getAvatarEnterInfoCount());
        values(proto.getAvatarEnterInfo(0).getAvatarAbilityInfo());
    }
}
