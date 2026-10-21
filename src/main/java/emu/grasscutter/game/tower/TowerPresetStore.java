package emu.grasscutter.game.tower;

import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.*;

/** Atomic local settings, with optimistic concurrency for two open GM tabs. */
public final class TowerPresetStore {
    public record State(int schemaVersion, long revision, TowerBattleProfile profile) {
        public State {
            if (schemaVersion != 1 || revision < 0 || profile == null)
                throw new IllegalArgumentException("不支持的深渊配置格式。");
        }
    }

    private final Path path;
    private volatile State state;

    public TowerPresetStore(Path path) throws IOException {
        this.path = path;
        if (Files.exists(path)) {
            var value =
                    com.google.gson.JsonParser.parseString(Files.readString(path))
                            .getAsJsonObject();
            state =
                    new State(
                            value.get("schemaVersion").getAsBigDecimal().intValueExact(),
                            value.get("revision").getAsBigDecimal().longValueExact(),
                            TowerBattleProfile.fromJson(value.get("profile")));
        } else state = new State(1, 0, TowerBattleProfile.DEFAULT);
        if (state == null) throw new IllegalArgumentException("深渊配置不能为空。");
    }

    public State get() {
        return state;
    }

    public synchronized State save(long expectedRevision, TowerBattleProfile profile)
            throws IOException {
        if (state.revision() != expectedRevision)
            throw new IllegalStateException("配置已被其他窗口修改，请刷新后重试。");
        var next = new State(1, Math.addExact(state.revision(), 1), profile);
        Files.createDirectories(path.toAbsolutePath().getParent());
        var temp = Files.createTempFile(path.toAbsolutePath().getParent(), "abyss-", ".tmp");
        try {
            Files.writeString(
                    temp, new GsonBuilder().setPrettyPrinting().create().toJson(next) + "\n");
            // Failure leaves the previous state and file intact; never silently use a partial
            // write.
            Files.move(
                    temp,
                    path,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            state = next;
            return next;
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
