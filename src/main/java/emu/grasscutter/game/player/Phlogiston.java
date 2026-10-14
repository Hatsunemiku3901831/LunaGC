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

    private Phlogiston() {}

    public static void change(Player player, float delta) {
        mutate(player, delta, false);
    }

    public static void set(Player player, float value) {
        mutate(player, value, true);
    }

    private static void mutate(Player player, float amount, boolean set) {
        if (player == null || !Float.isFinite(amount) || !set && amount == 0) return;
        GameEntity changedTeam;
        synchronized (player) {
            var team = player.getTeamManager().getEntity();
            float previous = player.getPhlogistonValue();
            if (team == null || !Float.isFinite(previous)) return;
            float value = clamp(set ? amount : (double) previous + amount, TEAM_MAX);
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
        if (vehicle == null
                || vehicle.getOwner() == null
                || !Float.isFinite(amount)
                || !set && amount == 0) return;
        synchronized (vehicle) {
            float previous = vehicle.getCurPhlogiston();
            if (!Float.isFinite(previous)) return;
            float value = clamp(set ? amount : (double) previous + amount, VEHICLE_MAX);
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

    public static boolean mutate(Ability ability, GameEntity target, float delta, boolean set) {
        if (ability == null
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
                mutate(vehicle, delta, set);
            } else {
                var caster = ability.getCasterEntity();
                if (caster == null || caster.getPlayer() != player) return false;
                mutate(player, delta, set);
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
}
