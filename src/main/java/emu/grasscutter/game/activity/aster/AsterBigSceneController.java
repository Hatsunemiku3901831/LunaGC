package emu.grasscutter.game.activity.aster;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.activity.*;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.game.world.sceneplay.ScenePlayBattleManager;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.scripts.constants.EventType;
import emu.grasscutter.scripts.data.*;
import emu.grasscutter.server.packet.send.*;

import java.util.*;
import java.util.concurrent.*;

/** Day-8 original play-2 groups and consent for direct or matched battles. */
public final class AsterBigSceneController {
    public static final int PLAY_ID = 2, MAIN = 133001413, ENTRANCE = 413001;
    private static final Set<Integer> GROUPS = Set.of(MAIN, 133001426, 133001427, 133001498);
    private static final int INVITE_SECONDS = 30;
    private final Scene scene;
    private final Map<Integer, SceneGroup> mounted = new ConcurrentHashMap<>();
    private int scheduleId;
    private long generation;
    private long invitationSerial;
    private Future<?> opening;
    private Invitation invitation;

    private static final class Invitation {
        final Map<Integer, Player> members;
        final Set<Integer> agreed = new HashSet<>();
        final Map<Integer, Integer> worldLevels;
        final long generation;
        long deadline;
        final boolean matching;
        boolean queued;

        Invitation(
                Map<Integer, Player> members,
                int owner,
                long generation,
                long deadline,
                boolean matching) {
            this.members = Map.copyOf(members);
            var levels = new LinkedHashMap<Integer, Integer>();
            members.forEach((uid, player) -> levels.put(uid, player.getWorldLevel()));
            this.worldLevels = Map.copyOf(levels);
            this.agreed.add(owner);
            this.generation = generation;
            this.deadline = deadline;
            this.matching = matching;
        }
    }

    private record Check(int code, int uid) {
        static final Check OK = new Check(0, 0);

        Check(Retcode code, int uid) {
            this(code.getNumber(), uid);
        }
    }

    public AsterBigSceneController(Scene scene) {
        this.scene = scene;
    }

    public boolean owns(int id) {
        return scene.getId() == 3 && GROUPS.contains(id);
    }

    public static boolean resourcesAvailable() {
        var entry = GameData.getMpPlayGroupDataMap().get(PLAY_ID);
        var match = GameData.getMpPlayMatchDataMap().get(PLAY_ID);
        return entry != null
                && entry.getGroupList() != null
                && entry.getServerLuaCallGroupList() != null
                && new HashSet<>(entry.getGroupList())
                        .equals(Set.of(133001426, 133001427, 133001498))
                && entry.getServerLuaCallGroupList().equals(List.of(MAIN))
                && entry.getBornGroupId() == MAIN
                && entry.getBornConfigId() == 413073
                && entry.getRewardGroupId() == MAIN
                && entry.getRewardConfigId() == AsterBigRewards.CONFIG
                && entry.getRadius() == 250
                && entry.getPrepareTime() == 20
                && match != null
                && "MP_PLAY_ASTER".equals(match.getPlayType())
                && match.getMinPlayers() == 2
                && match.getMaxPlayers() == 4
                && GameData.getGadgetDataMap().containsKey(70900361)
                && GameData.getGadgetDataMap().containsKey(AsterBigRewards.GADGET);
    }

    private ActivityConfigItem config() {
        return ActivityManager.getScheduleActivityConfigMap().values().stream()
                .filter(c -> c.getActivityId() == AsterSchedule.ACTIVITY_ID)
                .findFirst()
                .orElse(null);
    }

    /** The chapter flag follows both personal unlocks and the currently visited host's schedule. */
    public static boolean openForWorld(
            PlayerActivityData data, ActivityConfigItem config, int rank, long now) {
        if (!resourcesAvailable() || !AsterSchedule.phaseOpen(data, config, rank, now, 3))
            return false;
        var player = data.getPlayer();
        var host = player == null || player.getWorld() == null ? null : player.getWorld().getHost();
        if (host == null || host == player) return true;
        var owner =
                host.getActivityManager() == null
                        ? null
                        : host.getActivityManager()
                                .getPlayerActivityDataMap()
                                .get(AsterSchedule.ACTIVITY_ID);
        return AsterSchedule.phaseOpen(owner, config, host.getLevel(), now, 3);
    }

