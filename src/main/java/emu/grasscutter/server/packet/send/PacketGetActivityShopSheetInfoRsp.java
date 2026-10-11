package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.shop.ActivityShop;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ActivityShopSheetInfoOuterClass.ActivityShopSheetInfo;
import emu.grasscutter.net.proto.GetActivityShopSheetInfoRspOuterClass.GetActivityShopSheetInfoRsp;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;

public class PacketGetActivityShopSheetInfoRsp extends BasePacket {

    public PacketGetActivityShopSheetInfoRsp(Player player, int shopType) {
        this(player, shopType, System.currentTimeMillis());
    }

    public PacketGetActivityShopSheetInfoRsp(Player player, int shopType, long now) {
        super(PacketOpcodes.GetActivityShopSheetInfoRsp);
        var access = ActivityShop.resolve(player, shopType, now);
        var response = GetActivityShopSheetInfoRsp.newBuilder().setShopType(shopType);
        if (!access.activityShop() || !access.isOpen() || access.sheets().isEmpty()) {
            response.setRetcode(Retcode.RET_SHOP_NOT_OPEN_VALUE);
        } else {
            for (var sheet : access.sheets()) {
                response.addSheetInfoList(
                        ActivityShopSheetInfo.newBuilder()
                                .setSheetId(sheet.id())
                                .setBeginTime(sheet.beginTime())
                                .setEndTime(sheet.endTime()));
            }
        }
        setData(response.build());
    }
}
