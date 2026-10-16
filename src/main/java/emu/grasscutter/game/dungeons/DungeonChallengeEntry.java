package emu.grasscutter.game.dungeons;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.excels.dungeon.DungeonData;
import emu.grasscutter.game.dungeons.challenge.WorldChallenge;
import emu.grasscutter.game.dungeons.enums.*;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.gadget.GadgetWorktop;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.SceneType;
import emu.grasscutter.game.world.*;
import emu.grasscutter.scripts.constants.ScriptGadgetState;
import emu.grasscutter.scripts.data.*;
import emu.grasscutter.server.packet.send.PacketWorktopOptionNotify;

import java.util.*;
import java.util.function.LongSupplier;

/** A repeatable domain's initial starter, spawn and per-scene selection lifetime. */
public final class DungeonChallengeEntry {
    private static final Set<DungeonSubType> REPEATABLE =
            Set.of(
                    DungeonSubType.DUNGEON_SUB_TALENT,
                    DungeonSubType.DUNGEON_SUB_WEAPON,
                    DungeonSubType.DUNGEON_SUB_RELIQUARY);

    public record Spawn(
            int dungeonId,
            int sceneId,
            int groupId,
            int configId,
            int gadgetId,
            Position position,
            Position rotation) {
        public Spawn {
            position = new Position(position);
            rotation = new Position(rotation);
        }

        @Override
        public Position position() {
            return new Position(position);
        }

        @Override
        public Position rotation() {
            return new Position(rotation);
        }
    }

    private final Scene scene;
    private final LongSupplier clock;
    private volatile Spawn spawn;
    private DungeonManager manager;
    private EntityGadget selected;
    private WorldChallenge challengeAtSelection;
    private long selectedLifetime, hideAt, challengeSerialAtSelection;
    private boolean started, hidden;

    public DungeonChallengeEntry(Scene scene) {
        this(scene, System::nanoTime);
    }

    public DungeonChallengeEntry(Scene scene, LongSupplier clock) {
        this.scene = Objects.requireNonNull(scene);
        this.clock = Objects.requireNonNull(clock);
    }

    public static boolean eligible(DungeonData data) {
        return data != null
                && data.getId() > 0
                && data.getSceneId() > 0
                && data.getType() == DungeonType.DUNGEON_DAILY_FIGHT
                && REPEATABLE.contains(data.getSubType());
    }

    public static Spawn resolve(DungeonData data, Position birth, Position rotation) {
        if (!eligible(data) || !finite(birth) || !finite(rotation)) return null;
        try {
            return resolve(data, SceneMeta.of(data.getSceneId()), birth, rotation);
        } catch (RuntimeException error) {
            Grasscutter.getLogger()
                    .debug(
                            "Domain starter metadata unavailable for scene {}",
                            data.getSceneId(),
                            error);
            return null;
        }
    }

    public static Spawn resolve(
            DungeonData data, SceneMeta meta, Position birth, Position rotation) {
        if (!eligible(data)
                || meta == null
                || meta.blocks == null
                || !finite(birth)
                || !finite(rotation)) return null;
        record Key(int group, SceneGadget gadget) {}
        var keys = new ArrayList<Key>();
        for (var block : new TreeMap<>(meta.blocks).values()) {
            if (block == null) continue;
            if (!block.isLoaded()) block.load(data.getSceneId(), meta.context);
            if (block.groups == null) continue;
            for (var group : new TreeMap<>(block.groups).values()) {
                if (group == null || group.dynamic_load) continue;
                group.load(data.getSceneId());
                if (group.gadgets == null || group.suites == null || group.suites.isEmpty())
                    continue;
                var init = group.init_config;
                if (init != null && init.rand_suite && init.io_type != 1 && group.suites.size() > 1)
                    continue;
                int index = init == null ? 1 : init.suite;
                if (index < 1 || index > group.suites.size()) continue;
                var suite = group.getSuiteByIndex(index);
                if (suite == null || suite.gadgets == null) continue;
                for (int id : suite.gadgets) {
                    var gadget = group.gadgets.get(id);
                    if (gadget != null
                            && (gadget.gadget_id == 70360010 || gadget.gadget_id == 73051002)
                            && finite(gadget.pos)) keys.add(new Key(group.id, gadget));
                }
            }
        }
        var key =
                keys.stream()
                        .min(
                                Comparator.<Key>comparingDouble(k -> distance(k.gadget.pos, birth))
                                        .thenComparingInt(Key::group)
                                        .thenComparingInt(k -> k.gadget.config_id))
                        .orElse(null);
        if (key == null) return null;
        var position = key.gadget.pos;
        double dx = (double) birth.getX() - position.getX(),
                dz = (double) birth.getZ() - position.getZ();
        double length = Math.hypot(dx, dz);
        Position to = new Position(birth), facing = new Position(rotation);
        if (length > 0.00001) {
            double step = Math.min(2.2, length);
            to =
                    new Position(
                            (float) (position.getX() + dx / length * step),
                            position.getY(),
                            (float) (position.getZ() + dz / length * step));
            facing =
                    new Position(
                            0,
                            (float)
                                    Math.toDegrees(
                                            Math.atan2(
                                                    (double) position.getX() - to.getX(),
                                                    (double) position.getZ() - to.getZ())),
                            0);
        }
        return new Spawn(
                data.getId(),
                data.getSceneId(),
                key.group,
                key.gadget.config_id,
                key.gadget.gadget_id,
                to,
                facing);
    }

