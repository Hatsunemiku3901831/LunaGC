package emu.grasscutter.game.ability;

import com.google.protobuf.ByteString;

import emu.grasscutter.data.binout.*;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.entity.GameEntity;

import java.util.HashMap;

/** Child actions selected by the client's shield-break event; message 107 only reports state. */
public final class ShieldBarCallbacks {
    private ShieldBarCallbacks() {}

    public record Callback(
            AbilityMixinData mixin, AbilityModifier modifier, AbilityModifierAction action) {}

    public static void index(
            AbilityData data,
            AbilityMixinData[] mixins,
            AbilityModifier modifier,
            int modifierIndex) {
        if (mixins == null) return;
        for (int index = 0; index < mixins.length; index++) {
            var mixin = mixins[index];
            if (mixin == null || mixin.type != AbilityMixinData.Type.ShieldBarMixin) continue;
            // Current config type 36729 has one action-array category, at offset 0x38.
            var generator =
                    new AbilityLocalIdGenerator(
                            modifier == null
                                    ? AbilityLocalIdGenerator.ConfigAbilitySubContainerType.MIXIN
                                    : AbilityLocalIdGenerator.ConfigAbilitySubContainerType
                                            .MODIFIER_MIXIN);
            generator.modifierIndex = modifierIndex;
            generator.mixinIndex = index;
            var actions = new HashMap<Integer, AbilityModifierAction>();
            generator.initializeActionLocalIds(mixin.onShieldBroken, actions);
            actions.forEach(
                    (id, action) -> {
                        data.localIdToAction.put(id, action);
                        data.shieldBarCallbacks.put(id, new Callback(mixin, modifier, action));
                    });
        }
    }

    static void invoke(
            Ability ability,
            Callback callback,
            GameEntity bearer,
            GameEntity target,
            AbilityModifierController controller,
            int modifierId,
            int targetId,
            int argument,
            ByteString payload) {
        var scene = bearer.getScene();
        if (scene == null || argument < 0 || argument >= 100) return;
        synchronized (scene) {
            if (!callback.mixin.enable
                    || scene.getEntities().get(bearer.getId()) != bearer
                    || !AbilityExecutionContext.canTrigger(bearer, ability.getPlayerOwner())
                    || target == null
                    || targetId != 0 && target.getId() != targetId
                    || target.getScene() != scene) return;
            if (callback.modifier == null) {
                if (modifierId != 0
                        || controller != null
                        || bearer != ability.getOwner()
                        || !bearer.getInstancedAbilities().contains(ability)) return;
            } else if (modifierId == 0
                    || controller == null
                    || controller.getAbility() != ability
                    || controller.getModifierData() != callback.modifier
                    || bearer.getInstancedModifiers().get(modifierId) != controller) return;
            var context = new AbilityExecutionContext(ability, target, false, true);
            AbilityCooldownInvocation.run(
                    new AbilityCooldownInvocation.Scope(controller, false),
                    () ->
                            context.runIfCurrent(
                                    () ->
                                            ability.getManager()
                                                    .executeActionNow(
                                                            ability,
                                                            callback.action,
                                                            payload,
                                                            target)));
        }
    }
}
