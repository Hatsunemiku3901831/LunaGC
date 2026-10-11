package emu.grasscutter.scripts.service;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.net.proto.VisionTypeOuterClass.VisionType;
import emu.grasscutter.scripts.SceneScriptManager;
import emu.grasscutter.scripts.constants.EventType;
import emu.grasscutter.scripts.data.*;
import emu.grasscutter.scripts.listener.ScriptMonsterListener;

import java.util.*;
import java.util.concurrent.Future;

/** One group/tide pair, tracking actual spawned objects rather than unrelated monster deaths. */
public final class ScriptMonsterTideService {
    private final SceneScriptManager manager;
    private final SceneGroup group;
    private final String source;
    private final int total, minimum, maximum;
    private final List<Integer> order;
    private final Map<Integer, EntityMonster> owned = new LinkedHashMap<>();
    private final List<Future<?>> callbacks = new ArrayList<>();
    private final ScriptMonsterListener deathListener = this::onMonsterDead;
    private boolean valid = true, started, paused, finished;
    private int index, created, dead, wave;

    public ScriptMonsterTideService(
            SceneScriptManager manager,
            String source,
            SceneGroup group,
            int total,
            int minimum,
            int maximum,
            Integer[] order) {
        this.manager = manager;
        this.group = group;
        this.source = source;
        this.total = total;
        this.minimum = minimum;
        this.maximum = maximum;
        this.order = List.of(order);
    }

    public SceneGroup getGroup() {
        return group;
    }

    public String getSource() {
        return source;
    }

    public int getCreatedCount() {
        synchronized (manager.getScene()) {
            return created;
        }
    }

    public int getAliveCount() {
        synchronized (manager.getScene()) {
            return owned.size();
        }
    }

    public int getDeadCount() {
        synchronized (manager.getScene()) {
            return dead;
        }
    }

    public boolean start() {
        synchronized (manager.getScene()) {
            if (!current() || started) return false;
            started = true;
            manager.getScriptMonsterSpawnService().addMonsterDeadListener(deathListener);
            return refillSafely();
        }
    }

    private boolean current() {
        return valid
                && manager.getMonsterTide(group.id, source) == this
                && manager.allowsGroupCall(group);
    }

    private SceneMonster nextMonster() {
        for (int offset = 0; offset < order.size(); offset++) {
            int at = (index + offset) % order.size(), config = order.get(at);
            if (owned.containsKey(config)
                    || manager.getScene().getEntityByConfigId(config, group.id) != null) continue;
            index = (at + 1) % order.size();
            return group.monsters.get(config);
        }
        return null;
    }

    private boolean fill() {
        if (!current() || paused || finished) return false;
        if (owned.size() >= minimum) return true;
        boolean first = true;
        while (current() && owned.size() < maximum) {
            if (total != 0 && created >= total) {
                finish(0);
                return true;
            }
            var definition = nextMonster();
            if (definition == null) return false;
            var monster = manager.createMonster(group.id, group.block_id, definition);
            if (!current()
                    || monster == null
                    || monster.getScene() != manager.getScene()
                    || monster.getGroupId() != group.id
                    || monster.getConfigId() != definition.config_id) return false;
            owned.put(definition.config_id, monster);
            manager.addEntity(monster);
            created++;
            if (first) {
                wave++;
                first = false;
            }
            emit(EventType.EVENT_MONSTER_TIDE_CREATE, created, wave, monster);
        }
        return true;
    }

    private boolean refillSafely() {
        try {
            return fill();
        } catch (RuntimeException failure) {
            Grasscutter.getLogger()
                    .error("Monster tide {} failed in group {}", source, group.id, failure);
            return false;
        }
    }

    private void onMonsterDead(EntityMonster monster) {
        synchronized (manager.getScene()) {
            if (!current()
                    || monster == null
                    || monster.getScene() != manager.getScene()
                    || monster.getGroupId() != group.id
                    || owned.get(monster.getConfigId()) != monster) return;
            owned.remove(monster.getConfigId());
            dead++;
            emit(EventType.EVENT_MONSTER_TIDE_DIE, dead, 0, monster);
            if (finished || paused) return;
            if (!refillSafely()) unload();
        }
    }

    /**
     * Finishing stops further spawns; surviving monsters still retain their own death accounting.
     */
    public boolean finish(int reason) {
        synchronized (manager.getScene()) {
            if (!current() || finished || reason < 0) return false;
            finished = true;
            emit(EventType.EVENT_MONSTER_TIDE_OVER, reason, dead, null);
            return true;
        }
    }

    public boolean pause() {
        synchronized (manager.getScene()) {
            if (!current() || finished || paused) return false;
            paused = true;
            return true;
        }
    }

    public boolean resume() {
        synchronized (manager.getScene()) {
            if (!current() || finished || !paused) return false;
            paused = false;
            if (refillSafely()) return true;
            unload();
            return false;
        }
    }

    private void emit(int type, int p1, int p2, EntityMonster monster) {
        var event = new ScriptArgs(group.id, type, p1, p2).setEventSource(source);
        if (monster != null) {
            event.setSourceEntityId(monster.getId());
            var data = monster.getMonsterData();
            event.param4 = data == null || data.getType() == null ? 0 : data.getType().getValue();
        }
        callbacks.removeIf(Future::isDone);
        callbacks.add(manager.callEvent(event, this::current, true));
    }

    /** Let the last completion/death callback return before releasing this tide identifier. */
    public boolean canRetire() {
        synchronized (manager.getScene()) {
            return !current()
                    || finished && owned.isEmpty() && callbacks.stream().allMatch(Future::isDone);
        }
    }

    public void unload() {
        synchronized (manager.getScene()) {
            if (!valid) return;
            valid = false;
            manager.getScriptMonsterSpawnService().removeMonsterDeadListener(deathListener);
            callbacks.forEach(task -> task.cancel(false));
            callbacks.clear();
            var remaining =
                    owned.values().stream()
                            .filter(
                                    monster ->
                                            manager.getScene().getEntities().get(monster.getId())
                                                    == monster)
                            .map(monster -> (emu.grasscutter.game.entity.GameEntity) monster)
                            .toList();
            owned.clear();
            if (!remaining.isEmpty())
                manager.getScene().removeEntities(remaining, VisionType.VisionType_VISION_MISS);
        }
    }
}
