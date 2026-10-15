package emu.grasscutter.game.tps;

import com.google.protobuf.InvalidProtocolBufferException;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.tps.TpsAmmunitionData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.EntityWeapon;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.inventory.ItemType;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.net.proto.AbilityMetaUpdateTpsWeaponAmmunitionOuterClass.AbilityMetaUpdateTpsWeaponAmmunition;
import emu.grasscutter.net.proto.AbilitySyncStateInfoOuterClass.AbilitySyncStateInfo;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.SceneWeaponInfoOuterClass.SceneWeaponInfo;
import emu.grasscutter.net.proto.TpsAmmunitionChangeNotifyOuterClass.*;
import emu.grasscutter.net.proto.TpsEquipChangeNotifyOuterClass.TpsEquipChangeNotify;
import emu.grasscutter.net.proto.TpsRegionalPlaySupplyInfoNotifyOuterClass.*;
import emu.grasscutter.net.proto.TpsWeaponAmmunitionInfoOuterClass.TpsWeaponAmmunitionInfo;
import emu.grasscutter.net.proto._TpsWeaponOuterClass._TpsWeapon;
import emu.grasscutter.server.packet.send.PacketAbilityChangeNotify;
import it.unimi.dsi.fastutil.ints.*;
import java.util.*;

/**
 * Resource-backed TPS equipment. Protocol shapes are verified separately from gameplay semantics.
 */
public final class TpsWeaponSystem {
    public static final int TPS_WEAPON_ITEM_LIMIT = 20;

    private TpsWeaponSystem() {}

    public static boolean isTpsWeapon(GameItem item) {
        return item != null
                && item.getItemData() != null
                && item.getItemType() == ItemType.ITEM_TPS_WEAPON;
    }

    public static GameItem findOwnedWeapon(Player player, int itemId) {
        return player.getInventory().getItems().values().stream()
                .filter(item -> item.getItemId() == itemId && item.getOwnerId() == player.getUid())
                .filter(item -> item.getCount() > 0 && isTpsWeapon(item))
                .findFirst()
                .orElse(null);
    }

    public static List<GameItem> getWornWeapons(Avatar avatar) {
        if (avatar.getPlayer() == null) return List.of();
        return avatar.getTpsWeaponIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .map(id -> findOwnedWeapon(avatar.getPlayer(), id))
                .filter(Objects::nonNull)
                .toList();
    }

    public static List<Integer> getValidAccessories(GameItem item) {
        return item.getTpsAccessoryIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .filter(
                        id -> {
                            var data = GameData.getTpsWeaponAccessoryDataMap().get((int) id);
                            return data != null && data.getTpsWeaponId() == item.getItemId();
                        })
                .toList();
    }

    public static Int2IntMap getAffixLevels(GameItem item) {
        var result = new Int2IntLinkedOpenHashMap();
        var weapon = GameData.getTpsWeaponDataMap().get(item.getItemId());
        if (weapon != null) putAffix(result, weapon.getEquipAffixId(), weapon.getTpsWeaponBaseAffix());
        for (int id : getValidAccessories(item)) {
            var accessory = GameData.getTpsWeaponAccessoryDataMap().get(id);
            putAffix(result, accessory.getEquipAffixId(), accessory.getTpsWeaponBaseAffix());
        }
        return result;
    }

    private static void putAffix(Int2IntMap result, int encodedId, List<Integer> fallback) {
        if (encodedId > 0) result.put(encodedId / 10, encodedId % 10);
        else if (fallback != null)
            fallback.stream()
                    .filter(Objects::nonNull)
                    .filter(id -> id > 0)
                    .forEach(id -> result.put((int) id, 0));
    }

    public static _TpsWeapon toTpsWeaponProto(GameItem item) {
        return _TpsWeapon.newBuilder()
                .addAllAccessoryIdList(getValidAccessories(item))
                .putAllAffixMap(getAffixLevels(item))
                .build();
    }

