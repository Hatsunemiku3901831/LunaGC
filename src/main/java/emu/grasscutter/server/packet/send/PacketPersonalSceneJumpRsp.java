package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PersonalSceneJumpRspOuterClass.PersonalSceneJumpRsp;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;

public class PacketPersonalSceneJumpRsp extends BasePacket {

    public PacketPersonalSceneJumpRsp(Retcode error) {
        super(PacketOpcodes.PersonalSceneJumpRsp);
        setData(PersonalSceneJumpRsp.newBuilder().setRetcode(error.getNumber()));
    }

    public PacketPersonalSceneJumpRsp(int sceneId, Position pos) {
        super(PacketOpcodes.PersonalSceneJumpRsp);

        PersonalSceneJumpRsp proto =
                PersonalSceneJumpRsp.newBuilder().setDestSceneId(sceneId).setDestPos(pos.toProto()).build();

        this.setData(proto);
    }
}
