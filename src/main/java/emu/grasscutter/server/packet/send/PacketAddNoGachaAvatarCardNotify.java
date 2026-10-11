package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AddNoGachaAvatarCardNotifyOuterClass.AddNoGachaAvatarCardNotify;
import emu.grasscutter.net.proto.AddNoGachaAvatarCardTransferItemOuterClass.AddNoGachaAvatarCardTransferItem;

import java.util.List;

public class PacketAddNoGachaAvatarCardNotify extends BasePacket {

    public PacketAddNoGachaAvatarCardNotify(Avatar avatar, ActionReason reason) {
        this(avatar, reason, List.of());
    }

    public PacketAddNoGachaAvatarCardNotify(
            Avatar avatar, ActionReason reason, List<AddNoGachaAvatarCardTransferItem> transfers) {
        super(PacketOpcodes.AddNoGachaAvatarCardNotify, true);

        AddNoGachaAvatarCardNotify proto =
                AddNoGachaAvatarCardNotify.newBuilder()
                        .setAvatarId(avatar.getAvatarId())
                        .setReason(reason.getValue())
                        .setInitialLevel(avatar.getLevel())
                        .setItemId(1000 + (avatar.getAvatarId() % 10000000))
                        .setInitialPromoteLevel(avatar.getPromoteLevel())
                        .setIsTransferToItem(!transfers.isEmpty())
                        .addAllTransferItemList(transfers)
                        .build();

        this.setData(proto);
    }

    public PacketAddNoGachaAvatarCardNotify(int avatarId, ActionReason reason, GameItem item) {
        super(PacketOpcodes.AddNoGachaAvatarCardNotify, true);

        AddNoGachaAvatarCardNotify proto =
                AddNoGachaAvatarCardNotify.newBuilder()
                        .setAvatarId(avatarId)
                        .setReason(reason.getValue())
                        .setInitialLevel(1)
                        .setItemId(item.getItemId())
                        .setInitialPromoteLevel(0)
                        .build();

        this.setData(proto);
    }
}