    public static void applyAffixes(Avatar avatar) {
        for (var item : getWornWeapons(avatar)) {
            for (var entry : getAffixLevels(item).int2IntEntrySet()) {
                var data =
                        GameData.getEquipAffixDataMap().get(entry.getIntKey() * 10 + entry.getIntValue());
                if (data == null) continue;
                if (data.getAddProps() != null)
                    for (var prop : data.getAddProps()) {
                        if (prop != null && prop.getProp() != null)
                            avatar.addFightProperty(prop.getProp(), prop.getValue());
                    }
                avatar.addToExtraAbilityEmbryos(data.getOpenConfig(), true);
            }
        }
    }

    public static Avatar findAvatar(Player player, long guid) {
        var avatar = player.getAvatars().getAvatarByGuid(guid);
        if (avatar != null) return avatar;
        return player.getTeamManager().getAllTrialAvatars().stream()
                .filter(trial -> trial.getGuid() == guid)
                .findFirst()
                .orElse(null);
    }

    public static List<Avatar> getWearers(Player player) {
        Set<Avatar> result = Collections.newSetFromMap(new IdentityHashMap<>());
        player.getAvatars().forEach(result::add);
        result.addAll(player.getTeamManager().getAllTrialAvatars());
        return result.stream().filter(avatar -> !avatar.getTpsWeaponIds().isEmpty()).toList();
    }

    /** Validate the whole replacement before changing any avatar, including trial wearers. */
    public static int wear(Player player, long avatarGuid, List<Long> guids) {
        return wear(player, avatarGuid, guids, true);
    }

    public static int wear(Player player, long avatarGuid, List<Long> guids, boolean notify) {
        synchronized (player) {
            var avatar = findAvatar(player, avatarGuid);
            if (avatar == null) return Retcode.RET_CAN_NOT_FIND_AVATAR_VALUE;
            if (guids == null || guids.size() > 3) return Retcode.RET_EQUIP_EXCEED_LIMIT_VALUE;
            var ids = new ArrayList<Integer>();
            var slots = new Int2IntOpenHashMap();
            for (Long guid : guids) {
                var item = guid == null ? null : player.getInventory().getItemByGuid(guid);
                if (!isTpsWeapon(item) || item.getCount() <= 0 || item.getOwnerId() != player.getUid())
                    return Retcode.RET_ITEM_NOT_EXIST_VALUE;
                if (ids.contains(item.getItemId())) return Retcode.RET_FAIL_VALUE;
                var data = GameData.getTpsWeaponDataMap().get(item.getItemId());
                int slot = data == null ? 0 : data.getWearSlotType();
                int limit = slot == 1 ? 2 : slot == 2 ? 1 : 0;
                if (slots.addTo(slot, 1) + 1 > limit) return Retcode.RET_EQUIP_EXCEED_LIMIT_VALUE;
                ids.add(item.getItemId());
            }
            var changed = new ArrayList<Avatar>();
            for (var other : getWearers(player)) {
                if (other == avatar) continue;
                getWornWeapons(other).stream()
                        .filter(item -> ids.contains(item.getItemId()))
                        .forEach(TpsWeaponSystem::removeWeaponEntity);
                if (other.getTpsWeaponIds().removeIf(ids::contains)) changed.add(other);
            }
            if (!ids.equals(avatar.getTpsWeaponIds())) {
                getWornWeapons(avatar).forEach(TpsWeaponSystem::removeWeaponEntity);
                avatar.getTpsWeaponIds().clear();
                avatar.getTpsWeaponIds().addAll(ids);
                changed.add(avatar);
            }
            boolean loadoutChanged;
            if (TpsAvatarSystem.isTpsAvatar(avatar.getAvatarId())) {
                loadoutChanged = !player.isTpsLoadoutInitialized() || !player.getTpsLoadout().equals(ids);
                player.setTpsLoadoutInitialized(true);
                player.getTpsLoadout().clear();
                player.getTpsLoadout().addAll(ids);
            } else loadoutChanged = player.getTpsLoadout().removeIf(ids::contains);
            if (loadoutChanged) player.save();
            if (changed.isEmpty()) return Retcode.RET_SUCC_VALUE;
            if (!changed.contains(avatar)) changed.add(avatar);
            for (var changedAvatar : changed) {
                if (changedAvatar.getTrialAvatarId() == 0) changedAvatar.save();
                if (notify) {
                    changedAvatar.recalcStats();
                    sendEquipChange(changedAvatar);
                } else changedAvatar.recalcStats(false, false);
            }
            return Retcode.RET_SUCC_VALUE;
        }
    }

