package emu.grasscutter.game.activity.aster;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.activity.*;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AsterMidCampInfoNotifyOuterClass.AsterMidCampInfoNotify;
import emu.grasscutter.net.proto.AsterMidDetailInfoOuterClass.AsterMidDetailInfo;
import emu.grasscutter.net.proto.AsterMidInfoNotifyOuterClass.AsterMidInfoNotify;
import emu.grasscutter.net.proto.GadgetGeneralRewardInfoNotifyOuterClass.GadgetGeneralRewardInfoNotify;
import emu.grasscutter.net.proto.GadgetGeneralRewardInfoOuterClass.GadgetGeneralRewardInfo;
import emu.grasscutter.net.proto.GadgetInteractReqOuterClass.GadgetInteractReq;
import emu.grasscutter.net.proto.InterOpTypeOuterClass.InterOpType;
import emu.grasscutter.net.proto.InteractTypeOuterClass.InteractType;
import emu.grasscutter.net.proto.ResinCostTypeOuterClass.ResinCostType;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.scripts.constants.*;
import emu.grasscutter.scripts.data.*;
import emu.grasscutter.server.packet.send.*;

import java.util.*;
import java.util.concurrent.*;

/** The host owns the camps; each qualified player owns their recovery rewards and progress. */
public final class AsterMidSceneController {
    private final Scene scene;
    private int scheduleId;
    private final Map<Integer, SceneGroup> mounted = new ConcurrentHashMap<>();
    private final Map<Integer, AsterMidDetailInfo> notified = new HashMap<>();
    private final Set<Long> previewed = new HashSet<>();
    private Round round;

    private static final class Round {
        final AsterMidCamps.Camp camp;
        final int difficulty;
        final SceneGroup group;
        final EntityGadget seal;
        final PlayerActivityData data;
        Future<?> selection;
        boolean ended, won;

        Round(
                AsterMidCamps.Camp camp,
                int difficulty,
                SceneGroup group,
                EntityGadget seal,
                PlayerActivityData data) {
            this.camp = camp;
            this.difficulty = difficulty;
            this.group = group;
            this.seal = seal;
            this.data = data;
        }
    }

    public AsterMidSceneController(Scene scene) {
        this.scene = scene;
    }

    public boolean owns(int id) {
        return scene.getId() == 3 && AsterMidCamps.owns(id);
    }

    private ActivityConfigItem config() {
        return ActivityManager.getScheduleActivityConfigMap().values().stream()
                .filter(c -> c.getActivityId() == AsterSchedule.ACTIVITY_ID)
                .findFirst()
                .orElse(null);
    }

    private PlayerActivityData data(Player player) {
        return player == null || player.getActivityManager() == null
                ? null
                : player.getActivityManager()
                        .getPlayerActivityDataMap()
                        .get(AsterSchedule.ACTIVITY_ID);
    }

    private boolean eligible(Player player, ActivityConfigItem config, long now) {
        return player != null
                && AsterMidCamps.resourcesAvailable()
                && AsterSchedule.phaseOpen(data(player), config, player.getLevel(), now, 2);
    }

    public boolean allows(SceneGroup group) {
        synchronized (scene) {
            var config = config();
            return group != null
                    && mounted.get(group.id) == group
                    && config != null
                    && scheduleId == config.getScheduleId()
                    && eligible(scene.getHost(), config, System.currentTimeMillis());
        }
    }

    public boolean allowsGadget(int groupId, SceneGadget gadget) {
        if (!owns(groupId)) return true;
        synchronized (scene) {
            if (gadget == null
                    || !allows(gadget.group)
                    || scene.getEntityByConfigId(gadget.config_id, groupId) != null) return false;
            var camp = AsterMidCamps.byGroup(groupId);
            if (camp == null) return true;
            var data = data(scene.getHost());
            synchronized (data) {
                var progress = AsterSchedule.progress(data);
                return progress.midCampAvailable(camp.campId())
                        && (gadget.config_id != camp.rewardConfigId()
                                || progress.midOffers().containsKey(camp.campId()));
            }
        }
    }

