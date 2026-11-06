package emu.grasscutter.server.packet.send;

import static emu.grasscutter.config.Configuration.INVENTORY_LIMITS;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.StoreWeightLimitNotifyOuterClass.StoreWeightLimitNotify;

public class PacketStoreWeightLimitNotify extends BasePacket {

    public PacketStoreWeightLimitNotify() {
        super(PacketOpcodes.StoreWeightLimitNotify);

        StoreWeightLimitNotify p =
                StoreWeightLimitNotify.newBuilder()
                        .setStoreType(1) // Target StorePack enum; verified native pack dictionary route.
                        .setReliquaryCountLimit(INVENTORY_LIMITS.relics)
                        .setFurnitureCountLimit(INVENTORY_LIMITS.furniture)
                        .setWeaponCountLimit(INVENTORY_LIMITS.weapons)
                        .setMaterialCountLimit(INVENTORY_LIMITS.materials)
                        .build();

        this.setData(p);
    }
}
