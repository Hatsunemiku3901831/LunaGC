package emu.grasscutter.game.ability;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.data.binout.AbilityModifier.State;
import emu.grasscutter.game.ability.actions.AbilityActionHandler;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.AbilityStringOuterClass.AbilityString;
import emu.grasscutter.utils.Utils;
import it.unimi.dsi.fastutil.objects.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import lombok.Getter;

public class Ability {
    @Getter private AbilityData data;
    @Getter private GameEntity owner;
    @Getter private Player playerOwner;
    @Getter private boolean serverFoodHealing;

    void markServerFoodHealing() { serverFoodHealing = true; }
    private EntityAvatar casterEntity;

    @Getter private AbilityManager manager;
    @Getter private CompletableFuture<Void> triggerInitialization = CompletableFuture.completedFuture(null);

    void prepareTriggerInitialization() {
        triggerInitialization = new CompletableFuture<>();
    }

    @Getter private Object2FloatMap<String> abilitySpecials = Object2FloatMaps.synchronize(new Object2FloatOpenHashMap<>());
    private final Set<String> writtenOverrides = ConcurrentHashMap.newKeySet();

    public void markOverrideWritten(String name) {
        if(name!=null && !name.isEmpty()) writtenOverrides.add(name);
    }

    public boolean wasOverrideWritten(String name) { return writtenOverrides.contains(name); }
    private Map<String, Float> talentSpecials = new LinkedHashMap<>();

    @Getter
    private static Map<String, Object2FloatMap<String>> abilitySpecialsModified = new HashMap<>();

    @Getter private int hash;
    @Getter private Set<Integer> avatarSkillStartIds;
    @Getter private final long skillSlotSourceOrder = AbilitySkillSlots.nextSourceOrder();
    private volatile Set<AbilityModifierAction> serverTriggerCallbacks = Set.of();
    private volatile AbilityExecutionContext serverTriggerContext;

    void claimServerTriggerCallbacks(Set<AbilityModifierAction> callbacks, GameEntity target) {
        serverTriggerCallbacks = callbacks;
        serverTriggerContext = new AbilityExecutionContext(this, target);
    }

    boolean ownsServerTriggerCallback(AbilityModifierAction action) {
        var context = serverTriggerContext;
        return context != null && context.isCurrent() && serverTriggerCallbacks.contains(action);
    }
    private final Set<GameEntity> modifierTargets = Collections.newSetFromMap(new WeakHashMap<>());

    public void refreshModifierTargets(boolean notify) {
        List<GameEntity> targets;
        synchronized (modifierTargets) { targets = new ArrayList<>(modifierTargets); }
        var watchTargets = new ArrayList<>(targets);
        if (!watchTargets.contains(owner)) watchTargets.add(owner);
        for (var target : watchTargets) target.getAbilityGlobalValueWatchers().refresh(notify);
        for (var target : targets) {
            target.getAbilityHpModifier().refresh(notify);
            target.getAbilityPeriodicHealing().refresh();
            target.getAbilityModifierDuration().refresh();
        }
    }

    public Map<String, AbilityModifierController> getModifiers() {
        return getModifiers(owner);
    }

    /** Remove this ability's installed effects without clearing other abilities on the target. */
    public void removeInstalledModifiers() {
        List<GameEntity> targets;
        synchronized (modifierTargets) { targets = new ArrayList<>(modifierTargets); }
        if (!targets.contains(owner)) targets.add(owner);
        for (var target : targets) {
            var names = new HashSet<String>();
            var named = target.getAppliedAbilityModifiers().get(this);
            if (named != null) names.addAll(named.keySet());
            synchronized (target.getInstancedModifiers()) {
                for (var controller : target.getInstancedModifiers().values())
                    if (controller.getAbility() == this) names.add(controller.getName());
            }
            for (var name : names) removeModifier(target, name);
        }
    }

    public Map<String, AbilityModifierController> getModifiers(GameEntity target) {
        return target
                .getAppliedAbilityModifiers()
                .computeIfAbsent(this, key -> new ConcurrentHashMap<>());
    }

    public void registerModifier(GameEntity target, AbilityModifierController modifier) {
        registerModifier(target,modifier,true);
    }