    private static boolean finite(Position value) {
        return value != null
                && Float.isFinite(value.getX())
                && Float.isFinite(value.getY())
                && Float.isFinite(value.getZ());
    }

    private static double distance(Position first, Position second) {
        return Math.hypot(
                (double) first.getX() - second.getX(), (double) first.getZ() - second.getZ());
    }

    public void install(Spawn spawn) {
        if (spawn == null || spawn.sceneId != scene.getId()) return;
        synchronized (scene) {
            if (this.spawn != null) return;
            this.spawn = spawn;
            armExisting();
        }
    }

    private boolean current() {
        if (spawn == null
                || scene.getSceneType() != SceneType.SCENE_DUNGEON
                || scene.getScriptManager().isDestroyed()
                || scene.getWorld().getScenes().get(scene.getId()) != scene) return false;
        var current = scene.getDungeonManager();
        if (current == null
                || current.isTowerDungeon()
                || !eligible(current.getDungeonData())
                || current.getDungeonData().getId() != spawn.dungeonId
                || current.getDungeonData().getSceneId() != scene.getId()) return false;
        if (manager == null) manager = current;
        return manager == current;
    }

    private boolean owns(EntityGadget gadget) {
        return spawn != null
                && gadget.getScene() == scene
                && gadget.getGroupId() == spawn.groupId
                && gadget.getConfigId() == spawn.configId
                && gadget.getGadgetId() == spawn.gadgetId;
    }

    public void armExisting() {
        if (spawn == null) return;
        synchronized (scene) {
            for (var entity : scene.getEntities().values())
                if (entity instanceof EntityGadget gadget) tick(gadget);
        }
    }

    private boolean failedAfterSelection() {
        var challenge = scene.getChallenge();
        return started
                && challenge != null
                && challenge.getStartSerial() > 0
                && !challenge.inProgress()
                && !challenge.isSuccess()
                && (challenge != challengeAtSelection
                        || challenge.getStartSerial() != challengeSerialAtSelection);
    }

    private void acceptScriptReset(EntityGadget gadget) {
        // The resource's failure trigger owns the option/state reset. A stale failure from
        // before this selection must not reopen the next pending start.
        if (failedAfterSelection()
                && !manager.isFinishedSuccessfully()
                && gadget.getState() == ScriptGadgetState.Default
                && gadget.getContent() instanceof GadgetWorktop worktop
                && worktop.getWorktopOptions().contains(7)) {
            started = false;
            hidden = false;
            selected = null;
        }
    }

    public void tick(EntityGadget gadget) {
        if (spawn == null) return;
        synchronized (scene) {
            if (!owns(gadget)
                    || !current()
                    || !gadget.isAlive()
                    || !gadget.isAbilityLifecycleCurrent(gadget.getAbilityLifecycleVersion())
                    || scene.getEntities().get(gadget.getId()) != gadget) return;
            acceptScriptReset(gadget);
            if (started) {
                if (!failedAfterSelection()
                        && !hidden
                        && gadget == selected
                        && gadget.isAbilityLifecycleCurrent(selectedLifetime)
                        && clock.getAsLong() - hideAt >= 0) {
                    hidden = true;
                    gadget.updateState(ScriptGadgetState.GearStop);
                }
                return;
            }
            if (manager.isFinishedSuccessfully()
                    || scene.getChallenge() != null && scene.getChallenge().inProgress()
                    || !(gadget.getContent() instanceof GadgetWorktop worktop)) return;
            if (gadget.getState() != ScriptGadgetState.Default)
                gadget.updateState(ScriptGadgetState.Default);
            if (!worktop.getWorktopOptions().contains(7)) {
                worktop.addWorktopOptions(new int[] {7});
                scene.broadcastPacket(new PacketWorktopOptionNotify(gadget));
            }
        }
    }

    /**
     * Other worktops retain their handlers; a resource-confirmed failure can rearm this starter.
     */
    public boolean select(Player player, EntityGadget gadget, int option) {
        if (spawn == null) return true;
        synchronized (scene) {
            if (!owns(gadget) || option != 7) return true;
            if (!current()
                    || manager.isFinishedSuccessfully()
                    || !gadget.isAlive()
                    || !gadget.isAbilityLifecycleCurrent(gadget.getAbilityLifecycleVersion())
                    || scene.getEntities().get(gadget.getId()) != gadget
                    || player.getScene() != scene
                    || player.getWorld() != scene.getWorld()
                    || scene.getPlayers().stream().noneMatch(member -> member == player)
                    || scene.getChallenge() != null && scene.getChallenge().inProgress())
                return false;
            acceptScriptReset(gadget);
            if (started
                    || !(gadget.getContent() instanceof GadgetWorktop worktop)
                    || !worktop.getWorktopOptions().contains(7)) return false;
            started = true;
            selected = gadget;
            selectedLifetime = gadget.getAbilityLifecycleVersion();
            challengeAtSelection = scene.getChallenge();
            challengeSerialAtSelection =
                    challengeAtSelection == null ? 0 : challengeAtSelection.getStartSerial();
            hideAt = clock.getAsLong() + 1_000_000_000L;
            worktop.removeWorktopOption(7);
            scene.broadcastPacket(new PacketWorktopOptionNotify(gadget));
            return true;
        }
    }
}
