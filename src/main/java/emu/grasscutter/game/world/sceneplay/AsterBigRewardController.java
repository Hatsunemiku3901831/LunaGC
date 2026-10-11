package emu.grasscutter.game.world.sceneplay;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.activity.*;
import emu.grasscutter.game.activity.aster.*;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GadgetGeneralRewardInfoNotifyOuterClass.GadgetGeneralRewardInfoNotify;
import emu.grasscutter.net.proto.GadgetGeneralRewardInfoOuterClass.GadgetGeneralRewardInfo;
import emu.grasscutter.net.proto.GadgetInteractReqOuterClass.GadgetInteractReq;
import emu.grasscutter.net.proto.InterOpTypeOuterClass.InterOpType;
import emu.grasscutter.net.proto.InteractTypeOuterClass.InteractType;
import emu.grasscutter.net.proto.ResinCostTypeOuterClass.ResinCostType;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.VisionTypeOuterClass.VisionType;
import emu.grasscutter.server.packet.send.*;

import java.util.*;
import java.util.function.BooleanSupplier;

/**
 * Original play 2 reward point, with frozen personal drops and acknowledged two-document receipts.
 */
public final class AsterBigRewardController {
    private static final class Point {
        final EntityGadget entity;
        final PlayerActivityData owner;
        final String token;
        final BooleanSupplier current;
        final Set<Integer> previewed = new HashSet<>();

        Point(
                EntityGadget entity,
                PlayerActivityData owner,
                String token,
                BooleanSupplier current) {
            this.entity = entity;
            this.owner = owner;
            this.token = token;
            this.current = current;
        }
    }

    private final Scene scene;
    private Point point;

    AsterBigRewardController(Scene scene) {
        this.scene = scene;
    }

    public boolean owns(EntityGadget gadget) {
        return scene.getId() == 3
                && gadget.getGroupId() == AsterBigRewards.GROUP
                && gadget.getConfigId() == AsterBigRewards.CONFIG
                && gadget.getGadgetId() == AsterBigRewards.GADGET;
    }

    private static PlayerActivityData data(Player player) {
        if (player == null || player.getActivityManager() == null) return null;
        var data =
                player.getActivityManager()
                        .getPlayerActivityDataMap()
                        .get(AsterSchedule.ACTIVITY_ID);
        return data != null && data.getPlayer() == player && data.getUid() == player.getUid()
                ? data
                : null;
    }

    private static ActivityConfigItem config(PlayerActivityData data) {
        return data == null
                ? null
                : ActivityManager.getScheduleActivityConfigMap().get(data.getScheduleId());
    }

    private static boolean eligible(Player player, ActivityConfigItem config, long now) {
        return player != null
                && AsterSchedule.phaseOpen(data(player), config, player.getLevel(), now, 3);
    }

    private boolean current(Point selected) {
        return point == selected
                && selected.current.getAsBoolean()
                && scene.getEntities().get(selected.entity.getId()) == selected.entity
                && data(scene.getHost()) == selected.owner
                && eligible(scene.getHost(), config(selected.owner), System.currentTimeMillis());
    }

    /** Reservations survive scene disposal; a new round cannot erase an uncertain delivery. */
    boolean canReplace() {
        var owner = data(scene.getHost());
        if (owner == null) return point == null;
        synchronized (owner) {
            try {
                var offer = AsterSchedule.progress(owner).bigReward();
                return (point == null || offer != null && offer.token().equals(point.token))
                        && (offer == null || offer.reserved().isEmpty());
            } catch (IllegalArgumentException invalid) {
                return false;
            }
        }
    }

    /** Rehydrate the saved generation; current world members never create new qualification. */
    public boolean restore() {
        synchronized (scene) {
            var owner = data(scene.getHost());
            if (owner == null
                    || !eligible(scene.getHost(), config(owner), System.currentTimeMillis()))
                return false;
            final AsterBigRewardState.View offer;
            synchronized (owner) {
                try {
                    offer = AsterSchedule.progress(owner).bigReward();
                } catch (RuntimeException invalid) {
                    return false;
                }
            }
            if (point != null) {
                if (offer != null
                        && point.owner == owner
                        && offer.token().equals(point.token)
                        && current(point)) return true;
                close();
            }
            if (offer == null
                    || offer.remaining().isEmpty()
                    || !offer.rewards().containsKey(owner.getUid())
                    || offer.rewards().values().stream().anyMatch(r -> !canonical(r))) return false;
            var scripts = scene.getScriptManager();
            var group = scripts.getLoadedGroupById(AsterBigRewards.GROUP);
            if (group == null || !scripts.allowsGroupCall(group)) return false;
            var meta = group.gadgets.get(AsterBigRewards.CONFIG);
            if (meta == null
                    || meta.gadget_id != AsterBigRewards.GADGET
                    || scene.getEntityByConfigId(AsterBigRewards.CONFIG, AsterBigRewards.GROUP)
                            != null) return false;
            var entity = scripts.createGadget(group.id, group.block_id, meta);
            return entity != null
                    && owns(entity)
                    && publish(
                            entity,
                            owner,
                            offer.token(),
                            () ->
                                    scripts.getLoadedGroupById(group.id) == group
                                            && scripts.allowsGroupCall(group));
        }
    }

