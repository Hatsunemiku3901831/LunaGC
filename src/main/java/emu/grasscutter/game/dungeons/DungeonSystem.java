package emu.grasscutter.game.dungeons;

import emu.grasscutter.*;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.DungeonDropCatalog;
import emu.grasscutter.data.DungeonDropReferenceCatalog;
import emu.grasscutter.data.binout.ScenePointEntry;
import emu.grasscutter.data.excels.dungeon.*;
import emu.grasscutter.game.dungeons.fallback.MissingDomainFallbackManager;
import emu.grasscutter.game.dungeons.handlers.DungeonBaseHandler;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.SceneType;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.game.*;
import emu.grasscutter.server.packet.send.PacketDungeonEntryInfoRsp;
import it.unimi.dsi.fastutil.ints.*;
import java.util.List;
import lombok.val;

public final class DungeonSystem extends BaseGameSystem {
    private static final BasicDungeonSettleListener basicDungeonSettleObserver =
            new BasicDungeonSettleListener();
    private final Int2ObjectMap<DungeonBaseHandler> passCondHandlers;

    public DungeonSystem(GameServer server) {
        super(server);

        this.passCondHandlers = new Int2ObjectOpenHashMap<>();
        try {
            DungeonDropCatalog.reload();
            var rewards = Grasscutter.getConfig().server.game.gameOptions.dungeonRewards;
            if (rewards != null && rewards.referenceDropTables) DungeonDropReferenceCatalog.reload();
        } catch (java.io.IOException invalid) {
            throw new IllegalStateException("Cannot load dungeon rewards", invalid);
        }
        this.registerHandlers();
    }

    public void registerHandlers() {
        this.registerHandlers(this.passCondHandlers, DungeonBaseHandler.class);
    }

    public <T> void registerHandlers(Int2ObjectMap<T> map, Class<T> clazz) {
        var handlerClasses = Grasscutter.reflector.getSubTypesOf(clazz);
        for (var obj : handlerClasses) {
            this.registerHandler(map, obj);
        }
    }

