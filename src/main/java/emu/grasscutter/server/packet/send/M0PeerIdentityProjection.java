package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.Client72M0PeerRawOuterClass.IAKGECCPGDA;

/** Known loading token/local authority key only. Not a packet or permission to advance handshake. */
public final class M0PeerIdentityProjection {
    private M0PeerIdentityProjection() {}

    public static IAKGECCPGDA project(Player player) {
        return IAKGECCPGDA.newBuilder()
                .setMCCMGLDOECH(player.getEnterSceneToken())
                .setAANCEPCPHKJ(player.getPeerId())
                .build();
    }
}
