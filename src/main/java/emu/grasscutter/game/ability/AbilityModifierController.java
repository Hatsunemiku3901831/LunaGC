package emu.grasscutter.game.ability;

import emu.grasscutter.data.binout.*;
import lombok.Getter;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.net.proto.AbilityInvokeEntryHeadOuterClass.AbilityInvokeEntryHead;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.net.proto.AbilityMetaModifierChangeOuterClass.AbilityMetaModifierChange;
import emu.grasscutter.utils.Utils;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

public class AbilityModifierController {
    private static final Object ATTACHMENTS = new Object();
    private record Child(AbilityModifierController parent, GameEntity target, AbilityModifierController controller) {}
    private final Set<Child> attachedChildren=new java.util.HashSet<>();
    private volatile Child attachment;
    private volatile boolean attachmentRevoked, attachmentsEnding;
    private boolean auraDuration, intervalDuration;

    boolean ownsAuraDuration() { return auraDuration; }
    boolean ownsServerDuration() { return auraDuration || intervalDuration || attachment!=null && AbilityPartyAura.listener(modifierData); }
    boolean ownsIntervalDuration() { return intervalDuration; }

    boolean intervalCurrent(GameEntity target) {
        var callbacks=serverCallbacks;
        return intervalDuration && callbacks!=null && callbacks.target==target
                && !callbacks.ended.get() && callbacks.context.isCurrent()
                && target.getAbilityModifierDuration().withinLifetime(this);
    }

    boolean auraCurrent() {
        var callbacks=serverCallbacks;
        var player=ability.getPlayerOwner();
        if(!auraDuration || callbacks==null || attachmentsEnding || !callbacks.context.isCurrent()
                || !(callbacks.target instanceof emu.grasscutter.game.entity.EntityTeam team)
                || player==null || player.getTeamManager().getEntity()!=team || player.getScene()==null
                || player.getWorld()!=player.getScene().getWorld()
                || player.getScene().getScriptManager().isDestroyed()
                || player.getWorld().getScenes().get(player.getScene().getId())!=player.getScene()) return false;
        return ability.getOwner().getInstancedAbilities().contains(ability)
                && callbacks.target.getAbilityModifierDuration().withinLifetime(this);
    }

    void closeAuraAttachments() { if(auraDuration) synchronized(ATTACHMENTS) { attachmentsEnding=true; } }

    boolean attachmentCurrent() {
        var binding=attachment;
        if(binding==null) return true;
        var parent=binding.parent;
        return !attachmentRevoked && parent.auraCurrent();
    }

    void attachChild(GameEntity target, AbilityModifierController child) {
        if(child==this) return;
        boolean reject;
        synchronized(target.getInstancedModifiers()) {
            if(!target.getAbilityHpModifier().activeControllers().contains(child)) return;
            synchronized(ATTACHMENTS) {
                reject=!auraCurrent();
                if(!reject) {
                    var old=child.attachment;
                    if(old!=null) old.parent.attachedChildren.remove(old);
                    var binding=new Child(this,target,child);
                    child.attachment=binding;child.attachmentRevoked=false;attachedChildren.add(binding);
                }
            }
        }
        if(reject) child.getAbility().removeModifierInstanceIf(target,child,true,()-> {
            synchronized(ATTACHMENTS) { return child.attachment==null || child.attachment.parent==this; }
        });
    }

    private void removeAttachedChildren() {
        java.util.List<Child> snapshot;
        synchronized(ATTACHMENTS) {
            attachmentsEnding=true;
            var own=attachment;
            if(own!=null) { own.parent.attachedChildren.remove(own); attachmentRevoked=true; }
            snapshot=new java.util.ArrayList<>(attachedChildren);attachedChildren.clear();
        }
        for(var child:snapshot) child.controller.getAbility().removeModifierInstanceIf(child.target,child.controller,true,()-> {
            synchronized(ATTACHMENTS) {
                if(child.controller.attachment!=child) return false;
                child.controller.attachmentRevoked=true; return true;
            }
        });
    }
    @Getter private Ability ability;

    @Getter private AbilityData abilityData;
    @Getter private AbilityModifier modifierData;
    @Getter private final String name;
    private record ClientInstance(
            GameEntity target, AbilityExecutionContext context, AbilityInvokeEntry addition) {}
    private volatile ClientInstance clientInstance;
    private record Application(GameEntity target, AbilityExecutionContext context, boolean installedRoot) {}
    private volatile Application application;

    /** Server registration can precede the first client ID; confirmation retains that lifetime. */
    synchronized void bindApplication(GameEntity target) {
        if (application != null) return;
        application = new Application(target, new AbilityExecutionContext(ability, target),
                ability.getOwner().getInstancedAbilities().contains(ability));
    }

    boolean isClientInstanceCurrent(GameEntity target) {
        if (target instanceof emu.grasscutter.game.entity.EntityGadget gadget
                && gadget.getQuartzOreCycle() != null
                && !gadget.getQuartzOreCycle().applicationCurrent(this)) return false;
        var bound = application;
        if (bound != null && (bound.target != target || !bound.context.isCurrent()
                || bound.installedRoot && !ability.getOwner().getInstancedAbilities().contains(ability))) return false;
        var instance = clientInstance;
        return instance == null || instance.target == target && instance.context.isCurrent();
    }