    public static void removeWeaponEntity(GameItem item) {
        var entity = item.getWeaponEntity();
        if (entity != null && entity.getScene() != null) {
            entity.invalidateAbilityLifecycle();
            entity.getScene().getWeaponEntities().remove(entity.getId(), entity);
            entity.onRemoved();
        }
        item.setWeaponEntity(null);
    }

    public static void ensureWeaponEntity(GameItem item, Scene scene) {
        ensureWeaponEntity(item, scene, null);
    }

    private static void ensureWeaponEntity(GameItem item, Scene scene, Avatar caster) {
        if (scene == null || scene.getWorld() == null) return;
        var old = item.getWeaponEntity();
        if (old != null
                && old.getScene() == scene
                && old.getCasterAvatar() == caster
                && scene.getWeaponEntities().get(old.getId()) == old) return;
        removeWeaponEntity(item);
        var entity = new EntityWeapon(scene, item.getItemData().getGadgetId(), caster);
        item.setWeaponEntity(entity);
        scene.getWeaponEntities().put(entity.getId(), entity);
    }

    public static void ensureWeaponEntities(Avatar avatar, Scene scene) {
        getWornWeapons(avatar).forEach(item -> ensureWeaponEntity(item, scene, avatar));
    }

    public static void releaseSceneWeapons(Player player, Scene scene) {
        player.getInventory().getItems().values().stream()
                .filter(TpsWeaponSystem::isTpsWeapon)
                .filter(
                        item -> item.getWeaponEntity() != null && item.getWeaponEntity().getScene() == scene)
                .forEach(TpsWeaponSystem::removeWeaponEntity);
    }

    public static void onWeaponRemoved(Player player, GameItem item) {
        synchronized (player) {
            removeWeaponEntity(item);
            boolean magazineChanged = false;
            var data = GameData.getTpsWeaponDataMap().get(item.getItemId());
            if (data != null && data.getAmmoSlotIds() != null)
                for (Integer slot : data.getAmmoSlotIds())
                    if (slot != null)
                        magazineChanged |= player.getTpsMagazineAmmunition().remove(slot) != null;
            for (var avatar : getWearers(player)) {
                if (!avatar.getTpsWeaponIds().removeIf(id -> Objects.equals(id, item.getItemId())))
                    continue;
                if (avatar.getTrialAvatarId() == 0) avatar.save();
                avatar.recalcStats();
                sendEquipChange(avatar);
            }
            if (player.getTpsLoadout().removeIf(id -> Objects.equals(id, item.getItemId()))
                    || magazineChanged)
                player.save();
        }
    }

