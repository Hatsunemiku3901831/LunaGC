package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.Client72InventoryWireOuterClass.BNDIKNLDLOJ;
import emu.grasscutter.net.proto.Client72InventoryWireOuterClass.FNECPDFLEHN;
import emu.grasscutter.net.proto.Client72InventoryWireOuterClass.JBBEOMJDLFM;
import emu.grasscutter.net.proto.Client72InventoryWireOuterClass.KKOPHGDBCEO;
import emu.grasscutter.net.proto.Client72InventoryWireOuterClass.NDGHFPMJFIM;
import emu.grasscutter.net.proto.PlayerStoreNotifyOuterClass.PlayerStoreNotify;
import emu.grasscutter.net.proto.StoreTypeOuterClass.StoreType;

public class PacketPlayerStoreNotify extends BasePacket {

    public PacketPlayerStoreNotify(Player player) {
        super(PacketOpcodes.PlayerStoreNotify);

        this.buildHeader(2);

        var data =
                PlayerStoreNotify.newBuilder()
                        .setStoreType(StoreType.StoreType_STORE_PACK.getNumber());

        for (GameItem item : player.getInventory()) {
            var itemProto = toClient72Item(item);
            if (itemProto != null) data.addItemList(itemProto);
        }

        this.setData(data.build());
    }

    private static NDGHFPMJFIM toClient72Item(GameItem item) {
        var result = NDGHFPMJFIM.newBuilder()
                .setPJAGIOHBKLD(item.getItemId())
                .setFBLKPHOCBIK(item.getGuid());
        switch (item.getItemType()) {
            case ITEM_MATERIAL -> result.setFLPGMLOFAKP5(
                    KKOPHGDBCEO.newBuilder().setGNHFNECPAHD(item.getCount()));
            case ITEM_WEAPON -> {
                var weapon = FNECPDFLEHN.newBuilder()
                        .setNJAGFJINHMA(item.getLevel())
                        .setNILMBAGDEAL(item.getExp())
                        .setKFDCBKBPPGL(item.getPromoteLevel());
                // Server refinement -> target EquipAffix level keys is still unverified.
                result.setFLPGMLOFAKP6(BNDIKNLDLOJ.newBuilder()
                        .setFLPGMLOFAKP2(weapon)
                        .setFAPABAJBMAK(item.isLocked()));
            }
            case ITEM_RELIQUARY -> result.setFLPGMLOFAKP6(BNDIKNLDLOJ.newBuilder()
                    .setFLPGMLOFAKP1(JBBEOMJDLFM.newBuilder()
                            .setNJAGFJINHMA(item.getLevel())
                            .setCGKIGPFDFOF(item.isFavourite()))
                    .setFAPABAJBMAK(item.isLocked()));
            default -> { return null; }
        }
        return result.build();
    }
}
