package emu.grasscutter.game.ability;

import com.google.protobuf.ByteString;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.game.ability.actions.AbilityActionHandler;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.server.packet.send.PacketServerGlobalValueChangeNotify;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Value-range bindings on live abilities and attached modifiers, owned by their bearer. */
public final class AbilityGlobalValueWatchers {
    private record Key(Ability ability, String parent, AbilityMixinData mixin) {}
    private record Child(Ability ability, String name) {}
    private static final class State {
        final AbilityExecutionContext context;
        final Set<String> desired = new LinkedHashSet<>();
        GameEntity initializedSource;
        State(Key key, GameEntity bearer) { context = new AbilityExecutionContext(key.ability(), bearer); }
    }
    private record Claim(AbilityModifierController controller, boolean owned) {}
    private final GameEntity entity;
    private final Map<Key, State> states = new LinkedHashMap<>();
    private final Map<Child, Claim> claims = new LinkedHashMap<>();
    private final Set<Child> ownedChildren = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Map<AbilityGlobalValueWatchers, Boolean> dependents = new WeakHashMap<>();
    private final Set<GameEntity> sources = Collections.newSetFromMap(new IdentityHashMap<>());
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean dirty = new AtomicBoolean();
    private final AtomicBoolean notifyPending = new AtomicBoolean();
    private static final ThreadLocal<Boolean> notifications = ThreadLocal.withInitial(() -> true);
    public static boolean shouldNotify() { return notifications.get(); }
    private volatile Map<GameEntity, Set<String>> referenced = Map.of();

    public AbilityGlobalValueWatchers(GameEntity entity) { this.entity = entity; }

    /** Client confirmations must not repeat the server's initialization actions. */
    public boolean ownsChild(Ability ability, String name) { return ownedChildren.contains(new Child(ability,name)); }

    private List<AbilityGlobalValueWatchers> dependents() {
        synchronized (dependents) { return new ArrayList<>(dependents.keySet()); }
    }
    public Set<String> getReferencedKeys() {
        var keys = new LinkedHashSet<>(referenced.getOrDefault(entity, Set.of()));
        for (var watcher : dependents()) keys.addAll(watcher.referenced.getOrDefault(entity, Set.of()));
        return keys;
    }
    /** Source writes never hold a dependent's monitor while calling its actions. */
    public void changed() {
        refresh();
        for (var watcher : dependents()) watcher.refresh();
    }
    public void flushPending() { if (dirty.get()) drain(); }
    public void refresh() { refresh(shouldNotify()); }
    public void refresh(boolean notify) {
        if (notify) notifyPending.set(true);
        dirty.set(true); drain();
    }
    private void drain() {
        // Client registration and stat recalculation may surround a call with this registry lock.
        // Their caller flushes afterwards; entity tick also flushes any remaining pending refresh.
        if (Thread.holdsLock(entity.getInstancedModifiers()) || Thread.holdsLock(entity.getInstancedAbilities())
                || !running.compareAndSet(false, true)) return;
        try {
            int iterations = 0;
            while (dirty.getAndSet(false)) {
                boolean notify = notifyPending.getAndSet(false);
                var previous = notifications.get();
                notifications.set(notify);
                try { reconcile(); }
                finally { notifications.set(previous); }
                if (++iterations >= 64 && dirty.get()) {
                    dirty.set(false);
                    Grasscutter.getLogger().warn("Global-value modifier cycle stopped on entity {}",entity.getId());
                    break;
                }
            }
        } finally { running.set(false); }
        // A concurrent request after the last exchange must not be stranded.
        if (dirty.get()) drain();
    }

    private void addMixins(Map<Key, State> next, Ability ability, String parent, AbilityMixinData[] mixins) {
        if (mixins == null) return;
        for (var mixin : mixins) {
            if (mixin == null || !observesGlobalValue(mixin.type)
                    || !mixin.enable || mixin.globalValueKey == null || mixin.globalValueKey.isEmpty()) continue;
            var key = new Key(ability,parent,mixin);
            var state = states.get(key);
            if (state == null || !state.context.isCurrent()) state = new State(key,entity);
            if (state.context.isCurrent()) next.put(key,state);
        }
    }