    /** Reconcile an owned weapon after inventory registration, before its initial save. */
    public static boolean reconcileAccessories(Player player, GameItem item) {
        synchronized (player.getInventory()) {
            if (!isTpsWeapon(item)
                    || item.getCount() <= 0
                    || item.getOwnerId() != player.getUid()
                    || player.getInventory().getItemByGuid(item.getGuid()) != item) return false;
            var manual = new TreeSet<>(getValidAccessories(item));
            manual.removeAll(item.getTpsMaterialAccessoryIds());
            var automatic = new TreeSet<Integer>();
            for (var accessory : GameData.getTpsWeaponAccessoryDataMap().values()) {
                if (accessory == null
                        || accessory.getTpsWeaponId() != item.getItemId()
                        || accessory.getUnlockMaterialId() <= 0) continue;
                var token = player.getInventory().getItemById(accessory.getUnlockMaterialId());
                if (token != null
                        && token.getItemData() != null
                        && token.getItemType() == ItemType.ITEM_MATERIAL
                        && token.getCount() > 0
                        && token.getOwnerId() == player.getUid()
                        && player.getInventory().getItemByGuid(token.getGuid()) == token)
                    automatic.add(accessory.getId());
            }
            automatic.removeAll(manual);
            var effective = new TreeSet<>(manual);
            effective.addAll(automatic);
            var ids = new ArrayList<>(effective);
            var materialIds = new ArrayList<>(automatic);
            if (ids.equals(item.getTpsAccessoryIds())
                    && materialIds.equals(item.getTpsMaterialAccessoryIds())) return false;
            // Database snapshots use this monitor too; persist both lists from the same transition.
            synchronized (item) {
                item.setTpsAccessoryIds(ids);
                item.setTpsMaterialAccessoryIds(materialIds);
            }
            return true;
        }
    }

    /**
     * Called after all database items load, so database enumeration order cannot affect ownership.
     */
    public static void restoreAccessories(Player player) {
        synchronized (player.getInventory()) {
            for (var item : player.getInventory().getItems().values())
                if (reconcileAccessories(player, item)) item.save();
        }
    }

    /** Material grants may affect several weapons; reconcile each once after the whole award. */
    public static void onMaterialsChanged(
            Player player, Collection<Integer> materialIds, boolean notify) {
        synchronized (player.getInventory()) {
            var weaponIds = new TreeSet<Integer>();
            for (var data : GameData.getTpsWeaponAccessoryDataMap().values())
                if (data != null
                        && data.getUnlockMaterialId() > 0
                        && materialIds.contains(data.getUnlockMaterialId()))
                    weaponIds.add(data.getTpsWeaponId());
            for (int id : weaponIds) {
                var item = findOwnedWeapon(player, id);
                if (item == null || !reconcileAccessories(player, item)) continue;
                item.save();
                notifyAccessoryChange(player, item, notify && player.hasSentLoginPackets());
            }
        }
    }

    private static void notifyAccessoryChange(Player player, GameItem item, boolean notify) {
        if (notify)
            player.sendPacket(new emu.grasscutter.server.packet.send.PacketStoreItemChangeNotify(item));
        for (var avatar : getWearers(player))
            if (avatar.getTpsWeaponIds().contains(item.getItemId())) {
                if (notify) {
                    avatar.recalcStats();
                    sendEquipChange(avatar);
                } else avatar.recalcStats(false, false);
            }
    }

    /** Server/GM operation; client accessory payloads never grant ownership. */
    public static boolean unlockAccessory(Player player, int accessoryId) {
        synchronized (player.getInventory()) {
            var data = GameData.getTpsWeaponAccessoryDataMap().get(accessoryId);
            var item = data == null ? null : findOwnedWeapon(player, data.getTpsWeaponId());
            if (item == null || item.getTpsAccessoryIds().contains(accessoryId)) return false;
            var ids = new ArrayList<>(item.getTpsAccessoryIds());
            ids.add(accessoryId);
            item.setTpsAccessoryIds(ids);
            item.save();
            player.sendPacket(new emu.grasscutter.server.packet.send.PacketStoreItemChangeNotify(item));
            for (var avatar : getWearers(player))
                if (avatar.getTpsWeaponIds().contains(item.getItemId())) {
                    avatar.recalcStats();
                    sendEquipChange(avatar);
                }
            return true;
        }
    }

    public static TpsAmmunitionData getAmmunitionForSlot(int slot) {
        return GameData.getTpsAmmunitionDataMap().values().stream()
                .filter(Objects::nonNull)
                .filter(data -> data.getAmmoSlotIds() != null && data.getAmmoSlotIds().contains(slot))
                .min(Comparator.comparingInt(TpsAmmunitionData::getId))
                .orElse(null);
    }

