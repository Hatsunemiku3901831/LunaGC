package emu.grasscutter.game.ability.mixins;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityMixinData;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.GameEntity;

@AbilityMixin(AbilityMixinData.Type.AttachModifierToSelfGlobalValueMixin)
public class AttachModifierToSelfGlobalValueMixin extends AbilityMixinHandler {
    @Override public boolean execute(Ability ability, AbilityMixinData mixin,
                                     ByteString payload, GameEntity target) {
        if (ability == null) return false;
        var bearer = target == null ? ability.getOwner() : target;
        if (bearer == null) return false;
        bearer.getAbilityGlobalValueWatchers().refresh();
        return true;
    }
}
