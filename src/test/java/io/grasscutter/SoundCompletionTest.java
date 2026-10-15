package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.command.*;
import emu.grasscutter.command.commands.SoundCommand;
import emu.grasscutter.data.SoundRegistry;

import org.jline.reader.*;
import org.jline.reader.impl.DefaultParser;
import org.junit.jupiter.api.Test;

import java.util.*;

class SoundCompletionTest {
    final CommandMap map = new CommandMap(false).registerCommand("sound", new SoundCommand());
    final Completer completer = new ConsoleCommandCompleter(() -> map);

    List<String> complete(String value) {
        var line = new DefaultParser().parse(value, value.length(), Parser.ParseContext.COMPLETE);
        var candidates = new ArrayList<Candidate>();
        completer.complete(null, line, candidates);
        return candidates.stream().map(Candidate::value).toList();
    }

    @Test
    void commandAndAudioAliasAreBothAvailable() {
        assertEquals(List.of("audio"), complete("aud"));
        assertEquals(List.of("sound"), complete("sou"));
        assertTrue(complete("sound li").contains("list"));
    }

    @Test
    void soundsObservedAfterConstructionBecomeLiveCandidates() {
        String cue = "sound_completion_" + UUID.randomUUID().toString().replace("-", "");
        assertTrue(complete("sound " + cue).isEmpty());
        SoundRegistry.observe(cue);
        assertEquals(List.of(cue), complete("sound " + cue.toUpperCase(Locale.ROOT)));
        assertEquals(List.of(cue), complete("audio @10001 " + cue));
    }

    @Test
    void paginationDoesNotOfferSoundNamesForPageNumbersOrCoordinates() {
        assertEquals(List.of("--page"), complete("sound list cue --p"));
        assertTrue(complete("sound list --page ").isEmpty());
        assertTrue(complete("sound play_cue 1 ").isEmpty());
        assertTrue(complete("audio @100").isEmpty());
    }

    @Test
    void unavailableCommandMapIsSafeDuringStartup() {
        var line = new DefaultParser().parse("sou", 3, Parser.ParseContext.COMPLETE);
        var candidates = new ArrayList<Candidate>();
        assertDoesNotThrow(
                () -> new ConsoleCommandCompleter(() -> null).complete(null, line, candidates));
        assertTrue(candidates.isEmpty());
    }
}