    void registerModifier(GameEntity target, AbilityModifierController modifier, boolean refresh) {
        modifier.prepareAttachmentCallbacks(target);
        synchronized (target.getInstancedModifiers()) {
            modifier.bindApplication(target);
            var replaced=getModifiers(target).put(modifier.getName(), modifier);
            synchronized (modifierTargets) { modifierTargets.add(target); }
            target.getAbilityHpModifier().registered(modifier);
            target.getAbilityModifierDuration().registered(modifier);
            target.getAbilityPeriodicHealing().registered(modifier);
            if(replaced!=null && replaced!=modifier && replaced.hasAttachmentCallbacks()
                    && !target.getAbilityHpModifier().lifecycleControllers().contains(replaced))
                modifier.deferAttachmentReplacement(replaced);
            if (refresh) target.getAbilityHpModifier().refresh(true);
        }
        target.getAbilityGlobalValueWatchers().refresh();
        if(!Thread.holdsLock(target.getInstancedModifiers()) && !modifier.hasServerCallbacks())
            modifier.runAttachmentOnAdded(()->true);
    }

    public boolean removeModifier(GameEntity target, String name) {
        return removeModifier(target,name,true);
    }

    public void removeUniqueModifier(GameEntity target, String name) {
        var sources = Collections.newSetFromMap(new IdentityHashMap<Ability, Boolean>());
        sources.add(this);
        synchronized (target.getInstancedModifiers()) {
            for (var controller : target.getAbilityHpModifier().activeControllers()) {
                var data = controller.getModifierData();
                if (name.equals(controller.getName()) && (data.isUnique
                        || data.stacking != null && data.stacking.contains("Unique"))) sources.add(controller.getAbility());
            }
        }
        for (var source : sources) source.removeModifier(target, name);
    }

    boolean removeModifier(GameEntity target, String name, boolean refresh) {
        var instances = target.getInstancedModifiers();
        boolean removed;
        List<Runnable> endActions;
        synchronized (instances) {
            endActions = new ArrayList<>(target.getAbilityModifierDuration().removalCallbacks(this,name));
            for(var controller:target.getAbilityHpModifier().lifecycleControllers()) {
                if(controller.getAbility()==this && name.equals(controller.getName())
                        && (controller.hasServerCallbacks() || controller.hasAttachmentCallbacks() || controller.hasBindingCallbacks() || controller.hasAnimationCallbacks()))
                    endActions.add(controller::runServerOnRemoved);
                if(controller.getAbility()==this && name.equals(controller.getName()))
                    controller.closeAuraAttachments();
                if(controller.getAbility()==this && name.equals(controller.getName()))
                    endActions.add(() -> AnimationModifierCallbacks.parentRemoved(target, controller));
            }
            var named = target.getAppliedAbilityModifiers().get(this);
            removed = named != null && named.remove(name) != null;
            removed |= instances.int2ObjectEntrySet().removeIf(entry -> {
                if(entry.getValue().getAbility()!=this || !name.equals(entry.getValue().getName())) return false;
                target.getAbilityPeriodicHealing().clientRemoved(entry.getIntKey(),entry.getValue());
                return true;
            });
            if (named != null && named.isEmpty()) target.getAppliedAbilityModifiers().remove(this, named);
            removed |= target.getAbilityHpModifier().removeServerStacks(this, name);
            target.getAbilityPeriodicHealing().removed(this,name);
            if (removed && refresh) target.getAbilityHpModifier().refresh(true);
        }
        if (removed) AbilityGlobalValueWatchers.runRemovalCallbacks(() -> endActions.forEach(Runnable::run));
        if (removed) target.getAbilityGlobalValueWatchers().refresh();
        return removed;
    }

    public void unregisterModifier(GameEntity target, AbilityModifierController modifier) {
        unregisterModifier(target, modifier, true);
    }

    /** Release one binding's controller, preserving independently attached same-name instances. */
    boolean removeModifierInstance(GameEntity target, AbilityModifierController modifier, boolean refresh) {
        return removeModifierInstanceIf(target,modifier,refresh,()->true);
    }

    boolean removeModifierInstanceIf(GameEntity target, AbilityModifierController modifier, boolean refresh,
                                     BooleanSupplier owned) {
        boolean removed;
        synchronized (target.getInstancedModifiers()) {
            if(!owned.getAsBoolean()) return false;
            removed = target.getAbilityHpModifier().lifecycleControllers().contains(modifier);
            target.getInstancedModifiers().int2ObjectEntrySet().removeIf(entry -> {
                if (entry.getValue() != modifier) return false;
                target.getAbilityPeriodicHealing().clientRemoved(entry.getIntKey(), modifier);
                return true;
            });
            unregisterModifierLocked(target, modifier);
            if (removed && refresh) target.getAbilityHpModifier().refresh(true);
        }
        if (refresh) modifier.runServerOnRemoved();
        if (removed) target.getAbilityGlobalValueWatchers().refresh();
        return removed;
    }

