package emu.grasscutter.scripts;

import emu.grasscutter.game.world.sceneplay.ScenePlayBattleParameters;

import org.luaj.vm2.*;
import org.luaj.vm2.lib.VarArgFunction;

import java.util.*;

/** Reject invalid tide identifiers, arrays and limits before Java integer coercion. */
final class MonsterTideLua {
    private MonsterTideLua() {}

    static Map<String, LuaValue> functions() {
        var functions = new HashMap<String, LuaValue>();
        for (var operation :
                new String[] {
                    "AutoMonsterTide",
                    "KillMonsterTide",
                    "EndMonsterTide",
                    "PauseAutoMonsterTide",
                    "ContinueAutoMonster"
                })
            functions.put(
                    operation,
                    new VarArgFunction() {
                        @Override
                        public Varargs invoke(Varargs args) {
                            try {
                                var receiver = args.arg1().touserdata(ScriptLib.class);
                                if (!(receiver instanceof ScriptLib library))
                                    return LuaValue.valueOf(-1);
                                int first = integer(args.arg(2), false),
                                        second = integer(args.arg(3), false);
                                Integer[] order = null;
                                int total = 0, minimum = 0, maximum = 0, reason = 0;
                                if (operation.equals("AutoMonsterTide")) {
                                    var table = args.arg(4).checktable();
                                    int count = table.length();
                                    if (count == 0 || table.keys().length != count)
                                        return LuaValue.valueOf(-1);
                                    order = new Integer[count];
                                    for (int i = 0; i < count; i++)
                                        order[i] = integer(table.rawget(i + 1), false);
                                    total = integer(args.arg(5), true);
                                    minimum = integer(args.arg(6), false);
                                    maximum = integer(args.arg(7), false);
                                    if (maximum < minimum
                                            || maximum > count
                                            || new HashSet<>(Arrays.asList(order)).size() != count)
                                        return LuaValue.valueOf(-1);
                                } else if (operation.equals("EndMonsterTide"))
                                    reason = integer(args.arg(4), true);
                                var manager = library.getSceneScriptManagerOrNull();
                                if (manager == null) return LuaValue.valueOf(-1);
                                synchronized (manager.getScene()) {
                                    if (!manager.allowsGroupCall(
                                            library.getCurrentGroup().orElse(null)))
                                        return LuaValue.valueOf(-1);
                                    return LuaValue.valueOf(
                                            switch (operation) {
                                                case "AutoMonsterTide" -> library.AutoMonsterTide(
                                                        first, second, order, total, minimum,
                                                        maximum);
                                                case "KillMonsterTide" -> library.KillMonsterTide(
                                                        first, second);
                                                case "EndMonsterTide" -> library.EndMonsterTide(
                                                        first, second, reason);
                                                case "PauseAutoMonsterTide" -> library
                                                        .PauseAutoMonsterTide(first, second);
                                                default -> library.ContinueAutoMonster(
                                                        first, second);
                                            });
                                }
                            } catch (IllegalArgumentException | LuaError invalid) {
                                return LuaValue.valueOf(-1);
                            } catch (RuntimeException failure) {
                                ScriptLib.logger.error(
                                        "[LUA] Monster tide operation {} failed", operation, failure);
                                return LuaValue.valueOf(-1);
                            }
                        }
                    });
        return Map.copyOf(functions);
    }

    private static int integer(LuaValue value, boolean allowZero) {
        long number = ScenePlayBattleParameters.unsigned(value);
        if (number > Integer.MAX_VALUE || !allowZero && number == 0)
            throw new IllegalArgumentException();
        return (int) number;
    }
}
