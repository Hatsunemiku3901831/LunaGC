package emu.grasscutter.game.ability;

import emu.grasscutter.GameConstants;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.net.proto.AbilityAppliedAbilityOuterClass.AbilityAppliedAbility;
import emu.grasscutter.net.proto.AbilityScalarValueEntryOuterClass.AbilityScalarValueEntry;
import emu.grasscutter.net.proto.AbilityStringOuterClass.AbilityString;
import emu.grasscutter.net.proto.AbilitySyncStateInfoOuterClass.AbilitySyncStateInfo;
import emu.grasscutter.utils.Utils;

import java.util.*;

/** Root descriptions and current parameters; partial sync does not claim modifier recovery. */
public final class AbilityRootState {
    private final GameEntity entity;

    private record Root(
            Ability ability, AbilityAppliedAbility raw, long lifetime, int entityId, Scene scene) {}

    private final Map<Integer, Root> roots = new TreeMap<>();

    public AbilityRootState(GameEntity entity) {
        this.entity = entity;
    }

    private Root capture(Ability ability, AbilityAppliedAbility raw) {
        return new Root(
                ability,
                raw,
                entity.getAbilityLifecycleVersion(),
                entity.getId(),
                entity.getScene());
    }

    private boolean current(int id, Root root) {
        var installed = entity.getInstancedAbilities();
        if (!entity.isAbilityLifecycleCurrent(root.lifetime)
                || entity.getId() != root.entityId
                || entity.getScene() != root.scene
                || id <= 0
                || id > installed.size()
                || installed.get(id - 1) != root.ability) return false;
        if (root.ability != null
                && root.ability.getPlayerOwner() != null
                && root.ability.getPlayerOwner().getScene() != root.scene) return false;
        var found = root.scene == null ? null : root.scene.getEntityById(root.entityId);
        return found == null || found == entity;
    }

    private AbilityAppliedAbility seed(int id, Ability ability) {
        return AbilityAppliedAbility.newBuilder()
                .setInstancedAbilityId(id)
                .setAbilityName(AbilityString.newBuilder().setStr(ability.getData().abilityName))
                .setAbilityOverride(
                        AbilityString.newBuilder().setHash(GameConstants.DEFAULT_ABILITY_NAME))
                .build();
    }

    synchronized void rootsChanged() {
        roots.entrySet().removeIf(e -> !current(e.getKey(), e.getValue()));
        var installed = entity.getInstancedAbilities().toArray(new Ability[0]);
        for (int i = 0; i < installed.length; i++) {
            var ability = installed[i];
            if (ability != null && ability.getOwner() == entity)
                roots.putIfAbsent(i + 1, capture(ability, seed(i + 1, ability)));
        }
    }

    synchronized void invalidateFrom(int index) {
        roots.keySet().removeIf(id -> id > index);
    }

    synchronized void record(int id, AbilityAppliedAbility raw) {
        var installed = entity.getInstancedAbilities();
        if (id <= 0 || id > installed.size()) return;
        var cleaned = raw.toBuilder().setInstancedAbilityId(id).clearOverrideMap();
        raw.getOverrideMapList().stream()
                .filter(AbilityRootState::valid)
                .forEach(cleaned::addOverrideMap);
        roots.put(id, capture(installed.get(id - 1), cleaned.build()));
    }

    synchronized void recordOverride(int id, AbilityScalarValueEntry update) {
        if (!valid(update)) return;
        var root = roots.get(id);
        if (root == null || !current(id, root)) {
            var installed = entity.getInstancedAbilities();
            if (id <= 0 || id > installed.size() || installed.get(id - 1) == null) return;
            var ability = installed.get(id - 1);
            root = capture(ability, seed(id, ability));
        }
        var merged = new LinkedHashMap<AbilityString, AbilityScalarValueEntry>();
        for (var value : root.raw.getOverrideMapList()) merged.put(value.getKey(), value);
        merged.put(update.getKey(), update);
        roots.put(
                id,
                capture(
                        root.ability,
                        root.raw.toBuilder()
                                .clearOverrideMap()
                                .addAllOverrideMap(merged.values())
                                .build()));
    }

    private static boolean valid(AbilityScalarValueEntry value) {
        return Float.isFinite(value.getFloatValue())
                && (value.getKey().hasHash()
                        || value.getKey().hasStr() && !value.getKey().getStr().isEmpty());
    }

