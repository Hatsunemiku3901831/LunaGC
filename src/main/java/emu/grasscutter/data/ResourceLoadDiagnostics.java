package emu.grasscutter.data;

import emu.grasscutter.Grasscutter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Per-file failures, with bounded samples rather than one stack trace per rejected row. */
final class ResourceLoadDiagnostics {
    private final Class<?> type;
    private final Path path;
    private final List<String> samples = new ArrayList<>();
    private int rejected;

    ResourceLoadDiagnostics(Class<?> type, Path path) {
        this.type = type;
        this.path = path;
    }

    void reject(String location, Exception exception) {
        rejected++;
        if (samples.size() >= 5) return;
        samples.add(
                snippet(location)
                        + " ("
                        + exception.getClass().getSimpleName()
                        + ": "
                        + snippet(exception.getMessage())
                        + ")");
    }

    private static String snippet(String value) {
        if (value == null) return "";
        value = value.replace('\n', ' ').replace('\r', ' ');
        return value.length() > 200 ? value.substring(0, 200) + "..." : value;
    }

    void report() {
        if (rejected == 0) return;
        Grasscutter.getLogger()
                .warn(
                        "{} rejected {} entries from {}. First failures: {}",
                        type.getSimpleName(),
                        rejected,
                        path,
                        samples);
    }
}
