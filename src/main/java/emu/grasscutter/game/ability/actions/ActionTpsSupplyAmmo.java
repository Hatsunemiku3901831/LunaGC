package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.AbilityCooldownInvocation;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.tps.TpsWeaponSystem;

import java.util.Set;

@AbilityAction(AbilityModifierAction.Type.TPSSupplyAmmo)
public final class ActionTpsSupplyAmmo extends AbilityActionHandler {
    private static final Set<String> TARGETS =
            Set.of(
                    "Self",
                    "Target",
                    "Applier",
                    "Caster",
                    "Owner",
                    "CurLocalAvatar",
                    "OriginOwner",
                    "CasterOriginOwner");

    /** Only the native explicit-pool, unscaled branch is paired with current resources. */
    public static boolean supported(AbilityModifierAction action) {
        return action != null
                && action.type == AbilityModifierAction.Type.TPSSupplyAmmo
                && action.hasSupportedOffStageConfig()
                && (action.target == null || TARGETS.contains(action.target))
                && Boolean.TRUE.equals(action.tpsSupplyUseExplicitAmmoId)
                && !action.tpsSupplyUseBonus
                && action.tpsSupplyAmount != null
                && action.tpsSupplyAmmunitionId > 0
                && GameData.getTpsAmmunitionDataMap().containsKey(action.tpsSupplyAmmunitionId);
    }

    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString data, GameEntity target) {
        if (ability == null || !supported(action)) return false;
        return executeResolved(
                ability,
                action,
                data,
                resolveTarget(
                        ability, target == null ? ability.getOwner() : target, action.target));
    }

    @Override
    public boolean executeResolved(
            Ability ability, AbilityModifierAction action, ByteString data, GameEntity target) {
        if (ability == null || !supported(action) || !(target instanceof EntityAvatar recipient))
            return false;
        var owner = ability.getOwner();
        var player = ability.getPlayerOwner();
        var scene = player == null ? null : player.getScene();
        if (owner == null
                || owner.isDead()
                || scene == null
                || owner.getScene() != scene
                || scene.getEntityById(owner.getId()) != owner
                || recipient.getPlayer() != player
                || !owner.getInstancedAbilities().contains(ability)
                        && owner.getDynamicAbilities().get(ability.getData().abilityName)
                                != ability) return false;
        if (!conditionsPass(ability, action, target)) return true;
        var amount = action.tpsSupplyAmount.tryGet(propertiesFor(ability));
        if (amount.isEmpty()) return false;
        // The native action evaluates a float and truncates it with cvttss2si.
        float count = (float) amount.getAsDouble();
        if (!Float.isFinite(count) || count < 0 || (double) count >= 2147483648d) return false;
        return AbilityCooldownInvocation.recordShared(
                ability,
                action,
                recipient,
                () ->
                        TpsWeaponSystem.supplyAmmunition(
                                recipient, action.tpsSupplyAmmunitionId, (int) count));
    }
}
