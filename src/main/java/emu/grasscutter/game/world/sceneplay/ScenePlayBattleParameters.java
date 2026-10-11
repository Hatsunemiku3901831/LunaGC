package emu.grasscutter.game.world.sceneplay;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.util.List;

/** The progress thresholds and countdown used by PrestartScenePlayBattle. */
public record ScenePlayBattleParameters(long duration, long startCd, int mode, List<Long> stages) {
    public static final long MAX_UINT32 = 0xffff_ffffL;

    public ScenePlayBattleParameters {
        if (duration <= 0
                || duration > MAX_UINT32
                || startCd < 0
                || startCd > MAX_UINT32
                || stages == null
                || stages.size() < 2
                || stages.get(0) == null
                || stages.get(0) != 0)
            throw new IllegalArgumentException("Invalid scene battle parameters");
        stages = List.copyOf(stages);
        long previous = -1;
        for (long value : stages) {
            if (value <= previous || value > MAX_UINT32)
                throw new IllegalArgumentException("Progress thresholds must increase from zero");
            previous = value;
        }
    }

    public static ScenePlayBattleParameters fromLua(LuaTable table) {
        var raw = table.rawget("progress_stage");
        if (!raw.istable()) throw new IllegalArgumentException("Missing progress thresholds");
        var values = raw.checktable();
        if (values.keys().length != values.length())
            throw new IllegalArgumentException("Expected a dense progress array");
        var stages = new java.util.ArrayList<Long>();
        for (int i = 1; i <= values.length(); i++) stages.add(unsigned(values.rawget(i)));
        var mode = table.rawget("mode");
        return new ScenePlayBattleParameters(
                unsigned(table.rawget("duration")),
                unsigned(table.rawget("start_cd")),
                mode.isnil() ? 0 : (int) unsigned(mode),
                stages);
    }

    public static long unsigned(LuaValue value) {
        double number = value.todouble();
        if (value.type() != LuaValue.TNUMBER
                || !Double.isFinite(number)
                || number < 0
                || number > MAX_UINT32
                || number != Math.rint(number))
            throw new IllegalArgumentException("Expected an unsigned integer");
        return (long) number;
    }
}
