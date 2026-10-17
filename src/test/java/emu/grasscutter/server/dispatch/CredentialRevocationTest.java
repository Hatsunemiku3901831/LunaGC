package emu.grasscutter.server.dispatch;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CredentialRevocationTest {
    @Test
    void aLargeRevisionRoundTripsWithoutFloatingPointRounding() {
        var request = new CredentialRevocation.Request("account", Long.MAX_VALUE);
        assertEquals(request, CredentialRevocation.decode(request.message()));
        assertDoesNotThrow(() -> request.confirm(request.response(true)));
        assertTrue(request.message().get("throughRevision").getAsJsonPrimitive().isString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "01", "+1", "1.0", "1e0", "9223372036854775808", ""})
    void invalidOrNoncanonicalRevisionsAreRejected(String revision) {
        var packet = new CredentialRevocation.Request("a", 1).message();
        packet.addProperty("throughRevision", revision);
        assertThrows(IllegalArgumentException.class, () -> CredentialRevocation.decode(packet));
    }

    @Test
    void numbersAndExtraFieldsAreNotAcceptedAsRevisionStrings() {
        var packet = new CredentialRevocation.Request("a", 1).message();
        packet.addProperty("throughRevision", 1);
        assertThrows(IllegalArgumentException.class, () -> CredentialRevocation.decode(packet));
        packet.addProperty("throughRevision", "1");
        packet.addProperty("extra", true);
        assertThrows(IllegalArgumentException.class, () -> CredentialRevocation.decode(packet));
    }

    @Test
    void aReplyMustConfirmTheSameAccountAndCutoffWithABoolean() {
        var request = new CredentialRevocation.Request("a", 1);
        assertThrows(IllegalStateException.class, () -> request.confirm(request.response(false)));
        var reply = request.response(true);
        reply.addProperty("ok", "true");
        assertThrows(IllegalStateException.class, () -> request.confirm(reply));
        assertThrows(
                IllegalStateException.class,
                () -> request.confirm(new CredentialRevocation.Request("b", 1).response(true)));
        assertThrows(
                IllegalStateException.class,
                () -> request.confirm(new CredentialRevocation.Request("a", 2).response(true)));
        assertThrows(IllegalStateException.class, () -> request.confirm(JsonNull.INSTANCE));
    }
}
