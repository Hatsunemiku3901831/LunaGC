package emu.grasscutter.game.ability;

import com.google.protobuf.ByteString;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.actions.ActionDropSubfield;
import emu.grasscutter.game.entity.EntityGadget;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** One resource-defined pickup and disappearance period per normal Dragonspine quartz ore. */
public final class QuartzOreCycle {
    public static final int GADGET = 70590025;
    public static final String SUBFIELD = "Dulins_Blood";
    private static final String ROOT = "SceneObj_Area_Ljxs_Property_Prop_OreDulinsBlood_01";
    private final EntityGadget ore;
    private final LongSupplier clock;
    private final Map<AbilityCooldownInvocation.Ledger, Long> applications = new WeakHashMap<>();
    private volatile long generation;
    // Cycle state is protected by the scene monitor. Registration only accesses applications.
    private Cycle cycle;

    private record Profile(
            Ability root,
            AbilityModifierAction drop,
            AbilityModifierAction hide,
            AbilityModifierAction restore,
            long duration) {}

    private static final class Cycle {
        final Profile profile;
        final AbilityExecutionContext lifetime;
        boolean claimed, started, restoring;
        long start;

        Cycle(Profile profile, EntityGadget ore) {
            this.profile = profile;
            this.lifetime = new AbilityExecutionContext(profile.root, ore, false, true);
        }
    }

    public QuartzOreCycle(EntityGadget ore) {
        this(ore, System::nanoTime);
    }

    public QuartzOreCycle(EntityGadget ore, LongSupplier clock) {
        this.ore = ore;
        this.clock = clock;
    }

    private boolean own(AbilityModifierController controller) {
        return controller != null
                && controller.getAbility().getOwner() == ore
                && ROOT.equals(controller.getAbility().getData().abilityName)
                && ("Appear".equals(controller.getName())
                        || "Disppear".equals(controller.getName()));
    }

    /** Called while the modifier registry is locked; never acquires the scene or Lua monitor. */
    public void registered(AbilityModifierController controller) {
        if (!own(controller)) return;
        synchronized (applications) {
            applications.putIfAbsent(controller.getCooldownLedger(), generation);
        }
    }

    public boolean applicationCurrent(AbilityModifierController controller) {
        if (!own(controller)) return true;
        synchronized (applications) {
            var born = applications.get(controller.getCooldownLedger());
            return born == null || born == generation;
        }
    }

    private Profile profile() {
        if (ore.getGadgetId() != GADGET
                || ore.getScene().getEntities().get(ore.getId()) != ore
                || ore.getConfigGadget() == null
                || ore.getConfigGadget().getAbilities() == null
                || ore.getConfigGadget().getAbilities().stream()
                        .noneMatch(a -> a != null && ROOT.equals(a.getAbilityName()))) return null;
        var root =
                ore.getInstancedAbilities().stream()
                        .filter(a -> ROOT.equals(a.getData().abilityName))
                        .findFirst()
                        .orElse(null);
        if (root == null
                || root.getOwner() != ore
                || GameData.getAbilityData(ROOT) != root.getData()) return null;
        var caster = root.getPlayerOwner();
        if (caster == null
                || caster.getScene() != ore.getScene()
                || caster.getWorld() != ore.getWorld()) return null;
        var appear = root.getData().modifiers.get("Appear");
        var disappear = root.getData().modifiers.get("Disppear");
        if (appear == null
                || appear.onRemoved == null
                || disappear == null
                || disappear.onRemoved == null
                || disappear.duration == null) return null;
        var drop =
                Arrays.stream(appear.onRemoved)
                        .filter(
                                a ->
                                        plain(a)
                                                && a.type == AbilityModifierAction.Type.DropSubfield
                                                && SUBFIELD.equals(a.subfieldName))
                        .findFirst()
                        .orElse(null);
        var hide =
                Arrays.stream(appear.onRemoved)
                        .filter(a -> lua(a, root, 201))
                        .findFirst()
                        .orElse(null);
        var restore =
                Arrays.stream(disappear.onRemoved)
                        .filter(a -> lua(a, root, 0))
                        .findFirst()
                        .orElse(null);
        var seconds = disappear.duration.tryGet(root.getAbilitySpecials());
        if (drop == null
                || hide == null
                || restore == null
                || seconds.isEmpty()
                || seconds.getAsDouble() <= 0
                || seconds.getAsDouble() >= Long.MAX_VALUE / 1_000_000_000d) return null;
        return new Profile(
                root, drop, hide, restore, (long) (seconds.getAsDouble() * 1_000_000_000d));
    }

    private static boolean plain(AbilityModifierAction action) {
        return action != null
                && (action.target == null || "Self".equals(action.target))
                && action.otherTargets == null
                && (action.predicates == null || action.predicates.isEmpty())
                && action.hasSupportedOffStageConfig();
    }