    public int initialState(int groupId, SceneGadget gadget, int state) {
        var camp = AsterMidCamps.byGroup(groupId);
        var data = data(scene.getHost());
        if (camp == null || data == null || gadget.config_id != camp.sealConfigId()) return state;
        synchronized (data) {
            return AsterSchedule.progress(data).midOffers().containsKey(camp.campId())
                    ? ScriptGadgetState.GearStop
                    : state;
        }
    }

    public static AsterMidDetailInfo info(
            PlayerActivityData data, ActivityConfigItem config, int rank, long now) {
        var builder = AsterMidDetailInfo.newBuilder();
        if (config != null) builder.setBeginTime((int) (AsterSchedule.beginTime(config, 3) / 1000));
        if (data == null) return builder.build();
        synchronized (data) {
            var progress = AsterSchedule.progress(data);
            builder.setCollectCount(progress.midCollectCount());
            boolean open =
                    AsterSchedule.phaseOpen(data, config, rank, now, 2)
                            && AsterMidCamps.resourcesAvailable();
            builder.setIsOpen(open);
            var batch = GameData.getAsterMidDataMap().get(progress.midRefreshId());
            if (open && batch != null)
                batch.getCampVec().stream()
                        .filter(progress::midCampAvailable)
                        .map(AsterMidCamps.camps()::get)
                        .filter(Objects::nonNull)
                        .map(AsterMidCamps.Camp::toProto)
                        .forEach(builder::addCampList);
        }
        return builder.build();
    }

    public static AsterMidDetailInfo infoForWorld(
            PlayerActivityData data, ActivityConfigItem config, int rank, long now) {
        var personal = info(data, config, rank, now);
        var player = data == null ? null : data.getPlayer();
        var world = player == null ? null : player.getWorld();
        var host = world == null ? null : world.getHost();
        if (host == null || host == player || host.getActivityManager() == null) return personal;
        var owner =
                host.getActivityManager().getPlayerActivityDataMap().get(AsterSchedule.ACTIVITY_ID);
        var hosted = info(owner, config, host.getLevel(), now);
        return personal.toBuilder()
                .clearCampList()
                .addAllCampList(personal.getIsOpen() ? hosted.getCampListList() : List.of())
                .build();
    }

    private void ensureBatch(PlayerActivityData data) {
        synchronized (data) {
            var progress = AsterSchedule.progress(data);
            if (progress.midRefreshId() != 0 && !progress.midBatchFinished()) return;
            var choices = GameData.getAsterMidDataMap().keySet().intStream().sorted().toArray();
            if (choices.length == 0) throw new IllegalStateException("Aster camp batches missing");
            String before = data.getDetail();
            progress.startMidBatch(choices[ThreadLocalRandom.current().nextInt(choices.length)]);
            data.setDetail(progress);
            try {
                data.saveSync();
            } catch (RuntimeException failure) {
                data.setDetailJson(before);
                throw failure;
            }
        }
    }

    public static SceneGroup definition(int groupId, Position pos, Collection<SceneBlock> blocks) {
        var block =
                blocks.stream()
                        .filter(
                                b ->
                                        b.min != null
                                                && b.max != null
                                                && pos.getX() >= b.min.getX()
                                                && pos.getX() <= b.max.getX()
                                                && pos.getZ() >= b.min.getZ()
                                                && pos.getZ() <= b.max.getZ())
                        .min(Comparator.comparingInt(b -> b.id))
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Aster camp block missing: " + groupId));
        var group = SceneGroup.of(groupId);
        group.block_id = block.id;
        group.pos = new Position(pos);
        group.dynamic_load = true;
        group.refresh_id = 99999;
        return group;
    }

