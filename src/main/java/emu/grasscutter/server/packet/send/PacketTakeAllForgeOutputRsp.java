package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.ItemHintOuterClass.ItemHint;
import emu.grasscutter.net.proto.TakeAllForgeOutputRspOuterClass.TakeAllForgeOutputRsp;

import java.util.List;

public final class PacketTakeAllForgeOutputRsp extends BasePacket {
    public PacketTakeAllForgeOutputRsp(
            int retcode, List<ItemHint> output, List<ItemHint> returns, List<Integer> failures) {
        super(PacketOpcodes.TakeAllForgeOutputRsp);
        setData(
                TakeAllForgeOutputRsp.newBuilder()
                        .setRetcode(retcode)
                        .addAllOutputItemList(output)
                        .addAllReturnItemList(returns)
                        .addAllFailRetcodeList(failures));
    }
}