    private static boolean canonical(AsterBigRewards.Reward frozen) {
        var actual = AsterBigRewards.reward(frozen.worldLevel());
        return actual != null
                && actual.dropId() == frozen.dropId()
                && actual.adventureExp() == frozen.adventureExp()
                && actual.companionshipExp() == frozen.companionshipExp()
                && actual.essence() == frozen.essence();
    }

    /** Complete validation precedes this acknowledged retirement and the next round publication. */
    boolean retireForNewBattle() {
        synchronized (scene) {
            if (!canReplace()) return false;
            var owner = data(scene.getHost());
            if (owner != null) {
                synchronized (owner) {
                    String before = owner.getDetail();
                    boolean storing = false;
                    try {
                        var progress = AsterSchedule.progress(owner);
                        if (progress.bigReward() != null) {
                            progress.retireBigReward();
                            owner.setDetail(progress);
                            storing = true;
                            owner.saveSync();
                        }
                    } catch (RuntimeException failure) {
                        owner.setDetailJson(before);
                        if (storing) {
                            try {
                                owner.saveSync();
                            } catch (RuntimeException restoreFailure) {
                                if (restoreFailure != failure)
                                    failure.addSuppressed(restoreFailure);
                            }
                        }
                        Grasscutter.getLogger()
                                .error(
                                        "Aster large reward retirement was not acknowledged",
                                        failure);
                        return false;
                    }
                }
            }
            close();
            return true;
        }
    }

    boolean create(BooleanSupplier current) {
        synchronized (scene) {
            var owner = data(scene.getHost());
            var config = config(owner);
            if (owner == null
                    || !eligible(scene.getHost(), config, System.currentTimeMillis())
                    || !canReplace()) return false;
            var group = scene.getScriptManager().getLoadedGroupById(AsterBigRewards.GROUP);
            if (group == null || !scene.getScriptManager().allowsGroupCall(group)) return false;
            var meta = group.gadgets.get(AsterBigRewards.CONFIG);
            if (meta == null || meta.gadget_id != AsterBigRewards.GADGET) return false;
            var existing = scene.getEntityByConfigId(AsterBigRewards.CONFIG, AsterBigRewards.GROUP);
            if (existing != null && (point == null || existing != point.entity)) return false;
            var rewards = new LinkedHashMap<Integer, AsterBigRewards.Reward>();
            // Historical BaseScenePlayBattle::createGeneralRewardGadget enumerates the whole world.
            for (var player : scene.getWorld().getPlayers()) {
                var reward = AsterBigRewards.reward(player.getWorldLevel());
                if (player.getUid() <= 0
                        || reward == null
                        || rewards.putIfAbsent(player.getUid(), reward) != null) return false;
            }
            if (rewards.isEmpty() || rewards.size() > 4 || !rewards.containsKey(owner.getUid()))
                return false;
            var entity = scene.getScriptManager().createGadget(group.id, group.block_id, meta);
            if (entity == null || !owns(entity)) return false;
            final String token;
            synchronized (owner) {
                var progress = AsterSchedule.progress(owner);
                var old = progress.bigReward();
                if (old != null && !old.reserved().isEmpty()) return false;
                String before = owner.getDetail();
                progress.openBigReward(rewards);
                token = progress.bigReward().token();
                owner.setDetail(progress);
                try {
                    owner.saveSync();
                } catch (RuntimeException failure) {
                    owner.setDetailJson(before);
                    throw failure;
                }
            }
            return publish(entity, owner, token, current);
        }
    }

    private boolean publish(
            EntityGadget entity, PlayerActivityData owner, String token, BooleanSupplier current) {
        close();
        point = new Point(entity, owner, token, current);
        try {
            scene.addEntity(entity);
        } catch (RuntimeException failure) {
            if (scene.getEntities().get(entity.getId()) != entity) {
                point = null;
                throw failure;
            }
            // Registration succeeded. Preserve the generation if appearance broadcast failed.
            Grasscutter.getLogger().error("Aster large reward appearance failed", failure);
        }
        return true;
    }

    public GadgetGeneralRewardInfo info(EntityGadget gadget) {
        synchronized (scene) {
            var selected = point;
            if (selected == null || selected.entity != gadget || !current(selected))
                return GadgetGeneralRewardInfo.getDefaultInstance();
            synchronized (selected.owner) {
                var offer = AsterSchedule.progress(selected.owner).bigReward();
                if (offer == null || !offer.token().equals(selected.token))
                    return GadgetGeneralRewardInfo.getDefaultInstance();
                return GadgetGeneralRewardInfo.newBuilder()
                        .setResin(AsterBigRewards.RESIN)
                        .addAllQualifyUidList(offer.rewards().keySet().stream().sorted().toList())
                        .addAllRemainUidList(offer.remaining().stream().sorted().toList())
                        .build();
                // Original base point: dead_time=0 and destroy_cd=0, unlike the mid recovery point.
            }
        }
    }

