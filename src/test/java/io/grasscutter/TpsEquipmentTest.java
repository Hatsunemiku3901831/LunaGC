package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.*;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.config.ConfigEntityGadget;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.data.excels.avatar.AvatarData;
import emu.grasscutter.data.excels.tps.*;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.tps.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AbilityInvokeArgumentOuterClass.AbilityInvokeArgument;
import emu.grasscutter.net.proto.AbilityInvokeEntryHeadOuterClass.AbilityInvokeEntryHead;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.net.proto.AbilityMetaUpdateTpsWeaponAmmunitionOuterClass.AbilityMetaUpdateTpsWeaponAmmunition;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.TpsAmmunitionChangeNotifyOuterClass.TpsAmmunitionChangeNotify;
import emu.grasscutter.net.proto.TpsAmmunitionChangeOuterClass.TpsAmmunitionChange;
import emu.grasscutter.net.proto.TpsRegionalPlaySupplyInfoNotifyOuterClass.TpsRegionalPlaySupplyInfoNotify;
import emu.grasscutter.net.proto.TpsWeaponAccessoryInfoOuterClass.TpsWeaponAccessoryInfo;
import emu.grasscutter.utils.JsonUtils;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TpsEquipmentTest {
    private RecordingPlayer player;
    private final List<Runnable> restore = new ArrayList<>();

    @BeforeAll
    static void init() {
        Grasscutter.getLogger();
    }

    @BeforeEach
    void resources() {
        player = new RecordingPlayer();
        for (int id : new int[] {224001, 224002, 224003, 224004, 224005}) {
            int slot = id >= 224004 ? 2 : 1;
            put(
                    GameData.getItemDataMap(),
                    id,
                    JsonUtils.decode(
                            "{\"id\":"
                                    + id
                                    + ",\"itemType\":\"ITEM_TPS_WEAPON\",\"gadgetId\":"
                                    + (50016000 + id - 224000)
                                    + "}",
                            ItemData.class));
            put(
                    GameData.getTpsWeaponDataMap(),
                    id,
                    JsonUtils.decode(
                            "{\"id\":"
                                    + id
                                    + ",\"NNJGEAPMJFL\":"
                                    + slot
                                    + ",\"PIHHHIMPCAM\":"
                                    + id * 10
                                    + ",\"ammoSlotIds\":["
                                    + (id == 224002 ? 201 : 101)
                                    + "]}",
                            TpsWeaponData.class));
        }
        put(
                GameData.getTpsAmmunitionDataMap(),
                1001,
                JsonUtils.decode(
                        "{\"id\":1001,\"tpsAmmoLimit\":2000,\"ammoSlotIds\":[101,201]}",
                        TpsAmmunitionData.class));
        put(
                GameData.getTpsAmmunitionDataMap(),
                1002,
                JsonUtils.decode(
                        "{\"id\":1002,\"tpsAmmoLimit\":90,\"ammoSlotIds\":[102]}", TpsAmmunitionData.class));
        put(
                GameData.getTpsWeaponAccessoryDataMap(),
                224506,
                JsonUtils.decode(
                        "{\"id\":224506,\"tpsWeaponId\":224001,\"PIHHHIMPCAM\":2240121,\"KOHCBGDKMPG\":223112}",
                        TpsWeaponAccessoryData.class));
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
    void restore() {
        Collections.reverse(restore);
        restore.forEach(Runnable::run);
        restore.clear();
    }

    static class RecordingPlayer extends Player {
        final TeamManager team = new TeamManager(this);
        final List<BasePacket> packets = new ArrayList<>();
        int saves;
        boolean failNextSave;
        boolean loggedIn;

        @Override
        public TeamManager getTeamManager() {
            return team;
        }

        @Override
        public void save() {
            saves++;
            if (failNextSave) {
                failNextSave = false;
                throw new IllegalStateException("injected TPS save failure");
            }
        }

        @Override
        public void sendPacket(BasePacket packet) {
            packets.add(packet);
        }

        @Override
        public boolean hasSentLoginPackets() {
            return loggedIn;
        }
    }

    static class RecordingItem extends GameItem {
        int saves;

        RecordingItem(int id) {
            super(id, 99);
        }

        @Override
        public void save() {
            saves++;
        }
    }

    static class RecordingAvatar extends Avatar {
        final int avatarId;
        int saves, recalcs;

        RecordingAvatar(int id) {
            avatarId = id;
        }

        @Override
        public int getAvatarId() {
            return avatarId;
        }

        @Override
        public AvatarData getAvatarData() {
            return new AvatarData();
        }

        @Override
        public void save() {
            saves++;
        }

        @Override
        public void recalcStats(boolean force) {
            recalcs++;
        }
    }

    private RecordingAvatar avatar(int id, boolean trial) {
        var avatar = new RecordingAvatar(id);
        if (trial) {
            avatar.setTrialAvatarId(10064);
            avatar.setOwner(player);
            player.team.getTrialAvatars().put(id, avatar);
        } else player.getAvatars().addAvatar(avatar);
        avatar.saves = 0;
        return avatar;
    }

    private RecordingItem item(int id) {
        var item = new RecordingItem(id);
        player.getInventory().addItem(item);
        return item;
    }

    private void equip(Avatar avatar, GameItem... items) {
        assertEquals(
                0,
                TpsWeaponSystem.wear(
                        player, avatar.getGuid(), Arrays.stream(items).map(GameItem::getGuid).toList()));
    }

    @Test
    void inventoryStoresOneCopyPerWeaponEvenWhenGivenMany() {
        var first = item(224001);
        var next = item(224001);
        assertEquals(1, player.getInventory().getItems().size());
        assertEquals(1, first.getCount());
        assertEquals(first, TpsWeaponSystem.findOwnedWeapon(player, 224001));
        assertEquals(0, next.getGuid());
        assertEquals(
                20, player.getInventory().getInventoryTab(ItemType.ITEM_TPS_WEAPON).getMaxCapacity());
        assertTrue(first.toProto().hasTpsWeapon());
        assertFalse(first.toProto().hasMaterial());
    }

    @Test
    void duplicateGiveReportsNoGainAndDoesNotRepeatPacketsOrHistoryWrites() {
        var gun = item(224001);
        player.packets.clear();
        player.saves = 0;
        assertFalse(player.getInventory().addItem(new RecordingItem(224001)));
        assertTrue(player.packets.isEmpty());
        assertEquals(0, player.saves);
        assertSame(gun, TpsWeaponSystem.findOwnedWeapon(player, 224001));
    }

    @Test
    void invalidGuidDoesNotPartiallyTransferAnotherAvatarsWeapon() {
        var first = avatar(10000005, false);
        var second = avatar(10000007, false);
        var gun = item(224001);
        equip(first, gun);
        assertEquals(
                Retcode.RET_ITEM_NOT_EXIST_VALUE,
                TpsWeaponSystem.wear(player, second.getGuid(), List.of(gun.getGuid(), -1L)));
        assertEquals(List.of(224001), first.getTpsWeaponIds());
        assertTrue(second.getTpsWeaponIds().isEmpty());
        assertEquals(0, second.recalcs);
    }

    @Test
    void duplicateGuidIsRejectedWithoutChanges() {
        var first = avatar(10000005, false);
        var gun = item(224001);
        assertEquals(
                Retcode.RET_FAIL_VALUE,
                TpsWeaponSystem.wear(player, first.getGuid(), List.of(gun.getGuid(), gun.getGuid())));
        assertTrue(first.getTpsWeaponIds().isEmpty());
    }

    @Test
    void unknownAvatarIsRejected() {
        assertEquals(
                Retcode.RET_CAN_NOT_FIND_AVATAR_VALUE, TpsWeaponSystem.wear(player, -1, List.of()));
    }

    @Test
    void twoGunsAndOneGrenadeFitButOtherCombinationsDoNot() {
        var first = avatar(10000005, false);
        var a = item(224001);
        var b = item(224002);
        var c = item(224003);
        var d = item(224004);
        var e = item(224005);
        assertEquals(
                Retcode.RET_EQUIP_EXCEED_LIMIT_VALUE,
                TpsWeaponSystem.wear(
                        player, first.getGuid(), List.of(a.getGuid(), b.getGuid(), c.getGuid())));
        assertEquals(
                Retcode.RET_EQUIP_EXCEED_LIMIT_VALUE,
                TpsWeaponSystem.wear(player, first.getGuid(), List.of(d.getGuid(), e.getGuid())));
        equip(first, a, b, d);
        assertEquals(List.of(224001, 224002, 224004), first.getTpsWeaponIds());
    }

    @Test
    void missingWeaponConfigIsRejected() {
        var first = avatar(10000005, false);
        var gun = item(224001);
        put(GameData.getTpsWeaponDataMap(), 224001, null);
        assertEquals(
                Retcode.RET_EQUIP_EXCEED_LIMIT_VALUE,
                TpsWeaponSystem.wear(player, first.getGuid(), List.of(gun.getGuid())));
    }

    @Test
    void trialAndOwnedWearersTransferInsteadOfSharingWeapons() {
        var first = avatar(TpsAvatarSystem.MALE, true);
        var second = avatar(10000005, false);
        var gun = item(224001);
        equip(first, gun);
        assertEquals(List.of(224001), player.getTpsLoadout());
        equip(second, gun);
        assertTrue(first.getTpsWeaponIds().isEmpty());
        assertEquals(List.of(224001), second.getTpsWeaponIds());
        assertTrue(player.getTpsLoadout().isEmpty());
        assertEquals(0, first.saves);
        assertEquals(2, first.recalcs);
    }

    @Test
    void emptyReplacementUnequipsAndSameReplacementIsIdempotent() {
        var first = avatar(10000005, false);
        var gun = item(224001);
        equip(first, gun);
        int saves = first.saves;
        equip(first, gun);
        assertEquals(saves, first.saves);
        equip(first);
        assertTrue(first.getTpsWeaponIds().isEmpty());
        assertEquals(saves + 1, first.saves);
    }

    @Test
    void accessoriesAndAffixesFilterDuplicatesAndOtherWeapons() {
        var a = item(224001);
        var b = item(224002);
        a.getTpsAccessoryIds().addAll(Arrays.asList(224506, 224506, 0, null));
        b.getTpsAccessoryIds().add(224506);
        assertEquals(List.of(224506), TpsWeaponSystem.getValidAccessories(a));
        assertTrue(TpsWeaponSystem.getValidAccessories(b).isEmpty());
        assertEquals(1, TpsWeaponSystem.getAffixLevels(a).get(224012));
        assertEquals(List.of(224506), a.toProto().getTpsWeapon().getAccessoryIdListList());
        assertTrue(b.toProto().getTpsWeapon().getAccessoryIdListList().isEmpty());
    }

    @Test
    void accessoryUnlockRequiresAnOwnedWeaponAndIsIdempotent() {
        assertFalse(TpsWeaponSystem.unlockAccessory(player, 224506));
        var gun = item(224001);
        assertTrue(TpsWeaponSystem.unlockAccessory(player, 224506));
        int saves = gun.saves;
        assertFalse(TpsWeaponSystem.unlockAccessory(player, 224506));
        assertEquals(saves, gun.saves);
        assertEquals(List.of(224506), gun.getTpsAccessoryIds());
    }

    @Test
    void deletingWeaponRemovesLoadoutAndAllWearerReferences() {
        var first = avatar(TpsAvatarSystem.MALE, true);
        var gun = item(224001);
        equip(first, gun);
        assertTrue(player.getInventory().removeItem(gun, 99));
        assertTrue(first.getTpsWeaponIds().isEmpty());
        assertTrue(player.getTpsLoadout().isEmpty());
        assertNull(TpsWeaponSystem.findOwnedWeapon(player, 224001));
    }

    @Test
    void staleAndRepeatedWornIdsAreOmittedFromProto() {
        var first = avatar(10000005, false);
        var gun = item(224001);
        first.getTpsWeaponIds().addAll(Arrays.asList(224001, 224001, 123, null));
        assertEquals(List.of(gun), TpsWeaponSystem.getWornWeapons(first));
        assertEquals(1, TpsWeaponSystem.getSceneWeaponInfos(first).size());
    }

    @Test
    void sharedSlotsResolveToOneReserveAndZeroSurvivesReads() {
        assertEquals(1001, TpsWeaponSystem.getAmmunitionForSlot(101).getId());
        assertEquals(1001, TpsWeaponSystem.getAmmunitionForSlot(201).getId());
        assertNull(TpsWeaponSystem.getAmmunitionForSlot(-1));
        assertEquals(2000, TpsWeaponSystem.getReserve(player, 1001));
        player.getTpsAmmunition().put(1001, 0);
        assertEquals(0, TpsWeaponSystem.getReserve(player, 1001));
        assertEquals(0, TpsWeaponSystem.getReserve(player, -1));
    }

    @Test
    void reserveChangesClampWithoutIntegerOverflowAndSaveOnlyChanges() {
        assertEquals(0, TpsWeaponSystem.changeReserve(player, 1001, Integer.MIN_VALUE));
        assertEquals(2000, TpsWeaponSystem.changeReserve(player, 1001, Integer.MAX_VALUE));
        assertEquals(2, player.saves);
        assertEquals(2000, TpsWeaponSystem.changeReserve(player, 1001, 1));
        assertEquals(0, TpsWeaponSystem.changeReserve(player, -1, 1));
        assertEquals(2, player.saves);
    }

    @Test
    void invalidSavedReserveValuesAreBounded() {
        player.getTpsAmmunition().put(1001, null);
        assertEquals(0, TpsWeaponSystem.getReserve(player, 1001));
        player.getTpsAmmunition().put(1001, -5);
        assertEquals(0, TpsWeaponSystem.getReserve(player, 1001));
        player.getTpsAmmunition().put(1001, 3000);
        assertEquals(2000, TpsWeaponSystem.getReserve(player, 1001));
    }

    @Test
    void oldDocumentsWithoutTpsCollectionsAreUsable() throws Exception {
        for (String name : List.of("tpsLoadout", "tpsAmmunition", "tpsMagazineAmmunition")) {
            Field field = Player.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(player, null);
        }
        var avatar = new Avatar();
        Field wear = Avatar.class.getDeclaredField("tpsWeaponIds");
        wear.setAccessible(true);
        wear.set(avatar, null);
        var gun = item(224001);
        Field accessories = GameItem.class.getDeclaredField("tpsAccessoryIds");
        accessories.setAccessible(true);
        accessories.set(gun, null);
        assertTrue(player.getTpsLoadout().isEmpty());
        assertTrue(player.getTpsAmmunition().isEmpty());
        assertTrue(player.getTpsMagazineAmmunition().isEmpty());
        assertTrue(avatar.getTpsWeaponIds().isEmpty());
        assertTrue(gun.getTpsAccessoryIds().isEmpty());
    }

    @Test
    void sceneWeaponProtoRestoresItsMagazineWithoutUsingTheSharedTotal() {
        var gun = item(224001);
        player.getTpsAmmunition().put(1001, 39);
        player.getTpsMagazineAmmunition().put(101, 9);
        var proto = TpsWeaponSystem.toSceneWeaponInfo(player, gun);
        assertEquals(0, proto.getEntityId());
        assertEquals(101, proto.getAmmunitionList(0).getAmmunitionType());
        assertEquals(1001, proto.getAmmunitionList(0).getAmmunitionConfigId());
        assertEquals(9, proto.getAmmunitionList(0).getCurrentAmmunition());
        assertFalse(proto.getAbilityInfo().getIsInited());
    }

    private RecordingItem gunWithAbilitySnapshot(AbilityTestFixture fixture) throws Exception {
        realIceGunAbilities();
        var avatar = sceneAvatar(fixture);
        player.setWorld(fixture.scene.getWorld());
        var gun = item(224001);
        equip(avatar.getAvatar(), gun);
        TpsWeaponSystem.ensureWeaponEntities(avatar.getAvatar(), fixture.scene);
        var weapon = gun.getWeaponEntity();
        assertEquals(5, weapon.getInstancedAbilities().size());
        weapon.getInstancedAbilities().get(0).getAbilitySpecials().put("ServerSnapshot", 17f);
        weapon.getGlobalAbilityValues().put("SGV_WeaponSnapshot", 6f);
        avatar.getGlobalAbilityValues().put("SGV_AvatarOnly", 99f);
        return gun;
    }

    @Test
    void tpsAbilitySnapshotKeepsRealRootParametersAndItsOwnServerGlobals() throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var gun = gunWithAbilitySnapshot(fixture);
            player.getTpsMagazineAmmunition().put(101, 9);
            var before = Map.copyOf(gun.getWeaponEntity().getGlobalAbilityValues());
            player.packets.clear();
            fixture.scene.packets.clear();
            var proto = TpsWeaponSystem.getSceneWeaponInfos(currentAvatar().getAvatar()).get(0);
            var info = proto.getAbilityInfo();
            assertFalse(info.getIsInited());
            assertEquals(gun.getWeaponEntity().getId(), proto.getEntityId());
            assertEquals(1, info.getAppliedAbilitiesCount());
            var root = info.getAppliedAbilities(0);
            assertEquals(1, root.getInstancedAbilityId());
            assertEquals(
                    gun.getWeaponEntity().getInstancedAbilities().get(0).getData().abilityName,
                    root.getAbilityName().getStr());
            assertEquals(1, root.getOverrideMapCount());
            assertEquals("ServerSnapshot", root.getOverrideMap(0).getKey().getStr());
            assertEquals(17f, root.getOverrideMap(0).getFloatValue());
            assertEquals(1, info.getSgvDynamicValueMapCount());
            assertEquals("SGV_WeaponSnapshot", info.getSgvDynamicValueMap(0).getKey().getStr());
            assertEquals(6f, info.getSgvDynamicValueMap(0).getFloatValue());
            assertTrue(info.getDynamicValueMapList().isEmpty());
            assertTrue(info.getAppliedModifiersList().isEmpty());
            assertEquals(9, proto.getAmmunitionList(0).getCurrentAmmunition());
            assertEquals(TpsWeaponSystem.getAffixLevels(gun), proto.getAffixMapMap());
            assertEquals(before, gun.getWeaponEntity().getGlobalAbilityValues());
            assertTrue(player.packets.isEmpty());
            assertTrue(fixture.scene.packets.isEmpty());
        }
    }

    private EntityAvatar currentAvatar() {
        return TpsWeaponSystem.currentAvatar(player);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7})
    void tpsAbilitySnapshotCannotRestoreAnExpiredOrForeignWeapon(int invalid) throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var gun = gunWithAbilitySnapshot(fixture);
            var weapon = gun.getWeaponEntity();
            switch (invalid) {
                case 0 -> weapon.invalidateAbilityLifecycle();
                case 1 -> fixture.scene.getWeaponEntities().remove(weapon.getId());
                case 2 -> {
                    var replacement =
                            new emu.grasscutter.game.entity.EntityWeapon(
                                    fixture.scene, 50016001, currentAvatar().getAvatar());
                    replacement.setId(weapon.getId());
                    fixture.scene.getWeaponEntities().put(weapon.getId(), replacement);
                }
                case 3 -> player.setScene(null);
                case 4 -> player.setWorld(null);
                case 5 -> currentAvatar().getAvatar().getTpsWeaponIds().clear();
                case 6 -> {
                    var other = avatar(10000007, false);
                    var foreign =
                            new emu.grasscutter.game.entity.EntityWeapon(
                                    fixture.scene, 50016001, other);
                    gun.setWeaponEntity(foreign);
                    fixture.scene.getWeaponEntities().put(foreign.getId(), foreign);
                    foreign.getGlobalAbilityValues().put("SGV_Foreign", 8f);
                    foreign.getInstancedAbilities()
                            .get(0)
                            .getAbilitySpecials()
                            .put("ServerSnapshot", 8f);
                }
                case 7 -> weapon.getCasterAvatar().setOwner(fixture.player);
                default -> fail("Unknown fixture");
            }
            var proto = TpsWeaponSystem.toSceneWeaponInfo(player, gun);
            assertFalse(proto.getAbilityInfo().getIsInited());
            assertEquals(0, proto.getEntityId());
            assertTrue(proto.getAbilityInfo().getAppliedAbilitiesList().isEmpty());
            assertTrue(proto.getAbilityInfo().getSgvDynamicValueMapList().isEmpty());
        }
    }

    @Test
    void tpsAbilitySnapshotDoesNotCarryOldStateIntoAReplacementWeapon() throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var gun = gunWithAbilitySnapshot(fixture);
            var old = gun.getWeaponEntity();
            assertEquals(
                    1,
                    TpsWeaponSystem.toSceneWeaponInfo(player, gun)
                            .getAbilityInfo()
                            .getAppliedAbilitiesCount());
            player.loggedIn = true;
            TpsWeaponSystem.sendEquipChange(currentAvatar().getAvatar());
            assertNotSame(old, gun.getWeaponEntity());
            var packet =
                    fixture.scene.packets.stream()
                            .filter(p -> p.getOpcode() == PacketOpcodes.TpsEquipChangeNotify)
                            .findFirst()
                            .orElseThrow();
            var proto =
                    emu.grasscutter.net.proto.TpsEquipChangeNotifyOuterClass.TpsEquipChangeNotify
                            .parseFrom(packet.getData())
                            .getTpsWeaponList(0);
            assertEquals(gun.getWeaponEntity().getId(), proto.getEntityId());
            assertFalse(proto.getAbilityInfo().getIsInited());
            assertTrue(proto.getAbilityInfo().getAppliedAbilitiesList().isEmpty());
            assertTrue(proto.getAbilityInfo().getSgvDynamicValueMapList().isEmpty());
        }
    }

    @Test
    void unmappedElectricSlotDoesNotBorrowAnotherGunsReserve() {
        put(
                GameData.getTpsWeaponDataMap(),
                224002,
                JsonUtils.decode("{\"id\":224002,\"ammoSlotIds\":[201,202]}", TpsWeaponData.class));
        assertNull(TpsWeaponSystem.getAmmunitionForSlot(202));
        player.getTpsMagazineAmmunition().put(201, 7);
        var proto = TpsWeaponSystem.toSceneWeaponInfo(player, item(224002));
        assertEquals(1, proto.getAmmunitionListCount());
        assertEquals(201, proto.getAmmunitionList(0).getAmmunitionType());
        assertEquals(1001, proto.getAmmunitionList(0).getAmmunitionConfigId());
    }

    @Test
    void weaponInitializationDoesNotTriggerAnAbilityNotificationLoop() throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var entity = sceneAvatar(fixture);
            var gun = item(224001);
            equip(entity.getAvatar(), gun);
            var weapon =
                    new emu.grasscutter.game.entity.EntityWeapon(fixture.scene, 50016001) {
                        @Override
                        public emu.grasscutter.net.proto.AbilityControlBlockOuterClass.AbilityControlBlock
                                getAbilityControlBlock() {
                            return emu.grasscutter.net.proto.AbilityControlBlockOuterClass.AbilityControlBlock
                                    .newBuilder()
                                    .addAbilityEmbryoList(
                                            emu.grasscutter.net.proto.AbilityEmbryoOuterClass.AbilityEmbryo.newBuilder()
                                                    .setAbilityId(1))
                                    .build();
                        }
                    };
            gun.setWeaponEntity(weapon);
            fixture.scene.getWeaponEntities().put(weapon.getId(), weapon);
            player.loggedIn = true;
            player.packets.clear();
            var session =
                    new emu.grasscutter.server.game.GameSession(null) {
                        @Override
                        public Player getPlayer() {
                            return player;
                        }
                    };
            var handler = new emu.grasscutter.server.packet.recv.HandlerClientAbilityInitFinishNotify();
            for (int id :
                    new int[] {weapon.getId(), fixture.entity.getId(), entity.getId(), weapon.getId()}) {
                var notify =
                        emu.grasscutter.net.proto.ClientAbilityInitFinishNotifyOuterClass
                                .ClientAbilityInitFinishNotify.newBuilder()
                                .setEntityId(id)
                                .build();
                handler.handle(session, new byte[0], notify.toByteArray());
            }
            assertEquals(1, player.packets.size());
            assertEquals(PacketOpcodes.AbilityChangeNotify, player.packets.get(0).getOpcode());
        }
    }

    private EntityAvatar sceneAvatar(AbilityTestFixture fixture) {
        player.setScene(fixture.scene);
        var avatar = avatar(10000005, false);
        avatar.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 100);
        avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 100);
        var entity =
                new EntityAvatar(fixture.scene, avatar) {
                    @Override
                    public void initAbilities() {}
                };
        player.team.getActiveTeam().add(entity);
        player.team.setCurrentCharacterIndex(0);
        fixture.scene.getEntities().put(entity.getId(), entity);
        return entity;
    }

    private AbilityInvokeEntry invoke(int entityId, int type, int... pairs) {
        var meta = AbilityMetaUpdateTpsWeaponAmmunition.newBuilder().setUpdateTypeValue(type);
        for (int i = 0; i < pairs.length; i += 2)
            meta.addAmmunitionList(
                    TpsAmmunitionChange.newBuilder()
                            .setAmmunitionConfigId(pairs[i])
                            .setChangeCount(pairs[i + 1]));
        return AbilityInvokeEntry.newBuilder()
                .setEntityId(entityId)
                .setAbilityData(meta.build().toByteString())
                .build();
    }

    @Test
    void entityMigrationReusesSameSceneAndCleansOldScene() throws Exception {
        try (var one = new AbilityTestFixture();
                var two = new AbilityTestFixture()) {
            var gun = item(224001);
            TpsWeaponSystem.ensureWeaponEntity(gun, one.scene);
            var old = gun.getWeaponEntity();
            TpsWeaponSystem.ensureWeaponEntity(gun, one.scene);
            assertSame(old, gun.getWeaponEntity());
            TpsWeaponSystem.ensureWeaponEntity(gun, two.scene);
            assertFalse(one.scene.getWeaponEntities().containsKey(old.getId()));
            assertSame(gun.getWeaponEntity(), two.scene.getEntityById(gun.getWeaponEntity().getId()));
            TpsWeaponSystem.releaseSceneWeapons(player, one.scene);
            assertNotNull(gun.getWeaponEntity());
            TpsWeaponSystem.releaseSceneWeapons(player, two.scene);
            assertNull(gun.getWeaponEntity());
            assertTrue(two.scene.getWeaponEntities().isEmpty());
        }
    }

    @Test
    void unequippingAlsoRemovesTheOldEntityWhenLoginNotificationsAreSuppressed() throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var entity = sceneAvatar(fixture);
            var gun = item(224001);
            equip(entity.getAvatar(), gun);
            TpsWeaponSystem.ensureWeaponEntity(gun, fixture.scene);
            int old = gun.getWeaponEntity().getId();
            equip(entity.getAvatar());
            assertNull(gun.getWeaponEntity());
            assertNull(fixture.scene.getEntityById(old));
        }
    }

    @Test
    void clientCostsArePersistedOnceAndCannotSpendUnwornAmmo() throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var entity = sceneAvatar(fixture);
            equip(entity.getAvatar(), item(224001));
            player.saves = 0;
            assertTrue(
                    TpsWeaponSystem.onAmmunitionInvoke(
                            player, invoke(entity.getId(), 0, 1001, -3, 1001, -2)));
            assertEquals(1995, TpsWeaponSystem.getReserve(player, 1001));
            assertEquals(1, player.saves);
            assertFalse(
                    TpsWeaponSystem.onAmmunitionInvoke(
                            player, invoke(entity.getId(), 0, 1001, -1, 1002, -1)));
            assertEquals(1995, TpsWeaponSystem.getReserve(player, 1001));
        }
    }

    @Test
    void forgedGrantsAndOverdrawsDoNotPartiallyChangeAmmo() throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var entity = sceneAvatar(fixture);
            equip(entity.getAvatar(), item(224001));
            player.saves = 0;
            for (int type : new int[] {0, 1, 2, 3, 99})
                assertFalse(
                        TpsWeaponSystem.onAmmunitionInvoke(player, invoke(entity.getId(), type, 1001, 1)));
            assertFalse(
                    TpsWeaponSystem.onAmmunitionInvoke(
                            player, invoke(entity.getId(), 0, 1001, -1, 1001, -2000)));
            assertEquals(2000, TpsWeaponSystem.getReserve(player, 1001));
            assertEquals(0, player.saves);
        }
    }

    @Test
    void costsFromAnotherEntityOrAnEmptyTeamAreIgnored() throws Exception {
        assertFalse(TpsWeaponSystem.onAmmunitionInvoke(player, invoke(42, 0, 1001, -1)));
        assertEquals(0, player.getAvatars().getAvatarCount());
        try (var fixture = new AbilityTestFixture()) {
            var entity = sceneAvatar(fixture);
            equip(entity.getAvatar(), item(224001));
            player.saves = 0;
            assertFalse(
                    TpsWeaponSystem.onAmmunitionInvoke(player, invoke(fixture.entity.getId(), 0, 1001, -1)));
            assertEquals(0, player.saves);
        }
    }

    @Test
    void sceneSupplyPreservesTotalsForUnwornWeaponsAndExplicitlyIncludesEmptyPools()
            throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var entity = sceneAvatar(fixture);
            equip(entity.getAvatar(), item(224001));
            player.getTpsAmmunition().put(1001, 17);
            player.getTpsAmmunition().put(1002, 43);
            player.getTpsAmmunition().put(-123, 900);
            player.packets.clear();
            player.saves = 0;

            TpsWeaponSystem.sendSceneAmmunition(player);
            var totals = clientAmmunitionTotals(player.packets);
            assertEquals(17, totals.get(1001));
            assertEquals(43, totals.get(1002), "the current weapon does not use this stored pool");
            assertFalse(totals.containsKey(-123));
            assertEquals(0, player.saves);

            player.getTpsAmmunition().put(1002, 0);
            player.packets.clear();
            TpsWeaponSystem.sendSceneAmmunition(player);
            totals = clientAmmunitionTotals(player.packets);
            assertTrue(
                    totals.containsKey(1002), "an empty known pool is part of the full snapshot");
            assertEquals(0, totals.get(1002));
        }
    }

    @Test
    void sceneSupplyCarriesOneAbsoluteSnapshotWithoutSavingOrRefilling() throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            equip(sceneAvatar(fixture).getAvatar(), item(224001), item(224002));
            player.getTpsAmmunition().put(1001, Integer.MAX_VALUE);
            player.getTpsAmmunition().put(1002, -1);
            var before = new HashMap<>(player.getTpsAmmunition());
            player.packets.clear();
            player.saves = 0;
            TpsWeaponSystem.sendSceneAmmunition(player);
            TpsWeaponSystem.sendSceneAmmunition(player);
            assertEquals(2, player.packets.size());
            assertTrue(
                    player.packets.stream()
                            .allMatch(
                                    packet ->
                                            packet.getOpcode()
                                                    == PacketOpcodes
                                                            .TpsRegionalPlaySupplyInfoNotify));
            assertEquals(2000, clientAmmunitionTotals(player.packets).get(1001));
            assertEquals(0, clientAmmunitionTotals(player.packets).get(1002));
            assertEquals(before, player.getTpsAmmunition());
            assertEquals(0, player.saves);
        }
    }

    /** The native 6579 handler replaces totals; 24371 adds signed deltas. */
    private Map<Integer, Integer> clientAmmunitionTotals(List<BasePacket> packets)
            throws Exception {
        var totals = new HashMap<Integer, Integer>();
        for (var packet : packets) {
            if (packet.getOpcode() == PacketOpcodes.TpsRegionalPlaySupplyInfoNotify) {
                totals.clear();
                for (var entry :
                        TpsRegionalPlaySupplyInfoNotify.parseFrom(
                                        packet.getData() == null ? new byte[0] : packet.getData())
                                .getAmmunitionListList())
                    totals.put(entry.getAmmunitionConfigId(), entry.getTotalAmmunition());
            } else if (packet.getOpcode() == PacketOpcodes.TpsAmmunitionChangeNotify) {
                for (var entry :
                        TpsAmmunitionChangeNotify.parseFrom(packet.getData())
                                .getAmmunitionListList())
                    totals.merge(
                            entry.getAmmunitionConfigId(), entry.getChangeCount(), Integer::sum);
            }
        }
        return totals;
    }

    @Test
    void sceneSupplyIsSilentWithoutAnActiveTpsWeapon() {
        player.packets.clear();
        TpsWeaponSystem.sendSceneAmmunition(player);
        assertTrue(player.packets.isEmpty());
    }

    @Test
    void sceneAmmoNotifyDeduplicatesSharedPoolsAndRefillKeepsEntityIds() throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var entity = sceneAvatar(fixture);
            var gun = item(224001);
            equip(entity.getAvatar(), gun, item(224002));
            TpsWeaponSystem.ensureWeaponEntities(entity.getAvatar(), fixture.scene);
            int id = gun.getWeaponEntity().getId();
            player.getTpsAmmunition().put(1001, 0);
            TpsWeaponSystem.refillAmmunition(player);
            assertEquals(id, gun.getWeaponEntity().getId());
            var packets =
                    player.packets.stream()
                            .filter(
                                    p ->
                                            p.getOpcode()
                                                    == PacketOpcodes
                                                            .TpsRegionalPlaySupplyInfoNotify)
                            .toList();
            assertEquals(1, packets.size());
            var notify = TpsRegionalPlaySupplyInfoNotify.parseFrom(packets.get(0).getData());
            assertEquals(
                    1,
                    notify.getAmmunitionListList().stream()
                            .filter(entry -> entry.getAmmunitionConfigId() == 1001)
                            .count());
            assertEquals(2000, clientAmmunitionTotals(packets).get(1001));
            assertEquals(90, clientAmmunitionTotals(packets).get(1002));
            assertTrue(
                    player.packets.stream()
                            .noneMatch(
                                    packet ->
                                            packet.getOpcode()
                                                    == PacketOpcodes.TpsAmmunitionChangeNotify));
        }
    }

    private AbilityInvokeEntry magazineInvoke(
            int source, int weapon, int type, int slot, int ammunition, int count, int delta) {
        var meta =
                AbilityMetaUpdateTpsWeaponAmmunition.newBuilder()
                        .setUpdateTypeValue(type)
                        .addAccessoryList(
                                TpsWeaponAccessoryInfo.newBuilder()
                                        .setEntityId(weapon)
                                        .setAmmunitionType(slot)
                                        .setAmmunitionConfigId(ammunition)
                                        .setCurrentAmmunition(count))
                        .addAmmunitionList(
                                TpsAmmunitionChange.newBuilder()
                                        .setAmmunitionConfigId(1001)
                                        .setChangeCount(delta));
        return AbilityInvokeEntry.newBuilder()
                .setEntityId(source)
                .setAbilityData(meta.build().toByteString())
                .build();
    }

    @Test
    void anUnobservedMagazineUsesTheClientsResourceDefaultsInsteadOfTheTotalPool() {
        var gun = item(224001);
        assertEquals(0, TpsWeaponSystem.toSceneWeaponInfo(player, gun).getAmmunitionListCount());
        assertTrue(player.getTpsMagazineAmmunition().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aTeamChangeWhileReadingAWeaponDoesNotBreakTheAmmunitionSnapshot(boolean supply)
            throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var entity = sceneAvatar(fixture);
            var armed = new java.util.concurrent.atomic.AtomicBoolean();
            var gun =
                    new RecordingItem(224001) {
                        @Override
                        public int getItemId() {
                            if (armed.compareAndSet(true, false))
                                player.team.getActiveTeam().clear();
                            return super.getItemId();
                        }
                    };
            player.getInventory().addItem(gun);
            equip(entity.getAvatar(), gun);
            player.getTpsMagazineAmmunition().put(101, 9);
            armed.set(true);
            if (supply) assertDoesNotThrow(() -> TpsWeaponSystem.sendSceneAmmunition(player));
            else
                assertEquals(
                        9,
                        assertDoesNotThrow(() -> TpsWeaponSystem.toSceneWeaponInfo(player, gun))
                                .getAmmunitionList(0)
                                .getCurrentAmmunition());
            assertTrue(player.team.getActiveTeam().isEmpty());
            assertEquals(Map.of(101, 9), player.getTpsMagazineAmmunition());
        }
    }

    @Test
    void anUnwornWeaponsStoredMagazineDoesNotOccupyTheActivePool() throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var entity = sceneAvatar(fixture);
            var gun = item(224002);
            equip(entity.getAvatar(), gun);
            player.getTpsAmmunition().put(1001, 10);
            player.getTpsMagazineAmmunition().putAll(Map.of(101, 9, 201, 9));
            assertEquals(
                    9,
                    TpsWeaponSystem.toSceneWeaponInfo(player, gun)
                            .getAmmunitionList(0)
                            .getCurrentAmmunition());
        }
    }

    @Test
    void aPartyMembersTpsWeaponSeedsTotalsEvenWhenTheCurrentAvatarHasNone() throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            sceneAvatar(fixture);
            var member = avatar(10000007, false);
            var entity =
                    new EntityAvatar(fixture.scene, member) {
                        @Override
                        public void initAbilities() {}
                    };
            player.team.getActiveTeam().add(entity);
            equip(member, item(224001));
            player.packets.clear();
            TpsWeaponSystem.sendSceneAmmunition(player);
            assertEquals(1, player.packets.size());
            assertEquals(
                    PacketOpcodes.TpsRegionalPlaySupplyInfoNotify,
                    player.packets.get(0).getOpcode());
        }
    }

    @Test
    void aShotStoresItsNativeMagazineSnapshotAlongsideTheCost() throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var entity = sceneAvatar(fixture);
            var gun = item(224001);
            equip(entity.getAvatar(), gun);
            TpsWeaponSystem.ensureWeaponEntities(entity.getAvatar(), fixture.scene);
            player.getTpsAmmunition().put(1001, 100);
            player.saves = 0;
            assertTrue(
                    TpsWeaponSystem.onAmmunitionInvoke(
                            player,
                            magazineInvoke(
                                    entity.getId(),
                                    gun.getWeaponEntity().getId(),
                                    0,
                                    101,
                                    1001,
                                    44,
                                    -1)));
            assertEquals(99, TpsWeaponSystem.getReserve(player, 1001));
            assertEquals(44, player.getTpsMagazineAmmunition().get(101));
            assertEquals(1, player.saves);
            assertEquals(
                    44,
                    TpsWeaponSystem.toSceneWeaponInfo(player, gun)
                            .getAmmunitionList(0)
                            .getCurrentAmmunition());
        }
    }

    @Test
    void nativeReloadHasZeroTotalDeltaAndCanRestoreAnExplicitlyEmptyMagazine() throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var entity = sceneAvatar(fixture);
            var gun = item(224001);
            equip(entity.getAvatar(), gun);
            TpsWeaponSystem.ensureWeaponEntities(entity.getAvatar(), fixture.scene);
            player.getTpsAmmunition().put(1001, 100);
            player.getTpsMagazineAmmunition().put(101, 0);
            player.saves = 0;
            var reload =
                    magazineInvoke(
                            entity.getId(), gun.getWeaponEntity().getId(), 1, 101, 1001, 30, 0);
            assertTrue(TpsWeaponSystem.onAmmunitionInvoke(player, reload));
            assertEquals(100, TpsWeaponSystem.getReserve(player, 1001));
            assertEquals(30, player.getTpsMagazineAmmunition().get(101));
            assertEquals(1, player.saves);
            assertTrue(TpsWeaponSystem.onAmmunitionInvoke(player, reload));
            assertEquals(1, player.saves, "the same absolute reload snapshot does not save twice");
            var empty =
                    magazineInvoke(
                            entity.getId(), gun.getWeaponEntity().getId(), 1, 101, 1001, 0, 0);
            assertTrue(TpsWeaponSystem.onAmmunitionInvoke(player, empty));
            assertEquals(
                    0,
                    TpsWeaponSystem.toSceneWeaponInfo(player, gun)
                            .getAmmunitionList(0)
                            .getCurrentAmmunition());
        }
    }

    @Test
    void aReloadCannotSpendOrGrantTotalAmmunition() throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var entity = sceneAvatar(fixture);
            var gun = item(224001);
            equip(entity.getAvatar(), gun);
            TpsWeaponSystem.ensureWeaponEntities(entity.getAvatar(), fixture.scene);
            player.getTpsAmmunition().put(1001, 100);
            player.saves = 0;
            for (int delta : new int[] {-1, 1}) {
                assertFalse(
                        TpsWeaponSystem.onAmmunitionInvoke(
                                player,
                                magazineInvoke(
                                        entity.getId(),
                                        gun.getWeaponEntity().getId(),
                                        1,
                                        101,
                                        1001,
                                        30,
                                        delta)));
                assertEquals(100, TpsWeaponSystem.getReserve(player, 1001));
                assertTrue(player.getTpsMagazineAmmunition().isEmpty());
                assertEquals(0, player.saves);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6})
    void invalidMagazineSnapshotsDoNotSpendOrSaveAnything(int invalid) throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var entity = sceneAvatar(fixture);
            var gun = item(224001);
            equip(entity.getAvatar(), gun);
            TpsWeaponSystem.ensureWeaponEntities(entity.getAvatar(), fixture.scene);
            player.getTpsAmmunition().put(1001, 100);
            player.getTpsMagazineAmmunition().put(101, 45);
            int weapon = invalid == 1 ? 0 : gun.getWeaponEntity().getId();
            int slot = invalid == 2 ? 201 : 101;
            int ammunition = invalid == 3 ? 1002 : 1001;
            int count = invalid == 4 ? 100 : invalid == 5 ? -1 : invalid == 6 ? 46 : 44;
            player.saves = 0;
            assertFalse(
                    TpsWeaponSystem.onAmmunitionInvoke(
                            player,
                            magazineInvoke(
                                    entity.getId(), weapon, 0, slot, ammunition, count, -1)));
            assertEquals(100, TpsWeaponSystem.getReserve(player, 1001));
            assertEquals(Map.of(101, 45), player.getTpsMagazineAmmunition());
            assertEquals(0, player.saves);
        }
    }

    @Test
    void magazinesSharingAPoolCannotReserveMoreThanItsTotal() throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var entity = sceneAvatar(fixture);
            var ice = item(224001);
            var electric = item(224002);
            equip(entity.getAvatar(), ice, electric);
            TpsWeaponSystem.ensureWeaponEntities(entity.getAvatar(), fixture.scene);
            player.getTpsAmmunition().put(1001, 100);
            player.getTpsMagazineAmmunition().putAll(Map.of(101, 60, 201, 40));
            player.saves = 0;
            assertFalse(
                    TpsWeaponSystem.onAmmunitionInvoke(
                            player,
                            magazineInvoke(
                                    entity.getId(),
                                    ice.getWeaponEntity().getId(),
                                    0,
                                    101,
                                    1001,
                                    59,
                                    -10)));
            assertEquals(100, TpsWeaponSystem.getReserve(player, 1001));
            assertEquals(Map.of(101, 60, 201, 40), player.getTpsMagazineAmmunition());
            assertEquals(0, player.saves);
        }
    }

    @Test
    void corruptStoredMagazinesAreBoundedForTheActiveLoadoutWithoutSaving() throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var entity = sceneAvatar(fixture);
            var ice = item(224001);
            var electric = item(224002);
            equip(entity.getAvatar(), ice, electric);
            player.getTpsAmmunition().put(1001, 10);
            player.getTpsMagazineAmmunition().putAll(Map.of(101, 9, 201, 9, 999, 2000));
            player.saves = 0;
            assertEquals(
                    9,
                    TpsWeaponSystem.toSceneWeaponInfo(player, ice)
                            .getAmmunitionList(0)
                            .getCurrentAmmunition());
            assertEquals(
                    1,
                    TpsWeaponSystem.toSceneWeaponInfo(player, electric)
                            .getAmmunitionList(0)
                            .getCurrentAmmunition());
            assertEquals(Map.of(101, 9, 201, 9, 999, 2000), player.getTpsMagazineAmmunition());
            assertEquals(0, player.saves);
        }
    }

    @Test
    void anInvalidLaterMagazineRejectsTheWholeUpdate() throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var entity = sceneAvatar(fixture);
            var gun = item(224001);
            equip(entity.getAvatar(), gun);
            TpsWeaponSystem.ensureWeaponEntities(entity.getAvatar(), fixture.scene);
            player.getTpsAmmunition().put(1001, 100);
            var valid =
                    magazineInvoke(
                            entity.getId(), gun.getWeaponEntity().getId(), 0, 101, 1001, 44, -1);
            var meta =
                    AbilityMetaUpdateTpsWeaponAmmunition.parseFrom(valid.getAbilityData())
                            .toBuilder()
                            .addAccessoryList(
                                    TpsWeaponAccessoryInfo.newBuilder()
                                            .setEntityId(0)
                                            .setAmmunitionType(201)
                                            .setAmmunitionConfigId(1001)
                                            .setCurrentAmmunition(4));
            player.saves = 0;
            assertFalse(
                    TpsWeaponSystem.onAmmunitionInvoke(
                            player,
                            valid.toBuilder().setAbilityData(meta.build().toByteString()).build()));
            assertEquals(100, TpsWeaponSystem.getReserve(player, 1001));
            assertTrue(player.getTpsMagazineAmmunition().isEmpty());
            assertEquals(0, player.saves);
        }
    }

    @Test
    void equipmentReplacementSeedsTotalsBeforeTheNativeWeaponIsAnnounced() throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            player =
                    new RecordingPlayer() {
                        @Override
                        public void sendPacket(BasePacket packet) {
                            if (packet.getOpcode() == PacketOpcodes.TpsRegionalPlaySupplyInfoNotify)
                                assertTrue(
                                        fixture.scene.packets.stream()
                                                .noneMatch(
                                                        previous ->
                                                                previous.getOpcode()
                                                                        == PacketOpcodes
                                                                                .TpsEquipChangeNotify));
                            super.sendPacket(packet);
                        }
                    };
            var entity = sceneAvatar(fixture);
            equip(entity.getAvatar(), item(224001));
            player.loggedIn = true;
            fixture.scene.packets.clear();
            player.packets.clear();
            TpsWeaponSystem.sendEquipChange(entity.getAvatar());
            assertTrue(
                    player.packets.stream()
                            .anyMatch(
                                    packet ->
                                            packet.getOpcode()
                                                    == PacketOpcodes
                                                            .TpsRegionalPlaySupplyInfoNotify));
            assertTrue(
                    fixture.scene.packets.stream()
                            .anyMatch(
                                    packet ->
                                            packet.getOpcode()
                                                    == PacketOpcodes.TpsEquipChangeNotify));
        }
    }

    @Test
    void actualSceneInitializationSeedsTotalsBeforeTheFirstWeaponSnapshot() throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var order = new ArrayList<Integer>();
            player =
                    new RecordingPlayer() {
                        @Override
                        public void sendPacket(BasePacket packet) {
                            order.add(packet.getOpcode());
                            super.sendPacket(packet);
                        }
                    };
            var avatar = sceneAvatar(fixture).getAvatar();
            equip(avatar, item(224001));
            for (String name : List.of("proudSkillList", "talentIdList")) {
                var field = Avatar.class.getDeclaredField(name);
                field.setAccessible(true);
                field.set(avatar, new HashSet<Integer>());
            }
            player.setWorld(fixture.scene.getWorld());
            player.team.setEntity(
                    new emu.grasscutter.game.entity.EntityTeam(player) {
                        @Override
                        public void initAbilities() {}
                    });
            order.clear();
            var cutoff = new IllegalStateException("scene initialization prefix recorded");
            var session =
                    new emu.grasscutter.server.game.GameSession(null) {
                        @Override
                        public Player getPlayer() {
                            return player;
                        }

                        @Override
                        public void send(BasePacket packet) {
                            order.add(packet.getOpcode());
                            if (packet.getOpcode() == PacketOpcodes.PlayerEnterSceneInfoNotify)
                                throw cutoff;
                        }
                    };
            var handler = new emu.grasscutter.server.packet.recv.HandlerSceneInitFinishReq();
            assertSame(
                    cutoff,
                    assertThrows(
                            IllegalStateException.class,
                            () -> handler.handle(session, new byte[0], new byte[0])));
            int supply = order.indexOf(PacketOpcodes.TpsRegionalPlaySupplyInfoNotify);
            int weapons = order.indexOf(PacketOpcodes.PlayerEnterSceneInfoNotify);
            assertTrue(supply >= 0 && supply < weapons, order.toString());
        }
    }

    @Test
    void removingAWeaponReleasesItsMagazineBackToTheUnloadedTotal() throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var entity = sceneAvatar(fixture);
            var gun = item(224001);
            equip(entity.getAvatar(), gun);
            player.getTpsAmmunition().put(1001, 100);
            player.getTpsMagazineAmmunition().put(101, 30);
            assertTrue(player.getInventory().removeItem(gun));
            assertTrue(player.getTpsMagazineAmmunition().isEmpty());
            assertEquals(100, TpsWeaponSystem.getReserve(player, 1001));
        }
    }

    private void realIceGunAbilities() throws Exception {
        var configName = "TPS_Avatar_AutoWeapon_AssaultRifle_01";
        var config =
                JsonParser.parseString(
                                Files.readString(
                                        Path.of(
                                                "resources/BinOutput/Gadget/ConfigGadget_TPS_Avatar_Weapon_AssaultRifle_01.json")))
                        .getAsJsonObject()
                        .get(configName);
        put(
                GameData.getGadgetDataMap(),
                50016001,
                JsonUtils.decode(
                        "{\"id\":50016001,\"jsonName\":\"" + configName + "\"}", GadgetData.class));
        put(
                GameData.getGadgetConfigData(),
                configName,
                JsonUtils.decode(config.toString(), ConfigEntityGadget.class));
        for (var row :
                JsonParser.parseString(
                                Files.readString(
                                        Path.of(
                                                "resources/BinOutput/Ability/Temp/EquipAbilities/TPSWeaponAbilities/ConfigAbility_TPSWeapon_IceGun.json")))
                        .getAsJsonArray()) {
            var ability =
                    JsonUtils.decode(row.getAsJsonObject().get("Default").toString(), AbilityData.class);
            put(GameData.getAbilityDataMap(), ability.abilityName, ability);
        }
    }

    @Test
    void changingEquipmentBindsRealGunAbilitiesToTheWearerBeforeInitialization() throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            realIceGunAbilities();
            var entity = sceneAvatar(fixture);
            var gun = item(224001);
            player.loggedIn = true;
            equip(entity.getAvatar(), gun);
            var weapon = gun.getWeaponEntity();
            assertSame(entity.getAvatar(), weapon.getCasterAvatar());
            assertEquals(5, weapon.getInstancedAbilities().size());
            for (var ability : weapon.getInstancedAbilities()) {
                assertNotNull(ability);
                assertSame(player, ability.getPlayerOwner());
                assertSame(player.getAbilityManager(), ability.getManager());
                assertSame(entity, ability.getCasterEntity());
            }
            assertNotSame(
                    fixture.player.getAbilityManager(), weapon.getInstancedAbilities().get(0).getManager());
        }
    }

    @Test
    void anotherEquipmentNotificationRetainsCasterAndRemovesTheOldWeapon() throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var entity = sceneAvatar(fixture);
            var gun = item(224001);
            equip(entity.getAvatar(), gun);
            TpsWeaponSystem.ensureWeaponEntities(entity.getAvatar(), fixture.scene);
            var old = gun.getWeaponEntity();
            player.loggedIn = true;
            TpsWeaponSystem.sendEquipChange(entity.getAvatar());
            assertNotSame(old, gun.getWeaponEntity());
            assertSame(entity.getAvatar(), gun.getWeaponEntity().getCasterAvatar());
            assertNull(fixture.scene.getEntityById(old.getId()));
        }
    }

    @Test
    void removedAvatarCannotContinueSpendingAmmoFromTheOldActiveTeam() throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var entity = sceneAvatar(fixture);
            equip(entity.getAvatar(), item(224001));
            fixture.scene.getEntities().remove(entity.getId());
            assertFalse(TpsWeaponSystem.onAmmunitionInvoke(player, invoke(entity.getId(), 0, 1001, -1)));
            assertEquals(2000, TpsWeaponSystem.getReserve(player, 1001));
        }
    }

    @Test
    void replacedAvatarWithTheSameIdCannotSpendThePreviousAvatarsAmmo() throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var entity = sceneAvatar(fixture);
            equip(entity.getAvatar(), item(224001));
            fixture.scene.getEntities().put(entity.getId(), fixture.entity);
            assertFalse(TpsWeaponSystem.onAmmunitionInvoke(player, invoke(entity.getId(), 0, 1001, -1)));
        }
    }

    @Test
    void removedWeaponCannotSpendAmmoThroughItsItemsStalePointer() throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var entity = sceneAvatar(fixture);
            var gun = item(224001);
            equip(entity.getAvatar(), gun);
            TpsWeaponSystem.ensureWeaponEntities(entity.getAvatar(), fixture.scene);
            var weapon = gun.getWeaponEntity();
            fixture.scene.getWeaponEntities().remove(weapon.getId());
            assertFalse(TpsWeaponSystem.onAmmunitionInvoke(player, invoke(weapon.getId(), 0, 1001, -1)));
            assertEquals(2000, TpsWeaponSystem.getReserve(player, 1001));
        }
    }

    @Test
    void aWeaponBoundToAnotherAvatarCannotSpendTheCurrentAvatarsAmmo() throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var entity = sceneAvatar(fixture);
            var gun = item(224001);
            equip(entity.getAvatar(), gun);
            var foreign =
                    new emu.grasscutter.game.entity.EntityWeapon(
                            fixture.scene, 50016001, avatar(10000007, false));
            gun.setWeaponEntity(foreign);
            fixture.scene.getWeaponEntities().put(foreign.getId(), foreign);
            assertFalse(TpsWeaponSystem.onAmmunitionInvoke(player, invoke(foreign.getId(), 0, 1001, -1)));
        }
    }

    private AbilityInvokeEntry clientAmmo(int entityId, int type, int localId, int... pairs) {
        return invoke(entityId, type, pairs).toBuilder()
                .setArgumentType(AbilityInvokeArgument.ABILITY_META_UPDATE_TPS_WEAPON_AMMUNITION)
                .setForwardType(
                        emu.grasscutter.net.proto.ForwardTypeOuterClass.ForwardType.ForwardType_FORWARD_TO_ALL)
                .setHead(AbilityInvokeEntryHead.newBuilder().setLocalId(localId).setInstancedAbilityId(1))
                .build();
    }

    private void dispatchAmmo(
            String kind, AbilityTestFixture fixture, List<AbilityInvokeEntry> entries) throws Exception {
        player.setWorld(fixture.scene.getWorld());
        var session =
                new emu.grasscutter.server.game.GameSession(null) {
                    @Override
                    public Player getPlayer() {
                        return player;
                    }
                };
        switch (kind) {
            case "invocations" -> new emu.grasscutter.server.packet.recv.HandlerAbilityInvocationsNotify()
                    .handle(
                            session,
                            new byte[0],
                            emu.grasscutter.net.proto.AbilityInvocationsNotifyOuterClass.AbilityInvocationsNotify
                                    .newBuilder()
                                    .addAllInvokes(entries)
                                    .build()
                                    .toByteArray());
            case "change" -> new emu.grasscutter.server.packet.recv.HandlerClientAbilityChangeNotify()
                    .handle(
                            session,
                            new byte[0],
                            emu.grasscutter.net.proto.ClientAbilityChangeNotifyOuterClass
                                    .ClientAbilityChangeNotify.newBuilder()
                                    .addAllInvokes(entries)
                                    .build()
                                    .toByteArray());
            case "init" -> new emu.grasscutter.server.packet.recv.HandlerClientAbilityInitFinishNotify()
                    .handle(
                            session,
                            new byte[0],
                            emu.grasscutter.net.proto.ClientAbilityInitFinishNotifyOuterClass
                                    .ClientAbilityInitFinishNotify.newBuilder()
                                    .addAllInvokes(entries)
                                    .build()
                                    .toByteArray());
            case "combine" -> new emu.grasscutter.server.packet.recv
                            .HandlerClientAbilitiesInitFinishCombineNotify()
                    .handle(
                            session,
                            new byte[0],
                            emu.grasscutter.net.proto.ClientAbilitiesInitFinishCombineNotifyOuterClass
                                    .ClientAbilitiesInitFinishCombineNotify.newBuilder()
                                    .addEntityInvokeList(
                                            emu.grasscutter.net.proto.EntityAbilityInvokeEntryOuterClass
                                                    .EntityAbilityInvokeEntry.newBuilder()
                                                    .addAllInvokes(entries))
                                    .build()
                                    .toByteArray());
            default -> fail(kind);
        }
        player.getAbilityInvokeHandler().update(player);
        player.getClientAbilityInitFinishHandler().update(player);
    }

    private List<AbilityInvokeEntry> forwarded(AbilityTestFixture fixture) throws Exception {
        var result = new ArrayList<AbilityInvokeEntry>();
        for (var packet : fixture.scene.packets) {
            if (packet.getOpcode() == PacketOpcodes.AbilityInvocationsNotify)
                result.addAll(
                        emu.grasscutter.net.proto.AbilityInvocationsNotifyOuterClass.AbilityInvocationsNotify
                                .parseFrom(packet.getData())
                                .getInvokesList());
            if (packet.getOpcode() == PacketOpcodes.ClientAbilityInitFinishNotify)
                result.addAll(
                        emu.grasscutter.net.proto.ClientAbilityInitFinishNotifyOuterClass
                                .ClientAbilityInitFinishNotify.parseFrom(packet.getData())
                                .getInvokesList());
        }
        return result;
    }

    @ParameterizedTest
    @ValueSource(strings = {"invocations", "change", "init", "combine"})
    void ammunitionMetaWithLocalIdReachesTheReserveHandlerAtEveryEntryPoint(String kind)
            throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var entity = sceneAvatar(fixture);
            equip(entity.getAvatar(), item(224001));
            player.saves = 0;
            var cost = clientAmmo(entity.getId(), 0, 32771, 1001, -3);
            fixture.scene.packets.clear();
            dispatchAmmo(kind, fixture, List.of(cost));
            assertEquals(1997, TpsWeaponSystem.getReserve(player, 1001));
            assertEquals(1, player.saves);
            assertEquals(List.of(cost), forwarded(fixture));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"invocations", "change", "init", "combine"})
    void rejectedAmmunitionMetaIsNotForwardedButTheNextValidEntryIsProcessed(String kind)
            throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var entity = sceneAvatar(fixture);
            equip(entity.getAvatar(), item(224001));
            var foreign = clientAmmo(42, 0, 0, 1001, -1);
            var grant = clientAmmo(entity.getId(), 3, 0, 1001, 300);
            var overdraw = clientAmmo(entity.getId(), 1, 0, 1001, -2001);
            var valid = clientAmmo(entity.getId(), 0, 0, 1001, -2);
            player.saves = 0;
            fixture.scene.packets.clear();
            dispatchAmmo(kind, fixture, List.of(foreign, grant, overdraw, valid));
            assertEquals(1998, TpsWeaponSystem.getReserve(player, 1001));
            assertEquals(1, player.saves);
            assertEquals(List.of(valid), forwarded(fixture));
        }
    }

    @Test
    void legacyAbilityManagerEntryPointAlsoProcessesAmmunitionMetaWithLocalId() throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var entity = sceneAvatar(fixture);
            equip(entity.getAvatar(), item(224001));
            player.getAbilityManager().onAbilityInvoke(clientAmmo(entity.getId(), 0, 32771, 1001, -4));
            assertEquals(1996, TpsWeaponSystem.getReserve(player, 1001));
        }
    }

    @Test
    void currentWeaponCanSendAnAmmunitionMetaWithLocalId() throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var entity = sceneAvatar(fixture);
            var gun = item(224001);
            equip(entity.getAvatar(), gun);
            TpsWeaponSystem.ensureWeaponEntities(entity.getAvatar(), fixture.scene);
            dispatchAmmo(
                    "invocations",
                    fixture,
                    List.of(clientAmmo(gun.getWeaponEntity().getId(), 0, 32771, 1001, -4)));
            assertEquals(1996, TpsWeaponSystem.getReserve(player, 1001));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"invocations", "change", "init", "combine"})
    void malformedAmmoDoesNotBlockLaterValidEntriesOrGetForwarded(String kind) throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var entity = sceneAvatar(fixture);
            equip(entity.getAvatar(), item(224001));
            var malformed =
                    clientAmmo(entity.getId(), 0, 0).toBuilder()
                            .setAbilityData(com.google.protobuf.ByteString.copyFrom(new byte[] {(byte) 0x80}))
                            .build();
            var valid = clientAmmo(entity.getId(), 0, 0, 1001, -2);
            fixture.scene.packets.clear();
            assertDoesNotThrow(() -> dispatchAmmo(kind, fixture, List.of(malformed, valid)));
            assertEquals(1998, TpsWeaponSystem.getReserve(player, 1001));
            assertEquals(List.of(valid), forwarded(fixture));
        }
    }

    @Test
    void anAmmunitionMetaBeforeSceneCreationIsIgnored() {
        assertDoesNotThrow(
                () -> player.getAbilityManager().onAbilityInvoke(clientAmmo(42, 0, 0, 1001, -1)));
        assertEquals(0, player.saves);
    }

    private AbilityInvokeEntry timedMagazine(
            EntityAvatar entity, GameItem gun, int type, int count, int delta, double time) {
        return magazineInvoke(
                        entity.getId(),
                        gun.getWeaponEntity().getId(),
                        type,
                        101,
                        1001,
                        count,
                        delta)
                .toBuilder()
                .setArgumentType(AbilityInvokeArgument.ABILITY_META_UPDATE_TPS_WEAPON_AMMUNITION)
                .setForwardType(
                        emu.grasscutter.net.proto.ForwardTypeOuterClass.ForwardType
                                .ForwardType_FORWARD_TO_ALL)
                .setHead(
                        AbilityInvokeEntryHead.newBuilder()
                                .setLocalId(32771)
                                .setInstancedAbilityId(1))
                .setTotalTickTime(time)
                .build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"invocations", "change", "init", "combine"})
    void theSameTimedShotIsSpentSavedAndForwardedOnce(String kind) throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var entity = sceneAvatar(fixture);
            var gun = item(224001);
            equip(entity.getAvatar(), gun);
            TpsWeaponSystem.ensureWeaponEntities(entity.getAvatar(), fixture.scene);
            player.getTpsAmmunition().put(1001, 100);
            player.saves = 0;
            var shot = timedMagazine(entity, gun, 0, 44, -1, 12.5);
            fixture.scene.packets.clear();
            dispatchAmmo(kind, fixture, List.of(shot, shot));
            assertEquals(99, TpsWeaponSystem.getReserve(player, 1001));
            assertEquals(44, player.getTpsMagazineAmmunition().get(101));
            assertEquals(1, player.saves);
            assertEquals(List.of(shot), forwarded(fixture));
        }
    }

    @Test
    void replayingAnOldShotAfterReloadCannotSpendAgainOrRestoreTheOldMagazine() throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var entity = sceneAvatar(fixture);
            var gun = item(224001);
            equip(entity.getAvatar(), gun);
            TpsWeaponSystem.ensureWeaponEntities(entity.getAvatar(), fixture.scene);
            player.getTpsAmmunition().put(1001, 100);
            var shot = timedMagazine(entity, gun, 0, 44, -1, 12.5);
            assertTrue(TpsWeaponSystem.onAmmunitionInvoke(player, shot));
            assertTrue(
                    TpsWeaponSystem.onAmmunitionInvoke(
                            player, timedMagazine(entity, gun, 1, 45, 0, 13)));
            int saves = player.saves;
            assertFalse(TpsWeaponSystem.onAmmunitionInvoke(player, shot));
            assertEquals(99, TpsWeaponSystem.getReserve(player, 1001));
            assertEquals(45, player.getTpsMagazineAmmunition().get(101));
            assertEquals(saves, player.saves);
        }
    }

    @Test
    void replayingAnOldReloadCannotRollBackASubsequentShot() throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var entity = sceneAvatar(fixture);
            var gun = item(224001);
            equip(entity.getAvatar(), gun);
            TpsWeaponSystem.ensureWeaponEntities(entity.getAvatar(), fixture.scene);
            player.getTpsAmmunition().put(1001, 100);
            var reload = timedMagazine(entity, gun, 1, 45, 0, 12);
            assertTrue(TpsWeaponSystem.onAmmunitionInvoke(player, reload));
            assertTrue(
                    TpsWeaponSystem.onAmmunitionInvoke(
                            player, timedMagazine(entity, gun, 0, 44, -1, 13)));
            int saves = player.saves;
            assertFalse(TpsWeaponSystem.onAmmunitionInvoke(player, reload));
            assertEquals(99, TpsWeaponSystem.getReserve(player, 1001));
            assertEquals(44, player.getTpsMagazineAmmunition().get(101));
            assertEquals(saves, player.saves);
        }
    }

    @Test
    void aSaveFailureCannotMakeTheSameTimedShotSpendTwice() throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var entity = sceneAvatar(fixture);
            var gun = item(224001);
            equip(entity.getAvatar(), gun);
            TpsWeaponSystem.ensureWeaponEntities(entity.getAvatar(), fixture.scene);
            player.getTpsAmmunition().put(1001, 100);
            player.saves = 0;
            player.failNextSave = true;
            var shot = timedMagazine(entity, gun, 0, 44, -1, 12.5);
            assertThrows(
                    IllegalStateException.class,
                    () -> TpsWeaponSystem.onAmmunitionInvoke(player, shot));
            assertEquals(99, TpsWeaponSystem.getReserve(player, 1001));
            assertFalse(TpsWeaponSystem.onAmmunitionInvoke(player, shot));
            assertEquals(99, TpsWeaponSystem.getReserve(player, 1001));
            assertEquals(44, player.getTpsMagazineAmmunition().get(101));
            assertEquals(1, player.saves);
        }
    }

    @Test
    void anInvalidatedSourceCannotSpendOrRecordAnAmmunitionReport() throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var entity = sceneAvatar(fixture);
            var gun = item(224001);
            equip(entity.getAvatar(), gun);
            TpsWeaponSystem.ensureWeaponEntities(entity.getAvatar(), fixture.scene);
            player.getTpsAmmunition().put(1001, 100);
            player.saves = 0;
            var shot = timedMagazine(entity, gun, 0, 44, -1, 12.5);
            entity.invalidateAbilityLifecycle();
            assertFalse(TpsWeaponSystem.onAmmunitionInvoke(player, shot));
            assertEquals(100, TpsWeaponSystem.getReserve(player, 1001));
            assertEquals(0, player.saves);
            entity.activateAbilityLifecycle();
            assertTrue(TpsWeaponSystem.onAmmunitionInvoke(player, shot));
            assertEquals(99, TpsWeaponSystem.getReserve(player, 1001));
        }
    }

    @Test
    void aRecreatedSnapshotWeaponDoesNotInheritAnOldWeaponsReceiptWhenItsIdIsReused()
            throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var entity = sceneAvatar(fixture);
            var gun = item(224001);
            equip(entity.getAvatar(), gun);
            TpsWeaponSystem.ensureWeaponEntities(entity.getAvatar(), fixture.scene);
            player.getTpsAmmunition().put(1001, 100);
            var shot = timedMagazine(entity, gun, 0, 44, -1, 12.5);
            assertTrue(TpsWeaponSystem.onAmmunitionInvoke(player, shot));
            var previous = gun.getWeaponEntity();
            int reusedId = previous.getId();
            TpsWeaponSystem.releaseSceneWeapons(player, fixture.scene);
            TpsWeaponSystem.ensureWeaponEntities(entity.getAvatar(), fixture.scene);
            var replacement = gun.getWeaponEntity();
            assertNotSame(previous, replacement);
            fixture.scene.getWeaponEntities().remove(replacement.getId());
            replacement.setId(reusedId);
            fixture.scene.getWeaponEntities().put(reusedId, replacement);
            assertEquals(shot, timedMagazine(entity, gun, 0, 44, -1, 12.5));
            assertTrue(TpsWeaponSystem.onAmmunitionInvoke(player, shot));
            assertEquals(98, TpsWeaponSystem.getReserve(player, 1001));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"time", "event", "payload"})
    void distinctTimedReportsAreNotMerged(String difference) throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var entity = sceneAvatar(fixture);
            var gun = item(224001);
            equip(entity.getAvatar(), gun);
            TpsWeaponSystem.ensureWeaponEntities(entity.getAvatar(), fixture.scene);
            player.getTpsAmmunition().put(1001, 100);
            var shot = timedMagazine(entity, gun, 0, 44, -1, 12.5);
            assertTrue(TpsWeaponSystem.onAmmunitionInvoke(player, shot));
            var next =
                    switch (difference) {
                        case "time" -> shot.toBuilder().setTotalTickTime(13).build();
                        case "event" -> shot.toBuilder().setEventId(1).build();
                        default -> timedMagazine(entity, gun, 0, 43, -1, 12.5);
                    };
            assertTrue(TpsWeaponSystem.onAmmunitionInvoke(player, next));
            assertEquals(98, TpsWeaponSystem.getReserve(player, 1001));
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, -1, Double.NaN, Double.POSITIVE_INFINITY})
    void anUnidentifiedClockDoesNotMakeSeparateReportsDuplicates(double time) throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var entity = sceneAvatar(fixture);
            var gun = item(224001);
            equip(entity.getAvatar(), gun);
            TpsWeaponSystem.ensureWeaponEntities(entity.getAvatar(), fixture.scene);
            player.getTpsAmmunition().put(1001, 100);
            var shot = timedMagazine(entity, gun, 0, 44, -1, time);
            assertTrue(TpsWeaponSystem.onAmmunitionInvoke(player, shot));
            assertTrue(TpsWeaponSystem.onAmmunitionInvoke(player, shot));
            assertEquals(98, TpsWeaponSystem.getReserve(player, 1001));
        }
    }

    @Test
    void totalOnlyFeesRetainNativeHandlingWithoutInferringAMagazineOperationIdentity()
            throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var entity = sceneAvatar(fixture);
            equip(entity.getAvatar(), item(224001));
            player.getTpsAmmunition().put(1001, 100);
            var fee =
                    clientAmmo(entity.getId(), 0, 32771, 1001, -1).toBuilder()
                            .setTotalTickTime(12.5)
                            .build();
            assertTrue(TpsWeaponSystem.onAmmunitionInvoke(player, fee));
            assertTrue(TpsWeaponSystem.onAmmunitionInvoke(player, fee));
            assertEquals(98, TpsWeaponSystem.getReserve(player, 1001));
        }
    }

    @Test
    void aTimedReportFromTheActualWeaponEntityIsAlsoSpentOnce() throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var entity = sceneAvatar(fixture);
            var gun = item(224001);
            equip(entity.getAvatar(), gun);
            TpsWeaponSystem.ensureWeaponEntities(entity.getAvatar(), fixture.scene);
            player.getTpsAmmunition().put(1001, 100);
            var shot =
                    timedMagazine(entity, gun, 0, 44, -1, 12.5).toBuilder()
                            .setEntityId(gun.getWeaponEntity().getId())
                            .build();
            assertTrue(TpsWeaponSystem.onAmmunitionInvoke(player, shot));
            assertFalse(TpsWeaponSystem.onAmmunitionInvoke(player, shot));
            assertEquals(99, TpsWeaponSystem.getReserve(player, 1001));
        }
    }

    @Test
    void aRejectedReportCanBeRetriedAfterItsStatePreconditionIsSatisfied() throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var entity = sceneAvatar(fixture);
            var gun = item(224001);
            equip(entity.getAvatar(), gun);
            TpsWeaponSystem.ensureWeaponEntities(entity.getAvatar(), fixture.scene);
            player.getTpsAmmunition().put(1001, 0);
            var shot = timedMagazine(entity, gun, 0, 44, -1, 12.5);
            assertFalse(TpsWeaponSystem.onAmmunitionInvoke(player, shot));
            player.getTpsAmmunition().put(1001, 100);
            assertTrue(TpsWeaponSystem.onAmmunitionInvoke(player, shot));
            assertEquals(99, TpsWeaponSystem.getReserve(player, 1001));
        }
    }

    @Test
    void concurrentCopiesOfOneTimedReportSpendOnce() throws Exception {
        try (var fixture = new AbilityTestFixture()) {
            var entity = sceneAvatar(fixture);
            var gun = item(224001);
            equip(entity.getAvatar(), gun);
            TpsWeaponSystem.ensureWeaponEntities(entity.getAvatar(), fixture.scene);
            player.getTpsAmmunition().put(1001, 100);
            player.saves = 0;
            var shot = timedMagazine(entity, gun, 0, 44, -1, 12.5);
            var pool = java.util.concurrent.Executors.newFixedThreadPool(4);
            try {
                var attempts = new ArrayList<java.util.concurrent.Future<Boolean>>();
                for (int i = 0; i < 20; i++)
                    attempts.add(
                            pool.submit(() -> TpsWeaponSystem.onAmmunitionInvoke(player, shot)));
                int accepted = 0;
                for (var attempt : attempts)
                    if (attempt.get(5, java.util.concurrent.TimeUnit.SECONDS)) accepted++;
                assertEquals(1, accepted);
                assertEquals(99, TpsWeaponSystem.getReserve(player, 1001));
                assertEquals(1, player.saves);
            } finally {
                pool.shutdownNow();
            }
        }
    }
}
