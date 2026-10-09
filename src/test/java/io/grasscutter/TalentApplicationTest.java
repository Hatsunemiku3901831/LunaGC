package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import emu.grasscutter.data.*;
import emu.grasscutter.data.ResourceLoader.OpenConfigData;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.data.excels.avatar.*;
import emu.grasscutter.data.excels.tps.*;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.inventory.EquipType;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.tps.TpsWeaponSystem;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.AbilityAppliedAbilityOuterClass.AbilityAppliedAbility;
import emu.grasscutter.net.proto.AbilityInvocationsNotifyOuterClass.AbilityInvocationsNotify;
import emu.grasscutter.net.proto.AbilityInvokeEntryHeadOuterClass.AbilityInvokeEntryHead;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.net.proto.AbilityMetaAddAbilityOuterClass.AbilityMetaAddAbility;
import emu.grasscutter.net.proto.AbilityMetaReInitOverrideMapOuterClass.AbilityMetaReInitOverrideMap;
import emu.grasscutter.net.proto.AbilityScalarValueEntryOuterClass.AbilityScalarValueEntry;
import emu.grasscutter.net.proto.AbilityStringOuterClass.AbilityString;
import emu.grasscutter.net.proto.EvtCreateGadgetNotifyOuterClass.EvtCreateGadgetNotify;
import emu.grasscutter.utils.JsonUtils;
import it.unimi.dsi.fastutil.ints.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;

class TalentApplicationTest {
    private AbilityTestFixture fixture;
    private TestAvatar avatar;
    private EntityAvatar entity;
    private GameItem gun;
    private final List<Runnable> restore = new ArrayList<>();
    private static final Map<String, OpenConfigEntry> REAL_CONFIGS = new LinkedHashMap<>();
    private static List<EquipAffixData> realAffixes;
    private static List<TpsWeaponAccessoryData> realAccessories;
    private static List<TpsWeaponData> realWeapons;

    @BeforeAll
    static void resources() throws Exception {
        var talents = Path.of("resources/BinOutput/Talent/EquipTalents");
        try (var files = Files.list(talents)) {
            for (var file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                var parsed = JsonParser.parseString(Files.readString(file));
                if (!parsed.isJsonObject()) continue;
                var root = parsed.getAsJsonObject();
                for (var row : root.entrySet())
                    if (row.getKey().startsWith("TPS_"))
                        REAL_CONFIGS.put(
                                row.getKey(),
                                new OpenConfigEntry(
                                        row.getKey(),
                                        JsonUtils.decode(row.getValue().toString(), OpenConfigData[].class)));
            }
        }
        realAffixes =
                JsonUtils.loadToList(
                                Path.of("resources/ExcelBinOutput/EquipAffixExcelConfigData.json"),
                                EquipAffixData.class)
                        .stream()
                        .filter(d -> d.getOpenConfig() != null && d.getOpenConfig().startsWith("TPS_"))
                        .toList();
        realWeapons =
                JsonUtils.loadToList(
                        Path.of("resources/ExcelBinOutput/TpsWeaponExcelConfigData.json"), TpsWeaponData.class);
        realAccessories =
                JsonUtils.loadToList(
                        Path.of("resources/ExcelBinOutput/TpsWeaponAccessoryExcelConfigData.json"),
                        TpsWeaponAccessoryData.class);
    }

