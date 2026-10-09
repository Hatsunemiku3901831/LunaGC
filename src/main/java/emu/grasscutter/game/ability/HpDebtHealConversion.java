package emu.grasscutter.game.ability;

import emu.grasscutter.data.binout.AbilityMixinData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.actions.AbilityActionHandler;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;
import java.util.*;

/** Healing conversion belongs to an installed modifier, including its predicates and lifetime. */
public final class HpDebtHealConversion {
    private HpDebtHealConversion() {}

    /** True when this heal was consumed by a conversion or healing restriction. */
    public static boolean consume(
            Ability incoming, AbilityModifierAction event, GameEntity target, float amount) {
        synchronized (target) {
            boolean configured = false;
            for (var controller : modifiers(target)) {
                var data = controller.getModifierData();
                var ability = controller.getAbility();
                if (data == null || ability == null || data.modifierMixins == null) continue;
                for (var mixin : data.modifierMixins) {
                    if (mixin == null || mixin.type != AbilityMixinData.Type.SwitchHealToHPDebtsMixin)
                        continue;
                    configured = true;
                    if (!PredicateEvaluator.all(mixin.predicates, ability, ability.getOwner(), target, event))
                        continue;
                    float ratio =
                            mixin.ratio == null
                                    ? 0f
                                    : mixin.ratio.get(AbilityActionHandler.propertiesFor(ability), 0f);
                    // A zero ratio deliberately suppresses HP recovery; invalid ratios cannot bypass it.
                    if (Float.isFinite(ratio) && ratio > 0f) HpDebtSettlement.add(target, amount * ratio);
                    return true;
                }
            }
            if (configured || !target.isConvertToHpDebt()) return false;
            // Retain the explicit restriction API for callers outside the resource modifier system.
            return !isLegacyArlecchinoBurst(incoming, event, target);
        }
    }

    private static boolean isLegacyArlecchinoBurst(
            Ability ability, AbilityModifierAction event, GameEntity target) {
        return (ability.getOwner() == target || ability.getCasterEntity() == target)
                && target instanceof EntityAvatar avatar
                && avatar.getAvatar().getAvatarId() == 10000096
                && "Avatar_Arlecchino_ElementalBurst_Attack".equals(ability.getData().abilityName)
                && "Arlecchino_ElementalBurst_Heal".equals(event.healTag);
    }

    private static List<AbilityModifierController> modifiers(GameEntity target) {
        synchronized (target.getInstancedModifiers()) {
            var seen =
                    Collections.newSetFromMap(new IdentityHashMap<AbilityModifierController, Boolean>());
            for (var named : target.getAppliedAbilityModifiers().values()) seen.addAll(named.values());
            // Both client and server applications register here. Old client IDs can remain until
            // their removal arrives; reading them again would resurrect a replaced/removed policy.
            return List.copyOf(seen);
        }
    }
}
