package emu.grasscutter.game.world;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.GameDepot;
import emu.grasscutter.data.binout.SceneNpcBornEntry;
import emu.grasscutter.data.binout.routes.Route;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.codex.CodexAnimalData;
import emu.grasscutter.data.excels.monster.MonsterData;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.data.excels.world.WorldLevelData;
import emu.grasscutter.data.server.Grid;
import emu.grasscutter.game.activity.crucible.CrucibleSceneController;
import emu.grasscutter.game.activity.salesman.SalesmanSceneController;
import emu.grasscutter.game.activity.salesman.SalesmanNpcScene;
import emu.grasscutter.game.activity.aster.AsterSceneController;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.dungeons.DungeonManager;
import emu.grasscutter.game.dungeons.DungeonSettleListener;
import emu.grasscutter.game.dungeons.challenge.WorldChallenge;
import emu.grasscutter.game.dungeons.enums.DungeonPassConditionType;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.entity.gadget.GadgetWorktop;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.managers.blossom.BlossomManager;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.TeamInfo;
import emu.grasscutter.game.props.*;
import emu.grasscutter.game.quest.QuestGroupSuite;
import emu.grasscutter.game.world.data.TeleportProperties;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.net.proto.AttackResultOuterClass.AttackResult;
import emu.grasscutter.net.proto.ChangeHpDebtsReasonOuterClass;
import emu.grasscutter.net.proto.ChangeHpReasonOuterClass.ChangeHpReason;
import emu.grasscutter.net.proto.PropChangeReasonOuterClass;
import emu.grasscutter.net.proto.VisionTypeOuterClass.VisionType;
import emu.grasscutter.scripts.SceneIndexManager;
import emu.grasscutter.scripts.SceneScriptManager;
import emu.grasscutter.scripts.constants.EventType;
import emu.grasscutter.scripts.data.SceneBlock;
import emu.grasscutter.scripts.data.SceneGroup;
import emu.grasscutter.scripts.data.ScriptArgs;
import emu.grasscutter.server.event.entity.EntityCreationEvent;
import emu.grasscutter.server.event.player.PlayerTeleportEvent;
import emu.grasscutter.server.packet.send.*;
import emu.grasscutter.server.scheduler.ServerTaskScheduler;
import emu.grasscutter.utils.algorithms.KahnsSort;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.*;

import static emu.grasscutter.GameConstants.ENTITY_ID_BIT_SHIFT;

public class Scene {
    @Getter private final World world;
    @Getter private final SceneData sceneData;
    @Getter private final List<Player> players;
    @Getter private final Map<Integer, GameEntity> entities;
    @Getter private final Map<Integer, GameEntity> weaponEntities;
    @Getter private final Set<SpawnDataEntry> spawnedEntities;
    @Getter private final Set<SpawnDataEntry> deadSpawnedEntities;
    @Getter private final Set<SceneBlock> loadedBlocks;
    @Getter private final Set<SceneGroup> loadedGroups;
    @Getter private final BlossomManager blossomManager;
    private final HashSet<Integer> unlockedForces;
    private final long startWorldTime;
    @Getter @Setter DungeonManager dungeonManager;
    @Getter private final emu.grasscutter.game.dungeons.DungeonBossCleanup dungeonBossCleanup =
            new emu.grasscutter.game.dungeons.DungeonBossCleanup(this);
    @Getter Int2ObjectMap<Route> sceneRoutes;
    private Set<SpawnDataEntry.GridBlockId> loadedGridBlocks;
    @Getter @Setter private boolean dontDestroyWhenEmpty;
    @Getter private final SceneScriptManager scriptManager;
    @Getter private final CrucibleSceneController crucibleSceneController;
    @Getter private final SalesmanSceneController salesmanSceneController;
    @Getter private final AsterSceneController asterSceneController;
    @Getter private final emu.grasscutter.game.activity.aster.AsterMidSceneController asterMidSceneController;
    @Getter private final emu.grasscutter.game.activity.aster.AsterBigSceneController asterBigSceneController;
    @Getter private final emu.grasscutter.game.world.seal.SealBattleManager sealBattleManager;
    @Getter private final emu.grasscutter.game.world.sceneplay.ScenePlayBattleManager scenePlayBattleManager;
    @Getter @Setter private WorldChallenge challenge;
    @Getter private List<DungeonSettleListener> dungeonSettleListeners;
    @Getter @Setter private int prevScene;
    @Getter @Setter private int prevScenePoint;
    @Getter @Setter private int killedMonsterCount;
    private Set<SceneNpcBornEntry> npcBornEntrySet;
    @Getter private volatile boolean finishedLoading = false;
    @Getter protected int tickCount = 0;
    private long lastTimeNotify = 0;
    private long lastStreamCheck = 0;
    @Getter private boolean isPaused = false;

    private final List<Runnable> afterLoadedCallbacks = new ArrayList<>();
    private final Map<Player, List<Runnable>> afterPlayerEntryCallbacks = new IdentityHashMap<>();
    private final Queue<Runnable> afterHostInitCallbacks = new ConcurrentLinkedQueue<>();

    @Getter private GameEntity sceneEntity;
    @Getter private final ServerTaskScheduler scheduler;

    public Scene(World world, SceneData sceneData) {
        this(world, sceneData, true);
    }

    public Scene(World world, SceneData sceneData, boolean persistentGroups) {
        this.world = world;
        this.sceneData = sceneData;
        this.players = new CopyOnWriteArrayList<>();
        this.entities = new ConcurrentHashMap<>();
        this.weaponEntities = new ConcurrentHashMap<>();

        this.prevScene = 3;
        this.sceneRoutes = GameData.getSceneRoutes(getId());

        this.startWorldTime = world.getWorldTime();

        this.spawnedEntities = ConcurrentHashMap.newKeySet();
        this.deadSpawnedEntities = ConcurrentHashMap.newKeySet();
        this.loadedBlocks = ConcurrentHashMap.newKeySet();
        this.loadedGroups = ConcurrentHashMap.newKeySet();
        this.loadedGridBlocks = new HashSet<>();
        this.npcBornEntrySet = ConcurrentHashMap.newKeySet();
        this.scheduler = new ServerTaskScheduler();
        this.crucibleSceneController = new CrucibleSceneController(this);
        this.salesmanSceneController = new SalesmanSceneController(this);
        this.asterSceneController = new AsterSceneController(this);
        this.asterMidSceneController =
                new emu.grasscutter.game.activity.aster.AsterMidSceneController(this);
        this.asterBigSceneController =
                new emu.grasscutter.game.activity.aster.AsterBigSceneController(this);
        this.sealBattleManager = new emu.grasscutter.game.world.seal.SealBattleManager(this);
        this.scenePlayBattleManager =
                new emu.grasscutter.game.world.sceneplay.ScenePlayBattleManager(this);
        this.scriptManager = new SceneScriptManager(this, persistentGroups);
        this.blossomManager = new BlossomManager(this);
        this.unlockedForces = new HashSet<>();
        this.sceneEntity = new EntityScene(this);
    }

    public int getId() {
        return sceneData.getId();
    }

    public SceneType getSceneType() {
        return getSceneData().getSceneType();
    }

    public int getPlayerCount() {
        return this.getPlayers().size();
    }

    public Player getHost() {
        return this.getWorld().getHost();
    }