    private void loadGroup(int id, Position pos) {
        if (mounted.containsKey(id)) return;
        var manager = scene.getScriptManager();
        var group = definition(id, pos, manager.getBlocks().values());
        group.load(3);
        if (group.init_config == null
                || group.suites == null
                || group.variables == null
                || group.gadgets == null
                || group.monsters == null)
            throw new IllegalStateException("Aster camp script incomplete: " + id);
        scene.loadBlock(manager.getBlocks().get(group.block_id));
        mounted.put(id, group);
        manager.registerLocalGroups(List.of(group));
        try {
            var cached = manager.getCachedGroupInstanceById(id);
            if (cached != null) cached.reset();
            scene.onLoadGroup(List.of(group));
            scene.onRegisterGroups();
        } catch (RuntimeException failure) {
            unload(id);
            throw failure;
        }
    }

    private void load(AsterMidCamps.Camp camp) {
        for (var entry : camp.battleGroups().entrySet())
            loadGroup(entry.getKey(), entry.getValue());
        loadGroup(camp.groupId(), camp.groupPosition());
        var group = mounted.get(camp.groupId());
        var core = group.gadgets.get(camp.sealConfigId());
        var reward = group.gadgets.get(camp.rewardConfigId());
        if (core == null
                || core.gadget_id != camp.sealGadgetId()
                || reward == null
                || reward.gadget_id != 70950021)
            throw new IllegalStateException("Aster camp gadget mismatch: " + camp.campId());
        var ownerData = data(scene.getHost());
        synchronized (ownerData) {
            var difficulty = AsterSchedule.progress(ownerData).midOffers().get(camp.campId());
            if (difficulty != null) {
                var instance = scene.getScriptManager().getGroupInstanceById(group.id);
                instance.getCachedVariables().put("isDone", 1);
                instance.getCachedVariables().put("difficulty", difficulty);
                if (scene.getEntityByConfigId(camp.rewardConfigId(), group.id) == null)
                    spawnReward(camp, group);
            }
        }
    }

    private void unload(int id) {
        var group = mounted.remove(id);
        if (group == null) return;
        scene.getEntities().values().stream()
                .filter(e -> e.getGroupId() == id)
                .map(GameEntity::getId)
                .forEach(entity -> previewed.removeIf(key -> (int) key.longValue() == entity));
        if (round != null && (round.group == group || round.camp.battleGroups().containsKey(id)))
            round = null;
        try {
            scene.unloadGroup(scene.getScriptManager().getBlocks().get(group.block_id), group);
        } finally {
            scene.getScriptManager().unregisterLocalGroups(List.of(id));
        }
    }

    public void close() {
        synchronized (scene) {
            round = null;
            previewed.clear();
            for (int id : List.copyOf(mounted.keySet())) unload(id);
            scheduleId = 0;
            notified.clear();
        }
    }

