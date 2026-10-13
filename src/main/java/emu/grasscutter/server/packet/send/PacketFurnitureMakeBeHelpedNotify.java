package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.FurnitureMakeBeHelpedDataOuterClass.FurnitureMakeBeHelpedData;
import emu.grasscutter.net.proto.FurnitureMakeBeHelpedNotifyOuterClass.FurnitureMakeBeHelpedNotify;
import emu.grasscutter.net.proto.FurnitureMakeSlotOuterClass.FurnitureMakeSlot;

public final class PacketFurnitureMakeBeHelpedNotify extends BasePacket {
    public PacketFurnitureMakeBeHelpedNotify(
            FurnitureMakeBeHelpedData confirmed, FurnitureMakeSlot slots) {
        super(PacketOpcodes.FurnitureMakeBeHelpedNotify);
        if (confirmed == null || slots == null)
            throw new IllegalArgumentException(
                    "Furniture assistance details and queue are required");
        setData(
                FurnitureMakeBeHelpedNotify.newBuilder()
                        .setHelpedData(confirmed)
                        .setFurnitureMakeSlot(slots)
                        .build());
    }
}
