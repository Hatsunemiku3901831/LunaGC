package emu.grasscutter.game.ability.mixins;

import com.google.protobuf.ByteString;

import emu.grasscutter.data.binout.AbilityMixinData;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.GameEntity;

@AbilityMixin(AbilityMixinData.Type.AvatarCombatMixin)
public final class AvatarCombatMixin extends AbilityMixinHandler {
    @Override
    public boolean execute(
            Ability ability, AbilityMixinData mixin, ByteString payload, GameEntity target) {
        return ability != null
                && ability.getManager() != null
                && ability.getManager().getCombatCallbacks().apply(ability, mixin, payload, target);
    }
}
