package emu.grasscutter.game.ability.mixins;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityMixinData;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.GameEntity;

/** Executes the callback selected by the client's local mixin ID; does not synthesize reactions. */
@AbilityMixin(AbilityMixinData.Type.DoActionByElementReactionMixin)
public class DoActionByElementReactionMixin extends AbilityMixinHandler {
    @Override
    public boolean execute(
            Ability ability, AbilityMixinData mixin, ByteString payload, GameEntity target) {
        return ability != null
                && ability.getManager() != null
                && ability.getManager().executeMixinActionsNow(ability, mixin, payload, target);
    }
}
