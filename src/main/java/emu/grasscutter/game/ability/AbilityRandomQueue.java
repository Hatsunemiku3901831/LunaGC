package emu.grasscutter.game.ability;

import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.entity.GameEntity;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Resource queues currently select one eligible group; other counts need native verification. */
public final class AbilityRandomQueue {
    private static final AbilityModifierAction[] EMPTY=new AbilityModifierAction[0];
    private AbilityRandomQueue() {}

    public static boolean supportedCount(AbilityModifierAction action) {
        return action!=null && action.randomCount!=null && action.randomCount>=0 && action.randomCount<=1;
    }

    public static Optional<AbilityModifierAction[]> choose(Ability ability,AbilityModifierAction action,GameEntity target) {
        if (!supportedCount(action) || !AbilityGlobalValues.isCurrent(ability,target)
                || action.randomQueue!=null && action.randomQueue.length>256) return Optional.empty();
        if (action.randomCount==0 || action.randomQueue==null) return Optional.of(EMPTY);
        var eligible=new ArrayList<AbilityModifierAction.RandomActionGroup>();
        for (var group:action.randomQueue)
            if (group!=null && PredicateEvaluator.all(group.randomPredicates,ability,ability.getOwner(),target,action))
                eligible.add(group);
        if (eligible.isEmpty()) return Optional.of(EMPTY);
        var chosen=eligible.get(ThreadLocalRandom.current().nextInt(eligible.size())).actionQueue;
        return Optional.of(chosen==null?EMPTY:chosen);
    }
}
