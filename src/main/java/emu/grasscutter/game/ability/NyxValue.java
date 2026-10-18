package emu.grasscutter.game.ability;

import emu.grasscutter.data.common.DynamicFloat;
import emu.grasscutter.game.ability.actions.AbilityActionHandler;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.server.packet.send.PacketServerGlobalValueChangeNotify;

/** Nightsoul values are ability globals; their limits come from the resource, not an avatar ID. */
public final class NyxValue {
    private NyxValue() {}

    public static boolean change(Ability ability, GameEntity target, DynamicFloat value,
                                 DynamicFloat min, DynamicFloat max, boolean replace) {
        if (target == null || value == null) return false;
        var globals = target.getGlobalAbilityValues();
        var properties = AbilityActionHandler.propertiesFor(ability);
        if (target != ability.getOwner()) globals.forEach(properties::put);
        float delta = value.get(properties, 0f);
        float lower = min == null || min == DynamicFloat.ZERO
                ? properties.getOrDefault("NyxValueMin", 0f) : min.get(properties, 0f);
        float upper = max == null || max == DynamicFloat.ZERO
                ? properties.getOrDefault("NyxValueMax", Float.MAX_VALUE) : max.get(properties, 0f);
        if (!Float.isFinite(delta) || !Float.isFinite(lower) || !Float.isFinite(upper) || upper < lower) return false;
        if (!replace && delta == 0f) return true;
        // Ordinary global actions update this same key with compute. A separate monitor around
        // get/put does not serialize with those writes and can discard an accepted gain or cost.
        float updated = globals.compute("NyxValue", (key, previous) -> Math.max(lower,
                Math.min(upper, (replace || previous == null ? 0f : previous) + delta)));
        // Reconciliation may acquire player, scene and modifier locks; keep it outside the update.
        target.onAbilityValueUpdate();
        if (!AbilityManager.isServerOwnedChain() && target.getScene() != null) {
            target.getScene().broadcastPacket(new PacketServerGlobalValueChangeNotify(target, "NyxValue", updated));
        }
        return true;
    }
}