    public void update(long now) {
        if (scene.getId() != 3) return;
        synchronized (scene) {
            var config = config();
            var host = scene.getHost();
            var data = data(host);
            boolean active = !scene.getPlayers().isEmpty() && eligible(host, config, now);
            if (!active || scheduleId != 0 && scheduleId != config.getScheduleId()) close();
            if (active) {
                scheduleId = config.getScheduleId();
                expireRewards(now);
                ensureBatch(data);
                AsterProgress progress;
                synchronized (data) {
                    progress = AsterSchedule.progress(data);
                }
                var batch = GameData.getAsterMidDataMap().get(progress.midRefreshId());
                var wanted = new HashSet<Integer>();
                for (int id : batch.getCampVec()) {
                    var camp = AsterMidCamps.camps().get(id);
                    boolean near =
                            scene.getPlayers().stream()
                                    .anyMatch(
                                            p ->
                                                    horizontalDistance(
                                                                    p.getPosition(),
                                                                    camp.position())
                                                            <= 900 * 900);
                    if (progress.midCampAvailable(id)
                            && (near || round != null && round.camp.campId() == id)) {
                        wanted.add(camp.groupId());
                        wanted.addAll(camp.battleGroups().keySet());
                        load(camp);
                    }
                }
                for (int id : List.copyOf(mounted.keySet())) if (!wanted.contains(id)) unload(id);
                if (round != null
                        && round.selection != null
                        && round.selection.isDone()
                        && scene.getSealBattleManager().isSettled(round.seal)) {
                    if (!round.ended || !round.won) {
                        cleanupFailed(round);
                        round = null;
                    } else createReward(round.group, round.camp.rewardConfigId(), round.difficulty);
                }
            }
            notified.keySet()
                    .removeIf(
                            uid ->
                                    scene.getWorld().getPlayers().stream()
                                            .noneMatch(p -> p.getUid() == uid));
            previewed.removeIf(
                    key ->
                            scene.getPlayers().stream()
                                    .noneMatch(p -> p.getUid() == (int) (key >>> 32)));
            if (config != null)
                for (var player : scene.getWorld().getPlayers()) {
                    var playerData = data(player);
                    if (playerData == null) continue;
                    var detail = worldInfo(player, config, now);
                    if (!detail.equals(notified.put(player.getUid(), detail)))
                        notify(player, detail);
                }
        }
    }

    private AsterMidDetailInfo worldInfo(Player player, ActivityConfigItem config, long now) {
        return infoForWorld(data(player), config, player.getLevel(), now);
    }

    private void restoreRewardStates(PlayerActivityData data) {
        synchronized (data) {
            var progress = AsterSchedule.progress(data);
            boolean changed = false;
            for (int camp : progress.midOffers().keySet())
                changed |= progress.upgradeMidReward(camp, data.getUid());
            if (!changed) return;
            String before = data.getDetail();
            data.setDetail(progress);
            try {
                data.saveSync();
            } catch (RuntimeException failure) {
                data.setDetailJson(before);
                throw failure;
            }
        }
    }

    public void expireRewards(long now) {
        synchronized (scene) {
            var config = config();
            var owner = data(scene.getHost());
            if (scene.getId() != 3
                    || config == null
                    || scheduleId != config.getScheduleId()
                    || !eligible(scene.getHost(), config, now)) return;
            restoreRewardStates(owner);
            Set<Integer> camps;
            synchronized (owner) {
                camps = AsterSchedule.progress(owner).midOffers().keySet();
            }
            for (int camp : camps) retireReward(camp, now);
        }
    }

    /** Called for loaded and unloaded camps, so a reconnect cannot restart an expired timer. */
    private boolean retireReward(int campId, long now) {
        var owner = data(scene.getHost());
        synchronized (owner) {
            var progress = AsterSchedule.progress(owner);
            if (!progress.finishMidReward(campId, now, scene.getWorld().isMultiplayer()))
                return false;
            String before = owner.getDetail();
            owner.setDetail(progress);
            try {
                owner.saveSync();
            } catch (RuntimeException failure) {
                owner.setDetailJson(before);
                throw failure;
            }
        }
        var camp = AsterMidCamps.camps().get(campId);
        var gadget = scene.getEntityByConfigId(camp.rewardConfigId(), camp.groupId());
        if (gadget instanceof EntityGadget reward) {
            previewed.removeIf(key -> (int) key.longValue() == reward.getId());
            scene.killEntity(reward);
            var group = mounted.get(camp.groupId());
            if (group != null)
                scene.getScriptManager()
                        .callEvent(
                                new ScriptArgs(
                                        group.id,
                                        EventType.EVENT_GENERAL_REWARD_DIE,
                                        camp.rewardConfigId()),
                                () ->
                                        allows(group)
                                                && scene.getScriptManager()
                                                                .getLoadedGroupById(group.id)
                                                        == group);
        }
        return true;
    }

