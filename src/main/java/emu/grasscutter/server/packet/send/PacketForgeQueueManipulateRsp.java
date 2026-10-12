package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ForgeQueueManipulateRspOuterClass.ForgeQueueManipulateRsp;
import emu.grasscutter.net.proto.ForgeQueueManipulateTypeOuterClass.ForgeQueueManipulateType;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;

import java.util.List;

public class PacketForgeQueueManipulateRsp extends BasePacket {

    public PacketForgeQueueManipulateRsp(
            Retcode retcode,
            ForgeQueueManipulateType type,
            List<GameItem> output,
            List<GameItem> refund,
            List<GameItem> extra) {
        super(PacketOpcodes.ForgeQueueManipulateRsp);

        ForgeQueueManipulateRsp.Builder builder =
                ForgeQueueManipulateRsp.newBuilder()
                        .setRetcode(retcode.getNumber())
                        .setManipulateTypeValue(
                                type == ForgeQueueManipulateType.UNRECOGNIZED
                                        ? -1
                                        : type.getNumber())
                        .addAllOutputItemList(
                                output.stream().map(GameItem::toItemHintProto).toList())
                        .addAllReturnItemList(
                                refund.stream().map(GameItem::toItemHintProto).toList())
                        .addAllExtraOutputItemList(
                                extra.stream().map(GameItem::toItemHintProto).toList());

        this.setData(builder.build());
    }
}
