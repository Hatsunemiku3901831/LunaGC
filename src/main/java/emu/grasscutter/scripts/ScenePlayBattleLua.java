package emu.grasscutter.scripts;

import emu.grasscutter.game.world.sceneplay.ScenePlayBattleParameters;

import org.luaj.vm2.*;
import org.luaj.vm2.lib.VarArgFunction;

import java.util.HashMap;
import java.util.Map;

/** Reject malformed identifiers before LuaJ can silently truncate them to Java integers. */
final class ScenePlayBattleLua {
    private static final double MAX_EXACT_INTEGER = 9_007_199_254_740_991d;

    private ScenePlayBattleLua() {}

    static Map<String, LuaValue> functions() {
        var result = new HashMap<String, LuaValue>();
        for (var functionName :
                new String[] {
                    "PrestartScenePlayBattle",
                    "AddScenePlayBattleProgress",
                    "SetScenePlayBattlePlayTeamEntityGadgetId",
                    "GetScenePlayBattleUidValue",
                    "SetScenePlayBattleUidValue",
                    "GetScenePlayBattleHostUid",
                    "GetScenePlayBattleType",
                    "ScenePlayBattleUidOp"
                }) {
            result.put(
                    functionName,
                    new VarArgFunction() {
                        @Override
                        public Varargs invoke(Varargs args) {
                            try {
                                var receiver = args.arg1().touserdata(ScriptLib.class);
                                if (!(receiver instanceof ScriptLib library))
                                    throw new IllegalArgumentException();
                                if (functionName.equals("PrestartScenePlayBattle")) {
                                    if (!args.arg(2).istable())
                                        throw new IllegalArgumentException();
                                    return LuaValue.valueOf(
                                            library.PrestartScenePlayBattle(
                                                    args.arg(2).checktable()));
                                }
                                int group = identifier(args.arg(2), true);
                                return switch (functionName) {
                                    case "SetScenePlayBattlePlayTeamEntityGadgetId" -> LuaValue
                                            .valueOf(
                                                    library
                                                            .SetScenePlayBattlePlayTeamEntityGadgetId(
                                                                    group,
                                                                    identifier(
                                                                            args.arg(3), false)));
                                    case "AddScenePlayBattleProgress" -> LuaValue.valueOf(
                                            library.AddScenePlayBattleProgress(
                                                    group,
                                                    ScenePlayBattleParameters.unsigned(
                                                            args.arg(3))));
                                    case "SetScenePlayBattleUidValue" -> LuaValue.valueOf(
                                            library.SetScenePlayBattleUidValue(
                                                    group,
                                                    identifier(args.arg(3), false),
                                                    text(args.arg(4)),
                                                    exactInteger(args.arg(5))));
                                    case "GetScenePlayBattleUidValue" -> {
                                        long value =
                                                library.GetScenePlayBattleUidValue(
                                                        group,
                                                        identifier(args.arg(3), false),
                                                        text(args.arg(4)));
                                        if (value > MAX_EXACT_INTEGER || value < -MAX_EXACT_INTEGER)
                                            throw new LuaError(
                                                    "Scene battle value exceeds LuaJ's exact"
                                                            + " integer range");
                                        yield LuaValue.valueOf((double) value);
                                    }
                                    case "GetScenePlayBattleHostUid" -> LuaValue.valueOf(
                                            library.GetScenePlayBattleHostUid(group));
                                    case "GetScenePlayBattleType" -> LuaValue.valueOf(
                                            library.GetScenePlayBattleType(group));
                                    case "ScenePlayBattleUidOp" -> LuaValue.valueOf(
                                            library.ScenePlayBattleUidOp(
                                                    group,
                                                    identifier(args.arg(3), false),
                                                    array(args.arg(4), true),
                                                    (int)
                                                            ScenePlayBattleParameters.unsigned(
                                                                    args.arg(5)),
                                                    text(args.arg(6)),
                                                    array(args.arg(7), false),
                                                    array(args.arg(8), false),
                                                    (int)
                                                            ScenePlayBattleParameters.unsigned(
                                                                    args.arg(9)),
                                                    (int)
                                                            ScenePlayBattleParameters.unsigned(
                                                                    args.arg(10))));
                                    default -> throw new IllegalArgumentException();
                                };
                            } catch (IllegalArgumentException invalid) {
                                return LuaValue.valueOf(functionName.startsWith("Get") ? 0 : -1);
                            }
                        }
                    });
        }
        return Map.copyOf(result);
    }

    private static int identifier(LuaValue raw, boolean allowZero) {
        long value = ScenePlayBattleParameters.unsigned(raw);
        if (value > Integer.MAX_VALUE || !allowZero && value == 0)
            throw new IllegalArgumentException();
        return (int) value;
    }

    private static String text(LuaValue raw) {
        if (raw.type() != LuaValue.TSTRING || raw.tojstring().isBlank())
            throw new IllegalArgumentException();
        return raw.tojstring();
    }

    private static long exactInteger(LuaValue raw) {
        double value = raw.todouble();
        if (raw.type() != LuaValue.TNUMBER
                || !Double.isFinite(value)
                || Math.abs(value) > MAX_EXACT_INTEGER
                || value != Math.rint(value)) throw new IllegalArgumentException();
        return (long) value;
    }

    private static int[] array(LuaValue raw, boolean uids) {
        if (!raw.istable()) throw new IllegalArgumentException();
        var table = raw.checktable();
        int count = table.length();
        if (table.keys().length != count) throw new IllegalArgumentException();
        int[] result = new int[count];
        for (int i = 0; i < count; i++)
            result[i] =
                    uids
                            ? identifier(table.rawget(i + 1), false)
                            : (int) ScenePlayBattleParameters.unsigned(table.rawget(i + 1));
        return result;
    }
}