    public <T> void registerHandler(Int2ObjectMap<T> map, Class<? extends T> handlerClass) {
        try {
            DungeonValue opcode = handlerClass.getAnnotation(DungeonValue.class);

            if (opcode == null || opcode.value() == null) {
                return;
            }

            map.put(opcode.value().ordinal(), handlerClass.getDeclaredConstructor().newInstance());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Sends the entry info for the given dungeon point to the player.
     *
     * @param player The player to send the entry info to.
     * @param pointId The dungeon point ID.
     */
    public void sendEntryInfoFor(Player player, int pointId, int sceneId) {
        var entry = GameData.getScenePointEntryById(sceneId, pointId);
        if (entry == null) {
            // An invalid point ID was sent.
            player.sendPacket(new PacketDungeonEntryInfoRsp());
            return;
        }

        // Check if the player has quests with dungeon IDs.
        var questDungeons = player.getQuestManager().questsForDungeon(entry);
        if (questDungeons.size() > 0) {
            player.sendPacket(
                    new PacketDungeonEntryInfoRsp(entry.getPointData(), questDungeons, player));
        } else {
            player.sendPacket(new PacketDungeonEntryInfoRsp(entry.getPointData(), player));
        }
    }

    public boolean triggerCondition(
            DungeonPassConfigData.DungeonPassCondition condition, int... params) {
        var handler = passCondHandlers.get(condition.getCondType().ordinal());

        if (handler == null) {
            Grasscutter.getLogger()
                    .debug("Could not trigger condition {} at {}", condition.getCondType(), params);
            return false;
        }

        return handler.execute(condition, params);
    }

    public boolean enterDungeon(Player player, int pointId, int dungeonId, boolean savePrevious) {
        if (player == null) {
            return false;
        }
        synchronized (player.getSceneTransferLock()) {
            var world = player.getWorld();
            var source = player.getScene();
            DungeonData data = GameData.getDungeonDataMap().get(dungeonId);

            if (world == null || source == null || data == null) {
                return false;
            }
            Grasscutter.getLogger()
                    .debug(
                            "{} ({}) is trying to enter dungeon {}.",
                            player.getNickname(),
                            player.getUid(),
                            dungeonId);

            var sceneId = data.getSceneId();
            var origin = DungeonReturnLocation.capture(source, player.getPosition());
            if (!world.transferPlayerToScene(player, sceneId, data)) {
                return false;
            }
            var scene = player.getScene();
            scene.setDungeonManager(new DungeonManager(scene, data));
            MissingDomainFallbackManager.install(scene, data);
            scene.addDungeonSettleObserver(basicDungeonSettleObserver);
            if (savePrevious) {
                scene.setPrevScene(source.getId());
                scene.setPrevScenePoint(pointId);
            }
            player.rememberDungeonReturnLocation(origin);
            return true;
        }
    }

    /** used in tower dungeons handoff */
    public boolean handoffDungeon(
            Player player, int dungeonId, List<DungeonSettleListener> dungeonSettleListeners) {
        if (player == null) {
            return false;
        }
        synchronized (player.getSceneTransferLock()) {
            var world = player.getWorld();
            var source = player.getScene();
            DungeonData data = GameData.getDungeonDataMap().get(dungeonId);

            if (world == null
                    || source == null
                    || data == null
                    || dungeonSettleListeners == null
                    || dungeonSettleListeners.stream().anyMatch(java.util.Objects::isNull)) {
                return false;
            }
            var observers = List.copyOf(dungeonSettleListeners);
            int previousScene =
                    source.getSceneType() == SceneType.SCENE_DUNGEON
                            ? source.getPrevScene()
                            : source.getId();
            int previousPoint = source.getPrevScenePoint();
            Grasscutter.getLogger()
                    .info(
                            "{}({}) is trying to enter tower dungeon {}",
                            player.getNickname(),
                            player.getUid(),
                            dungeonId);

            return world.handoffTowerDungeonScene(
                    player,
                    data,
                    scene -> {
                        var dungeonManager = new DungeonManager(scene, data);
                        dungeonManager.configureTower(player.getTowerManager().getBattleProfile(),
                                player.getTowerManager().getCurrentFloorNumber(),
                                player.getTowerManager().getCurrentMonsterLevel());
                        dungeonManager.setTowerDungeon(true);
                        scene.setDungeonManager(dungeonManager);
                        scene.setPrevScene(previousScene);
                        scene.setPrevScenePoint(previousPoint);
                        observers.forEach(scene::addDungeonSettleObserver);
                    });
        }
    }

    public void exitDungeon(Player player) {
        Scene scene = player.getScene();

        if (scene == null || scene.getSceneType() != SceneType.SCENE_DUNGEON) {
            return;
        }

        // Get previous scene
        int prevScene = scene.getPrevScene() > 0 ? scene.getPrevScene() : 3;

        // Get previous position
        val dungeonManager = scene.getDungeonManager();
        if (dungeonManager != null && dungeonManager.hasDispatchedTowerExit()) return;
        DungeonData dungeonData = dungeonManager != null ? dungeonManager.getDungeonData() : null;
        Position prevPos = new Position(GameConstants.START_POSITION);

        if (dungeonData != null) {
            ScenePointEntry entry =
                    GameData.getScenePointEntryById(prevScene, scene.getPrevScenePoint());

            if (entry != null) {
                prevPos.set(entry.getPointData().getTranPos());
            }
        }
        var returnPos = new Position(prevPos);
        Runnable exit =
                () -> finishExitDungeon(player, scene, dungeonManager, prevScene, returnPos);
        if (dungeonManager != null && dungeonManager.isTowerDungeon()) {
            dungeonManager.quitDungeon(exit);
            return;
        }
        if (dungeonManager != null && !dungeonManager.isFinishedSuccessfully())
            dungeonManager.quitDungeon();
        exit.run();
    }

    private void finishExitDungeon(
            Player player,
            Scene scene,
            DungeonManager dungeonManager,
            int prevScene,
            Position prevPos) {
        synchronized (player.getSceneTransferLock()) {
            synchronized (scene) {
                if (player.getScene() != scene
                        || player.getWorld() != scene.getWorld()
                        || scene.getWorld().getScenes().get(scene.getId()) != scene
                        || scene.getScriptManager().isDestroyed()
                        || scene.getDungeonManager() != dungeonManager) return;
                if (dungeonManager != null) dungeonManager.unsetTrialTeam(player);
                // clean temp team if it has
                if (!player.getTeamManager().cleanTemporaryTeam()) {
                    // no temp team. Will use real current team, but check
                    // for any dead avatar to prevent switching into them.
                    player.getTeamManager().checkCurrentAvatarIsAlive(null);
                }
                player.getTowerManager().clearEntry();
                if (dungeonManager != null) {
                    dungeonManager.setTowerDungeon(false);
                }

                // Transfer player back to world after a small delay.
                // This wait is important for avoiding double teleports,
                // which specifically happen when player quits a dungeon
                // by teleporting to map waypoints.
                // From testing, 200ms seem reasonable.
                player.getWorld().queueTransferPlayerToScene(player, prevScene, prevPos, 200);
            }
        }
    }

    public void restartDungeon(Player player) {
        tryRestartDungeon(player);
    }

    public boolean tryRestartDungeon(Player player) {
        if (player == null) return false;
        synchronized (player.getSceneTransferLock()) {
            var scene = player.getScene();
            var world = player.getWorld();
            if (scene == null || world == null) return false;
            var dungeonManager = scene.getDungeonManager();
            if (dungeonManager == null || dungeonManager.getDungeonData() == null) return false;
            var dungeonData = dungeonManager.getDungeonData();
            var isTower = dungeonManager.isTowerDungeon();
            var prevScene = scene.getPrevScene();
            var pointId = scene.getPrevScenePoint();
            java.util.function.BooleanSupplier restart =
                    () ->
                            world.restartDungeonScene(
                                    player,
                                    dungeonData,
                                    fresh -> {
                                        var newManager = new DungeonManager(fresh, dungeonData);
                                        if (isTower) newManager.configureTower(dungeonManager.getTowerBattleProfile(),
                                                dungeonManager.getTowerFloorNumber(), dungeonManager.getTowerBaseMonsterLevel());
                                        newManager.setTowerDungeon(isTower);
                                        fresh.setDungeonManager(newManager);
                                        fresh.setPrevScene(prevScene);
                                        fresh.setPrevScenePoint(pointId);
                                        if (isTower) {
                                            fresh.addDungeonSettleObserver(
                                                    new TowerDungeonSettleListener());
                                        } else {
                                            MissingDomainFallbackManager.install(
                                                    fresh, dungeonData);
                                            fresh.addDungeonSettleObserver(
                                                    basicDungeonSettleObserver);
                                        }
                                    },
                                    () ->
                                            !isTower
                                                    || player.getTowerManager()
                                                            .canRestartChamber(dungeonData));
            return isTower
                    ? player.getTowerManager().tryRestartChamber(dungeonData, restart)
                    : restart.getAsBoolean();
        }
    }
}