    @BeforeEach
    void setup() throws Exception {
        fixture = new AbilityTestFixture();
        fixture.player.setUid(73001);
        REAL_CONFIGS.forEach((key, value) -> put(GameData.getOpenConfigEntries(), key, value));
        realAffixes.forEach(d -> put(GameData.getEquipAffixDataMap(), d.getId(), d));
        realAccessories.forEach(d -> put(GameData.getTpsWeaponAccessoryDataMap(), d.getId(), d));
        realWeapons.forEach(d -> put(GameData.getTpsWeaponDataMap(), d.getId(), d));
        avatar = new TestAvatar(fixture.player);
        entity = addAvatar(avatar);
        gun = item(224001, "ITEM_TPS_WEAPON");
        avatar.getTpsWeaponIds().add(224001);
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

    static class TestAvatar extends Avatar {
        final Player player;
        final IntSet talents = new IntOpenHashSet();
        final Set<Integer> proud = new LinkedHashSet<>();
        final Map<Integer, Integer> levels = new LinkedHashMap<>();

        TestAvatar(Player player) {
            this.player = player;
        }

        @Override
        public Player getPlayer() {
            return player;
        }

        @Override
        public int getAvatarId() {
            return 10000134;
        }

        @Override
        public int getSkillDepotId() {
            return 50001;
        }

        @Override
        public AvatarData getAvatarData() {
            return new AvatarData() {
                @Override
                public float getBaseHp(int level) {
                    return 100f;
                }

                @Override
                public float getBaseAttack(int level) {
                    return 10f;
                }

                @Override
                public float getBaseDefense(int level) {
                    return 10f;
                }
            };
        }

        @Override
        public IntSet getTalentIdList() {
            return talents;
        }

        @Override
        public Set<Integer> getProudSkillList() {
            return proud;
        }

        @Override
        public Map<Integer, Integer> getSkillLevelMap() {
            return levels;
        }

        @Override
        public void save() {}
    }

    private EntityAvatar addAvatar(TestAvatar value) {
        var result =
                new EntityAvatar(fixture.scene, value) {
                    @Override
                    public void initAbilities() {}
                };
        fixture.scene.getEntities().put(result.getId(), result);
        fixture.player.getTeamManager().getActiveTeam().add(result);
        return result;
    }

    private GameItem item(int id, String type) {
        var data =
                JsonUtils.decode(
                        "{\"id\":"
                                + id
                                + ",\"itemType\":\""
                                + type
                                + "\",\"gadgetId\":50016001,\"equipType\":\"EQUIP_WEAPON\",\"skillAffix\":[7001]}",
                        ItemData.class);
        put(GameData.getItemDataMap(), id, data);
        var result = new GameItem(data);
        result.setOwner(fixture.player);
        fixture.player.getInventory().getItems().put(result.getGuid(), result);
        return result;
    }

    private Ability ability(String name, GameEntity owner) {
        return new Ability(
                JsonUtils.decode(
                        "{\"abilityName\":\"" + name + "\",\"abilitySpecials\":{\"Value\":8}}",
                        AbilityData.class),
                owner,
                fixture.player);
    }

    private boolean unlocked(Ability ability, String flag) {
        return PredicateEvaluator.evaluate(
                Map.of("$type", "ByUnlockTalentParam", "talentParam", flag),
                ability,
                ability.getOwner(),
                fixture.entity,
                null);
    }

    private void accessory(int encodedAffix) {
        var data =
                realAccessories.stream()
                        .filter(d -> d.getEquipAffixId() == encodedAffix)
                        .findFirst()
                        .orElseThrow();
        gun.getTpsAccessoryIds().add(data.getId());
    }

    private void affix(int id, String config, float... params) {
        put(
                GameData.getEquipAffixDataMap(),
                id,
                JsonUtils.decode(
                        "{\"affixId\":"
                                + id
                                + ",\"openConfig\":\""
                                + config
                                + "\",\"paramList\":"
                                + JsonUtils.encode(params)
                                + "}",
                        EquipAffixData.class));
    }

    private void config(String name, String rows) {
        put(
                GameData.getOpenConfigEntries(),
                name,
                new OpenConfigEntry(name, JsonUtils.decode(rows, OpenConfigData[].class)));
    }

    @Test
    void realScopeNegativeReferenceSurvivesParsing() {
        var entry = REAL_CONFIGS.get("TPS_Weapon_IceGun_Accessory_Scope");
        assertNotNull(entry.getAbilityVarSetters(), "-%1 must not discard the setter");
        assertEquals(0, entry.getAbilityVarSetters()[0].getParamIndex());
    }

    @Test
    void wornScopeAppliesNegativeParameterOnlyToItsAbility() {
        accessory(2240141);
        // A nonzero fixture exercises the expression: shipped TPS affix parameters are all zero.
        affix(2240141, "TPS_Weapon_IceGun_Accessory_Scope", .35f);
        var scoped = ability("TPS_IceGun_Accessory_Manager", entity);
        assertEquals(
                -.35f,
                scoped.getAbilitySpecials().getFloat("IceGun_Accessory_Scope_ResistStripRatio"),
                .00001f);
        assertFalse(
                ability("TPS_ElectricGun_Accessory_Manager", entity)
                        .getAbilitySpecials()
                        .containsKey("IceGun_Accessory_Scope_ResistStripRatio"));
    }

    @Test
    void realMagazineUnlockIsScopedAndTracksUnequip() {
        accessory(2240121);
        var ice = ability("TPS_IceGun_Accessory_Manager", entity);
        assertTrue(unlocked(ice, "Unlock_Accessory_Magazine"));
        assertFalse(
                unlocked(
                        ability("TPS_ElectricGun_Accessory_Manager", entity), "Unlock_Accessory_Magazine"));
        avatar.getTpsWeaponIds().clear();
        assertFalse(unlocked(ice, "Unlock_Accessory_Magazine"));
    }

    @Test
    void foreignAccessoryAndEmptyInventoryDoNotUnlock() {
        var electricAccessory =
                realAccessories.stream()
                        .filter(d -> d.getTpsWeaponId() == 224002)
                        .findFirst()
                        .orElseThrow();
        gun.getTpsAccessoryIds().add(electricAccessory.getId());
        assertFalse(
                unlocked(
                        ability("TPS_ElectricGun_Accessory_Manager", entity),
                        "Unlock_Accessory_Barrel&Muzzle"));
        accessory(2240121);
        gun.setCount(0);
        assertFalse(
                unlocked(ability("TPS_IceGun_Accessory_Manager", entity), "Unlock_Accessory_Magazine"));
    }

    @Test
    void foreignOwnedWeaponCannotApplyParameters() {
        accessory(2240121);
        affix(2240121, "TPS_Weapon_IceGun_Accessory_Magazine", .65f);
        var stranger = new Player();
        stranger.setUid(73002);
        gun.setOwner(stranger);
        assertFalse(
                ability("TPS_IceGun_Accessory_Manager", entity)
                        .getAbilitySpecials()
                        .containsKey("IceGun_Accessory_Magazine_FireIntervalRatio"));
    }

    @Test
    void eachAbilityHasIndependentWeaponParameters() {
        accessory(2240121);
        affix(2240121, "TPS_Weapon_IceGun_Accessory_Magazine", .65f);
        var first = ability("TPS_IceGun_Accessory_Manager", entity);
        affix(2240121, "TPS_Weapon_IceGun_Accessory_Magazine", .4f);
        var second = ability("TPS_IceGun_Accessory_Manager", entity);
        assertEquals(
                .65f, first.getAbilitySpecials().getFloat("IceGun_Accessory_Magazine_FireIntervalRatio"));
        assertEquals(
                .4f, second.getAbilitySpecials().getFloat("IceGun_Accessory_Magazine_FireIntervalRatio"));
        assertFalse(
                first.getData().abilitySpecials.containsKey("IceGun_Accessory_Magazine_FireIntervalRatio"));
    }

    @Test
    void weaponRefinementSelectsItsOwnAffixLevel() {
        config(
                "TestWeapon",
                "[{\"$type\":\"SetAbilityVar\",\"abilityName\":\"TestWeaponAbility\",\"varName\":\"Value\",\"varValue\":\"%1\"}]");
        affix(70010, "TestWeapon", .2f);
        affix(70012, "TestWeapon", .7f);
        var weapon = item(11499, "ITEM_WEAPON");
        avatar.getEquips().put(EquipType.EQUIP_WEAPON.getValue(), weapon);
        assertEquals(.2f, ability("TestWeaponAbility", entity).getAbilitySpecials().getFloat("Value"));
        weapon.setRefinement(2);
        assertEquals(.7f, ability("TestWeaponAbility", entity).getAbilitySpecials().getFloat("Value"));
    }

    @Test
    void proudSkillUnlockReadsDeclaredFlagAndAbilityScope() {
        config(
                "TestPassive",
                "[{\"$type\":\"UnlockTalentParam\",\"abilityName\":\"PassiveAbility\",\"talentParam\":\"SeparateFlag\"}]");
        put(
                GameData.getProudSkillDataMap(),
                900001,
                JsonUtils.decode(
                        "{\"proudSkillId\":900001,\"openConfig\":\"TestPassive\"}", ProudSkillData.class));
        avatar.proud.add(900001);
        assertTrue(unlocked(ability("PassiveAbility", entity), "SeparateFlag"));
        assertFalse(unlocked(ability("AnotherAbility", entity), "SeparateFlag"));
    }

    private EntityClientGadget projectile(GameEntity owner, int id) {
        var result =
                new EntityClientGadget(
                        fixture.scene,
                        fixture.player,
                        EvtCreateGadgetNotify.newBuilder()
                                .setEntityId(id)
                                .setOwnerEntityId(owner.getId())
                                .build());
        fixture.scene.getEntities().put(id, result);
        return result;
    }

    @Test
    void projectileKeepsOriginalCasterAfterPartySelectionChanges() {
        var projectile = projectile(entity, 991);
        var shot = ability("TPS_IceGun_Accessory_Manager", projectile);
        var other = addAvatar(new TestAvatar(fixture.player));
        fixture.player.getTeamManager().getActiveTeam().remove(entity);
        fixture.player.getTeamManager().getActiveTeam().add(entity);
        fixture.player.getTeamManager().setCurrentCharacterIndex(0);
        assertSame(entity, shot.getCasterEntity());
        assertNotSame(other, shot.getCasterEntity());
    }

    @Test
    void nestedProjectileUsesOriginalAvatarUnlocks() {
        accessory(2240121);
        var child = projectile(projectile(entity, 992), 993);
        var shot = ability("TPS_IceGun_Accessory_Manager", child);
        assertSame(entity, shot.getCasterEntity());
        assertTrue(unlocked(shot, "Unlock_Accessory_Magazine"));
    }

    @Test
    void tpsWeaponEntityResolvesItsEquippedCaster() {
        TpsWeaponSystem.ensureWeaponEntities(avatar, fixture.scene);
        var weaponAbility = ability("TPS_IceGun_Accessory_Manager", gun.getWeaponEntity());
        assertSame(entity, weaponAbility.getCasterEntity());
    }

    @Test
    void malformedTalentRowDoesNotDiscardLaterValidRows() {
        assertDoesNotThrow(
                () ->
                        new OpenConfigEntry(
                                "Test",
                                JsonUtils.decode(
                                        "[null,"
                                                + "{\"$type\":\"SetAbilityVar\",\"abilityName\":\"Test\",\"varName\":\"bad\",\"varValue\":\"-%0\"},"
                                                + "{\"$type\":\"SetAbilityVar\",\"abilityName\":\"Test\",\"varName\":\"ok\",\"varValue\":{\"$type\":\"Wrapped\",\"MLHKFPOMJDJ\":\"%1\"}}]",
                                        OpenConfigData[].class)));
    }

    @Test
    void allTwelveRealAccessoriesUnlockOnlyTheirDeclaredWeaponAbility() {
        assertEquals(12, realAccessories.size());
        for (var accessory : realAccessories) {
            var affix =
                    realAffixes.stream()
                            .filter(d -> d.getId() == accessory.getEquipAffixId())
                            .findFirst()
                            .orElseThrow();
            var flag = REAL_CONFIGS.get(affix.getOpenConfig()).getTalentParamEntries()[0];
            var weapon = TpsWeaponSystem.findOwnedWeapon(fixture.player, accessory.getTpsWeaponId());
            if (weapon == null) weapon = item(accessory.getTpsWeaponId(), "ITEM_TPS_WEAPON");
            weapon.getTpsAccessoryIds().clear();
            weapon.getTpsAccessoryIds().add(accessory.getId());
            avatar.getTpsWeaponIds().clear();
            avatar.getTpsWeaponIds().add(weapon.getItemId());
            assertTrue(
                    unlocked(ability(flag.getAbilityName(), entity), flag.getParamName()),
                    affix.getOpenConfig());
            assertFalse(
                    unlocked(ability("OtherWeaponAbility", entity), flag.getParamName()),
                    affix.getOpenConfig());
        }
    }

    @Test
    void actualZeroWeaponParametersAreReadWithoutInventingDamageValues() {
        assertEquals(20, realAffixes.size());
        for (var affix : realAffixes) assertArrayEquals(new float[8], affix.getParamList());
        var shot = ability("TPS_Weapon_AutoWeapon_AssaultRifle_01_Fire", entity);
        assertTrue(shot.getAbilitySpecials().containsKey("IceGun_Bullet_Damage_Ratio"));
        assertEquals(0f, shot.getAbilitySpecials().getFloat("IceGun_Bullet_Damage_Ratio"));
    }

    @Test
    void explicitTargetAndForceCasterHaveSeparateUnlockOwners() {
        accessory(2240121);
        var other = addAvatar(new TestAvatar(fixture.player));
        var ice = ability("TPS_IceGun_Accessory_Manager", entity);
        var pred =
                new HashMap<String, Object>(
                        Map.of(
                                "$type",
                                "ByUnlockTalentParam",
                                "talentParam",
                                "Unlock_Accessory_Magazine",
                                "target",
                                "Target"));
        assertFalse(PredicateEvaluator.evaluate(pred, ice, entity, other, null));
        pred.put("forceByCaster", true);
        assertTrue(PredicateEvaluator.evaluate(pred, ice, entity, other, null));
    }

    @Test
    void originOwnerUnlockDoesNotFollowSelectedPartyMember() {
        accessory(2240121);
        var shot = ability("TPS_IceGun_Accessory_Manager", projectile(entity, 994));
        addAvatar(new TestAvatar(fixture.player));
        fixture.player.getTeamManager().setCurrentCharacterIndex(1);
        assertTrue(
                PredicateEvaluator.evaluate(
                        Map.of(
                                "$type",
                                "ByUnlockTalentParam",
                                "talentParam",
                                "Unlock_Accessory_Magazine",
                                "target",
                                "OriginOwner"),
                        shot,
                        shot.getOwner(),
                        fixture.entity,
                        null));
    }

    @Test
    void declaredAbilityScopeCanBeDifferentFromCallingAbility() {
        accessory(2240121);
        var caller = ability("Avatar_TPS_Innate_Ability", entity);
        assertFalse(unlocked(caller, "Unlock_Accessory_Magazine"));
        assertTrue(
                PredicateEvaluator.evaluate(
                        Map.of(
                                "$type",
                                "ByUnlockTalentParam",
                                "talentParam",
                                "Unlock_Accessory_Magazine",
                                "abilityName",
                                "TPS_IceGun_Accessory_Manager"),
                        caller,
                        entity,
                        fixture.entity,
                        null));
    }

    @Test
    void signedWrappedParameterSkipsMetadataAndPreservesMissingDefault() {
        config(
                "TestWeapon",
                "[{\"$type\":\"SetAbilityVar\",\"abilityName\":\"TestWeaponAbility\",\"varName\":\"Value\","
                        + "\"varValue\":{\"$type\":\"Wrapped\",\"MLHKFPOMJDJ\":\"-%2\"}}]");
        var weapon = item(11499, "ITEM_WEAPON");
        avatar.getEquips().put(EquipType.EQUIP_WEAPON.getValue(), weapon);
        affix(70010, "TestWeapon", .2f);
        assertEquals(8f, ability("TestWeaponAbility", entity).getAbilitySpecials().getFloat("Value"));
        affix(70010, "TestWeapon", .2f, .7f);
        assertEquals(-.7f, ability("TestWeaponAbility", entity).getAbilitySpecials().getFloat("Value"));
    }

    @Test
    void legacySerializedOpenConfigDefaultsToPositiveParameter() {
        var config =
                JsonUtils.decode(
                        "{\"name\":\"Legacy\",\"abilityVarSetters\":[{\"abilityName\":\"TestWeaponAbility\","
                                + "\"varName\":\"Value\",\"paramIndex\":0}]}",
                        OpenConfigEntry.class);
        put(GameData.getOpenConfigEntries(), "Legacy", config);
        affix(70010, "Legacy", .6f);
        avatar.getEquips().put(EquipType.EQUIP_WEAPON.getValue(), item(11499, "ITEM_WEAPON"));
        assertEquals(.6f, ability("TestWeaponAbility", entity).getAbilitySpecials().getFloat("Value"));
    }

    private void setOwnerId(EntityClientGadget gadget, String fieldName, int value) throws Exception {
        var field = EntityClientGadget.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setInt(gadget, value);
    }

    @Test
    void cyclicProjectileOwnerChainCannotRecurseForeverOrGuessACaster() throws Exception {
        var a = projectile(entity, 995);
        var b = projectile(a, 996);
        setOwnerId(a, "ownerEntityId", b.getId());
        setOwnerId(a, "originalOwnerEntityId", b.getId());
        setOwnerId(b, "ownerEntityId", a.getId());
        setOwnerId(b, "originalOwnerEntityId", a.getId());
        var child =
                assertTimeoutPreemptively(java.time.Duration.ofSeconds(2), () -> projectile(a, 997));
        assertNull(ability("TPS_IceGun_Accessory_Manager", child).getCasterEntity());
    }

    private AbilityScalarValueEntry value(String name, float amount) {
        return AbilityScalarValueEntry.newBuilder()
                .setKey(AbilityString.newBuilder().setStr(name))
                .setFloatValue(amount)
                .build();
    }

    private void invoke(
            String methodName, GameEntity owner, int index, com.google.protobuf.ByteString data)
            throws Exception {
        var method = AbilityManager.class.getDeclaredMethod(methodName, AbilityInvokeEntry.class);
        method.setAccessible(true);
        method.invoke(
                fixture.player.getAbilityManager(),
                AbilityInvokeEntry.newBuilder()
                        .setEntityId(owner.getId())
                        .setHead(AbilityInvokeEntryHead.newBuilder().setInstancedAbilityId(index))
                        .setAbilityData(data)
                        .build());
    }

    @Test
    void reinitOverridesApplyServerAffixParametersAndKeepOtherAbilityScope() throws Exception {
        config(
                "ScopeValues",
                "[{\"$type\":\"SetAbilityVar\",\"abilityName\":\"ProjectileA\",\"varName\":\"Value\",\"varValue\":\"-%1\"},"
                        + "{\"$type\":\"SetAbilityVar\",\"abilityName\":\"AvatarB\",\"varName\":\"Value\",\"varValue\":\"%2\"}]");
        affix(2240010, "ScopeValues", .3f, .9f);
        var bullet = projectile(entity, 998);
        var bulletAbility = ability("ProjectileA", bullet);
        bullet.getInstancedAbilities().add(bulletAbility);
        var avatarAbility = ability("AvatarB", entity);
        entity.getInstancedAbilities().add(avatarAbility);
        fixture.player.packets.clear();
        invoke(
                "handleReinitOverrideMap",
                bullet,
                1,
                AbilityMetaReInitOverrideMap.newBuilder()
                        .addOverrideMap(value("Value", 99f))
                        .build()
                        .toByteString());
        assertEquals(-.3f, bulletAbility.getAbilitySpecials().getFloat("Value"));
        assertEquals(.9f, avatarAbility.getAbilitySpecials().getFloat("Value"));
        var packet =
                fixture.player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.AbilityInvocationsNotify)
                        .findFirst()
                        .orElseThrow();
        var updates = AbilityInvocationsNotify.parseFrom(packet.getData()).getInvokesList();
        assertEquals(2, updates.size());
        assertEquals(
                -.3f, AbilityScalarValueEntry.parseFrom(updates.get(0).getAbilityData()).getFloatValue());
        assertEquals(
                .9f, AbilityScalarValueEntry.parseFrom(updates.get(1).getAbilityData()).getFloatValue());
    }

