package emu.grasscutter.scripts;

import emu.grasscutter.game.world.sceneplay.ScenePlayBattleParameters;

import org.luaj.vm2.*;
import org.luaj.vm2.lib.VarArgFunction;

/** Preserve identifiers before LuaJ's Java bridge can truncate or coerce them. */
final class ScenePlayRewardLua {
    private ScenePlayRewardLua() {}

    static LuaValue function() {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                try {
                    var receiver = args.arg1().touserdata(ScriptLib.class);
                    if (!(receiver instanceof ScriptLib library) || !args.arg(2).istable())
                        return LuaValue.valueOf(-1);
                    var table = args.arg(2).checktable();
                    long group = ScenePlayBattleParameters.unsigned(table.get("group_id"));
                    long config = ScenePlayBattleParameters.unsigned(table.get("config_id"));
                    if (group == 0
                            || config == 0
                            || group > Integer.MAX_VALUE
                            || config > Integer.MAX_VALUE) return LuaValue.valueOf(-1);
                    return LuaValue.valueOf(
                            library.CreateScenePlayGeneralRewardGadget((int) group, (int) config));
                } catch (IllegalArgumentException | LuaError invalid) {
                    return LuaValue.valueOf(-1);
                }
            }
        };
    }
}
