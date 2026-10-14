package emu.grasscutter.game.ability;

import com.google.protobuf.ByteString;

import emu.grasscutter.data.binout.AbilityMixinData;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.server.game.GameSession;

import java.util.*;

/** Explicit combat transitions, without inferring their direction from healing globals. */
public final class AbilityCombatCallbacks {
    private record State(long lifetime, GameSession session, boolean combat) {}

    private final Map<Object, Map<AbilityMixinData, State>> states = new WeakHashMap<>();

    private static Boolean combat(ByteString payload) {
        if (payload == null || payload.size() > 32) return null;
        var input = payload.newCodedInput();
        boolean value = false, seen = false;
        try {
            while (!input.isAtEnd()) {
                if (input.readTag() != 8 || seen) return null;
                long raw = input.readUInt64();
                if (raw != 0 && raw != 1) return null;
                value = raw == 1;
                seen = true;
            }
            return value;
        } catch (java.io.IOException malformed) {
            return null;
        }
    }

    public boolean apply(
            Ability ability, AbilityMixinData mixin, ByteString payload, GameEntity target) {
        return apply(ability, mixin, payload, target, null);
    }

    public boolean apply(
            Ability ability,
            AbilityMixinData mixin,
            ByteString payload,
            GameEntity target,
            AbilityModifierController parent) {
        Boolean combat = combat(payload);
        if (combat == null
                || ability == null
                || mixin == null
                || !mixin.enable
                || mixin.type != AbilityMixinData.Type.AvatarCombatMixin
                || !(ability.getOwner() instanceof EntityAvatar owner)
                || owner.getPlayer() != ability.getPlayerOwner()
                || owner.isDead()
                || !(owner.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP) > 0)
                || !AbilityGlobalValues.isCurrent(ability, target)
                || mixin.elementTypeModifier != null
                || mixin.doAfterDie != null
                || mixin.isUnique != null
                || mixin.NHPDOIELPCE != null
                || !AbilityActionSequence.supportedPredicates(mixin.predicates, 0)) return false;
        var enter = mixin.onEnterCombat != null ? mixin.onEnterCombat : mixin.rawEnterCombat;
        var exit = mixin.onExitCombat != null ? mixin.onExitCombat : mixin.rawExitCombat;
        if (AbilityActionSequence.callbacks(ability.getData(), enter) == null
                || AbilityActionSequence.callbacks(ability.getData(), exit) == null) return false;
        var context = new AbilityExecutionContext(ability, owner);
        var scene = owner.getScene();
        if (scene == null) return false;
        synchronized (scene) {
            Object source = source(ability, mixin, owner, parent);
            if (source == null) return false;
            if (!context.isCurrent()
                    || !PredicateEvaluator.all(mixin.predicates, ability, owner, owner, null))
                return false;
            synchronized (states) {
                var byMixin = states.computeIfAbsent(source, k -> new IdentityHashMap<>());
                var previous = byMixin.get(mixin);
                long lifetime = owner.getAbilityLifecycleVersion();
                var session = ability.getPlayerOwner().getSession();
                if (previous != null
                        && previous.lifetime() == lifetime
                        && previous.session() == session
                        && previous.combat() == combat) return true;
                byMixin.put(mixin, new State(lifetime, session, combat));
                AbilityCooldownInvocation.run(
                        new AbilityCooldownInvocation.Scope(parent, false),
                        () ->
                                AbilityActionSequence.run(
                                        ability,
                                        combat ? enter : exit,
                                        owner,
                                        () ->
                                                context.isCurrent()
                                                        && source(ability, mixin, owner, parent)
                                                                == source));
                return true;
            }
        }
    }

    private static boolean contains(AbilityMixinData[] values, AbilityMixinData mixin) {
        return values != null && Arrays.stream(values).anyMatch(value -> value == mixin);
    }

    private static Object source(
            Ability ability,
            AbilityMixinData mixin,
            EntityAvatar owner,
            AbilityModifierController parent) {
        if (parent != null) {
            synchronized (owner.getInstancedModifiers()) {
                return parent.getAbility() == ability
                                && owner.getAbilityHpModifier()
                                        .lifecycleControllers()
                                        .contains(parent)
                                && contains(parent.getModifierData().modifierMixins, mixin)
                        ? parent
                        : null;
            }
        }
        var defaults = ability.getData().modifiers.get("Default");
        return contains(ability.getData().abilityMixins, mixin)
                        || defaults != null && contains(defaults.modifierMixins, mixin)
                ? ability
                : null;
    }
}
