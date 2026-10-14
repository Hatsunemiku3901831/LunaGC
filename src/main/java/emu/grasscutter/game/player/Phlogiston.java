package emu.grasscutter.game.player;

import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.AbilityGlobalValues;
import emu.grasscutter.game.entity.EntityVehicle;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.server.packet.send.PacketServerGlobalValueChangeNotify;
import emu.grasscutter.server.packet.send.PacketVehiclePhlogistonPointsNotify;

/** Natlan's exploration fuel, held both by the party and by each Saurian it rides. */
public final class Phlogiston {
    public static final String TEAM_KEY = "SGV_PlayerTeam_Phlogiston";
    public static final float TEAM_MAX = 100f;
    public static final float VEHICLE_MAX = 50f;

    public enum RatioBasis {
        ABSOLUTE,
        CURRENT,
        CAPACITY
    }

    private Phlogiston() {}

    public static void change(Player player, float delta) {
        mutate(player, delta, false);
    }

    public static void set(Player player, float value) {
        mutate(player, value, true);
    }

    private static void mutate(Player player, float amount, boolean set) {
        mutate(player, amount, set, RatioBasis.ABSOLUTE);
    }

    private static void mutate(Player player, float amount, boolean set, RatioBasis basis) {
        if (player == null || !Float.isFinite(amount) || !set && amount == 0) return;
        if (!set && amount < 0 && infinite(player)) return;
        GameEntity changedTeam;
        synchronized (player) {
            var team = player.getTeamManager().getEntity();
            float previous = player.getPhlogistonValue();
            if (team == null || !Float.isFinite(previous)) return;
            double scaled = scale(amount, previous, TEAM_MAX, basis);
            float value = clamp(set ? scaled : previous + scaled, TEAM_MAX);
            if (Float.compare(previous, value) == 0) return;
            player.setPhlogistonValue(value);
            team.getGlobalAbilityValues().put(TEAM_KEY, value);
            changedTeam = team;
            player.sendPacket(
                    new PacketServerGlobalValueChangeNotify(team.getId(), TEAM_KEY, value));
        }
        changedTeam.onAbilityValueUpdate();
    }

    /**
     * A Saurian burns its own fuel, including once that fuel is gone - reading an empty tank as
     * "this vehicle has none" is what used to hand the bill to the party instead.
     */
    public static void change(EntityVehicle vehicle, float delta) {
        mutate(vehicle, delta, false);
    }

    private static void mutate(EntityVehicle vehicle, float amount, boolean set) {
        mutate(vehicle, amount, set, RatioBasis.ABSOLUTE);
    }

    private static void mutate(EntityVehicle vehicle, float amount, boolean set, RatioBasis basis) {
        if (vehicle == null
                || vehicle.getOwner() == null
                || !Float.isFinite(amount)
                || !set && amount == 0) return;
        if (!set && amount < 0 && infinite(vehicle.getOwner())) return;
        synchronized (vehicle) {
            float previous = vehicle.getCurPhlogiston();
            if (!Float.isFinite(previous)) return;
            double scaled = scale(amount, previous, VEHICLE_MAX, basis);
            float value = clamp(set ? scaled : previous + scaled, VEHICLE_MAX);
            if (Float.compare(previous, value) == 0) return;
            vehicle.setCurPhlogiston(value);
            vehicle.getOwner().sendPacket(new PacketVehiclePhlogistonPointsNotify(vehicle));
        }
    }

    /**
     * Check the invoking entity before choosing party or vehicle fuel; an empty tank never changes
     * ownership.
     */
    public static boolean change(Ability ability, GameEntity target, float delta) {
        return mutate(ability, target, delta, false);
    }
    /** Current mixin's explicit target overrides automatic party/Saurian selection. */
    public static boolean change(
            Ability ability, GameEntity bearer, float delta, String targetType) {
        return change(ability, bearer, delta, targetType, RatioBasis.ABSOLUTE);
    }

