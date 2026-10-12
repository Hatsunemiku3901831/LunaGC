package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.dungeons.DungeonRestartGuard;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.DungeonRestartReq)
public class HandlerDungeonRestartReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var player = session.getPlayer();
        if (player == null) return;
        synchronized (player.getSceneTransferLock()) {
            if (!DungeonRestartGuard.mayRestart(player, payload)) return;
            if (player.getServer().getDungeonSystem().tryRestartDungeon(player)) {
                player.sendPacket(new BasePacket(PacketOpcodes.DungeonRestartRsp));
            }
        }
    }
}
