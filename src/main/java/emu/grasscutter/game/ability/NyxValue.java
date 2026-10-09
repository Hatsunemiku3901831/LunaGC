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
        synchronized (globals) {
            var properties = AbilityActionHandler.propertiesFor(ability);
            if (target != ability.getOwner()) globals.forEach(properties::put);
            float delta = value.get(properties, 0f);
            float lower = min == null || min == DynamicFloat.ZERO
                    ? properties.getOrDefault("NyxValueMin", 0f) : min.get(properties, 0f);
            float upper = max == null || max == DynamicFloat.ZERO
                    ? properties.getOrDefault("NyxValueMax", Float.MAX_VALUE) : max.get(properties, 0f);
            if (!Float.isFinite(delta) || !Float.isFinite(lower) || !Float.isFinite(upper) || upper < lower) return false;
            if (!replace && delta == 0f) return true;
            float updated = Math.max(lower, Math.min(upper,
                    (replace ? 0f : globals.getOrDefault("NyxValue", 0f)) + delta));
            globals.put("NyxValue", updated);
            target.onAbilityValueUpdate();
            if (!AbilityManager.isServerOwnedChain() && target.getScene() != null) {
                target.getScene().broadcastPacket(new PacketServerGlobalValueChangeNotify(target, "NyxValue", updated));
            }
        }
        return true;
    }
}
