package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import emu.grasscutter.data.excels.quest.QuestData;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;

class QuestFixtureResourcesTest {
    @TempDir Path temp;
    final Gson gson = new Gson();

    @Test
    void eachFixtureGetsIndependentRowsAndNestedValues() throws Exception {
        var file = temp.resolve("quests.json");
        Files.writeString(
                file, "[{subId:101,mainId:10,trialAvatarList:[10000001]},{subId:201,mainId:20}]");
        var first = QuestFixtureResources.quests(file, Set.of(10));
        assertEquals(1, first.length);
        first[0].getTrialAvatarList().set(0, 99);
        var id = QuestData.class.getDeclaredField("subId");
        id.setAccessible(true);
        id.set(first[0], 999);
        var second = QuestFixtureResources.quests(file, Set.of(10));
        assertEquals(101, second[0].getId());
        assertEquals(List.of(10000001), second[0].getTrialAvatarList());
        assertNotSame(first[0], second[0]);
        assertEquals(201, QuestFixtureResources.quests(file, Set.of(20))[0].getId());
    }

    @Test
    void changingTheResourceFileInvalidatesTheSnapshot() throws Exception {
        var file = temp.resolve("quests.json");
        Files.writeString(file, "[{subId:101,mainId:10}]");
        assertEquals(101, QuestFixtureResources.quests(file, Set.of(10))[0].getId());
        var old = Files.getLastModifiedTime(file);
        Files.writeString(file, "[{subId:102,mainId:10}]");
        Files.setLastModifiedTime(file, FileTime.fromMillis(old.toMillis() + 2000));
        assertEquals(102, QuestFixtureResources.quests(file, Set.of(10))[0].getId());
    }

    @Test
    void currentResourceRowsAreEquivalentToTheOriginalFullTableRead() throws Exception {
        var path = Path.of("resources/ExcelBinOutput/QuestExcelConfigData.json");
        var parents = Set.of(11005, 11103, 19090, 309);
        var raw = Files.readString(path);
        long started = System.nanoTime();
        var expected =
                Arrays.stream(gson.fromJson(raw, QuestData[].class))
                        .filter(row -> parents.contains(row.getMainId()))
                        .toArray(QuestData[]::new);
        long fullNanos = System.nanoTime() - started;
        QuestFixtureResources.quests(path, parents);
        started = System.nanoTime();
        var actual = QuestFixtureResources.quests(path, parents);
        long cachedNanos = System.nanoTime() - started;
        assertTrue(expected.length > 0);
        assertEquals(gson.toJsonTree(expected), gson.toJsonTree(actual));
        System.out.println(
                "quest fixture snapshot rows="
                        + actual.length
                        + " originalBytes="
                        + raw.length()
                        + " snapshotBytes="
                        + gson.toJson(actual).length()
                        + " fullReadNanos="
                        + fullNanos
                        + " cachedReadNanos="
                        + cachedNanos);
    }
}
