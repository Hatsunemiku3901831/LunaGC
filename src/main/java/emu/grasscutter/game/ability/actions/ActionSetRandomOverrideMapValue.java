package emu.grasscutter.game.ability.actions;

import com.google.protobuf.*;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.AbilityOverrideMap;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.net.proto.AbilityActionSetRandomOverrideMapValueOuterClass.AbilityActionSetRandomOverrideMapValue;
import java.util.OptionalDouble;

@AbilityAction(AbilityModifierAction.Type.SetRandomOverrideMapValue)
public class ActionSetRandomOverrideMapValue extends AbilityActionHandler {
    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
        AbilityActionSetRandomOverrideMapValue valueProto;
        if (ability == null || action == null || abilityData == null) return false;
        if (target == null) target = ability.getOwner();
        if (!conditionsPass(ability, action, target)) return true;
        try {
            valueProto = AbilityActionSetRandomOverrideMapValue.parseFrom(abilityData);
        } catch (InvalidProtocolBufferException e) {
            return false;
        }

        float value = valueProto.getRandomValue();
        var properties = propertiesFor(ability);
        var lower = action.valueRangeMin == null ? OptionalDouble.of(0) : action.valueRangeMin.tryGet(properties);
        var upper = action.valueRangeMax == null ? OptionalDouble.of(0) : action.valueRangeMax.tryGet(properties);
        if (lower.isEmpty() || upper.isEmpty()) return false;
        float valueRangeMin = (float)lower.getAsDouble();
        float valueRangeMax = (float)upper.getAsDouble();

        if (!Float.isFinite(value) || !Float.isFinite(valueRangeMin) || !Float.isFinite(valueRangeMax)
                || valueRangeMin > valueRangeMax || value < valueRangeMin || value > valueRangeMax) return false;
        return AbilityOverrideMap.write(ability, action, target, value);
    }
}
