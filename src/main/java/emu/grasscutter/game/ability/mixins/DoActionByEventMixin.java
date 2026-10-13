package emu.grasscutter.game.ability.mixins;

import com.google.protobuf.ByteString;

import emu.grasscutter.data.binout.AbilityMixinData;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.GameEntity;

/** Executes a client-selected local callback; event detection remains with its source. */
@AbilityMixin(AbilityMixinData.Type.DoActionByEventMixin)
public final class DoActionByEventMixin extends DoActionByElementReactionMixin {
    @Override
    public boolean execute(
            Ability ability, AbilityMixinData mixin, ByteString payload, GameEntity target) {
        if (target != null && target.getAbilityHealthEvents().owns(ability, mixin)) return true;
        return super.execute(ability, mixin, payload, target);
    }
}
