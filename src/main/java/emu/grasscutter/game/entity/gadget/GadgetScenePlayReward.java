package emu.grasscutter.game.entity.gadget;

import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.GadgetInteractReqOuterClass.GadgetInteractReq;
import emu.grasscutter.net.proto.SceneGadgetInfoOuterClass.SceneGadgetInfo;

public final class GadgetScenePlayReward extends GadgetContent {
    public GadgetScenePlayReward(EntityGadget gadget) {
        super(gadget);
    }

    @Override
    public boolean onInteract(Player player, GadgetInteractReq request) {
        return getGadget()
                .getScene()
                .getScenePlayBattleManager()
                .getRewards()
                .interact(player, getGadget(), request);
    }

    @Override
    public void onBuildProto(SceneGadgetInfo.Builder info) {
        info.setGeneralReward(
                getGadget().getScene().getScenePlayBattleManager().getRewards().info(getGadget()));
    }
}
