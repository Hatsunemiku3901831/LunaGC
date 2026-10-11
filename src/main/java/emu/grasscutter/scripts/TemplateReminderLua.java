package emu.grasscutter.scripts;

import org.luaj.vm2.*;
import org.luaj.vm2.lib.VarArgFunction;

/** Keep signed int32 reminder arguments intact before LuaJ's Java coercion. */
final class TemplateReminderLua {
    private TemplateReminderLua() {}

    static LuaValue function() {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                try {
                    var receiver = args.arg1().touserdata(ScriptLib.class);
                    long id =
                            emu.grasscutter.game.world.sceneplay.ScenePlayBattleParameters.unsigned(
                                    args.arg(2));
                    if (!(receiver instanceof ScriptLib library)
                            || id <= 0
                            || id > Integer.MAX_VALUE
                            || !args.arg(3).istable()) return LuaValue.valueOf(-1);
                    var table = args.arg(3).checktable();
                    int size = table.length();
                    if (table.keys().length != size) return LuaValue.valueOf(-1);
                    var values = new int[size];
                    for (int i = 0; i < size; i++) {
                        var raw = table.rawget(i + 1);
                        double value = raw.todouble();
                        if (raw.type() != LuaValue.TNUMBER
                                || !Double.isFinite(value)
                                || value != Math.rint(value)
                                || value < Integer.MIN_VALUE
                                || value > Integer.MAX_VALUE) return LuaValue.valueOf(-1);
                        values[i] = (int) value;
                    }
                    return LuaValue.valueOf(library.ShowTemplateReminder((int) id, values));
                } catch (IllegalArgumentException | LuaError invalid) {
                    return LuaValue.valueOf(-1);
                }
            }
        };
    }
}
