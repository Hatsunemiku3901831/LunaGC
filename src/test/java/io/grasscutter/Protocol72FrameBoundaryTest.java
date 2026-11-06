package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.Grasscutter.ServerDebugMode;
import emu.grasscutter.Grasscutter.ServerRunMode;
import emu.grasscutter.config.Configuration;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.plugin.PluginManager;
import emu.grasscutter.server.event.EventHandler;
import emu.grasscutter.server.event.HandlerPriority;
import emu.grasscutter.server.event.game.SendPacketEvent;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.server.game.GameServerPacketHandler;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.game.GameSessionManager;
import emu.grasscutter.utils.FileUtils;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Real transport/dispatch entry points, with no socket listener, player, resource loader or DB. */
public class Protocol72FrameBoundaryTest {
    private ServerRunMode oldMode;
    private ServerDebugMode oldLogging;
    private boolean oldEncryption;
    private Field pluginField;
    private Object oldPlugins;
    private RecordingSession session;
    private final ListAppender<ILoggingEvent> log = new ListAppender<>();

    @BeforeEach
    void prepareEntryPoints() throws Exception {
        Grasscutter.getConfig();
        oldMode = Grasscutter.getRunMode();
        oldEncryption = Configuration.GAME_INFO.useXorEncryption;
        oldLogging = Configuration.GAME_INFO.logPackets;
        Grasscutter.setRunModeOverride(ServerRunMode.DISPATCH_ONLY);
        Configuration.GAME_INFO.useXorEncryption = false;
        Configuration.GAME_INFO.logPackets = ServerDebugMode.NONE;
        pluginField = Grasscutter.class.getDeclaredField("pluginManager");
        pluginField.setAccessible(true);
        oldPlugins = pluginField.get(null);
        pluginField.set(null, null);

        // DISPATCH_ONLY returns before game systems or listeners are created. Install only the
        // fixture registry; the actual GameSession and GameServerPacketHandler execute unchanged.
        var server = new GameServer();
        var registry = new GameServerPacketHandler(FixtureHandler.class);
        registry.registerPacketHandler(LowHandler.class);
        registry.registerPacketHandler(HighHandler.class);
        registry.registerPacketHandler(MaxHandler.class);
        var registryField = GameServer.class.getDeclaredField("packetHandler");
        registryField.setAccessible(true);
        registryField.set(server, registry);
        session = new RecordingSession(server);
        session.setState(GameSession.SessionState.ACTIVE);
        session.onConnected(new RecordingTunnel(session.written));
        log.start();
        Grasscutter.getLogger().addAppender(log);
    }

    @AfterEach
    void restoreEntryPoints() throws Exception {
        Grasscutter.getLogger().detachAppender(log);
        log.stop();
        pluginField.set(null, oldPlugins);
        Configuration.GAME_INFO.logPackets = oldLogging;
        Configuration.GAME_INFO.useXorEncryption = oldEncryption;
        Grasscutter.setRunModeOverride(oldMode);
    }

    @ParameterizedTest
    @ValueSource(ints = {32767, 32768, 65535})
    void unsignedOpcodeReachesRegisteredHandlerWithExactBytes(int opcode) {
        // Independent target PacketHead tags 3/u32 and 6/u64, including a nonzero high 64-bit value.
        byte[] header = hex("18ffffffff0f30ffffffffffffffffff01");
        byte[] body = hex("0896011203010203");

        session.handleReceive(frame(opcode, header, body));

        assertEquals(1, session.received.size(), "uint16 opcode must reach its positive registry key");
        assertReceived(0, opcode, header, body);
        assertNoCaughtTransportFailure();
    }

    @ParameterizedTest
    @ValueSource(ints = {32767, 32768, 65535})
    void unsignedHeaderLengthAndU32BodyReachHandlerWithoutTruncation(int size) {
        byte[] header = pattern(size);
        byte[] body = pattern(65536);

        session.handleReceive(frame(32767, header, body));

        assertEquals(1, session.received.size(), "header length is unsigned uint16");
        assertReceived(0, 32767, header, body);
        assertNoCaughtTransportFailure();
    }

