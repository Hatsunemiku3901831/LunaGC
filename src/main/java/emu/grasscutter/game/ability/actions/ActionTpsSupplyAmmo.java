package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.AbilityCooldownInvocation;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.tps.TpsWeaponSystem;

import java.util.Optional;
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
        return supportedConfiguration(action)
                && GameData.getTpsAmmunitionDataMap().containsKey(action.tpsSupplyAmmunitionId);
    }

    /** Inactive dispatch branches may reference a pool absent from this resource revision. */
    public static boolean supportedConfiguration(AbilityModifierAction action) {
        return action != null
                && action.type == AbilityModifierAction.Type.TPSSupplyAmmo
                && action.hasSupportedOffStageConfig()
                && (action.target == null || TARGETS.contains(action.target))
                && Boolean.TRUE.equals(action.tpsSupplyUseExplicitAmmoId)
                && !action.tpsSupplyUseBonus
                && action.tpsSupplyAmount != null
                && action.tpsSupplyAmmunitionId > 0;
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
        if (ability == null
                || !supported(action)
                || !(target instanceof EntityAvatar recipient)
                || !isCurrentSource(ability, recipient)) return false;
        if (!conditionsPass(ability, action, target)) return true;
        var planned = prepareResolved(ability, action, target);
        if (planned.isEmpty()) return false;
        var supply = planned.get();
        return AbilityCooldownInvocation.recordPreparedApplication(
                ability,
                action,
                () -> Optional.of((java.util.function.BooleanSupplier) supply::grant));
    }

    public record Supply(Ability ability, EntityAvatar recipient, int pool, int amount) {
        public boolean grant() {
            synchronized (recipient.getPlayer()) {
                return isCurrentSource(ability, recipient)
                        && TpsWeaponSystem.supplyAmmunition(recipient, pool, amount);
            }
        }
    }

    /** Validate without mutating: a box checks every selected award before granting any. */
    public static Optional<Supply> prepareResolved(
            Ability ability, AbilityModifierAction action, GameEntity target) {
        if (ability == null || !supported(action) || !(target instanceof EntityAvatar recipient))
            return Optional.empty();
        if (recipient.getPlayer() == null
                || recipient.getPlayer().getPendingTpsSupply() != null
                || recipient.getPlayer().getTpsReportTransaction().isPending())
            return Optional.empty();
        if (!isCurrentSource(ability, recipient)) return Optional.empty();
        var amount = action.tpsSupplyAmount.tryGet(propertiesFor(ability));
        if (amount.isEmpty()) return Optional.empty();
        // The native action evaluates a float and truncates it with cvttss2si.
        float count = (float) amount.getAsDouble();
        if (!Float.isFinite(count) || count < 0 || (double) count >= 2147483648d)
            return Optional.empty();
        return Optional.of(
                new Supply(ability, recipient, action.tpsSupplyAmmunitionId, (int) count));
    }

    public static boolean isCurrentSource(Ability ability, EntityAvatar recipient) {
        if (ability == null || recipient == null) return false;
        var owner = ability.getOwner();
        var player = ability.getPlayerOwner();
        var scene = player == null ? null : player.getScene();
        if (owner == null
                || owner.isDead()
                || scene == null
                || owner.getScene() != scene
                || scene.getEntityById(owner.getId()) != owner
                || recipient.getPlayer() != player
                || !TpsWeaponSystem.isSupplyRecipient(recipient)
                || !owner.getInstancedAbilities().contains(ability)
                        && owner.getDynamicAbilities().get(ability.getData().abilityName)
                                != ability) return false;
        return true;
    }
}