    private static boolean lua(AbilityModifierAction action, Ability root, int state) {
        return plain(action)
                && action.type == AbilityModifierAction.Type.ExecuteGadgetLua
                && action.param1 != null
                && action.param1.get(root) == state
                && action.param2 != null
                && action.param2.get(root) == 0
                && action.param3 != null
                && action.param3.get(root) == 0;
    }

    private Cycle current(Profile profile) {
        if (cycle != null && (cycle.profile.root != profile.root || !cycle.lifetime.isCurrent()))
            return null;
        if (cycle == null) cycle = new Cycle(profile, ore);
        return cycle;
    }

    public boolean drop(BooleanSupplier body) {
        synchronized (ore.getScene()) {
            var profile = profile();
            var scope = AbilityCooldownInvocation.capture();
            if (profile == null
                    || ore.getState() != 0 && ore.getState() != 201
                    || scope != null
                            && (!own(scope.controller()) || !applicationCurrent(scope.controller()))
                    || !ActionDropSubfield.ready(ore, SUBFIELD)) return false;
            var active = current(profile);
            if (active == null) return false;
            if (active.claimed) return true;
            // Spawning may fail after the scene accepted a pickup. That outcome cannot replay.
            active.claimed = true;
            boolean accepted = body.getAsBoolean();
            if (!accepted) active.claimed = false;
            return accepted;
        }
    }

    void removed(AbilityModifierController controller, ByteString payload) {
        if (!own(controller) || !applicationCurrent(controller)) return;
        var lua = ore.getEntityController();
        if (lua == null) return;
        // Native Lua acquires its controller before changing the scene. Keep that lock order.
        synchronized (lua) {
            Profile profile;
            long selected;
            synchronized (ore.getScene()) {
                profile = profile();
                if (profile == null
                        || profile.root != controller.getAbility()
                        || !controller.isClientInstanceCurrent(ore)
                        || !ore.isAlive()) return;
                if ("Disppear".equals(controller.getName())) {
                    restoreIfDue();
                    return;
                }
                if (ore.getState() != 0 && ore.getState() != 201) return;
                selected = generation;
            }
            AbilityCooldownInvocation.run(
                    controller,
                    () -> {
                        if (!new ActionDropSubfield()
                                .execute(profile.root, profile.drop, payload, ore)) return;
                        synchronized (ore.getScene()) {
                            if (selected == generation
                                    && cycle != null
                                    && cycle.claimed
                                    && cycle.lifetime.isCurrent()
                                    && ore.getState() == 0) ore.onClientExecuteRequest(201, 0, 0);
                        }
                    });
        }
    }

    /** Both original Lua and the material callback may arrive first. Neither restarts the clock. */
    public void stateChanged(int oldState, int state) {
        synchronized (ore.getScene()) {
            if (oldState == state) return;
            if (state == 201) {
                var profile = profile();
                var active = profile == null ? null : current(profile);
                if (active != null && !active.started) {
                    active.started = true;
                    active.start = clock.getAsLong();
                }
            } else {
                cycle = null;
                generation++;
            }
        }
    }

    public void clear() {
        synchronized (ore.getScene()) {
            cycle = null;
            generation++;
        }
    }

    public void tick() {
        var lua = ore.getEntityController();
        if (lua == null) return;
        synchronized (lua) {
            synchronized (ore.getScene()) {
                restoreIfDue();
            }
        }
    }

    private boolean due() {
        return cycle != null
                && cycle.started
                && !cycle.restoring
                && clock.getAsLong() - cycle.start >= cycle.profile.duration;
    }

    private void restoreIfDue() {
        if (!due()
                || !ore.isAlive()
                || ore.getState() != 201
                || !cycle.lifetime.isCurrent()
                || profile() == null
                || !ore.getInstancedAbilities().contains(cycle.profile.root)) return;
        cycle.restoring = true;
        ore.getEntityController().onClientExecuteRequest(ore, 0, 0, 0);
    }

    public int clientExecute(int p1, int p2, int p3, java.util.function.IntSupplier body) {
        var lua = ore.getEntityController();
        if (lua == null) return -1;
        synchronized (lua) {
            synchronized (ore.getScene()) {
                if (p1 == 0) {
                    restoreIfDue();
                    return 0;
                }
                if (p1 == 201
                        && (profile() == null || ore.getState() != 0 && ore.getState() != 201))
                    return -1;
                return body.getAsInt();
            }
        }
    }

    public boolean executeLua(Ability ability, AbilityModifierAction action) {
        var lua = ore.getEntityController();
        if (lua == null) return false;
        synchronized (lua) {
            synchronized (ore.getScene()) {
                var profile = profile();
                var scope = AbilityCooldownInvocation.capture();
                if (profile == null
                        || profile.root != ability
                        || action != profile.hide && action != profile.restore
                        || scope != null
                                && (!own(scope.controller())
                                        || !applicationCurrent(scope.controller()))) return false;
                return ore.onClientExecuteRequest(action.param1.getInt(ability), 0, 0) == 0;
            }
        }
    }
}
