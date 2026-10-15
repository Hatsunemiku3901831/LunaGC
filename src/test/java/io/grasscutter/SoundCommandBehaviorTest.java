package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.commands.SoundCommand;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.ScenePlayerSoundNotifyOuterClass.ScenePlayerSoundNotify;
import emu.grasscutter.server.event.EventHandler;
import emu.grasscutter.server.event.game.ReceiveCommandFeedbackEvent;
import emu.grasscutter.server.packet.send.PacketScenePlayerSoundNotify;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

class SoundCommandBehaviorTest {
    NatsaurusPossessionTest env;
    final List<String> messages = new ArrayList<>();
    String prefix;

    @BeforeEach
    void setup() throws Exception {
        env = new NatsaurusPossessionTest();
        env.setup();
        env.fixture
                .player
                .getSession()
                .setState(emu.grasscutter.server.game.GameSession.SessionState.ACTIVE);
        prefix = "sound_test_" + UUID.randomUUID().toString().replace("-", "");
        Grasscutter.getPluginManager()
                .registerListener(
                        new EventHandler<>(ReceiveCommandFeedbackEvent.class)
                                .listener(
                                        event -> {
                                            messages.add(event.getMessage());
                                            event.cancel();
                                        }));
    }

    @AfterEach
    void close() {
        env.close();
    }

    void observe(String name) {
        new PacketScenePlayerSoundNotify(new Position(), name, 1);
    }

    List<String> rows() {
        return messages.stream().map(String::trim).filter(s -> s.startsWith(prefix)).toList();
    }

    @ParameterizedTest
    @ValueSource(strings = {"NaN", "Infinity", "-Infinity"})
    void nonFiniteCoordinatesNeverReachTheScene(String coordinate) {
        new SoundCommand().execute(null, env.fixture.player, List.of(prefix, coordinate, "2", "3"));
        assertTrue(
                env.fixture.scene.packets.stream()
                        .noneMatch(p -> p.getOpcode() == PacketOpcodes.ScenePlayerSoundNotify));
        assertFalse(messages.isEmpty());
    }

    @Test
    void finiteCoordinatesStillReachTheScene() throws Exception {
        new SoundCommand().execute(null, env.fixture.player, List.of(prefix, "1", "-2", "3.5"));
        var packet =
                env.fixture.scene.packets.stream()
                        .filter(p -> p.getOpcode() == PacketOpcodes.ScenePlayerSoundNotify)
                        .findFirst()
                        .orElseThrow();
        var sound = ScenePlayerSoundNotify.parseFrom(packet.getData());
        assertEquals(prefix, sound.getSoundName());
        assertEquals(1, sound.getPlayPos().getX());
        assertEquals(-2, sound.getPlayPos().getY());
        assertEquals(3.5, sound.getPlayPos().getZ());
    }

    @Test
    void listCanRunFromConsoleWithoutAPlayer() {
        observe(prefix + "_one");
        assertDoesNotThrow(() -> new SoundCommand().execute(null, null, List.of("list", prefix)));
        assertEquals(List.of(prefix + "_one"), rows());
        assertTrue(
                env.fixture.scene.packets.stream()
                        .noneMatch(p -> p.getOpcode() == PacketOpcodes.ScenePlayerSoundNotify));
    }

    @Test
    void observedNamesAreDeduplicatedAndFilteredWithoutCaseSensitivity() {
        observe(prefix + "_one");
        observe((prefix + "_ONE").toUpperCase(Locale.ROOT));
        observe("unrelated_cue");
        new SoundCommand().execute(null, null, List.of("list", prefix.toUpperCase(Locale.ROOT)));
        assertEquals(List.of(prefix + "_one"), rows());
    }

    @Test
    void pageTwoContainsOnlyTheLastFiveObservedNames() {
        for (int i = 0; i < 25; i++) observe(prefix + String.format(Locale.ROOT, "_%02d", i));
        new SoundCommand().execute(null, null, List.of("list", prefix, "--page", "2"));
        assertEquals(
                java.util.stream.IntStream.range(20, 25)
                        .mapToObj(i -> prefix + String.format(Locale.ROOT, "_%02d", i))
                        .toList(),
                rows());
    }

    @Test
    void invalidPageAndExtraArgumentsNeverPlayASound() {
        observe(prefix + "_one");
        for (var args :
                List.of(
                        List.of("list", prefix, "--page", "0"),
                        List.of("list", prefix, "--page", "x"),
                        List.of("list", prefix, "extra"),
                        List.of("list", prefix, "--page", "2147483647"))) {
            assertDoesNotThrow(() -> new SoundCommand().execute(null, env.fixture.player, args));
        }
        assertTrue(rows().isEmpty());
        assertTrue(
                env.fixture.scene.packets.stream()
                        .noneMatch(p -> p.getOpcode() == PacketOpcodes.ScenePlayerSoundNotify));
    }

    @Test
    void playWithoutATargetReturnsFeedbackInsteadOfThrowing() {
        assertDoesNotThrow(() -> new SoundCommand().execute(null, null, List.of(prefix)));
        assertFalse(messages.isEmpty());
    }

    @Test
    void actualCommandMapSupportsTheAudioAliasWithoutAnOnlinePlayer() {
        observe(prefix + "_one");
        var previous = Grasscutter.getPermissionHandler();
        try {
            Grasscutter.setPermissionHandler(
                    new emu.grasscutter.command.DefaultPermissionHandler());
            var map =
                    new emu.grasscutter.command.CommandMap(false)
                            .registerCommand("sound", new SoundCommand());
            map.invoke(null, null, "audio list " + prefix);
            assertEquals(List.of(prefix + "_one"), rows());
        } finally {
            Grasscutter.setPermissionHandler(previous);
        }
    }

    @Test
    void directoryStillPassesThroughCommandPermissionChecks() {
        observe(prefix + "_one");
        var previous = Grasscutter.getPermissionHandler();
        var checks = new ArrayList<String>();
        try {
            Grasscutter.setPermissionHandler(
                    new emu.grasscutter.command.PermissionHandler() {
                        @Override
                        public boolean EnablePermissionCommand() {
                            return true;
                        }

                        @Override
                        public boolean checkPermission(
                                emu.grasscutter.game.player.Player sender,
                                emu.grasscutter.game.player.Player target,
                                String normal,
                                String targeted) {
                            checks.add(normal);
                            checks.add(targeted);
                            return false;
                        }
                    });
            var map =
                    new emu.grasscutter.command.CommandMap(false)
                            .registerCommand("sound", new SoundCommand());
            map.invoke(null, null, "sound list " + prefix);
            assertEquals(List.of("player.sound", "player.sound.others"), checks);
            assertTrue(rows().isEmpty());
        } finally {
            Grasscutter.setPermissionHandler(previous);
        }
    }

    @Test
    void pageOptionMayPrecedeTheFilterAndUseEqualsSyntax() {
        for (int i = 0; i < 25; i++) observe(prefix + String.format(Locale.ROOT, "_%02d", i));
        new SoundCommand().execute(null, null, List.of("list", "--page=2", prefix));
        assertEquals(5, rows().size());
        assertEquals(prefix + "_20", rows().get(0));
    }
}
