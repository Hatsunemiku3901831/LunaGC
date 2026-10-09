package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.tps.*;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.tps.TpsWeaponSystem;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.TpsEquipChangeNotifyOuterClass.TpsEquipChangeNotify;
import emu.grasscutter.utils.JsonUtils;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TpsAccessoryOwnershipTest {
    private static List<TpsWeaponAccessoryData> accessories;
    private static List<TpsWeaponData> weapons;
    private static List<ItemData> materials;
    private TpsEquipmentTest.RecordingPlayer player;
    private final List<Runnable> restore = new ArrayList<>();

    @BeforeAll
    static void resources() throws Exception {
        Grasscutter.getLogger();
        var root = Path.of("resources/ExcelBinOutput");
        accessories =
                JsonUtils.loadToList(
                        root.resolve("TpsWeaponAccessoryExcelConfigData.json"), TpsWeaponAccessoryData.class);
        weapons =
                JsonUtils.loadToList(root.resolve("TpsWeaponExcelConfigData.json"), TpsWeaponData.class);
        var ids = accessories.stream().map(TpsWeaponAccessoryData::getUnlockMaterialId).toList();
        materials =
                JsonUtils.loadToList(root.resolve("MaterialExcelConfigData.json"), ItemData.class).stream()
                        .filter(d -> ids.contains(d.getId()))
                        .toList();
        materials.forEach(ItemData::onLoad);
        assertEquals(12, accessories.size());
        assertEquals(12, materials.size());
    }

    @BeforeEach
    void setup() {
        player = new TpsEquipmentTest.RecordingPlayer();
        player.setUid(74001);
        accessories.forEach(d -> put(GameData.getTpsWeaponAccessoryDataMap(), d.getId(), d));
        weapons.forEach(
                d -> {
                    put(GameData.getTpsWeaponDataMap(), d.getId(), d);
                    var item =
                            JsonUtils.decode(
                                    "{\"id\":" + d.getId() + ",\"itemType\":\"ITEM_TPS_WEAPON\"}", ItemData.class);
                    item.onLoad();
                    put(GameData.getItemDataMap(), d.getId(), item);
                });
        materials.forEach(d -> put(GameData.getItemDataMap(), d.getId(), d));
    }

    private <K, V> void put(Map<K, V> map, K key, V value) {
        var old = map.put(key, value);
        restore.add(
                () -> {
                    if (old == null) map.remove(key);
                    else map.put(key, old);
                });
    }

    @AfterEach
    void cleanup() {
        Collections.reverse(restore);
        restore.forEach(Runnable::run);
    }

    private TpsEquipmentTest.RecordingItem item(int id) {
        var result = new TpsEquipmentTest.RecordingItem(id);
        result.setCount(1);
        return result;
    }

    private TpsEquipmentTest.RecordingItem add(int id) {
        var result = item(id);
        assertTrue(player.getInventory().addItem(result));
        return result;
    }

    private TpsWeaponAccessoryData accessory(int id) {
        return GameData.getTpsWeaponAccessoryDataMap().get(id);
    }

    private void assertUnlocked(TpsEquipmentTest.RecordingItem gun, TpsWeaponAccessoryData data) {
        assertEquals(List.of(data.getId()), TpsWeaponSystem.getValidAccessories(gun));
        var proto = TpsWeaponSystem.toTpsWeaponProto(gun);
        assertEquals(List.of(data.getId()), proto.getAccessoryIdListList());
        assertEquals(
                data.getEquipAffixId() % 10, proto.getAffixMapMap().get(data.getEquipAffixId() / 10));
    }

    @ParameterizedTest
    @ValueSource(
            ints = {
                224505, 224506, 224507, 224508, 224509, 224510, 224511, 224512, 224521, 224522, 224523,
                224524
            })
    void materialBeforeWeaponUnlocksRealResourceAccessory(int id) {
        var data = accessory(id);
        add(data.getUnlockMaterialId());
        var gun = add(data.getTpsWeaponId());
        assertUnlocked(gun, data);
        assertEquals(1, gun.saves, "Initial insert should persist the reconciled item once");
    }

    @ParameterizedTest
    @ValueSource(
            ints = {
                224505, 224506, 224507, 224508, 224509, 224510, 224511, 224512, 224521, 224522, 224523,
                224524
            })
    void materialAfterWeaponUnlocksRealResourceAccessory(int id) {
        var data = accessory(id);
        var gun = add(data.getTpsWeaponId());
        add(data.getUnlockMaterialId());
        assertUnlocked(gun, data);
        assertEquals(2, gun.saves);
    }

    @ParameterizedTest
    @ValueSource(
            ints = {
                224505, 224506, 224507, 224508, 224509, 224510, 224511, 224512, 224521, 224522, 224523,
                224524
            })
    void removingLastMaterialRevokesItsAccessoryAndAffix(int id) {
        var data = accessory(id);
        var gun = add(data.getTpsWeaponId());
        var token = add(data.getUnlockMaterialId());
        assertUnlocked(gun, data);
        assertTrue(player.getInventory().removeItem(token, 1));
        assertTrue(gun.getTpsAccessoryIds().isEmpty());
        assertFalse(TpsWeaponSystem.getAffixLevels(gun).containsKey(data.getEquipAffixId() / 10));
        assertEquals(3, gun.saves);
    }

    @Test
    void bulkAwardWorksRegardlessOfInputOrder() {
        var gun = item(224001);
        var tokens =
                accessories.stream()
                        .filter(d -> d.getTpsWeaponId() == 224001)
                        .map(d -> item(d.getUnlockMaterialId()))
                        .toList();
        var award = new ArrayList<GameItem>();
        award.add(tokens.get(0));
        award.add(gun);
        award.addAll(tokens.subList(1, tokens.size()));
        player.getInventory().addItems(award);
        assertEquals(List.of(224505, 224506, 224507, 224508), gun.getTpsAccessoryIds());
        assertEquals(2, gun.saves, "Batch material changes should reconcile each weapon once");
    }

    @Test
    void mergingMaterialStackReconcilesAndNonzeroRemovalKeepsUnlock() {
        var data = accessory(224506);
        var gun = add(data.getTpsWeaponId());
        var token = item(data.getUnlockMaterialId());
        token.setCount(0);
        assertTrue(player.getInventory().addItem(token));
        assertTrue(gun.getTpsAccessoryIds().isEmpty());
        // Higher stack capacity exercises the general merge/removal path; shipped tokens have limit 1.
        var stack =
                JsonUtils.decode(
                        "{\"id\":223112,\"itemType\":\"ITEM_MATERIAL\",\"stackLimit\":10}", ItemData.class);
        stack.onLoad();
        put(GameData.getItemDataMap(), 223112, stack);
        token.setItemData(stack);
        var incoming = item(223112);
        incoming.setCount(2);
        assertTrue(player.getInventory().addItem(incoming));
        assertUnlocked(gun, data);
        assertTrue(player.getInventory().removeItem(token, 1));
        assertUnlocked(gun, data);
        assertEquals(2, gun.saves, "An unchanged ownership set should not save the gun again");
        assertTrue(player.getInventory().removeItem(token, 1));
        assertTrue(gun.getTpsAccessoryIds().isEmpty());
    }

    @Test
    void manualAndLegacyUnlocksSurviveMaterialRemoval() {
        var gun = add(224001);
        gun.getTpsAccessoryIds().add(224505); // Old persisted item, before provenance existed.
        assertTrue(TpsWeaponSystem.unlockAccessory(player, 224506));
        var first = add(223111);
        var second = add(223112);
        var automatic = add(223113);
        assertEquals(List.of(224505, 224506, 224507), gun.getTpsAccessoryIds());
        player.getInventory().removeItem(first);
        player.getInventory().removeItem(second);
        player.getInventory().removeItem(automatic);
        assertEquals(List.of(224505, 224506), gun.getTpsAccessoryIds());
    }

    @Test
    void ownershipDoesNotLeakToAnotherWeaponOrPlayer() {
        var gun = add(224001);
        var otherGun = add(224002);
        var other = new TpsEquipmentTest.RecordingPlayer();
        other.setUid(74002);
        assertTrue(other.getInventory().addItem(item(223112)));
        assertTrue(gun.getTpsAccessoryIds().isEmpty());
        add(223112);
        assertEquals(List.of(224506), gun.getTpsAccessoryIds());
        assertTrue(otherGun.getTpsAccessoryIds().isEmpty());
    }

    private static class QuietAvatar extends TpsEquipmentTest.RecordingAvatar {
        QuietAvatar() {
            super(10000134);
        }

        @Override
        public void recalcStats(boolean force, boolean notify) {
            recalcs++;
        }
    }

    @Test
    void quietInitializationUpdatesWearerWithoutPackets() {
        var gun = add(224001);
        var avatar = new QuietAvatar();
        player.getAvatars().addAvatar(avatar);
        avatar.getTpsWeaponIds().add(224001);
        player.packets.clear();
        assertTrue(player.getInventory().addItem(item(223112), false, false));
        assertEquals(List.of(224506), gun.getTpsAccessoryIds());
        assertEquals(1, avatar.recalcs);
        assertTrue(player.packets.isEmpty());
    }

    @Test
    void onlineWearerReceivesUpdatedEquipmentAndRemoval() throws Exception {
        var gun = add(224001);
        var avatar = new QuietAvatar();
        player.getAvatars().addAvatar(avatar);
        avatar.getTpsWeaponIds().add(224001);
        player.loggedIn = true;
        player.packets.clear();
        var token = add(223112);
        var notifications =
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.TpsEquipChangeNotify)
                        .toList();
        assertEquals(1, notifications.size());
        var equipment = TpsEquipChangeNotify.parseFrom(notifications.get(0).getData());
        assertEquals(avatar.getGuid(), equipment.getAvatarGuid());
        assertEquals(1, equipment.getTpsWeaponListCount());
        assertTrue(equipment.getTpsWeaponList(0).getAffixMapMap().containsKey(224012));
        assertEquals(1, avatar.recalcs);
        player.packets.clear();
        player.getInventory().removeItem(token);
        var removed =
                player.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.TpsEquipChangeNotify)
                        .findFirst()
                        .orElseThrow();
        assertFalse(
                TpsEquipChangeNotify.parseFrom(removed.getData())
                        .getTpsWeaponList(0)
                        .getAffixMapMap()
                        .containsKey(224012));
        assertEquals(2, avatar.recalcs);
    }

    @Test
    void restoreRemovesOnlyExpiredMaterialGrantsAndIsIdempotent() {
        var gun = add(224001);
        gun.setTpsAccessoryIds(new ArrayList<>(List.of(224505, 224506, 999999)));
        gun.setTpsMaterialAccessoryIds(new ArrayList<>(List.of(224506)));
        player.packets.clear();
        TpsWeaponSystem.restoreAccessories(player);
        assertEquals(List.of(224505), gun.getTpsAccessoryIds());
        assertTrue(gun.getTpsMaterialAccessoryIds().isEmpty());
        assertEquals(2, gun.saves);
        assertTrue(player.packets.isEmpty());
        TpsWeaponSystem.restoreAccessories(player);
        assertEquals(2, gun.saves);
    }

    @Test
    void restoreFindsAllRegisteredMaterialsAndKeepsLegacyIdsManual() {
        var gun = add(224001);
        add(223112);
        gun.setTpsAccessoryIds(new ArrayList<>(List.of(224505)));
        gun.setTpsMaterialAccessoryIds(null); // Legacy Mongo document without the field.
        player.packets.clear();
        TpsWeaponSystem.restoreAccessories(player);
        assertEquals(List.of(224505, 224506), gun.getTpsAccessoryIds());
        assertEquals(List.of(224506), gun.getTpsMaterialAccessoryIds());
        assertTrue(player.packets.isEmpty());
    }

    @Test
    void reconciliationRejectsUnregisteredAndForeignWeapons() {
        add(223112);
        var detached = item(224001);
        detached.setOwner(player);
        assertFalse(TpsWeaponSystem.reconcileAccessories(player, detached));
        var other = new TpsEquipmentTest.RecordingPlayer();
        other.setUid(74002);
        detached.setOwner(other);
        player.getInventory().getItems().put(detached.getGuid(), detached);
        assertFalse(TpsWeaponSystem.reconcileAccessories(player, detached));
        assertTrue(detached.getTpsAccessoryIds().isEmpty());
    }

    @Test
    void reconciliationRejectsForeignMaterials() {
        var gun = add(224001);
        var token = add(223112);
        var other = new TpsEquipmentTest.RecordingPlayer();
        other.setUid(74002);
        token.setOwner(other);
        TpsWeaponSystem.restoreAccessories(player);
        assertTrue(gun.getTpsAccessoryIds().isEmpty());
    }

    @Test
    void oneMaterialCanUnlockSeveralResourceMappedWeapons() {
        var linked =
                JsonUtils.decode(
                        "{\"id\":224999,\"tpsWeaponId\":224002,\"unlockMaterialId\":223112,\"equipAffixId\":2240211}",
                        TpsWeaponAccessoryData.class);
        put(GameData.getTpsWeaponAccessoryDataMap(), linked.getId(), linked);
        var ice = add(224001);
        var electric = add(224002);
        var token = add(223112);
        assertEquals(List.of(224506), ice.getTpsAccessoryIds());
        assertEquals(List.of(224999), electric.getTpsAccessoryIds());
        assertEquals(2, ice.saves);
        assertEquals(2, electric.saves);
        player.getInventory().removeItem(token);
        assertTrue(ice.getTpsAccessoryIds().isEmpty());
        assertTrue(electric.getTpsAccessoryIds().isEmpty());
    }

    @Test
    void duplicateMaterialAwardsDoNotResaveOrNotifyTheUnchangedWeapon() {
        var gun = add(224001);
        add(223112);
        player.loggedIn = true;
        player.packets.clear();
        add(223112); // Shipped material limit is 1.
        assertEquals(2, gun.saves);
        assertEquals(1, player.packets.size(), "Only the material stack notification is needed");
        assertEquals(List.of(224506), gun.getTpsMaterialAccessoryIds());
    }
}
