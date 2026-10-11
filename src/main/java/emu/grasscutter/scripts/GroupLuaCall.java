package emu.grasscutter.scripts;

import emu.grasscutter.game.world.sceneplay.ScenePlayBattleParameters;
import emu.grasscutter.scripts.data.SceneGroup;

import org.luaj.vm2.*;
import org.luaj.vm2.lib.VarArgFunction;

import java.util.Map;

/** Synchronous group calls keep the destination and the original caller as separate contexts. */
final class GroupLuaCall {
    private GroupLuaCall() {}

    static Map<String, LuaValue> functions() {
        return Map.of(
                "ExecuteGroupLua", function(true),
                "ExecuteActiveGroupLua", function(false));
    }

    private static LuaValue function(boolean load) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                try {
                    var receiver = args.arg1().touserdata(ScriptLib.class);
                    if (!(receiver instanceof ScriptLib library)) return LuaValue.valueOf(-1);
                    long id = ScenePlayBattleParameters.unsigned(args.arg(2));
                    if (id == 0
                            || id > Integer.MAX_VALUE
                            || args.arg(3).type() != LuaValue.TSTRING
                            || args.arg(3).tojstring().isBlank()
                            || !args.arg(4).istable()) return LuaValue.valueOf(-1);
                    var parameters = args.arg(4).checktable();
                    int count = parameters.length();
                    if (count > 3 || parameters.keys().length != count) return LuaValue.valueOf(-1);
                    var values = new LuaValue[count];
                    for (int i = 0; i < count; i++)
                        values[i] =
                                LuaValue.valueOf(
                                        (double)
                                                ScenePlayBattleParameters.unsigned(
                                                        parameters.rawget(i + 1)));
                    // ScriptLib itself is also used as the context of server initiated calls.
                    var rawUid = args.arg1().get("uid");
                    long uid = rawUid.isnil() ? 0 : ScenePlayBattleParameters.unsigned(rawUid);
                    if (uid > Integer.MAX_VALUE) return LuaValue.valueOf(-1);
                    return LuaValue.valueOf(
                            GroupLuaCall.call(
                                    library,
                                    args.arg1(),
                                    (int) uid,
                                    (int) id,
                                    args.arg(3).tojstring(),
                                    values,
                                    load));
                } catch (IllegalArgumentException | LuaError invalid) {
                    return LuaValue.valueOf(-1);
                } catch (RuntimeException failure) {
                    ScriptLib.logger.error("[LUA] Resolving group call failed", failure);
                    return LuaValue.valueOf(-1);
                }
            }
        };
    }

    private static int call(
            ScriptLib library,
            LuaValue caller,
            int uid,
            int groupId,
            String name,
            LuaValue[] parameters,
            boolean load) {
        var manager = library.getSceneScriptManagerOrNull();
        if (manager == null) return -1;
        var scene = manager.getScene();
        synchronized (scene) {
            var origin = library.getCurrentGroup().orElse(null);
            if (!manager.allowsGroupCall(origin)) return -1;
            var target = load ? manager.getGroupById(groupId) : manager.getLoadedGroupById(groupId);
            if (target == null
                    || !manager.allowsGroupCall(origin)
                    || !manager.allowsGroupCall(target)
                    || target.getBindings() == null) return -1;
            var rawFunction = target.getBindings().get(name);
            if (!(rawFunction instanceof LuaFunction function)) return -1;
            return scene.getScenePlayBattleManager()
                    .withGroupLuaCall(
                            origin,
                            target,
                            () ->
                                    library.withGroupContext(
                                            manager,
                                            target,
                                            () ->
                                                    invoke(
                                                            library,
                                                            caller,
                                                            uid,
                                                            target,
                                                            function,
                                                            name,
                                                            parameters)));
        }
    }

    private static int invoke(
            ScriptLib library,
            LuaValue caller,
            int uid,
            SceneGroup target,
            LuaFunction function,
            String name,
            LuaValue[] parameters) {
        var event = new emu.grasscutter.scripts.data.ScriptArgs(target.id, 0).setUid(uid);
        library.setCurrentCallParams(event);
        var args = new LuaValue[parameters.length + 2];
        args[0] = library.createEventContext(event, target);
        args[1] = caller;
        System.arraycopy(parameters, 0, args, 2, parameters.length);
        try {
            var status = function.invoke(args).arg1();
            double value = status.todouble();
            if (status.type() != LuaValue.TNUMBER
                    || !Double.isFinite(value)
                    || value != Math.rint(value)
                    || value < Integer.MIN_VALUE
                    || value > Integer.MAX_VALUE) return -1;
            return (int) value;
        } catch (RuntimeException failure) {
            ScriptLib.logger.error(
                    "[LUA] Group call {} failed in group {}", name, target.id, failure);
            return -1;
        }
    }
}
