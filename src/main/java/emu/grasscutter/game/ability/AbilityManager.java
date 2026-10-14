package emu.grasscutter.game.ability;

import com.google.protobuf.*;
import emu.grasscutter.*;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.actions.*;
import emu.grasscutter.game.ability.mixins.*;
import emu.grasscutter.game.avatar.Avatar;

import emu.grasscutter.net.proto.AbilityMetaSpecialEnergyOuterClass;
import emu.grasscutter.net.proto.DetailAbilityInfoOuterClass.DetailAbilityInfo;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.EntityClientGadget;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.data.excels.ProudSkillData;
import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.net.proto.AbilityMetaAddAbilityOuterClass.AbilityMetaAddAbility;
import emu.grasscutter.net.proto.AbilityMetaModifierChangeOuterClass.AbilityMetaModifierChange;
import emu.grasscutter.server.packet.send.PacketMonsterSummonTagNotify;
import emu.grasscutter.net.proto.AbilityMetaReInitOverrideMapOuterClass.AbilityMetaReInitOverrideMap;
import emu.grasscutter.net.proto.AbilityMetaSetKilledStateOuterClass.AbilityMetaSetKilledState;
import emu.grasscutter.net.proto.AbilityMetaUpdateMoonOvergrowValueOuterClass.AbilityMetaUpdateMoonOvergrowValue;
import emu.grasscutter.net.proto.AbilityScalarTypeOuterClass.AbilityScalarType;
import emu.grasscutter.net.proto.AbilityScalarValueEntryOuterClass.AbilityScalarValueEntry;
import emu.grasscutter.net.proto.ModifierActionOuterClass.ModifierAction;
import emu.grasscutter.server.event.player.PlayerUseSkillEvent;
import emu.grasscutter.net.proto.AbilityStringOuterClass;
import emu.grasscutter.net.proto.AbilityStringOuterClass.AbilityString;
import emu.grasscutter.utils.Utils;

import emu.grasscutter.net.proto.AbilityInvokeArgumentOuterClass.AbilityInvokeArgument;
import emu.grasscutter.net.proto.AbilityInvokeEntryHeadOuterClass.AbilityInvokeEntryHead;
import emu.grasscutter.net.proto.ChangeHpDebtsReasonOuterClass;
import emu.grasscutter.net.proto.PropChangeReasonOuterClass;
import emu.grasscutter.server.packet.send.PacketAbilityInvocationsNotify;
import emu.grasscutter.server.packet.send.PacketAvatarFightPropNotify;
import emu.grasscutter.server.packet.send.PacketEntityFightPropChangeReasonNotify;
import emu.grasscutter.server.packet.send.PacketEntityFightPropUpdateNotify;
import emu.grasscutter.server.packet.send.PacketPlayerEnterSceneInfoNotify;
import emu.grasscutter.server.packet.send.PacketServerGlobalValueChangeNotify;
import emu.grasscutter.game.props.*;
import io.netty.util.concurrent.FastThreadLocalThread;

import java.util.*;
import java.util.concurrent.*;

import lombok.Getter;