    private PlayerActivityData data(Player player) {
        if (player == null || player.getActivityManager() == null) return null;
        var data =
                player.getActivityManager()
                        .getPlayerActivityDataMap()
                        .get(AsterSchedule.ACTIVITY_ID);
        return data != null && data.getPlayer() == player && data.getUid() == player.getUid()
                ? data
                : null;
    }

    private boolean eligible(Player player, ActivityConfigItem config, long now) {
        return player != null
                && AsterSchedule.phaseOpen(data(player), config, player.getLevel(), now, 3);
    }

    public boolean allows(SceneGroup group) {
        synchronized (scene) {
            var config = config();
            return group != null
                    && mounted.get(group.id) == group
                    && config != null
                    && config.getScheduleId() == scheduleId
                    && eligible(scene.getHost(), config, System.currentTimeMillis());
        }
    }

    public boolean allows(int id) {
        return !owns(id) || allows(mounted.get(id));
    }

    private EntityGadget entrance() {
        var entity = scene.getEntityByConfigId(ENTRANCE, MAIN);
        return entity instanceof EntityGadget gadget
                        && gadget.getGadgetId() == 70900361
                        && gadget.getMetaGadget() != null
                        && allows(gadget.getMetaGadget().group)
                ? gadget
                : null;
    }

    private void load() {
        var scripts = scene.getScriptManager();
        var entry = GameData.getMpPlayGroupDataMap().get(PLAY_ID);
        var definitions = new ArrayList<SceneGroup>();
        // Validate the entire dependency set before publishing any group or entrance.
        for (int id : List.of(133001426, 133001427, 133001498, MAIN)) {
            var group =
                    AsterMidSceneController.definition(
                                    id, entry.centerPosition(), scripts.getBlocks().values())
                            .load(3);
            if (group.init_config == null
                    || group.getSuiteByIndex(group.init_config.suite) == null
                    || group.variables == null
                    || group.gadgets == null
                    || group.monsters == null)
                throw new IllegalStateException("Aster large script incomplete: " + id);
            definitions.add(group);
        }
        var main = definitions.get(definitions.size() - 1);
        var gadget = main.gadgets.get(ENTRANCE);
        var reward = main.gadgets.get(AsterBigRewards.CONFIG);
        if (gadget == null
                || gadget.gadget_id != 70900361
                || reward == null
                || reward.gadget_id != AsterBigRewards.GADGET)
            throw new IllegalStateException("Aster large entrance/reward mismatch");
        definitions.forEach(group -> scene.loadBlock(scripts.getBlocks().get(group.block_id)));
        definitions.forEach(group -> mounted.put(group.id, group));
        scripts.registerLocalGroups(definitions);
        try {
            for (var group : definitions) {
                var cached = scripts.getCachedGroupInstanceById(group.id);
                if (cached != null) cached.reset();
            }
            scene.onLoadGroup(definitions);
            scene.onRegisterGroups();
            long ticket = generation;
            opening =
                    scripts.callEvent(
                            new ScriptArgs(MAIN, EventType.EVENT_SCENE_MP_PLAY_OPEN, PLAY_ID)
                                    .setUid(scene.getHost().getUid()),
                            () -> ticket == generation && allows(main),
                            true);
        } catch (RuntimeException failure) {
            close();
            throw failure;
        }
    }