    /**
     * Total remaining ammunition, including loaded magazines. The legacy method name is retained;
     * the client derives the reserve by subtracting its magazine counts from this total.
     */
    public static int getReserve(Player player, int id) {
        var data = GameData.getTpsAmmunitionDataMap().get(id);
        if (data == null) return 0;
        int limit = ammunitionLimit(player, data);
        Integer stored = player.getTpsAmmunition().getOrDefault(id, limit);
        return Math.max(0, Math.min(stored == null ? 0 : stored, limit));
    }

    /** Native scene 7001 uses the TPS limit; other scenes use the separate normal limit. */
    private static int ammunitionLimit(Player player, TpsAmmunitionData data) {
        var scene = player.getScene();
        int limit = scene != null && scene.getId() == 7001 || data.getNormalAmmoLimit() == null
                ? data.getTpsAmmoLimit() : data.getNormalAmmoLimit();
        return Math.max(0, limit);
    }

    /** Server-authored supplies and costs persist the resulting total within resource limits. */
    public static int changeReserve(Player player, int id, int delta) {
        synchronized (player) {
            var data = GameData.getTpsAmmunitionDataMap().get(id);
            if (data == null) return 0;
            int before = getReserve(player, id);
            int after =
                    (int) Math.max(0L, Math.min((long) before + delta, ammunitionLimit(player, data)));
            if (before != after) {
                player.getTpsAmmunition().put(id, after);
                player.save();
            }
            return after;
        }
    }

    public static boolean hasAmmunitionSpace(EntityAvatar recipient, int id) {
        var player = recipient == null ? null : recipient.getPlayer();
        if (player == null) return false;
        synchronized (player) {
            var data = GameData.getTpsAmmunitionDataMap().get(id);
            return isSupplyRecipient(player, recipient) && data != null
                    && getReserve(player, id) < ammunitionLimit(player, data);
        }
    }

    public static boolean isSupplyRecipient(EntityAvatar recipient) {
        var player=recipient==null?null:recipient.getPlayer();
        if(player==null) return false;
        synchronized(player) { return isSupplyRecipient(player,recipient); }
    }

    private static boolean isSupplyRecipient(Player player, EntityAvatar recipient) {
        var scene = player.getScene();
        return scene != null && recipient.getScene() == scene && !recipient.isDead()
                && currentAvatar(player) == recipient
                && scene.getEntities().get(recipient.getId()) == recipient
                && !getWornWeapons(recipient.getAvatar()).isEmpty();
    }

    /** Resource actions award totals; the client's positive pickup requests cannot award ammo. */
    public static boolean supplyAmmunition(EntityAvatar recipient, int id, int amount) {
        var player = recipient == null ? null : recipient.getPlayer();
        if (player == null || amount < 0) return false;
        synchronized (player) {
            if (!isSupplyRecipient(player, recipient)
                    || !GameData.getTpsAmmunitionDataMap().containsKey(id)) return false;
            int before = getReserve(player, id);
            int gained = changeReserve(player, id, amount) - before;
            if (gained > 0) {
                var packet = new BasePacket(PacketOpcodes.TpsAmmunitionChangeNotify);
                packet.setData(
                        TpsAmmunitionChangeNotify.newBuilder()
                                .addAmmunitionList(
                                        TpsAmmunitionChangeNotifyEntry.newBuilder()
                                                .setAmmunitionConfigId(id)
                                                .setChangeCount(gained)));
                player.sendPacket(packet);
            }
            return true;
        }
    }

    private static IntSet ammunitionIds(Avatar avatar) {
        var result = new IntOpenHashSet();
        for (var item : getWornWeapons(avatar)) {
            var data = GameData.getTpsWeaponDataMap().get(item.getItemId());
            if (data == null || data.getAmmoSlotIds() == null) continue;
            for (Integer slot : data.getAmmoSlotIds()) {
                if (slot == null) continue;
                var ammo = getAmmunitionForSlot(slot);
                if (ammo != null) result.add(ammo.getId());
            }
        }
        return result;
    }

