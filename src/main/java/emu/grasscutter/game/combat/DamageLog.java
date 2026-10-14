package emu.grasscutter.game.combat;

import com.google.gson.*;

import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.props.ElementType;
import emu.grasscutter.game.world.World;
import emu.grasscutter.utils.lang.Language;

import java.util.*;
import java.util.function.LongSupplier;

/** World-local statistics of accepted combat HP loss, never raw client damage claims. */
public final class DamageLog {
    private record Key(int uid, int avatarId) {}

    private static final class Row {
        final Key key;
        final String name;
        double damage, taken, maxHit;
        long hits;

        Row(EntityAvatar avatar) {
            key = new Key(avatar.getPlayer().getUid(), avatar.getAvatar().getAvatarId());
            var data = avatar.getAvatar().getAvatarData();
            String resolved = null;
            if (data != null) {
                try {
                    var texts = Language.getTextMapKey(data.getNameTextMapHash());
                    if (texts != null) resolved = texts.get("CHS");
                } catch (RuntimeException unavailable) {
                    // A missing display label must not interrupt HP settlement.
                }
            }
            name = resolved == null || resolved.isBlank() ? "角色 " + key.avatarId() : resolved;
        }
    }

    private final World world;
    private final LongSupplier clock;
    private final Map<Key, Row> rows = new LinkedHashMap<>();
    private final Deque<JsonObject> recent = new ArrayDeque<>();
    private final Map<EntityClientGadget, EntityAvatar> owners = new LinkedHashMap<>();
    private long lastHit, activeMillis;
    private boolean started;
    private long revision;

    public DamageLog(World world) {
        this(world, () -> System.nanoTime() / 1_000_000L);
    }

    public DamageLog(World world, LongSupplier clock) {
        this.world = Objects.requireNonNull(world);
        this.clock = Objects.requireNonNull(clock);
    }

    private boolean valid(EntityAvatar avatar) {
        var player = avatar.getPlayer();
        return player != null
                && player.getUid() > 0
                && player.getWorld() == world
                && player.getScene() == avatar.getScene()
                && player.getTeamManager().getActiveTeam().contains(avatar);
    }

    private EntityAvatar resolve(EntityClientGadget gadget) {
        GameEntity current = gadget;
        var seen = Collections.newSetFromMap(new IdentityHashMap<GameEntity, Boolean>());
        for (int i = 0; i < 32 && current instanceof EntityClientGadget g; i++) {
            if (!seen.add(current)
                    || g.getScene() != gadget.getScene()
                    || g.getOwner() != gadget.getOwner()) return null;
            current = g.getScene().getEntityById(g.getOwnerEntityId());
        }
        return current instanceof EntityAvatar a && a.getPlayer() == gadget.getOwner() && valid(a)
                ? a
                : null;
    }

    public synchronized void trackGadget(EntityClientGadget gadget) {
        if (gadget == null || gadget.getScene().getWorld() != world) return;
        owners.remove(gadget);
        var source = resolve(gadget);
        if (source != null) owners.put(gadget, source);
        while (owners.size() > 4096) owners.remove(owners.keySet().iterator().next());
    }

    private Row row(EntityAvatar avatar) {
        var key = new Key(avatar.getPlayer().getUid(), avatar.getAvatar().getAvatarId());
        var result = rows.get(key);
        if (result == null) {
            if (rows.size() >= 256) return null;
            result = new Row(avatar);
            rows.put(key, result);
        }
        return result;
    }

    public synchronized void record(
            GameEntity target, int attackerId, float damage, ElementType element) {
        if (target == null
                || !Float.isFinite(damage)
                || damage <= 0
                || attackerId == 0
                || target.getScene() == null
                || target.getScene().getWorld() != world
                || target.getScene().getEntities().get(target.getId()) != target) return;
        var attacker = target.getScene().getEntities().get(attackerId);
        if (attacker == null) return;
        if (target instanceof EntityAvatar hurt) {
            if (!valid(hurt)) return;
            var r = row(hurt);
            if (r == null) return;
            r.taken += damage;
            revision++;
            return;
        }
        EntityAvatar source = attacker instanceof EntityAvatar a ? a : null;
        if (attacker instanceof EntityClientGadget gadget) {
            source = owners.get(gadget);
            if (source == null) source = resolve(gadget);
        }
        if (source == null || !valid(source)) return;
        var r = row(source);
        if (r == null) return;
        r.damage += damage;
        r.hits++;
        r.maxHit = Math.max(r.maxHit, damage);
        long now = clock.getAsLong();
        if (started) {
            long delta = now - lastHit;
            if (delta > 0 && delta < 5000) activeMillis += delta;
        }
        started = true;
        lastHit = now;
        revision++;
        var hit = new JsonObject();
        hit.addProperty("uid", r.key.uid());
        hit.addProperty("avatarId", r.key.avatarId());
        hit.addProperty("targetId", target.getEntityTypeId());
        hit.addProperty("damage", damage);
        hit.addProperty("element", element == null ? 0 : element.getValue());
        recent.addLast(hit);
        while (recent.size() > 40) recent.removeFirst();
    }

    public synchronized JsonObject snapshot() {
        double total = 0, taken = 0, seconds = started ? Math.max(1, activeMillis / 1000.0) : 0;
        var output = new JsonObject();
        var data = new JsonArray();
        for (var r : rows.values()) {
            total += r.damage;
            taken += r.taken;
            var item = new JsonObject();
            item.addProperty("uid", r.key.uid());
            item.addProperty("avatarId", r.key.avatarId());
            item.addProperty("name", r.name);
            item.addProperty("damage", r.damage);
            item.addProperty("taken", r.taken);
            item.addProperty("hits", r.hits);
            item.addProperty("maxHit", r.maxHit);
            item.addProperty("dps", seconds > 0 ? r.damage / seconds : 0);
            data.add(item);
        }
        var history = new JsonArray();
        recent.forEach(h -> history.add(h.deepCopy()));
        output.addProperty("revision", revision);
        output.addProperty("activeSeconds", seconds);
        output.addProperty("totalDamage", total);
        output.addProperty("totalTaken", taken);
        output.addProperty("dps", seconds > 0 ? total / seconds : 0);
        output.add("rows", data);
        output.add("recent", history);
        return output;
    }

    public synchronized void reset() {
        rows.clear();
        recent.clear();
        activeMillis = 0;
        lastHit = 0;
        started = false;
        revision++;
        // Existing summons keep their original caster when an operator starts a fresh sample.
    }
}
