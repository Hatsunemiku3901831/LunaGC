package emu.grasscutter.game.ability.mixins;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityMixinData;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.GameEntity;

@AbilityMixin(AbilityMixinData.Type.AttachModifierToGlobalValueMixin)
public final class AttachModifierToGlobalValueMixin extends AbilityMixinHandler {
    @Override public boolean execute(Ability ability, AbilityMixinData mixin,
                                     ByteString payload, GameEntity target) {
        if (ability == null || target == null) return false;
        target.getAbilityGlobalValueWatchers().refresh();
        return true;
    }
}
