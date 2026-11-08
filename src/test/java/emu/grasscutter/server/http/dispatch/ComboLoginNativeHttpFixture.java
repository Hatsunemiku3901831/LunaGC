package emu.grasscutter.server.http.dispatch;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.LoggerFactory;

/** Secret-bearing newline JSON protocol: use Python stdin/stdout PIPE only, never a terminal/file. */
public final class ComboLoginNativeHttpFixture {
    static final List<String> CASES = List.of(
            "success", "wrong_token", "empty_token", "missing_token", "unknown_uid", "repeat_success");

    public static void main(String[] args) {
        PrintStream originalOut = System.out;
        PrintStream pipe = new PrintStream(originalOut, true, StandardCharsets.UTF_8);
        // Library startup/HTTP/log output cannot enter the protocol stream.
        System.setOut(System.err);
        int exit = 0;
        var logging = (LoggerContext) LoggerFactory.getILoggerFactory();
        logging.reset();
        logging.getLogger(Logger.ROOT_LOGGER_NAME).setLevel(Level.OFF);
        try {
            ComboHttpMongoHarness.check(args.length == 1 && args[0].equals("--pipe"), "Explicit --pipe is required");
            try (var fixture = ComboHttpMongoHarness.open();
                    var input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
                var seed = new JsonObject();
                seed.addProperty("schema", "combo-http-v1");
                seed.addProperty("phase", "seed");
                seed.addProperty("uid", ComboHttpMongoHarness.UID);
                seed.addProperty("session_token", fixture.sessionToken);
                seed.addProperty("url", fixture.url());
                send(pipe, seed);
                for (String label : CASES) {
                    String line = input.readLine();
                    ComboHttpMongoHarness.check(line != null, "Missing PIPE request");
                    JsonObject frame = ComboHttpMongoHarness.parse(line);
                    ComboHttpMongoHarness.check("combo-http-v1".equals(frame.get("schema").getAsString())
                                    && label.equals(frame.get("case").getAsString()), "PIPE request order/schema changed");
                    send(pipe, fixture.exercise(label, frame.get("request_body").getAsString(),
                            label.equals("success") || label.equals("repeat_success")));
                }
            }
            var complete = new JsonObject();
            complete.addProperty("schema", "combo-http-v1");
            complete.addProperty("phase", "complete");
            complete.addProperty("cases", CASES.size());
            complete.addProperty("uuidDatabaseDropped", true);
            complete.addProperty("httpStopped", true);
            send(pipe, complete);
        } catch (Throwable failure) {
            // No exception message/cause: parsers and database exceptions can include owned secrets.
            System.err.println("COMBO_HTTP_FIXTURE_FAILED");
            exit = 2;
        } finally {
            logging.stop();
            System.setOut(originalOut);
        }
        if (exit != 0) System.exit(exit);
    }

    private static void send(PrintStream pipe, JsonObject value) {
        pipe.println(value);
        pipe.flush();
    }
}