    void unregisterModifier(GameEntity target, AbilityModifierController modifier, boolean refresh) {
        synchronized (target.getInstancedModifiers()) {
            unregisterModifierLocked(target,modifier);
            if (refresh) target.getAbilityHpModifier().refresh(true);
        }
        if(refresh) modifier.runServerOnRemoved();
        if(refresh) AnimationModifierCallbacks.parentRemoved(target, modifier);
        target.getAbilityGlobalValueWatchers().refresh();
    }

    private void unregisterModifierLocked(GameEntity target, AbilityModifierController modifier) {
        modifier.closeAuraAttachments();
        var named = target.getAppliedAbilityModifiers().get(this);
        var remainingServer = target.getAbilityHpModifier().unregisterServerStack(modifier);
        target.getAbilityPeriodicHealing().removed(modifier);
        target.getAbilityModifierDuration().cancel(modifier);
        // An older client instance may expire after its replacement was already installed.
        if (named != null) {
            named.remove(modifier.getName(), modifier);
            if (remainingServer != null) named.putIfAbsent(modifier.getName(), remainingServer);
            if (named.isEmpty()) target.getAppliedAbilityModifiers().remove(this, named);
        }
    }

    void retireModifierLocked(GameEntity target, AbilityModifierController modifier) {
        modifier.closeAuraAttachments();
        target.getInstancedModifiers().int2ObjectEntrySet().removeIf(entry -> {
            if(entry.getValue()!=modifier) return false;
            target.getAbilityPeriodicHealing().clientRemoved(entry.getIntKey(),modifier);
            return true;
        });
        unregisterModifierLocked(target,modifier);
    }

    boolean expireServerModifier(GameEntity target, AbilityModifierController modifier,
                                 BooleanSupplier currentTimer, Runnable onRemoved) {
        synchronized(target.getInstancedModifiers()) {
            if(!currentTimer.getAsBoolean()
                    || !target.getAbilityHpModifier().activeControllers().contains(modifier)
                    || target.getInstancedModifiers().containsValue(modifier) && !modifier.ownsServerDuration()) return false;
            if(modifier.ownsServerDuration()) target.getInstancedModifiers().int2ObjectEntrySet().removeIf(entry -> {
                if(entry.getValue()!=modifier) return false;
                target.getAbilityPeriodicHealing().clientRemoved(entry.getIntKey(),modifier);
                return true;
            });
            unregisterModifierLocked(target,modifier);
            target.getAbilityHpModifier().refresh(true);
        }
        onRemoved.run();
        target.getAbilityGlobalValueWatchers().refresh();
        return true;
    }

    public Ability(AbilityData data, GameEntity owner, Player playerOwner) {
        this.data = data;
        this.owner = owner;
        this.manager =
                playerOwner != null
                        ? playerOwner.getAbilityManager()
                        : owner.getWorld().getHost().getAbilityManager();

        if (this.data.abilitySpecials != null) {
            for (var entry : this.data.abilitySpecials.entrySet())
                abilitySpecials.put(entry.getKey(), entry.getValue().floatValue());
        }

        this.playerOwner = playerOwner;

        casterEntity = resolveCasterEntity(playerOwner, owner);
        Avatar casterAvatar = AbilityTalentResolver.casterAvatar(playerOwner, owner);
        if (casterAvatar == null && casterEntity != null) casterAvatar = casterEntity.getAvatar();
        if (casterAvatar != null) {
            talentSpecials = AbilityTalentResolver.overrides(casterAvatar, data.abilityName, data.abilitySpecials);
            talentSpecials.forEach(abilitySpecials::put);
        }

        hash = Utils.abilityHash(data.abilityName);

        data.initialize();

        avatarSkillStartIds = new HashSet<>();
        if (data.onAbilityStart != null) {
            avatarSkillStartIds.addAll(
                    Arrays.stream(data.onAbilityStart)
                            .filter(action -> action.type == AbilityModifierAction.Type.AvatarSkillStart)
                            .map(action -> action.skillID)
                            .toList());
        }
        avatarSkillStartIds.addAll(
                data.modifiers.values().stream()
                        .map(
                                m ->
                                        (List<AbilityModifierAction>)
                                                (m.onAdded == null ? Collections.emptyList() : Arrays.asList(m.onAdded)))
                        .flatMap(List::stream)
                        .filter(action -> action.type == AbilityModifierAction.Type.AvatarSkillStart)
                        .map(action -> action.skillID)
                        .toList());

        if (data.onAdded != null) {
            processOnAddedAbilityModifiers();
        }
    }

