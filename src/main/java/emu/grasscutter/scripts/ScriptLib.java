package emu.grasscutter.scripts;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.activity.ActivityManager;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.TeamGlobalValues;
import emu.grasscutter.game.dungeons.challenge.DungeonChallenge;
import emu.grasscutter.game.dungeons.challenge.enums.FatherChallengeProperty;
import emu.grasscutter.game.dungeons.challenge.factory.ChallengeFactory;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.entity.gadget.GadgetWorktop;
import emu.grasscutter.game.entity.gadget.platform.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.*;
import emu.grasscutter.game.quest.enums.*;
import emu.grasscutter.game.world.*;
import emu.grasscutter.game.world.sceneplay.ScenePlayBattleParameters;
import emu.grasscutter.net.proto.EnterTypeOuterClass;
import emu.grasscutter.net.proto.VisionTypeOuterClass.VisionType;
import emu.grasscutter.scripts.constants.*;
import emu.grasscutter.scripts.data.*;
import emu.grasscutter.server.packet.send.*;
import io.netty.util.concurrent.FastThreadLocal;
import lombok.val;
import org.luaj.vm2.*;
import org.slf4j.*;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Supplier;

import static emu.grasscutter.GameConstants.ENTITY_ID_BIT_SHIFT;
import static emu.grasscutter.game.props.EnterReason.Lua;
import static emu.grasscutter.scripts.ScriptUtils.*;
import static emu.grasscutter.scripts.constants.GroupKillPolicy.*;

@SuppressWarnings("unused")
public class ScriptLib {
    public static final Logger logger = Grasscutter.getLogger();
    private final FastThreadLocal<SceneScriptManager> sceneScriptManager;
    private final FastThreadLocal<SceneGroup> currentGroup;
    private final FastThreadLocal<ScriptArgs> callParams;
    private final FastThreadLocal<GameEntity> currentEntity;

    public ScriptLib() {
        this.sceneScriptManager = new FastThreadLocal<>();
        this.currentGroup = new FastThreadLocal<>();
        this.callParams = new FastThreadLocal<>();
        this.currentEntity = new FastThreadLocal<>();
    }

    public void setSceneScriptManager(SceneScriptManager sceneScriptManager) {
        this.sceneScriptManager.set(sceneScriptManager);
    }

    public void removeSceneScriptManager() {
        this.sceneScriptManager.remove();
    }

    public SceneScriptManager getSceneScriptManager() {

        return Optional.of(sceneScriptManager.get()).get();
    }

    @Nullable public SceneScriptManager getSceneScriptManagerOrNull() {
        return sceneScriptManager.getIfExists();
    }