    public void update(long now) {
        if (scene.getId() != 3) return;
        synchronized (scene) {
            var config = config();
            boolean active =
                    resourcesAvailable()
                            && eligible(scene.getHost(), config, now)
                            && !scene.getPlayers().isEmpty();
            if (!active || scheduleId != 0 && config.getScheduleId() != scheduleId) close();
            if (!active) return;
            scheduleId = config.getScheduleId();
            boolean nearby = scene.getPlayers().stream().anyMatch(player -> inside(player));
            var info = scene.getScenePlayBattleManager().getInfo(MAIN);
            boolean busy =
                    invitation != null
                            || info != null
                                    && info.getState() >= ScenePlayBattleManager.INIT
                                    && info.getState() <= ScenePlayBattleManager.START
                            || scene.getEntityByConfigId(AsterBigRewards.CONFIG, MAIN) != null;
            if ((nearby || busy) && mounted.isEmpty()) load();
            else if (!nearby && !busy && !mounted.isEmpty()) close();
            if (!mounted.isEmpty()
                    && opening != null
                    && opening.isDone()
                    && scene.getScenePlayBattleManager().getInfo(MAIN).getState() == 0)
                scene.getScenePlayBattleManager().getRewards().restore();
            if (invitation != null && (now / 1000 >= invitation.deadline || !invitationCurrent()))
                cancelInvitation();
        }
    }

    public void close() {
        synchronized (scene) {
            generation++;
            cancelInvitation();
            scene.getScenePlayBattleManager().getRewards().close();
            var groups = List.copyOf(mounted.values());
            mounted.clear(); // Invalidate queued callbacks before disposing the exact definitions.
            for (var group : groups) {
                try {
                    scene.unloadGroup(
                            scene.getScriptManager().getBlocks().get(group.block_id), group);
                } finally {
                    scene.getScriptManager().unregisterLocalGroups(List.of(group.id));
                }
            }
            scheduleId = 0;
            opening = null;
        }
    }

    private boolean inside(Player player) {
        var center = GameData.getMpPlayGroupDataMap().get(PLAY_ID).centerPosition();
        double x = player.getPosition().getX() - center.getX(),
                z = player.getPosition().getZ() - center.getZ();
        int radius = GameData.getMpPlayGroupDataMap().get(PLAY_ID).getRadius();
        return x * x + z * z <= (double) radius * radius;
    }

