package emu.grasscutter.server.http.dispatch;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Actual loopback HTTP/default authentication/synchronous Account save on a fresh Mongo UUID DB. */
class ComboLoginRoutesMongoTest {
    private ComboHttpMongoHarness fixture;

    @BeforeEach
    void open() throws Exception {
        fixture = ComboHttpMongoHarness.open();
    }

    @AfterEach
    void close() throws Exception {
        if (fixture != null) fixture.close();
    }

    @Test
    void protectedNativeShapeAcceptsStringOuterFieldsAnd64BitNestedUid() throws Exception {
        fixture.exercise("success", ComboHttpMongoHarness.request(
                ComboHttpMongoHarness.UID, fixture.sessionToken, true), true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"wrong_token", "empty_token", "missing_token"})
    void protectedCredentialsFailWithoutTokenOrBsonChange(String label) throws Exception {
        String token = label.equals("wrong_token") ? "owned-unmatched-session" : "";
        fixture.exercise(label, ComboHttpMongoHarness.request(
                ComboHttpMongoHarness.UID, token, !label.equals("missing_token")), false);
    }

    @Test
    void unknown64BitUidDoesNotCreateAccountOrIssueToken() throws Exception {
        fixture.exercise("unknown_uid", ComboHttpMongoHarness.request(
                ComboHttpMongoHarness.UNKNOWN_UID, fixture.sessionToken, true), false);
    }

    @Test
    void repeatedSuccessPreservesSessionAndUsesExistingLoginTokenRotation() throws Exception {
        String request = ComboHttpMongoHarness.request(ComboHttpMongoHarness.UID, fixture.sessionToken, true);
        fixture.exercise("success", request, true);
        fixture.exercise("repeat_success", request, true);
    }
}
