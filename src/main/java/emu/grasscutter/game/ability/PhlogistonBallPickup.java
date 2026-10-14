package emu.grasscutter.game.ability;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import emu.grasscutter.data.binout.AbilityMixinData;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.inventory.MaterialType;
import emu.grasscutter.game.player.Phlogiston;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ItemUseAction.ItemUseAddPhlogiston;
import emu.grasscutter.net.proto.AbilityMixinHumanDragonCollabPickPhlogistonBallOuterClass.AbilityMixinHumanDragonCollabPickPhlogistonBall;
import emu.grasscutter.net.proto.VisionTypeOuterClass.VisionType;

/** One scene claim shared by the collaboration callback and normal item interaction. */
public final class PhlogistonBallPickup {
    private PhlogistonBallPickup() {}

    public static boolean isFuelBall(EntityItem ball) {
        var data = ball == null ? null : ball.getItemData();
        return data != null
                && data.isUseOnGain()
                && data.getMaterialType() == MaterialType.MATERIAL_ADSORBATE
                && data.getItemUseActions() != null
                && data.getItemUseActions().stream()
                        .anyMatch(a -> a instanceof ItemUseAddPhlogiston);
    }

    public static boolean pickup(Player player, GameEntity bearer, EntityItem ball) {
        var scene = player == null ? null : player.getScene();
        if (scene == null || bearer == null || ball == null || bearer.isDead()) return false;
        synchronized (scene) {
            if (ball.getScene() != scene
                    || scene.getEntityById(ball.getId()) != ball
                    || !AbilityExecutionContext.canTrigger(bearer, player)
                    || !isFuelBall(ball)
                    || !ball.isShare() && (int) (ball.getGuid() >> 32) != player.getUid())
                return false;
            EntityVehicle vehicle = bearer instanceof EntityVehicle v ? v : null;
            if (vehicle != null && vehicle.getOwner() != player
                    || vehicle == null
                            && (!(bearer instanceof EntityAvatar avatar)
                                    || avatar.getPlayer() != player)) return false;
            double total = 0;
            for (var action : ball.getItemData().getItemUseActions()) {
                if (!(action instanceof ItemUseAddPhlogiston fuel)) return false;
                float amount = fuel.amount(ball.getCount());
                if (!Float.isFinite(amount)) return false;
                total += amount;
            }
            float previous =
                    vehicle == null ? player.getPhlogistonValue() : vehicle.getCurPhlogiston();
            if (!Float.isFinite(previous) || total <= 0 || total > Float.MAX_VALUE) return false;
            // Remove under the scene monitor before fuel callbacks can re-enter this claim.
            scene.removeEntity(ball, VisionType.VisionType_VISION_REMOVE);
            if (vehicle == null) Phlogiston.change(player, (float) total);
            else Phlogiston.change(vehicle, (float) total);
            return true;
        }
    }

    public static boolean invoke(
            Ability ability,
            AbilityMixinData mixin,
            ByteString payload,
            GameEntity bearer,
            AbilityModifierController parent) {
        if (ability == null
                || mixin == null
                || !mixin.enable
                || mixin.type != AbilityMixinData.Type.HumanDragonCollabPickPhlogistonBallMixin
                || payload == null
                || !AbilityGlobalValues.isCurrent(ability, bearer)) return false;
        var player = ability.getPlayerOwner();
        var scene = player == null ? null : player.getScene();
        if (scene == null) return false;
        final AbilityMixinHumanDragonCollabPickPhlogistonBall message;
        try {
            message = AbilityMixinHumanDragonCollabPickPhlogistonBall.parseFrom(payload);
        } catch (InvalidProtocolBufferException ignored) {
            return false;
        }
        Integer gadget =
                mixin.phlogistonBallGadgetId != null
                        ? mixin.phlogistonBallGadgetId
                        : mixin.rawPhlogistonBallGadgetId;
        if (gadget == null || gadget <= 0 || message.getEntityId() == 0) return false;
        synchronized (scene) {
            if (!PhlogistonMixinCallbacks.scope(ability, mixin, bearer, parent)
                    || !PredicateEvaluator.all(
                            mixin.predicates, ability, ability.getOwner(), bearer, null))
                return false;
            var entity = scene.getEntityById(message.getEntityId());
            return entity instanceof EntityItem ball
                    && ball.getGadgetId() == gadget
                    && pickup(player, bearer, ball);
        }
    }
}
