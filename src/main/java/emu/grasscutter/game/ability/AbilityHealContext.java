package emu.grasscutter.game.ability;

/** The immutable healing event tag follows an onHeal callback, including nested predicates. */
public final class AbilityHealContext {
    private static final ThreadLocal<String> tag = new ThreadLocal<>();

    private AbilityHealContext() {}

    public static String currentTag() {
        return tag.get();
    }

    public static void run(String healTag, Runnable callback) {
        String previous = tag.get();
        try {
            if (healTag == null) tag.remove();
            else tag.set(healTag);
            callback.run();
        } finally {
            if (previous == null) tag.remove();
            else tag.set(previous);
        }
    }
}