    public GameEntity getEntityById(int id) {

        if (id == 0x13800001) return this.sceneEntity;
        else if (id == this.getWorld().getLevelEntityId()) return this.getWorld().getEntity();

        var teamEntityPlayer =
                players.stream().filter(p -> p.getTeamManager().getEntity().getId() == id).findAny();
        if (teamEntityPlayer.isPresent()) return teamEntityPlayer.get().getTeamManager().getEntity();

        var entity = this.entities.get(id);
        if (entity == null) entity = this.weaponEntities.get(id);
        if (entity == null) entity = this.scenePlayBattleManager.getPlayTeamEntity(id);
        if (entity == null && (id >> ENTITY_ID_BIT_SHIFT) == EntityIdType.AVATAR.getId()) {
            for (var player : getPlayers()) {
                for (var avatar : player.getTeamManager().getActiveTeam()) {
                    if (avatar.getId() == id) return avatar;
                }
            }
        }

        if (entity == null && (id >> ENTITY_ID_BIT_SHIFT) == EntityIdType.WEAPON.getId()) {
            for (var player : this.getPlayers()) {
                for (var avatar : player.getTeamManager().getActiveTeam()) {
                    if (avatar.getWeaponEntityId() == id) return avatar;
                }
            }
        }

        return entity;
    }

    public GameEntity getFirstEntityByConfigId(int configId) {
        return this.entities.values().stream()
                .filter(x -> x.getConfigId() == configId)
                .findFirst()
                .orElse(null);
    }

    public GameEntity getEntityByConfigId(int configId, int groupId) {
        return this.entities.values().stream()
                .filter(x -> x.getConfigId() == configId && x.getGroupId() == groupId)
                .findFirst()
                .orElse(null);
    }

    @Nullable public Route getSceneRouteById(int routeId) {
        return sceneRoutes.get(routeId);
    }

    public void setPaused(boolean paused) {
        if (this.isPaused != paused) {
            this.isPaused = paused;
            this.broadcastPacket(new PacketSceneTimeNotify(this));
        }
    }

    public int getSceneTime() {
        return (int) (this.getWorld().getWorldTime() - this.startWorldTime);
    }

    public int getSceneTimeSeconds() {
        return this.getSceneTime() / 1000;
    }

    public void addDungeonSettleObserver(DungeonSettleListener dungeonSettleListener) {
        if (dungeonSettleListeners == null) {
            dungeonSettleListeners = new ArrayList<>();
        }

        dungeonSettleListeners.add(dungeonSettleListener);
    }

    public void triggerDungeonEvent(DungeonPassConditionType conditionType, int... params) {
        if (this.dungeonManager == null) return;
        this.dungeonManager.triggerEvent(conditionType, params);
    }

    public boolean isInScene(GameEntity entity) {
        return this.entities.containsKey(entity.getId());
    }

    public void addPlayer(Player player) {
        final List<Runnable> arrived;
        synchronized (player.getSceneTransferLock()) {
            if (getPlayers().contains(player)) return;

            // Validate before leaving so an unavailable TPS scene preserves the current party.
            if (!emu.grasscutter.game.tps.TpsAvatarSystem.canEnter(player, this.getSceneData()))
                throw new IllegalStateException("TPS scene resources unavailable: " + this.getId());

            // Never hold the destination monitor while waiting for the source scene.
            var previous = player.getScene();
            if (previous != null && previous != this) previous.removePlayer(player);

            synchronized (this) {
                if (getPlayers().contains(player)) return;
                getPlayers().add(player);
                player.setSceneId(this.getId());
                player.setScene(this);

                emu.grasscutter.game.tps.TpsAvatarSystem.prepareSceneTeam(player, this);
                this.setupPlayerAvatars(player);
                if (player.hasSentLoginPackets()) {
                    var borrowed = player.getTeamManager().getTpsSceneAvatar();
                    if (borrowed != null)
                        player.sendPacket(new PacketAvatarAddNotify(borrowed, false));
                    player.sendPacket(new PacketAvatarTeamUpdateNotify(player));
                }
                arrived = afterPlayerEntryCallbacks.remove(player);
            }
        }
        if (arrived != null) arrived.forEach(this::invokeSceneCallback);
    }

    /** Wait for this live player object to join; a saved scene ID is not proof of arrival. */
    public void runWhenPlayerEnters(Player player, Runnable callback) {
        if (player == null || callback == null) return;
        synchronized (this) {
            if (scriptManager.isDestroyed() || player.getWorld() != world) return;
            if (player.getScene() != this
                    || players.stream().noneMatch(member -> member == player)) {
                afterPlayerEntryCallbacks
                        .computeIfAbsent(player, ignored -> new ArrayList<>())
                        .add(callback);
                return;
            }
        }
        invokeSceneCallback(callback);
    }

    public void removePlayer(Player player) {
        synchronized (player.getSceneTransferLock()) {
            synchronized (this) {
                if (getPlayers().stream().noneMatch(member -> member == player)) return;

                if (this.getChallenge() != null && this.getChallenge().inProgress()) {
                    player.sendPacket(new PacketDungeonChallengeFinishNotify(this.getChallenge()));
                }

                this.crucibleSceneController.onPlayerLeaving(player);
                this.salesmanSceneController.onPlayerLeaving(player);
                this.asterBigSceneController.onPlayerLeaving(player);
                this.sealBattleManager.onPlayerLeaving(player);
                this.scenePlayBattleManager.onPlayerLeaving(player);
                getPlayers().remove(player);
                player.setScene(null);

                this.removePlayerAvatars(player);

                for (EntityBaseGadget gadget : player.getTeamManager().getGadgets()) {
                    this.removeEntity(gadget);
                }

                this.getEntities().values().stream()
                        .filter(gameEntity -> gameEntity instanceof EntityVehicle)
                        .map(gameEntity -> (EntityVehicle) gameEntity)
                        .filter(entityVehicle -> entityVehicle.getOwner().equals(player))
                        .forEach(
                                entityVehicle ->
                                        this.removeEntity(
                                                entityVehicle,
                                                VisionType.VisionType_VISION_REMOVE));

                if (this.getPlayerCount() <= 0 && !this.dontDestroyWhenEmpty) {
                    afterPlayerEntryCallbacks.clear();
                    this.crucibleSceneController.close();
                    this.salesmanSceneController.close();
                    this.asterSceneController.close();
                    this.asterMidSceneController.close();
                    this.asterBigSceneController.close();
                    this.getScriptManager().onDestroy();
                    this.getWorld().deregisterScene(this);
                }

                this.saveGroups();
            }
        }
    }

    private void setupPlayerAvatars(Player player) {

        player.getTeamManager().getActiveTeam().clear();

        TeamInfo teamInfo = player.getTeamManager().getCurrentTeamInfo();
        for (int avatarId : teamInfo.getAvatars()) {
            Avatar avatar = player.getTeamManager().getAvatarForTeam(avatarId);
            if (avatar == null) continue;
            player
                    .getTeamManager()
                    .getActiveTeam()
                    .add(
                            EntityCreationEvent.call(
                                    EntityAvatar.class,
                                    new Class<?>[] {Scene.class, Avatar.class},
                                    new Object[] {player.getScene(), avatar}));
        }

        if (player.getTeamManager().getCurrentCharacterIndex()
                        >= player.getTeamManager().getActiveTeam().size()
                || player.getTeamManager().getCurrentCharacterIndex() < 0) {
            player
                    .getTeamManager()
                    .setCurrentCharacterIndex(0);
        }
        player.getTeamManager().refreshTeamSkillLevelBonuses(false);
        player.getTeamManager().refreshTeamResonances(false);
    }