    @Test
    void invalidReinitSlotCannotPatchAnotherAbility() throws Exception {
        accessory(2240121);
        var bullet = projectile(entity, 999);
        var current = ability("TPS_IceGun_Accessory_Manager", bullet);
        bullet.getInstancedAbilities().add(current);
        fixture.player.packets.clear();
        invoke(
                "handleReinitOverrideMap",
                bullet,
                2,
                AbilityMetaReInitOverrideMap.newBuilder()
                        .addOverrideMap(value("Value", 99f))
                        .build()
                        .toByteString());
        assertEquals(8f, current.getAbilitySpecials().getFloat("Value"));
        assertTrue(fixture.player.packets.isEmpty());
    }

    @Test
    void addAbilityOverridesClientParametersUsingActualWornAffix() throws Exception {
        accessory(2240121);
        affix(2240121, "TPS_Weapon_IceGun_Accessory_Magazine", .65f);
        var bullet = projectile(entity, 1000);
        var name = "TPS_IceGun_Accessory_Manager";
        put(GameData.getAbilityDataMap(), name, ability(name, entity).getData());
        invoke(
                "handleAddNewAbility",
                bullet,
                1,
                AbilityMetaAddAbility.newBuilder()
                        .setAbility(
                                AbilityAppliedAbility.newBuilder()
                                        .setAbilityName(AbilityString.newBuilder().setStr(name))
                                        .addOverrideMap(value("IceGun_Accessory_Magazine_FireIntervalRatio", 99f)))
                        .build()
                        .toByteString());
        assertEquals(
                .65f,
                bullet
                        .getInstancedAbilities()
                        .get(0)
                        .getAbilitySpecials()
                        .getFloat("IceGun_Accessory_Magazine_FireIntervalRatio"));
        assertEquals(
                .65f,
                fixture
                        .player
                        .getAbilityManager()
                        .computeGadgetVarOverrides(bullet)
                        .get("IceGun_Accessory_Magazine_FireIntervalRatio"));
    }

