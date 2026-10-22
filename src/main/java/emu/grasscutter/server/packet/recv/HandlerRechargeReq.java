package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.RechargeReqOuterClass.RechargeReq;
import emu.grasscutter.net.proto.RechargeRspOuterClass.RechargeRsp;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketGetShopRsp;

@Opcodes(PacketOpcodes.RechargeReq)
public class HandlerRechargeReq extends PacketHandler {
    // The local client patch consumes this acknowledgement instead of entering checkout.
    public static final String LOCAL_CRYSTAL_ACK = "LunaGC.FreeRecharge:";

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var req = RechargeReq.parseFrom(payload);
        var rsp = RechargeRsp.newBuilder();
        String id = "", kind = "";
        int count =
                (req.hasCardProduct() ? 1 : 0)
                        + (req.hasMcoinProduct() ? 1 : 0)
                        + (req.hasPlayProduct() ? 1 : 0)
                        + (req.hasBeyondMcoinProduct() ? 1 : 0);
        if (req.hasCardProduct()) {
            id = req.getCardProduct().getProductId();
            kind = "card";
        }
        if (req.hasMcoinProduct()) {
            id = req.getMcoinProduct().getProductId();
            kind = "crystals";
        }
        if (req.hasPlayProduct()) {
            id = req.getPlayProduct().getProductId();
            kind = "pass";
        }
        if (req.hasBeyondMcoinProduct()) {
            id = req.getBeyondMcoinProduct().getProductId();
            kind = "beyond_crystals";
        }
        rsp.setProductId(id);
        boolean refreshCrystals = false;
        try {
            if (count != 1) throw new IllegalArgumentException("Exactly one product is required");
            var store = session.getServer().getShopSystem().getFreeStore();
            store.purchaseById(session.getPlayer(), id, kind);
            // purchaseById persists the grant and purchase count before returning. Use the
            // canonical desktop id even when the request used a resource platform alias.
            if (kind.equals("crystals")) {
                rsp.setProductId(LOCAL_CRYSTAL_ACK + store.forProductId(id).productId());
                refreshCrystals = true;
            }
        } catch (IllegalArgumentException e) {
            rsp.setRetcode(1);
        } catch (RuntimeException e) {
            // A storage failure must not leave the client waiting or acknowledge success.
            Grasscutter.getLogger().error("Unable to finish local recharge for player {}",
                    session.getPlayer().getUid(), e);
            rsp.setRetcode(1);
        }
        var packet = new BasePacket(PacketOpcodes.RechargeRsp);
        packet.setData(rsp.build());
        session.send(packet);
        if (refreshCrystals) {
            session.send(new PacketGetShopRsp(session.getPlayer(),
                    session.getServer().getShopSystem(), 903));
        }
    }
}