    private void cleanupFailed(Round failed) {
        if (mounted.get(failed.group.id) != failed.group) return;
        for (int id : failed.camp.battleGroups().keySet()) {
            var instance = scene.getScriptManager().getGroupInstanceById(id);
            if (instance != null)
                scene.getScriptManager().refreshGroup(instance, failed.camp.emptySuite(), false);
        }
        var instance = scene.getScriptManager().getGroupInstanceById(failed.group.id);
        if (instance != null) {
            instance.getCachedVariables().put("isDone", 2);
            instance.getCachedVariables().put("isShow", 0);
        }
        if (scene.getEntities().get(failed.seal.getId()) == failed.seal)
            failed.seal.updateState(ScriptGadgetState.Default);
    }

    private void notify(Player player, AsterMidDetailInfo detail) {
        var packet = new BasePacket(PacketOpcodes.AsterMidInfoNotify);
        packet.setData(AsterMidInfoNotify.newBuilder().setInfo(detail).build());
        player.sendPacket(packet);
        var camps = new BasePacket(PacketOpcodes.AsterMidCampInfoNotify);
        camps.setData(
                AsterMidCampInfoNotify.newBuilder()
                        .addAllCampList(detail.getCampListList())
                        .build());
        player.sendPacket(camps);
        player.getActivityManager().triggerActivityConditions();
    }

    public int select(Player player, int expectedSchedule, int entityId, int difficulty) {
        synchronized (scene) {
            var config = config();
            var ownerData = data(player);
            if (scene.getId() != 3
                    || config == null
                    || config.getScheduleId() != expectedSchedule
                    || scheduleId != expectedSchedule
                    || !eligible(player, config, System.currentTimeMillis()))
                return Retcode.RET_ACTIVITY_CLOSE_VALUE;
            if (player != scene.getHost() || !present(player)) return Retcode.RET_FORBIDDEN_VALUE;
            if (round != null) return Retcode.RET_FREQUENT_VALUE;
            var entity = scene.getEntities().get(entityId);
            if (!(entity instanceof EntityGadget seal)) return Retcode.RET_ENTITY_NOT_EXIST_VALUE;
            var camp = AsterMidCamps.byGroup(seal.getGroupId());
            var group = camp == null ? null : mounted.get(camp.groupId());
            var meta = GameData.getAsterMidDifficultyDataMap().get(difficulty);
            if (camp == null
                    || group == null
                    || seal.getConfigId() != camp.sealConfigId()
                    || seal.getGadgetId() != camp.sealGadgetId()
                    || !allows(group)
                    || !near(player, seal)
                    || meta == null
                    || !meta.getWorldLevelVec().contains(player.getWorldLevel())
                    || AsterMidRewards.reward(difficulty) == null
                    || !scene.getSealBattleManager().isSettled(seal))
                return Retcode.RET_ACTIVITY_ITEM_ERROR_VALUE;
            synchronized (ownerData) {
                var progress = AsterSchedule.progress(ownerData);
                if (!progress.midCampAvailable(camp.campId())
                        || progress.midOffers().containsKey(camp.campId())
                        || progress.pendingMidClaim() != 0)
                    return Retcode.RET_ACTIVITY_ITEM_ERROR_VALUE;
            }
            var selected = new Round(camp, difficulty, group, seal, ownerData);
            round = selected;
            selected.selection =
                    scene.getScriptManager()
                            .callEvent(
                                    new ScriptArgs(
                                                    group.id,
                                                    EventType.EVENT_SELECT_DIFFICULTY,
                                                    seal.getConfigId(),
                                                    difficulty)
                                            .setSourceEntityId(seal.getId())
                                            .setParam3(meta.getMonsterLevel()),
                                    () -> current(selected));
            return 0;
        }
    }

    private boolean current(Round selected) {
        synchronized (scene) {
            return round == selected
                    && data(scene.getHost()) == selected.data
                    && allows(selected.group)
                    && scene.getEntities().get(selected.seal.getId()) == selected.seal;
        }
    }

