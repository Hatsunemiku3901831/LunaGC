package emu.grasscutter.game.tps;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.net.proto.AbilityMetaUpdateTpsWeaponAmmunitionOuterClass.AbilityMetaUpdateTpsWeaponAmmunition;

import it.unimi.dsi.fastutil.ints.*;

import java.util.*;

/** Observed native magazine snapshots. Unknown slots retain the client's resource defaults. */
final class TpsMagazineSystem {
    private TpsMagazineSystem() {}

    /** Build a read-only view within the total shared by all weapons in the active party. */
    static Int2IntMap visible(Player player, GameItem additional) {
        synchronized (player.getInventory()) {
            var weapons = new LinkedHashSet<GameItem>();
            for (var entity : player.getTeamManager().getActiveTeamSnapshot())
                if (entity != null)
                    weapons.addAll(TpsWeaponSystem.getWornWeapons(entity.getAvatar()));
            if (additional != null) weapons.add(additional);
            var slots = new TreeSet<Integer>();
            for (var weapon : weapons) {
                var data = GameData.getTpsWeaponDataMap().get(weapon.getItemId());
                if (data != null && data.getAmmoSlotIds() != null)
                    for (Integer slot : data.getAmmoSlotIds())
                        if (slot != null && slot > 0) slots.add(slot);
            }
            var result = new Int2IntLinkedOpenHashMap();
            var available = new Int2IntOpenHashMap();
            for (int slot : slots) {
                var ammunition = TpsWeaponSystem.getAmmunitionForSlot(slot);
                var stored = player.getTpsMagazineAmmunition().get(slot);
                if (ammunition == null || stored == null) continue;
                int id = ammunition.getId();
                if (!available.containsKey(id))
                    available.put(id, TpsWeaponSystem.getReserve(player, id));
                int count = Math.min(Math.max(0, stored), available.get(id));
                result.put(slot, count);
                available.addTo(id, -count);
            }
            return result;
        }
    }

    /** Called under the inventory transaction; null rejects the whole update before any state changes. */
    static Int2IntMap validate(
            Player player,
            Avatar avatar,
            Scene scene,
            AbilityMetaUpdateTpsWeaponAmmunition update,
            Int2LongMap totalChanges) {
        var result = new Int2IntLinkedOpenHashMap();
        var next = visible(player, null);
        for (var snapshot : update.getAccessoryListList()) {
            var weapon =
                    TpsWeaponSystem.getWornWeapons(avatar).stream()
                            .filter(
                                    item ->
                                            TpsWeaponSystem.isCurrentWeaponEntity(
                                                    item, avatar, scene, snapshot.getEntityId()))
                            .findFirst()
                            .orElse(null);
            if (weapon == null) return null;
            int slot = snapshot.getAmmunitionType();
            var data = GameData.getTpsWeaponDataMap().get(weapon.getItemId());
            var ammunition = TpsWeaponSystem.getAmmunitionForSlot(slot);
            if (data == null
                    || data.getAmmoSlotIds() == null
                    || !data.getAmmoSlotIds().contains(slot)
                    || ammunition == null
                    || ammunition.getId() != snapshot.getAmmunitionConfigId()
                    || result.containsKey(slot)) return null;
            long count = Integer.toUnsignedLong(snapshot.getCurrentAmmunition());
            long total =
                    (long) TpsWeaponSystem.getReserve(player, ammunition.getId())
                            + totalChanges.get(ammunition.getId());
            if (count > total
                    || (update.getUpdateTypeValue() == 0
                            && next.containsKey(slot)
                            && count > next.get(slot))) return null;
            result.put(slot, (int) count);
            next.put(slot, (int) count);
        }
        var loaded = new Int2LongOpenHashMap();
        for (var entry : next.int2IntEntrySet()) {
            var ammunition = TpsWeaponSystem.getAmmunitionForSlot(entry.getIntKey());
            if (ammunition == null) continue;
            int id = ammunition.getId();
            loaded.addTo(id, entry.getIntValue());
            if (loaded.get(id)
                    > (long) TpsWeaponSystem.getReserve(player, id) + totalChanges.get(id))
                return null;
        }
        return result;
    }
}