    public EntityAvatar getCasterEntity() {
        // The caster remains the same if the player changes their selected party member.
        if (casterEntity == null) casterEntity = resolveCasterEntity(this.playerOwner, this.owner);
        return casterEntity;
    }

    /** Refresh only changed talent bindings; retain unrelated and unchanged runtime variables. */
    public synchronized Map<String, Float> refreshTalentSpecials() {
        var caster = getCasterEntity();
        var avatar =
                caster != null
                        ? caster.getAvatar()
                        : AbilityTalentResolver.casterAvatar(playerOwner, owner);
        var next = AbilityTalentResolver.overrides(avatar, data.abilityName, data.abilitySpecials);
        var changes = new LinkedHashMap<String, Float>();
        for (var name : talentSpecials.keySet()) {
            if (next.containsKey(name)) continue;
            Float original = data.abilitySpecials == null ? null : data.abilitySpecials.get(name);
            if (original != null && Float.isFinite(original)) abilitySpecials.put(name, original);
            else abilitySpecials.removeFloat(name);
            changes.put(name, original != null && Float.isFinite(original) ? original : 0f);
        }
        next.forEach(
                (name, value) -> {
                    if (!Objects.equals(talentSpecials.get(name), value)) {
                        abilitySpecials.put(name, value);
                        changes.put(name, value);
                    }
                });
        talentSpecials = next;
        changes.keySet().forEach(this::markOverrideWritten);
        return changes;
    }

    private EntityAvatar resolveCasterEntity(Player player, GameEntity owner) {
        var explicitCaster = AbilityTalentResolver.caster(player, owner);
        if (explicitCaster != null) return explicitCaster;
        if (owner instanceof emu.grasscutter.game.entity.EntityClientGadget
                || owner instanceof emu.grasscutter.game.entity.EntityWeapon) return null;
        if (player == null || data.abilityName == null) {
            return owner instanceof EntityAvatar avatarEntity ? avatarEntity : null;
        }
        String abilityName = data.abilityName;
        for (var member : player.getTeamManager().getActiveTeam()) {
            var av = member.getAvatar();
            var avData = av.getAvatarData();
            if (avData == null) continue;
            String charName = avData.getName();
            if (charName == null || charName.isEmpty()) continue;
            if (abilityName.startsWith(charName + "_")
                    || abilityName.contains("_" + charName + "_")
                    || abilityName.endsWith("_" + charName)) {
                return member;
            }
        }
        return owner instanceof EntityAvatar avatarEntity ? avatarEntity : null;
    }

    public void processOnAddedAbilityModifiers() {
        for (AbilityModifierAction modifierAction : data.onAdded) {
            if (modifierAction == null || modifierAction.type == null) continue;

            if (modifierAction.type == AbilityModifierAction.Type.ApplyModifier
                    || modifierAction.type == AbilityModifierAction.Type.AttachModifier) {
                if (modifierAction.modifierName == null) continue;
                else if (!data.modifiers.containsKey(modifierAction.modifierName)) continue;

                var modifierData = data.modifiers.get(modifierAction.modifierName);
                if (modifierData != null
                        && (modifierData.state == State.Limbo
                                || modifierData.state == State.LockHP
                                || modifierData.state == State.Invincible
                                || AbilityPropertyModifier.hasProperties(modifierData))) {
                    var target = AbilityActionHandler.resolveTarget(this, owner, modifierAction.target);
                    if (target == null) continue;
                    @SuppressWarnings("unchecked")
                    var predicates = (List<Map<String, Object>>) (List<?>) modifierAction.predicates;
                    if (!PredicateEvaluator.all(predicates, this, owner, target, modifierAction)) continue;
                    // Keep initial protection removable through the same controller registry.
                    registerModifier(target, new AbilityModifierController(this, data, modifierData));
                }
            }
        }
    }

    public static String getAbilityName(AbilityString abString) {
        if (abString == null) return null;
        if (abString.hasStr()) return abString.getStr();
        if (abString.hasHash()) return GameData.getAbilityHashes().get(abString.getHash());

        return null;
    }

    @Override
    public String toString() {
        return "Ability Name: %s; Entity Owner: %s; Player Owner: %s"
                .formatted(data.abilityName, owner, playerOwner);
    }
}