    public int monsterLevel(int groupId, int fallback) {
        synchronized (scene) {
            if (round == null || !round.camp.battleGroups().containsKey(groupId) || !current(round))
                return fallback;
            return GameData.getAsterMidDifficultyDataMap().get(round.difficulty).getMonsterLevel();
        }
    }

    public void onBattleEnd(EntityGadget seal, boolean won) {
        synchronized (scene) {
            if (round != null && round.seal == seal) {
                round.ended = true;
                round.won = won;
            }
        }
    }

    public boolean createReward(SceneGroup group, int configId, int difficulty) {
        synchronized (scene) {
            var selected = round;
            if (selected == null
                    || !current(selected)
                    || !selected.ended
                    || !selected.won
                    || selected.group != group
                    || selected.difficulty != difficulty
                    || selected.camp.rewardConfigId() != configId) return false;
            synchronized (selected.data) {
                var progress = AsterSchedule.progress(selected.data);
                String before = selected.data.getDetail();
                var qualified = new HashSet<Integer>();
                for (var player : scene.getWorld().getPlayers()) qualified.add(player.getUid());
                if (!progress.offerMidReward(selected.camp.campId(), difficulty, qualified))
                    return false;
                selected.data.setDetail(progress);
                try {
                    selected.data.saveSync();
                } catch (RuntimeException failure) {
                    selected.data.setDetailJson(before);
                    throw failure;
                }
            }
            boolean created = spawnReward(selected.camp, group);
            // The persisted offer is restored on the next update if creation fails.
            round = null;
            return created;
        }
    }

    private boolean spawnReward(AsterMidCamps.Camp camp, SceneGroup group) {
        var meta = group.gadgets.get(camp.rewardConfigId());
        if (meta == null || meta.gadget_id != 70950021) return false;
        var entity = scene.getScriptManager().createGadget(group.id, group.block_id, meta);
        if (entity == null) return false;
        scene.addEntity(entity);
        return true;
    }

    public GadgetGeneralRewardInfo rewardInfo(EntityGadget gadget) {
        synchronized (scene) {
            var camp = AsterMidCamps.byGroup(gadget.getGroupId());
            var host = scene.getHost();
            var data = data(host);
            if (camp == null || data == null || gadget.getConfigId() != camp.rewardConfigId())
                return GadgetGeneralRewardInfo.getDefaultInstance();
            synchronized (data) {
                var progress = AsterSchedule.progress(data);
                var offer = progress.midReward(camp.campId());
                var reward = offer == null ? null : AsterMidRewards.reward(offer.difficulty());
                if (reward == null) return GadgetGeneralRewardInfo.getDefaultInstance();
                return GadgetGeneralRewardInfo.newBuilder()
                        .setResin(reward.resin())
                        .setDeadTime((int) offer.deadTime())
                        .addAllQualifyUidList(offer.qualified().stream().sorted().toList())
                        .addAllRemainUidList(offer.remaining().stream().sorted().toList())
                        .build();
            }
        }
    }

    private void notifyReward(EntityGadget gadget) {
        var packet = new BasePacket(PacketOpcodes.GadgetGeneralRewardInfoNotify);
        packet.setData(
                GadgetGeneralRewardInfoNotify.newBuilder()
                        .setEntityId(gadget.getId())
                        .setGeneralRewardInfo(rewardInfo(gadget))
                        .build());
        scene.broadcastPacket(packet);
    }

    private static long previewKey(Player player, EntityGadget gadget) {
        return ((long) player.getUid() << 32) | (gadget.getId() & 0xffff_ffffL);
    }

