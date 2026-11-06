package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.InvalidProtocolBufferException;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.config.Configuration;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.recv.HandlerGetPlayerTokenReq;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Entry-only failures: no Mongo, authenticated preparation, or live client is used. */
class TokenNegotiationFailureTest {
    private boolean oldXor;

    @BeforeEach
    void enableEncryptedTransport() {
        Grasscutter.getConfig();
        oldXor = Configuration.GAME_INFO.useXorEncryption;
        Configuration.GAME_INFO.useXorEncryption = true;
    }

    @AfterEach
    void restoreTransport() {
        Configuration.GAME_INFO.useXorEncryption = oldXor;
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "804a00", "804affffffff0f"})
    void missingZeroOrUnsupportedUnsignedKeyRefusesBeforeAccountAdmission(String keyWire) throws Exception {
        var session = new EntrySession();
        // Independent target tags: account_token=2, account_uid=9,
        // client_rand_key=623, key_id=1184. Nonempty credentials ensure a
        // regression entering AccountLoginAdmission reaches its session check.
        byte[] request = hex("1203746f6b4a0461636374fa26022525" + keyWire);

        new HandlerGetPlayerTokenReq().handle(session, new byte[0], request);

        assertEquals(1, session.sent.size());
        var refusal = session.sent.get(0);
        assertEquals(PacketOpcodes.GetPlayerTokenRsp, refusal.getOpcode());
        assertArrayEquals(hex("70ffffffffffffffffff01"), refusal.getData());
        assertTrue(refusal.useDispatchKey());
        assertEquals(GameSession.SessionState.WAITING_FOR_TOKEN, session.getState());
        assertFalse(session.useSecretKey());
        assertNull(session.getAccount());
        assertNull(session.getPlayer());
    }

    @ParameterizedTest
    @ValueSource(strings = {"4a03", "804a80", "fa2602ff"})
    void malformedProtoFailsParsingWithoutReplyOrAccountPreparation(String wire) {
        var session = new EntrySession();

        assertThrows(InvalidProtocolBufferException.class,
                () -> new HandlerGetPlayerTokenReq().handle(session, new byte[0], hex(wire)));

        assertTrue(session.sent.isEmpty());
        assertEquals(GameSession.SessionState.WAITING_FOR_TOKEN, session.getState());
        assertFalse(session.useSecretKey());
        assertNull(session.getAccount());
        assertNull(session.getPlayer());
    }

    private static final class EntrySession extends GameSession {
        private final List<BasePacket> sent = new ArrayList<>();

        private EntrySession() {
            super(null);
        }

        @Override
        public void send(BasePacket packet) {
            sent.add(packet);
        }

        @Override
        public boolean isLoginRevoked() {
            throw new AssertionError("Rejected negotiation entered AccountLoginAdmission");
        }

        @Override
        public GameServer getServer() {
            throw new AssertionError("Rejected negotiation entered authenticated player preparation");
        }

        @Override
        public void setAccount(Account account) {
            throw new AssertionError("Rejected negotiation installed an account");
        }

        @Override
        public void setPlayer(Player player) {
            throw new AssertionError("Rejected negotiation created or loaded a player");
        }

        @Override
        public void close() {
            throw new AssertionError("Entry refusal unexpectedly entered admission cleanup");
        }
    }

    private static byte[] hex(String value) {
        return HexFormat.of().parseHex(value);
    }
}
