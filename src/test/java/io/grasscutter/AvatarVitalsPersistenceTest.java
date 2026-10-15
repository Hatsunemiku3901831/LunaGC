package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.data.excels.avatar.*;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.props.*;
import emu.grasscutter.net.proto.AvatarDataNotifyOuterClass.AvatarDataNotify;
import emu.grasscutter.server.packet.send.PacketAvatarDataNotify;
import emu.grasscutter.utils.JsonUtils;

import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

/** Actual Mongo, avatar admission, equipment loading and login snapshots. */
class AvatarVitalsPersistenceTest extends QuestTestFixture {
    private final List<Runnable> restores = new ArrayList<>();

    private <K, V> void put(Map<K, V> map, K key, V value) {
        var old = map.put(key, value);
        restores.add(
                () -> {
                    if (old == null) map.remove(key);
                    else map.put(key, old);
                });
    }

    @BeforeEach
    void resources() throws Exception {
        store.getMapper().map(Avatar.class, GameItem.class);
        boolean previousEnergy = Grasscutter.getConfig().server.game.gameOptions.energyUsage;
        restores.add(
                () -> Grasscutter.getConfig().server.game.gameOptions.energyUsage = previousEnergy);
        Grasscutter.getConfig().server.game.gameOptions.energyUsage = true;
        var energy =
                JsonUtils.decode(
                        "{id:10735,costElemType:'Fire',costElemVal:60,specialEnergyMin:120}",
                        AvatarSkillData.class);
        put(GameData.getAvatarSkillDataMap(), energy.getId(), energy);
        var depot =
                JsonUtils.decode(
                        "{id:7301,skills:[10731],energySkill:10735,talents:[],inherentProudSkillOpens:[]}",
                        AvatarSkillDepotData.class);
        depot.onLoad();
        put(GameData.getAvatarSkillDepotDataMap(), depot.getId(), depot);
        var avatarData =
                new AvatarData() {
                    @Override
                    public int getId() {
                        return 10000073;
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

                    @Override
                    public AvatarSkillDepotData getSkillDepot() {
                        return depot;
                    }

                    @Override
                    public List<Integer> getFetters() {
                        return List.of();
                    }

                    @Override
                    public int getInitialWeapon() {
                        return 11001;
                    }
                };
        put(GameData.getAvatarDataMap(), avatarData.getId(), avatarData);
        var item =
                JsonUtils.decode(
                        "{id:11001,itemType:'ITEM_WEAPON',rankLevel:1,skillAffix:[7100],weaponProp:[]}",
                        ItemData.class);
        item.onLoad();
        put(GameData.getItemDataMap(), item.getId(), item);
        var affix =
                JsonUtils.decode(
                        "{affixId:71000,id:7100,level:0,openConfig:'',paramList:[],"
                                + "addProps:[{propType:'FIGHT_PROP_HP',value:100}]}",
                        EquipAffixData.class);
        affix.onLoad();
        put(GameData.getEquipAffixDataMap(), affix.getId(), affix);
    }

    @AfterEach
    void restoreResources() throws Exception {
        drain();
        Collections.reverse(restores);
        restores.forEach(Runnable::run);
    }

    private Avatar source(float hp, float energy, float special) throws Exception {
        var avatar = new Avatar(10000073);
        avatar.setOwner(player);
        // Persist a nonempty raw collection; the depot has no active constellation.
        replaceField(Avatar.class, avatar, "talentIdList", new HashSet<>(Set.of(0)));
        replaceField(Avatar.class, avatar, "proudSkillList", new HashSet<>(Set.of(0)));
        var weapon = new GameItem(11001);
        weapon.setOwner(player);
        weapon.setEquipCharacter(avatar.getAvatarId());
        avatar.getEquips().put(weapon.getEquipSlot(), weapon);
        avatar.recalcStats();
        assertEquals(200, avatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP));
        avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, hp);
        avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_FIRE_ENERGY, energy);
        avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY, special);
        // recalc cleared the empty passive list; keep the Mongo fixture's collection explicit.
        replaceField(Avatar.class, avatar, "proudSkillList", new HashSet<>(Set.of(0)));
        store.save(weapon);
        store.save(avatar);
        return avatar;
    }

    private Document stored(Avatar avatar) {
        return store.getDatabase()
                .getCollection("avatars")
                .find(new Document("_id", avatar.getObjectId()))
                .first();
    }

    private void savedPools(Avatar avatar, float hp, float energy, Float special) {
        var fields =
                new Document("currentHp", (double) hp).append("currentEnergy", (double) energy);
        if (special != null) fields.append("currentSpecialEnergy", (double) special);
        store.getDatabase()
                .getCollection("avatars")
                .updateOne(new Document("_id", avatar.getObjectId()), new Document("$set", fields));
        if (special == null)
            store.getDatabase()
                    .getCollection("avatars")
                    .updateOne(
                            new Document("_id", avatar.getObjectId()),
                            new Document("$unset", new Document("currentSpecialEnergy", "")));
    }

    private Avatar loadAvatars() {
        player.getAvatars().loadFromDatabase();
        var avatar = player.getAvatars().getAvatarById(10000073);
        assertNotNull(avatar);
        assertTrue(avatar.getEquips().isEmpty());
        return avatar;
    }

    private Avatar loadAll() {
        var avatar = loadAvatars();
        player.getInventory().loadFromDatabase();
        assertNotNull(avatar.getWeapon());
        assertEquals(200, avatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP));
        return avatar;
    }

    @Test
    void ready_saves_capture_the_current_pools_instead_of_stale_scalar_fields() throws Exception {
        var avatar = source(150, 35, 45);
        var raw = stored(avatar);
        assertNotNull(raw);
        assertEquals(150, ((Number) raw.get("currentHp")).floatValue());
        assertEquals(35, ((Number) raw.get("currentEnergy")).floatValue());
        assertEquals(45, ((Number) raw.get("currentSpecialEnergy")).floatValue());
    }

    @Test
    void saved_absolute_hp_and_both_pools_survive_the_actual_inventory_load() throws Exception {
        var source = source(150, 35, 45);
        savedPools(source, 150, 35, 45f);
        var loaded = loadAll();
        assertEquals(150, loaded.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertEquals(35, loaded.getFightProperty(FightProperty.FIGHT_PROP_CUR_FIRE_ENERGY));
        assertEquals(45, loaded.getFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY));
        var entity = new EntityAvatar(player.getScene(), loaded);
        assertEquals(35, entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_FIRE_ENERGY));
    }

    @ParameterizedTest
    @ValueSource(floats = {0, 0.25f, 1, 30, 150})
    void loading_does_not_heal_dead_or_low_hp_avatars(float hp) throws Exception {
        var source = source(hp, 0, 0);
        savedPools(source, hp, 0, 0f);
        var loaded = loadAll();
        assertEquals(hp, loaded.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertEquals(0, loaded.getFightProperty(FightProperty.FIGHT_PROP_CUR_FIRE_ENERGY));
        assertEquals(0, loaded.getFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY));
        var entity = new EntityAvatar(player.getScene(), loaded);
        assertEquals(hp <= 0, entity.isDead());
    }

    @Test
    void early_recalculations_and_saves_do_not_replace_the_pending_snapshot() throws Exception {
        var source = source(150, 35, 45);
        savedPools(source, 150, 35, 45f);
        var loaded = loadAvatars();
        for (int i = 0; i < 2; i++) {
            loaded.recalcStats();
            assertEquals(100, loaded.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP));
            store.save(loaded);
            assertEquals(150, ((Number) stored(loaded).get("currentHp")).floatValue());
            assertEquals(35, ((Number) stored(loaded).get("currentEnergy")).floatValue());
        }
        player.getInventory().loadFromDatabase();
        assertEquals(150, loaded.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertEquals(45, loaded.getFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY));
    }

    @Test
    void an_uninitialized_read_only_avatar_cannot_save_zero_over_the_stored_vitals()
            throws Exception {
        var source = source(150, 35, 45);
        savedPools(source, 150, 35, 45f);
        var loaded = store.find(Avatar.class).first();
        assertNotNull(loaded);
        assertTrue(loaded.getFightProperties().isEmpty());
        store.save(loaded);
        assertEquals(150, ((Number) stored(loaded).get("currentHp")).floatValue());
        assertEquals(35, ((Number) stored(loaded).get("currentEnergy")).floatValue());
        assertEquals(45, ((Number) stored(loaded).get("currentSpecialEnergy")).floatValue());
    }

    @Test
    void inflated_saved_values_are_clamped_after_equipment_has_finished_loading() throws Exception {
        var source = source(150, 35, 45);
        savedPools(source, 350, 75, 160f);
        var loaded = loadAll();
        assertEquals(200, loaded.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertEquals(60, loaded.getFightProperty(FightProperty.FIGHT_PROP_CUR_FIRE_ENERGY));
        assertEquals(120, loaded.getFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY));
    }

    @ParameterizedTest
    @ValueSource(floats = {-1, Float.NaN, Float.POSITIVE_INFINITY})
    void invalid_saved_values_cannot_enter_the_login_snapshot(float invalid) throws Exception {
        var source = source(150, 35, 45);
        savedPools(source, invalid, invalid, invalid);
        var loaded = loadAll();
        assertEquals(0, loaded.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertEquals(0, loaded.getFightProperty(FightProperty.FIGHT_PROP_CUR_FIRE_ENERGY));
        assertEquals(0, loaded.getFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY));
    }

    @Test
    void legacy_elemental_energy_is_not_reinterpreted_as_special_energy() throws Exception {
        var source = source(150, 35, 45);
        savedPools(source, 150, 35, null);
        var loaded = loadAll();
        assertEquals(35, loaded.getFightProperty(FightProperty.FIGHT_PROP_CUR_FIRE_ENERGY));
        assertEquals(0, loaded.getFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY));
    }

    @Test
    void later_live_recalculations_do_not_replay_the_login_snapshot() throws Exception {
        var source = source(150, 35, 45);
        savedPools(source, 150, 35, 45f);
        var loaded = loadAll();
        loaded.getEquips().clear();
        loaded.recalcStats();
        assertEquals(100, loaded.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP));
        assertEquals(75, loaded.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        loaded.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 20);
        loaded.recalcStats();
        assertEquals(20, loaded.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
    }

    @Test
    void the_real_login_packet_contains_the_restored_values() throws Exception {
        var source = source(150, 35, 45);
        savedPools(source, 150, 35, 45f);
        var loaded = loadAll();
        player.getTeamManager().getActiveTeam().add(new EntityAvatar(player.getScene(), loaded));
        var proto = AvatarDataNotify.parseFrom(new PacketAvatarDataNotify(player).getData());
        assertEquals(1, proto.getAvatarListCount());
        var properties = proto.getAvatarList(0).getFightPropMapMap();
        assertEquals(150, properties.get(FightProperty.FIGHT_PROP_CUR_HP.getId()));
        assertEquals(35, properties.get(FightProperty.FIGHT_PROP_CUR_FIRE_ENERGY.getId()));
        assertEquals(45, properties.get(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY.getId()));
    }

    @Test
    void a_new_avatar_keeps_the_live_hp_fraction_when_equipment_changes() {
        var avatar = new Avatar(10000073);
        avatar.setOwner(player);
        assertEquals(100, avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        var weapon = new GameItem(11001);
        weapon.setOwner(player);
        avatar.getEquips().put(weapon.getEquipSlot(), weapon);
        avatar.recalcStats();
        assertEquals(200, avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 40);
        avatar.recalcStats();
        assertEquals(40, avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
    }

    @Test
    void scene_special_energy_restore_does_not_replace_the_elemental_pool() throws Exception {
        var avatar = source(150, 35, 45);
        avatar.restoreSceneEnergy(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY, 7);
        var entity = new EntityAvatar(player.getScene(), avatar);
        assertEquals(35, entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_FIRE_ENERGY));
        assertEquals(7, entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY));
        store.save(avatar);
        assertEquals(35, ((Number) stored(avatar).get("currentEnergy")).floatValue());
        assertEquals(7, ((Number) stored(avatar).get("currentSpecialEnergy")).floatValue());
    }

    @Test
    void the_configured_unlimited_elemental_energy_mode_still_fills_its_own_pool()
            throws Exception {
        var source = source(150, 35, 45);
        savedPools(source, 150, 35, 45f);
        Grasscutter.getConfig().server.game.gameOptions.energyUsage = false;
        var loaded = loadAll();
        assertEquals(60, loaded.getFightProperty(FightProperty.FIGHT_PROP_CUR_FIRE_ENERGY));
        assertEquals(45, loaded.getFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY));
    }

    @Test
    void ready_saves_clamp_pools_and_do_not_store_the_loading_marker() throws Exception {
        var avatar = source(350, 75, 160);
        var raw = stored(avatar);
        assertEquals(200, ((Number) raw.get("currentHp")).floatValue());
        assertEquals(60, ((Number) raw.get("currentEnergy")).floatValue());
        assertEquals(120, ((Number) raw.get("currentSpecialEnergy")).floatValue());
        assertFalse(raw.containsKey("loadingVitals"));
        assertFalse(raw.containsKey("pendingVitals"));
    }

    @Test
    void the_main_characters_automatic_save_before_inventory_keeps_the_snapshot() throws Exception {
        var source = source(150, 35, 45);
        savedPools(source, 150, 35, 45f);
        var mainData =
                JsonUtils.decode(
                        "{id:10000007,hpBase:100,attackBase:10,defenseBase:10,initialWeapon:11001}",
                        AvatarData.class);
        replaceField(AvatarData.class, mainData, "fetters", List.of());
        put(GameData.getAvatarDataMap(), mainData.getId(), mainData);
        store.getDatabase()
                .getCollection("avatars")
                .updateOne(
                        new Document("_id", source.getObjectId()),
                        new Document("$set", new Document("avatarId", 10000007)));
        store.getDatabase()
                .getCollection("items")
                .updateMany(
                        new Document("ownerId", player.getUid()),
                        new Document("$set", new Document("equipCharacter", 10000007)));
        player.setMainCharacterId(10000007);
        player.getAvatars().loadFromDatabase();
        var loaded = player.getAvatars().getAvatarById(10000007);
        assertNotNull(loaded);
        drain();
        assertEquals(150, ((Number) stored(loaded).get("currentHp")).floatValue());
        player.getInventory().loadFromDatabase();
        assertEquals(200, loaded.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP));
        assertEquals(source.getWeapon().getObjectId(), loaded.getWeapon().getObjectId());
        assertEquals(150, loaded.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        assertEquals(35, loaded.getFightProperty(FightProperty.FIGHT_PROP_CUR_FIRE_ENERGY));
        assertEquals(45, loaded.getFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY));
    }

    @Test
    void missing_energy_metadata_does_not_erase_saved_pools() throws Exception {
        var source = source(150, 35, 45);
        savedPools(source, 150, 35, 45f);
        var depot = GameData.getAvatarSkillDepotDataMap().get(7301);
        replaceField(AvatarSkillDepotData.class, depot, "energySkillData", null);
        var loaded = loadAll();
        assertEquals(150, loaded.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));
        store.save(loaded);
        assertEquals(35, ((Number) stored(loaded).get("currentEnergy")).floatValue());
        assertEquals(45, ((Number) stored(loaded).get("currentSpecialEnergy")).floatValue());
    }
}
