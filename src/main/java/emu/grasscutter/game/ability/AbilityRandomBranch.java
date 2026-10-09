package emu.grasscutter.game.ability;

import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.actions.AbilityActionHandler;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/** The resource probability chooses one branch; invalid arithmetic chooses neither. */
public final class AbilityRandomBranch {
    private AbilityRandomBranch() {}

    public static Optional<Boolean> choose(Ability ability, AbilityModifierAction action) {
        if (ability == null || action == null || action.chance == null) return Optional.empty();
        var evaluated=action.chance.tryGet(AbilityActionHandler.propertiesFor(ability));
        if (evaluated.isEmpty()) return Optional.empty();
        double chance=evaluated.getAsDouble();
        if (chance<0 || chance>1) return Optional.empty();
        return Optional.of(ThreadLocalRandom.current().nextFloat()<chance);
    }
}
