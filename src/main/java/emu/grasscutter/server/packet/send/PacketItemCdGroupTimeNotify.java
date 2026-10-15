package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ItemCdGroupTimeNotifyOuterClass.ItemCdGroupTimeNotify;

/** Group expiry uses Unix milliseconds, matching the client's inventory cooldown clock. */
public class PacketItemCdGroupTimeNotify extends BasePacket {
    public PacketItemCdGroupTimeNotify(Player player) {
        this(player, System.currentTimeMillis());
    }

    public PacketItemCdGroupTimeNotify(Player player, long now) {
        super(PacketOpcodes.ItemCdGroupTimeNotify);
        setData(
                ItemCdGroupTimeNotify.newBuilder()
                        .putAllItemCdMap(player.itemUseCooldownSnapshot(now)));
    }
}
