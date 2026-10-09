package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.AbilityGlobalValues;
import emu.grasscutter.game.ability.AbilityOverrideMap;
import emu.grasscutter.game.entity.*;

@AbilityAction(AbilityModifierAction.Type.SetGlobalValueToOverrideMap)
public final class ActionSetGlobalValueToOverrideMap extends AbilityActionHandler {
    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
        if (ability == null || target == null || action == null) return false;
        if (!conditionsPass(ability, action, target)) return true;
        if (action.globalValueKey == null || action.globalValueKey.isBlank()
                || action.overrideMapKey == null || action.overrideMapKey.isBlank()) return false;
        GameEntity entity = target;
        if (action.isFromOwner) {
            if (target instanceof EntityClientGadget) {
                EntityClientGadget gadget = (EntityClientGadget)target;
                entity = entity.getScene() == null ? null
                        : entity.getScene().getEntityById(gadget.getOwnerEntityId());
            } else if (target instanceof EntityGadget) {
                EntityGadget gadget = (EntityGadget)target;
                entity = gadget.getOwner();
            }
        }
        // isFromOwner can resolve to nothing - a summon whose owner has already left the scene -
        // and the whole action then threw on the first read below.
        if (entity == null) return true;
        if (!AbilityGlobalValues.isCurrent(ability,entity)) return false;

        String globalValueKey = action.globalValueKey;
        String abilityFormula = action.abilityFormula;
        Float globalValue = entity.getGlobalAbilityValues().get(globalValueKey);
        if (globalValue == null) {
            Grasscutter.getLogger().trace("Action does not contains {} global key", (Object)globalValueKey);
            return true;
        }

        // Most actions name no formula at all. Reading it as one threw before the override map was
        // ever written, so every ability that came through here - Xilonen's and Citlali's bullets,
        // Mavuika's motorcycle - lost the value it was setting, not just the formula.
        if ("DummyThrowSpeed".equals(abilityFormula)) {
            globalValue = Float.valueOf(globalValue.floatValue() * 30.0f / ((float)Math.sin(0.9424778) * 100.0f) - 1.0f);
        }
        if (!AbilityOverrideMap.write(ability,action,target,globalValue)) return false;
        // Preserve the legacy formula path; ordinary copies never rewrite the source global.
        return !"DummyThrowSpeed".equals(abilityFormula)
                || AbilityGlobalValues.copy(ability,entity,globalValueKey,globalValue);
    }
}
