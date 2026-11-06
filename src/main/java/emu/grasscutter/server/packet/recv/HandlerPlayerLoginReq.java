package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.AccountLoginAdmission;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PlayerLoginReqOuterClass.PlayerLoginReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.game.GameSession.SessionState;
import emu.grasscutter.server.packet.send.*;

@Opcodes(PacketOpcodes.PlayerLoginReq)
public class HandlerPlayerLoginReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        PlayerLoginReq.parseFrom(payload);
        AccountLoginAdmission.step(session, () -> handleAdmitted(session));
    }

    private void handleAdmitted(GameSession session) throws Exception {
        if (session.getAccount() == null) {
            session.close();
            return;
        }

        Player player = session.getPlayer();
        if (!emu.grasscutter.data.ResourceLoader.isLoadedAll()) {
            Grasscutter.getLogger()
                    .warn(
                            "Player {} login arrived before resources finished. Closing the session.",
                            player.getUid());
            session.close();
            return;
        }
        int avatarCount = player.getAvatars().getAvatarCount();
        if ((avatarCount == 0) != (player.getMainCharacterId() == 0)) {
            Grasscutter.getLogger().error("Player {} has an incomplete character creation; reload and inspect the saved state.", player.getUid());
            session.close();
            return;
        }
        if (avatarCount == 0) {
            session.setState(SessionState.PICKING_CHARACTER);
            session.send(new PacketDoSetPlayerBornDataNotify());
            session.send(new PacketPlayerLoginRsp(session));
            return;
        }

        player.onLogin();
        session.send(new PacketPlayerLoginRsp(session));
    }

}
