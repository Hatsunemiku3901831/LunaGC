package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;

import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.AbilityGlobalValues;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.props.FightProperty;

import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;

@AbilityAction(AbilityModifierAction.Type.GetFightProperty)
public final class ActionGetFightProperty extends AbilityActionHandler {
    private static final Set<String> SOURCES =
            Set.of(
                    "Self",
                    "Target",
                    "Applier",
                    "Owner",
                    "Caster",
                    "CurLocalAvatar",
                    "OriginOwner",
                    "CasterOriginOwner",
                    "AllPlayerAvatars");

    /** Only the explicitly dumped Sum operation supports a collection of property sources. */
    public static boolean supportedSource(AbilityModifierAction action) {
        if (action == null || !SOURCES.contains(action.fightPropertySourceTarget())) return false;
        var aggregation = action.fightPropertyAggregation();
        return "AllPlayerAvatars".equals(action.fightPropertySourceTarget())
                ? "Sum".equals(aggregation)
                : aggregation == null || "Sum".equals(aggregation);
    }

    @Override
    public boolean execute(
            Ability ability,
            AbilityModifierAction action,
            ByteString abilityData,
            GameEntity target) {
        if (ability == null
                || action == null
                || action.fightProp == null
                || !AbilityGlobalValues.isCurrent(ability, target)
                || !supportedSource(action)) return false;
        if (!conditionsPass(ability, action, target)) return true;
        List<? extends GameEntity> sources;
        if ("AllPlayerAvatars".equals(action.fightPropertySourceTarget())) {
            var player = ability.getPlayerOwner();
            if (player == null) return false;
            sources =
                    List.copyOf(
                            new LinkedHashSet<>(player.getTeamManager().getActiveTeamSnapshot()));
        } else {
            var source = resolveTarget(ability, target, action.fightPropertySourceTarget());
            if (source == null) return false;
            sources = List.of(source);
        }
        double total = 0;
        int count = 0;
        for (var source : sources) {
            if (!AbilityGlobalValues.isCurrent(ability, source)) continue;
            var value = read(ability, action, source);
            if (value.isEmpty()) return false;
            total += value.getAsDouble();
            count++;
        }
        return count != 0
                && AbilityGlobalValues.copy(ability, target, action.globalValueKey, (float) total);
    }

    private static OptionalDouble read(
            Ability ability, AbilityModifierAction action, GameEntity source) {
        var properties = new Object2FloatOpenHashMap<String>();
        for (var property : FightProperty.values())
            properties.put(property.name(), source.getFightProperty(property));
        properties.putAll(ability.getAbilitySpecials());
        // Missing names are not a real zero-valued property and must not erase an existing cache.
        var ops = action.fightProp.getOps();
        if (ops != null)
            for (var op : ops) {
                if (op == null || op.op == null) return OptionalDouble.empty();
                if (op.op == emu.grasscutter.data.common.DynamicFloat.StackOp.Op.KEY
                        && !properties.containsKey(op.sValue)) return OptionalDouble.empty();
            }
        return action.fightProp.tryGet(properties);
    }
}