    private static String resolve(AbilityString key, Set<String> names) {
        if (key.hasStr()) return key.getStr();
        if (!key.hasHash()) return null;
        String result = null;
        for (var name : names)
            if (Utils.abilityHash(name) == key.getHash()) {
                if (result != null) return null;
                result = name;
            }
        return result;
    }

    private AbilityAppliedAbility build(Root root) {
        if (root.ability == null) return root.raw;
        var live = new TreeMap<String, Float>();
        var specials = root.ability.getAbilitySpecials();
        synchronized (specials) {
            specials.forEach(
                    (key, value) -> {
                        if (key != null && !key.isEmpty() && Float.isFinite(value))
                            live.put(key, value);
                    });
        }
        var info = root.raw.toBuilder().clearOverrideMap();
        var seen = new HashSet<String>();
        var opaque = new LinkedHashMap<AbilityString, AbilityScalarValueEntry>();
        for (var value : root.raw.getOverrideMapList()) {
            var name = resolve(value.getKey(), live.keySet());
            if (name != null && live.containsKey(name)) {
                if (seen.add(name))
                    info.addOverrideMap(value.toBuilder().setFloatValue(live.get(name)));
            } else opaque.put(value.getKey(), value);
        }
        opaque.values().forEach(info::addOverrideMap);
        live.forEach(
                (name, value) -> {
                    if (seen.add(name))
                        info.addOverrideMap(
                                AbilityScalarValueEntry.newBuilder()
                                        .setKey(AbilityString.newBuilder().setStr(name))
                                        .setFloatValue(value));
                });
        return info.build();
    }

    public synchronized List<AbilityAppliedAbility> snapshot() {
        roots.entrySet().removeIf(e -> !current(e.getKey(), e.getValue()));
        var scene = entity.getScene();
        if (scene == null || scene.getEntityById(entity.getId()) != entity) return List.of();
        return roots.values().stream().map(this::build).toList();
    }

    private record Overrides(AbilityAppliedAbility root, Set<String> names) {}

    private static void rememberNames(AbilityAppliedAbility root, Set<String> names) {
        root.getOverrideMapList().stream()
                .map(AbilityScalarValueEntry::getKey)
                .filter(AbilityString::hasStr)
                .forEach(key -> names.add(key.getStr()));
    }

    private synchronized List<Overrides> overrideSnapshot() {
        var result = new ArrayList<Overrides>();
        for (var full : snapshot()) {
            var source = roots.get(full.getInstancedAbilityId());
            var names = new HashSet<String>();
            rememberNames(full, names);
            if (source.ability == null) {
                result.add(new Overrides(full, Set.copyOf(names)));
                continue;
            }
            var defaults = source.ability.getData().abilitySpecials;
            if (defaults != null) names.addAll(defaults.keySet());
            var specials = source.ability.getAbilitySpecials();
            synchronized (specials) {
                names.addAll(specials.keySet());
            }
            names.removeIf(name -> name == null || name.isEmpty());
            var partial = full.toBuilder().clearOverrideMap();
            for (var value : full.getOverrideMapList()) {
                var name = resolve(value.getKey(), names);
                Float original = name == null || defaults == null ? null : defaults.get(name);
                boolean explicit =
                        source.raw.getOverrideMapList().stream()
                                .anyMatch(
                                        raw ->
                                                raw.getKey().equals(value.getKey())
                                                        || name != null
                                                                && (raw.getKey().hasStr()
                                                                        ? name.equals(
                                                                                raw.getKey()
                                                                                        .getStr())
                                                                        : raw.getKey().getHash()
                                                                                == Utils
                                                                                        .abilityHash(
                                                                                                name)));
                if (name == null
                        || explicit
                        || source.ability.wasOverrideWritten(name)
                        || original == null
                        || Float.compare(original, value.getFloatValue()) != 0)
                    partial.addOverrideMap(value);
            }
            // Keep names removed by default/finite-value filtering for the final hash check.
            result.add(new Overrides(partial.build(), Set.copyOf(names)));
        }
        return List.copyOf(result);
    }

    private static int hash(AbilityString value, int fallback) {
        return value.hasHash()
                ? value.getHash()
                : value.hasStr() ? Utils.abilityHash(value.getStr()) : fallback;
    }

