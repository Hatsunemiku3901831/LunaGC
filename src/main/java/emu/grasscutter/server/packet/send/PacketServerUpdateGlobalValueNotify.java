package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ServerUpdateGlobalValueNotifyOuterClass.ServerUpdateGlobalValueNotify;
import emu.grasscutter.net.proto.UpdateTypeOuterClass.UpdateType;
import emu.grasscutter.utils.Utils;

/** An operation on the receiving player's team entity, rather than a scene broadcast. */
public final class PacketServerUpdateGlobalValueNotify extends BasePacket {
    public PacketServerUpdateGlobalValueNotify(
            int entityId, String key, UpdateType type, float amount) {
        super(PacketOpcodes.ServerUpdateGlobalValueNotify);
        if (type != UpdateType.UpdateType_ADD && type != UpdateType.UpdateType_SET)
            throw new IllegalArgumentException("Unsupported global value operation");
        if (key == null || key.isBlank() || Utils.abilityHash(key) == 0 || !Float.isFinite(amount))
            throw new IllegalArgumentException("Invalid global value");
        var message =
                ServerUpdateGlobalValueNotify.newBuilder()
                        .setEntityId(entityId)
                        .setKeyHash(Utils.abilityHash(key))
                        .setUpdateType(type);
        if (type == UpdateType.UpdateType_ADD) message.setDelta(amount);
        else message.setValue(amount);
        setData(message);
    }
}
