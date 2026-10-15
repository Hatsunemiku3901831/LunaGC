package emu.grasscutter.game.ability;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import emu.grasscutter.data.binout.AbilityMixinData;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.inventory.MaterialType;
import emu.grasscutter.game.player.Phlogiston;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ItemUseAction.ItemUseAddPhlogiston;
import emu.grasscutter.game.world.Scene;
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

    /** Gadget interaction has no bearer ID; use the player's current Saurian membership. */
    public static boolean pickup(Player player, EntityItem ball) {
        var scene = player == null ? null : player.getScene();
        if (scene == null) return false;
        synchronized (scene) {
            return pickup(player, recipient(player, scene), ball);
        }
    }

    /** Resource awards have no bearer ID either; resolve membership under the same scene lock. */
    public static boolean replenish(Player player, float amount) {
        var scene = player == null ? null : player.getScene();
        if (scene == null || !Float.isFinite(amount) || amount <= 0) return false;
        synchronized (scene) {
            var bearer = recipient(player, scene);
            if (bearer == null
                    || bearer.isDead()
                    || player.getTeamManager().getEntity() == null
                    || !AbilityExecutionContext.canTrigger(bearer, player)) return false;
            if (bearer instanceof EntityVehicle vehicle) {
                if (!Float.isFinite(vehicle.getCurPhlogiston())) return false;
                Phlogiston.change(vehicle, amount);
            } else {
                if (!(bearer instanceof EntityAvatar avatar)
                        || avatar.getPlayer() != player
                        || !Float.isFinite(player.getPhlogistonValue())) return false;
                Phlogiston.change(player, amount);
            }
            return true;
        }
    }

    /** Caller holds the scene monitor across selection and the award. */
    private static GameEntity recipient(Player player, Scene scene) {
        if (player.getScene() != scene) return null;
        EntityVehicle ridden = null;
        for (var entity : scene.getEntities().values()) {
            if (!(entity instanceof EntityVehicle vehicle)
                    || vehicle.getOwner() != player
                    || !NatsaurusVehicleHelper.isNatsaurusMount(vehicle)
                    || vehicle.getVehicleMembers().stream()
                            .noneMatch(member -> member.getUid() == player.getUid())) continue;
            // A stale or ambiguous rider state must not redirect fuel to the human pool.
            if (!vehicle.isAlive() || ridden != null) return null;
            ridden = vehicle;
        }
        return ridden == null ? player.getTeamManager().getCurrentAvatarEntity() : ridden;
    }

    public static boolean pickup(Player player, GameEntity bearer, EntityItem ball) {
        var scene = player == null ? null : player.getScene();
        if (scene == null
                || bearer == null
                || ball == null
                || bearer.isDead()
                || player.getTeamManager().getEntity() == null) return false;
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
            try {
                scene.removeEntity(ball, VisionType.VisionType_VISION_REMOVE);
            } catch (RuntimeException exception) {
                // Scene removal commits before cleanup and the disappearance notification.
                // A failed callback must not lose the fuel from an already consumed ball.
                emu.grasscutter.Grasscutter.getLogger()
                        .warn("Fuel ball {} removal callback failed", ball.getId(), exception);
            }
            if (scene.getEntities().get(ball.getId()) == ball) return false;
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