    private static boolean observesGlobalValue(AbilityMixinData.Type type) {
        return type == AbilityMixinData.Type.AttachModifierToGlobalValueMixin
                || type == AbilityMixinData.Type.AttachModifierToSelfGlobalValueMixin
                || type == AbilityMixinData.Type.AttachModifierToSelfGlobalValueNoInitMixin;
    }
    private Map<Key, State> active() {
        var next = new LinkedHashMap<Key, State>();
        var abilities = Collections.newSetFromMap(new IdentityHashMap<Ability, Boolean>());
        abilities.addAll(entity.getInstancedAbilities());
        // The dynamic map is a cache, not an activation list. Its live modifiers are scanned below.
        for (var ability : abilities) {
            if (ability == null) continue;
            addMixins(next,ability,"",ability.getData().abilityMixins);
            var defaults = ability.getData().modifiers.get("Default");
            if (defaults != null) addMixins(next,ability,"Default",defaults.modifierMixins);
        }
        List<AbilityModifierController> modifiers;
        synchronized (entity.getInstancedModifiers()) {
            modifiers = entity.getAbilityHpModifier().activeControllers();
        }
        for (var controller : modifiers) addMixins(next,controller.getAbility(),controller.getName(),
                controller.getModifierData().modifierMixins);
        return next;
    }

    private GameEntity source(Ability ability, String name) {
        if (name == null || name.equals("Self") || name.equals("Target") || name.equals("Applier")) return entity;
        var player = ability.getPlayerOwner();
        return switch (name) {
            case "Owner", "Caster" -> ability.getOwner();
            case "Team" -> player == null ? null : player.getTeamManager().getEntity();
            case "CurLocalAvatar", "OriginOwner" -> player == null ? null : player.getTeamManager().getCurrentAvatarEntity();
            case "CasterOriginOwner" -> ability.getCasterEntity();
            case "TargetTeam" -> entity instanceof EntityAvatar avatar ? avatar.getPlayer().getTeamManager().getEntity() : null;
            case "MPLevel" -> player == null || player.getWorld() == null ? null : player.getWorld().getEntity();
            default -> null;
        };
    }

    /** Finite, ascending, half-open intervals; a final extra bound terminates the last interval. */
    public static int selectStep(Ability ability, GameEntity bearer, AbilityMixinData mixin, float value) {
        if (!Float.isFinite(value) || mixin.modifierNameSteps == null || mixin.modifierNameSteps.isEmpty()) return -1;
        if (mixin.valueSteps == null || mixin.valueSteps.isEmpty()) return value > 0 ? 0 : -1;
        var values = AbilityActionHandler.propertiesFor(ability,bearer);
        var bounds = new ArrayList<Float>();
        for (var expression : mixin.valueSteps) {
            if (expression == null) return -1;
            float bound = expression.get(values,0);
            if (!Float.isFinite(bound) || (!bounds.isEmpty() && bound <= bounds.get(bounds.size()-1))) return -1;
            bounds.add(bound);
        }
        for (int i=0;i<mixin.modifierNameSteps.size() && i<bounds.size();i++) {
            if (value >= bounds.get(i) && (i+1 == bounds.size() || value < bounds.get(i+1))) return i;
        }
        return -1;
    }