    @Test
    void abilityUsesItsPlayersManagerInMultiplayerWorld() {
        var guest = new AbilityTestFixture.TestPlayer();
        guest.setUid(73002);
        guest.setScene(fixture.scene);
        var guestAvatar = addAvatar(new TestAvatar(guest));
        var guestAbility = new Ability(ability("Test", entity).getData(), guestAvatar, guest);
        assertSame(guest.getAbilityManager(), guestAbility.getManager());
        assertSame(guestAvatar, guestAbility.getCasterEntity());
    }

    @Test
    void relicSetParametersRequireActualTwoAndFourPieceCounts() {
        config(
                "TestSet",
                "[{\"$type\":\"SetAbilityVar\",\"abilityName\":\"TestSetAbility\",\"varName\":\"Value\",\"varValue\":\"%1\"}]");
        put(
                GameData.getReliquarySetDataMap(),
                6200,
                JsonUtils.decode(
                        "{\"setId\":6200,\"equipAffixId\":6200,\"setNeedNum\":[2,4]}",
                        emu.grasscutter.data.excels.reliquary.ReliquarySetData.class));
        affix(62000, "TestSet", .2f);
        affix(62001, "TestSet", .6f);
        for (int slot = 1; slot <= 4; slot++) {
            var relic = new GameItem();
            relic.setCount(1);
            relic.setItemData(
                    JsonUtils.decode(
                            "{\"id\":99001,\"itemType\":\"ITEM_RELIQUARY\",\"setId\":6200}", ItemData.class));
            relic.setOwner(fixture.player);
            avatar.getEquips().put(slot, relic);
            assertEquals(
                    slot == 1 ? 8f : slot < 4 ? .2f : .6f,
                    ability("TestSetAbility", entity).getAbilitySpecials().getFloat("Value"));
        }
        avatar.getEquips().remove(4);
        avatar.getEquips().put(5, avatar.getEquips().get(1));
        assertEquals(.2f, ability("TestSetAbility", entity).getAbilitySpecials().getFloat("Value"));
    }