public final class AbilityManager extends BasePlayerManager {
    @Getter private final AbilityCombatCallbacks combatCallbacks = new AbilityCombatCallbacks();
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AbilityManager.class);
    private final Set<EntityAvatar> resonanceEntities = Collections.newSetFromMap(new WeakHashMap<>());

    private static final HashMap<AbilityModifierAction.Type, AbilityActionHandler> actionHandlers =
        new HashMap<>();
    public static final HashMap<AbilityMixinData.Type, AbilityMixinHandler> mixinHandlers =
        new HashMap<>();

    /**
     * onAdded actions the server is responsible for even when the modifier orchestrates nothing.
     *
     * <p>SetGlobalValue is here because a modifier's marks are what its own later blocks test: the
     * one that spawns Sandrone's robot clears a mark, searches for a target, then spawns only if
     * the mark came back set. Skipping the clear left the mark absent on the first hold, so the
     * spawn never fired until a later attack had written it. Both reasons it used to be left out
     * are gone - writes made here no longer reach the client, and a bare SetGlobalValue no longer
     * reads as 1. Damage, healing and energy stay off the list: the client resolves those itself.
     */
    private static final java.util.EnumSet<AbilityModifierAction.Type> SERVER_OWNED_ON_ADDED =
        java.util.EnumSet.of(
            AbilityModifierAction.Type.Predicated,
            AbilityModifierAction.Type.CreateGadget,
            AbilityModifierAction.Type.SetGlobalPos,
            AbilityModifierAction.Type.GetFightProperty,
            AbilityModifierAction.Type.AddAvatarSkillInfo,
            AbilityModifierAction.Type.RemoveAvatarSkillInfo,
            AbilityModifierAction.Type.AvatarSkillStart,
            AbilityModifierAction.Type.ModifyAvatarSkillCD,
            AbilityModifierAction.Type.AvatarShareCDSkillStart,
            AbilityModifierAction.Type.ActivitySkillShareCDStart,
            AbilityModifierAction.Type.ClearShareCD,
            AbilityModifierAction.Type.SetGlobalValue,
            AbilityModifierAction.Type.TriggerVehicleOff);

    /**
     * What a server owned chain may run, at any depth.
     *
     * <p>The nested actions need the same restriction as the top level ones, and more sharply: a
     * Predicated block hands its children whichever entity its search picked, so an unfiltered
     * child that kills or hurts its target would fire straight at the nearest enemy - killing it
     * outright, with no damage event behind it. Only state and spawning is allowed through.
     */
    private static final java.util.EnumSet<AbilityModifierAction.Type> SERVER_OWNED_CHAIN =
        java.util.EnumSet.of(
            AbilityModifierAction.Type.Predicated,
            AbilityModifierAction.Type.Repeated,
            AbilityModifierAction.Type.CreateGadget,
            AbilityModifierAction.Type.SetGlobalPos,
            AbilityModifierAction.Type.SetGlobalValue,
            AbilityModifierAction.Type.AddGlobalValue,
            AbilityModifierAction.Type.ClearGlobalValue,
            AbilityModifierAction.Type.GetFightProperty,
            AbilityModifierAction.Type.AddAvatarSkillInfo,
            AbilityModifierAction.Type.RemoveAvatarSkillInfo,
            AbilityModifierAction.Type.AvatarSkillStart,
            AbilityModifierAction.Type.ModifyAvatarSkillCD,
            AbilityModifierAction.Type.AvatarShareCDSkillStart,
            AbilityModifierAction.Type.ActivitySkillShareCDStart,
            AbilityModifierAction.Type.ClearShareCD,
            AbilityModifierAction.Type.CopyGlobalValue,
            AbilityModifierAction.Type.TriggerVehicleOff);

    public static boolean isAllowedInServerOwnedChain(AbilityModifierAction.Type type) {
        return type != null && SERVER_OWNED_CHAIN.contains(type);
    }

    /**
     * Set while the server runs a modifier chain the client did not ask it to run.
     *
     * <p>Values written in there are the server's own bookkeeping - a mark one block sets so a later
     * block can test it - and the client has already worked them out for itself. Pushing them back
     * overwrites state that was correct, so those writes stay local.
     */
    private static final ThreadLocal<Boolean> serverOwnedChain =
        ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final ThreadLocal<Integer> callbackActionDepth = ThreadLocal.withInitial(() -> 0);

    public static boolean isServerOwnedChain() {
        return serverOwnedChain.get();
    }

    /**
     * Runs a block as the server's own bookkeeping.
     *
     * <p>Restores whatever the flag was rather than clearing it, so a chain that opens inside
     * another one does not hand the rest of its parent back to the client half way through.
     */
    public static void runServerOwned(Runnable body) {
        runServerOwned(true, body);
    }

    private static void runServerOwned(boolean owned, Runnable body) {
        var previous = serverOwnedChain.get();
        serverOwnedChain.set(owned);
        try {
            body.run();
        } finally {
            serverOwnedChain.set(previous);
        }
    }

    public static final ExecutorService eventExecutor;

    static {
        eventExecutor =
            new ThreadPoolExecutor(
                4,
                4,
                60,
                TimeUnit.SECONDS,
                new LinkedBlockingDeque<>(1000),
                FastThreadLocalThread::new,
                new ThreadPoolExecutor.AbortPolicy());

        registerHandlers();
    }

    @Getter
    private boolean abilityInvulnerable = false;
    private int burstCasterId;
    private int burstSkillId;

    private long arlecchinoChargedAttackTime = 0L;
    private long arlecchinoESkillTime = 0L;
    @Getter private final AbilitySharedCooldown sharedCooldown;
    @Getter private final AbilitySkillCooldown skillCooldown;
    @Getter private final AbilityRevivalSkillControl revivalSkillControl = new AbilityRevivalSkillControl();
    private volatile GameEntity scanTarget;

    public void setScanTarget(GameEntity target) { scanTarget = target; }
    public GameEntity getScanTarget() {
        var target=scanTarget;
        var scene=player.getScene();
        return target!=null && scene!=null && target.getScene()==scene
                && scene.getEntityById(target.getId())==target && target.isAlive()?target:null;
    }

    public AbilityManager(Player player) {
        super(player);
        this.sharedCooldown = new AbilitySharedCooldown(player);
        this.skillCooldown = new AbilitySkillCooldown(player);
        removePendingEnergyClear();
    }

    public void removePendingEnergyClear() {
        this.burstCasterId = 0;
        this.burstSkillId = 0;
    }

    private void onPossibleElementalBurst(Ability ability, AbilityModifier modifier, int entityId) {

        if (ability == null) {
            Grasscutter.getLogger().trace("possible elemental burst is null");
            return;
        }

        if (this.burstCasterId == 0) return;

        boolean skillInvincibility = modifier.state == AbilityModifier.State.Invincible;
        if (modifier.onAdded != null) {
            skillInvincibility |=
                Arrays.stream(modifier.onAdded)
                    .filter(
                        action ->
                            action.type == AbilityModifierAction.Type.AttachAbilityStateResistance
                                && action.resistanceListID == 11002)
                    .toList()
                    .size()
                    > 0;
        }

        if (this.burstCasterId == entityId
            && (ability.getAvatarSkillStartIds().contains(this.burstSkillId) || skillInvincibility)) {
            Grasscutter.getLogger()
                .trace(
                    "Caster ID's {} burst successful, clearing energy and setting invulnerability",
                    entityId);
            this.abilityInvulnerable = true;

            this.player
                .getEnergyManager()
                .handleEvtDoSkillSuccNotify(
                    this.player.getSession(), this.burstSkillId, this.burstCasterId);

            this.removePendingEnergyClear();
        }
    }

    public static void registerHandlers() {
        var handlerClassesAction = Grasscutter.reflector.getSubTypesOf(AbilityActionHandler.class);

        for (var obj : handlerClassesAction) {
            try {
                if (obj.isAnnotationPresent(AbilityAction.class)) {
                    var handler = obj.getDeclaredConstructor().newInstance();
                    for (var abilityAction : obj.getAnnotation(AbilityAction.class).value()) {
                        actionHandlers.put(abilityAction, handler);
                    }
                } else {
                    continue;
                }
            } catch (Exception e) {
                Grasscutter.getLogger().error("Unable to register handler.", e);
            }
        }

        var handlerClassesMixin = Grasscutter.reflector.getSubTypesOf(AbilityMixinHandler.class);
        for (var obj : handlerClassesMixin) {
            try {
                if (obj.isAnnotationPresent(AbilityMixin.class)) {
                    AbilityMixinData.Type abilityMixin = obj.getAnnotation(AbilityMixin.class).value();
                    mixinHandlers.put(abilityMixin, obj.getDeclaredConstructor().newInstance());
                } else {
                    continue;
                }
            } catch (Exception e) {
                Grasscutter.getLogger().error("Unable to register handler.", e);
            }
        }
    }

    public void executeAction(
        Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
        if (action == null || !BindingModifierCallbacks.current() || !action.hasSupportedOffStageConfig() || !AbilityCooldownInvocation.allows(action)) return;
        var handler = actionHandlers.get(action.type);
        if (handler == null || ability == null) {
            if (DebugConstants.LOG_MISSING_ABILITY_HANDLERS) {
                Grasscutter.getLogger()
                    .debug("Missing ability action handler for {} (invoker: {}).", action.type, ability);
            }

            return;
        }

        var context = new AbilityExecutionContext(ability, target,
                action.type == AbilityModifierAction.Type.ServerLuaCall
                        && action.luaCallType == AbilityModifierAction.LuaCallType.CurScenePlay);
        if (!context.isCurrent()) return;
        // A written position and the spawn that reads it belong to this invocation. Queuing them
        // separately can let a later cast change the point before the first summon is created.
        if (action.type == AbilityModifierAction.Type.SetGlobalPos
                || action.type == AbilityModifierAction.Type.CreateGadget
                || action.type == AbilityModifierAction.Type.Summon) {
            context.runIfCurrent(() -> executeActionNow(ability, action, abilityData, target));
            return;
        }
        var cooldownInvocation = AbilityCooldownInvocation.capture();
        var bindingGuard = BindingModifierCallbacks.captureGuard();
        eventExecutor.submit(
            () -> AbilityCooldownInvocation.run(cooldownInvocation, () -> context.runIfCurrent(() -> {
                if ((bindingGuard != null && !bindingGuard.getAsBoolean()) || !action.hasSupportedOffStageConfig()) return;
                try {
                    if (!handler.execute(ability, action, abilityData, target)) {
                        Grasscutter.getLogger()
                            .debug("Ability execute action failed for {} at {}.", action.type, ability);
                    }
                } catch (Throwable e) {
                    // submit() parks anything thrown in a Future nobody reads, so an action that
                    // blew up left no trace at all - the skill just quietly did nothing.
                    Grasscutter.getLogger()
                        .error("Ability action {} threw at {}.", action.type, ability, e);
                }
            })));
    }

    /**
     * Runs an action on the calling thread instead of handing it to the pool.
     *
     * <p>A modifier's actions are a sequence, not a set: one block sets a value the next block
     * reads. Submitted individually they race, and the read lands before the write often enough
     * that whatever they were guarding happens an invocation late, or not at all.
     */
    public void executeActionNow(
        Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
        executeActionNow(ability, action, abilityData, target, false);
    }

    /** The action sequence has already selected this leaf's recipient. */
    void executeResolvedActionNow(Ability ability, AbilityModifierAction action, GameEntity target) {
        executeActionNow(ability, action, ByteString.EMPTY, target, true);
    }

    private void executeActionNow(
        Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target, boolean resolved) {
        if (action == null || !BindingModifierCallbacks.current() || !action.hasSupportedOffStageConfig() || !AbilityCooldownInvocation.allows(action)) return;
        var handler = actionHandlers.get(action.type);
        if (handler == null || ability == null) {
            if (DebugConstants.LOG_MISSING_ABILITY_HANDLERS) {
                Grasscutter.getLogger()
                    .debug("Missing ability action handler for {} (invoker: {}).", action.type, ability);
            }

            return;
        }

        try {
            boolean success = resolved
                    ? handler.executeResolved(ability, action, abilityData, target)
                    : handler.execute(ability, action, abilityData, target);
            if (!success) {
                Grasscutter.getLogger()
                    .debug("Ability execute action failed for {} at {}.", action.type, ability);
            }
        } catch (Throwable e) {
            Grasscutter.getLogger().error("Ability action {} threw at {}.", action.type, ability, e);
        }
    }

    public void executeMixin(Ability ability, AbilityMixinData mixinData, ByteString abilityData) {
        executeMixin(ability, mixinData, abilityData, ability == null ? null : ability.getOwner());
    }

    public void executeMixin(Ability ability, AbilityMixinData mixinData, ByteString abilityData,
                             GameEntity target) {

        if (ability == null || mixinData == null || target == null) return;

        var handler = mixinHandlers.get(mixinData.type);
        // SwitchHealToHPDebtsMixin is evaluated by HealHP against installed modifiers.
        // A bare invocation cannot represent its predicates or its removal lifetime.

        if (handler == null || ability == null) {
            Grasscutter.getLogger()
                .trace("Could not execute ability mixin {} at {}", mixinData.type, ability);
            return;
        }

        var context = new AbilityExecutionContext(ability, target);
        if (!context.isCurrent()) return;
        if (mixinData.type==AbilityMixinData.Type.CheckSubTagScanEntityMixin
                || mixinData.type==AbilityMixinData.Type.TryEnterVehicleMixin
                || mixinData.type==AbilityMixinData.Type.DoActionOnVehicleInteractPostMixin
                || mixinData.type==AbilityMixinData.Type.TriggerVehicleOff
                || mixinData.type==AbilityMixinData.Type.AvatarCombatMixin) {
            // Scan selection must be visible to the immediately following modifier attachment.
            context.runIfCurrent(() -> handler.execute(ability,mixinData,abilityData,target));
            return;
        }
        eventExecutor.submit(
            () -> context.runIfCurrent(() -> {
                try {
                    if (!handler.execute(ability, mixinData, abilityData, target)) {
                        Grasscutter.getLogger()
                            .error("Ability execute action failed for {} at {}.", mixinData.type, ability);
                    }
                } catch (Throwable e) {
                    Grasscutter.getLogger()
                        .error("Ability mixin {} threw at {}.", mixinData.type, ability, e);
                }
            }));
    }

    /** A client-selected callback is one ordered action chain on its invocation target. */
    public boolean executeMixinActionsNow(Ability ability, AbilityMixinData mixin,
                                         ByteString payload, GameEntity target) {
        if (ability == null || mixin == null || target == null) return false;
        var actions = mixin.actions;
        if (actions == null || actions.length == 0) actions = mixin.actionQueue;
        if (actions == null || actions.length == 0) return true;
        var context = new AbilityExecutionContext(ability, target);
        if (!context.isCurrent()
                || !PredicateEvaluator.all(mixin.predicates, ability, ability.getOwner(), target, null))
            return true;
        return executeCallbackActionsNow(ability,actions,payload,target,target);
    }

    /** Ordered native branches share target resolution and lifetime checks with mixin callbacks. */
    public boolean executeCallbackActionsNow(Ability ability, AbilityModifierAction[] actions,
                                              ByteString payload, GameEntity self, GameEntity selectedTarget) {
        if (ability == null || self == null) return false;
        var context=new AbilityExecutionContext(ability,self);
        if (!context.isCurrent()) return false;
        if (actions == null) return true;
        int depth=callbackActionDepth.get();
        if (depth>=32) return false;
        callbackActionDepth.set(depth+1);
        try { return AbilityActionBudget.run(()->{
            for (var action:actions) {
                if (!context.isCurrent() || !BindingModifierCallbacks.current()) return true;
                if (action == null || (isServerOwnedChain() && !isAllowedInServerOwnedChain(action.type))) continue;
                var base="Target".equals(action.target)?selectedTarget:self;
                if (base == null) continue;
                for (var recipient:AbilityActionSequence.targets(ability,action,base,true)) {
                    if (!context.isCurrent() || !BindingModifierCallbacks.current()) return true;
                    if (recipient == null || !AbilityExecutionContext.canTrigger(recipient,ability.getPlayerOwner())) continue;
                    if (!AbilityActionBudget.consume()) return false;
                    @SuppressWarnings("unchecked")
                    var predicates=(List<Map<String,Object>>)(List<?>)action.predicates;
                    if (PredicateEvaluator.all(predicates,ability,ability.getOwner(),recipient,action))
                        executeActionNow(ability,action,payload,recipient,true);
                }
            }
            return true;
        }); } finally {
            if (depth==0) callbackActionDepth.remove(); else callbackActionDepth.set(depth);
        }
    }

    public void executeHealingCallbacks(Ability ability, AbilityModifierAction[] actions,
                                        GameEntity owner, String healTag) {
        queueCallbackActions(ability, actions, ByteString.EMPTY, owner, healTag);
    }

    private void queueCallbackActions(Ability ability, AbilityModifierAction[] actions,
                                      ByteString payload, GameEntity owner, String healTag) {
        if (ability == null || owner == null || actions == null || actions.length == 0) return;
        var context = new AbilityExecutionContext(ability, owner);
        if (!context.isCurrent()) return;
        var snapshot = actions.clone();
        var owned = isServerOwnedChain();
        var cooldownInvocation = AbilityCooldownInvocation.capture();
        eventExecutor.submit(() -> AbilityCooldownInvocation.run(cooldownInvocation, () -> AbilityHealContext.run(healTag, () -> runServerOwned(owned, () -> {
            context.runIfCurrent(() -> executeCallbackActionsNow(ability, snapshot, payload, owner, owner));
        }))));
    }

    public void onAbilityInvoke(AbilityInvokeEntry invoke) throws Exception {
        acceptAbilityInvoke(invoke);
    }

    /** TPS metadata is validated before forwarding; other invokes retain their existing routing. */
    public boolean acceptAbilityInvoke(AbilityInvokeEntry invoke) throws Exception {
        if (usesPlayTeam(invoke)) {
            var scene = player.getScene();
            if (scene == null) return false;
            synchronized (scene) {
                if (player.getScene() != scene || !ownsPlayTeams(invoke)) return false;
                return acceptAbilityInvokeCurrent(invoke);
            }
        }
        return acceptAbilityInvokeCurrent(invoke);
    }

    private static boolean isPlayTeamId(int id) {
        return (id >> GameConstants.ENTITY_ID_BIT_SHIFT) == EntityIdType.PLAY_TEAM.getId();
    }

    private static boolean usesPlayTeam(AbilityInvokeEntry invoke) {
        return isPlayTeamId(invoke.getEntityId()) || isPlayTeamId(invoke.getHead().getTargetId());
    }

    private boolean ownsPlayTeams(AbilityInvokeEntry invoke) {
        for (int id : new int[] {invoke.getEntityId(), invoke.getHead().getTargetId()}) {
            if (!isPlayTeamId(id)) continue;
            var entity = player.getScene().getScenePlayBattleManager().getPlayTeamEntity(id);
            if (entity == null || entity.getPlayer() != player) return false;
        }
        return true;
    }

    private boolean acceptAbilityInvokeCurrent(AbilityInvokeEntry invoke) throws Exception {
        // Metadata may carry the local ID of its originating action. It is still metadata,
        // and must not be redirected to handleServerInvoke's action/mixin dispatch.
        if (invoke.getArgumentType() == AbilityInvokeArgument.ABILITY_META_UPDATE_TPS_WEAPON_AMMUNITION) {
            try {
                return emu.grasscutter.game.tps.TpsWeaponSystem.onAmmunitionInvoke(this.player, invoke);
            } catch (InvalidProtocolBufferException e) {
                Grasscutter.getLogger().debug("Invalid TPS ammunition metadata for player {}", player.getUid());
                return false;
            }
        }
        Grasscutter.getLogger()
            .trace(
                "Ability invoke: "
                    + invoke
                    + " "
                    + invoke.getArgumentType()
                    + " ("
                    + invoke.getArgumentTypeValue()
                    + "): "
                    + this.player.getScene().getEntityById(invoke.getEntityId()));
        var entity = this.player.getScene().getEntityById(invoke.getEntityId());
        if (entity instanceof EntityAvatar avatarEntity && avatarEntity.getInstancedAbilities().isEmpty()) {
            initializeEntityAbilities(avatarEntity);
        }
        if (entity != null) {
            Grasscutter.getLogger()
                .trace(
                    "Entity {} has a group of {} and a config of {}.",
                    invoke.getEntityId(),
                    entity.getGroupId(),
                    entity.getConfigId());

            Grasscutter.getLogger()
                .trace(
                    "Invoke type of {} ({}) has entity {}.",
                    invoke.getArgumentType(),
                    invoke.getArgumentTypeValue(),
                    entity.getId());
        } else if (DebugConstants.LOG_ABILITIES) {
            Grasscutter.getLogger()
                .debug(
                    "Invoke type of {} ({}) has no entity. (referring to {})",
                    invoke.getArgumentType(),
                    invoke.getArgumentTypeValue(),
                    invoke.getEntityId());
        }

        if (invoke.getHead().getTargetId() != 0) {
            Grasscutter.getLogger()
                .trace("Target: " + this.player.getScene().getEntityById(invoke.getHead().getTargetId()));
        }
        if (invoke.getHead().getLocalId() != 0) {
            this.handleServerInvoke(invoke);
            return true;
        }

        switch (invoke.getArgumentType()) {
            case AbilityInvokeArgument_ABILITY_META_OVERRIDE_PARAM -> this.handleOverrideParam(invoke);

            case AbilityInvokeArgument_ABILITY_META_REINIT_OVERRIDEMAP -> this.handleReinitOverrideMap(invoke);
            case AbilityInvokeArgument_ABILITY_META_MODIFIER_CHANGE -> this.handleModifierChange(invoke);
            case AbilityInvokeArgument_ABILITY_MIXIN_COST_STAMINA -> this.handleMixinCostStamina(invoke);
            case AbilityInvokeArgument_ABILITY_ACTION_GENERATE_ELEM_BALL -> this.handleGenerateElemBall(invoke);
            case AbilityInvokeArgument_ABILITY_META_GLOBAL_FLOAT_VALUE -> this.handleGlobalFloatValue(invoke);
            case AbilityInvokeArgument_ABILITY_META_CLEAR_GLOBAL_FLOAT_VALUE ->
                this.handleClearGlobalFloatValue(invoke);
            case AbilityInvokeArgument_ABILITY_META_MODIFIER_DURABILITY_CHANGE -> this
                .handleModifierDurabilityChange(invoke);
            case AbilityInvokeArgument_ABILITY_META_ADD_NEW_ABILITY -> this.handleAddNewAbility(invoke);

            case AbilityInvokeArgument_ABILITY_META_SET_KILLED_SETATE -> this.handleKillState(invoke);
            case AbilityInvokeArgument_ABILITY_META_ADD_SPECIAL_ENERGY_VALUE -> this.handleAddSpecialEnergy(invoke);
            case ABILITY_META_UPDATE_MOON_OVERGROW_VALUE ->
                this.handleUpdateMoonOvergrowValue(invoke);

            default -> {
                int typeVal = invoke.getArgumentTypeValue();
                if (typeVal > 100) {
                    Grasscutter.getLogger().trace("UnknownAbilityInvoke: type={} entityId={} dataSize={}",
                        typeVal, invoke.getEntityId(), invoke.getAbilityData().size());
                }
                if (DebugConstants.LOG_MISSING_ABILITIES) {
                    Grasscutter.getLogger()
                        .trace("Missing invoke handler for ability {}.", invoke.getArgumentType().name());
                }
            }
        }
        return true;
    }

    private void handleClearGlobalFloatValue(AbilityInvokeEntry invoke)
        throws InvalidProtocolBufferException {
        var entity = this.player.getScene().getEntityById(invoke.getEntityId());

        if (entity == null) return;

        var entry = AbilityScalarValueEntry.parseFrom(invoke.getAbilityData());

        if (entry == null) return;
        String key = resolveGlobalValueKey(entry.getKey(), entity);

        if (key == null || key.isEmpty() || key.startsWith("SGV_")) return;

        entity.getGlobalAbilityValues().remove(key);
        entity.onAbilityValueUpdate();
    }

    /**
     * Mirrors the party's Verdant Dew, which the client owns. Recorded, never echoed: the server
     * evaluates ByTargetGlobalValue(MoonOvergrowPoint_All, Team) and would otherwise read zero.
     */
    private void handleUpdateMoonOvergrowValue(AbilityInvokeEntry invoke)
        throws InvalidProtocolBufferException {
        var update = AbilityMetaUpdateMoonOvergrowValue.parseFrom(invoke.getAbilityData());
        if (update.getUpdateType() != AbilityMetaUpdateMoonOvergrowValue._UpdateType.SET) return;

        var entity = this.player.getScene().getEntityById(invoke.getEntityId());
        if (entity == null) entity = this.player.getTeamManager().getEntity();

        entity.getGlobalAbilityValues().put(TeamManager.VERDANT_DEW, update.getValue());
        entity.onAbilityValueUpdate();
    }

    private void handleAddSpecialEnergy(AbilityInvokeEntry invoke) throws InvalidProtocolBufferException {
        var head = invoke.getHead();
        AbilityMetaSpecialEnergyOuterClass.AbilityMetaSpecialEnergy abilityMetaSpecialEnergy = AbilityMetaSpecialEnergyOuterClass.AbilityMetaSpecialEnergy.parseFrom(invoke.getAbilityData());
        var entity = this.player.getScene().getEntityById(invoke.getEntityId());
        if (entity == null) {
            Grasscutter.getLogger().trace("Entity not found: {}", invoke.getEntityId());
            return;
        }
        var target = this.player.getScene().getEntityById(head.getTargetId());
        if (target == null) target = entity;
        float specialEnergyAdd = abilityMetaSpecialEnergy.getValue();
        target.addSpecialEnergy(specialEnergyAdd);
    }

    public void handleServerInvoke(AbilityInvokeEntry invoke) {
        if (usesPlayTeam(invoke)) {
            var scene = player.getScene();
            if (scene == null) return;
            synchronized (scene) {
                if (player.getScene() == scene && ownsPlayTeams(invoke))
                    handleServerInvokeCurrent(invoke);
            }
        } else handleServerInvokeCurrent(invoke);
    }

    private void handleServerInvokeCurrent(AbilityInvokeEntry invoke) {
        var head = invoke.getHead();

        var entity = this.player.getScene().getEntityById(invoke.getEntityId());

        if (entity == null) {
            Grasscutter.getLogger().trace(
                "handleServerInvoke: entity not found: entityId={} localId={} type={}",
                invoke.getEntityId(), head.getLocalId(), invoke.getArgumentType());
            return;
        }

        var target = this.player.getScene().getEntityById(head.getTargetId());
        if (target == null) target = entity;

        Ability ability = null;
        AbilityModifierController invokedModifier = null;

        if (head.getInstancedModifierId() != 0) {
            var modifier = entity.getInstancedModifiers().get(head.getInstancedModifierId());
            if (modifier != null) {
                ability = modifier.getAbility();
                invokedModifier = modifier;
            }
        }

        if (ability == null
            && head.getInstancedAbilityId() != 0
            && (head.getInstancedAbilityId() - 1) >= 0
            && (head.getInstancedAbilityId() - 1) < entity.getInstancedAbilities().size()) {
            ability = entity.getInstancedAbilities().get(head.getInstancedAbilityId() - 1);
        }

        if (ability == null
            && invoke.getArgumentType() == AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_ACTION_GENERATE_ELEM_BALL) {
            ability = this.findOwnerElemBallAbility(entity, head.getLocalId());
        }

        if (ability == null) {
            Grasscutter.getLogger().trace(
                "[InvokeMiss] ability not found: entity={} abilId={} modId={} listSize={}",
                entity.getId(), head.getInstancedAbilityId(), head.getInstancedModifierId(),
                entity.getInstancedAbilities().size());
            return;
        }
        if (ability != null && target != null) {

            var data = ability.getData();

            var detailAbility = DetailAbilityInfo.newBuilder()
                .setParentAbilityName(AbilityString.newBuilder().setStr(data.abilityName))
                .setInstancedAbilityId(head.getInstancedAbilityId())
                .setInstancedModifierId(head.getInstancedModifierId())
                .setLocalId(head.getLocalId())
                .build();

            target.setDetailAbilityInfo(detailAbility);
        }

        var combatMixin=ability.getData().localIdToMixin.get(head.getLocalId());
        boolean combatArgument=invoke.getArgumentType()==AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_MIXIN_AVATAR_COMBAT;
        boolean combatCallback=combatMixin!=null && combatMixin.type==AbilityMixinData.Type.AvatarCombatMixin;
        if (combatArgument!=combatCallback) return;
        if (combatArgument) {
            if (this.player!=ability.getPlayerOwner()
                    || head.getInstancedModifierId()!=0 && invokedModifier==null) return;
            combatCallbacks.apply(ability,combatMixin,invoke.getAbilityData(),target,invokedModifier);
            return;
        }
        var action = ability.getData().localIdToAction.get(head.getLocalId());
        if (action != null) {
            if (ability.ownsServerTriggerCallback(action)) return;
            if (invokedModifier != null && invokedModifier.ownsAnimationCallback(action)) return;
            if (head.getInstancedModifierId() != 0 && invokedModifier == null
                    && AnimationModifierCallbacks.isCallback(ability.getData(), action)) return;
            boolean owned = entity.getAbilityPeriodicHealing().ownsAction(ability,head.getInstancedModifierId(),action);
            if (AbilityCooldownInvocation.isCooldownAction(action)) {
                if (head.getInstancedModifierId() != 0 && invokedModifier == null) return;
                if (invokedModifier != null && AbilityCooldownInvocation.isModifierCallback(ability.getData(), action)
                        && !invokedModifier.ownsCooldownCallback(action)) return;
                if (invokedModifier == null && AbilityCooldownInvocation.isModifierCallback(ability.getData(), action)) {
                    // A removed explicit instance cannot consume a later cast's charges. Without
                    // an instance ID, a unique live application is the only unambiguous match.
                    if (head.getInstancedModifierId() != 0) return;
                    var currentAbility = ability;
                    var matches = entity.getAbilityHpModifier().activeControllers().stream()
                            .filter(c -> c.getAbility() == currentAbility && c.ownsCooldownCallback(action)).toList();
                    if (matches.size() != 1) return;
                    invokedModifier = matches.get(0);
                }
                if (owned && invokedModifier == null) return;
                var scope = invokedModifier == null ? null : new AbilityCooldownInvocation.Scope(invokedModifier, false);
                var currentAbility = ability;
                var currentTarget = target;
                // Record before processing later removal/start messages; asynchronous dispatch
                // could evaluate this cast against the next cast's changed parameters.
                AbilityCooldownInvocation.run(scope, () -> runServerOwned(owned, () ->
                        executeActionNow(currentAbility, action, invoke.getAbilityData(), currentTarget)));
                return;
            }
            if (owned) return;
            this.executeAction(ability, action, invoke.getAbilityData(), target);
            return;
        } else {
            var mixin = ability.getData().localIdToMixin.get(head.getLocalId());

            if (mixin != null) {
                Grasscutter.getLogger().trace("Executing mixin: {}", mixin);
                executeMixin(ability, mixin, invoke.getAbilityData(), target);

                return;
            }
        }

        Grasscutter.getLogger().trace(
            "handleServerInvoke: action/mixin not found: localId={} ability={} knownActionIds={} knownMixinIds={}",
            head.getLocalId(),
            ability.getData().abilityName,
            ability.getData().localIdToAction.keySet(),
            ability.getData().localIdToMixin.keySet());
    }

    private Ability findOwnerElemBallAbility(GameEntity entity, int localId) {
        GameEntity owner = entity;
        for (int hops = 0; hops < 8 && owner instanceof EntityClientGadget gadget; hops++) {
            owner = this.player.getScene().getEntityById(gadget.getOwnerEntityId());
        }
        if (!(owner instanceof EntityAvatar avatar)) return null;

        var icon = avatar.getAvatar().getAvatarData().getIconName();
        if (icon == null || icon.isEmpty()) return null;
        var prefix = "Avatar_" + icon.substring(icon.lastIndexOf('_') + 1) + "_";

        var matches =
            GameData.getAbilityDataMap().values().stream()
                .filter(data -> data.abilityName != null && data.abilityName.startsWith(prefix))
                .filter(
                    data -> {
                        var action = data.localIdToAction.get(localId);
                        return action != null && action.type == AbilityModifierAction.Type.GenerateElemBall;
                    })
                .toList();
        return matches.size() == 1 ? new Ability(matches.get(0), avatar, this.player) : null;
    }

    public void onSkillStart(Player player, int skillId, int casterId) {

        if (player.getUid() != this.player.getUid()) {
            return;
        }

        var currentAvatar = player.getTeamManager().getCurrentAvatarEntity();
        if (currentAvatar == null || currentAvatar.getId() != casterId) {
            return;
        }

        var skillData = GameData.getAvatarSkillDataMap().get(skillId);
        if (skillData == null) {
            return;
        }

        var event = new PlayerUseSkillEvent(player, skillData, currentAvatar.getAvatar());
        if (!event.call()) return;

        if (skillData.getCostElemVal() <= 0) {
            return;
        }

        this.burstSkillId = skillId;
        this.burstCasterId = casterId;
    }

    public void onSkillEnd(Player player) {

        if (player.getUid() != this.player.getUid()) {
            return;
        }

        if (!this.abilityInvulnerable) {
            return;
        }

        this.abilityInvulnerable = false;
    }

    private void setAbilityOverrideValue(Ability ability, AbilityScalarValueEntry valueChange) {
        String key = valueChange.getKey().hasStr() ? valueChange.getKey().getStr() : null;
        if (valueChange.getKey().hasHash()) {
            // The global name table contains ability names, not their parameter names.
            // Resolve a compressed parameter against the specials of this exact instance.
            int hash = valueChange.getKey().getHash();
            key = ability.getAbilitySpecials().keySet().stream()
                .filter(name -> Utils.abilityHash(name) == hash)
                .findFirst().orElse(null);
        }
        if (key == null || key.isEmpty() || !Float.isFinite(valueChange.getFloatValue())) return;

        ability.getAbilitySpecials().put(key, valueChange.getFloatValue());
        Grasscutter.getLogger()
            .trace(
                "Ability {} changed {} to {}",
                ability.getData().abilityName,
                key,
                valueChange.getFloatValue());
    }

    private void handleOverrideParam(AbilityInvokeEntry invoke) throws Exception {
        var entity = this.player.getScene().getEntityById(invoke.getEntityId());
        var head = invoke.getHead();

        if (entity == null) {
            Grasscutter.getLogger().trace("Entity not found: {}", invoke.getEntityId());
            return;
        }

        var instancedAbilityIndex = head.getInstancedAbilityId() - 1;
        if (instancedAbilityIndex < 0 || instancedAbilityIndex >= entity.getInstancedAbilities().size()) {
            Grasscutter.getLogger().trace("Ability not found {}", head.getInstancedAbilityId());
            return;
        }

        var valueChange = AbilityScalarValueEntry.parseFrom(invoke.getAbilityData());

        var ability = entity.getInstancedAbilities().get(instancedAbilityIndex);
        if (ability == null) return;
        setAbilityOverrideValue(ability, valueChange);
        ability.refreshModifierTargets(true);
    }

    private void handleReinitOverrideMap(AbilityInvokeEntry invoke) throws Exception {
        var entity = player.getScene().getEntityById(invoke.getEntityId());
        if (entity == null) return;
        int index = invoke.getHead().getInstancedAbilityId() - 1;
        if (index < 0 || index >= entity.getInstancedAbilities().size()) return;
        var ability = entity.getInstancedAbilities().get(index);
        if (ability == null) return;
        var changes = AbilityMetaReInitOverrideMap.parseFrom(invoke.getAbilityData());
        for (var change : changes.getOverrideMapList()) setAbilityOverrideValue(ability, change);
        ability.refreshModifierTargets(true);

        if (!(entity instanceof EntityClientGadget) && !(entity instanceof emu.grasscutter.game.entity.EntityWeapon)) return;
        var computed = resolveTalentOverrides(ability);
        var notifications = new ArrayList<AbilityInvokeEntry>();
        computed.forEach((name, value) -> {
            ability.getAbilitySpecials().put(name, value);
            notifications.add(talentOverrideInvoke(entity, index + 1, name, value));
        });

        // A shared variable name can have different values in different abilities.
        var caster = ability.getCasterEntity();
        if (caster != null && !computed.isEmpty()) {
            var casterAbilities = caster.getInstancedAbilities();
            for (int i = 0; i < casterAbilities.size(); i++) {
                var candidate = casterAbilities.get(i);
                if (candidate == null || candidate == ability) continue;
                var ownValues = resolveTalentOverrides(candidate);
                for (var entry : ownValues.entrySet()) {
                    if (!computed.containsKey(entry.getKey())) continue;
                    candidate.getAbilitySpecials().put(entry.getKey(), entry.getValue());
                    notifications.add(talentOverrideInvoke(caster, i + 1, entry.getKey(), entry.getValue()));
                }
                candidate.refreshModifierTargets(true);
            }
        }
        ability.refreshModifierTargets(true);
        if (!notifications.isEmpty()) player.sendPacket(new PacketAbilityInvocationsNotify(notifications));
    }

    private static Map<String, Float> resolveTalentOverrides(Ability ability) {
        var avatar = AbilityTalentResolver.casterAvatar(ability.getPlayerOwner(), ability.getOwner());
        if (avatar == null && ability.getCasterEntity() != null) avatar = ability.getCasterEntity().getAvatar();
        return AbilityTalentResolver.overrides(avatar, ability.getData().abilityName, ability.getData().abilitySpecials);
    }

    private static AbilityInvokeEntry talentOverrideInvoke(GameEntity entity, int abilityId, String name, float value) {
        return AbilityInvokeEntry.newBuilder()
                .setEntityId(entity.getId())
                .setArgumentType(AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_META_OVERRIDE_PARAM)
                .setHead(AbilityInvokeEntryHead.newBuilder().setInstancedAbilityId(abilityId))
                .setAbilityData(AbilityScalarValueEntry.newBuilder()
                        .setKey(AbilityString.newBuilder().setStr(name).setHash(Utils.abilityHash(name)))
                        .setFloatValue(value).build().toByteString())
                .build();
    }

    /** Refresh equipped entities only; fired projectiles retain their launch parameters. */
    public void refreshEquipmentTalents(Avatar avatar, boolean notify) {
        if (avatar.getPlayer() != player) return;
        var entities = Collections.newSetFromMap(new IdentityHashMap<GameEntity, Boolean>());
        var avatarEntity = avatar.getAsEntity();
        if (avatarEntity != null) entities.add(avatarEntity);
        var weapon = avatar.getWeapon();
        if (weapon != null && weapon.getWeaponEntity() != null) entities.add(weapon.getWeaponEntity());
        for (var item : emu.grasscutter.game.tps.TpsWeaponSystem.getWornWeapons(avatar))
            if (item.getWeaponEntity() != null) entities.add(item.getWeaponEntity());
        var updates = new ArrayList<AbilityInvokeEntry>();
        for (var entity : entities) {
            var abilities = entity.getInstancedAbilities();
            for (int i = 0; i < abilities.size(); i++) {
                var ability = abilities.get(i);
                if (ability == null) continue;
                var values = ability.refreshTalentSpecials();
                ability.refreshModifierTargets(notify);
                for (var entry : values.entrySet())
                    updates.add(talentOverrideInvoke(entity, i + 1, entry.getKey(), entry.getValue()));
            }
        }
        if (notify && player.hasSentLoginPackets() && !updates.isEmpty())
            player.sendPacket(new PacketAbilityInvocationsNotify(updates));
    }

    /** Keep server instance IDs aligned with the new team announcement, retaining live skills. */
    public void refreshTeamResonanceAbilities(int previousResonanceCount, boolean layoutChanged, boolean notify) {
        var updates = new ArrayList<AbilityInvokeEntry>();
        var active = player.getTeamManager().getActiveTeamSnapshot();
        List<EntityAvatar> departed;
        synchronized (resonanceEntities) {
            departed = resonanceEntities.stream().filter(e -> !active.contains(e)).toList();
            resonanceEntities.clear();
            resonanceEntities.addAll(active);
        }
        for (var entity : departed)
            for (var ability : entity.getInstancedAbilities())
                if (ability != null && GameData.getTeamResonanceDataMap().values().stream()
                        .anyMatch(d -> d.getOpenConfig().equals(ability.getData().abilityName)))
                    ability.removeInstalledModifiers();
        for (var entity : active) {
            if (!layoutChanged) {
                refreshEquipmentTalents(entity.getAvatar(), notify);
                continue;
            }
            var instances = entity.getInstancedAbilities();
            if (instances.isEmpty()) continue; // First client initialization uses the new roster.
            var old = new ArrayList<>(instances);
            var block = entity.getAbilityControlBlock().getAbilityEmbryoListList();
            int oldSize = Math.min(old.size(), block.size()
                    - player.getTeamManager().getTeamResonancesConfig().size() + previousResonanceCount);
            var byHash = new HashMap<Integer, ArrayDeque<Ability>>();
            for (var ability : old.subList(0, oldSize))
                if (ability != null) byHash.computeIfAbsent(ability.getHash(), h -> new ArrayDeque<>()).add(ability);
            var next = new ArrayList<Ability>();
            var retained = Collections.newSetFromMap(new IdentityHashMap<Ability, Boolean>());
            for (var embryo : block) {
                var candidates = byHash.get(embryo.getAbilityNameHash());
                Ability ability = candidates == null || candidates.isEmpty() ? null : candidates.removeFirst();
                if (ability != null) retained.add(ability);
                else {
                    var name = GameData.getAbilityHashes().get(embryo.getAbilityNameHash());
                    var data = name == null ? null : GameData.getAbilityData(name);
                    if (data != null) {
                        ability = new Ability(data, entity, player);
                        for (var entry : AbilityTalentResolver.overrides(entity.getAvatar(), name).entrySet())
                            updates.add(talentOverrideInvoke(entity, embryo.getAbilityId(), entry.getKey(), entry.getValue()));
                    }
                }
                next.add(ability);
            }
            for (var ability : old.subList(0, oldSize))
                if (ability != null && !retained.contains(ability)) ability.removeInstalledModifiers();
            next.addAll(old.subList(oldSize, old.size())); // Dynamic abilities keep their relative order.
            if (!old.equals(next)) {
                instances.clear();
                instances.addAll(next);
            }
            refreshEquipmentTalents(entity.getAvatar(), notify);
        }
        if (notify && player.hasSentLoginPackets() && !updates.isEmpty())
            player.sendPacket(new PacketAbilityInvocationsNotify(updates));
    }

    private Ability findExistingAbility(GameEntity source, int abilityId, String parentName) {
        if (source == null) return null;
        int index = abilityId - 1;
        if (index >= 0 && index < source.getInstancedAbilities().size()) {
            var candidate = source.getInstancedAbilities().get(index);
            if (candidate != null
                    && (parentName == null || parentName.equals(candidate.getData().abilityName))) {
                return candidate;
            }
        }
        if (parentName == null) return null;
        for (var candidate : source.getInstancedAbilities()) {
            if (candidate != null && parentName.equals(candidate.getData().abilityName)) return candidate;
        }
        return source.getDynamicAbilities().get(parentName);
    }

    private void handleModifierChange(AbilityInvokeEntry invoke) throws Exception {

        var modChange = AbilityMetaModifierChange.parseFrom(invoke.getAbilityData());
        var head = invoke.getHead();

        if (head.getInstancedModifierId() > 2000) {
            return;
        }

        // is_serverbuff_modifier has no named counterpart in the 7.0 AbilityInvokeEntryHead.
        if (false) {

            this.player.getScene().broadcastPacket(new PacketAbilityInvocationsNotify(invoke));
            return;
        }

        var entity = this.player.getScene().getEntityById(invoke.getEntityId());
        if (entity == null) {
            if (DebugConstants.LOG_ABILITIES) {
                Grasscutter.getLogger().debug("Entity not found: {}", invoke.getEntityId());
            }

            return;
        }

        if (modChange.getAction() == ModifierAction.MODIFIER_ACTION_ADDED) {
            String resolvedParentName = null;
            var parentAbStr = modChange.getParentAbilityName();
            if (!parentAbStr.getStr().isEmpty()) {
                resolvedParentName = parentAbStr.getStr();
            } else if (parentAbStr.hasHash()) {
                resolvedParentName = GameData.getAbilityHashes().get(parentAbStr.getHash());
            }

            boolean fromParentName = resolvedParentName != null;
            var source = this.player.getScene().getEntityById(head.getTargetId());
            var instancedAbility = findExistingAbility(source, head.getInstancedAbilityId(), resolvedParentName);
            if (instancedAbility == null) {
                instancedAbility = findExistingAbility(entity, head.getInstancedAbilityId(), resolvedParentName);
            }
            if (instancedAbility == null && fromParentName) {
                for (var avatar : this.player.getTeamManager().getActiveTeam()) {
                    instancedAbility = findExistingAbility(avatar, head.getInstancedAbilityId(), resolvedParentName);
                    if (instancedAbility != null) break;
                }
            }
            var instancedAbilityData = instancedAbility != null ? instancedAbility.getData()
                    : (fromParentName ? GameData.getAbilityData(resolvedParentName) : null);

            if (instancedAbilityData == null) {
                Grasscutter.getLogger().trace("handleModifierChange: no ability data found for entityId={} parentAbility={}", invoke.getEntityId(), resolvedParentName);
                return;
            }

            if (instancedAbility == null) {
                var owner = source != null ? source : entity;
                var data = instancedAbilityData;
                instancedAbility = owner.getDynamicAbilities().computeIfAbsent(data.abilityName,
                        name -> new Ability(data, owner, player));
            }

            if (instancedAbilityData.modifiers == null) {
                Grasscutter.getLogger().trace("handleModifierChange: modifiers map is null for ability={} entityId={}", instancedAbilityData.abilityName, invoke.getEntityId());
                return;
            }
            var modifierArray = instancedAbilityData.modifiers.values().toArray();
            if (modChange.getModifierLocalId() < 0 || modChange.getModifierLocalId() >= modifierArray.length) {
                Grasscutter.getLogger().trace(
                    "handleModifierChange: modifierLocalId={} out of bounds for ability={} (modifierCount={}), entityId={} modId={}",
                    modChange.getModifierLocalId(), instancedAbilityData.abilityName, modifierArray.length,
                    invoke.getEntityId(), head.getInstancedModifierId());
                return;
            }

            var modifierData = (AbilityModifier) modifierArray[modChange.getModifierLocalId()];
            if (entity.getInstancedModifiers().containsKey(head.getInstancedModifierId())) {
                Grasscutter.getLogger()
                    .trace(
                        "Replacing entity {} modifier id {} with ability {} modifier {}",
                        invoke.getEntityId(),
                        head.getInstancedModifierId(),
                        instancedAbilityData.abilityName,
                        modifierData);
            } else {
                Grasscutter.getLogger()
                    .trace(
                        "Adding entity {} modifier id {} with ability {} modifier {}",
                        invoke.getEntityId(),
                        head.getInstancedModifierId(),
                        instancedAbilityData.abilityName,
                        modifierData);
            }

            onPossibleElementalBurst(instancedAbility, modifierData, invoke.getEntityId());

            boolean hasOrchestration = false;
            if (fromParentName && modifierData.onAdded != null) {
                for (var a : modifierData.onAdded) {
                    if (a != null && (a.type == AbilityModifierAction.Type.AttachModifier
                            || a.type == AbilityModifierAction.Type.ApplyModifier)) {
                        hasOrchestration = true;
                        break;
                    }
                }
            }

            AbilityModifierController modifier;
            AbilityModifierController previous;

            // Follow-up actions and removal messages refer to this client instance ID even
            // when onAdded applies another modifier (e.g. Nahida's release/camera chain).
            var instances = entity.getInstancedModifiers();
            synchronized (instances) {
                previous=instances.get(head.getInstancedModifierId());
                if(previous!=null && (previous.hasServerCallbacks() || previous.hasAttachmentCallbacks() || previous.hasBindingCallbacks() || previous.hasAnimationCallbacks()) && previous.getAbility()==instancedAbility
                        && previous.getModifierData()==modifierData) {
                    modifier=previous;
                } else {
                    modifier=entity.getAbilityHpModifier().pendingServerController(instancedAbility,
                            instancedAbilityData.modifiers.keySet().toArray(new String[0])[modChange.getModifierLocalId()]);
                    if (modifier == null) modifier = entity.getAbilityHpModifier().pendingCooldownController(instancedAbility,
                            instancedAbilityData.modifiers.keySet().toArray(new String[0])[modChange.getModifierLocalId()]);
                    if (modifier == null) modifier = entity.getAbilityHpModifier().pendingAttachmentController(instancedAbility,
                            instancedAbilityData.modifiers.keySet().toArray(new String[0])[modChange.getModifierLocalId()]);
                    if(modifier==null) {
                        modifier=new AbilityModifierController(instancedAbility,instancedAbilityData,modifierData);
                        modifier.inheritCooldownLedger(previous);
                    }
                }
                instances.put(head.getInstancedModifierId(), modifier);
                modifier.prepareAnimationCallbacks(entity);
                entity.getAbilityPeriodicHealing().clientAdded(head.getInstancedModifierId());
                // Register the replacement before refreshing its properties; intermediate state
                // can otherwise count both a pending server buff and its client confirmation.
                if (previous != null && previous != modifier) previous.getAbility().unregisterModifier(entity, previous, false);
                instancedAbility.registerModifier(entity, modifier);
            }
            if(previous!=null && previous!=modifier) previous.runServerOnRemoved();

            boolean initializedByWatcher = entity.getAbilityGlobalValueWatchers()
                    .ownsChild(instancedAbility, modifier.getName());
            boolean animationConfirmed = modifier.hasAnimationCallbacks();
            boolean initializedOnServer=modifier.hasServerCallbacks() || modifier.hasBindingCallbacks() || animationConfirmed;
            if (animationConfirmed) modifier.runAnimationOnAdded();
            if (!initializedOnServer && fromParentName && hasOrchestration && modifierData.onAdded != null) {
                final var finalAbility = instancedAbility;
                final var finalEntity = entity;
                var actions = Arrays.stream(modifierData.onAdded)
                        .filter(a -> a != null && (!initializedByWatcher || !isAllowedInServerOwnedChain(a.type)))
                        .toArray(AbilityModifierAction[]::new);
                AbilityCooldownInvocation.run(modifier, () ->
                        executeCallbackActionsNow(finalAbility, actions, invoke.getAbilityData(), finalEntity, finalEntity));
            } else if (!initializedOnServer && modifierData.onAdded != null && !initializedByWatcher) {
                // A modifier whose onAdded neither attaches nor applies another modifier used to run
                // nothing at all here, so anything the server alone is meant to do - spawning an
                // avatar's summon, most visibly - never happened. Only the state and spawn actions
                // are run: damage, healing and energy stay off this path, since the client already
                // resolves those and running them again would double them up.
                final var ownedAbility = instancedAbility;
                final var ownedEntity = entity;
                AbilityCooldownInvocation.run(modifier, () -> runServerOwned(
                    () -> {
                        var actions = Arrays.stream(modifierData.onAdded)
                                .filter(a -> a != null && SERVER_OWNED_ON_ADDED.contains(a.type))
                                .toArray(AbilityModifierAction[]::new);
                        executeCallbackActionsNow(ownedAbility, actions, invoke.getAbilityData(), ownedEntity, ownedEntity);
                    }));
            }
            modifier.runAttachmentOnAdded(()->true);
            runVehicleAttachment(modifier, entity, invoke.getAbilityData());
        } else if (modChange.getAction() == ModifierAction.MODIFIER_ACTION_REMOVED) {
            var instances = entity.getInstancedModifiers();
            AbilityModifierController modifier;
            synchronized (instances) {
                modifier = instances.remove(head.getInstancedModifierId());
                if (modifier != null) {
                    entity.getAbilityPeriodicHealing().clientRemoved(head.getInstancedModifierId(),modifier);
                }
            }
            if (modifier != null) {
                var removed = modifier;
                if (!removed.hasServerCallbacks() && !removed.hasBindingCallbacks() && !removed.hasAnimationCallbacks() && removed.getModifierData().onRemoved != null)
                    AbilityCooldownInvocation.run(new AbilityCooldownInvocation.Scope(removed, true), () ->
                            runServerOwned(() -> executeCallbackActionsNow(removed.getAbility(),
                                    removed.getModifierData().onRemoved, invoke.getAbilityData(), entity, entity)));
                modifier.getAbility().unregisterModifier(entity, modifier);
            }
        } else {

            Grasscutter.getLogger().debug("Unknown action");
        }
        entity.getAbilityGlobalValueWatchers().flushPending();
    }

    private void runVehicleAttachment(AbilityModifierController controller,GameEntity target,ByteString payload) {
        var mixins=controller.getModifierData().modifierMixins;
        if(mixins==null || Arrays.stream(mixins).noneMatch(m -> m!=null
                && m.type==AbilityMixinData.Type.TryEnterVehicleMixin)
                || !controller.startVehicleAttachment()) return;
        for(var mixin:mixins)
            if(mixin!=null && (mixin.type==AbilityMixinData.Type.TryEnterVehicleMixin
                    || mixin.type==AbilityMixinData.Type.DoActionOnVehicleInteractPostMixin))
                executeMixin(controller.getAbility(),mixin,payload,target);
    }

    private void handleMixinCostStamina(AbilityInvokeEntry invoke)
        throws InvalidProtocolBufferException {
    }

    private void handleGenerateElemBall(AbilityInvokeEntry invoke)
        throws InvalidProtocolBufferException {
        this.player.getEnergyManager().handleGenerateElemBall(invoke);
    }

    private void handleGlobalFloatValue(AbilityInvokeEntry invoke)
        throws InvalidProtocolBufferException {
        var entity = this.player.getScene().getEntityById(invoke.getEntityId());
        if (entity == null) return;

        var entry = AbilityScalarValueEntry.parseFrom(invoke.getAbilityData());
        if (entry == null) return;

        String key = resolveGlobalValueKey(entry.getKey(), entity);

        if (key == null || key.isEmpty()) return;
        if (key.startsWith("SGV_")) return;

        float value = entry.getFloatValue();
        if (!Float.isFinite(value)) return;

        if (key.equals("MoonOvergrowPoint_All")) {
            value = 50f;
            this.player.sendPacket(new PacketServerGlobalValueChangeNotify(entity, key, 50f));
        }

        if ("_Arlecchino_HPDebtsMark_Level".equals(key)) {
            Float prev = entity.getGlobalAbilityValues().get(key);
            if (value == 0f && prev != null && prev > 0f) {

                boolean isChargedAttack = arlecchinoChargedAttackTime > arlecchinoESkillTime
                    && (System.currentTimeMillis() - arlecchinoChargedAttackTime) < 5000L;
                if (isChargedAttack) {
                    for (var teamMember : this.player.getTeamManager().getActiveTeam()) {
                        if (teamMember.getAvatar().getAvatarId() == 10000096) {
                            applyArlecchinoBoL(teamMember, prev.intValue());
                            break;
                        }
                    }
                }
            }
        }

        entity.getGlobalAbilityValues().put(key, value);
        entity.onAbilityValueUpdate();
    }

    private String resolveGlobalValueKey(AbilityString value, GameEntity entity) {
        if (value.hasStr()) return value.getStr();
        if (!value.hasHash()) return null;
        int hash = value.getHash();
        var candidates = new LinkedHashSet<String>(entity.getGlobalAbilityValues().keySet());
        candidates.addAll(entity.getAbilityHpModifier().getReferencedKeys());
        candidates.addAll(entity.getAbilityGlobalValueWatchers().getReferencedKeys());
        String legacy = GameData.getAbilityHashes().get(hash);
        if (legacy != null) candidates.add(legacy);
        String resolved = null;
        for (var key : candidates) {
            if (key == null || key.isEmpty() || Utils.abilityHash(key) != hash) continue;
            // A hash cannot distinguish two names. Wait for an explicit string instead of guessing.
            if (resolved != null && !resolved.equals(key)) return null;
            resolved = key;
        }
        return resolved;
    }

    private float getArlecchinoBoLRatio(Avatar avatar, String varName) {
        var depot = GameData.getAvatarSkillDepotDataMap().get(avatar.getSkillDepotId());
        if (depot == null) return 0f;
        var skillLevelMap = avatar.getEffectiveSkillLevelMap();
        var result = new float[]{0f};

        depot.getSkillsAndEnergySkill().forEach(skillId -> {
            if (result[0] > 0f) return;
            var skillData = GameData.getAvatarSkillDataMap().get(skillId);
            if (skillData == null || skillData.getProudSkillGroupId() == 0) return;
            int level = skillLevelMap.getOrDefault(skillId, 1);
            var proudSkill = GameData.getProudSkillDataMap().get(skillData.getProudSkillGroupId() * 100 + level);
            if (proudSkill == null || proudSkill.getOpenConfig() == null) return;
            var entry = GameData.getOpenConfigEntries().get(proudSkill.getOpenConfig());
            if (entry == null || entry.getAbilityVarSetters() == null) return;
            float[] params = proudSkill.getParamList();
            if (params == null) return;
            for (var setter : entry.getAbilityVarSetters()) {
                if (!varName.equals(setter.getVarName())) continue;
                Float value = setter.resolve(params);
                if (value != null) { result[0] = value; return; }
            }
        });
        if (result[0] > 0f) return result[0];

        if (depot.getInherentProudSkillOpens() != null) {
            for (var inherent : depot.getInherentProudSkillOpens()) {
                if (result[0] > 0f) break;
                if (inherent.getProudSkillGroupId() == 0) continue;
                var proudSkill = GameData.getProudSkillDataMap().get(inherent.getProudSkillGroupId() * 100 + 1);
                if (proudSkill == null || proudSkill.getOpenConfig() == null) continue;
                var entry = GameData.getOpenConfigEntries().get(proudSkill.getOpenConfig());
                if (entry == null || entry.getAbilityVarSetters() == null) continue;
                float[] params = proudSkill.getParamList();
                if (params == null) continue;
                for (var setter : entry.getAbilityVarSetters()) {
                    if (!varName.equals(setter.getVarName())) continue;
                    Float value = setter.resolve(params);
                    if (value != null) { result[0] = value; break; }
                }
            }
        }
        return result[0];
    }

    public void flushPendingBoL() {

    }

    public void onArlecchinoSkillNotify(int skillId) {
        if (skillId == 10961) {
            arlecchinoChargedAttackTime = System.currentTimeMillis();
        } else if (skillId == 10962) {
            arlecchinoESkillTime = System.currentTimeMillis();
        }
    }

    private void applyArlecchinoBoL(EntityAvatar arlecchino, int markLevel) {
        String varName = markLevel >= 2 ? "HpDebts_Level_2_Ratio" : "HpDebts_Level_1_Ratio";
        float ratio = getArlecchinoBoLRatio(arlecchino.getAvatar(), varName);
        if (ratio <= 0f) return;

        float maxHp = arlecchino.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
        float debt = ratio * maxHp;
        float curDebt = arlecchino.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
        float newDebt = Math.min(curDebt + debt, 2f * maxHp);
        float change = newDebt - curDebt;
        if (change <= 0f) return;

        arlecchino.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, newDebt);
        var scene = this.player.getScene();
        scene.broadcastPacket(new PacketEntityFightPropUpdateNotify(arlecchino, FightProperty.FIGHT_PROP_CUR_HP_DEBTS));
        scene.broadcastPacket(new PacketEntityFightPropChangeReasonNotify(
            arlecchino,
            FightProperty.FIGHT_PROP_CUR_HP_DEBTS,
            change,
            PropChangeReasonOuterClass.PropChangeReason.PropChangeReason_PROP_CHANGE_ABILITY,
            ChangeHpDebtsReasonOuterClass.ChangeHpDebtsReason.CHANGE_HP_DEBTS_REASON_CHANGE_HP_DEBTS_ADD_ABILITY
        ));
    }

    private void addAbilityByHash(EntityAvatar entity, int hash) {
        var name = GameData.getAbilityHashes().get(hash);
        var data = name != null ? GameData.getAbilityData(name) : null;
        entity.getInstancedAbilities().add(data != null ? new Ability(data, entity, player) : null);
    }

    private void initializeEntityAbilities(EntityAvatar avatar) {
        var avatarData = avatar.getAvatar().getAvatarData();
        if (avatarData.getAbilities() != null) {
            for (int hash : avatarData.getAbilities()) addAbilityByHash(avatar, hash);
        }
        for (int hash : emu.grasscutter.GameConstants.defaultAbilityHashes()) {
            addAbilityByHash(avatar, hash);
        }
        for (int hash : player.getTeamManager().getTeamResonancesConfig()) {
            addAbilityByHash(avatar, hash);
        }
        var skillDepot = GameData.getAvatarSkillDepotDataMap().get(avatar.getAvatar().getSkillDepotId());
        if (skillDepot != null && skillDepot.getAbilities() != null) {
            for (int hash : skillDepot.getAbilities()) addAbilityByHash(avatar, hash);
        }
        for (String name : avatar.getAvatar().getExtraAbilityEmbryos()) {
            var data = GameData.getAbilityData(name);
            avatar.getInstancedAbilities().add(data != null ? new Ability(data, avatar, player) : null);
        }
        avatar.getAbilityGlobalValueWatchers().refresh();
    }

    private void invokeAction(
        AbilityModifierAction action, GameEntity target, GameEntity sourceEntity) {
    }

    private void handleModifierDurabilityChange(AbilityInvokeEntry invoke)
        throws InvalidProtocolBufferException {
    }

    private static volatile Set<Integer> moonLightAbilityHashes;

    private static Set<Integer> getMoonLightAbilityHashes() {
        Set<Integer> s = moonLightAbilityHashes;
        if (s == null) {
            s = new HashSet<>();
            for (var e : GameData.getAbilityHashes().int2ObjectEntrySet()) {
                String name = e.getValue();
                if (name != null && name.startsWith("Avatar_") && name.endsWith("_MoonLight")) {
                    s.add(e.getIntKey());
                }
            }
            moonLightAbilityHashes = s;
        }
        return s;
    }

    private void handleAddNewAbility(AbilityInvokeEntry invoke)
        throws InvalidProtocolBufferException {
        var entity = this.player.getScene().getEntityById(invoke.getEntityId());

        if (entity == null) {
            Grasscutter.getLogger().trace("handleAddNewAbility: entity not found: {}", invoke.getEntityId());
            return;
        }

        var addAbility = AbilityMetaAddAbility.parseFrom(invoke.getAbilityData());
        var abString = addAbility.getAbility().getAbilityName();
        var abilityName = Ability.getAbilityName(abString);
        var abilityData = GameData.getAbilityData(abilityName);

        var abilities = entity.getInstancedAbilities();
        int targetIndex = invoke.getHead().getInstancedAbilityId() - 1;
        if (targetIndex < 0) return;

        while (abilities.size() < targetIndex) abilities.add(null);

        int abHash = abString.hasHash() ? abString.getHash() : 0;
        boolean isTeamMoonPhase = "TeamAbility_MoonPhase".equals(abilityName)
            || abHash == Utils.abilityHash("TeamAbility_MoonPhase");
        boolean isMoonLightAbility =
            (abilityName != null && abilityName.startsWith("Avatar_") && abilityName.endsWith("_MoonLight"))
            || getMoonLightAbilityHashes().contains(abHash);

        // Repeated here: the client only wires the Moonsign up once its ability exists.
        if (isTeamMoonPhase || isMoonLightAbility) {
            var teamManager = this.player.getTeamManager();
            teamManager.sendMoonsignState();
            log.debug("{} loaded: re-sent Moonsign level={} to team entity={}",
                abilityName, teamManager.getMoonsignLevel(), teamManager.getEntity().getId());
        }

        if (abilityData == null) {
            Grasscutter.getLogger().trace(
                "handleAddNewAbility: ability data not found (stub at {}): entity={} name={} hash={}",
                targetIndex, entity.getId(), abilityName,
                abString.hasHash() ? abString.getHash() : 0);
            if (targetIndex == abilities.size()) abilities.add(null);
            else if (targetIndex < abilities.size() && abilities.get(targetIndex) == null) abilities.set(targetIndex, null);
            return;
        }

        // The wire ID selects a slot. Appending when that slot already exists shifts
        // later dynamic abilities away from the IDs used by release/hit invocations.
        var newAbility = targetIndex < abilities.size() ? abilities.get(targetIndex) : null;
        if (newAbility == null || !abilityName.equals(newAbility.getData().abilityName)) {
            newAbility = new Ability(abilityData, entity, player);
        }
        if (targetIndex == abilities.size()) {
            abilities.add(newAbility);
        } else {
            abilities.set(targetIndex, newAbility);
        }
        for (var override : addAbility.getAbility().getOverrideMapList()) {
            setAbilityOverrideValue(newAbility, override);
        }

        if (entity instanceof EntityClientGadget || entity instanceof emu.grasscutter.game.entity.EntityWeapon) {
            var overrides = new ArrayList<AbilityInvokeEntry>();
            var values = resolveTalentOverrides(newAbility);
            for (var entry : values.entrySet()) {
                newAbility.getAbilitySpecials().put(entry.getKey(), entry.getValue());
                overrides.add(talentOverrideInvoke(entity, targetIndex + 1, entry.getKey(), entry.getValue()));
            }
            if (!overrides.isEmpty()) player.sendPacket(new PacketAbilityInvocationsNotify(overrides));
        }
        newAbility.refreshModifierTargets(true);
    }

    private void handleKillState(AbilityInvokeEntry invoke) throws InvalidProtocolBufferException {
        var scene = this.getPlayer().getScene();
        var entity = scene.getEntityById(invoke.getEntityId());
        if (entity == null) {
            Grasscutter.getLogger()
                .trace("Entity of ID {} was not found in the scene.", invoke.getEntityId());
            return;
        }

        var killState = AbilityMetaSetKilledState.parseFrom(invoke.getAbilityData());
        if (killState.getKilled()) {
            if (!(entity instanceof EntityAvatar) && !(entity instanceof EntityClientGadget)) {
                scene.killEntity(entity);
            }
        } else if (!entity.isAlive()) {
            if (entity instanceof EntityAvatar) {

                Grasscutter.getLogger()
                    .trace("Entity of ID {} is EntityAvatar. Ignoring", invoke.getEntityId());
                return;
            }
            // Entities with no combat state of their own used to answer null here; they now answer
            // an empty map, and reviving something that never had hit points is still meaningless.
            if (entity.getFightProperties().isEmpty()) return;
            entity.setFightProperty(
                FightProperty.FIGHT_PROP_CUR_HP,
                entity.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP));
        }
    }

    public Map<String, Float> computeGadgetVarOverrides(EntityClientGadget gadget) {
        var result = new LinkedHashMap<String, Float>();
        var avatar = AbilityTalentResolver.casterAvatar(player, gadget);
        if (avatar == null) return result;
        var names = new LinkedHashSet<String>();
        if (gadget.getConfigGadget() != null && gadget.getConfigGadget().getAbilities() != null)
            for (var config : gadget.getConfigGadget().getAbilities())
                if (config != null && config.getAbilityName() != null) names.add(config.getAbilityName());
        for (var ability : gadget.getInstancedAbilities())
            if (ability != null) names.add(ability.getData().abilityName);
        var data = GameData.getGadgetDataMap().get(gadget.getGadgetId());
        if (names.isEmpty() && data != null && data.getJsonName() != null)
            names.add("Bullet_" + data.getJsonName());
        for (var name : names) result.putAll(AbilityTalentResolver.overrides(avatar, name));
        return result;
    }

    public void addAbilityToEntity(GameEntity entity, String name) {
        AbilityData data = GameData.getAbilityData(name);
        if (data != null) addAbilityToEntity(entity, data);
    }

    public void addAbilityToEntity(GameEntity entity, AbilityData abilityData) {
        var ability = new Ability(abilityData, entity, this.player);
        entity.getInstancedAbilities().add(ability);
        fireAbilityOnAdded(ability, entity);
    }

    /** Trigger the caster's instance, retaining onAdded state when the same ability runs again. */
    public boolean triggerAbility(GameEntity entity, String name) {
        if (entity == null || name == null || name.isBlank()) return false;
        if (!AbilityExecutionContext.canTrigger(entity, this.player)) return false;
        Ability selected;
        boolean created = false;
        boolean controlled = AbilityActionSequence.isRunning();
        Set<AbilityModifierAction> controlledCallbacks = Collections.newSetFromMap(new IdentityHashMap<>());
        var instances = entity.getInstancedAbilities();
        synchronized (instances) {
            selected = instances.stream()
                    .filter(a -> a.getPlayerOwner() == this.player && name.equals(a.getData().abilityName))
                    .findFirst().orElse(null);
            if (selected == null) {
                var data = GameData.getAbilityData(name);
                if (data == null) return false;
                if (controlled && !collectTriggerCallbacks(data, controlledCallbacks)) return false;
                selected = new Ability(data, entity, this.player);
                selected.prepareTriggerInitialization();
                instances.add(selected);
                created = true;
            } else if (controlled && !collectTriggerCallbacks(selected.getData(), controlledCallbacks)) {
                return false;
            }
        }
        var instance = selected;
        if (controlled) instance.claimServerTriggerCallbacks(controlledCallbacks, entity);
        var context = new AbilityExecutionContext(instance, entity);
        var ownedChain = isServerOwnedChain() || controlled;
        var healTag = AbilityHealContext.currentTag();
        // Continuations do not wait or retain entity-list locks during nested cross-entity triggers.
        instance.getTriggerInitialization().thenRun(() -> AbilityHealContext.run(healTag, () -> runServerOwned(ownedChain, () -> {
            if (instance.getData().onAbilityStart == null) return;
            context.runIfCurrent(() -> {
                if (controlled) AbilityActionSequence.run(instance, instance.getData().onAbilityStart, entity, context::isCurrent);
                else executeCallbackActionsNow(instance, instance.getData().onAbilityStart, ByteString.EMPTY, entity, entity);
            });
        }))).exceptionally(error -> {
            Grasscutter.getLogger().error("Triggered ability {} failed during initialization/start", name, error);
            return null;
        });
        if (created) {
            try {
                runServerOwned(ownedChain, () -> fireAbilityOnAdded(instance, entity, true, controlled));
                instance.getTriggerInitialization().complete(null);
            } catch (RuntimeException | Error error) {
                instance.getTriggerInitialization().completeExceptionally(error);
                throw error;
            }
        }
        return true;
    }

    private static boolean collectTriggerCallbacks(AbilityData data, Set<AbilityModifierAction> output) {
        var added = AbilityActionSequence.callbacks(data, data.onAdded);
        var started = AbilityActionSequence.callbacks(data, data.onAbilityStart);
        var defaultModifier = data.modifiers == null ? null : data.modifiers.get("Default");
        var defaults = AbilityActionSequence.callbacks(data, defaultModifier == null ? null : defaultModifier.onAdded);
        if (added == null || started == null || defaults == null) return false;
        output.addAll(added); output.addAll(started); output.addAll(defaults);
        return true;
    }

    private void fireAbilityOnAdded(Ability ability, GameEntity entity) {
        fireAbilityOnAdded(ability, entity, false);
    }

    private void fireAbilityOnAdded(Ability ability, GameEntity entity, boolean immediate) {
        fireAbilityOnAdded(ability, entity, immediate, false);
    }

    private void fireAbilityOnAdded(Ability ability, GameEntity entity, boolean immediate, boolean controlled) {
        var data = ability.getData();
        if (data == null) return;

        var actions = new ArrayList<AbilityModifierAction>();
        if (data.onAdded != null) Collections.addAll(actions, data.onAdded);

        if (data.modifiers != null) {
            var defaultMod = data.modifiers.get("Default");
            if (defaultMod != null && defaultMod.onAdded != null) {
                Collections.addAll(actions, defaultMod.onAdded);
            }
        }
        var sequence = actions.toArray(AbilityModifierAction[]::new);
        if (controlled) {
            var context = new AbilityExecutionContext(ability, entity);
            AbilityActionSequence.run(ability, sequence, entity, context::isCurrent);
        } else if (immediate) executeCallbackActionsNow(ability, sequence, ByteString.EMPTY, entity, entity);
        else queueCallbackActions(ability, sequence, ByteString.EMPTY, entity, AbilityHealContext.currentTag());
        entity.getAbilityGlobalValueWatchers().refresh();
    }
}
