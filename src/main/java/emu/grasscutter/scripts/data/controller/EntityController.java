package emu.grasscutter.scripts.data.controller;

import emu.grasscutter.*;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.props.ElementType;
import emu.grasscutter.scripts.*;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.script.*;
import org.luaj.vm2.*;

public class EntityController {
    private static final Set<String> SERVER_CALLED = Set.of("OnBeHurt", "OnDie", "OnTimer");

    private transient CompiledScript entityController;
    private transient Bindings entityControllerBindings;

    public EntityController(CompiledScript entityController, Bindings entityControllerBindings) {
        this.entityController = entityController;
        this.entityControllerBindings = entityControllerBindings;
    }

    public void onBeHurt(GameEntity entity, ElementType elementType, boolean isHost) {
        callControllerScriptFunc(
                entity,
                "OnBeHurt",
                LuaValue.valueOf(elementType.getValue()),
                LuaValue.valueOf(0),
                LuaValue.valueOf(isHost));
    }

    public void onDie(GameEntity entity, ElementType elementType) {
        callControllerScriptFunc(
                entity, "OnDie", LuaValue.valueOf(elementType.getValue()), LuaValue.valueOf(0));
    }

    public void onTimer(GameEntity entity, int now) {
        callControllerScriptFunc(entity, "OnTimer", LuaValue.valueOf(now));
    }

    public void onPlayStageChange(GameEntity entity, int previous, int stage, int last) {
        callControllerScriptFunc(
                entity,
                "OnPlayStageChange",
                LuaValue.valueOf(previous),
                LuaValue.valueOf(stage),
                LuaValue.valueOf(last));
    }

    public int onClientExecuteRequest(GameEntity entity, int param1, int param2, int param3) {
        if (DebugConstants.LOG_LUA_SCRIPTS) {
            Grasscutter.getLogger()
                    .debug(
                            "Request on {}, {}: {}",
                            entity.getGroupId(),
                            param1,
                            entity.getPosition().toString());
        }
        LuaValue value =
                callControllerScriptFunc(
                        entity,
                        "OnClientExecuteReq",
                        LuaValue.valueOf(param1),
                        LuaValue.valueOf(param2),
                        LuaValue.valueOf(param3));
        return clientExecuteResult(value);
    }

    static int clientExecuteResult(LuaValue value) {
        // Controller failures (including LuaError's -1) must reach the client unchanged.
        return value.isint() ? value.toint() : 0;
    }

    // TODO actual execution should probably be handle by EntityControllerScriptManager
    private LuaValue callControllerScriptFunc(GameEntity entity, String funcName, LuaValue arg1) {
        return callControllerScriptFunc(entity, funcName, arg1, LuaValue.NIL, LuaValue.NIL);
    }

    private LuaValue callControllerScriptFunc(
            GameEntity entity, String funcName, LuaValue arg1, LuaValue arg2) {
        return callControllerScriptFunc(entity, funcName, arg1, arg2, LuaValue.NIL);
    }

    private LuaValue callControllerScriptFunc(
            GameEntity entity, String funcName, LuaValue arg1, LuaValue arg2, LuaValue arg3) {
        return runInCurrentLifecycle(
                entity, () -> invokeController(entity, funcName, arg1, arg2, arg3), LuaValue.valueOf(-1));
    }

    /** Skill callbacks share controller serialization, entity context and activity cancellation. */
    public boolean callAbilityFunction(GameEntity entity, Function<Bindings, Boolean> callback) {
        return runInCurrentLifecycle(
                entity,
                () -> {
                    synchronized (this) {
                        return ScriptLoader.getScriptLib()
                                .withEntityContext(entity, () -> callback.apply(entityControllerBindings));
                    }
                },
                false);
    }

    private <T> T runInCurrentLifecycle(GameEntity entity, Supplier<T> callback, T cancelled) {
        var activity = entity.getScene().getCrucibleSceneController();
        if (!activity.owns(entity.getGroupId())) return callback.get();
        var result = new java.util.concurrent.atomic.AtomicReference<T>(cancelled);
        activity.runIfCurrent(
                activity.getLifecycle().ticket(),
                () -> {
                    if (entity.getScene().getEntities().get(entity.getId()) == entity)
                        result.set(callback.get());
                });
        return result.get();
    }

    private synchronized LuaValue invokeController(
            GameEntity entity, String funcName, LuaValue arg1, LuaValue arg2, LuaValue arg3) {
        LuaValue funcLua = null;
        if (funcName != null && !funcName.isEmpty()) {
            funcLua = (LuaValue) entityControllerBindings.get(funcName);
        }

        LuaValue ret = LuaValue.ONE;

        if (funcLua != null) {
            try {
                var function = funcLua;
                ret =
                        ScriptLoader.getScriptLib()
                                .withEntityContext(
                                        entity,
                                        () ->
                                                function
                                                        .invoke(
                                                                new LuaValue[] {ScriptLoader.getScriptLibLua(), arg1, arg2, arg3})
                                                        .arg1());
            } catch (LuaError error) {
                ScriptLib.logger.error(
                        "[LUA] call function failed in gadget {} with {} {} {},{}",
                        entity.getEntityTypeId(),
                        funcName,
                        arg1,
                        arg2,
                        arg3,
                        error);
                ret = LuaValue.valueOf(-1);
            }
        } else if (funcName != null && !SERVER_CALLED.contains(funcName)) {
            ScriptLib.logger.error(
                    "[LUA] unknown func in gadget {} with {} {} {} {}",
                    entity.getEntityTypeId(),
                    funcName,
                    arg1,
                    arg2,
                    arg3);
        }
        return ret;
    }
}