    public static SceneWeaponInfo toSceneWeaponInfo(Player player, GameItem item) {
        var magazines = TpsMagazineSystem.visible(player, item);
        var builder =
                SceneWeaponInfo.newBuilder()
                        .setEntityId(item.getWeaponEntity() == null ? 0 : item.getWeaponEntity().getId())
                        .setGadgetId(item.getItemData().getGadgetId())
                        .setItemId(item.getItemId())
                        .setGuid(item.getGuid())
                        .setLevel(item.getLevel())
                        .putAllAffixMap(getAffixLevels(item))
                        .setAbilityInfo(AbilitySyncStateInfo.newBuilder().setIsInited(true));
        var data = GameData.getTpsWeaponDataMap().get(item.getItemId());
        if (data != null && data.getAmmoSlotIds() != null)
            for (Integer slot : data.getAmmoSlotIds()) {
                if (slot == null) continue;
                var ammo = getAmmunitionForSlot(slot);
                if (ammo == null || !magazines.containsKey((int) slot)) continue;
                // Omit unknown slots: the native combat config initializes those magazines.
                builder.addAmmunitionList(
                        TpsWeaponAmmunitionInfo.newBuilder()
                                .setAmmunitionType(slot)
                                .setAmmunitionConfigId(ammo.getId())
                                .setCurrentAmmunition(magazines.get((int) slot)));
            }
        return builder.build();
    }

    public static List<SceneWeaponInfo> getSceneWeaponInfos(Avatar avatar) {
        if (avatar.getPlayer() == null) return List.of();
        return getWornWeapons(avatar).stream()
                .map(item -> toSceneWeaponInfo(avatar.getPlayer(), item))
                .toList();
    }

    public static void sendEquipChange(Avatar avatar) {
        var player = avatar.getPlayer();
        if (player == null || !player.hasSentLoginPackets()) return;
        var entity = avatar.getAsEntity();
        var scene = entity == null ? null : entity.getScene();
        for (var item : getWornWeapons(avatar)) {
            removeWeaponEntity(item);
            ensureWeaponEntity(item, scene, avatar);
        }
        var packet = new BasePacket(PacketOpcodes.TpsEquipChangeNotify);
        packet.setData(
                TpsEquipChangeNotify.newBuilder()
                        .setAvatarGuid(avatar.getGuid())
                        .addAllTpsWeaponList(getSceneWeaponInfos(avatar)));
        if (scene == null) player.sendPacket(packet);
        else {
            sendSceneAmmunition(player);
            scene.broadcastPacket(packet);
        }
        sendWeaponAbilityBlocks(avatar);
    }

    public static void sendWeaponAbilityBlocks(Avatar avatar) {
        var player = avatar.getPlayer();
        if (player == null || !player.hasSentLoginPackets()) return;
        for (var item : getWornWeapons(avatar)) {
            var entity = item.getWeaponEntity();
            if (entity == null) continue;
            var block = entity.getAbilityControlBlock();
            if (block.getAbilityEmbryoListCount() > 0)
                player.sendPacket(new PacketAbilityChangeNotify(entity.getId(), block));
        }
    }

    public static void sendSceneAmmunition(Player player) {
        synchronized (player) {
            boolean hasWeapons = player.getTeamManager().getActiveTeamSnapshot().stream()
                    .anyMatch(entity -> entity != null && !ammunitionIds(entity.getAvatar()).isEmpty());
            if (!hasWeapons) return;
            // 6579 replaces the whole total map. 24371 is a delta, not a snapshot.
            var builder = TpsRegionalPlaySupplyInfoNotify.newBuilder();
            GameData.getTpsAmmunitionDataMap().values().stream()
                    .filter(Objects::nonNull)
                    .mapToInt(TpsAmmunitionData::getId)
                    .filter(id -> id > 0)
                    .distinct()
                    .sorted()
                    .forEach(
                            id ->
                                    builder.addAmmunitionList(
                                            TpsAmmunitionSupplyInfo.newBuilder()
                                                    .setAmmunitionConfigId(id)
                                                    .setTotalAmmunition(getReserve(player, id))));
            var packet = new BasePacket(PacketOpcodes.TpsRegionalPlaySupplyInfoNotify);
            packet.setData(builder);
            player.sendPacket(packet);
        }
    }

