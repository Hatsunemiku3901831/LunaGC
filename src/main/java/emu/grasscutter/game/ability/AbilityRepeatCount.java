package emu.grasscutter.game.ability;

import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.actions.AbilityActionHandler;
import java.util.OptionalInt;

/** Current resource counts are integers; do not invent rounding or silently truncate a bad count. */
public final class AbilityRepeatCount {
    private static final int MAX_REPEATS=256;
    private AbilityRepeatCount() {}
    public static OptionalInt resolve(Ability ability,AbilityModifierAction action) {
        if (ability==null || action==null || action.repeatTimes==null) return OptionalInt.empty();
        var value=action.repeatTimes.tryGet(AbilityActionHandler.propertiesFor(ability));
        if (value.isEmpty()) return OptionalInt.empty();
        double count=value.getAsDouble();
        return count>=0 && count<=MAX_REPEATS && count==Math.rint(count)
                ? OptionalInt.of((int)count) : OptionalInt.empty();
    }
}
