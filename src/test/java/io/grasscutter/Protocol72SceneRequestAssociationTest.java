package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.game.world.World;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.recv.*;
import java.util.HexFormat;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Actual handler entry: only the verified request token is interpreted. */
class Protocol72SceneRequestAssociationTest {
    private record Request(PacketHandler handler, long rawZeroToken, String zeroWire,
                           long adjacentToken, String adjacentWire) {}

    static Stream<Request> requests() {
        // Independent full native constructor -> writer observations. A zero
        // encoded token is omitted, including normal Done/Post scene tokens.
        return Stream.of(
            new Request(new HandlerEnterSceneReadyReq(), 4294925116L, "", 4294925117L, "18ffffffff0f"),
            new Request(new HandlerSceneInitFinishReq(), 4294922560L, "", 4294922561L, "58ffffffff0f"),
            new Request(new HandlerEnterSceneDoneReq(), 42557, "", 42558, "6001"),
            new Request(new HandlerPostEnterSceneReq(), 64180, "090100000000004043", 64181,
                "0901000000000040433001"));
    }

    @BeforeAll
    static void initializeConfiguration() { Grasscutter.getConfig(); }

    @ParameterizedTest
    @MethodSource("requests")
    void staleTokenCannotReachAnySceneMutationOrSend(Request request) {
        var session = new EntrySession((int) request.adjacentToken());
        assertThrows(IllegalArgumentException.class,
            () -> request.handler().handle(session, new byte[0], hex(request.zeroWire())));
        assertFalse(session.player.sceneReached);
    }

    @ParameterizedTest
    @MethodSource("requests")
    void nativeOmittedRawZeroTokenReachesTheExistingSceneFlow(Request request) {
        var session = new EntrySession((int) request.rawZeroToken());
        assertThrows(SceneFlowReached.class,
            () -> request.handler().handle(session, new byte[0], hex(request.zeroWire())));
        assertTrue(session.player.sceneReached);
    }

    @ParameterizedTest
    @MethodSource("requests")
    void nonzeroNativeNeighborAlsoAssociatesWithoutChangingThePlayerToken(Request request) {
        var session = new EntrySession((int) request.adjacentToken());
        assertThrows(SceneFlowReached.class,
            () -> request.handler().handle(session, new byte[0], hex(request.adjacentWire())));
        assertTrue(session.player.sceneReached);
        assertEquals((int) request.adjacentToken(), session.player.getEnterSceneToken());
    }

    private static final class SceneFlowReached extends RuntimeException {}

    private static final class EntryPlayer extends Player {
        private boolean sceneReached;
        private SceneFlowReached reach() { sceneReached = true; return new SceneFlowReached(); }
        @Override public World getWorld() { throw reach(); }
        @Override public Scene getScene() { throw reach(); }
        @Override public void setSceneLoadState(SceneLoadState state) { throw reach(); }
    }

    private static final class EntrySession extends GameSession {
        private final EntryPlayer player = new EntryPlayer();
        private EntrySession(int token) { super(null); player.setEnterSceneToken(token); }
        @Override public Player getPlayer() { return player; }
        @Override public void send(BasePacket packet) { throw player.reach(); }
    }

    private static byte[] hex(String text) { return HexFormat.of().parseHex(text); }
}
