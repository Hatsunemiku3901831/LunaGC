package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ForgeQueueManipulateRspOuterClass.ForgeQueueManipulateRsp;
import emu.grasscutter.net.proto.ForgeQueueManipulateTypeOuterClass.ForgeQueueManipulateType;
import emu.grasscutter.net.proto.ItemHintOuterClass.ItemHint;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;

import java.util.List;

public class PacketForgeQueueManipulateRsp extends BasePacket {

    public PacketForgeQueueManipulateRsp(
            Retcode retcode,
            ForgeQueueManipulateType type,
            List<GameItem> output,
            List<GameItem> refund,
            List<GameItem> extra) {
        this(
                retcode.getNumber(),
                type == ForgeQueueManipulateType.UNRECOGNIZED ? -1 : type.getNumber(),
                output.stream().map(GameItem::toItemHintProto).toList(),
                refund.stream().map(GameItem::toItemHintProto).toList(),
                extra.stream().map(GameItem::toItemHintProto).toList());
    }

    public PacketForgeQueueManipulateRsp(
            int retcode,
            int type,
            List<ItemHint> output,
            List<ItemHint> refund,
            List<ItemHint> extra) {
        super(PacketOpcodes.ForgeQueueManipulateRsp);

        ForgeQueueManipulateRsp.Builder builder =
                ForgeQueueManipulateRsp.newBuilder()
                        .setRetcode(retcode)
                        .setManipulateTypeValue(type)
                        .addAllOutputItemList(output)
                        .addAllReturnItemList(refund)
                        .addAllExtraOutputItemList(extra);

        this.setData(builder.build());
    }
}
