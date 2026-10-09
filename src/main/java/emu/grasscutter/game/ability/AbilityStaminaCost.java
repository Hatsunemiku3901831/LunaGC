package emu.grasscutter.game.ability;

import emu.grasscutter.data.common.DynamicFloat;
import emu.grasscutter.game.ability.actions.AbilityActionHandler;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;

import java.util.*;

/** Live Actor_CostStaminaRatio layers, independent of nightsoul gauge values or entity IDs. */
public final class AbilityStaminaCost {
    static final String PROPERTY = "Actor_CostStaminaRatio";
    private final GameEntity entity;
    private final Map<AbilityModifierController, AbilityExecutionContext> sources =
            new IdentityHashMap<>();

    public AbilityStaminaCost(GameEntity entity) {
        this.entity = entity;
    }

    /** The caller holds the target's modifier registry lock. */
    public void registered(AbilityModifierController controller) {
        var data = controller.getModifierData();
        if (data != null
                && data.properties != null
                && data.properties.getValues().get(PROPERTY) != null)
            sources.put(controller, new AbilityExecutionContext(controller.getAbility(), entity));
    }

    public void clear() {
        synchronized (entity.getInstancedModifiers()) {
            sources.clear();
        }
    }

    private double factor() {
        synchronized (entity.getInstancedModifiers()) {
            var active = entity.getAbilityHpModifier().activeControllers();
            sources.keySet().removeIf(source -> !active.contains(source));
            double ratio = 0;
            for (var source : active) {
                var context = sources.get(source);
                if (context == null || !context.isCurrent()) continue;
                var expression = source.getModifierData().properties.getValues().get(PROPERTY);
                var properties = AbilityActionHandler.propertiesFor(source.getAbility(), entity);
                if (expression == null) return 1;
                if (expression.isDynamic() && expression.getOps() != null)
                    for (var token : expression.getOps())
                        if (token != null
                                && token.op == DynamicFloat.StackOp.Op.KEY
                                && !properties.containsKey(token.sValue)) return 1;
                var value = expression.tryGet(properties);
                // An unresolved layer cannot prove a reduction or an exemption.
                if (value.isEmpty()) return 1;
                ratio += value.getAsDouble();
                if (!Double.isFinite(ratio)) return 1;
            }
            return Math.max(0, 1 + ratio);
        }
    }

    /** Apply once in the relative-update path; restoration and vehicle stamina are unaffected. */
    public static int adjust(Player player, int amount, boolean character) {
        if (player == null || !character || amount >= 0 || player.getTeamManager() == null)
            return amount;
        var team = player.getTeamManager();
        if (team.getActiveTeamSnapshot().isEmpty()) return amount;
        var avatar = team.getCurrentAvatarEntity();
        if (avatar == null
                || avatar.getPlayer() != player
                || avatar.getScene() != player.getScene()
                || !avatar.isAlive()
                || !avatar.isAbilityLifecycleCurrent(avatar.getAbilityLifecycleVersion()))
            return amount;
        double adjusted = amount * avatar.getAbilityStaminaCost().factor();
        return (int) Math.max(Integer.MIN_VALUE, Math.min(0, adjusted));
    }
}
