package emu.grasscutter.game.ability;

import emu.grasscutter.Grasscutter;
import java.util.function.BooleanSupplier;

/** Nested callbacks share a work limit; every independent callback receives a fresh allowance. */
public final class AbilityActionBudget {
    private static final int MAX_STEPS=8192;
    private static final ThreadLocal<State> current=new ThreadLocal<>();
    private static final class State { int remaining=MAX_STEPS; boolean exhausted; }
    private AbilityActionBudget() {}

    public static boolean run(BooleanSupplier body) {
        var previous=current.get();
        if (previous!=null) return !previous.exhausted && body.getAsBoolean() && !previous.exhausted;
        var state=new State(); current.set(state);
        try { return body.getAsBoolean() && !state.exhausted; }
        finally {
            current.remove();
            if (state.exhausted) Grasscutter.getLogger().warn("Ability callback stopped after {} action steps",MAX_STEPS);
        }
    }

    public static boolean consume() {
        var state=current.get();
        if (state==null) return true;
        if (state.remaining>0) { state.remaining--; return true; }
        state.exhausted=true; return false;
    }
}
