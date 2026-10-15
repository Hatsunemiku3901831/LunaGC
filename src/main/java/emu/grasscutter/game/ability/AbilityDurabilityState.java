package emu.grasscutter.game.ability;

import emu.grasscutter.data.binout.AbilityMixinData;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.net.proto.AbilityInvokeEntryHeadOuterClass.AbilityInvokeEntryHead;
import emu.grasscutter.net.proto.AbilityMetaModifierDurabilityChangeOuterClass.AbilityMetaModifierDurabilityChange;
import emu.grasscutter.net.proto.AbilityMixinShieldBarOuterClass.AbilityMixinShieldBar;

import java.util.*;

/** Current, entity-local durability reports. This is not a client recovery wire message. */
public final class AbilityDurabilityState {
    public record ModifierState(
            int parentEntityId,
            String parentAbilityName,
            AbilityInvokeEntryHead head,
            AbilityMetaModifierDurabilityChange update) {}

    public record ShieldBarState(
            int parentEntityId,
            String parentAbilityName,
            AbilityInvokeEntryHead head,
            AbilityMixinShieldBar update) {}

    public record Snapshot(List<ModifierState> modifiers, List<ShieldBarState> shieldBars) {}

    private final GameEntity entity;
    // The normal controller registry is the only source currently modeled by this server.
    private final Map<Integer, Stored<ModifierState>> modifiers = new LinkedHashMap<>();
    private final Map<Long, Stored<ShieldBarState>> shieldBars = new LinkedHashMap<>();

    public AbilityDurabilityState(GameEntity entity) {
        this.entity = entity;
    }

    private record Stored<T>(Binding binding, T state) {}

    private final class Binding {
        final Ability ability;
        final AbilityModifierController controller;
        final AbilityInvokeEntryHead head;
        final AbilityMixinData mixin;
        final AbilityExecutionContext context;

        Binding(
                Ability ability,
                AbilityModifierController controller,
                AbilityInvokeEntryHead head,
                AbilityMixinData mixin) {
            this.ability = ability;
            this.controller = controller;
            this.head = head;
            this.mixin = mixin;
            context = new AbilityExecutionContext(ability, entity, false, true);
        }

        boolean current() {
            var scene = entity.getScene();
            if (scene == null
                    || scene.getEntityById(entity.getId()) != entity
                    || !context.isCurrent()
                    || !parentMatches(ability, head)) return false;
            int id = head.getInstancedModifierId();
            if (controller != null && entity.getInstancedModifiers().get(id) != controller)
                return false;
            if (controller == null && (id != 0 || ability.getOwner() != entity)) return false;
            return mixin == null
                    || mixin.enable
                            && ability.getData().localIdToMixin.get(head.getLocalId()) == mixin
                            && ownsMixin(ability, controller, mixin);
        }
    }

    private static boolean parentMatches(Ability ability, AbilityInvokeEntryHead head) {
        var owner = ability.getOwner();
        var roots = owner.getInstancedAbilities();
        int id = head.getInstancedAbilityId();
        if (roots.contains(ability)) {
            // IDs are unsigned. Zero is allowed when the instance ID alone identifies a modifier.
            return id == 0 || id > 0 && id <= roots.size() && roots.get(id - 1) == ability;
        }
        // Named dynamic parents have no slot; never borrow an occupied slot of another ability.
        return owner.getDynamicAbilities().get(ability.getData().abilityName) == ability
                && (id == 0 || id > 0 && (id > roots.size() || roots.get(id - 1) == null));
    }

    private static boolean ownsMixin(
            Ability ability, AbilityModifierController controller, AbilityMixinData mixin) {
        var mixins =
                controller == null
                        ? ability.getData().abilityMixins
                        : controller.getModifierData().modifierMixins;
        return mixins != null && Arrays.stream(mixins).anyMatch(candidate -> candidate == mixin);
    }

    private Binding bind(AbilityInvokeEntryHead head, boolean shield) {
        int modifierId = head.getInstancedModifierId();
        var controller = modifierId == 0 ? null : entity.getInstancedModifiers().get(modifierId);
        if (modifierId != 0 && controller == null || !shield && controller == null) return null;
        Ability ability;
        if (controller != null) ability = controller.getAbility();
        else {
            int id = head.getInstancedAbilityId();
            var roots = entity.getInstancedAbilities();
            if (id <= 0 || id > roots.size()) return null;
            ability = roots.get(id - 1);
        }
        if (ability == null) return null;
        var mixin = shield ? ability.getData().localIdToMixin.get(head.getLocalId()) : null;
        if (shield
                && (mixin == null
                        || mixin.type != AbilityMixinData.Type.ShieldBarMixin
                        || !ownsMixin(ability, controller, mixin))) return null;
        var binding = new Binding(ability, controller, head, mixin);
        return binding.current() ? binding : null;
    }

    boolean recordModifier(
            AbilityInvokeEntryHead head, AbilityMetaModifierDurabilityChange update) {
        if (!Float.isFinite(update.getRemainingDurability())
                || !Float.isFinite(update.getJODMMPLFMEG())) return false;
        // Do not alias a serverbuff/server/unknown source onto the normal instance map.
        if (head.getFPDAONBEBHKValue() != 0 || update.getIsServerbuffModifier()) return true;
        synchronized (entity.getInstancedModifiers()) {
            prune();
            var binding = bind(head, false);
            if (binding != null) {
                var ability = binding.ability;
                modifiers.put(
                        head.getInstancedModifierId(),
                        new Stored<>(
                                binding,
                                new ModifierState(
                                        ability.getOwner().getId(),
                                        ability.getData().abilityName,
                                        head,
                                        update)));
            }
        }
        return true;
    }

    boolean recordShieldBar(AbilityInvokeEntryHead head, AbilityMixinShieldBar update) {
        if (!Float.isFinite(update.getRemainingDurability())
                || !Float.isFinite(update.getMaxDurability())) return false;
        if (head.getFPDAONBEBHKValue() != 0) return true;
        synchronized (entity.getInstancedModifiers()) {
            prune();
            var binding = bind(head, true);
            if (binding != null) {
                var ability = binding.ability;
                long key =
                        Integer.toUnsignedLong(head.getInstancedModifierId()) << 32
                                | Integer.toUnsignedLong(head.getLocalId());
                shieldBars.put(
                        key,
                        new Stored<>(
                                binding,
                                new ShieldBarState(
                                        ability.getOwner().getId(),
                                        ability.getData().abilityName,
                                        head,
                                        update)));
            }
        }
        return true;
    }

    /** Removed or replaced instances must not pass their report on to a later application. */
    void modifierRebound(int id) {
        synchronized (entity.getInstancedModifiers()) {
            modifiers.remove(id);
            shieldBars
                    .values()
                    .removeIf(saved -> saved.binding.head.getInstancedModifierId() == id);
        }
    }

    private void prune() {
        modifiers.values().removeIf(saved -> !saved.binding.current());
        shieldBars.values().removeIf(saved -> !saved.binding.current());
    }

    public Snapshot snapshot() {
        synchronized (entity.getInstancedModifiers()) {
            prune();
            return new Snapshot(
                    modifiers.values().stream().map(Stored::state).toList(),
                    shieldBars.values().stream().map(Stored::state).toList());
        }
    }

    public void clear() {
        synchronized (entity.getInstancedModifiers()) {
            modifiers.clear();
            shieldBars.clear();
        }
    }
}