    private Check checkMember(Player player) {
        int uid = player.getUid();
        if (player.getScene() != scene
                || player.getWorld() != scene.getWorld()
                || !scene.getPlayers().contains(player)
                || !scene.getWorld().getPlayers().contains(player)
                || player.getSceneLoadState() != Player.SceneLoadState.LOADED
                || scene.getWorld().getServer().getPlayers().get(uid) != player)
            return new Check(Retcode.RET_MP_GUEST_LOADING_FIRST_ENTER, uid);
        if (!eligible(player, config(), System.currentTimeMillis()))
            return new Check(Retcode.RET_MP_PLAY_NOT_ACTIVE, uid);
        if (!inside(player)) return new Check(Retcode.RET_MP_OWNER_NOT_ENTER, uid);
        if (player.getTeamManager().getActiveTeam().stream()
                .noneMatch(avatar -> avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP) > 0))
            return new Check(Retcode.RET_MP_REPLY_NO_VALID_AVATAR, uid);
        return Check.OK;
    }

    private Check checkOwner(Player owner, int id, boolean skipMatch, List<Player> players) {
        if (owner != scene.getHost())
            return new Check(Retcode.RET_MP_NOT_IN_MY_WORLD, owner.getUid());
        var entrance = entrance();
        if (id != PLAY_ID
                || !resourcesAvailable()
                || entrance == null
                || opening == null
                || !opening.isDone()) return new Check(Retcode.RET_MP_PLAY_NOT_ACTIVE, 0);
        if (invitation != null || !scene.getScenePlayBattleManager().canPrepare(PLAY_ID))
            return new Check(Retcode.RET_MP_IN_MP_PLAY_BATTLE, 0);
        if (!entrance.isInteractEnabled()) return new Check(Retcode.RET_MP_PLAY_NOT_ACTIVE, 0);
        if (!skipMatch && !GameData.getMpPlayMatchDataMap().get(PLAY_ID).isAutoMatch())
            return new Check(Retcode.RET_MP_MATCH_PLAY_NOT_OPEN, 0);
        if (players.isEmpty() || players.size() > 4 || !players.contains(owner))
            return new Check(Retcode.RET_MP_WORLD_IS_FULL, 0);
        for (var player : players) {
            var check = checkMember(player);
            if (check.code() != 0) return check;
        }
        return Check.OK;
    }

    public void checkOwnerRequest(Player owner, int id, boolean skipMatch) {
        synchronized (scene) {
            var check =
                    checkOwner(owner, id, skipMatch, List.copyOf(scene.getWorld().getPlayers()));
            if (check.code() == 0 && hasReward(owner))
                check = new Check(Retcode.RET_MP_PLAY_REMAIN_REWARDS, owner.getUid());
            owner.sendPacket(PacketMpPlay.ownerCheck(id, skipMatch, check.code(), check.uid()));
        }
    }

    public void startInvitation(Player owner, int id, boolean skipMatch) {
        synchronized (scene) {
            var players = List.copyOf(scene.getWorld().getPlayers());
            var check = checkOwner(owner, id, skipMatch, players);
            owner.sendPacket(PacketMpPlay.startInvite(id, skipMatch, check.code()));
            if (check.code() != 0) return;
            var members = new LinkedHashMap<Integer, Player>();
            for (var player : players) members.put(player.getUid(), player);
            invitation =
                    new Invitation(
                            members,
                            owner.getUid(),
                            generation,
                            System.currentTimeMillis() / 1000 + INVITE_SECONDS,
                            !skipMatch);
            invitationSerial++;
            if (members.size() == 1) completeInvitation();
            else
                for (var player : members.values())
                    player.sendPacket(
                            PacketMpPlay.ownerInvite(id, INVITE_SECONDS, hasReward(player)));
        }
    }

    private boolean hasReward(Player player) {
        var data = data(scene.getHost());
        if (data == null) return false;
        synchronized (data) {
            var reward = AsterSchedule.progress(data).bigReward();
            return reward != null && reward.remaining().contains(player.getUid());
        }
    }

    private boolean invitationCurrent() {
        return invitation != null
                && invitation.generation == generation
                && entrance() != null
                && scene.getWorld().getPlayers().size() == invitation.members.size()
                && invitation.members.values().stream()
                        .allMatch(player -> checkMember(player).code() == 0);
    }

    public void replyInvitation(Player player, int id, boolean agree) {
        synchronized (scene) {
            if (id != PLAY_ID
                    || !invitationCurrent()
                    || invitation.queued
                    || System.currentTimeMillis() / 1000 >= invitation.deadline
                    || invitation.members.get(player.getUid()) != player
                    || player == scene.getHost()
                    || invitation.agreed.contains(player.getUid())) {
                player.sendPacket(
                        PacketMpPlay.guestReplyResponse(id, Retcode.RET_MP_REPLY_TIMEOUT_VALUE));
                return;
            }
            player.sendPacket(PacketMpPlay.guestReplyResponse(id, 0));
            sendToMembers(PacketMpPlay.guestReply(id, player.getUid(), agree));
            if (!agree) cancelInvitation();
            else {
                invitation.agreed.add(player.getUid());
                if (invitation.agreed.size() == invitation.members.size()) completeInvitation();
            }
        }
    }

    private void completeInvitation() {
        if (!invitation.matching) {
            beginPreparation();
            return;
        }
        invitation.queued = true;
        invitation.deadline = System.currentTimeMillis() / 1000 + 300;
        sendToMembers(PacketMpPlay.inviteResult(PLAY_ID, true));
    }

    public record MatchingTeam(
            Scene source,
            int scheduleId,
            long generation,
            long serial,
            List<Player> players,
            Map<Integer, Integer> worldLevels) {
        public MatchingTeam {
            players = List.copyOf(players);
            worldLevels = Map.copyOf(worldLevels);
        }
    }

    /** The server observes consent outside scene/world locks; only a solo caller may start here. */
    public MatchingTeam matchingTeam(Player owner, int playId, boolean permitSoloStart) {
        synchronized (scene) {
            if (permitSoloStart
                    && invitation == null
                    && scene.getWorld().getPlayers().size() == 1
                    && checkOwner(owner, playId, false, List.of(owner)).code() == 0)
                startInvitation(owner, playId, false);
            if (playId != PLAY_ID
                    || owner != scene.getHost()
                    || invitation == null
                    || !invitation.queued) return null;
            var team =
                    new MatchingTeam(
                            scene,
                            scheduleId,
                            generation,
                            invitationSerial,
                            List.copyOf(invitation.members.values()),
                            invitation.worldLevels);
            return isMatching(team) ? team : null;
        }
    }

    public boolean isMatching(MatchingTeam team) {
        synchronized (scene) {
            return team != null
                    && team.source() == scene
                    && team.scheduleId() == scheduleId
                    && team.generation() == generation
                    && team.serial() == invitationSerial
                    && invitation != null
                    && invitation.queued
                    && invitationCurrent()
                    && System.currentTimeMillis() / 1000 < invitation.deadline
                    && invitation.worldLevels.equals(team.worldLevels())
                    && new HashSet<>(team.players())
                            .equals(new HashSet<>(invitation.members.values()))
                    && scene.getWorld().getWorldLevel()
                            == team.worldLevels().get(scene.getHost().getUid())
                    && team.players().stream()
                            .allMatch(p -> p.getWorldLevel() == team.worldLevels().get(p.getUid()));
        }
    }

    public void releaseMatching(long serial) {
        synchronized (scene) {
            if (invitation != null && invitation.queued && serial == invitationSerial) {
                invitation = null;
                invitationSerial++;
            }
        }
    }

    /** Admission/loading finishes before enabling the original MATCH difficulty and battle. */
    public boolean startMatchedPreparation(Player owner, int schedule, List<Player> players) {
        synchronized (scene) {
            if (schedule != scheduleId
                    || checkOwner(owner, PLAY_ID, false, players).code() != 0
                    || !scene.getWorld().isMultiplayer()
                    || !new HashSet<>(scene.getWorld().getPlayers()).equals(new HashSet<>(players)))
                return false;
            boolean success =
                    scene.getScenePlayBattleManager()
                            .prepare(
                                    GameData.getMpPlayGroupDataMap().get(PLAY_ID),
                                    GameData.getMpPlayMatchDataMap().get(PLAY_ID),
                                    2,
                                    ScenePlayBattleManager.MATCH,
                                    players);
            if (success)
                scene.broadcastPacket(
                        new PacketMpPlayPrepareNotify(
                                PLAY_ID,
                                scene.getScenePlayBattleManager()
                                        .getInfo(MAIN)
                                        .getPrepareEndTime()));
            return success;
        }
    }

    private void beginPreparation() {
        boolean success =
                invitationCurrent()
                        && scene.getScenePlayBattleManager()
                                .prepare(
                                        GameData.getMpPlayGroupDataMap().get(PLAY_ID),
                                        GameData.getMpPlayMatchDataMap().get(PLAY_ID),
                                        2,
                                        ScenePlayBattleManager.DIRECT,
                                        invitation.members.values());
        sendToMembers(PacketMpPlay.inviteResult(PLAY_ID, success));
        invitation = null;
        if (success)
            scene.broadcastPacket(
                    new PacketMpPlayPrepareNotify(
                            PLAY_ID,
                            scene.getScenePlayBattleManager().getInfo(MAIN).getPrepareEndTime()));
    }

    private void sendToMembers(emu.grasscutter.net.packet.BasePacket packet) {
        if (invitation != null)
            invitation.members.values().forEach(player -> player.sendPacket(packet));
    }

    private void cancelInvitation() {
        sendToMembers(PacketMpPlay.inviteResult(PLAY_ID, false));
        invitation = null;
    }

    public void onPlayerLeaving(Player player) {
        synchronized (scene) {
            if (invitation != null && invitation.members.get(player.getUid()) == player)
                cancelInvitation();
        }
    }
}
