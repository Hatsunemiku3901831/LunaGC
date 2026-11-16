package emu.grasscutter.server.game;

import java.net.InetSocketAddress;

/** Uses the real finite constructor without binding any socket. */
public final class FiniteGameServerTestSupport {
    private FiniteGameServerTestSupport() {}

    public static GameServer unbound() {
        return new GameServer(new InetSocketAddress("127.0.0.1", 0), false);
    }
}
