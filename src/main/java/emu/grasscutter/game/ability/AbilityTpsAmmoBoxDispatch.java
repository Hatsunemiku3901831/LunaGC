package emu.grasscutter.game.ability;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.actions.AbilityActionHandler;
import emu.grasscutter.game.ability.actions.ActionTpsSupplyAmmo;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.tps.TpsWeaponSystem;

import java.util.*;

/** Native ammo boxes consume explicit units first, then rotate through equipped main weapons. */
public final class AbilityTpsAmmoBoxDispatch {
    private static final Set<String> TYPES =
            Set.of("ASSAULT_RIFLE", "GRENADE", "HOWITZER", "SHOTGUN", "SNIPER");
    private static final Set<String> TARGETS =
            Set.of(
                    "Self",
                    "Target",
                    "CurLocalAvatar",
                    "Owner",
                    "Caster",
                    "Applier",
                    "OriginOwner",
                    "CasterOriginOwner");
    private static final AbilityModifierAction[] EMPTY = new AbilityModifierAction[0];

    private AbilityTpsAmmoBoxDispatch() {}

    /** A removal award is driven by removal, never by an independently replayed leaf message. */
    public static boolean isRemovalCallback(AbilityData data, AbilityModifierAction action) {
        return data.modifiers != null
                && data.modifiers.values().stream()
                        .anyMatch(
                                modifier ->
                                        modifier.onRemoved != null
                                                && Arrays.stream(modifier.onRemoved)
                                                        .anyMatch(a -> a == action));
    }

    /** Only the current resource's ordered explicit-pool supply groups are paired. */
    public static boolean supported(AbilityModifierAction action) {
        if (action == null
                || action.type != AbilityModifierAction.Type.TPSAmmoBoxDispatch
                || !action.hasSupportedOffStageConfig()
                || action.otherTargets != null
                || action.target != null && !TARGETS.contains(action.target)
                || action.tpsAmmoBoxTotal == null
                || action.tpsAmmoBoxGroups == null
                || action.tpsAmmoBoxGroups.length == 0
                || action.tpsAmmoBoxGroups.length > 64) return false;
        int leaves = 0;
        for (var group : action.tpsAmmoBoxGroups) {
            if (group == null
                    || group.key == null
                    || group.key.isBlank()
                    || group.weaponType == null
                    || !TYPES.contains(group.weaponType)) return false;
            if (group.actionList == null) continue;
            leaves += group.actionList.length;
            if (leaves > 256) return false;
            for (var leaf : group.actionList)
                if (!ActionTpsSupplyAmmo.supportedConfiguration(leaf)
                        || leaf.otherTargets != null
                        || leaf.predicates != null && !leaf.predicates.isEmpty()
                        || leaf.targetPredicates != null && !leaf.targetPredicates.isEmpty())
                    return false;
        }
        return true;
    }

    private static OptionalInt integer(float value) {
        return Float.isFinite(value)
                        && (double) value >= -2147483648d
                        && (double) value < 2147483648d
                ? OptionalInt.of((int) value)
                : OptionalInt.empty();
    }

    private static OptionalInt weight(Ability ability, String key) {
        Float value = key == null ? null : ability.getOwner().getGlobalAbilityValues().get(key);
        return integer(value == null ? 0f : value);
    }

    public static Optional<List<AbilityModifierAction[]>> choose(
            Ability ability, AbilityModifierAction action, EntityAvatar recipient) {
        if (!supported(action)
                || !AbilityGlobalValues.isCurrent(ability, recipient)
                || !ActionTpsSupplyAmmo.isCurrentSource(ability, recipient))
            return Optional.empty();
        var raw = action.tpsAmmoBoxTotal.tryGet(AbilityActionHandler.propertiesFor(ability));
        if (raw.isEmpty()) return Optional.empty();
        var total = integer((float) raw.getAsDouble());
        if (total.isEmpty() || total.getAsInt() < 1 || total.getAsInt() > 100)
            return Optional.empty();
        var groups = action.tpsAmmoBoxGroups;
        int[] remaining = new int[groups.length];
        for (int i = 0; i < groups.length; i++) {
            var count = weight(ability, groups[i].key);
            if (count.isEmpty()) return Optional.empty();
            remaining[i] = count.getAsInt();
        }
        var result = new ArrayList<AbilityModifierAction[]>();
        boolean progress = true;
        while (result.size() < total.getAsInt() && progress) {
            progress = false;
            for (int i = 0; i < groups.length && result.size() < total.getAsInt(); i++)
                if (remaining[i] > 0) {
                    remaining[i]--;
                    progress = true;
                    result.add(groups[i].actionList == null ? EMPTY : groups[i].actionList);
                }
        }
        if (result.size() == total.getAsInt()) return Optional.of(result);
        var adaptive = weight(ability, action.tpsAmmoBoxAdaptiveKey);
        if (adaptive.isEmpty()) return Optional.empty();
        int count = Math.min(Math.max(0, adaptive.getAsInt()), total.getAsInt() - result.size());
        if (count == 0) return Optional.of(result);
        var equipped = new ArrayList<AbilityModifierAction[]>();
        for (var item : TpsWeaponSystem.getWornWeapons(recipient.getAvatar())) {
            var weapon = GameData.getTpsWeaponDataMap().get(item.getItemId());
            if (weapon == null
                    || weapon.getWearSlotType() != 1
                    || weapon.getTpsWeaponType() == null) continue;
            for (var group : groups)
                if (weapon.getTpsWeaponType().equals("TPS_WEAPON_TYPE_" + group.weaponType)
                        && group.actionList != null
                        && group.actionList.length > 0) {
                    // Preserve equal categories and the equipment order; grenades are not main
                    // guns.
                    equipped.add(group.actionList);
                    break;
                }
        }
        if (!equipped.isEmpty())
            for (int i = 0; i < count; i++) result.add(equipped.get(i % equipped.size()));
        return Optional.of(result);
    }

    /** Preflight the complete selected plan, including repeated occurrences, before any write. */
    public static Optional<List<ActionTpsSupplyAmmo.Supply>> prepare(
            Ability ability, AbilityModifierAction action, EntityAvatar recipient) {
        var chosen = choose(ability, action, recipient);
        if (chosen.isEmpty()) return Optional.empty();
        var result = new ArrayList<ActionTpsSupplyAmmo.Supply>();
        for (var group : chosen.get())
            for (var leaf : group) {
                if (result.size() >= 256) return Optional.empty();
                var target = AbilityActionHandler.resolveTarget(ability, recipient, leaf.target);
                var supply = ActionTpsSupplyAmmo.prepareResolved(ability, leaf, target);
                if (supply.isEmpty()) return Optional.empty();
                result.add(supply.get());
            }
        return Optional.of(result);
    }
}
