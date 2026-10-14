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

    /** What a change with no amount of its own is worth. */
    public static final float DEFAULT_STEP = 5f;

    private Phlogiston() {}

    public static void change(Player player, float delta) {
        if (player == null || !Float.isFinite(delta) || delta == 0) return;
        synchronized (player) {
            var team = player.getTeamManager().getEntity();
            float previous = player.getPhlogistonValue();
            if (team == null || !Float.isFinite(previous)) return;
            float value = clamp((double) previous + delta, TEAM_MAX);
            if (Float.compare(previous, value) == 0) return;
            player.setPhlogistonValue(value);
            player.sendPacket(
                    new PacketServerGlobalValueChangeNotify(team.getId(), TEAM_KEY, value));
        }
    }

    /**
     * A Saurian burns its own fuel, including once that fuel is gone - reading an empty tank as
     * "this vehicle has none" is what used to hand the bill to the party instead.
     */
    public static void change(EntityVehicle vehicle, float delta) {
        if (vehicle == null || vehicle.getOwner() == null || !Float.isFinite(delta) || delta == 0)
            return;
        synchronized (vehicle) {
            float previous = vehicle.getCurPhlogiston();
            if (!Float.isFinite(previous)) return;
            float value = clamp((double) previous + delta, VEHICLE_MAX);
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
                change(vehicle, delta);
            } else {
                var caster = ability.getCasterEntity();
                if (caster == null || caster.getPlayer() != player) return false;
                change(player, delta);
            }
            return true;
        }
    }

    private static float clamp(double value, float max) {
        return (float) Math.max(0d, Math.min(max, value));
    }
}
