package emu.grasscutter.server.game;

import emu.grasscutter.game.player.Player;

/** Associates a decoded CN72 handshake request with the current scene entry. */
public final class SceneHandshakeToken {
    private SceneHandshakeToken() {}

    public static void requireCurrent(Player player, int token) {
        if (token != player.getEnterSceneToken()) {
            // Use the existing packet-handler failure path. No target error
            // code or recovery protocol has been established for stale tokens.
            throw new IllegalArgumentException("Scene request token does not match the current entry");
        }
    }
}
