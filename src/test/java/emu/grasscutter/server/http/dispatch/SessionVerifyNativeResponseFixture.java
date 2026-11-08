package emu.grasscutter.server.http.dispatch;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.google.gson.JsonObject;
import java.io.OutputStream;
import java.io.PrintStream;
import org.slf4j.LoggerFactory;

/**
 * Pipe-only bridge for the fixed SDK native parser/formatter check. Stdout contains synthetic raw
 * HTTP bodies and a credential: consume with subprocess.PIPE, never a terminal, log, file, or hash.
 */
public final class SessionVerifyNativeResponseFixture {
    public static void main(String[] args) throws Exception {
        PrintStream pipe = System.out;
        try (var quiet = new PrintStream(OutputStream.nullOutputStream())) {
            System.setOut(quiet);
            var logging = (LoggerContext) LoggerFactory.getILoggerFactory();
            logging.reset();
            logging.getLogger(Logger.ROOT_LOGGER_NAME).setLevel(Level.OFF);
            var fixture = new SessionVerifyRoutesTest();
            JsonObject responses;
            try {
                fixture.startHttpWithReadOnlyAccountFixture();
                responses = fixture.nativeResponses();
            } finally {
                fixture.restore();
                logging.stop();
            }
            // Use the captured pipe only after all HTTP and logging lifecycle work has finished.
            pipe.print(responses);
            pipe.flush();
        } finally {
            System.setOut(pipe);
        }
    }
}