    public static boolean change(
            Ability ability, GameEntity bearer, float delta, String targetType, RatioBasis basis) {
        if (targetType == null
                || basis == null
                || !java.util.Set.of("Auto", "Self", "Team", "Vehicle").contains(targetType)
                || ability == null
                || bearer == null
                || !Float.isFinite(delta)
                || !AbilityGlobalValues.isCurrent(ability, bearer)) return false;
        var player = ability.getPlayerOwner();
        var owner = ability.getOwner();
        if (player == null || player.getScene() == null || owner == null || owner.isDead())
            return false;
        synchronized (player.getScene()) {
            EntityVehicle vehicle =
                    bearer instanceof EntityVehicle v
                            ? v
                            : owner instanceof EntityVehicle v ? v : null;
            if (vehicle != null && vehicle.getOwner() != player) return false;
            if (vehicle != null
                    && !"Team".equals(targetType)
                    && !Float.isFinite(vehicle.getCurPhlogiston())) return false;
            if (vehicle == null) {
                var caster = ability.getCasterEntity();
                if (caster == null || caster.getPlayer() != player) return false;
            }
            if ("Vehicle".equals(targetType) && vehicle == null) return false;
            if (vehicle != null
                    && !"Team".equals(targetType)
                    && (!"Auto".equals(targetType) || vehicle.getCurPhlogiston() > 0))
                mutate(vehicle, delta, false, basis);
            else mutate(player, delta, false, basis);
            return true;
        }
    }

    public static boolean mutate(Ability ability, GameEntity target, float delta, boolean set) {
        return mutate(ability, target, delta, set, RatioBasis.ABSOLUTE);
    }

    public static boolean mutate(
            Ability ability, GameEntity target, float delta, boolean set, RatioBasis basis) {
        if (ability == null
                || basis == null
                || target == null
                || !Float.isFinite(delta)
                || ability.getPlayerOwner() == null) return false;
        var player = ability.getPlayerOwner();
        var owner = ability.getOwner();
        var scene = player.getScene();
        if (scene == null || owner == null) return false;
        synchronized (scene) {
            if (owner.isDead() || !AbilityGlobalValues.isCurrent(ability, target)) return false;
            if (owner instanceof EntityVehicle vehicle) {
                if (vehicle.getOwner() != player) return false;
                mutate(vehicle, delta, set, basis);
            } else {
                var caster = ability.getCasterEntity();
                if (caster == null || caster.getPlayer() != player) return false;
                mutate(player, delta, set, basis);
            }
            return true;
        }
    }

    /** Supply the existing pool to scene/team sync without refilling it. */
    public static emu.grasscutter.net.proto.AbilityScalarValueEntryOuterClass
                    .AbilityScalarValueEntry
            snapshot(Player player) {
        float value = player.getPhlogistonValue();
        if (!Float.isFinite(value)) value = 0;
        value = clamp(value, TEAM_MAX);
        var team = player.getTeamManager().getEntity();
        if (team != null) team.getGlobalAbilityValues().put(TEAM_KEY, value);
        return emu.grasscutter.net.proto.AbilityScalarValueEntryOuterClass.AbilityScalarValueEntry
                .newBuilder()
                .setKey(
                        emu.grasscutter.net.proto.AbilityStringOuterClass.AbilityString.newBuilder()
                                .setHash(emu.grasscutter.utils.Utils.abilityHash(TEAM_KEY))
                                .setStr(TEAM_KEY))
                .setFloatValue(value)
                .build();
    }

    private static float clamp(double value, float max) {
        return (float) Math.max(0d, Math.min(max, value));
    }

    private static double scale(float amount, float previous, float max, RatioBasis basis) {
        return (double) amount
                * switch (basis) {
                    case ABSOLUTE -> 1f;
                    case CURRENT -> clamp(previous, max);
                    case CAPACITY -> max;
                };
    }

    /** The client's blocker list is held on Team entities, with one entry per live mixin. */
    private static boolean infinite(Player player) {
        var team = player.getTeamManager().getEntity();
        if (team == null) return false;
        synchronized (team.getInstancedModifiers()) {
            if (team.getAbilityHpModifier()
                    .hasActiveMixin(
                            emu.grasscutter.data.binout.AbilityMixinData.Type
                                    .InfinitePhlogistonMixin)) return true;
            for (var ability : team.getInstancedAbilities()) {
                if (ability == null) continue;
                if (hasInfinite(ability, team, ability.getData().abilityMixins)) return true;
                var defaults = ability.getData().modifiers.get("Default");
                if (defaults != null && hasInfinite(ability, team, defaults.modifierMixins))
                    return true;
            }
        }
        return false;
    }

    private static boolean hasInfinite(
            Ability ability,
            GameEntity bearer,
            emu.grasscutter.data.binout.AbilityMixinData[] mixins) {
        if (mixins == null) return false;
        for (var mixin : mixins)
            if (mixin != null
                    && mixin.enable
                    && mixin.type
                            == emu.grasscutter.data.binout.AbilityMixinData.Type
                                    .InfinitePhlogistonMixin
                    && AbilityGlobalValues.isCurrent(ability, bearer)
                    && emu.grasscutter.game.ability.PredicateEvaluator.all(
                            mixin.predicates, ability, ability.getOwner(), bearer, null))
                return true;
        return false;
    }
}