    public boolean interact(Player player, EntityGadget gadget, GadgetInteractReq req) {
        synchronized (scene) {
            int code = Retcode.RET_ACTIVITY_CLOSE_VALUE;
            var selected = point;
            var claimant = data(player);
            long now = System.currentTimeMillis();
            if (selected != null
                    && selected.entity == gadget
                    && current(selected)
                    && owns(gadget)
                    && eligible(player, config(selected.owner), now)
                    && claimant != null
                    && player.getScene() == scene
                    && player.getWorld() == scene.getWorld()
                    && scene.getPlayers().contains(player)
                    && scene.getWorld().getPlayers().contains(player)
                    && player.getSceneLoadState() == Player.SceneLoadState.LOADED
                    && req.getGadgetEntityId() == gadget.getId()
                    && (req.getGadgetId() == 0 || req.getGadgetId() == gadget.getGadgetId())
                    && player.getPosition().computeDistance(gadget.getPosition()) <= 5) {
                AsterBigRewardState.View offer;
                synchronized (selected.owner) {
                    offer = AsterSchedule.progress(selected.owner).bigReward();
                }
                code = Retcode.RET_ACTIVITY_ITEM_ERROR_VALUE;
                if (offer != null
                        && offer.token().equals(selected.token)
                        && offer.canClaim(player.getUid())) {
                    if (req.getOpType() == InterOpType.InterOpType_INTER_OP_START) {
                        selected.previewed.add(player.getUid());
                        code = 0;
                    } else if (req.getOpType() != InterOpType.InterOpType_INTER_OP_FINISH
                            || !selected.previewed.contains(player.getUid())
                            || req.getResinCostType() != ResinCostType.ResinCostType_NONE
                                    && req.getResinCostType() != ResinCostType.ResinCostType_NORMAL)
                        code = Retcode.RET_FORBIDDEN_VALUE;
                    else {
                        try {
                            synchronized (player.getInventory()) {
                                code =
                                        AsterBigRewards.takeShared(
                                                selected.owner,
                                                claimant,
                                                config(selected.owner),
                                                player.getLevel(),
                                                now,
                                                selected.token,
                                                () ->
                                                        player.getResinManager()
                                                                .useResin(AsterBigRewards.RESIN),
                                                r ->
                                                        AsterRewardDelivery.grant(
                                                                player,
                                                                r.adventureExp(),
                                                                r.companionshipExp(),
                                                                110),
                                                selected.owner::saveSync,
                                                claimant::saveSync);
                            }
                            if (code == 0) {
                                selected.previewed.remove(player.getUid());
                                var reward = offer.rewards().get(player.getUid());
                                player.sendPacket(
                                        new PacketItemAddHintNotify(
                                                new GameItem(110, reward.essence()),
                                                ActionReason.TakeGeneralReward));
                                if (!retireIfEmpty(selected)) notifyPoint(gadget);
                            }
                        } catch (RuntimeException failure) {
                            code = Retcode.RET_SVR_ERROR_VALUE;
                            Grasscutter.getLogger()
                                    .error(
                                            "Aster large reward needs reconciliation: uid {} token"
                                                    + " {}",
                                            player.getUid(),
                                            selected.token,
                                            failure);
                        }
                    }
                }
            }
            player.sendPacket(
                    new PacketGadgetInteractRsp(
                            gadget,
                            InteractType.InteractType_INTERACT_GENERAL_REWARD,
                            req.getOpType() == InterOpType.UNRECOGNIZED ? null : req.getOpType(),
                            code));
            return false; // A shared point survives an individual claim.
        }
    }

    private boolean retireIfEmpty(Point selected) {
        synchronized (selected.owner) {
            var offer = AsterSchedule.progress(selected.owner).bigReward();
            if (offer != null && offer.remaining().isEmpty() && offer.reserved().isEmpty()) {
                close();
                return true;
            }
            return false;
        }
    }

    private void notifyPoint(EntityGadget gadget) {
        var packet = new BasePacket(PacketOpcodes.GadgetGeneralRewardInfoNotify);
        packet.setData(
                GadgetGeneralRewardInfoNotify.newBuilder()
                        .setEntityId(gadget.getId())
                        .setGeneralRewardInfo(info(gadget))
                        .build());
        scene.broadcastPacket(packet);
    }

    void tick() {
        synchronized (scene) {
            if (point != null && !current(point)) close();
        }
    }

    public void close() {
        var selected = point;
        point = null;
        if (selected != null && scene.getEntities().get(selected.entity.getId()) == selected.entity)
            scene.removeEntity(selected.entity, VisionType.VisionType_VISION_MISS);
        // Durable reservations/receipts remain available for reconciliation after scene disposal.
    }
}
