package emu.grasscutter.server.packet.recv;

import com.google.protobuf.InvalidProtocolBufferException;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.inventory.ItemUseException;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.UseItemReqOuterClass.UseItemReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketUseItemRsp;

@Opcodes(PacketOpcodes.UseItemReq)
public class HandlerUseItemReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        UseItemReq req;
        try {
            req = UseItemReq.parseFrom(payload);
        } catch (InvalidProtocolBufferException | NullPointerException malformed) {
            session.send(new PacketUseItemRsp());
            return;
        }

        GameItem useItem;
        try {
            if (req.getOptionIdxListCount() == 0) {
                useItem =
                        session.getServer()
                                .getInventorySystem()
                                .useItem(
                                        session.getPlayer(),
                                        req.getTargetGuid(),
                                        req.getGuid(),
                                        req.getCount(),
                                        req.getOptionIdx(),
                                        req.getIsEnterMpDungeonTeam());
            } else {
                useItem =
                        session.getServer()
                                .getInventorySystem()
                                .useItem(
                                        session.getPlayer(),
                                        req.getTargetGuid(),
                                        req.getGuid(),
                                        req.getCount(),
                                        req.getOptionIdx(),
                                        req.getIsEnterMpDungeonTeam(),
                                        req.getOptionIdxListList());
            }
        } catch (ItemUseException rejected) {
            session.send(new PacketUseItemRsp(rejected.getRetcode()));
            return;
        } catch (RuntimeException failure) {
            // Inventory may already have a pending receipt. Do not retry the economic operation.
            Grasscutter.getLogger()
                    .error(
                            "UseItem failed for UID {} and source GUID {}.",
                            session.getPlayer() != null ? session.getPlayer().getUid() : 0,
                            req.getGuid(),
                            failure);
            session.send(new PacketUseItemRsp());
            return;
        }
        if (useItem != null) {
            session.send(
                    new PacketUseItemRsp(
                            req.getTargetGuid(),
                            useItem,
                            req.getOptionIdx(),
                            req.getOptionIdxListList()));
        } else {
            session.send(new PacketUseItemRsp());
        }
    }
}
