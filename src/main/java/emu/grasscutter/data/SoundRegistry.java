package emu.grasscutter.data;

import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * Names observed in outgoing sound packets during this process, not an exhaustive sound catalog.
 */
public final class SoundRegistry {
    private static final ConcurrentSkipListSet<String> NAMES =
            new ConcurrentSkipListSet<>(String.CASE_INSENSITIVE_ORDER);

    private SoundRegistry() {}

    public static void observe(String soundName) {
        if (soundName != null && !soundName.isBlank()) NAMES.add(soundName);
    }

    public static List<String> names(String filter) {
        String query = filter == null ? "" : filter.toLowerCase(Locale.ROOT);
        return NAMES.stream()
                .filter(name -> name.toLowerCase(Locale.ROOT).contains(query))
                .toList();
    }

    public static final class Candidates implements Iterable<String> {
        @Override
        public Iterator<String> iterator() {
            return names(null).iterator();
        }
    }
}
