package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.RetcodeOuterClass;
import emu.grasscutter.net.proto.UseItemRspOuterClass.UseItemRsp;

import java.util.List;

public class PacketUseItemRsp extends BasePacket {

    public PacketUseItemRsp(long targetGuid, GameItem useItem) {
        this(targetGuid, useItem, 0);
    }

    public PacketUseItemRsp(long targetGuid, GameItem useItem, int optionIdx) {
        this(targetGuid, useItem, optionIdx, List.of());
    }

    public PacketUseItemRsp(
            long targetGuid, GameItem useItem, int optionIdx, List<Integer> optionIds) {
        super(PacketOpcodes.UseItemRsp);

        UseItemRsp proto =
                UseItemRsp.newBuilder()
                        .setTargetGuid(targetGuid)
                        .setItemId(useItem.getItemId())
                        .setGuid(useItem.getGuid())
                        .setOptionIdx(optionIdx)
                        .addAllOptionIdxList(optionIds)
                        .build();

        this.setData(proto);
    }

    public PacketUseItemRsp() {
        this(RetcodeOuterClass.Retcode.RET_SVR_ERROR);
    }

    public PacketUseItemRsp(RetcodeOuterClass.Retcode retcode) {
        super(PacketOpcodes.UseItemRsp);

        UseItemRsp proto =
                UseItemRsp.newBuilder()
                        .setRetcode(retcode.getNumber())
                        .build();

        this.setData(proto);
    }
}
