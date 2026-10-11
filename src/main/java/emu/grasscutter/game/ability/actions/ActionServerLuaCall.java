package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.scripts.ScriptLoader;

import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.LuaValue;

import java.util.concurrent.TimeUnit;

import javax.script.Bindings;

@AbilityAction(AbilityModifierAction.Type.ServerLuaCall)
public final class ActionServerLuaCall extends AbilityActionHandler {
    private static final MissingFunctionWarningLimiter missingFunctionWarnings =
            new MissingFunctionWarningLimiter(TimeUnit.MINUTES.toNanos(1));

    @Override
    public boolean execute(
            Ability ability,
            AbilityModifierAction action,
            ByteString abilityData,
            GameEntity target) {
        if (ability == null
                || action == null
                || target == null
                || target.getScene() == null
                || action.luaCallType == null
                || action.funcName == null
                || action.funcName.isBlank()
                || action.paramNum < 0
                || action.paramNum > 3
                || ScriptLoader.getScriptLib() == null) return false;
        if (!conditionsPass(ability, action, target)) return true;

        var source =
                switch (action.luaCallType) {
                    case OwnerFromGroup, OwnerGadget -> ability.getOwner();
                    default -> target;
                };
        if (source == null || source.getScene() == null) return false;

        return switch (action.luaCallType) {
            case Gadget, OwnerGadget -> {
                var controller = source.getEntityController();
                yield controller != null
                        && controller.callAbilityFunction(
                                source,
                                bindings ->
                                        callFunction(
                                                bindings,
                                                action.funcName,
                                                ability,
                                                action,
                                                target));
            }
            case FromGroup, OwnerFromGroup, SpecificGroup -> {
                int groupId = source.getGroupId();
                if (action.luaCallType == AbilityModifierAction.LuaCallType.SpecificGroup) {
                    if (action.callParamList == null || action.callParamList.length == 0)
                        yield false;
                    groupId = action.callParamList[0];
                }
                if (groupId <= 0) yield false;
                var manager = source.getScene().getScriptManager();
                var activity = source.getScene().getCrucibleSceneController();
                var ticket = activity.getLifecycle().ticket();
                var group = manager.getGroupById(groupId);
                if (group == null || group.getBindings() == null) yield false;
                if (!activity.owns(groupId))
                    yield ScriptLoader.getScriptLib()
                            .withEntityContext(
                                    source,
                                    group,
                                    () ->
                                            callFunction(
                                                    group.getBindings(),
                                                    action.funcName,
                                                    ability,
                                                    action,
                                                    target));
                var result = new boolean[1];
                activity.runIfCurrent(
                        ticket,
                        () -> {
                            if (manager.findGroupById(group.id) == group)
                                result[0] =
                                        ScriptLoader.getScriptLib()
                                                .withEntityContext(
                                                        source,
                                                        group,
                                                        () ->
                                                                callFunction(
                                                                        group.getBindings(),
                                                                        action.funcName,
                                                                        ability,
                                                                        action,
                                                                        target));
                        });
                yield result[0];
            }
            case CurScenePlay -> source.getScene()
                    .getScenePlayBattleManager()
                    .callServerLua(
                            source,
                            ability.getOwner(),
                            ability.getPlayerOwner(),
                            group ->
                                    ScriptLoader.getScriptLib()
                                            .withEntityContext(
                                                    source,
                                                    group,
                                                    () ->
                                                            callFunction(
                                                                    group.getBindings(),
                                                                    action.funcName,
                                                                    ability,
                                                                    action,
                                                                    target)));
            default -> false;
        };
    }

    /**
     * Handles fetching and calling a function.
     *
     * @param bindings The bindings to fetch the function from.
     * @param functionName The name of the function to call.
     * @param ability The ability data.
     * @param action The ability action data.
     * @return Whether the function was called successfully.
     */
    private static boolean callFunction(
            Bindings bindings,
            String functionName,
            Ability ability,
            AbilityModifierAction action,
            GameEntity target) {
        try {
            // Resolve live bindings: a later definition or reload can fix an earlier miss.
            var function = bindings == null ? null : bindings.get(functionName);
            if (!(function instanceof LuaFunction luaFunction)) {
                long suppressed = missingFunctionWarnings.acquireWarning(System.nanoTime());
                if (suppressed >= 0)
                    Grasscutter.getLogger()
                            .warn(
                                    "Unable to invoke {}: binding is not a LuaFunction ({});"
                                        + " suppressed {} repeated warnings.",
                                    functionName,
                                    function == null
                                            ? "missing"
                                            : function.getClass().getSimpleName(),
                                    suppressed);
                return false;
            }
            var args = new LuaValue[action.paramNum + 1];
            args[0] = ScriptLoader.getScriptLib().createAbilityContext(ability, target);
            if (action.paramNum >= 1) args[1] = LuaValue.valueOf(action.param1.get(ability));
            if (action.paramNum >= 2) args[2] = LuaValue.valueOf(action.param2.get(ability));
            if (action.paramNum >= 3) args[3] = LuaValue.valueOf(action.param3.get(ability));
            var status = luaFunction.invoke(args).arg1();
            return action.luaCallType != AbilityModifierAction.LuaCallType.CurScenePlay
                    || status.type() == LuaValue.TNUMBER && status.todouble() == 0;
        } catch (Exception exception) {
            // Real failures retain their stack trace; only absent functions are sampled.
            Grasscutter.getLogger().warn("Unable to invoke {}.", functionName, exception);
            return false;
        }
    }

    // One global sample per interval, without retaining names, players or Lua environments.
    static final class MissingFunctionWarningLimiter {
        private final long intervalNanos;
        private boolean initialized;
        private long lastWarningNanos;
        private long suppressedWarnings;

        MissingFunctionWarningLimiter(long intervalNanos) {
            this.intervalNanos = intervalNanos;
        }

        synchronized long acquireWarning(long nowNanos) {
            if (initialized && nowNanos - lastWarningNanos < intervalNanos) {
                suppressedWarnings++;
                return -1;
            }
            // Move the window and collect its count together; logging happens outside this lock.
            initialized = true;
            lastWarningNanos = nowNanos;
            long suppressed = suppressedWarnings;
            suppressedWarnings = 0;
            return suppressed;
        }
    }
}