    private void reconcile() {
        var next = active();
        var dependencies = new IdentityHashMap<GameEntity, Set<String>>();
        var initializedSources = Collections.newSetFromMap(new IdentityHashMap<GameEntity, Boolean>());
        for (var entry : next.entrySet()) {
            var key = entry.getKey(); var state = entry.getValue(); var mixin = key.mixin();
            boolean noInit = mixin.type == AbilityMixinData.Type.AttachModifierToSelfGlobalValueNoInitMixin;
            GameEntity source = noInit ? entity : source(key.ability(),mixin.globalValueTarget);
            if (source == null || !AbilityExecutionContext.canTrigger(source,key.ability().getPlayerOwner())) {
                state.desired.clear(); continue;
            }
            dependencies.computeIfAbsent(source,k -> new LinkedHashSet<>()).add(mixin.globalValueKey);
            float fallback = noInit || mixin.defaultGlobalValueOnCreate == null ? 0
                    : mixin.defaultGlobalValueOnCreate.get(AbilityActionHandler.propertiesFor(key.ability(),entity),0);
            if (mixin.type == AbilityMixinData.Type.AttachModifierToSelfGlobalValueMixin
                    && state.initializedSource != source && Float.isFinite(fallback)) {
                // AstaPS initializes a missing key. A live binding must never reset later writes/clears.
                state.initializedSource = source;
                if (source.getGlobalAbilityValues().putIfAbsent(mixin.globalValueKey,fallback) == null) {
                    initializedSources.add(source);
                    if (shouldNotify() && source.getScene() != null)
                        source.getScene().broadcastPacket(new PacketServerGlobalValueChangeNotify(source,mixin.globalValueKey,fallback));
                }
            }
            float value = source.getGlobalAbilityValues().getOrDefault(mixin.globalValueKey,fallback);
            int step = selectStep(key.ability(),entity,mixin,value);
            if (mixin.removesAppliedModifier()) state.desired.clear();
            if (step >= 0) {
                var name = mixin.modifierNameSteps.get(step);
                if (name != null && !name.isEmpty() && key.ability().getData().modifiers.containsKey(name)) state.desired.add(name);
            }
        }
        states.clear(); states.putAll(next);
        for (var previous : new ArrayList<>(sources)) {
            if (!dependencies.containsKey(previous)) {
                var subscribers = previous.getAbilityGlobalValueWatchers().dependents;
                synchronized (subscribers) { subscribers.remove(this); }
                sources.remove(previous);
            }
        }
        for (var source : dependencies.keySet()) {
            if (sources.add(source) && source != entity) {
                var subscribers = source.getAbilityGlobalValueWatchers().dependents;
                synchronized (subscribers) { subscribers.put(this,true); }
            }
        }
        var immutable = new IdentityHashMap<GameEntity, Set<String>>();
        dependencies.forEach((source,keys) -> immutable.put(source,Set.copyOf(keys)));
        referenced = Collections.unmodifiableMap(immutable);
        var wanted = new LinkedHashSet<Child>();
        states.forEach((key,state) -> state.desired.forEach(name -> wanted.add(new Child(key.ability(),name))));
        boolean changed = false;
        for (var entry : new ArrayList<>(claims.entrySet())) {
            if (wanted.contains(entry.getKey())) continue;
            ownedChildren.remove(entry.getKey());
            if (entry.getValue().owned()) changed |= entry.getKey().ability().removeModifier(entity,entry.getKey().name(),false);
            claims.remove(entry.getKey());
        }
        var added = new ArrayList<Child>();
        for (var child : wanted) {
            var existing = child.ability().getModifiers(entity).get(child.name());
            if (claims.containsKey(child) && existing != null) continue;
            var controller = existing != null ? existing : new AbilityModifierController(child.ability(),
                    child.ability().getData(),child.ability().getData().modifiers.get(child.name()));
            claims.put(child,new Claim(controller,existing == null));
            if (existing == null) {
                ownedChildren.add(child);
                child.ability().registerModifier(entity,controller,false);
                changed = true; added.add(child);
            }
        }
        if (changed) entity.getAbilityHpModifier().refresh(shouldNotify());
        for (var child : added) {
            // Changes can remove a parent while another child's onAdded is executing.
            var owners = states.entrySet().stream().filter(e -> e.getKey().ability()==child.ability()
                    && e.getValue().desired.contains(child.name())).toList();
            if (owners.stream().noneMatch(e -> e.getValue().context.isCurrent())) continue;
            var modifier = child.ability().getData().modifiers.get(child.name());
            if (modifier.onAdded != null) AbilityManager.runServerOwned(() -> {
                var actions = Arrays.stream(modifier.onAdded)
                        .filter(action -> action != null && AbilityManager.isAllowedInServerOwnedChain(action.type))
                        .toArray(AbilityModifier.AbilityModifierAction[]::new);
                child.ability().getManager().executeCallbackActionsNow(child.ability(),actions,ByteString.EMPTY,entity,entity);
            });
        }
        // Notify other subscribers after this bearer's full modifier result has been reconciled.
        for (var source : initializedSources) source.onAbilityValueUpdate();
    }
}
