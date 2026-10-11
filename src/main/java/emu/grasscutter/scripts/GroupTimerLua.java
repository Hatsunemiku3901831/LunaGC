package emu.grasscutter.scripts;

import emu.grasscutter.game.world.sceneplay.ScenePlayBattleParameters;

import org.luaj.vm2.*;
import org.luaj.vm2.lib.VarArgFunction;

import java.util.Map;

/** Reject malformed identifiers and delays before loading or replacing a scene timer. */
final class GroupTimerLua {
    private GroupTimerLua() {}

    static Map<String, LuaValue> functions() {
        return Map.of(
                "CreateGroupTimerEvent", function(true), "CancelGroupTimerEvent", function(false));
    }

    private static LuaValue function(boolean create) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                try {
                    var receiver = args.arg1().touserdata(ScriptLib.class);
                    long id = ScenePlayBattleParameters.unsigned(args.arg(2));
                    var source = args.arg(3);
                    if (!(receiver instanceof ScriptLib library)
                            || id <= 0
                            || id > Integer.MAX_VALUE
                            || source.type() != LuaValue.TSTRING
                            || source.tojstring().isBlank()) return LuaValue.valueOf(-1);
                    if (!create)
                        return LuaValue.valueOf(
                                library.CancelGroupTimerEvent((int) id, source.tojstring()));
                    var time = args.arg(4);
                    double value = time.todouble();
                    if (time.type() != LuaValue.TNUMBER
                            || !Double.isFinite(value)
                            || value < 0
                            || Math.ceil(value) > Integer.MAX_VALUE) return LuaValue.valueOf(-1);
                    return LuaValue.valueOf(
                            library.CreateGroupTimerEvent((int) id, source.tojstring(), value));
                } catch (IllegalArgumentException | LuaError invalid) {
                    return LuaValue.valueOf(-1);
                }
            }
        };
    }
}
