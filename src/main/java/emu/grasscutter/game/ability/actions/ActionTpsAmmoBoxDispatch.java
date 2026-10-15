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
            // The parent owns the decision. Repeated leaves are distinct awards.
            for (var supply : plan) if (!supply.grant()) return false;
            return true;
        }
    }
}
