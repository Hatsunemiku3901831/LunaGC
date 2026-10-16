package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;

import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.*;
import emu.grasscutter.game.entity.*;

import java.util.List;
import java.util.function.BooleanSupplier;

@AbilityAction(AbilityModifierAction.Type.TPSAmmoBoxDispatch)
public final class ActionTpsAmmoBoxDispatch extends AbilityActionHandler {
    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString data, GameEntity target) {
        if (ability == null || action == null) return false;
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
                || !(target instanceof EntityAvatar recipient)
                || recipient.getPlayer() == null
                || !AbilityTpsAmmoBoxDispatch.supported(action)
                || !AbilityCooldownInvocation.allows(action)) return false;
        if (!conditionsPass(ability, action, target)) return true;
        return AbilityCooldownInvocation.recordPreparedApplication(
                ability,
                action,
                () -> {
                    synchronized (recipient.getPlayer()) {
                        var supplies =
                                AbilityTpsAmmoBoxDispatch.prepare(ability, action, recipient);
                        return supplies.map(
                                plan -> (BooleanSupplier) () -> grantAll(recipient, plan));
                    }
                });
    }

    private static boolean grantAll(EntityAvatar recipient, List<ActionTpsSupplyAmmo.Supply> plan) {
        synchronized (recipient.getPlayer()) {
            for (var supply : plan)
                if (supply.recipient() != recipient
                        || !ActionTpsSupplyAmmo.isCurrentSource(supply.ability(), recipient))
                    return false;
            return emu.grasscutter.game.tps.TpsWeaponSystem.supplyAmmunition(
                    recipient,
                    plan.stream()
                            .map(
                                    s ->
                                            new emu.grasscutter.game.tps.TpsSupplyTransaction
                                                    .Amount(s.pool(), s.amount()))
                            .toList());
        }
    }
}