    private static long key(AbilityAppliedAbility root) {
        return Integer.toUnsignedLong(hash(root.getAbilityName(), 0)) << 32
                | Integer.toUnsignedLong(
                        hash(root.getAbilityOverride(), GameConstants.DEFAULT_ABILITY_NAME));
    }

    /** The native uninitialized path accepts named overrides and retains default initialization. */
    public void appendOverrides(AbilitySyncStateInfo.Builder info) {
        if (info.getIsInited()) return;
        var current = overrideSnapshot();
        var templates = new LinkedHashMap<Long, AbilityAppliedAbility>();
        var merged = new LinkedHashMap<Identity, AbilityAppliedAbility>();
        var names = new HashMap<Identity, Set<String>>();
        var templateDefaults = new HashMap<Long, Set<String>>();
        for (var root : info.getAppliedAbilitiesList()) {
            rememberNames(root, names.computeIfAbsent(identity(root), ignored -> new HashSet<>()));
            if (root.getInstancedAbilityId() == 0)
                templates.merge(key(root), root, AbilityRootState::merge);
            else
                merged.merge(
                        new Identity(key(root), root.getInstancedAbilityId()),
                        root,
                        AbilityRootState::merge);
        }
        var covered = new HashSet<Long>();
        for (var snapshot : current) {
            var root = snapshot.root;
            var scope = names.computeIfAbsent(identity(root), ignored -> new HashSet<>());
            scope.addAll(snapshot.names);
            templateDefaults
                    .computeIfAbsent(key(root), ignored -> new HashSet<>())
                    .addAll(snapshot.names);
            if (root.getOverrideMapCount() == 0) continue;
            var template = templates.get(key(root));
            if (template != null) scope.addAll(names.get(identity(template)));
            var partial = template == null ? root : merge(template, root);
            merged.merge(
                    new Identity(key(root), root.getInstancedAbilityId()),
                    partial,
                    AbilityRootState::merge);
            covered.add(key(root));
        }
        templates.forEach(
                (variant, template) -> {
                    if (!covered.contains(variant)) {
                        names.get(identity(template))
                                .addAll(templateDefaults.getOrDefault(variant, Set.of()));
                        merged.put(identity(template), template);
                    }
                });
        info.clearAppliedAbilities();
        merged.forEach(
                (id, root) -> {
                    var safe = withoutAmbiguousParameters(root, names.getOrDefault(id, Set.of()));
                    if (safe != null) info.addAppliedAbilities(safe);
                });
    }

    private record Identity(long variant, int instanceId) {}

    private static Identity identity(AbilityAppliedAbility root) {
        return new Identity(key(root), root.getInstancedAbilityId());
    }

    private static AbilityAppliedAbility withoutAmbiguousParameters(
            AbilityAppliedAbility root, Set<String> names) {
        var known = new HashMap<Integer, String>();
        var ambiguous = new HashSet<Integer>();
        for (var name : names) {
            int hash = Utils.abilityHash(name);
            var previous = known.putIfAbsent(hash, name);
            if (previous != null && !previous.equals(name)) ambiguous.add(hash);
        }
        if (ambiguous.isEmpty()) return root;
        var safe = root.toBuilder().clearOverrideMap();
        root.getOverrideMapList().stream()
                .filter(value -> !ambiguous.contains(hash(value.getKey(), 0)))
                .forEach(safe::addOverrideMap);
        return root.getOverrideMapCount() > 0 && safe.getOverrideMapCount() == 0
                ? null
                : safe.build();
    }

    private static AbilityAppliedAbility merge(
            AbilityAppliedAbility first, AbilityAppliedAbility last) {
        var values = new LinkedHashMap<Integer, AbilityScalarValueEntry>();
        for (var value : first.getOverrideMapList()) values.put(hash(value.getKey(), 0), value);
        for (var value : last.getOverrideMapList()) values.put(hash(value.getKey(), 0), value);
        return last.toBuilder()
                .mergeUnknownFields(first.getUnknownFields())
                .clearOverrideMap()
                .addAllOverrideMap(values.values())
                .build();
    }

    public AbilitySyncStateInfo toSyncState() {
        var info = AbilitySyncStateInfo.newBuilder();
        appendOverrides(info);
        AbilityGlobalValueSnapshot.append(entity, info);
        return info.build();
    }

    public synchronized void clear() {
        roots.clear();
    }
}
