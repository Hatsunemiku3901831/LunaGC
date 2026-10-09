package emu.grasscutter.server.http;

import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;

/** A probe usable in the JRE runtime image without installing curl or starting server services. */
public final class ServerHealthCheck {
    private ServerHealthCheck() {}

    public static boolean isHealthy(int responseCode, String body) {
        if (responseCode != 200) return false;
        try {
            var status = JsonParser.parseString(body).getAsJsonObject().getAsJsonObject("status");
            return status != null && status.getAsJsonObject("health").get("healthy").getAsBoolean();
        } catch (RuntimeException exception) { return false; }
    }

    public static void main(String[] args) {
        try {
            var uri = URI.create(args.length == 0 ? "http://127.0.0.1:8088/status/server" : args[0]);
            var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(3)).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (isHealthy(response.statusCode(), response.body())) return;
            System.err.println("Server health check failed: HTTP " + response.statusCode());
        } catch (Exception exception) {
            System.err.println("Server health check failed: " + exception.getClass().getSimpleName());
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
        }
        System.exit(1);
    }
}