    private String printTable(LuaTable table) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        for (var meta : table.keys()) {
            sb.append(meta).append(":").append(table.get(meta)).append(",");
        }
        sb.append("}");
        return sb.toString();
    }

    public void setCurrentGroup(SceneGroup currentGroup) {
        this.currentGroup.set(currentGroup);
    }

    public void setCurrentCallParams(ScriptArgs callArgs) {
        this.callParams.set(callArgs);
    }

    public void removeCurrentCallParams() {
        this.callParams.remove();
    }

    /** A group function starts without the caller's entity or event parameters. */
    <T> T withGroupContext(SceneScriptManager manager, SceneGroup group, Supplier<T> callback) {
        var previousManager = sceneScriptManager.getIfExists();
        var previousGroup = currentGroup.getIfExists();
        var previousEntity = currentEntity.getIfExists();
        var previousParams = callParams.getIfExists();
        try {
            setSceneScriptManager(manager);
            setCurrentGroup(group);
            removeCurrentEntity();
            removeCurrentCallParams();
            return callback.get();
        } finally {
            if (previousParams == null) removeCurrentCallParams(); else setCurrentCallParams(previousParams);
            if (previousEntity == null) removeCurrentEntity(); else setCurrentEntity(previousEntity);
            if (previousGroup == null) removeCurrentGroup(); else setCurrentGroup(previousGroup);
            if (previousManager == null) removeSceneScriptManager(); else setSceneScriptManager(previousManager);
        }
    }

    public Optional<SceneGroup> getCurrentGroup() {
        return Optional.ofNullable(this.currentGroup.getIfExists());
    }

    public void removeCurrentGroup() {
        this.currentGroup.remove();
    }

    public void setCurrentEntity(GameEntity currentGroup) {
        this.currentEntity.set(currentGroup);
    }

    public void removeCurrentEntity() {
        this.currentEntity.remove();
    }

    public Optional<GameEntity> getCurrentEntity() {
        return Optional.ofNullable(this.currentEntity.getIfExists());
    }

    /** Restore the caller's Lua context, including nested callbacks and exceptions. */
    public <T> T withEntityContext(GameEntity entity, Supplier<T> callback) {
        var manager = entity.getScene().getScriptManager();
        var instance = manager.getGroupInstanceById(entity.getGroupId());
        var group = instance == null ? null : instance.getLuaGroup();
        if (group == null && entity instanceof EntityGadget gadget && gadget.getMetaGadget() != null)
            group = gadget.getMetaGadget().group;
        return withEntityContext(entity, group, callback);
    }

    /** A SpecificGroup action uses its selected group instead of the entity's own group. */
    public <T> T withEntityContext(GameEntity entity, SceneGroup group, Supplier<T> callback) {
        var previousManager = sceneScriptManager.getIfExists();
        var previousGroup = currentGroup.getIfExists();
        var previousEntity = currentEntity.getIfExists();
        try {
            setCurrentEntity(entity);
            setSceneScriptManager(entity.getScene().getScriptManager());
            if (group == null) removeCurrentGroup(); else setCurrentGroup(group);
            return callback.get();
        } finally {
            if (previousEntity == null) removeCurrentEntity(); else setCurrentEntity(previousEntity);
            if (previousGroup == null) removeCurrentGroup(); else setCurrentGroup(previousGroup);
            if (previousManager == null) removeSceneScriptManager(); else setSceneScriptManager(previousManager);
        }
    }

    /**
     * Keep ScriptLib's Java receiver while exposing per-call metadata expected by SLC scripts.
     * These fields belong to this invocation, rather than shared globals or player caches.
     */
    public LuaValue createAbilityContext(Ability ability, GameEntity target) {
        var fields = new LuaTable();
        fields.set("uid", ability.getPlayerOwner() == null ? 0 : ability.getPlayerOwner().getUid());
        fields.set("source_entity_id", ability.getOwner() == null ? 0 : ability.getOwner().getId());
        fields.set("target_entity_id", target.getId());
        fields.set("group_id", getCurrentGroup().map(group -> group.id).orElse(0));
        return createContext(fields);
    }

    public LuaValue createEventContext(ScriptArgs event, SceneGroup group) {
        var fields = new LuaTable();
        fields.set("uid", event.uid);
        fields.set("group_id", group == null ? event.group_id : group.id);
        fields.set("source_entity_id", event.source_eid);
        fields.set("target_entity_id", event.target_eid);
        return createContext(fields);
    }

    private LuaValue createContext(LuaTable fields) {
        return new LuaUserdata(this) {
            @Override
            public LuaValue get(LuaValue key) {
                var value = fields.rawget(key);
                return value.isnil() ? ScriptLoader.getScriptLibLua().get(key) : value;
            }
        };
    }

    private GameEntity createGadget(int configId, SceneGroup group) {
        var gadget = group.gadgets.get(configId);
        var entity = getSceneScriptManager().createGadget(group.id, group.block_id, gadget);
        if (entity == null) {
            logger.warn("[LUA] Create gadget null with cid: {} gid: {} bid: {}", configId, group.id, group.block_id);
            return null;
        }

        getSceneScriptManager().addEntity(entity);
        return entity;
    }

    @Nullable
    public EntityGadget getCurrentEntityGadget() {
        val entity = currentEntity.getIfExists();
        if (entity instanceof EntityGadget) {
            return (EntityGadget) entity;
        }
        return null;
    }

    private int killGroupEntityWithPolicy(SceneScriptManager sceneScriptManager, SceneGroup group, GroupKillPolicy killPolicy) {

        var targets = new ArrayList<SceneObject>();
        if (killPolicy == GROUP_KILL_MONSTER || killPolicy == GROUP_KILL_ALL) {
            targets.addAll(group.monsters.values());
        }
        if (killPolicy == GROUP_KILL_GADGET || killPolicy == GROUP_KILL_ALL) {
            targets.addAll(group.gadgets.values());
        }

        targets.forEach(o -> {
            var entity = sceneScriptManager.getScene().getEntityByConfigId(o.config_id, group.id);
            if (entity == null) {
                return;
            }
            sceneScriptManager.getScene().killEntity(entity, 0);
        });
        return 0;
    }

    private int killGroupEntityWithTable(SceneScriptManager sceneScriptManager, SceneGroup group, LuaTable lists) {

        var monsterList = lists.get("monsters");
        var gadgetList = lists.get("gadgets");

        int[] targets = new int[monsterList.length() + gadgetList.length()];
        int targetsIndex = 0;
        for (int i = 1; i <= monsterList.length(); i++, targetsIndex++) {
            targets[targetsIndex] = monsterList.get(i).optint(-1);
        }
        for (int i = 1; i <= gadgetList.length(); i++, targetsIndex++) {
            targets[targetsIndex] = gadgetList.get(i).optint(-1);
        }

        for (int cfgId : targets) {
            var entity = sceneScriptManager.getScene().getEntityByConfigId(cfgId, group.id);
            if (entity == null || cfgId == 0) {
                continue;
            }
            sceneScriptManager.getScene().killEntity(entity, 0);
        }
        return 0;
    }

    private void printLog(String source, String msg) {
        var currentGroup = this.currentGroup.getIfExists();
        if (currentGroup != null) {
            logger.trace("[LUA] {} {} {}", source, currentGroup.id, msg);
        } else {
            logger.trace("[LUA] {} {}", source, msg);
        }
    }

    public int ActivateDungeonCheckPoint(int var1) {
        logger.warn("[LUA] Call untested ActivateDungeonCheckPoint with {}", var1);

        var dungeonManager = getSceneScriptManager().getScene().getDungeonManager();
        if (dungeonManager == null) {
            return 1;
        }
        return dungeonManager.activateRespawnPoint(var1) ? 0 : 2;
    }

    public synchronized int ActiveChallenge(
            int challengeId,
            int challengeIndex,
            int timeLimitOrGroupId,
            int groupId,
            int objectiveKills,
            int param5) {
        logger.debug(
                "[LUA] Call ActiveChallenge with {},{},{},{},{},{}",
                challengeId,
                challengeIndex,
                timeLimitOrGroupId,
                groupId,
                objectiveKills,
                param5);

        var scene = getSceneScriptManager().getScene();
        var existingChallenge = scene.getChallenge();
        if (existingChallenge != null && existingChallenge.inProgress()) {
            logger.warn(
                    "ActiveChallenge: tried to create challenge while one is already in progress");
            return 0;
        }

        var players = scene.getPlayers();
        var metadata = GameData.getDungeonChallengeConfigDataMap().get(challengeIndex);
        if (players.isEmpty()
                || metadata == null
                || metadata.getChallengeType() == null
                || getCurrentGroup().isEmpty()) return 1;
        var towerManager = players.get(0).getTowerManager();
        var manager = scene.getDungeonManager();
        if (manager != null && manager.isTowerDungeon()) {
            switch (metadata.getChallengeType()) {
                case CHALLENGE_KILL_COUNT_IN_TIME,
                        CHALLENGE_KILL_COUNT_FAST,
                        CHALLENGE_KILL_MONSTER_IN_TIME,
                        CHALLENGE_TRIGGER_IN_TIME,
                        CHALLENGE_SURVIVE -> {
                    timeLimitOrGroupId = towerManager.resolveHalfTime(timeLimitOrGroupId);
                    if (timeLimitOrGroupId <= 0) return 1;
                }
                case CHALLENGE_TIME_FLY -> {
                    objectiveKills = towerManager.resolveHalfTime(objectiveKills);
                    if (objectiveKills <= 0) return 1;
                }
                default -> {}
            }
        }

        var challenge =
                ChallengeFactory.getChallenge(
                        challengeId,
                        challengeIndex,
                        timeLimitOrGroupId,
                        groupId,
                        objectiveKills,
                        param5,
                        scene,
                        getCurrentGroup().get());

        if (challenge == null || challenge.getGroup() == null) {
            return 1;
        }

        if (challenge instanceof DungeonChallenge dungeonChallenge) {

            dungeonChallenge.setStage(
                    getSceneScriptManager().getVariables(groupId).getOrDefault("stage", -1) == 0);
        }

        scene.setChallenge(challenge);
        challenge.start();
        return 0;
    }

    public int AddBlossomScheduleProgressByGroupId(int groupId) {
        logger.warn("[LUA] Call unimplemented AddBlossomScheduleProgressByGroupId with {}", groupId);

        return 0;
    }

    public int AddExtraGroupSuite(int groupId, int suite) {
        logger.debug("[LUA] Call AddExtraGroupSuite with {},{}", groupId, suite);
        SceneGroup group = getSceneScriptManager().getGroupById(groupId);
        SceneGroupInstance groupInstance = getSceneScriptManager().getGroupInstanceById(groupId);
        if (group == null || groupInstance == null || group.monsters == null) {
            return 1;
        }
        var suiteData = group.getSuiteByIndex(suite);
        if (suiteData == null) {
            Grasscutter.getLogger().warn("trying to get suite that doesn't exist: {} {}", groupId, suite);
            return 1;
        }
        this.getSceneScriptManager().addGroupSuite(groupInstance, suiteData);
        return 0;
    }

    public int AddPlayerGroupVisionType(int[] uids, int[] var2) {
        logger.warn("[LUA] Call unimplemented AddPlayerGroupVisionType with {} {}", uids, var2);

        return 0;
    }

    public int AddQuestProgress(String var1) {
        logger.debug("[LUA] Call AddQuestProgress with {}", var1);
        for (var player : getSceneScriptManager().getScene().getPlayers()) {
            player.getPlayerProgress().addToCurrentProgress(var1, 1);
            player.getQuestManager().queueEvent(QuestCond.QUEST_COND_LUA_NOTIFY, var1);
            player.getQuestManager().queueEvent(QuestContent.QUEST_CONTENT_LUA_NOTIFY, var1);
        }

        return 0;
    }

    public int AddSceneTag(int sceneId, int sceneTagId) {
        logger.debug("[LUA] Call AddSceneTag with {}, {}", sceneId, sceneTagId);
        getSceneScriptManager().getScene().getHost().getProgressManager().addSceneTag(sceneId, sceneTagId);
        return 0;
    }

    public int AddTeamEntityGlobalFloatValue(int[] sceneUidList, String key, double delta) {
        return TeamGlobalValues.add(teamValueScene(), sceneUidList, key, delta);
    }

    public int AddTeamServerGlobalValue(int uid, String key, double delta) {
        return TeamGlobalValues.addServer(teamValueScene(), uid, key, delta);
    }

    @Nullable
    private Scene teamValueScene() {
        var entity = getCurrentEntity().orElse(null);
        if (entity != null) return entity.getScene();
        var manager = getSceneScriptManagerOrNull();
        return manager == null ? null : manager.getScene();
    }

    private int sceneBattleGroup(int groupId) {
        var scene = teamValueScene();
        var group = getCurrentGroup().orElse(null);
        if (scene == null
                || groupId < 0
                || group != null && scene.getScriptManager().getLoadedGroupById(group.id) != group)
            return 0;
        if (groupId != 0) return groupId;
        return group != null ? group.id : getCurrentEntity().map(GameEntity::getGroupId).orElse(0);
    }

    public int PrestartScenePlayBattle(LuaTable table) {
        try {
            int groupId = (int) ScenePlayBattleParameters.unsigned(table.rawget("group_id"));
            var scene = teamValueScene();
            return scene != null
                            && scene.getScenePlayBattleManager()
                                    .prestart(
                                            sceneBattleGroup(groupId),
                                            ScenePlayBattleParameters.fromLua(table))
                    ? 0
                    : -1;
        } catch (IllegalArgumentException invalid) {
            return -1;
        }
    }

    public int AddScenePlayBattleProgress(int groupId, long progress) {
        var scene = teamValueScene();
        return scene != null
                        && scene.getScenePlayBattleManager()
                                .addProgress(sceneBattleGroup(groupId), progress)
                ? 0
                : -1;
    }

    public int SetScenePlayBattlePlayTeamEntityGadgetId(int groupId, int gadgetId) {
        var scene = teamValueScene();
        return scene != null
                        && scene.getScenePlayBattleManager()
                                .setPlayTeamEntityGadgetId(sceneBattleGroup(groupId), gadgetId)
                ? 0
                : -1;
    }

    public long GetScenePlayBattleUidValue(int groupId, int uid, String key) {
        var scene = teamValueScene();
        return scene == null
                ? 0
                : scene.getScenePlayBattleManager()
                        .getUidValue(sceneBattleGroup(groupId), uid, key);
    }

    public int SetScenePlayBattleUidValue(int groupId, int uid, String key, long value) {
        var scene = teamValueScene();
        return scene != null
                        && scene.getScenePlayBattleManager()
                                .setUidValue(sceneBattleGroup(groupId), uid, key, value)
                ? 0
                : -1;
    }

    public int GetScenePlayBattleHostUid(int groupId) {
        var scene = teamValueScene();
        return scene == null
                ? 0
                : scene.getScenePlayBattleManager().getHostUid(sceneBattleGroup(groupId));
    }

    public int GetScenePlayBattleType(int groupId) {
        var scene = teamValueScene();
        return scene == null
                ? 0
                : scene.getScenePlayBattleManager().getType(sceneBattleGroup(groupId));
    }

    public int ScenePlayBattleUidOp(
            int groupId,
            int configId,
            int[] uids,
            int op,
            String name,
            int[] parameters,
            int[] targets,
            int index,
            int duration) {
        var scene = teamValueScene();
        return scene != null
                        && scene.getScenePlayBattleManager()
                                .uidOp(
                                        sceneBattleGroup(groupId),
                                        configId,
                                        uids,
                                        op,
                                        name,
                                        parameters,
                                        targets,
                                        index,
                                        duration)
                ? 0
                : -1;
    }

    public int AssignPlayerShowTemplateReminder(int var1, LuaTable var2) {
        logger.warn("[LUA] Call unimplemented AssignPlayerShowTemplateReminder {} {}", var1, var2);

        return 0;
    }

    public int AttachChildChallenge(int var1, int var2, int var3, int[] var4, LuaTable var5, LuaTable var6) {
        logger.warn("[LUA] Call unimplemented AttachChildChallenge with {} {} {} {} {} {}", var1, var2, var3, var4, var5, var6);

        return 0;
    }

    public int AutoMonsterTide(int sourceId, int groupId, Integer[] ordersConfigId, int total, int minimum, int maximum) {
        var manager = getSceneScriptManagerOrNull();
        if (manager == null || sourceId <= 0 || groupId <= 0) return -1;
        synchronized (manager.getScene()) {
            var origin = getCurrentGroup().orElse(null);
            if (!manager.allowsGroupCall(origin)) return -1;
            var group = manager.getGroupById(groupId);
            if (!manager.allowsGroupCall(origin)) return -1;
            return manager.startMonsterTideInGroup(Integer.toString(sourceId), group, ordersConfigId, total, minimum, maximum);
        }
    }

    public int KillMonsterTide(int groupId, int tideIndex) {
        var manager = getSceneScriptManagerOrNull();
        if (manager == null || groupId <= 0 || tideIndex <= 0) return -1;
        synchronized (manager.getScene()) {
            var tide = manager.getMonsterTide(groupId, Integer.toString(tideIndex));
            return tide == null || !manager.allowsGroupCall(tide.getGroup()) ? -1
                    : manager.killMonsterTide(groupId, Integer.toString(tideIndex));
        }
    }

    public int EndMonsterTide(int groupId, int tideIndex, int reason) {
        var manager = getSceneScriptManagerOrNull();
        var tide = manager == null ? null : manager.getMonsterTide(groupId, Integer.toString(tideIndex));
        return reason < 0 || tide == null || !tide.finish(reason) ? -1 : 0;
    }

    public int PauseAutoMonsterTide(int groupId, int tideIndex) {
        var manager = getSceneScriptManagerOrNull();
        var tide = manager == null ? null : manager.getMonsterTide(groupId, Integer.toString(tideIndex));
        return tide == null || !tide.pause() ? -1 : 0;
    }

    public int ContinueAutoMonster(int groupId, int tideIndex) {
        var manager = getSceneScriptManagerOrNull();
        var tide = manager == null ? null : manager.getMonsterTide(groupId, Integer.toString(tideIndex));
        return tide == null || !tide.resume() ? -1 : 0;
    }

    public int BeginCameraSceneLook(LuaTable sceneLookParams) {
        logger.warn("[LUA] Call unimplemented BeginCameraSceneLook with {}", printTable(sceneLookParams));

        return 0;
    }

    public int CancelGroupTimerEvent(int groupID, String source) {
        logger.debug("[LUA] Call CancelGroupTimerEvent");
        var manager = getSceneScriptManagerOrNull();
        if (manager == null) return -1;
        synchronized (manager.getScene()) {
            if (!manager.allowsGroupCall(getCurrentGroup().orElse(null))) return -1;
            return manager.cancelGroupTimerEvent(groupID, source);
        }
    }

    public int CauseDungeonFail() {
        logger.debug("[LUA] Call CauseDungeonFail");
        var scriptManager = sceneScriptManager.getIfExists();
        if (scriptManager == null) {
            return 1;
        }

        var dungeonManager = scriptManager.getScene().getDungeonManager();
        if (dungeonManager == null) {
            return 2;
        }

        dungeonManager.failDungeon();
        return 0;
    }

    public int ChangeGroupGadget(LuaTable table) {
        logger.debug("[LUA] Call ChangeGroupGadget with {}, current group {}", printTable(table), getCurrentGroup().get().id);
        var configId = table.get("config_id").toint();
        var state = table.get("state").toint();

        var entity = getSceneScriptManager().getScene().getEntityByConfigId(configId, getCurrentGroup().get().id);
        if (entity == null) {
            return 1;
        }

        if (entity instanceof EntityGadget entityGadget) {
            entityGadget.updateState(state);
            return 0;
        }

        return 1;
    }

    public LuaValue ChangeGroupVariableValue(String var, int value) {
        logger.debug("[LUA] Call ChangeGroupVariableValue with {},{}", var, value);
        val groupId = currentGroup.get().id;
        val variables = getSceneScriptManager().getVariables(groupId);

        val old = variables.getOrDefault(var, 0);
        variables.put(var, old + value);
        getSceneScriptManager().callEvent(new ScriptArgs(groupId, EventType.EVENT_VARIABLE_CHANGE, old + value, old).setEventSource(var));
        return LuaValue.ZERO;
    }

    public int ChangeGroupVariableValueByGroup(String var, int value, int groupId) {
        logger.debug("[LUA] Call ChangeGroupVariableValueByGroup with {},{}", var, groupId);
        val variables = getSceneScriptManager().getVariables(groupId);

        val old = variables.getOrDefault(var, 0);
        variables.put(var, old + value);
        getSceneScriptManager().callEvent(new ScriptArgs(groupId, EventType.EVENT_VARIABLE_CHANGE, old + value, old).setEventSource(var));
        return 0;
    }

    public int ChangeToTargetLevelTag(int var1) {
        logger.warn("[LUA] Call unimplemented ChangeToTargetLevelTag with {}", var1);
        return 0;
    }

    public boolean CheckIsInMpMode() {
        logger.debug("[LUA] Call CheckIsInMpMode");
        return this.getSceneScriptManager().getScene().getWorld().isMultiplayer();
    }

    /**
     * Whether the entity with this config id is currently alive in this group.
     *
     * <p>Returns a boolean rather than the usual 0-for-success int: the scripts use it directly as a
     * condition ({@code return not ScriptLib.CheckIsInGroup(...)}), and in Lua every number is
     * truthy - a 0 would read as "yes it is there" and invert the check.
     */
    public boolean CheckIsInGroup(int groupId, int configId) {
        logger.debug("[LUA] Call CheckIsInGroup with {},{}", groupId, configId);
        return getSceneScriptManager().getScene().getEntityByConfigId(configId, groupId) != null;
    }

    public int CheckRemainGadgetCountByGroupId(LuaTable table) {
        logger.debug("[LUA] Call CheckRemainGadgetCountByGroupId with {}", printTable(table));
        var groupId = table.get("group_id").toint();

        var count = getSceneScriptManager().getScene().getEntities().values().stream()
                .filter(g -> g instanceof EntityGadget entityGadget && entityGadget.getGroupId() == groupId)
                .count();
        return (int) count;
    }

    public boolean CheckSceneTag(int sceneId, int sceneTagId) {
        logger.debug("[LUA] Call CheckSceneTag with {}, {}", sceneId, sceneTagId);
        return getSceneScriptManager().getScene().getHost().getProgressManager().checkSceneTag(sceneId, sceneTagId);
    }

    public int ClearPlayerEyePoint(int var1) {
        logger.warn("[LUA] Call unimplemented ClearPlayerEyePoint with {}", var1);

        return 0;
    }

    public int CreateBlossomChestByGroupId(int groupId, int var2) {
        logger.warn("[LUA] Call unimplemented CreateBlossomChestByGroupId with {} {}", groupId, var2);

        return 0;
    }

    public int CreateChannellerSlabCampRewardGadget(int configId) {
        logger.warn("[LUA] Call unimplemented CreateChannellerSlabCampRewardGadget {}", configId);

        var group = currentGroup.getIfExists();
        if (group == null) {
            return 1;
        }
        createGadget(configId, group);

        return 0;
    }

    public int CreateEffigyChallengeMonster(int var1, int[] var2) {
        logger.warn("[LUA] Call unimplemented CreateEffigyChallengeMonster with {} {}", var1, var2);

        return 0;
    }

    public int CreateFatherChallenge(int var1, int var2, int var3, LuaTable var4) {
        logger.warn("[LUA] Call unimplemented CreateFatherChallenge with {} {} {} {}", var1, var2, var3, var4);

        return 0;
    }

    public int CreateGadget(LuaTable table) {
        logger.debug("[LUA] Call CreateGadget with {}", printTable(table));
        var configId = table.get("config_id").toint();

        if (configId == 0) {
            Grasscutter.getLogger().warn("Tried to CreateGadget with config_id 0: {}", printTable(table));
            return 0;
        }

        var group = getCurrentGroup();

        if (group.isEmpty()) {
            return 1;
        }
        createGadget(configId, group.get());

        return 0;
    }

    public int CreateGroupTimerEvent(int groupID, String source, double time) {
        logger.debug("[LUA] Call CreateGroupTimerEvent with {} {} {}", groupID, source, time);
        var manager = getSceneScriptManagerOrNull();
        if (manager == null) return -1;
        synchronized (manager.getScene()) {
            if (!manager.allowsGroupCall(getCurrentGroup().orElse(null))) return -1;
            return manager.createGroupTimerEvent(groupID, source, time);
        }
    }

    public int ShowTemplateReminder(int id, int[] parameters) {
        var manager = getSceneScriptManagerOrNull();
        if (id <= 0 || parameters == null || manager == null) return -1;
        synchronized (manager.getScene()) {
            if (!manager.allowsGroupCall(getCurrentGroup().orElse(null))) return -1;
            manager.getScene().broadcastPacket(new emu.grasscutter.server.packet.send
                    .PacketShowTemplateReminderNotify(id, parameters));
            return 0;
        }
    }

    public int CreateScenePlayGeneralRewardGadget(int groupId, int configId) {
        var manager = getSceneScriptManagerOrNull();
        if (manager == null) return -1;
        synchronized (manager.getScene()) {
            var origin = getCurrentGroup().orElse(null);
            if (origin == null || !manager.allowsGroupCall(origin)) return -1;
            return manager.getScene().getScenePlayBattleManager()
                    .createGeneralRewardGadget(origin.id, groupId, configId);
        }
    }

    public int CreateMonster(LuaTable table) {
        logger.debug("[LUA] Call CreateMonster with {}", printTable(table));
        var configId = table.get("config_id").toint();
        var delayTime = table.get("delay_time").toint();

        if (getCurrentGroup().isEmpty()) {
            return 1;
        }

        getSceneScriptManager().spawnMonstersByConfigId(getCurrentGroup().get(), configId, delayTime);
        return 0;
    }

    /** Spawns a monster from the group's config at a position the script chooses. */
    public int CreateMonsterByConfigIdByPos(int configId, LuaTable bornPos, LuaTable face) {
        logger.debug("[LUA] Call CreateMonsterByConfigIdByPos with {}, {}, {}", configId, bornPos, face);

        var group = this.currentGroup.getIfExists();
        if (group == null) return 1;

        var entity =
                getSceneScriptManager()
                        .createMonsterByConfigIdByPos(
                                group, configId, luaTablePosition(bornPos), luaTablePosition(face));
        if (entity == null) return 2;

        getSceneScriptManager().addEntity(entity);
        return 0;
    }

    private static Position luaTablePosition(LuaTable table) {
        if (table == null || table.isnil()) return null;
        return new Position(
                table.get("x").tofloat(), table.get("y").tofloat(), table.get("z").tofloat());
    }

    public int CreateMonsterFaceAvatar(LuaTable var1) {
        logger.warn("[LUA] Call unimplemented CreateMonsterFaceAvatar with {}", printTable(var1));

        return 0;
    }

    public int DelPlayerGroupVisionType(int[] uids, int[] var2) {
        logger.warn("[LUA] Call unimplemented DelPlayerGroupVisionType with {} {}", uids, var2);

        return 0;
    }

    public int DelSceneTag(int sceneId, int sceneTagId) {
        logger.debug("[LUA] Call DelSceneTag with {}, {}", sceneId, sceneTagId);
        getSceneScriptManager().getScene().getHost().getProgressManager().delSceneTag(sceneId, sceneTagId);
        return 0;
    }

    public int DelWorktopOption(int var1) {
        logger.debug("[LUA] Call DelWorktopOption with {}", var1);
        var callParams = this.callParams.getIfExists();
        var group = this.currentGroup.getIfExists();
        if (callParams == null || group == null) {
            return 1;
        }
        var configId = callParams.param1;
        var entity = getSceneScriptManager().getScene().getEntityByConfigId(configId, getCurrentGroup().get().id);
        if (!(entity instanceof EntityGadget gadget)) {
            return 1;
        }

        if (!(gadget.getContent() instanceof GadgetWorktop worktop)) {
            return 2;
        }

        worktop.removeWorktopOption(callParams.param2);

        var scene = getSceneScriptManager().getScene();
        Grasscutter.getGameServer().getScheduler().scheduleDelayedTask(() -> {
            scene.broadcastPacket(new PacketWorktopOptionNotify(gadget));
        }, 1);

        return 0;
    }

    public int DelWorktopOptionByGroupId(int groupId, int configId, int option) {
        logger.debug("[LUA] Call DelWorktopOptionByGroupId with {},{},{}", groupId, configId, option);
        val entity = getSceneScriptManager().getScene().getEntityByConfigId(configId, groupId);

        if (!(entity instanceof EntityGadget gadget)) {
            return 1;
        }

        if (!(gadget.getContent() instanceof GadgetWorktop worktop)) {
            return 1;
        }

        worktop.removeWorktopOption(option);
        getSceneScriptManager().getScene().broadcastPacket(new PacketWorktopOptionNotify(gadget));

        return 0;
    }

    public int EndTimeAxis(String identifier) {
        logger.debug("[LUA] Call EndTimeAxis");
        var group = this.getCurrentGroup().orElse(null);
        var manager = getSceneScriptManagerOrNull();
        if (group == null || manager == null || identifier == null || identifier.isBlank()) return -1;
        manager.stopTimeAxis(group.id, identifier);
        return 0;
    }

    public int EnterWeatherArea(int var1) {
        logger.warn("[LUA] Call unimplemented EnterWeatherArea with {}", var1);

        return 0;
    }

    public int ExpeditionChallengeEnterRegion(boolean var1) {
        logger.warn("[LUA] unimplemented Call ExpeditionChallengeEnterRegion with {}", var1);

        return 0;
    }

    public int FinishExpeditionChallenge() {
        logger.warn("[LUA] unimplemented Call FinishExpeditionChallenge");

        return 0;
    }

    public int ForceRefreshAuthorityByConfigId(int var1, int uid) {
        logger.warn("[LUA] Call unimplemented ForceRefreshAuthorityByConfigId with {} {}", var1, uid);

        return 0;
    }

    public int GadgetPlayUidOp(int groupId, int configId, LuaTable uids, int op, String name, LuaTable args) {
        var gadget = playGadget(groupId, configId);
        if (gadget == null) return -1;
        var selected = new ArrayList<Integer>();
        var params = new ArrayList<Integer>();
        for (int index = 1; index <= uids.length(); index++) selected.add(uids.get(index).checkint());
        for (int index = 1; index <= args.length(); index++) params.add(args.get(index).checkint());
        return gadget.gadgetPlayUidOp(selected, op, name, params) ? 0 : -1;
    }

    public int StartGadgetPlay(int groupId, int configId) {
        var gadget = playGadget(groupId, configId);
        return gadget != null && gadget.startGadgetPlay() ? 0 : -1;
    }

    public int StopGadgetPlay(int groupId, int configId) {
        var gadget = playGadget(groupId, configId);
        if (gadget == null) return -1;
        gadget.stopGadgetPlay();
        return 0;
    }

    public int GadgetLuaNotifyGroup(int param1, int param2, int param3) {
        var gadget = getCurrentEntityGadget();
        if (gadget == null) return -1;
        gadget.getScene().getScriptManager().callEvent(new ScriptArgs(gadget.getGroupId(),
                EventType.EVENT_GADGET_LUA_NOTIFY, param1, param2).setParam3(param3));
        return 0;
    }

    public int ExecuteGadgetLua(int groupId, int configId, int param1, int param2, int param3) {
        var gadget = playGadget(groupId, configId);
        if (gadget == null || gadget.getEntityController() == null) return -1;
        return gadget.onClientExecuteRequest(param1, param2, param3);
    }

    private EntityGadget playGadget(int groupId, int configId) {
        var current = getCurrentEntityGadget();
        if (current != null && groupId == 0 && configId == 0) return current;
        var scene = current != null ? current.getScene() : getSceneScriptManager().getScene();
        int resolvedGroup = groupId != 0 ? groupId
                : current != null ? current.getGroupId() : getCurrentGroup().map(group -> group.id).orElse(0);
        var entity = scene.getEntityByConfigId(configId, resolvedGroup);
        return entity instanceof EntityGadget gadget ? gadget : null;
    }

    public int GetGadgetPlayUidValue(int groupId, int configId, int uid, String key) {
        var gadget = playGadget(groupId, configId);
        return gadget == null ? 0 : gadget.getGadgetPlayState().getUidValue(uid, key);
    }

    public int SetGadgetPlayUidValue(int groupId, int configId, int uid, String key, int value) {
        var gadget = playGadget(groupId, configId);
        if (gadget == null) return -1;
        return gadget.getGadgetPlayState().setRoundUidValue(uid, key, value, System.currentTimeMillis() / 1000) ? 0 : -1;
    }

    public int GetGadgetPlayProgress(int groupId, int configId) {
        var gadget = playGadget(groupId, configId);
        return gadget == null ? 0 : gadget.getGadgetPlayState().getProgress();
    }

    public int AddGadgetPlayProgress(int groupId, int configId, int delta) {
        var gadget = playGadget(groupId, configId);
        if (gadget == null) return -1;
        gadget.addGadgetPlayProgress(delta);
        return 0;
    }

    public int GetGadgetPlayStageBeginProgress(int groupId, int configId) {
        var gadget = playGadget(groupId, configId);
        return gadget == null ? 0 : gadget.getGadgetPlayState().getStageBeginProgress();
    }

    public int GetUidByTeamEntityId(int entityId) {
        return TeamGlobalValues.uidByEntityId(teamValueScene(), entityId);
    }

    public float GetTeamAbilityFloatValue(int uid, String key) {
        return TeamGlobalValues.get(teamValueScene(), uid, key);
    }

    public float GetTeamServerGlobalValue(int uid, String key) {
        return TeamGlobalValues.getServer(teamValueScene(), uid, key);
    }

    public LuaTable GetActivityOpenAndCloseTimeByScheduleId(int scheduleId) {
        logger.debug("[LUA] Call GetActivityOpenAndCloseTimeByScheduleId with {}", scheduleId);
        var result = new LuaTable();
        var activityConfig = ActivityManager.getScheduleActivityConfigMap().get(scheduleId);

        if (activityConfig != null) {
            result.set(1, LuaValue.valueOf(activityConfig.getBeginTime().getTime()));
            result.set(2, LuaValue.valueOf(activityConfig.getEndTime().getTime()));
        }

        return result;
    }

    public int GetAvatarEntityIdByUid(int uid) {
        logger.warn("[LUA] Call unchecked GetAvatarEntityIdByUid with {}", uid);

        var entity = getSceneScriptManager().getScene().getEntities().values().stream()
                .filter(e -> e instanceof EntityAvatar && ((EntityAvatar) e).getPlayer().getUid() == uid)
                .findFirst();
        return entity.map(GameEntity::getId).orElse(0);
    }

    public int GetChannellerSlabLoopDungeonLimitTime() {
        logger.warn("[LUA] Call unimplemented GetChannellerSlabLoopDungeonLimitTime");

        return 0;
    }

    public int GetContextGroupId() {
        logger.debug("[LUA] Call GetContextGroupId");
        var group = currentGroup.getIfExists();
        if (group != null) return group.id;
        var entity = currentEntity.getIfExists();
        return entity != null && entity.getGroupId() > 0 ? entity.getGroupId() : -1;
    }

    public int GetCurTriggerCount() {
        logger.debug("[LUA] Call GetCurTriggerCount");

        return getSceneScriptManager().getTriggerCount();
    }

    public int GetEffigyChallengeMonsterLevel() {
        logger.warn("[LUA] Call unimplemented GetEffigyChallengeMonsterLevel");

        return 0;
    }

    public int GetEntityIdByConfigId(int configId) {
        logger.warn("[LUA] Call GetEntityIdByConfigId with {}", configId);

        var entity = getSceneScriptManager().getScene().getEntityByConfigId(configId, getCurrentGroup().get().id);
        return entity != null ? entity.getId() : 0;
    }

    public int GetGadgetIdByEntityId(int entityId) {
        logger.debug("[LUA] Call GetGadgetIdByEntityId with {}", entityId);
        var entity = getSceneScriptManager().getScene().getEntityById(entityId);
        if (!(entity instanceof EntityBaseGadget)) {
            return 0;
        }
        return ((EntityBaseGadget) entity).getGadgetId();
    }

    public int GetGadgetStateByConfigId(int groupId, int configId) {
        logger.debug("[LUA] Call GetGadgetStateByConfigId with {},{}", groupId, configId);
        val scene = getSceneScriptManager().getScene();
        val gadget = groupId == 0 ? scene.getEntityByConfigId(configId, getCurrentGroup().get().id) : scene.getEntityByConfigId(configId, groupId);
        if (!(gadget instanceof EntityGadget)) {
            return -1;
        }
        return ((EntityGadget) gadget).getState();
    }

    public int GetGameHour() {
        logger.debug("[LUA] Call GetGameHour");
        return getSceneScriptManager().getScene().getWorld().getGameTimeHours();
    }

    public int GetGroupMonsterCount() {
        int returnValue = (int) getSceneScriptManager().getScene().getEntities().values().stream()
                .filter(e -> e instanceof EntityMonster && e.getGroupId() == currentGroup.get().id)
                .count();
        logger.debug("[LUA] Call GetGroupMonsterCount = {}", returnValue);
        return returnValue;
    }

    public int GetGroupMonsterCountByGroupId(int groupId) {
        int returnValue = (int) getSceneScriptManager().getScene().getEntities().values().stream()
                .filter(e -> e instanceof EntityMonster && e.getGroupId() == groupId)
                .count();
        logger.debug("[LUA] Call GetGroupMonsterCountByGroupId with {} = {}", groupId, returnValue);
        return returnValue;
    }

    public int GetGroupSuite(int groupId) {
        logger.debug("[LUA] Call GetGroupSuite with {}", groupId);
        var instance = getSceneScriptManager().getGroupInstanceById(groupId);
        if (instance != null) return instance.getActiveSuiteId();
        return 0;
    }

    public int GetGroupTempValue(String name, LuaTable var2) {
        logger.warn("[LUA] Call unimplemented GetGroupTempValue with {} {}", name, printTable(var2));

        return 0;
    }

    public int GetGroupVariableValue(String var) {
        int returnValue = getSceneScriptManager().getVariables(currentGroup.get().id).getOrDefault(var, 0);
        logger.debug("[LUA] Call GetGroupVariableValue with {} = {}", var, returnValue);
        return returnValue;
    }

    public int GetGroupVariableValueByGroup(String var, int groupId) {
        int returnValue = getSceneScriptManager().getVariables(groupId).getOrDefault(var, 0);
        logger.debug("[LUA] Call GetGroupVariableValueByGroup with {},{} = {}", var, groupId, returnValue);
        return returnValue;
    }

    public int GetHostQuestState(int questId) {
        logger.debug("[LUA] Call GetHostQuestState with {}", questId);
        val player = getSceneScriptManager().getScene().getWorld().getHost();
        val quest = player.getQuestManager().getQuestById(questId);
        if (quest == null) {
            return QuestState.QUEST_STATE_NONE.getValue();
        }

        return quest.getState().getValue();
    }

    public int GetHuntingMonsterExtraSuiteIndexVec() {
        logger.warn("[LUA] Call unimplemented GetHuntingMonsterExtraSuiteIndexVec");

        return 0;
    }

    public int GetLanternRiteValue() {
        logger.warn("[LUA] Call unimplemented GetLanternRiteValue");

        return 0;
    }

    public int GetMonsterIdByEntityId(int entityId) {
        logger.debug("[LUA] Call GetMonsterIdByEntityId with {}", entityId);
        var entity = getSceneScriptManager().getScene().getEntityById(entityId);
        if (!(entity instanceof EntityMonster)) {
            return 0;
        }
        return ((EntityMonster) entity).getMonsterData().getId();
    }

    public int[] GetOpeningDungeonListByRosterId(int var1) {
        logger.warn("[LUA] Call unimplemented GetOpeningDungeonListByRosterId with {}", var1);
        return new int[] {0, 0};
    }

    public LuaTable GetPosByEntityId(int entityId) {
        logger.warn("[LUA] Call unchecked GetPosByEntityId with {}", entityId);

        var entity = getSceneScriptManager().getScene().getEntityById(entityId);
        return posToLua(entity != null ? entity.getPosition() : null);
    }

    public int GetQuestState(int entityId, int questId) {
        logger.debug("[LUA] Call GetQuestState with {} {}", entityId, questId);

        val player = getSceneScriptManager().getScene().getWorld().getHost();
        val quest = player.getQuestManager().getQuestById(questId);
        if (quest == null) {
            return QuestState.QUEST_STATE_NONE.getValue();
        }

        return quest.getState().getValue();
    }

    public int GetRegionConfigId(LuaTable var1) {
        logger.debug("[LUA] Call GetRegionConfigId with {}", printTable(var1));
        var EntityId = var1.get("region_eid").toint();
        var entity = getSceneScriptManager().getScene().getScriptManager().getRegionById(EntityId);
        if (entity == null) {
            return -1;
        }
        return entity.getConfigId();
    }

    public int GetRegionEntityCount(LuaTable table) {
        logger.debug("[LUA] Call GetRegionEntityCount with {}", printTable(table));
        var regionId = table.get("region_eid").toint();
        var entityType = table.get("entity_type").toint();
        var region = this.getSceneScriptManager().getRegionById(regionId);
        if (region == null) {
            return 0;
        }
        return (int) region.getEntities().stream().filter(e -> e >> ENTITY_ID_BIT_SHIFT == entityType).count();
    }

    public LuaTable GetRotationByEntityId(int entityId) {
        logger.debug("[LUA] Call unchecked GetRotationByEntityId with {}", entityId);

        var entity = getSceneScriptManager().getScene().getEntityById(entityId);
        return posToLua(entity != null ? entity.getRotation() : null);
    }

    public LuaTable GetSceneUidList() {
        logger.debug("[LUA] Call GetSceneUidList");

        var scriptManager = sceneScriptManager.getIfExists();
        if (scriptManager == null) {
            return new LuaTable();
        }
        var players = scriptManager.getScene().getPlayers();
        var result = new LuaTable();
        for (int i = 0; i < players.size(); i++) {
            result.set(i + 1, LuaValue.valueOf(players.get(i).getUid()));
        }
        return result;
    }

    public long GetServerTime() {
        return System.currentTimeMillis() / 1000;
    }

    public int GoToGroupSuite(int groupId, int suite) {
        logger.debug("[LUA] Call GoToGroupSuite with {},{}", groupId, suite);
        SceneGroup group = getSceneScriptManager().getGroupById(groupId);
        SceneGroupInstance groupInstance = getSceneScriptManager().getGroupInstanceById(groupId);
        if (group == null || groupInstance == null || group.monsters == null) {
            return 1;
        }
        var suiteData = group.getSuiteByIndex(suite);
        if (suiteData == null) {
            return 1;
        }

        if (groupInstance.getActiveSuiteId() == 0 || groupInstance.getActiveSuiteId() != suite) {
            groupInstance.getDeadEntities().clear();
            this.getSceneScriptManager().addGroupSuite(groupInstance, suiteData);
            groupInstance.setActiveSuiteId(suite);
        }

        return 0;
    }

    public int InitTimeAxis(String identifier, int[] delays, boolean shouldLoop) {
        return InitTimeAxis(identifier, delays == null ? null : Arrays.stream(delays).asDoubleStream().toArray(), shouldLoop);
    }

    public int InitTimeAxis(String identifier, LuaTable delays, boolean shouldLoop) {
        if (delays == null || delays.length() == 0 || delays.keys().length != delays.length()) return -1;
        var points = new double[delays.length()];
        for (int i = 0; i < points.length; i++) {
            var value = delays.rawget(i + 1);
            if (value.type() != LuaValue.TNUMBER) return -1;
            points[i] = value.todouble();
        }
        return InitTimeAxis(identifier, points, shouldLoop);
    }

    public int InitTimeAxis(String identifier, double[] delays, boolean shouldLoop) {
        if (this.getCurrentGroup().isEmpty()) {
            logger.warn("[LUA] Call InitTimeAxis without a group");
            return -1;
        }

        var scriptManager = this.getSceneScriptManagerOrNull();
        if (scriptManager == null) return -1;
        var group = this.getCurrentGroup().get();

        try {
            var timeAxis = new SceneTimeAxis(scriptManager, group.id, identifier, delays, shouldLoop);
            if (!scriptManager.tryInitTimeAxis(timeAxis)) return -1;
            timeAxis.start();
        } catch (IllegalArgumentException exception) {
            logger.debug("Invalid time axis {} in group {}", identifier, group.id, exception);
            return -1;
        }

        return 0;
    }

    public boolean IsPlayerAllAvatarDie(int sceneUid) {
        logger.warn("[LUA] Call unchecked IsPlayerAllAvatarDie with {}", sceneUid);
        var playerEntities = getSceneScriptManager().getScene().getEntities().values().stream().filter(e -> e.getEntityType() == EntityType.Avatar).toList();
        for (GameEntity p : playerEntities) {
            var player = (EntityAvatar) p;
            if (player.isAlive()) {
                return false;
            }
        }
        return true;
    }

    public int KillEntityByConfigId(LuaTable table) {
        logger.debug("[LUA] Call KillEntityByConfigId with {}", printTable(table));
        var configId = table.get("config_id");
        if (configId.type() != LuaValue.TNUMBER || !configId.isint() || configId.toint() <= 0)
            return 1;
        var requestedGroup = table.get("group_id");
        if (!requestedGroup.isnil()
                && (requestedGroup.type() != LuaValue.TNUMBER
                        || !requestedGroup.isint()
                        || requestedGroup.toint() < 0)) return 1;
        int groupId =
                requestedGroup.isnil() || requestedGroup.toint() == 0
                        ? getCurrentGroup().map(group -> group.id).orElse(0)
                        : requestedGroup.toint();
        var manager = getSceneScriptManagerOrNull();
        if (groupId <= 0 || manager == null || manager.isDestroyed()) return 1;

        var group = manager.getLoadedGroupById(groupId);
        if (group == null) group = manager.getGroupById(groupId);
        if (group == null || !manager.allowsGroupCall(group)) return 1;
        var instance = manager.getGroupInstanceById(groupId);
        if (group.gadgets != null
                && group.gadgets.containsKey(configId.toint())
                && instance != null
                && instance.markScriptGadgetDestroyed(configId.toint())) {
            instance.save();
        }
        var entity = manager.getScene().getEntityByConfigId(configId.toint(), groupId);
        if (entity != null) manager.getScene().killEntity(entity, 0);
        return 0;
    }

    public int KillExtraGroupSuite(int groupId, int suite) {
        logger.debug("[LUA] Call KillExtraGroupSuite with {},{}", groupId, suite);
        SceneGroup group = getSceneScriptManager().getGroupById(groupId);
        if (group == null || group.monsters == null) {
            return 1;
        }
        var suiteData = group.getSuiteByIndex(suite);
        if (suiteData == null) {
            return 1;
        }
        this.getSceneScriptManager().killGroupSuite(group, suiteData);
        return 0;
    }

    public int KillGroupEntity(LuaTable var1) {
        logger.debug("[LUA] Call KillGroupEntity with {}", printTable(var1));

        var sceneManager = sceneScriptManager.getIfExists();
        var groupId = var1.get("group_id").optint(-1);
        var killPolicyId = var1.get("kill_policy").optint(-1);
        var gadgetList = var1.get("gadgets");
        if (groupId == -1 || sceneManager == null) {
            return 1;
        }
        var group = sceneManager.getGroupById(groupId);
        if (group == null) {
            return 2;
        }
        if (killPolicyId != -1) {
            var killPolicy = GroupKillPolicy.values()[killPolicyId];
            if (killPolicy == GROUP_KILL_MONSTER || killPolicy == GROUP_KILL_ALL)
                sceneManager.cancelGroupMonsterSpawns(group.id);
            return killGroupEntityWithPolicy(sceneManager, group, killPolicy);
        }
        if (var1.get("monsters").length() > 0) sceneManager.cancelGroupMonsterSpawns(group.id);
        return killGroupEntityWithTable(sceneManager, group, var1);
    }

    public int MarkPlayerAction(int var1, int var2, int var3) {
        logger.warn("[LUA] Call unimplemented MarkPlayerAction with {},{},{}", var1, var2, var3);

        return 0;
    }

    public int ModifyClimatePolygonParamTable(int var1, LuaTable var2) {
        logger.warn("[LUA] Call unimplemented ModifyClimatePolygonParamTable with {} {}", var1, printTable(var2));

        return 0;
    }

    public int ModifyFatherChallengeProperty(int challengeId, int propertyTypeIndex, int value) {
        val propertyType = FatherChallengeProperty.values()[propertyTypeIndex];
        logger.warn("[LUA] Call unimplemented ModifyFatherChallengeProperty with {} {} {}", challengeId, propertyType.name(), value);

        return 0;
    }

    public int MoveAvatarByPointArray(int uid, int targetId, LuaTable var3, String var4) {
        logger.warn("[LUA] Call unimplemented MoveAvatarByPointArray with {} {} {} {}", uid, targetId, printTable(var3), var4);

        return 0;
    }

    public int MoveAvatarByPointArrayWithTemplate(int uid, int pointarray_id, int[] routelist, int var4, LuaTable var5) {
        logger.warn("[LUA] Call unimplemented MoveAvatarByPointArrayWithTemplate with {} {} {} {} {}", uid, pointarray_id, routelist, var4, printTable(var5));

        return 0;
    }

    public int MovePlayerToPos(LuaTable var1) {
        logger.warn("[LUA] Call unchecked MovePlayerToPos with {}", printTable(var1));

        return TransPlayerToPos(var1);
    }

    public int PlayCutScene(int cutsceneId, int var2) {
        logger.warn("[LUA] Call unchecked PlayCutScene with {} {}", cutsceneId, var2);
        if (emu.grasscutter.config.Configuration.GAME_OPTIONS.disableCutscenes) {
            logger.debug("Cutscene {} suppressed by game.disableCutscenes.", cutsceneId);
            return 0;
        }
        sceneScriptManager.get().getScene().broadcastPacket(new PacketCutsceneBeginNotify(cutsceneId));

        return 0;
    }

    public int PlayCutSceneWithParam(int cutsceneId, int var2, LuaTable var3) {
        logger.warn("[LUA] Call unimplemented PlayCutScene with {} {} {}", cutsceneId, var2, var3);

        return 0;
    }

    public void PrintContextLog(String msg) {
        printLog("PrintContextLog", msg);
    }

    public void PrintLog(String msg) {
        printLog("PrintLog", msg);
    }

    public int RefreshBlossomDropRewardByGroupId(int groupId) {
        logger.warn("[LUA] Call unimplemented RefreshBlossomDropRewardByGroupId with {}", groupId);

        return 0;
    }

    public int RefreshBlossomGroup(LuaTable var1) {
        logger.warn("[LUA] Call unimplemented RefreshBlossomGroup with {}", printTable(var1));

        return 0;
    }

    public int RefreshGroup(LuaTable table) {
        logger.debug("[LUA] Call RefreshGroup with {}", printTable(table));

        int groupId = table.get("group_id").toint();
        int suite = table.get("suite").toint();
        SceneGroupInstance groupInstance = getSceneScriptManager().getGroupInstanceById(groupId);
        if (groupInstance == null) {
            logger.warn("[LUA] trying to refresh unloaded group {}", groupId);
            return 1;
        }
        getSceneScriptManager().refreshGroup(groupInstance, suite, table.get("exclude_prev").optboolean(false));
        return 0;
    }

    public int RefreshHuntingClueGroup() {
        logger.warn("[LUA] Call unimplemented RefreshHuntingClueGroup");

        return 0;
    }

    public int RemoveEntityByConfigId(int groupId, int entityType, int configId) {
        logger.debug("[LUA] Call RemoveEntityByConfigId");
        val entity = getSceneScriptManager().getScene().getEntityByConfigId(configId, groupId);
        if (entity == null || entity.getEntityType().getValue() != entityType) {
            return 1;
        }
        getSceneScriptManager().getScene().removeEntity(entity, VisionType.VisionType_VISION_REMOVE);
        return 0;
    }

    public int DelAllSubEntityByOriginOwnerConfigId(int configId) {
        var manager = getSceneScriptManagerOrNull();
        var group = getCurrentGroup().orElse(null);
        if (manager == null || group == null || configId <= 0) return -1;
        var scene = manager.getScene();
        synchronized (scene) {
            if (!manager.allowsGroupCall(group)) return -1;
            var children = scene.getEntities().values().stream()
                    .filter(e -> e.getOriginOwnerGroupId() == group.id
                            && e.getOriginOwnerConfigId() == configId).toList();
            // Despawn, not a kill: this must not advance the monster tide or award drops.
            scene.removeEntities(children, VisionType.VisionType_VISION_REMOVE);
            return 0;
        }
    }

    public int RemoveExtraGroupSuite(int groupId, int suite) {
        logger.debug("[LUA] Call RemoveExtraGroupSuite with {},{}", groupId, suite);
        SceneGroup group = getSceneScriptManager().getGroupById(groupId);
        if (group == null || group.monsters == null) {
            return 1;
        }
        var suiteData = group.getSuiteByIndex(suite);
        if (suiteData == null) {
            return 1;
        }
        this.getSceneScriptManager().removeGroupSuite(group, suiteData);
        return 0;
    }

    public int RevokePlayerShowTemplateReminder(int var1, LuaValue var2) {
        logger.warn("[LUA] Call unimplemented RevokePlayerShowTemplateReminder {} {}", var1, var2);

        return 0;
    }

    public int ScenePlaySound(LuaTable soundInfo) {
        logger.debug("[LUA] Call ScenePlaySound with {}", printTable(soundInfo));
        val luaSoundName = soundInfo.get("sound_name");
        val luaIsBroadcast = soundInfo.get("is_broadcast");
        val luaPlayPosition = soundInfo.get("play_pos");
        val luaPlayType = soundInfo.get("play_type");
        val soundName = luaSoundName.optjstring(null);
        val isBroadcast = luaIsBroadcast.optboolean(true);
        val playPosition = luaToPos(luaPlayPosition);
        val playType = luaPlayType.optint(0);
        sceneScriptManager.get().getScene().broadcastPacket(new PacketScenePlayerSoundNotify(playPosition, soundName, playType));
        return 0;
    }

    public int sendCloseCommonTipsToClient() {
        logger.warn("[LUA] Call unchecked sendCloseCommonTipsToClient");
        sceneScriptManager.get().getScene().broadcastPacket(new PacketCloseCommonTipsNotify());
        return 0;
    }

    public int SendServerMessageByLuaKey(String var1, int[] var2) {
        logger.warn("[LUA] Call unimplemented SendServerMessageByLuaKey with {} {}", var1, var2);

        return 0;
    }

    public int sendShowCommonTipsToClient(String title, String content, int closeTime) {
        logger.debug("[LUA] Call sendShowCommonTipsToClient with {}, {}, {}", title, content, closeTime);
        sceneScriptManager.get().getScene().broadcastPacket(new PacketShowCommonTipsNotify(title, content, closeTime));
        return 0;
    }

    public int SetBlossomScheduleStateByGroupId(int groupId, int scene) {
        logger.warn("[LUA] Call unimplemented SetBlossomScheduleStateByGroupId with {} {}", groupId, scene);

        return 0;
    }

    public int SetEntityServerGlobalValueByConfigId(int cfgId, String sgvName, double value) {
        logger.debug("[LUA] Call SetEntityServerGlobalValueByConfigId with {}, {}, {}", cfgId, sgvName, value);
        var scriptManager = this.getSceneScriptManagerOrNull();
        var group = getCurrentGroup().orElse(null);
        if (scriptManager == null || group == null) return 1;
        var scene = scriptManager.getScene();
        var entity = scene.getEntityByConfigId(cfgId, group.id);
        if (entity == null) return 2;
        return setEntityServerGlobalValue(scene, entity, sgvName, value);
    }

    public int SetEntityServerGlobalValueByEntityId(int entityId, String sgvName, double value) {
        logger.debug("[LUA] Call SetEntityServerGlobalValueByEntityId with {}, {}, {}", entityId, sgvName, value);
        var scriptManager = this.getSceneScriptManagerOrNull();
        if (scriptManager == null) return 1;
        var scene = scriptManager.getScene();
        var entity = scene.getEntityById(entityId);
        if (entity == null) return 2;
        return setEntityServerGlobalValue(scene, entity, sgvName, value);
    }

    private int setEntityServerGlobalValue(Scene scene, GameEntity entity, String key, double value) {
        float storedValue = (float) value;
        if (key == null || key.isEmpty() || !Float.isFinite(storedValue)) return 1;
        long version = entity.getAbilityLifecycleVersion();
        if (!entity.isAbilityLifecycleCurrent(version)) return 2;
        entity.getGlobalAbilityValues().put(key, storedValue);
        entity.onAbilityValueUpdate();
        scene.runWhenHostInitialized(() -> {
            if (scene.getEntityById(entity.getId()) == entity && entity.isAbilityLifecycleCurrent(version)) {
                scene.broadcastPacket(new PacketServerGlobalValueChangeNotify(entity, key, storedValue));
            }
        });
        return 0;
    }

    public int SetEnvironmentEffectState(int var1, String var2, int[] var3, int[] var4) {
        logger.warn("[LUA] Call unimplemented SetEnvironmentEffectState with {} {} {} {}", var1, var2, var3, var4);
        return 0;
    }

    public int SetGadgetEnableInteract(int groupId, int configId, boolean enable) {
        logger.debug("[LUA] Call SetGadgetEnableInteract with {} {} {}", groupId, configId, enable);
        var entity = getSceneScriptManager().getScene().getEntityByConfigId(configId, groupId);
        if (!(entity instanceof EntityGadget gadget)) {
            return -1;
        }
        gadget.setInteractEnabled(enable);
        return 0;
    }

    public int SetGadgetStateByConfigId(int configId, int gadgetState) {
        logger.debug("[LUA] Call SetGadgetStateByConfigId with {},{}, current group {}", configId, gadgetState, getCurrentGroup().get().id);
        GameEntity entity = getSceneScriptManager().getScene().getEntityByConfigId(configId, getCurrentGroup().get().id);
        if (!(entity instanceof EntityGadget gadget)) {
            return 1;
        }
        gadget.updateState(gadgetState);
        return 0;
    }

    public int SetGroupGadgetStateByConfigId(int groupId, int configId, int gadgetState) {
        logger.debug("[LUA] Call SetGroupGadgetStateByConfigId with {},{},{}", groupId, configId, gadgetState);
        val entity = getSceneScriptManager().getScene().getEntityByConfigId(configId, groupId);
        if (!(entity instanceof EntityGadget gadget)) {
            // Same story as SetWorktopOptionsByGroupId: the caller aborts on this, so say which
            // gadget was missing rather than failing the whole Lua action quietly.
            logger.warn(
                    "SetGroupGadgetStateByConfigId: no gadget {} in group {} of scene {}",
                    configId, groupId, getSceneScriptManager().getScene().getId());
            return -1;
        }
        gadget.updateState(gadgetState);
        return 0;
    }

    public int SetGroupReplaceable(int groupId, boolean value) {
        logger.warn("[LUA] Call unchecked SetGroupReplaceable with {} {}", groupId, value);
        var group = getSceneScriptManager().getGroupById(groupId);
        if (group != null && group.is_replaceable != null) {
            group.is_replaceable.value = value;
            return 0;
        }
        return 1;
    }

    public int SetGroupTempValue(String name, int value, LuaTable var3) {
        logger.warn("[LUA] Call unimplemented SetGroupTempValue with {} {} {}", name, value, printTable(var3));

        return 0;
    }

    public int SetGroupVariableValue(String var, int value) {
        logger.debug("[LUA] Call SetGroupVariableValue with {},{}", var, value);
        val groupId = currentGroup.get().id;
        val variables = getSceneScriptManager().getVariables(groupId);
        val old = variables.getOrDefault(var, value);
        variables.put(var, value);
        getSceneScriptManager().callEvent(new ScriptArgs(groupId, EventType.EVENT_VARIABLE_CHANGE, value, old).setEventSource(var));
        return 0;
    }

    public int SetGroupVariableValueByGroup(String var, int value, int groupId) {
        logger.debug("[LUA] Call SetGroupVariableValueByGroup with {},{},{}", var, value, groupId);
        val variables = getSceneScriptManager().getVariables(groupId);
        val old = variables.getOrDefault(var, value);
        variables.put(var, value);
        getSceneScriptManager().callEvent(new ScriptArgs(groupId, EventType.EVENT_VARIABLE_CHANGE, value, old).setEventSource(var));
        return 0;
    }

    public int SetHandballGalleryBallPosAndRot(int galleryId, LuaTable position, LuaTable rotation) {
        logger.warn("[LUA] Call unimplemented SetHandballGalleryBallPosAndRot with {} {} {}", galleryId, printTable(position), printTable(rotation));

        return 0;
    }

    public int SetIsAllowUseSkill(int canUse) {
        logger.debug("[LUA] Call SetIsAllowUseSkill with {}", canUse);
        getSceneScriptManager().getScene().broadcastPacket(new PacketCanUseSkillNotify(canUse == 1));
        return 0;
    }

    public int SetMonsterBattleByGroup(int configId, int groupId) {
        logger.debug("[LUA] Call SetMonsterBattleByGroup with {} {}", configId, groupId);

        var entity = getSceneScriptManager().getScene().getEntityByConfigId(configId, groupId);
        if (entity instanceof EntityMonster monster) {
            this.getSceneScriptManager().getScene().broadcastPacket(new PacketMonsterForceAlertNotify(monster.getId()));
        }
        return 0;
    }

    public int SetPlatformPointArray(int entityConfigId, int pointArrayId, int[] var3, LuaTable var4) {
        logger.warn("[LUA] Call unchecked SetPlatformPointArray with {} {} {} {}", entityConfigId, pointArrayId, var3, printTable(var4));

        val entity = getSceneScriptManager().getScene().getEntityByConfigId(entityConfigId, getCurrentGroup().get().id);
        if (entity == null) {
            return 1;
        }
        if (!(entity instanceof EntityGadget entityGadget)) {
            return 2;
        }

        var routeConfig = entityGadget.getRouteConfig();
        if (!(routeConfig instanceof PointArrayRoute)) {
            routeConfig = new PointArrayRoute((entityGadget).getMetaGadget());
            entityGadget.setRouteConfig(routeConfig);
        }

        val configRoute = (PointArrayRoute) routeConfig;

        if (configRoute.getPointArrayId() == pointArrayId) {
            return -1;
        }

        configRoute.setPointArrayId(pointArrayId);

        sceneScriptManager.get().getScene().broadcastPacket(new PacketPlatformChangeRouteNotify(entityGadget));

        return -1;
    }

    public int SetPlatformRouteId(int entityConfigId, int routeId) {
        logger.debug("[LUA] Call SetPlatformRouteId {} {}", entityConfigId, routeId);

        val entity = getSceneScriptManager().getScene().getEntityByConfigId(entityConfigId, getCurrentGroup().get().id);
        if (entity == null) {
            return 1;
        }
        if (!(entity instanceof EntityGadget entityGadget)) {
            return 2;
        }

        var routeConfig = entityGadget.getRouteConfig();
        if (!(routeConfig instanceof ConfigRoute)) {
            routeConfig = new ConfigRoute((entityGadget).getMetaGadget());
            entityGadget.setRouteConfig(routeConfig);
        }

        val configRoute = (ConfigRoute) routeConfig;
        if (configRoute.getRouteId() == routeId) {
            return 0;
        }

        configRoute.setRouteId(routeId);
        configRoute.setStartIndex(0);
        configRoute.setStarted(false);
        for (var task : configRoute.getScheduledIndexes()) {
            sceneScriptManager.get().getScene().getScheduler().cancelTask(task);
        }
        configRoute.getScheduledIndexes().clear();

        sceneScriptManager.get().getScene().broadcastPacket(new PacketPlatformChangeRouteNotify(entityGadget));
        return 0;
    }

    public int SetPlayerInteractOption(String var1) {
        logger.warn("[LUA] Call unimplemented SetPlayerInteractOption {}", var1);

        return 0;
    }

    public int SetTeamEntityGlobalFloatValue(int[] sceneUidList, String key, double value) {
        return TeamGlobalValues.set(teamValueScene(), sceneUidList, key, value);
    }

    public int SetTeamServerGlobalValue(int uid, String key, double value) {
        return TeamGlobalValues.setServer(teamValueScene(), uid, key, value);
    }

    public int SetWeatherAreaState(int weatherAreaId, int state) {
        logger.debug("[LUA] Call SetWeatherAreaState with {} {}", weatherAreaId, state);
        var manager = getSceneScriptManagerOrNull();
        if (manager == null || weatherAreaId <= 0 || (state != 0 && state != 1)) return 1;
        var scene = manager.getScene();
        if (scene == null
                || (state == 1 && !WeatherAreaState.canActivate(weatherAreaId, scene.getId())))
            return 1;
        boolean success = true;
        for (var player : scene.getPlayers())
            success &= WeatherAreaState.apply(player, weatherAreaId, state);
        return success ? 0 : 1;
    }

    public int SetWorktopOptions(LuaTable table) {
        logger.debug("[LUA] Call SetWorktopOptions with {}", printTable(table));
        var callParams = this.callParams.getIfExists();
        var group = this.currentGroup.getIfExists();
        if (callParams == null || group == null) {
            return 1;
        }
        var configId = callParams.param1;
        var entity = getSceneScriptManager().getScene().getEntityByConfigId(configId, getCurrentGroup().get().id);

        var worktopOptions = new int[table.length()];
        for (int i = 1; i <= table.length(); i++) {
            worktopOptions[i - 1] = table.get(i).optint(-1);
        }
        if (!(entity instanceof EntityGadget gadget) || worktopOptions.length == 0) {
            return 2;
        }

        if (!(gadget.getContent() instanceof GadgetWorktop worktop)) {
            return 3;
        }

        worktop.addWorktopOptions(worktopOptions);
        var scene = this.getSceneScriptManager().getScene();

        synchronized (this.getSceneScriptManager().getScene()) {
            scene.broadcastPacket(new PacketWorktopOptionNotify(gadget));
        }
        return 0;
    }

    public int SetWorktopOptionsByGroupId(int groupId, int configId, int[] options) {
        logger.debug("[LUA] Call SetWorktopOptionsByGroupId with {},{},{}", groupId, configId, options);
        val entity = getSceneScriptManager().getScene().getEntityByConfigId(configId, groupId);

        // Both failures abort the whole Lua action that called this - the abyss puts its second-half
        // worktop up this way, and a silent 1 here left the half swapped in with nothing to start it.
        if (!(entity instanceof EntityGadget gadget)) {
            logger.warn(
                    "SetWorktopOptionsByGroupId: no gadget {} in group {} of scene {}",
                    configId, groupId, getSceneScriptManager().getScene().getId());
            return 1;
        }

        if (!(gadget.getContent() instanceof GadgetWorktop worktop)) {
            logger.warn(
                    "SetWorktopOptionsByGroupId: gadget {} in group {} is not a worktop but {}",
                    configId, groupId,
                    gadget.getContent() == null ? "nothing" : gadget.getContent().getClass().getSimpleName());
            return 2;
        }

        worktop.addWorktopOptions(options);
        this.getSceneScriptManager().getScene().broadcastPacket(new PacketWorktopOptionNotify(gadget));

        return 0;
    }

    public int ShowClientGuide(String guideName) {
        logger.warn("[LUA] Call unchecked ShowClientGuide with {}", guideName);
        if (GameData.getGuideTriggerDataStringMap().get(guideName) != null) {

            return 1;
        }
        sceneScriptManager.get().getScene().broadcastPacket(new PacketShowClientGuideNotify(guideName));
        return 0;
    }

    public int ShowReminder(int reminderId) {
        logger.debug("[LUA] Call ShowReminder with {}", reminderId);
        getSceneScriptManager().getScene().broadcastPacket(new PacketDungeonShowReminderNotify(reminderId));
        return 0;
    }

    public int ShowReminderRadius(int var1, LuaTable var2, int var3) {
        logger.warn("[LUA] Call unimplemented ShowReminderRadius with {} {} {}", var1, printTable(var2), var3);

        return 0;
    }

    public int StartFatherChallenge(int var1) {
        logger.warn("[LUA] Call unimplemented StartFatherChallenge with {}", var1);

        return 0;
    }

    public int StartGallery(int galleryId) {
        logger.warn("[LUA] Call unimplemented StartGallery with {}", galleryId);

        return 0;
    }

    public int StartHomeGallery(int galleryId, int uid) {
        logger.warn("[LUA] Call unimplemented StartHomeGallery with {} {}", galleryId, uid);

        return 0;
    }

    public int StartPlatform(int configId) {
        logger.debug("[LUA] Call StartPlatform {} ", configId);
        val entity = sceneScriptManager.get().getScene().getEntityByConfigId(configId, getCurrentGroup().get().id);
        if (!(entity instanceof EntityGadget entityGadget)) {
            return 1;
        }

        return entityGadget.startPlatform() ? 0 : 2;
    }

    public int StartSealBattle(int gadgetId, LuaTable parameters) {
        var manager = sceneScriptManager.getIfExists();
        var group = currentGroup.getIfExists();
        if (manager == null || group == null) return -1;
        var entity = manager.getScene().getEntityByConfigId(gadgetId, group.id);
        if (!(entity instanceof EntityGadget gadget)) return -1;
        try {
            var config = emu.grasscutter.game.world.seal.SealBattleParameters.fromLua(parameters);
            return manager.getScene().getSealBattleManager().start(gadget, group, config) ? 0 : -1;
        } catch (IllegalArgumentException invalid) {
            logger.debug("Invalid seal battle {}: {}", gadgetId, invalid.getMessage());
            return -1;
        }
    }

    public int CreateAsterMidGeneralRewardGadget(LuaTable parameters) {
        var manager = sceneScriptManager.getIfExists();
        var group = currentGroup.getIfExists();
        if (manager == null || group == null || parameters == null) return -1;
        return manager.getScene().getAsterMidSceneController().createReward(group,
                parameters.get("config_id").optint(0), parameters.get("difficulty_id").optint(0)) ? 0 : -1;
    }

    public int StopChallenge(int var1, int var2) {
        logger.warn("[LUA] Call unimplemented StopChallenge with {} {}", var1, var2);

        return 0;
    }

    public int StopGallery(int galleryId, boolean var2) {
        logger.warn("[LUA] Call unimplemented StopGallery with {} {}", galleryId, var2);

        return 0;
    }

    public int StopPlatform(int configId) {
        logger.debug("[LUA] Call StopPlatform {} ", configId);
        val entity = sceneScriptManager.get().getScene().getEntityByConfigId(configId, getCurrentGroup().get().id);
        if (!(entity instanceof EntityGadget entityGadget)) {
            return 1;
        }

        return entityGadget.stopPlatform() ? 0 : 2;
    }

    public int TowerCountTimeStatus(int isDone, int var2) {
        logger.warn("[LUA] Call unimplemented TowerCountTimeStatus with {},{}", isDone, var2);

        return 0;
    }

    public int TowerMirrorTeamSetUp(int team) {
        return TowerMirrorTeamSetUp(team, 0);
    }

    public int TowerMirrorTeamSetUp(int team, int var1) {
        logger.debug("[LUA] Call TowerMirrorTeamSetUp with {},{}", team, var1);
        // This is the mid-chamber team swap on two-team floors, driven from the floor's Lua.
        var scripts = getSceneScriptManager();
        var scene = scripts.getScene();
        var players = scene.getPlayers();
        var manager = scene.getDungeonManager();
        if (players.size() != 1
                || manager == null
                || !manager.isTowerDungeon()
                || players.get(0).getScene() != scene
                || !players.get(0).getTowerManager().tryMirrorTeamSetUp(team - 1)) return 1;
        scripts.unloadCurrentMonsterTide();
        return 0;
    }

    public int TransPlayerToPos(LuaTable var1) {
        var scriptManager = sceneScriptManager.getIfExists();
        if (scriptManager == null || var1 == null) return 2;
        var targetsTable = var1.get("uid_list");
        if (!targetsTable.istable() || targetsTable.length() == 0) return 1;
        var targetPos = relocationVector(var1.get("pos"), false);
        var rot = var1.get("rot");
        var targetRot = rot.isnil() ? null : relocationVector(rot, true);
        if (targetPos == null || !rot.isnil() && targetRot == null) return 2;
        var ids = new LinkedHashSet<Integer>();
        for (int i = 1; i <= targetsTable.length(); i++) {
            var uid = targetsTable.get(i);
            if (!uid.isint() || uid.toint() <= 0) return 1;
            ids.add(uid.toint());
        }
        var scene = scriptManager.getScene();
        synchronized (scene) {
            if (!scriptManager.allowsGroupCall(getCurrentGroup().orElse(null))) return 2;
            var targets = new ArrayList<Player>(ids.size());
            for (int uid : ids) {
                var player =
                        scene.getPlayers().stream()
                                .filter(member -> member.getUid() == uid)
                                .findFirst()
                                .orElse(null);
                if (!scene.canRelocatePlayer(player)) return 2;
                targets.add(player);
            }
            for (var player : targets)
                if (!scene.relocatePlayer(player, targetPos, targetRot)) return 2;
            return 0;
        }
    }

    private static Position relocationVector(LuaValue value, boolean optionalAxes) {
        if (!value.istable()) return null;
        float[] axes = new float[3];
        String[] names = {"x", "y", "z"};
        for (int i = 0; i < names.length; i++) {
            var axis = value.get(names[i]);
            if (axis.isnil() && optionalAxes) continue;
            if (!axis.isnumber()) return null;
            axes[i] = axis.tofloat();
            if (!Float.isFinite(axes[i])) return null;
        }
        return new Position(axes[0], axes[1], axes[2]);
    }

    public int TryReallocateEntityAuthority(int uid, int endConfig, int var3) {
        logger.warn("[LUA] Call unimplemented TryReallocateEntityAuthority with {} {} {}", uid, endConfig, var3);

        return 0;
    }

    public int UnfreezeGroupLimit(int dungeonEntryId) {

        logger.debug("[LUA] Call UnfreezeGroupLimit with {}", dungeonEntryId);

        var scene = sceneScriptManager.get().getScene();
        scene.getPlayers().get(0).sendPacket(new PacketUnfreezeGroupLimitNotify(
                dungeonEntryId,
                scene.getId()));

        return 0;
    }

    public int UnlockFloatSignal(int groupId, int gadgetSignalId) {
        logger.warn("[LUA] Call unimplemented UnlockFloatSignal with {} {}", groupId, gadgetSignalId);

        return 0;
    }

    public int UnlockForce(int force) {
        logger.debug("[LUA] Call UnlockForce {}", force);
        getSceneScriptManager().getScene().unlockForce(force);
        return 0;
    }

    public int UpdatePlayerGalleryScore(int galleryId, LuaTable var2) {
        logger.warn("[LUA] Call unimplemented UpdatePlayerGalleryScore with {} {}", galleryId, printTable(var2));

        return 0;
    }

    public int CreateVariable(String type, Object value) {
        logger.warn("[LUA] Call unimplemented CreateVariable with {} {}", type, value);

        switch (type) {
            case "int":
            default:
                logger.warn("[LUA] Call CreateVariable with unsupported type {} and value {}", type, value);
        }
        return 0;
    }

    public int SetVariableValue(int var1) {
        logger.warn("[LUA] Call unimplemented SetVariableValue with {}", var1);

        return 0;
    }

    public int GetVariableValue(int var1) {
        logger.warn("[LUA] Call unimplemented GetVariableValue with {}", var1);

        return 0;
    }

    public int GetEntityType(int entityId) {
        logger.debug("[LUA] Call GetEntityType with {}", entityId);
        var entity = getSceneScriptManager().getScene().getEntityById(entityId);
        if (entity == null) {
            return EntityType.None.getValue();
        }

        return entity.getEntityType().getValue();
    }

    public int GetSeaLampActivityPhase() {
        logger.warn("[LUA] Call unimplemented GetSeaLampActivityPhase");

        return 0;
    }

    public long GetServerTimeByWeek() {
        logger.debug("[LUA] Call GetServerTimeByWeek");
        return Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
    }

    public int LockForce(int force) {
        logger.debug("[LUA] Call LockForce {}", force);
        getSceneScriptManager().getScene().lockForce(force);
        return 0;
    }

    public int GetMonsterID(int var1) {

        return 0;
    }

    public int SetGadgetState(int gadgetState) {
        logger.debug("[LUA] Call SetGadgetState with {}", gadgetState);
        EntityGadget gadget = getCurrentEntityGadget();
        if (gadget == null) return -1;

        gadget.updateState(gadgetState);
        return 0;
    }

    public int GetGadgetState(int gadgetState) {
        logger.debug("[LUA] Call GetGadgetState with {}", gadgetState);
        EntityGadget gadget = getCurrentEntityGadget();
        if (gadget == null) return -1;

        return gadget.getState();
    }

    public int ResetGadgetState(int gadgetState) {
        logger.debug("[LUA] Call ResetGadgetState with {}", gadgetState);
        EntityGadget gadget = getCurrentEntityGadget();
        if (gadget == null) return -1;

        gadget.getPosition().set(gadget.getBornPos());
        gadget.getRotation().set(gadget.getBornRot());
        gadget.setStartValue(0);
        gadget.setStopValue(0);
        gadget.updateState(gadgetState);
        return 0;
    }

    public int SetGearStartValue(int startValue) {
        logger.debug("[LUA] Call SetGearStartValue with {}", startValue);
        EntityGadget gadget = getCurrentEntityGadget();
        if (gadget == null) return -1;

        gadget.setStartValue(startValue);
        return 0;
    }

    public int GetGearStartValue() {
        logger.debug("[LUA] Call GetGearStartValue");
        EntityGadget gadget = getCurrentEntityGadget();
        if (gadget == null) return -1;

        return gadget.getStartValue();
    }

    public int SetGearStopValue(int stopValue) {
        logger.debug("[LUA] Call SetGearStopValue with {}", stopValue);
        EntityGadget gadget = getCurrentEntityGadget();
        if (gadget == null) return -1;

        gadget.setStopValue(stopValue);
        return 0;
    }

    public int GetGearStopValue() {
        logger.debug("[LUA] Call GetGearStopValue");
        EntityGadget gadget = getCurrentEntityGadget();
        if (gadget == null) return -1;

        return gadget.getStopValue();
    }

    public int GetGadgetStateBeginTime() {
        logger.debug("[LUA] Call GetGadgetStateBeginTime");
        EntityGadget gadget = getCurrentEntityGadget();
        if (gadget == null) return -1;

        return gadget.getTicksSinceChange();
    }

    public int GetContextGadgetConfigId() {
        logger.debug("[LUA] Call GetContextGadgetConfigId");
        EntityGadget gadget = getCurrentEntityGadget();
        if (gadget == null) return -1;

        return gadget.getConfigId();
    }

    public int DropSubfield(LuaTable table) {
        logger.debug("[LUA] Call DropSubfield with {}", printTable(table));
        String subfield_name = table.get("subfield_name").toString();
        var entity = getCurrentEntity();
        if (!entity.isPresent()) return -1;

        entity.get().dropSubfield(subfield_name);

        return -1;
    }

    public int[] GetGatherConfigIdList() {
        logger.debug("[LUA] Call GetGatherConfigIdList");
        EntityGadget gadget = getCurrentEntityGadget();

        var children = gadget.getChildren();

        int[] configIds = new int[children.size()];
        for (int i = 0; i < children.size(); i++) {
            configIds[i] = children.get(i).getConfigId();
        }

        return configIds;
    }
}
