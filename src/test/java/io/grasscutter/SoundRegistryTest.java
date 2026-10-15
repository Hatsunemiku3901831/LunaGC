package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.SoundRegistry;
import emu.grasscutter.server.packet.send.PacketScenePlayerSoundNotify;

import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;

class SoundRegistryTest {
    @Test
    void blankNamesAreIgnoredAndSnapshotsCannotBeMutated() {
        int before = SoundRegistry.names(null).size();
        SoundRegistry.observe(null);
        SoundRegistry.observe("");
        SoundRegistry.observe("  ");
        assertEquals(before, SoundRegistry.names(null).size());
        assertThrows(
                UnsupportedOperationException.class,
                () -> SoundRegistry.names(null).add("invented"));
    }

    @Test
    void liveCandidateIteratorSeesNamesObservedAfterItsConstruction() {
        var candidates = new SoundRegistry.Candidates();
        var cue = "late_sound_" + UUID.randomUUID();
        SoundRegistry.observe(cue);
        var names = new ArrayList<String>();
        candidates.forEach(names::add);
        assertTrue(names.contains(cue));
    }

    @Test
    void concurrentObservationAndQueriesRemainSortedAndDeduplicated() throws Exception {
        var prefix = "parallel_sound_" + UUID.randomUUID();
        var executor = Executors.newFixedThreadPool(4);
        try {
            var jobs = new ArrayList<Future<?>>();
            for (int worker = 0; worker < 4; worker++)
                jobs.add(
                        executor.submit(
                                () -> {
                                    for (int i = 0; i < 50; i++) {
                                        SoundRegistry.observe(
                                                prefix + String.format(Locale.ROOT, "_%02d", i));
                                        assertDoesNotThrow(() -> SoundRegistry.names(prefix));
                                    }
                                }));
            for (var job : jobs) job.get(5, TimeUnit.SECONDS);
            var actual = SoundRegistry.names(prefix);
            assertEquals(50, actual.size());
            assertEquals(actual.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList(), actual);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectedPacketCannotAddANeverConstructedSoundToTheCatalog() {
        var cue = "rejected_sound_" + UUID.randomUUID();
        assertThrows(
                RuntimeException.class,
                () -> new PacketScenePlayerSoundNotify(null, cue, Integer.MAX_VALUE));
        assertTrue(SoundRegistry.names(cue).isEmpty());
    }
}
