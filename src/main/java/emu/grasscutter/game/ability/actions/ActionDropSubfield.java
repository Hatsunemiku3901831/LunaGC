package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.AbilityCooldownInvocation;
import emu.grasscutter.game.entity.GameEntity;

import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** Uses the server subfield tables; the client cannot choose item IDs or quantities. */
@AbilityAction(AbilityModifierAction.Type.DropSubfield)
public final class ActionDropSubfield extends AbilityActionHandler {
    private static final Set<String> TARGETS = Set.of("Self", "Target", "Caster", "Owner");

    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString data, GameEntity target) {
        if (ability == null
                || action == null
                || action.target != null && !TARGETS.contains(action.target)) return false;
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
                || action == null
                || target == null
                || target.getScene() == null
                || action.type != AbilityModifierAction.Type.DropSubfield
                || !action.hasSupportedOffStageConfig()) return false;
        if (!current(ability, target)) return false;
        if (!conditionsPass(ability, action, target)) return true;
        // Other tracked actions can hold this ledger while dispatching a sub-action. Never wait
        // for it with the scene locked: their sub-action may itself need the scene monitor.
        return AbilityCooldownInvocation.recordPreparedApplication(
                ability,
                action,
                () -> {
                    synchronized (target.getScene()) {
                        if (!current(ability, target) || !ready(target, action.subfieldName))
                            return Optional.empty();
                    }
                    return Optional.of(
                            (BooleanSupplier)
                                    () -> {
                                        synchronized (target.getScene()) {
                                            return current(ability, target)
                                                    && ready(target, action.subfieldName)
                                                    && target.dropSubfield(action.subfieldName);
                                        }
                                    });
                });
    }

    private static boolean current(Ability ability, GameEntity target) {
        var owner = ability.getOwner();
        var caster = ability.getPlayerOwner();
        var scope = AbilityCooldownInvocation.capture();
        if (target instanceof emu.grasscutter.game.entity.EntityGadget gadget
                && gadget.getQuartzOreCycle() != null
                && scope != null
                && !gadget.getQuartzOreCycle().applicationCurrent(scope.controller())) return false;
        return !(owner == null
                || owner.getScene() != target.getScene()
                || target.getScene().getEntities().get(target.getId()) != target
                || owner.getScene().getEntityById(owner.getId()) != owner
                || caster == null
                || caster.getScene() != target.getScene()
                || caster.getWorld() != target.getWorld()
                || !owner.getInstancedAbilities().contains(ability)
                        && owner.getDynamicAbilities().get(ability.getData().abilityName)
                                != ability);
    }

    /** A missing item/table must not consume a material completion or hide its source. */
    public static boolean ready(GameEntity target, String name) {
        if (name == null || name.isBlank()) return false;
        var mapping = GameData.getSubfieldMappingMap().get(target.getEntityTypeId());
        if (mapping == null || mapping.getSubfields() == null) return false;
        for (var field : mapping.getSubfields()) {
            if (field == null || !name.equals(field.getSubfieldName())) continue;
            var drop = GameData.getDropSubfieldMappingMap().get(field.getDrop_id());
            var table =
                    drop == null
                            ? null
                            : GameData.getDropTableExcelConfigDataMap().get(drop.getItemId());
            if (table == null
                    || table.getDropVec() == null
                    || table.getRandomType() != 0 && table.getRandomType() != 1) return false;
            for (var entry : table.getDropVec()) {
                if (entry == null) return false;
                if (entry.getItemId() == 0 || entry.getWeight() <= 0) continue;
                if (!GameData.getItemDataMap().containsKey(entry.getItemId())
                        || entry.getCountRange() == null) return false;
                var range = entry.getCountRange().split(";", -1);
                if (range.length < 1 || range.length > 2) return false;
                try {
                    for (var value : range) if (Integer.parseInt(value) < 0) return false;
                } catch (NumberFormatException invalid) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }
}
