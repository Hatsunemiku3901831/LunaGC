package emu.grasscutter.game.world.seal;

import emu.grasscutter.scripts.constants.SealBattleType;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** The energy-charge parameters used by the original Aster recovery scripts. */
public record SealBattleParameters(
        int radius,
        int battleTime,
        int monsterGroupId,
        int defaultKillCharge,
        int autoCharge,
        int autoDecline,
        int maxEnergy) {
    public SealBattleParameters {
        if (radius <= 0
                || battleTime <= 0
                || monsterGroupId <= 0
                || maxEnergy <= 0
                || defaultKillCharge < 0
                || autoCharge < 0
                || autoDecline < 0) {
            throw new IllegalArgumentException("Invalid energy-charge battle parameters");
        }
    }

    public static SealBattleParameters fromLua(LuaTable table) {
        if (table == null
                || integer(table, "battle_type", -1) != SealBattleType.ENERGY_CHARGE.ordinal()) {
            throw new IllegalArgumentException("Unsupported seal battle type");
        }
        return new SealBattleParameters(
                integer(table, "radius", -1),
                integer(table, "battle_time", -1),
                integer(table, "monster_group_id", -1),
                integer(table, "default_kill_charge", 0),
                integer(table, "auto_charge", 0),
                integer(table, "auto_decline", 0),
                integer(table, "max_energy", -1));
    }

    private static int integer(LuaTable table, String name, int fallback) {
        LuaValue value = table.get(name);
        if (value.isnil()) return fallback;
        double number = value.isnumber() ? value.todouble() : Double.NaN;
        if (!Double.isFinite(number)
                || number < 0
                || number > Integer.MAX_VALUE
                || number != Math.rint(number)) {
            throw new IllegalArgumentException("Invalid seal battle parameter: " + name);
        }
        return (int) number;
    }
}
