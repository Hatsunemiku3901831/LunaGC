package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.net.proto.ClientAbilityInitFinishNotifyOuterClass.ClientAbilityInitFinishNotify;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.ClientAbilityInitFinishNotify)
public class HandlerClientAbilityInitFinishNotify extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        ClientAbilityInitFinishNotify notif = ClientAbilityInitFinishNotify.parseFrom(payload);

        Player player = session.getPlayer();

        player.getAbilityManager().onSkillEnd(player);

        for (AbilityInvokeEntry entry : notif.getInvokesList()) {
            if (player.getAbilityManager().acceptAbilityInvoke(entry))
                player.getClientAbilityInitFinishHandler().addEntry(entry.getForwardType(), entry);
        }
        var current = emu.grasscutter.game.tps.TpsWeaponSystem.currentAvatar(player);
        // Weapon initialization also sends this notify. Replying to that would restart it.
        if (current != null && notif.getEntityId() == current.getId())
            emu.grasscutter.game.tps.TpsWeaponSystem.sendWeaponAbilityBlocks(current.getAvatar());

        if (notif.getInvokesList().size() > 0) {
            session.getPlayer().getClientAbilityInitFinishHandler().update(session.getPlayer());
        }
    }
}
