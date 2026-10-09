package emu.grasscutter.game.ability;

import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.actions.AbilityActionHandler;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.server.packet.send.PacketServerGlobalValueChangeNotify;

/** Finite resource writes; callbacks run after the map's atomic update has completed. */
public final class AbilityGlobalValues {
    private AbilityGlobalValues() {}

    public static boolean isCurrent(Ability ability, GameEntity target) {
        return ability != null && target != null
                && AbilityExecutionContext.canTrigger(ability.getOwner(), ability.getPlayerOwner())
                && AbilityExecutionContext.canTrigger(target, ability.getPlayerOwner());
    }

    public static boolean write(Ability ability, AbilityModifierAction action, GameEntity target,
                                float value, boolean add) {
        if (action == null) return false;
        float minimum=0, maximum=0;
        if (action.useLimitRange) {
            if (action.minValue == null || action.maxValue == null || ability == null) return false;
            var properties=AbilityActionHandler.propertiesFor(ability);
            var lower=action.minValue.tryGet(properties);
            var upper=action.maxValue.tryGet(properties);
            if (lower.isEmpty() || upper.isEmpty()) return false;
            minimum=(float)lower.getAsDouble(); maximum=(float)upper.getAsDouble();
            if (minimum > maximum) return false;
        }
        return write(ability,target,action.key,value,add,action.useLimitRange,minimum,maximum);
    }

    public static boolean copy(Ability ability, GameEntity target, String key, float value) {
        return write(ability,target,key,value,false,false,0,0);
    }

    public static boolean clear(Ability ability,GameEntity target,String key) {
        if (key==null || key.isBlank() || !isCurrent(ability,target)) return false;
        if (target.getGlobalAbilityValues().remove(key)==null) return true;
        target.onAbilityValueUpdate();
        if (!AbilityManager.isServerOwnedChain()) {
            var scene=target.getScene(); var host=scene==null?null:scene.getHost();
            if (host!=null) host.sendPacket(new PacketServerGlobalValueChangeNotify(target,key,0f));
        }
        return true;
    }

    private static boolean write(Ability ability, GameEntity target, String key, float value,
                                 boolean add, boolean bounded, float minimum, float maximum) {
        if (key == null || key.isBlank() || !Float.isFinite(value) || !isCurrent(ability,target)) return false;
        boolean[] valid={true}, changed={false};
        float[] written={0};
        target.getGlobalAbilityValues().compute(key,(name,previous)->{
            if (add && previous != null && !Float.isFinite(previous)) {
                valid[0]=false; return previous;
            }
            double result=add ? (previous == null ? 0d : previous.doubleValue())+value : value;
            if (bounded) result=Math.max(minimum,Math.min(maximum,result));
            float next=(float)result;
            if (!Float.isFinite(next)) { valid[0]=false; return previous; }
            written[0]=next;
            changed[0]=previous == null || Float.compare(previous,next) != 0;
            return next;
        });
        if (!valid[0]) return false;
        if (!changed[0]) return true;
        target.onAbilityValueUpdate();
        if (!AbilityManager.isServerOwnedChain()) {
            var scene=target.getScene();
            var host=scene == null ? null : scene.getHost();
            if (host != null) host.sendPacket(new PacketServerGlobalValueChangeNotify(target,key,written[0]));
        }
        return true;
    }
}
