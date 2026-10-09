package emu.grasscutter.server.game;

import static org.junit.jupiter.api.Assertions.*;
import emu.grasscutter.server.http.ServerHealthCheck;
import org.junit.jupiter.api.Test;

class ServerHealthCheckTest {
    @Test void anOpenHttpPortAloneDoesNotProveGameLiveness() {
        assertFalse(ServerHealthCheck.isHealthy(200, "{\"status\":{\"health\":{\"healthy\":false}}}"));
        assertFalse(ServerHealthCheck.isHealthy(503, "{\"status\":{\"health\":{\"healthy\":true}}}"));
        assertFalse(ServerHealthCheck.isHealthy(200, "{\"status\":{\"playerCount\":1}}"));
        assertFalse(ServerHealthCheck.isHealthy(200, ""));
    }
    @Test void onlyAnExplicitHealthyResponsePasses() {
        assertTrue(ServerHealthCheck.isHealthy(200, "{\"status\":{\"health\":{\"healthy\":true}}}"));
    }
}
