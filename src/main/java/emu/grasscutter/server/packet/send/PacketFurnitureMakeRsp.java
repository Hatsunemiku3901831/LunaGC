package emu.grasscutter.server.packet.send;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.home.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;

import java.util.*;

public class PacketFurnitureMakeRsp extends BasePacket {

    public PacketFurnitureMakeRsp(GameHome home) {
        super(PacketOpcodes.FurnitureMakeRsp);

        if (home == null) {
            setData(failure(Retcode.RET_FURNITURE_MAKE_NOT_IN_CORRECT_HOME_VALUE));
            return;
        }
        synchronized (home) {
            var definition = GameData.getHomeWorldLevelDataMap().get(home.getLevel());
            if (definition == null
                    || definition.getId() != home.getLevel()
                    || definition.getFurnitureMakeSlotCount() <= 0) {
                setData(failure(Retcode.RET_FURNITURE_MAKE_CONFIG_ERROR_VALUE));
                return;
            }
            var slots = home.getFurnitureMakeSlotItemList();
            if (slots == null) slots = List.of();
            Set<Integer> indexes = new HashSet<>();
            for (var slot : slots) {
                if (slot == null
                        || slot.getIndex() < 0
                        || slot.getAccelerateTime() < 0
                        || !indexes.add(slot.getIndex())) {
                    setData(failure(Retcode.RET_FURNITURE_MAKE_CONFIG_ERROR_VALUE));
                    return;
                }
            }
            setData(
                    FurnitureMakeRspOuterClass.FurnitureMakeRsp.newBuilder()
                            .setFurnitureMakeSlotNum(definition.getFurnitureMakeSlotCount())
                            .setFurnitureMakeSlot(
                                    FurnitureMakeSlotOuterClass.FurnitureMakeSlot.newBuilder()
                                            .addAllFurnitureMakeDataList(
                                                    slots.stream()
                                                            .map(FurnitureMakeSlotItem::toProto)
                                                            .toList()))
                            .build());
        }
    }

    public PacketFurnitureMakeRsp(int retcode) {
        super(PacketOpcodes.FurnitureMakeRsp);
        setData(failure(retcode));
    }

    private static FurnitureMakeRspOuterClass.FurnitureMakeRsp failure(int retcode) {
        return FurnitureMakeRspOuterClass.FurnitureMakeRsp.newBuilder().setRetcode(retcode).build();
    }
}
