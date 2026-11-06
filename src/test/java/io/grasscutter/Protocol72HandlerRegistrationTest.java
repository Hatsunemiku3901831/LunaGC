package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.packet.NoResponseExpected;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.server.game.GameServerPacketHandler;
import emu.grasscutter.server.game.GameSession;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Public registration failures must reach the caller and leave the original dispatch intact. */
public class Protocol72HandlerRegistrationTest {
    private GameServerPacketHandler registry;
    private RecordingSession session;

    @BeforeEach
    void prepareRegistry() {
        registry = new GameServerPacketHandler(FixtureHandler.class);
        session = new RecordingSession();
        session.setState(GameSession.SessionState.ACTIVE);
    }

    @Test
    void singleRegistrationDispatchesOriginalHandlerAndTracksNoResponse() throws Exception {
        registry.registerPacketHandler(OriginalHandler.class);

        registry.handle(session, 65535, new byte[0], new byte[] {8, 1});

        assertEquals(List.of(OriginalHandler.class), session.invoked);
        assertTrue(noResponse().contains(65535));
        assertEquals(OriginalHandler.class, handlers().get(65535).getClass());
    }

    @Test
    void conflictingHandlerExplicitlyFailsWithoutReplacingOriginalOrNoResponse() throws Exception {
        registry.registerPacketHandler(OriginalHandler.class);
        PacketHandler original = handlers().get(65535);

        var failure = assertThrows(IllegalStateException.class,
                () -> registry.registerPacketHandler(ConflictingHandler.class));

        assertTrue(failure.getMessage().contains("65535"));
        assertTrue(failure.getMessage().contains(OriginalHandler.class.getName()));
        assertTrue(failure.getMessage().contains(ConflictingHandler.class.getName()));
        assertSame(original, handlers().get(65535));
        assertTrue(noResponse().contains(65535));
        registry.handle(session, 65535, new byte[0], new byte[] {8, 1});
        assertEquals(List.of(OriginalHandler.class), session.invoked);
    }

    @Test
    void duplicateSameHandlerAlsoFailsInsteadOfInventingAnAliasRule() throws Exception {
        registry.registerPacketHandler(OriginalHandler.class);
        PacketHandler original = handlers().get(65535);

        assertThrows(IllegalStateException.class,
                () -> registry.registerPacketHandler(OriginalHandler.class));

        assertSame(original, handlers().get(65535));
        assertTrue(noResponse().contains(65535));
    }

    @Test
    void rejectedNoResponseConflictDoesNotChangeOriginalResponsePolicy() throws Exception {
        registry.registerPacketHandler(ReplyHandler.class);
        PacketHandler original = handlers().get(32768);

        assertThrows(IllegalStateException.class,
                () -> registry.registerPacketHandler(SilentConflictHandler.class));

        assertSame(original, handlers().get(32768));
        assertFalse(noResponse().contains(32768));
        registry.handle(session, 32768, new byte[0], new byte[] {8, 1});
        assertEquals(List.of(ReplyHandler.class), session.invoked);
    }

    @Test
    void outOfRangePositiveOpcodeExplicitlyFailsBeforeRegistration() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> registry.registerPacketHandler(OutOfRangeHandler.class));

        assertFalse(handlers().containsKey(65536));
        assertFalse(noResponse().contains(65536));
    }

    @Test
    void disabledNonpositiveAndUnannotatedHandlersStayUnregistered() throws Exception {
        registry.registerPacketHandler(DisabledHandler.class);
        registry.registerPacketHandler(NegativeHandler.class);
        registry.registerPacketHandler(ZeroHandler.class);
        registry.registerPacketHandler(UnannotatedHandler.class);

        assertTrue(handlers().isEmpty());
        assertTrue(noResponse().isEmpty());
    }

    @Test
    void productionScanRegistersEveryEnabledHandlerWithoutCollisionsOrSilentLoss() throws Exception {
        var classes = Grasscutter.reflector.getSubTypesOf(PacketHandler.class);
        var expected = classes.stream().filter(handlerClass -> {
            var opcode = handlerClass.getAnnotation(Opcodes.class);
            return opcode != null && !opcode.disabled() && opcode.value() > 0;
        }).toList();

        registry = assertDoesNotThrow(() -> new GameServerPacketHandler(PacketHandler.class));

        assertFalse(expected.isEmpty(), "production scan must actually discover enabled handlers");
        assertEquals(expected.size(), handlers().size());
        for (var handlerClass : expected) {
            int opcode = handlerClass.getAnnotation(Opcodes.class).value();
            assertNotNull(handlers().get(opcode), handlerClass.getName());
            assertEquals(handlerClass, handlers().get(opcode).getClass());
            assertEquals(handlerClass.isAnnotationPresent(NoResponseExpected.class),
                    noResponse().contains(opcode), handlerClass.getName());
        }
        System.out.printf("Production Handler scan: discovered=%d enabled=%d registered=%d collisions=0%n",
                classes.size(), expected.size(), handlers().size());
    }

    @SuppressWarnings("unchecked")
    private Int2ObjectMap<PacketHandler> handlers() throws Exception {
        var field = GameServerPacketHandler.class.getDeclaredField("handlers");
        field.setAccessible(true);
        return (Int2ObjectMap<PacketHandler>) field.get(registry);
    }

    private IntSet noResponse() throws Exception {
        var field = GameServerPacketHandler.class.getDeclaredField("noResponseOpcodes");
        field.setAccessible(true);
        return (IntSet) field.get(registry);
    }

    private static final class RecordingSession extends GameSession {
        private final List<Class<?>> invoked = new ArrayList<>();
        private RecordingSession() { super(null); }
    }

    public abstract static class FixtureHandler extends PacketHandler {
        @Override
        public void handle(GameSession session, byte[] header, byte[] payload) {
            ((RecordingSession) session).invoked.add(getClass());
        }
    }

    @NoResponseExpected @Opcodes(65535)
    public static final class OriginalHandler extends FixtureHandler {}
    @Opcodes(65535) public static final class ConflictingHandler extends FixtureHandler {}
    @Opcodes(32768) public static final class ReplyHandler extends FixtureHandler {}
    @NoResponseExpected @Opcodes(32768)
    public static final class SilentConflictHandler extends FixtureHandler {}
    @NoResponseExpected @Opcodes(65536)
    public static final class OutOfRangeHandler extends FixtureHandler {}
    @Opcodes(value = 32767, disabled = true)
    public static final class DisabledHandler extends FixtureHandler {}
    @Opcodes(-1) public static final class NegativeHandler extends FixtureHandler {}
    @Opcodes(0) public static final class ZeroHandler extends FixtureHandler {}
    public static final class UnannotatedHandler extends FixtureHandler {}
}
