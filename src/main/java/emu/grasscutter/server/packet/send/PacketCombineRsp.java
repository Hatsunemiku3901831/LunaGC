package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.*;

public class PacketCombineRsp extends BasePacket {

    public PacketCombineRsp() {
        super(PacketOpcodes.CombineRsp);

        CombineRspOuterClass.CombineRsp proto =
                CombineRspOuterClass.CombineRsp.newBuilder()
                        .setRetcode(RetcodeOuterClass.Retcode.RET_SVR_ERROR_VALUE)
                        .build();

        this.setData(proto);
    }

    public PacketCombineRsp(int retcode) {
        super(PacketOpcodes.CombineRsp);

        CombineRspOuterClass.CombineRsp proto =
                CombineRspOuterClass.CombineRsp.newBuilder().setRetcode(retcode).build();

        this.setData(proto);
    }

    public PacketCombineRsp(
            CombineReqOuterClass.CombineReq combineReq,
            Iterable<ItemParamOuterClass.ItemParam> costItemList,
            Iterable<ItemParamOuterClass.ItemParam> resultItemList,
            Iterable<ItemParamOuterClass.ItemParam> totalRandomItemList,
            Iterable<ItemParamOuterClass.ItemParam> totalReturnItemList,
            Iterable<ItemParamOuterClass.ItemParam> totalExtraItemList) {

        super(PacketOpcodes.CombineRsp);

        CombineRspOuterClass.CombineRsp.Builder proto =
                CombineRspOuterClass.CombineRsp.newBuilder()
                        .setRetcode(RetcodeOuterClass.Retcode.RET_SUCC_VALUE)
                        .setCombineId(combineReq.getCombineId())
                        .setCombineCount(combineReq.getCombineCount())
                        .setAvatarGuid(combineReq.getAvatarGuid())
                        .addAllCostItemList(costItemList);
        int units = combineReq.getCombineCount();
        for (var item : resultItemList) {
            // Callers report acknowledged totals. The native primary path scales field 7.
            if (units > 0 && item.getCount() % units == 0)
                proto.addResultItemList(item.toBuilder().setCount(item.getCount() / units));
            else proto.addAbsoluteResultItemList(item);
        }
        // The three bonus origins have not yet been matched to native fields 5, 9 and 11.
        // Current combine flows supply empty groups; keep these API parameters for later mapping.

        this.setData(proto.build());
    }
}