    @Test
    void aggregateDispatchesEachLegalFrameInOrder() {
        byte[] firstHeader = hex("1801308180808010");
        byte[] firstBody = hex("0801");
        byte[] secondHeader = hex("18ffffffff0f");
        byte[] secondBody = hex("120200ff");
        byte[] first = frame(32767, firstHeader, firstBody);
        byte[] second = frame(65535, secondHeader, secondBody);
        byte[] aggregate = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, aggregate, first.length, second.length);

        session.handleReceive(aggregate);

        assertEquals(2, session.received.size());
        assertReceived(0, 32767, firstHeader, firstBody);
        assertReceived(1, 65535, secondHeader, secondBody);
        assertNoCaughtTransportFailure();
    }

    static Stream<Arguments> malformedFrames() {
        byte[] valid = frame(32767, hex("1801"), hex("0801"));
        byte[] badStart = valid.clone();
        badStart[0] = 0;
        byte[] badEnd = valid.clone();
        badEnd[badEnd.length - 1] ^= 1;
        return Stream.of(
                Arguments.of("empty", new byte[0]),
                Arguments.of("short fixed header", Arrays.copyOf(valid, 11)),
                Arguments.of("truncated header", declaredFrame(4, 0)),
                Arguments.of("truncated body", declaredFrame(0, 4)),
                Arguments.of("negative body", declaredFrame(0, -1)),
                // Bounded red reproducer: old code allocates 1 MiB before discovering 2 bytes left.
                Arguments.of("large declared body", declaredFrame(0, 1024 * 1024)),
                // Added after the bounded red was preserved and the preallocation guard installed.
                Arguments.of("maximum signed body", declaredFrame(0, Integer.MAX_VALUE)),
                Arguments.of("combined declared length overflows int",
                        declaredFrame(65535, Integer.MAX_VALUE)),
                Arguments.of("high header declared without bytes", declaredFrame(65535, 0)),
                Arguments.of("missing trailer", Arrays.copyOf(valid, valid.length - 1)),
                Arguments.of("bad start", badStart),
                Arguments.of("bad trailer", badEnd));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("malformedFrames")
    void malformedFrameRejectsBeforeAllocationReadFailureOrBusinessDispatch(String name, byte[] wire) {
        assertDoesNotThrow(() -> session.handleReceive(wire), name);

        assertTrue(session.received.isEmpty(), name + " must not invoke a handler");
        assertNoCaughtTransportFailure();
    }

    @Test
    void malformedFirstFrameCannotDispatchFollowingFrame() {
        byte[] bad = frame(32767, hex("1801"), hex("0801"));
        bad[bad.length - 1] ^= 1;
        byte[] next = frame(32767, new byte[0], hex("0802"));
        byte[] wire = Arrays.copyOf(bad, bad.length + next.length);
        System.arraycopy(next, 0, wire, bad.length, next.length);

        session.handleReceive(wire);

        assertTrue(session.received.isEmpty(), "bad frame stops this aggregate without resynchronizing");
        assertNoCaughtTransportFailure();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 32767, 32768, 65535})
    void encoderMatchesIndependentUnsignedEnvelope(int opcode) {
        byte[] header = pattern(32768);
        byte[] body = pattern(65536);
        var packet = packet(opcode, header, body);

        assertArrayEquals(frame(opcode, header, body), packet.build());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 65536, 65537, Integer.MAX_VALUE})
    void encoderRejectsOpcodeOutsideUnsigned16InsteadOfTruncating(int opcode) {
        var packet = packet(opcode, hex("1801"), hex("0801"));

        assertThrows(IllegalArgumentException.class, packet::build);
    }

    @Test
    void encoderRejectsHeaderOutsideUnsigned16InsteadOfTruncating() {
        var packet = packet(32767, pattern(65536), hex("0801"));

        assertThrows(IllegalArgumentException.class, packet::build);
    }

    @ParameterizedTest
    @ValueSource(ints = {32767, 32768, 65535})
    void sendUsesActualEventReplacementEnvelope(int opcode) throws Exception {
        byte[] header = hex("1807308180808010");
        byte[] body = hex("0807");
        replaceOnSend(packet(opcode, header, body));

        session.send(packet(32767, new byte[0], new byte[0]));

        assertEquals(1, session.written.size());
        assertArrayEquals(frame(opcode, header, body), session.written.get(0));
        assertEquals(1, session.getPacketsSent());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 65536, 65537})
    void sendRejectsOutOfRangeActualEventReplacement(int opcode) throws Exception {
        replaceOnSend(packet(opcode, hex("1801"), hex("0801")));

        session.send(packet(32767, new byte[0], new byte[0]));

        assertTrue(session.written.isEmpty(), "event replacement must not truncate into a valid opcode");
        assertEquals(0, session.getPacketsSent());
    }

    @Test
    void sendRejectsOversizeHeaderInActualEventReplacement() throws Exception {
        replaceOnSend(packet(32767, pattern(65536), hex("0801")));

        session.send(packet(32767, new byte[0], new byte[0]));

        assertTrue(session.written.isEmpty());
        assertEquals(0, session.getPacketsSent());
    }

    private void replaceOnSend(BasePacket replacement) throws Exception {
        var pluginPath = FileUtils.getPluginPath("");
        if (Files.isDirectory(pluginPath)) {
            try (var files = Files.list(pluginPath)) {
                assertTrue(files.noneMatch(path -> path.toString().endsWith(".jar")),
                        "entry fixture must not load external plugins");
            }
        }
        var plugins = new PluginManager();
        pluginField.set(null, plugins);
        plugins.registerListener(new EventHandler<>(SendPacketEvent.class)
                .priority(HandlerPriority.NORMAL)
                .listener(event -> event.setPacket(replacement)));
    }

    private void assertReceived(int index, int opcode, byte[] header, byte[] body) {
        var received = session.received.get(index);
        assertEquals(opcode, received.opcode());
        assertArrayEquals(header, received.header());
        assertArrayEquals(body, received.body());
    }

    private void assertNoCaughtTransportFailure() {
        assertTrue(log.list.stream().noneMatch(event -> event.getThrowableProxy() != null),
                () -> "invalid length must be rejected before allocation/read, rather than caught: "
                        + log.list.stream().filter(event -> event.getThrowableProxy() != null)
                                .map(event -> event.getThrowableProxy().getClassName()).toList());
    }

    private static BasePacket packet(int opcode, byte[] header, byte[] body) {
        var packet = new BasePacket(opcode);
        packet.shouldEncrypt = false;
        packet.setHeader(header);
        packet.setData(body);
        return packet;
    }

    // This oracle is independent of BasePacket.build/writeUint16 and the receive implementation.
    private static byte[] frame(int opcode, byte[] header, byte[] body) {
        return ByteBuffer.allocate(12 + header.length + body.length)
                .putShort((short) 0x4567).putShort((short) opcode)
                .putShort((short) header.length).putInt(body.length)
                .put(header).put(body).putShort((short) 0x89ab).array();
    }

    private static byte[] declaredFrame(int headerLength, int bodyLength) {
        return ByteBuffer.allocate(12).putShort((short) 0x4567).putShort((short) 32767)
                .putShort((short) headerLength).putInt(bodyLength).putShort((short) 0x89ab).array();
    }

    private static byte[] pattern(int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) bytes[i] = (byte) (i * 37 + 11);
        return bytes;
    }

    private static byte[] hex(String value) {
        return HexFormat.of().parseHex(value);
    }

    private record Received(int opcode, byte[] header, byte[] body) {}

    private static final class RecordingSession extends GameSession {
        private final List<Received> received = new ArrayList<>();
        private final List<byte[]> written = new ArrayList<>();

        private RecordingSession(GameServer server) {
            super(server);
        }
    }

    private record RecordingTunnel(List<byte[]> frames) implements GameSessionManager.KcpTunnel {
        @Override public InetSocketAddress getAddress() { return new InetSocketAddress("127.0.0.1", 1); }
        @Override public void writeData(byte[] bytes) { frames.add(bytes); }
        @Override public void close() {}
        @Override public int getSrtt() { return 0; }
    }

    // The reflector scans emu.grasscutter only, so this base deliberately has no auto-registered
    // production or fixture descendants. The three concrete handlers are registered explicitly.
    public abstract static class FixtureHandler extends PacketHandler {
        @Override
        public void handle(GameSession session, byte[] header, byte[] payload) {
            int opcode = getClass().getAnnotation(Opcodes.class).value();
            ((RecordingSession) session).received.add(new Received(opcode, header, payload));
        }
    }

    @Opcodes(32767) public static final class LowHandler extends FixtureHandler {}
    @Opcodes(32768) public static final class HighHandler extends FixtureHandler {}
    @Opcodes(65535) public static final class MaxHandler extends FixtureHandler {}
}