    public boolean interactReward(Player player, EntityGadget gadget, GadgetInteractReq req) {
        synchronized (scene) {
            var config = config();
            var data = data(player);
            var owner = data(scene.getHost());
            var camp = AsterMidCamps.byGroup(gadget.getGroupId());
            int code = Retcode.RET_ACTIVITY_CLOSE_VALUE;
            long now = System.currentTimeMillis();
            if (camp != null
                    && config != null
                    && config.getScheduleId() == scheduleId
                    && eligible(player, config, now)
                    && eligible(scene.getHost(), config, now)
                    && present(player)
                    && near(player, gadget)
                    && scene.getEntities().get(gadget.getId()) == gadget
                    && req.getGadgetEntityId() == gadget.getId()
                    && gadget.getConfigId() == camp.rewardConfigId()
                    && gadget.getGadgetId() == 70950021
                    && allows(mounted.get(gadget.getGroupId()))) {
                AsterMidRewardState.View offer;
                synchronized (owner) {
                    offer = AsterSchedule.progress(owner).midReward(camp.campId());
                }
                code = Retcode.RET_ACTIVITY_ITEM_ERROR_VALUE;
                if (offer != null
                        && offer.canClaim(player.getUid(), now)
                        && AsterMidRewards.reward(offer.difficulty()) != null) {
                    if (req.getOpType() == InterOpType.InterOpType_INTER_OP_START) {
                        previewed.add(previewKey(player, gadget));
                        code = 0;
                    } else if (req.getOpType() != InterOpType.InterOpType_INTER_OP_FINISH
                            || !previewed.contains(previewKey(player, gadget))
                            || req.getResinCostType() != ResinCostType.ResinCostType_NONE
                                    && req.getResinCostType() != ResinCostType.ResinCostType_NORMAL)
                        code = Retcode.RET_FORBIDDEN_VALUE;
                    else {
                        var reward = AsterMidRewards.reward(offer.difficulty());
                        try {
                            code =
                                    AsterMidRewards.takeShared(
                                            owner,
                                            data,
                                            config,
                                            player.getLevel(),
                                            now,
                                            camp.campId(),
                                            offer.token(),
                                            () -> player.getResinManager().useResin(reward.resin()),
                                            r -> AsterMidRewards.grant(player, r),
                                            owner::saveSync,
                                            data::saveSync);
                            if (code == 0) {
                                previewed.remove(previewKey(player, gadget));
                                for (int id = 1200110; id <= 1200115; id++)
                                    player.sendPacket(
                                            new PacketActivityUpdateWatcherNotify(
                                                    AsterSchedule.ACTIVITY_ID,
                                                    data.getWatcherInfoMap().get(id)));
                                player.sendPacket(
                                        new PacketItemAddHintNotify(
                                                new GameItem(109, reward.energy()),
                                                ActionReason.TakeGeneralReward));
                                if (!retireReward(camp.campId(), now)) notifyReward(gadget);
                                for (var member : scene.getWorld().getPlayers())
                                    if (data(member) != null)
                                        notify(member, worldInfo(member, config, now));
                            }
                        } catch (RuntimeException failure) {
                            code = Retcode.RET_SVR_ERROR_VALUE;
                            Grasscutter.getLogger()
                                    .error(
                                            "Aster recovery claim failed: uid {} camp {}",
                                            player.getUid(),
                                            camp.campId(),
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
            // The controller retires a shared point only after all claims or its deadline.
            return false;
        }
    }

    private boolean present(Player player) {
        return player.getScene() == scene
                && player.getWorld() == scene.getWorld()
                && scene.getWorld().getPlayers().contains(player)
                && scene.getPlayers().contains(player)
                && player.getSceneLoadState() == Player.SceneLoadState.LOADED;
    }

    private static double horizontalDistance(Position a, Position b) {
        double dx = a.getX() - (double) b.getX(), dz = a.getZ() - (double) b.getZ();
        return dx * dx + dz * dz;
    }

    private static boolean near(Player player, EntityGadget gadget) {
        double dy = player.getPosition().getY() - (double) gadget.getPosition().getY();
        return horizontalDistance(player.getPosition(), gadget.getPosition()) + dy * dy <= 25;
    }
}