    boolean isApplicationCurrent() {
        var bound = application;
        if (bound != null) return bound.context.isCurrent();
        var instance = clientInstance;
        return instance == null || isClientInstanceCurrent(instance.target);
    }

    /** Removal callbacks retain the entity lifetime when their root has already been uninstalled. */
    boolean isApplicationCurrent(GameEntity target) {
        var bound = application;
        if (bound != null) return bound.target == target && bound.context.isCurrent();
        return isClientInstanceCurrent(target);
    }

    boolean isDuplicateClientAdd(GameEntity target, AbilityInvokeEntry addition) {
        var instance = clientInstance;
        double time = addition.getTotalTickTime();
        return instance != null && isClientInstanceCurrent(target)
                && Double.isFinite(time) && time > 0 && instance.addition.equals(addition);
    }

    void bindClientInstance(GameEntity target, AbilityInvokeEntry addition) {
        clientInstance = new ClientInstance(
                target, new AbilityExecutionContext(ability, target, false, true), addition);
    }

    boolean matchesClientRemoval(
            AbilityInvokeEntryHead head, AbilityMetaModifierChange change) {
        int root = head.getInstancedAbilityId();
        var owner = ability.getOwner();
        if (root != 0) {
            var roots = owner.getInstancedAbilities();
            if (root > 0 && root <= roots.size() && roots.get(root - 1) != null) {
                if (roots.get(root - 1) != ability) return false;
            } else if (owner.getDynamicAbilities().get(abilityData.abilityName) != ability) return false;
        }
        var parent = change.getParentAbilityName();
        return !change.hasParentAbilityName()
                || parent.hasStr()
                        && (parent.getStr().isEmpty() || parent.getStr().equals(abilityData.abilityName))
                || parent.hasHash() && parent.getHash() == Utils.abilityHash(abilityData.abilityName);
    }

    @Getter private long skillSlotSourceOrder = AbilitySkillSlots.nextSourceOrder();
    private volatile ServerCallbacks serverCallbacks;
    private volatile ModifierAttachmentCallbacks attachments;
    private volatile BindingModifierCallbacks bindingCallbacks;
    private volatile AnimationModifierCallbacks animationCallbacks;
    void prepareAnimationCallbacks(GameEntity target) {
        if (animationCallbacks == null && !hasServerCallbacks() && !hasBindingCallbacks())
            animationCallbacks = AnimationModifierCallbacks.create(this, target);
    }
    boolean hasAnimationCallbacks() { var callbacks = animationCallbacks; return callbacks != null && callbacks.current(); }
    boolean ownsAnimationCallback(AbilityModifier.AbilityModifierAction action) {
        var callbacks = animationCallbacks; return callbacks != null && callbacks.owns(action);
    }
    void runAnimationOnAdded() { var callbacks = animationCallbacks; if (callbacks != null) callbacks.added(); }
    void animationParentRemoved(AbilityModifierController parent) {
        var callbacks = animationCallbacks; if (callbacks != null) callbacks.parentRemoved(parent);
    }
    void prepareBindingCallbacks(GameEntity target) { bindingCallbacks = new BindingModifierCallbacks(this, target); }
    public boolean hasBindingCallbacks() { var callbacks = bindingCallbacks; return callbacks != null && callbacks.available(); }
    void runBindingOnAdded(BooleanSupplier current) { var callbacks = bindingCallbacks; if (callbacks != null) callbacks.added(current); }
    private final java.util.Queue<AbilityModifierController> attachmentReplacements=new java.util.concurrent.ConcurrentLinkedQueue<>();
    void deferAttachmentReplacement(AbilityModifierController previous) { attachmentReplacements.add(previous); }
    private void finishAttachmentReplacements() {
        for(AbilityModifierController old;(old=attachmentReplacements.poll())!=null;) old.runServerOnRemoved();
    }
    synchronized void prepareAttachmentCallbacks(GameEntity target) {
        var mixins=modifierData.modifierMixins;
        if(mixins==null || java.util.Arrays.stream(mixins).noneMatch(m -> m!=null
                && m.type==AbilityMixinData.Type.AttachActionToModifierMixin)) return;
        if(attachments==null || !attachments.available()) attachments=new ModifierAttachmentCallbacks(this,target);
        else if(!attachments.forTarget(target)) throw new IllegalArgumentException("Modifier controller has another bearer");
    }
    boolean hasAttachmentCallbacks() { var hooks=attachments; return hooks!=null && hooks.available(); }
    void runAttachmentOnAdded(BooleanSupplier current) {
        finishAttachmentReplacements();
        var hooks=attachments; if(hooks!=null) hooks.added(current);
    }
    private final Set<AbilityModifier.AbilityModifierAction> cooldownCallbacks;
    private AbilityCooldownInvocation.Ledger cooldownLedger = new AbilityCooldownInvocation.Ledger();
    private AtomicBoolean vehicleAttachment = new AtomicBoolean();
    boolean startVehicleAttachment() { return vehicleAttachment.compareAndSet(false,true); }