    public static void refillAmmunition(Player player) {
        synchronized (player) {
            for (var data : GameData.getTpsAmmunitionDataMap().values())
                player.getTpsAmmunition().put(data.getId(), ammunitionLimit(player, data));
            player.save();
            sendSceneAmmunition(player);
        }
    }

    /**
     * Costs from the owner's current avatar/weapon may change totals; reloads only update
     * magazines. Grants are server-authored.
     */
    public static boolean onAmmunitionInvoke(Player player, AbilityInvokeEntry invoke)
            throws InvalidProtocolBufferException {
        if (player == null || invoke == null) return false;
        synchronized (player) {
            var scene = player.getScene();
            var current = currentAvatar(player);
            if (scene == null
                    || current == null
                    || current.getScene() != scene
                    || current.getAvatar().getPlayer() != player
                    || scene.getEntities().get(current.getId()) != current) return false;
            var avatar = current.getAvatar();
            boolean ownedEntity =
                    invoke.getEntityId() == current.getId()
                            || getWornWeapons(avatar).stream()
                                    .anyMatch(
                                                    item -> isCurrentWeaponEntity(
                                                            item, avatar, scene, invoke.getEntityId()));
            if (!ownedEntity) return false;
            var update = AbilityMetaUpdateTpsWeaponAmmunition.parseFrom(invoke.getAbilityData());
            if (update.getUpdateTypeValue() > 1 || update.getUpdateTypeValue() < 0) return false;
            var allowed = ammunitionIds(avatar);
            var changes = new Int2LongOpenHashMap();
            for (var entry : update.getAmmunitionListList()) {
                if (!allowed.contains(entry.getAmmunitionConfigId()) || entry.getChangeCount() > 0
                        || (update.getUpdateTypeValue() == 1 && entry.getChangeCount() != 0))
                    return false;
                changes.addTo(entry.getAmmunitionConfigId(), entry.getChangeCount());
            }
            for (var entry : changes.int2LongEntrySet())
                if (getReserve(player, entry.getIntKey()) + entry.getLongValue() < 0) return false;
            var magazines = TpsMagazineSystem.validate(player, avatar, scene, update, changes);
            if (magazines == null) return false;
            boolean changed = false;
            for (var entry : changes.int2LongEntrySet())
                if (entry.getLongValue() != 0) {
                    player
                            .getTpsAmmunition()
                            .put(
                                    entry.getIntKey(),
                                    (int) (getReserve(player, entry.getIntKey()) + entry.getLongValue()));
                    changed = true;
                }
            for (var entry : magazines.int2IntEntrySet()) {
                var before = player.getTpsMagazineAmmunition().put(entry.getIntKey(), entry.getIntValue());
                changed |= !Objects.equals(before, entry.getIntValue());
            }
            if (changed) player.save();
            return true;
        }
    }

    public static EntityAvatar currentAvatar(Player player) {
        var team = player.getTeamManager().getActiveTeam();
        int index = player.getTeamManager().getCurrentCharacterIndex();
        return index >= 0 && index < team.size() ? team.get(index) : null;
    }

    static boolean isCurrentWeaponEntity(GameItem item, Avatar avatar, Scene scene, int entityId) {
        var entity = item.getWeaponEntity();
        return scene != null && entity != null && entity.getId() == entityId && entity.getScene() == scene
                && entity.getCasterAvatar() == avatar
                && scene.getWeaponEntities().get(entityId) == entity;
    }
}
