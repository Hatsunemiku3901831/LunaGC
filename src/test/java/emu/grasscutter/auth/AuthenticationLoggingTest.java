package emu.grasscutter.auth;

import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.server.http.dispatch.AuthenticationHandler;
import emu.grasscutter.utils.RSADecryptionUtil;
import io.javalin.http.Context;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import javax.crypto.Cipher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AuthenticationLoggingTest {
    private static final String SYNTHETIC = "synthetic-auth-content-047215";
    private final ListAppender<ILoggingEvent> events = new ListAppender<>();
    private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    private PrintStream originalError;
    private Level originalLevel;

    @BeforeEach
    void capture() {
        var logger = Grasscutter.getLogger();
        originalLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        events.start();
        logger.addAppender(events);
        originalError = System.err;
        System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void restore() {
        System.setErr(originalError);
        var logger = Grasscutter.getLogger();
        logger.detachAppender(events);
        logger.setLevel(originalLevel);
        events.stop();
    }

    static Stream<Arguments> invalidBodies() {
        return Stream.of(
                Arguments.of("maPassportLogin", "{\"account\":{\"probe\":\"" + SYNTHETIC + "\"}}", 400),
                Arguments.of("maPassportVerify", "{\"token\":{\"token_type\":\"" + SYNTHETIC + "\"}}", 400),
                Arguments.of("maPassportMobileCaptchaLogin", "{\"mobile\":{\"probe\":\"" + SYNTHETIC + "\"}}", 400),
                Arguments.of("maPassportExchange", "{\"src_token\":{\"token\":\"" + SYNTHETIC
                        + "\"},\"dst_token_type\":\"" + SYNTHETIC + "\"}", 500));
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    void rejectedBodiesDoNotEnterLogsOrStderr(String handler, String body, int expectedStatus)
            throws Exception {
        var status = new AtomicInteger(200);
        Context context = (Context) Proxy.newProxyInstance(
                Context.class.getClassLoader(), new Class<?>[] {Context.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "body" -> body;
                    case "header" -> null;
                    case "ip" -> "127.0.0.1";
                    case "status" -> { status.set((Integer) arguments[0]); yield proxy; }
                    case "result", "json" -> proxy;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        var method = AuthenticationHandler.class.getDeclaredMethod(handler, Context.class);
        method.setAccessible(true);
        method.invoke(null, context);
        assertEquals(expectedStatus, status.get());
        assertNoContent(SYNTHETIC, body);
    }

    @Test
    void successfulRsaDecryptionKeepsReturnValueOutOfLogs() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024);
        var key = generator.generateKeyPair();
        var cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key.getPublic());
        byte[] encrypted = cipher.doFinal(SYNTHETIC.getBytes(StandardCharsets.UTF_8));
        var method = RSADecryptionUtil.class.getDeclaredMethod(
                "tryDecrypt", byte[].class, java.security.PrivateKey.class, String.class, String.class);
        method.setAccessible(true);
        assertEquals(SYNTHETIC, method.invoke(null, encrypted, key.getPrivate(),
                "RSA/ECB/PKCS1Padding", "synthetic-test-key"));
        assertNoContent(SYNTHETIC, Base64.getEncoder().encodeToString(encrypted));
    }

    @Test
    void existingPlaintextFallbackKeepsReturnValueOutOfLogs() throws Exception {
        String encoded = Base64.getEncoder().encodeToString(SYNTHETIC.getBytes(StandardCharsets.UTF_8));
        assertEquals(SYNTHETIC, RSADecryptionUtil.decrypt(encoded));
        assertNoContent(SYNTHETIC, encoded);
    }

    private void assertNoContent(String... forbidden) {
        var log = new StringBuilder(stderr.toString(StandardCharsets.UTF_8));
        for (var event : events.list) {
            log.append(event.getFormattedMessage());
            if (event.getThrowableProxy() != null) log.append(ThrowableProxyUtil.asString(event.getThrowableProxy()));
        }
        assertFalse(events.list.isEmpty(), "The real logger must be captured");
        for (String value : forbidden) assertFalse(log.toString().contains(value), "Authentication content reached a log sink");
    }
}