    AbilityCooldownInvocation.Ledger getCooldownLedger() { return cooldownLedger; }

    boolean ownsCooldownCallback(AbilityModifier.AbilityModifierAction action) {
        return cooldownCallbacks.contains(action);
    }

    boolean hasCooldownCallbacks() { return !cooldownCallbacks.isEmpty(); }

    void inheritCooldownLedger(AbilityModifierController previous, GameEntity target) {
        if (previous != null && previous.ability == ability && previous.modifierData == modifierData
                && previous.isClientInstanceCurrent(target)) {
            cooldownLedger = previous.cooldownLedger;
            vehicleAttachment = previous.vehicleAttachment;
            skillSlotSourceOrder = previous.skillSlotSourceOrder;
        }
    }
    private static final AtomicLong applicationSequence=new AtomicLong();
    private static final class ServerCallbacks {
        final GameEntity target;
        final AbilityExecutionContext context;
        final Set<AbilityModifier.AbilityModifierAction> added,removed;
        final AtomicBoolean started=new AtomicBoolean(),ended=new AtomicBoolean();
        volatile long applicationOrder=applicationSequence.incrementAndGet();
        ServerCallbacks(Ability ability, GameEntity target, Set<AbilityModifier.AbilityModifierAction> added,
                        Set<AbilityModifier.AbilityModifierAction> removed) {
            this.target=target; this.context=new AbilityExecutionContext(ability,target);
            this.added=added; this.removed=removed;
        }
    }

    boolean prepareServerCallbacks(GameEntity target) {
        var added=AbilityActionSequence.callbacks(abilityData,modifierData.onAdded);
        var removed=AbilityActionSequence.callbacks(abilityData,modifierData.onRemoved);
        if(added==null || removed==null) return false;
        var callbacks=new ServerCallbacks(ability,target,added,removed);
        if(!callbacks.context.isCurrent()) return false;
        auraDuration=AbilityPartyAura.window(ability,modifierData,target);
        intervalDuration=AbilityModifierInterval.supported(ability,modifierData,name,target);
        serverCallbacks=callbacks;
        return true;
    }
    public boolean hasServerCallbacks() {
        var callbacks=serverCallbacks;
        return callbacks!=null && callbacks.context.isCurrent();
    }
    long serverApplicationOrder() {
        var callbacks=serverCallbacks;
        return callbacks==null?Long.MAX_VALUE:callbacks.applicationOrder;
    }
    void refreshServerApplication() {
        var callbacks=serverCallbacks;
        if(callbacks!=null && !callbacks.ended.get() && callbacks.context.isCurrent())
            callbacks.applicationOrder=applicationSequence.incrementAndGet();
    }
    boolean ownsServerCallback(AbilityModifier.AbilityModifierAction action) {
        var callbacks=serverCallbacks;
        return callbacks!=null && callbacks.context.isCurrent()
                && (callbacks.added.contains(action) || callbacks.removed.contains(action));
    }
    void runServerOnAdded(BooleanSupplier parentCurrent) {
        finishAttachmentReplacements();
        var callbacks=serverCallbacks;
        if(callbacks==null || !callbacks.started.compareAndSet(false,true)) return;
        AbilityCooldownInvocation.run(this, () -> AbilityActionSequence.run(ability,modifierData.onAdded,callbacks.target,()-> {
            if(!parentCurrent.getAsBoolean() || !callbacks.context.isCurrent() || callbacks.ended.get()) return false;
            synchronized(callbacks.target.getInstancedModifiers()) {
                return callbacks.target.getAbilityHpModifier().activeControllers().contains(this);
            }
        }));
        runAttachmentOnAdded(parentCurrent);
    }
    public void runServerOnRemoved() {
        AbilityGlobalValueWatchers.runRemovalCallbacks(this::runRemovalCallbacks);
    }
    private void runRemovalCallbacks() {
        removeAttachedChildren();
        var callbacks=serverCallbacks;
        if(callbacks!=null && callbacks.ended.compareAndSet(false,true))
            AbilityCooldownInvocation.run(this, () -> AbilityActionSequence.run(ability,modifierData.onRemoved,callbacks.target,callbacks.context::isCurrent));
        var binding=bindingCallbacks; if(binding!=null) binding.removed();
        var hooks=attachments; if(hooks!=null) hooks.removed();
        var animation=animationCallbacks; if(animation!=null) animation.removed();
    }

    public AbilityModifierController(
            Ability ability, AbilityData abilityData, AbilityModifier modifierData) {
        this.ability = ability;
        this.abilityData = abilityData;
        this.modifierData = modifierData;
        this.cooldownCallbacks = AbilityCooldownInvocation.callbacks(modifierData.onAdded, modifierData.onRemoved);
        this.name = abilityData.modifiers.entrySet().stream()
                .filter(entry -> entry.getValue() == modifierData)
                .map(java.util.Map.Entry::getKey).findFirst().orElse("");
    }
}