    @Test
    void rebindingNormalWeaponChangesCasterAndRemovesOldEntity() {
        var weapon = item(11499, "ITEM_WEAPON");
        avatar.getEquips().put(EquipType.EQUIP_WEAPON.getValue(), weapon);
        addAvatar(avatar);
        var oldEntity = weapon.getWeaponEntity();
        var recipient = new TestAvatar(fixture.player);
        avatar.getEquips().clear();
        recipient.getEquips().put(EquipType.EQUIP_WEAPON.getValue(), weapon);
        var recipientEntity = addAvatar(recipient);
        assertNotSame(oldEntity, weapon.getWeaponEntity());
        assertFalse(fixture.scene.getWeaponEntities().containsKey(oldEntity.getId()));
        assertSame(
                recipientEntity, ability("TestWeaponAbility", weapon.getWeaponEntity()).getCasterEntity());
    }

    @Test
    void existingAbilityRefreshesAccessoryParametersAfterStatsRecalculation() {
        var active = ability("TPS_IceGun_Accessory_Manager", entity);
        entity.getInstancedAbilities().add(active);
        accessory(2240121);
        affix(2240121, "TPS_Weapon_IceGun_Accessory_Magazine", .65f);
        avatar.recalcStats(false, false);
        assertEquals(
                .65f, active.getAbilitySpecials().getFloat("IceGun_Accessory_Magazine_FireIntervalRatio"));
        gun.getTpsAccessoryIds().clear();
        avatar.recalcStats(false, false);
        assertFalse(
                active.getAbilitySpecials().containsKey("IceGun_Accessory_Magazine_FireIntervalRatio"));
    }

