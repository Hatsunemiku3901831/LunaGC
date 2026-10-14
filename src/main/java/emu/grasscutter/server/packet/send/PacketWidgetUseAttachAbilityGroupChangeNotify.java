package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.WidgetUseAttachAbilityGroupChangeNotifyOuterClass.WidgetUseAttachAbilityGroupChangeNotify;

/** Current-client notification: boolean field 2, material field 3, opcode 5968. */
public final class PacketWidgetUseAttachAbilityGroupChangeNotify extends BasePacket {
    public PacketWidgetUseAttachAbilityGroupChangeNotify(int materialId, boolean attach) {
        super(PacketOpcodes.WidgetUseAttachAbilityGroupChangeNotify, true);
        setData(
                WidgetUseAttachAbilityGroupChangeNotify.newBuilder()
                        .setIsAttach(attach)
                        .setMaterialId(materialId));
    }
}
