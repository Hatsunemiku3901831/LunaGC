package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.expedition.ExpeditionClaims;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketAvatarExpeditionAllDataRsp;
import emu.grasscutter.utils.Utils;

@Opcodes(PacketOpcodes.AvatarExpeditionAllDataReq)
public class HandlerAvatarExpeditionAllDataReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var player = session.getPlayer();
        synchronized (player.getInventory()) {
            if (ExpeditionClaims.refresh(player, Utils.getCurrentSeconds())) player.save();
            session.send(
                    new PacketAvatarExpeditionAllDataRsp(
                            player.getExpeditionInfo(), player.getExpeditionLimit()));
        }
    }
}