    @Test
    void materialOwnershipRefreshesAnAlreadyRunningAbilityWithRealAccessoryConfig() {
        fixture.player.getInventory().getItems().remove(gun.getGuid());
        gun = new TpsEquipmentTest.RecordingItem(224001);
        assertTrue(fixture.player.getInventory().addItem(gun, false, false));
        assertTrue(fixture.player.getAvatars().addAvatar(avatar));
        var active = ability("TPS_IceGun_Accessory_Manager", entity);
        entity.getInstancedAbilities().add(active);
        String key = "IceGun_Accessory_Magazine_FireIntervalRatio";
        assertFalse(unlocked(active, "Unlock_Accessory_Magazine"));
        assertFalse(active.getAbilitySpecials().containsKey(key));
        var data =
                JsonUtils.decode(
                        "{\"id\":223112,\"itemType\":\"ITEM_MATERIAL\",\"stackLimit\":1}", ItemData.class);
        data.onLoad();
        put(GameData.getItemDataMap(), 223112, data);
        var token = new TpsEquipmentTest.RecordingItem(223112);
        token.setCount(1);
        assertTrue(fixture.player.getInventory().addItem(token, false, false));
        assertTrue(unlocked(active, "Unlock_Accessory_Magazine"));
        assertEquals(0f, active.getAbilitySpecials().getFloat(key));
        assertTrue(active.getAbilitySpecials().containsKey(key));
        fixture.player.getInventory().removeItem(token);
        assertFalse(unlocked(active, "Unlock_Accessory_Magazine"));
        assertFalse(active.getAbilitySpecials().containsKey(key));
    }

    @Test
    void removingWeaponTalentRestoresResourceDefaultWithoutChangingRuntimeValues() {
        config(
                "ScopeValues",
                "[{\"$type\":\"SetAbilityVar\",\"abilityName\":\"Test\",\"varName\":\"Value\",\"varValue\":\"%1\"}]");
        affix(2240010, "ScopeValues", .3f);
        var active = ability("Test", entity);
        entity.getInstancedAbilities().add(active);
        active.getAbilitySpecials().put("RuntimeCounter", 77f);
        avatar.getTpsWeaponIds().clear();
        avatar.recalcStats(false, false);
        assertEquals(8f, active.getAbilitySpecials().getFloat("Value"));
        assertEquals(77f, active.getAbilitySpecials().getFloat("RuntimeCounter"));
    }
}
