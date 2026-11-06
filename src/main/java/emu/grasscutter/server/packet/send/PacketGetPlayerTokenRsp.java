package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GetPlayerTokenRspOuterClass.GetPlayerTokenRsp;
import emu.grasscutter.server.game.GameSession;

/** Fixed CN72 response contract; see CLIENT_72_RESPONSE_CONTRACT.md. */
public class PacketGetPlayerTokenRsp extends BasePacket {
    /** Identity-only body for the explicit clear-transport configuration, not an RSA fallback. */
    public PacketGetPlayerTokenRsp(GameSession session) {
        super(PacketOpcodes.GetPlayerTokenRsp, true);
        this.setUseDispatchKey(true);
        this.setData(identity(session).build());
    }

    /** Refusal: only the target-verified error field is emitted. */
    public PacketGetPlayerTokenRsp(GameSession session, int retcode) {
        super(PacketOpcodes.GetPlayerTokenRsp, true);
        this.setUseDispatchKey(true);
        this.setData(GetPlayerTokenRsp.newBuilder().setRetcode(retcode).build());
    }

    /** The client decrypts and verifies these fields before installing the session key. */
    public PacketGetPlayerTokenRsp(GameSession session, String encryptedSeed, String signature) {
        super(PacketOpcodes.GetPlayerTokenRsp, true);
        this.setUseDispatchKey(true);
        this.setData(identity(session).setServerRandKey(encryptedSeed).setSign(signature).build());
    }

    private static GetPlayerTokenRsp.Builder identity(GameSession session) {
        return GetPlayerTokenRsp.newBuilder()
                .setUid(session.getPlayer().getUid())
                .setToken(session.getAccount().getToken());
    }
}
