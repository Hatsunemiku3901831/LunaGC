package io.grasscutter;

import com.google.gson.Gson;

import emu.grasscutter.data.excels.quest.QuestData;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache immutable, uninitialized resource snapshots; every fixture receives fresh quest objects.
 */
final class QuestFixtureResources {
    private static final Gson GSON = new Gson();

    private record Key(Path path, FileTime modified, long size, Set<Integer> parents) {}

    private static final Map<Key, String> snapshots = new ConcurrentHashMap<>();

    static QuestData[] quests(Path source, Set<Integer> parents) throws IOException {
        Path path = source.toRealPath();
        var key =
                new Key(
                        path,
                        Files.getLastModifiedTime(path),
                        Files.size(path),
                        Set.copyOf(parents));
        String snapshot;
        try {
            snapshot =
                    snapshots.computeIfAbsent(
                            key,
                            ignored -> {
                                try {
                                    var all =
                                            GSON.fromJson(
                                                    Files.readString(path), QuestData[].class);
                                    return GSON.toJson(
                                            Arrays.stream(all)
                                                    .filter(
                                                            row ->
                                                                    key.parents()
                                                                            .contains(
                                                                                    row
                                                                                            .getMainId()))
                                                    .toArray(QuestData[]::new));
                                } catch (IOException error) {
                                    throw new UncheckedIOException(error);
                                }
                            });
        } catch (UncheckedIOException error) {
            throw error.getCause();
        }
        return GSON.fromJson(snapshot, QuestData[].class);
    }
}