    private synchronized void removePlayerAvatars(Player player) {
        emu.grasscutter.game.tps.TpsWeaponSystem.releaseSceneWeapons(player, this);
        var team = player.getTeamManager().getActiveTeam();

        team.forEach(e -> removeEntity(e, VisionType.VisionType_VISION_REMOVE));
        team.clear();
        if (player.getTeamManager().endTpsSceneTeam(this)) player.save();
    }

    public void spawnPlayer(Player player) {
        var teamManager = player.getTeamManager();
        if (this.isInScene(teamManager.getCurrentAvatarEntity())) {
            return;
        }

        if (teamManager.getCurrentAvatarEntity().getFightProperty(FightProperty.FIGHT_PROP_CUR_HP)
                <= 0f) {
            teamManager.getCurrentAvatarEntity().setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 1f);
        }

        this.addEntity(teamManager.getCurrentAvatarEntity());

        teamManager.getActiveTeam().stream()
                .map(EntityAvatar::getAvatar)
                .forEach(Avatar::sendSkillExtraChargeMap);
    }

    private void addEntityDirectly(GameEntity entity) {
        var previous = getEntities().put(entity.getId(), entity);
        if (previous != null && previous != entity) previous.invalidateAbilityLifecycle();
        entity.activateAbilityLifecycle();
        entity.onCreate();
    }

    public synchronized void addEntity(GameEntity entity) {
        this.addEntityDirectly(entity);
        this.broadcastPacket(new PacketSceneEntityAppearNotify(entity));
    }

    public synchronized void addEntityToSingleClient(Player player, GameEntity entity) {
        this.addEntityDirectly(entity);
        player.sendPacket(new PacketSceneEntityAppearNotify(entity));
    }

    public void addDropEntity(GameItem item, GameEntity bornForm, Player player, boolean share) {

        ItemData itemData = GameData.getItemDataMap().get(item.getItemId());
        if (itemData == null) return;
        if (itemData.isEquip()) {
            float range = (1.5f + (.05f * item.getCount()));
            for (int j = 0; j < item.getCount(); j++) {
                Position pos = bornForm.getPosition().nearby2d(range).addY(0.5f);
                EntityItem entity = new EntityItem(this, player, itemData, pos, item.getCount(), share);
                addEntity(entity);
            }
        } else {
            EntityItem entity =
                    new EntityItem(
                            this,
                            player,
                            itemData,
                            bornForm.getPosition().clone().addY(0.5f),
                            item.getCount(),
                            share);
            addEntity(entity);
        }
    }

    public void addEntities(Collection<? extends GameEntity> entities) {
        addEntities(entities, VisionType.VisionType_VISION_BORN);
    }

    public void updateEntity(GameEntity entity) {
        this.broadcastPacket(new PacketSceneEntityUpdateNotify(entity));
    }

    public void updateEntity(GameEntity entity, VisionType type) {
        this.broadcastPacket(new PacketSceneEntityUpdateNotify(Arrays.asList(entity), type));
    }

    private static <T> List<List<T>> chopped(List<T> list, final int L) {
        List<List<T>> parts = new ArrayList<List<T>>();
        final int N = list.size();
        for (int i = 0; i < N; i += L) {
            parts.add(new ArrayList<T>(list.subList(i, Math.min(N, i + L))));
        }
        return parts;
    }

    public synchronized void addEntities(
            Collection<? extends GameEntity> entities, VisionType visionType) {
        if (entities == null || entities.isEmpty()) {
            return;
        }

        for (var entity : entities) {
            this.addEntityDirectly(entity);
        }

        for (var l : chopped(new ArrayList<>(entities), 100)) {
            this.broadcastPacket(new PacketSceneEntityAppearNotify(l, visionType));
        }
    }

    private GameEntity removeEntityDirectly(GameEntity entity) {
        // Party avatars can be removed before their first appearance packet registers them.
        if (entity.getScene() == this) entity.invalidateAbilityLifecycle();
        if (!getEntities().remove(entity.getId(), entity)) return null;
        this.sealBattleManager.onEntityRemoved(entity);
        var removed = entity;
        if (removed != null) {
            removed.onRemoved();
        }
        return removed;
    }

    public void removeEntity(GameEntity entity) {
        this.removeEntity(entity, VisionType.VisionType_VISION_DIE);
    }

    public synchronized void removeEntity(GameEntity entity, VisionType visionType) {
        GameEntity removed = this.removeEntityDirectly(entity);
        if (removed != null) {
            this.broadcastPacket(new PacketSceneEntityDisappearNotify(removed, visionType));
        }
    }

    public void removeEntities(List<GameEntity> entity, VisionType visionType) {
        var toRemove =
                entity.stream()
                        .filter(Objects::nonNull)
                        .map(this::removeEntityDirectly)
                        .filter(Objects::nonNull)
                        .toList();
        if (!toRemove.isEmpty()) {
            this.broadcastPacket(new PacketSceneEntityDisappearNotify(toRemove, visionType));
        }
    }

    public synchronized void replaceEntity(EntityAvatar oldEntity, EntityAvatar newEntity) {
        if (oldEntity.getPlayer() == newEntity.getPlayer() && oldEntity.isInSceneParty()
                && newEntity.isInSceneParty()) {
            // Switching visible avatars does not end either party member's ability lifetime.
            this.getEntities().remove(oldEntity.getId(), oldEntity);
        } else this.removeEntityDirectly(oldEntity);
        this.addEntityDirectly(newEntity);
        this.broadcastPacket(
                new PacketSceneEntityDisappearNotify(oldEntity, VisionType.VisionType_VISION_REPLACE));
        this.broadcastPacket(
                new PacketSceneEntityAppearNotify(
                        newEntity, VisionType.VisionType_VISION_REPLACE, oldEntity.getId()));
    }

    public void showOtherEntities(Player player) {
        GameEntity currentEntity = player.getTeamManager().getCurrentAvatarEntity();
        List<GameEntity> entities =
                this.getEntities().values().stream()
                        .filter(entity -> entity != currentEntity)
                        .filter(
                                gameEntity ->
                                        !(gameEntity instanceof Rebornable rebornable) || !rebornable.isInCD())
                        .toList();

        player.sendPacket(new PacketSceneEntityAppearNotify(entities, VisionType.VisionType_VISION_MEET));
        this.sealBattleManager.sendTo(player);
    }

    public void handleAttack(AttackResult result) {
        GameEntity target = getEntityById(result.getDefenseId());
        ElementType attackType = ElementType.getTypeByValue(result.getElementType());

        if (target == null) {
            // 6000 lines of this in a single session, each one dumping a proto for a hit that was
            // going nowhere anyway. Behind isDebugEnabled so the dump is not built when unwanted.
            var logger = Grasscutter.getLogger();
            if (logger.isDebugEnabled()) {
                logger.debug(
                        "handleAttack: target not found defenseId={} attackerId={} damage={} unknownFields={}",
                        result.getDefenseId(),
                        result.getAttackerId(),
                        result.getDamage(),
                        result.getUnknownFields().toString().replaceAll("\\s+", " "));
            }
            return;
        }
        if (target instanceof EntityAvatar) {
            if (((EntityAvatar) target).getPlayer().isInGodMode()) {
                return;
            }
        }

        GameEntity attacker = getEntityById(result.getAttackerId());
        if (attacker instanceof EntityClientGadget && target instanceof EntityAvatar) {
            if (target.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS) > 0f) return;
            float curHp = target.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
            float capped = Math.min(result.getDamage(), curHp - 1f);
            if (capped > 0) target.damage(capped, result.getAttackerId(), attackType);
            return;
        }

        if (target instanceof EntityAvatar avatar) {
            if (avatar.getPlayer()
            .getAbilityManager()
            .isAbilityInvulnerable()) {
                return;
            }
        }

        target.damage(result.getDamage(), result.getAttackerId(), attackType);

        // Arlecchino's 0.03s unique FireAttack modifier consumes Bond through ReduceHPDebts.
        // Paying again for every combat packet also consumed Bond on skills, charged attacks
        // and each target of the same hit, before her burst could snapshot it for healing.

        // Clorinde's gunshot and Impale already add/pay Bond through resource actions.
        // A hit must not charge a second, invented 1.5% payment.
    }

    public void killEntity(GameEntity target) {
        killEntity(target, 0);
    }

    public void killEntity(GameEntity target, int attackerId) {
        killEntity(target, attackerId, ChangeHpReason.ChangeHpReason_CHANGE_HP_NONE);
    }

    public void killEntity(GameEntity target, int attackerId, ChangeHpReason reason) {
        GameEntity attacker = null;
        synchronized (this) {
            if (target == null || target.getScene() != this) return;
            // Ability self-destruction and lethal hits can arrive together or repeat after removal.
            if (!(target instanceof EntityAvatar) && entities.get(target.getId()) != target) return;
            if (!target.tryBeginDeath()) return;
            if (attackerId > 0) attacker = getEntityById(attackerId);
            if (target instanceof EntityMonster monster) {
                dungeonBossCleanup.rememberDeath(monster);
                var owner = attacker == null ? null : attacker.getTrueOwner();
                int uid = owner instanceof EntityAvatar avatar ? avatar.getPlayer().getUid() : 0;
                getScriptManager()
                        .onMonsterDieBeforeLeaveScene(
                                monster,
                                uid,
                                reason == null || reason == ChangeHpReason.UNRECOGNIZED
                                        ? 0
                                        : reason.getNumber());
            }
        }

        if (attacker != null) {

            if (attacker instanceof EntityClientGadget gadgetAttacker) {
                var clientGadgetOwner = getEntityById(gadgetAttacker.getOwnerEntityId());
                if (clientGadgetOwner instanceof EntityAvatar) {
                    ((EntityClientGadget) attacker)
                            .getOwner()
                            .getCodex()
                            .checkAnimal(target, CodexAnimalData.CountType.CODEX_COUNT_TYPE_KILL);
                }
            } else if (attacker instanceof EntityAvatar avatarAttacker) {
                avatarAttacker
                        .getPlayer()
                        .getCodex()
                        .checkAnimal(target, CodexAnimalData.CountType.CODEX_COUNT_TYPE_KILL);
            }
        }

        this.broadcastPacket(
                new PacketLifeStateChangeNotify(attackerId, target, LifeState.LIFE_DEAD));

        // Activity watchers count kills. The attacker may be a summon or a client gadget, so credit
        // the avatar that actually owns it rather than the entity that landed the hit.
        if (target instanceof EntityMonster killed
                && (attacker != null ? attacker.getTrueOwner() : null)
                        instanceof EntityAvatar avatarAttacker) {
            var monsterId = String.valueOf(killed.getMonsterData().getId());
            var activityManager = avatarAttacker.getPlayer().getActivityManager();
            activityManager.triggerWatcher(
                    WatcherTriggerType.TRIGGER_BATTLE_FOR_MONSTER_DIE_OR, monsterId);
            activityManager.triggerWatcher(
                    WatcherTriggerType.TRIGGER_KILL_MONSTERS_WITHOUT_VEHICLE, monsterId);
            getCrucibleSceneController().onMonsterKilled(killed, avatarAttacker.getPlayer());
            this.sealBattleManager.onMonsterKilled(killed, avatarAttacker.getPlayer());
        }

        var world = this.getWorld();
        if (target instanceof EntityMonster monster
                && this.getSceneType() != SceneType.SCENE_DUNGEON) {
            if (monster.getMetaMonster() != null
                    && !world.getServer().getDropSystem().handleMonsterDrop(monster)) {
                Grasscutter.getLogger()
                        .debug(
                                "Can not solve monster drop: drop_id = {}, drop_tag = {}. Falling"
                                    + " back to legacy drop system.",
                                monster.getMetaMonster().drop_id,
                                monster.getMetaMonster().drop_tag);
                world.getServer().getDropSystemLegacy().callDrop(monster);
            }
        }

        if (target instanceof EntityGadget gadget) {
            if (gadget.getMetaGadget() != null) {
                world.getServer()
                        .getDropSystem()
                        .handleChestDrop(
                                gadget.getMetaGadget().drop_id,
                                gadget.getMetaGadget().drop_count,
                                gadget);
            }
        }

        if (!(target instanceof EntityMonster monster
                && dungeonBossCleanup.removeAfterDeath(monster))) this.removeEntity(target);

        if (target instanceof EntityClientGadget cg && cg.getOwner() != null) {
            cg.getOwner().getTeamManager().getGadgets().remove(cg);
        }

        target.onDeath(attackerId);

        // A commission that asks for kills counts them here, by the group the monster belongs to.
        if (target instanceof EntityMonster monster) {
            var host = this.getWorld().getHost();
            if (host != null && host.getDailyTaskManager() != null) {
                host.getDailyTaskManager().onMonsterDeath(this, monster.getGroupId());
            }
        }

        this.triggerDungeonEvent(
                DungeonPassConditionType.DUNGEON_COND_KILL_MONSTER_COUNT, ++killedMonsterCount);
    }

    public void onTick() {

        if (this.getSceneType() == SceneType.SCENE_HOME_WORLD
                || this.getSceneType() == SceneType.SCENE_HOME_ROOM) {
            this.finishLoading();
            return;
        }

        // Every stage stands on its own. A scene whose scripts are incomplete used to throw on the
        // way in and never reach the entity loop below, so nothing in the scene moved, died or
        // despawned - and the player was left in a world that had quietly stopped.
        if (!isPaused) stage("the scheduler", () -> this.getScheduler().runTasks());
        if (!isPaused) stage("the group timers", this.getScriptManager()::tickGroupTimers);
        if (!isPaused) stage("the seal battles", this.sealBattleManager::tick);
        if (!isPaused) stage("the scene play battles", this.scenePlayBattleManager::tick);
        if (!isPaused) stage("the monster tides", this.getScriptManager()::tickMonsterTides);

        // Streaming does not need the full tick rate - it answers "which groups are near the
        // player", and a player cannot outrun half a second of it. The timers and triggers below
        // do need it, which is why only this part is throttled.
        var nowMs = System.currentTimeMillis();
        if (nowMs - this.lastStreamCheck >= 500L) {
            this.lastStreamCheck = nowMs;
            stage("the Salesman NPC suites", () -> this.salesmanSceneController.update(nowMs));
            stage(
                "loading groups",
                () -> {
                    // checkSpawns is the fallback for scenes that have no scripts, so it must not
                    // run while the scripts are merely still loading - init() runs on its own
                    // thread, and every spawn in Spawns.json/GadgetSpawns.json belongs to a scene
                    // that does have scripts. Spawning during that window duplicated those
                    // monsters and gadgets against the scripted copies, and nothing cleaned the
                    // extras up: checkSpawns owns the removal pass, and it stops running the
                    // moment checkGroups takes over.
                    if (this.getScriptManager().isInit()) {
                        this.crucibleSceneController.update(nowMs);
                        this.asterSceneController.update(nowMs);
                        this.asterMidSceneController.update(nowMs);
                        this.asterBigSceneController.update(nowMs);
                        this.checkGroups();
                    }
                    else if (this.getScriptManager().isInitAttempted()) this.checkSpawns();
                });
        }

        stage("checking regions", () -> this.scriptManager.checkRegions());

        if (challenge != null) stage("the challenge timer", () -> challenge.onCheckTimeOut());

        var sceneTime = getSceneTimeSeconds();
        Set<EntityAvatar> tickedAvatars = Collections.newSetFromMap(new IdentityHashMap<>());

        // getEntities() is already concurrent, so it is iterated in place rather than copied
        // wholesale every tick - at a 200ms tick that copy was the scene's largest allocation.
        this.getEntities()
                .forEach(
                        (eid, e) ->
                                stage(
                                        "an entity's tick",
                                        () -> {
                                            if (e instanceof EntityAvatar avatar) tickedAvatars.add(avatar);
                                            if (!e.isAlive()) {
                                                if (this.getEntities().remove(eid, e)) {
                                                    e.invalidateAbilityLifecycle();
                                                    this.sealBattleManager.onEntityRemoved(e);
                                                }
                                            }
                                            else e.onTick(sceneTime);
                                        }));

        for (var player : getPlayers()) {
            if (player.getScene() != this) continue;
            for (var avatar : player.getTeamManager().getActiveTeamSnapshot()) {
                if (tickedAvatars.contains(avatar) || avatar.getScene() != this
                        || getEntities().get(avatar.getId()) != null) continue;
                stage("an offstage avatar's abilities", () -> {
                    if (!avatar.isAlive()) avatar.invalidateAbilityLifecycle();
                    else if (avatar.isInSceneParty()) avatar.onAbilityTick();
                });
            }
        }

        stage("the blossoms", () -> blossomManager.onTick());

        stage(
                "the tower",
                () -> {
                    // Ticked with nobody here this used to walk off the end of an empty list.
                    var host = this.players.isEmpty() ? null : this.players.get(0);
                    var towerManager = host != null ? host.getTowerManager() : null;
                    if (towerManager != null && towerManager.isInProgress()) towerManager.onTick();
                });

        stage("the npc groups", this::checkNpcGroup);
        stage("finishing loading", this::finishLoading);
        stage("respawning players", this::checkPlayerRespawn);

        this.tickCount++;
        var now = System.currentTimeMillis();
        if (now - this.lastTimeNotify >= 10_000L) {
            this.lastTimeNotify = now;
            stage("the time notify", () -> this.broadcastPacket(new PacketSceneTimeNotify(this)));
        }
    }

    /** Stages of a tick already reported as failing, so the log says each one once. */
    private final Set<String> reportedStages = ConcurrentHashMap.newKeySet();

    /**
     * Runs one stage of the tick, keeping a failure in it from stopping the others.
     *
     * <p>Reported once per stage per scene: a scene that throws does it every second, and the log is
     * more useful with one copy of the reason than three thousand.
     */
    private void stage(String name, Runnable body) {
        try {
            body.run();
        } catch (Throwable e) {
            if (this.reportedStages.add(name)) {
                Grasscutter.getLogger()
                        .error("Scene {} threw during {}; the rest of the tick still ran.", this.getId(), name, e);
            } else {
                Grasscutter.getLogger().debug("Scene {} threw during {} again.", this.getId(), name, e);
            }
        }
    }

    protected void checkPlayerRespawn() {
        if (this.getScriptManager().getConfig() == null) return;
        var diePos = this.getScriptManager().getConfig().die_y;

        this.players.forEach(
                player -> {
                    if (this.getScriptManager().getConfig() == null) return;

                    if (diePos >= player.getPosition().getY()) {

                        this.respawnPlayer(player);
                    }
                });

        this.getEntities()
                .forEach(
                        (id, entity) -> {
                            if (diePos >= entity.getPosition().getY()) {
                                this.killEntity(entity);
                            }
                        });
    }

    public Position getDefaultLocation(Player player) {
        val defaultPosition = getScriptManager().getConfig().born_pos;
        return defaultPosition != null ? defaultPosition : player.getPosition();
    }

    public Position getDefaultRotation(Player player) {
        var defaultRotation = this.getScriptManager().getConfig().born_rot;
        return defaultRotation != null ? defaultRotation : player.getRotation();
    }

    private Position getRespawnLocation(Player player) {

        var lastCheckpointPos = dungeonManager != null ? dungeonManager.getRespawnLocation() : null;
        return lastCheckpointPos != null ? lastCheckpointPos : getDefaultLocation(player);
    }

    private Position getRespawnRotation(Player player) {
        var lastCheckpointRot =
                this.dungeonManager != null ? this.dungeonManager.getRespawnRotation() : null;
        return lastCheckpointRot != null ? lastCheckpointRot : this.getDefaultRotation(player);
    }

    public boolean respawnPlayer(Player player) {

        player.getTeamManager().applyVoidDamage();

        var targetPos = getRespawnLocation(player);
        var targetRot = getRespawnRotation(player);
        var teleportProps =
                TeleportProperties.builder()
                        .sceneId(getId())
                        .teleportTo(targetPos)
                        .teleportRot(targetRot)
                        .teleportType(PlayerTeleportEvent.TeleportType.INTERNAL)
                        .enterType(EnterTypeOuterClass.EnterType.EnterType_ENTER_GOTO)
                        .enterReason(
                                dungeonManager != null ? EnterReason.DungeonReviveOnWaypoint : EnterReason.Revival);

        return this.getWorld().transferPlayerToScene(player, teleportProps.build());
    }

    public void finishLoading() {
        final List<Runnable> ready;
        synchronized (afterLoadedCallbacks) {
            if (finishedLoading) return;
            finishedLoading = true;
            ready = new ArrayList<>(afterLoadedCallbacks);
            afterLoadedCallbacks.clear();
        }
        ready.forEach(this::invokeSceneCallback);
    }

    public void runWhenFinished(Runnable runnable) {
        if (runnable == null) return;
        synchronized (afterLoadedCallbacks) {
            if (!finishedLoading) {
                afterLoadedCallbacks.add(runnable);
                return;
            }
        }
        invokeSceneCallback(runnable);
    }

    private void invokeSceneCallback(Runnable callback) {
        try {
            callback.run();
        } catch (RuntimeException error) {
            Grasscutter.getLogger().error("Scene {} callback failed", getId(), error);
        }
    }

    public void playerSceneInitialized(Player player) {
        this.scenePlayBattleManager.sendTo(player);
        if (!player.equals(this.getHost())) return;

        Runnable callback;
        while ((callback = this.afterHostInitCallbacks.poll()) != null) callback.run();
    }

    public void runWhenHostInitialized(Runnable runnable) {
        if (this.isFinishedLoading()) {
            runnable.run();
            return;
        }

        this.afterHostInitCallbacks.add(runnable);
        // Initialization can finish between the first check and the enqueue.
        if (this.isFinishedLoading() && this.afterHostInitCallbacks.remove(runnable)) runnable.run();
    }

    public int getEntityLevel(int baseLevel, int worldLevelOverride) {
        int level = worldLevelOverride > 0 ? worldLevelOverride + baseLevel - 22 : baseLevel;
        level = Math.min(level, 100);
        level = level <= 0 ? 1 : level;

        return level;
    }

    public int getLevelForMonster(int configId, int defaultLevel) {
        if (getDungeonManager() != null) {
            return getDungeonManager().getLevelForMonster(configId);
        } else if (getWorld().getWorldLevel() > 0) {
            var worldLevelData = GameData.getWorldLevelDataMap().get(getWorld().getWorldLevel());

            if (worldLevelData != null) {
                return worldLevelData.getMonsterLevel();
            }
        }
        return defaultLevel;
    }

    public void checkNpcGroup() {
        Set<SceneNpcBornEntry> npcBornEntries = ConcurrentHashMap.newKeySet();
        for (Player player : this.getPlayers()) {
            npcBornEntries.addAll(loadNpcForPlayer(player));
        }

        this.npcBornEntrySet = npcBornEntries;
    }

    public void checkSpawns() {
        Set<SpawnDataEntry.GridBlockId> loadedGridBlocks = new HashSet<>();
        for (Player player : this.getPlayers()) {
            Collections.addAll(
                    loadedGridBlocks,
                    SpawnDataEntry.GridBlockId.getAdjacentGridBlockIds(
                            player.getSceneId(), player.getPosition()));
        }

        Set<SpawnDataEntry.GridBlockId> leavingBlocks = new HashSet<>(this.loadedGridBlocks);
        leavingBlocks.removeAll(loadedGridBlocks);
        if (!leavingBlocks.isEmpty()) {
            this.getDeadSpawnedEntities().removeIf(entry -> leavingBlocks.contains(entry.getBlockId()));
        }
        if (this.loadedGridBlocks.containsAll(
                loadedGridBlocks)) {
            return;
        }
        this.loadedGridBlocks = loadedGridBlocks;
        var spawnLists = GameDepot.getSpawnLists();
        Set<SpawnDataEntry> visible = new HashSet<>();
        for (var block : loadedGridBlocks) {
            var spawns = spawnLists.get(block);
            if (spawns != null) {
                visible.addAll(spawns);
            }
        }

        WorldLevelData worldLevelData = GameData.getWorldLevelDataMap().get(getWorld().getWorldLevel());
        int worldLevelOverride = 0;

        if (worldLevelData != null) {
            worldLevelOverride = worldLevelData.getMonsterLevel();
        }

        List<GameEntity> toAdd = new ArrayList<>();
        List<GameEntity> toRemove = new ArrayList<>();
        var spawnedEntities = this.getSpawnedEntities();
        for (SpawnDataEntry entry : visible) {

            if (!spawnedEntities.contains(entry) && !this.getDeadSpawnedEntities().contains(entry)) {

                GameEntity entity = null;

                if (entry.getMonsterId() > 0) {
                    MonsterData data = GameData.getMonsterDataMap().get(entry.getMonsterId());
                    if (data == null) continue;

                    int level = this.getEntityLevel(entry.getLevel(), worldLevelOverride);

                    EntityMonster monster =
                            new EntityMonster(this, data, entry.getPos(), entry.getRot(), level);
                    monster.setGroupId(entry.getGroup().getGroupId());
                    monster.setPoseId(entry.getPoseId());
                    monster.setConfigId(entry.getConfigId());
                    monster.setSpawnEntry(entry);

                    entity = monster;
                } else if (entry.getGadgetId() > 0) {
                    EntityGadget gadget =
                            new EntityGadget(this, entry.getGadgetId(), entry.getPos(), entry.getRot());
                    gadget.setGroupId(entry.getGroup().getGroupId());
                    gadget.setConfigId(entry.getConfigId());
                    gadget.setSpawnEntry(entry);
                    int state = entry.getGadgetState();
                    if (state > 0) {
                        gadget.setState(state);
                    }
                    gadget.buildContent();

                    gadget.setFightProperty(FightProperty.FIGHT_PROP_BASE_HP, Float.POSITIVE_INFINITY);
                    gadget.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, Float.POSITIVE_INFINITY);
                    gadget.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, Float.POSITIVE_INFINITY);

                    entity = gadget;
                    blossomManager.initBlossom(gadget);
                }

                if (entity == null) continue;

                toAdd.add(entity);
                spawnedEntities.add(entry);
            }
        }

        for (GameEntity entity : this.getEntities().values()) {
            var spawnEntry = entity.getSpawnEntry();
            if (spawnEntry != null
                    && !(entity instanceof EntityWeapon)
                    && !visible.contains(spawnEntry)) {
                toRemove.add(entity);
                spawnedEntities.remove(spawnEntry);
            }
        }

        if (toAdd.size() > 0) {
            toAdd.forEach(this::addEntityDirectly);
            this.broadcastPacket(new PacketSceneEntityAppearNotify(toAdd, VisionType.VisionType_VISION_BORN));
        }

        if (toRemove.size() > 0) {
            toRemove.forEach(this::removeEntityDirectly);
            this.broadcastPacket(
                    new PacketSceneEntityDisappearNotify(toRemove, VisionType.VisionType_VISION_REMOVE));
            blossomManager.recycleGadgetEntity(toRemove);
        }
    }

    public List<SceneBlock> getPlayerActiveBlocks(Player player) {

        return SceneIndexManager.queryNeighbors(
                getScriptManager().getBlocksIndex(),
                player.getPosition().toXZDoubleArray(),
                Grasscutter.getConfig().server.game.loadEntitiesForPlayerRange);
    }

    public Set<Integer> getPlayerActiveGroups(Player player) {

        Position playerPosition = player.getPosition();
        Set<Integer> activeGroups = new HashSet<>();
        for (int i = 0; i < 4; i++) {
            Grid grid = getScriptManager().getGroupGrids().get(i);

            activeGroups.addAll(grid.getNearbyGroups(i, playerPosition));
        }

        return activeGroups;
    }

    public boolean loadBlock(SceneBlock block) {
        if (this.loadedBlocks.contains(block)) return false;

        this.onLoadBlock(block, this.players);
        this.loadedBlocks.add(block);
        return true;
    }

    public void checkGroups() {
        Set<Integer> visible =
                this.players.stream()
                        .map(this::getPlayerActiveGroups)
                        .flatMap(Collection::stream)
                        .filter(id -> !crucibleSceneController.owns(id))
                        .filter(id -> !asterMidSceneController.owns(id))
                        .filter(id -> !asterBigSceneController.owns(id))
                        .collect(Collectors.toSet());

        for (var group : this.loadedGroups) {
            if (!visible.contains(group.id) && !group.dynamic_load && !group.dontUnload
                    && !crucibleSceneController.owns(group.id))
                unloadGroup(scriptManager.getBlocks().get(group.block_id), group.id);
        }

        var toLoad =
                visible.stream()
                        .filter(g -> this.loadedGroups.stream().noneMatch(gr -> gr.id == g))
                        .map(
                                g -> {
                                    for (var b : scriptManager.getBlocks().values()) {
                                        loadBlock(b);
                                        SceneGroup group = b.groups.getOrDefault(g, null);
                                        if (group != null && !group.dynamic_load) return group;
                                    }

                                    return null;
                                })
                        .filter(Objects::nonNull)
                        .toList();

        this.onLoadGroup(toLoad);
        if (!toLoad.isEmpty()) this.onRegisterGroups();
    }

    public void onLoadBlock(SceneBlock block, List<Player> players) {
        this.getScriptManager().loadBlockFromScript(block);
        scriptManager.getLoadedGroupSetPerBlock().computeIfAbsent(block.id, id -> ConcurrentHashMap.newKeySet());

        Grasscutter.getLogger().trace("Scene {} block {} loaded.", this.getId(), block.id);
    }

    public int loadDynamicGroup(int group_id) {
        SceneGroup group = getScriptManager().getGroupById(group_id);
        if (group == null) return -1;

        this.onLoadGroup(new ArrayList<>(List.of(group)));

        if (GameData.getGroupReplacements().containsKey(group_id)) onRegisterGroups();

        if (group.init_config == null) return -1;
        return group.init_config.suite;
    }

    public boolean unregisterDynamicGroup(int groupId) {
        var group = getScriptManager().getGroupById(groupId);
        if (group == null) return false;

        var block = getScriptManager().getBlocks().get(group.block_id);
        this.unloadGroup(block, groupId);
        return true;
    }

    public void onRegisterGroups() {
        var sceneGroups = this.loadedGroups;
        var sceneGroupMap =
                sceneGroups.stream().collect(Collectors.toMap(item -> item.id, item -> item));
        var sceneGroupsIds = sceneGroups.stream().map(group -> group.id).toList();
        var dynamicGroups =
                sceneGroups.stream().filter(group -> group.dynamic_load).map(group -> group.id).toList();

        var nodes = new ArrayList<KahnsSort.Node>();
        var groupList = new ArrayList<Integer>();
        GameData.getGroupReplacements().values().stream()
                .filter(replacement -> dynamicGroups.contains(replacement.id))
                .forEach(
                        replacement -> {
                            Grasscutter.getLogger().debug("Graph ordering replacement {}", replacement);
                            replacement.replace_groups.forEach(
                                    group -> {
                                        nodes.add(new KahnsSort.Node(replacement.id, group));
                                        if (!groupList.contains(group)) groupList.add(group);
                                    });

                            if (!groupList.contains(replacement.id)) groupList.add(replacement.id);
                        });

        KahnsSort.Graph graph = new KahnsSort.Graph(nodes, groupList);
        List<Integer> dynamicGroupsOrdered = KahnsSort.doSort(graph);

        dynamicGroupsOrdered.forEach(
                group -> {
                    if (GameData.getGroupReplacements().containsKey((int) group)) {
                        var data = GameData.getGroupReplacements().get((int) group);
                        var sceneGroupReplacement =
                                this.loadedGroups.stream().filter(g -> g.id == group).findFirst().orElseThrow();
                        if (sceneGroupReplacement.is_replaceable != null) {
                            var it = data.replace_groups.iterator();
                            while (it.hasNext()) {
                                var replace_group = it.next();
                                if (!sceneGroupsIds.contains(replace_group)) continue;

                                SceneGroup sceneGroup = sceneGroupMap.get(replace_group);
                                if (sceneGroup != null
                                        && sceneGroup.is_replaceable != null
                                        && ((sceneGroup.is_replaceable.value
                                                        && sceneGroup.is_replaceable.version
                                                                <= sceneGroupReplacement.is_replaceable.version)
                                                || sceneGroup.is_replaceable.new_bin_only)) {
                                    this.unloadGroup(
                                            scriptManager.getBlocks().get(sceneGroup.block_id), replace_group);
                                    it.remove();
                                    Grasscutter.getLogger().debug("Graph ordering: unloaded {}", replace_group);
                                }
                            }
                        }
                    }
                });
    }

    public void loadTriggerFromGroup(SceneGroup group, String triggerName) {

        this.getScriptManager()
                .registerTrigger(
                        group.triggers.values().stream()
                                .filter(p -> p.getName().contains(triggerName))
                                .toList());
        group.regions.values().stream()
                .filter(q -> q.config_id == Integer.parseInt(triggerName.substring(13)))
                .map(region -> new EntityRegion(this, region))
                .forEach(getScriptManager()::registerRegion);
    }

    public synchronized void onLoadGroup(List<SceneGroup> groups) {
        if (groups == null || groups.isEmpty()) {
            return;
        }

        for (var group : groups) {
            if (!crucibleSceneController.allows(group.id)) continue;
            if (!asterBigSceneController.allows(group.id)) continue;
            if (this.loadedGroups.contains(group)) continue;

            this.getScriptManager().loadGroupFromScript(group);
            if (!this.scriptManager.getLoadedGroupSetPerBlock().containsKey(group.block_id))
                this.onLoadBlock(scriptManager.getBlocks().get(group.block_id), players);
            this.scriptManager.getLoadedGroupSetPerBlock().get(group.block_id).add(group);
        }

        var entities = new ArrayList<GameEntity>();
        for (var group : groups) {
            if (!crucibleSceneController.allows(group.id)) continue;
            if (!asterBigSceneController.allows(group.id)) continue;
            if (this.loadedGroups.contains(group)) continue;

            if (group.init_config == null) {
                continue;
            }

            var groupInstance = this.getScriptManager().getGroupInstanceById(group.id);
            var cachedInstance = this.getScriptManager().getCachedGroupInstanceById(group.id);
            if (cachedInstance != null) {
                cachedInstance.setLuaGroup(group);
                groupInstance = cachedInstance;
            }

            this.getScriptManager()
                    .refreshGroup(groupInstance, 0, false);

            this.loadedGroups.add(group);
        }

        this.scriptManager.meetEntities(entities);
        groups.forEach(
                g -> scriptManager.callEvent(new ScriptArgs(g.id, EventType.EVENT_GROUP_LOAD, g.id)));

        Grasscutter.getLogger().trace("Scene {} loaded {} group(s)", this.getId(), groups.size());
    }

    public synchronized void unloadGroup(SceneBlock block, int group_id) {
        var group = loadedGroups.stream().filter(item -> item.id == group_id).findFirst().orElse(null);
        if (group == null && block != null && block.groups != null) group = block.groups.get(group_id);
        if (group != null) unloadGroup(block, group);
    }

    public synchronized void unloadGroup(SceneBlock block, SceneGroup group) {
        int group_id = group.id;
        // Callers resolve the block via getBlocks().get(...), which yields null for a group whose
        // block was never loaded (dynamic groups in particular). Nothing below can run without it.
        if (block == null) {
            Grasscutter.getLogger()
                    .debug("unloadGroup: no block for group {} in scene {}", group_id, this.getId());
            return;
        }

        List<GameEntity> toRemove =
                this.getEntities().values().stream()
                        .filter(e -> e != null && (e.getBlockId() == block.id && e.getGroupId() == group_id))
                        .toList();

        if (toRemove.size() > 0) {
            toRemove.forEach(this::removeEntityDirectly);
            this.broadcastPacket(
                    new PacketSceneEntityDisappearNotify(toRemove, VisionType.VisionType_VISION_REMOVE));
        }

        getScriptManager().cancelGroupTimers(group_id);
        if (group.triggers != null) {
            group.triggers.values().forEach(getScriptManager()::deregisterTrigger);
        }
        if (group.regions != null) {
            group.regions.values().forEach(getScriptManager()::deregisterRegion);
        }
        if (challenge != null && group.id == challenge.getGroup().id) {
            challenge.fail();
        }

        // The per-block set is dropped from the map once it empties (below), so a later unload
        // targeting the same block finds no entry at all.
        var blockGroups = scriptManager.getLoadedGroupSetPerBlock().get(block.id);
        if (blockGroups != null) {
            blockGroups.remove(group);
            if (blockGroups.isEmpty()) {
                this.scriptManager.getLoadedGroupSetPerBlock().remove(block.id);
                Grasscutter.getLogger().trace("Scene {} block {} is unloaded.", this.getId(), block.id);
            }
        }
        this.loadedGroups.remove(group);

        this.broadcastPacket(new PacketGroupUnloadNotify(List.of(group_id)));
        this.scriptManager.unregisterGroup(group);
    }

    public synchronized void onPlayerCreateGadget(EntityClientGadget gadget) {
        var owner = gadget.getOwner();

        this.addEntityDirectly(gadget);
        owner.getTeamManager().getGadgets().add(gadget);
        if (dungeonBossCleanup.removeLateGadget(gadget)) return;

        for (var player : this.getPlayers()) {
            if (player != owner) {
                player.getSession().send(new PacketSceneEntityAppearNotify(gadget));
            }
        }
    }

    public void onPlayerDestroyGadget(int entityId) {
        GameEntity entity = getEntities().get(entityId);

        if (!(entity instanceof EntityClientGadget gadget)) {
            for (var player : this.getPlayers()) {
                player.getTeamManager().getGadgets().removeIf(g -> g.getId() == entityId);
            }
            return;
        }

        this.removeEntityDirectly(gadget);

        var owner = gadget.getOwner();
        owner.getTeamManager().getGadgets().remove(gadget);

        this.broadcastPacket(
                new PacketSceneEntityDisappearNotify(gadget, VisionType.VisionType_VISION_DIE));
    }

    public void broadcastPacket(BasePacket packet) {

        for (Player player : this.getPlayers()) {
            player.getSession().send(packet);
        }
    }

    public void broadcastPacketToOthers(Player excludedPlayer, BasePacket packet) {

        if (this.getPlayerCount() == 1 && this.getPlayers().get(0) == excludedPlayer) {
            return;
        }

        for (Player player : this.getPlayers()) {
            if (player == excludedPlayer) {
                continue;
            }

            player.getSession().send(packet);
        }
    }

    public void addItemEntity(int itemId, int amount, GameEntity bornForm) {
        ItemData itemData = GameData.getItemDataMap().get(itemId);
        if (itemData == null) {
            return;
        }
        if (itemData.isEquip()) {
            float range = (1.5f + (.05f * amount));
            for (int i = 0; i < amount; i++) {
                Position pos = bornForm.getPosition().nearby2d(range).addZ(.9f);
                EntityItem entity = new EntityItem(this, null, itemData, pos, 1);
                addEntity(entity);
            }
        } else {
            EntityItem entity =
                    new EntityItem(
                            this, null, itemData, bornForm.getPosition().clone().addZ(.9f), amount);
            addEntity(entity);
        }
    }

    public void loadNpcForPlayerEnter(Player player) {
        this.salesmanSceneController.updatePlayer(player, System.currentTimeMillis());
        this.npcBornEntrySet.addAll(loadNpcForPlayer(player));
    }

    private List<SceneNpcBornEntry> loadNpcForPlayer(Player player) {
        var pos = player.getPosition();
        var data = GameData.getSceneNpcBornData().get(getId());
        if (data == null) {
            return List.of();
        }

        var npcList =
                SceneIndexManager.queryNeighbors(
                        data.getIndex(),
                        pos.toDoubleArray(),
                        Grasscutter.getConfig().server.game.loadEntitiesForPlayerRange);

        var sceneNpcBornCanidates =
                npcList.stream().filter(i -> i.getGroupId() != SalesmanNpcScene.GROUP_ID)
                        .filter(i -> !this.npcBornEntrySet.contains(i)).toList();

        List<SceneNpcBornEntry> sceneNpcBornEntries = new ArrayList<>();
        sceneNpcBornCanidates.forEach(
                i -> {
                    var groupInstance = scriptManager.getGroupInstanceById(i.getGroupId());
                    if (groupInstance == null) return;
                    if (i.getSuiteIdList() != null
                            && !i.getSuiteIdList().contains(groupInstance.getActiveSuiteId())) return;
                    sceneNpcBornEntries.add(i);
                });

        if (sceneNpcBornEntries.size() > 0) {
            this.broadcastPacket(new PacketGroupSuiteNotify(sceneNpcBornEntries));
            Grasscutter.getLogger().trace("Loaded Npc Group Suite {}", sceneNpcBornEntries);
        }

        return npcList.stream()
                .filter(i -> this.npcBornEntrySet.contains(i) || sceneNpcBornEntries.contains(i))
                .toList();
    }

    public void loadGroupForQuest(List<QuestGroupSuite> sceneGroupSuite) {
        if (!scriptManager.isInit()) {
            return;
        }

        sceneGroupSuite.forEach(
                i -> {
                    var group = scriptManager.getGroupById(i.getGroup());
                    if (group == null) return;

                    var groupInstance = scriptManager.getGroupInstanceById(i.getGroup());
                    var suite = group.getSuiteByIndex(i.getSuite());
                    if (suite == null || groupInstance == null) {
                        return;
                    }

                    scriptManager.refreshGroup(groupInstance, i.getSuite(), false);
                });
    }

    public void unlockForce(int force) {
        this.unlockedForces.add(force);
        this.broadcastPacket(new PacketSceneForceUnlockNotify(force, true));
    }

    public void lockForce(int force) {
        this.unlockedForces.remove(force);
        this.broadcastPacket(new PacketSceneForceLockNotify(force));
    }

    public void selectWorktopOptionWith(SelectWorktopOptionReqOuterClass.SelectWorktopOptionReq req) {
        GameEntity entity = getEntityById(req.getGadgetEntityId());
        if (entity == null) {
            return;
        }

        if (entity instanceof EntityGadget gadget) {
            if (gadget.getContent() instanceof GadgetWorktop worktop) {
                boolean shouldDelete = worktop.onSelectWorktopOption(req);
                if (shouldDelete) {
                    entity.getScene().removeEntity(entity, VisionType.VisionType_VISION_REMOVE);
                }
            }
        }
    }

    public void saveGroups() {
        this.getScriptManager().getCachedGroupInstances().values().forEach(SceneGroupInstance::save);
    }
}
