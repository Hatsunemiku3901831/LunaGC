package emu.grasscutter.game.ability;

import emu.grasscutter.data.binout.*;
import lombok.Getter;
import emu.grasscutter.game.entity.GameEntity;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

public class AbilityModifierController {
    @Getter private Ability ability;

    @Getter private AbilityData abilityData;
    @Getter private AbilityModifier modifierData;
    @Getter private final String name;
    @Getter private long skillSlotSourceOrder = AbilitySkillSlots.nextSourceOrder();
    private volatile ServerCallbacks serverCallbacks;
    private volatile ModifierAttachmentCallbacks attachments;
    private volatile BindingModifierCallbacks bindingCallbacks;
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

    void inheritCooldownLedger(AbilityModifierController previous) {
        if (previous != null && previous.ability == ability && previous.modifierData == modifierData) {
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
        serverCallbacks=callbacks; return true;
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
        var callbacks=serverCallbacks;
        if(callbacks!=null && callbacks.ended.compareAndSet(false,true))
            AbilityCooldownInvocation.run(this, () -> AbilityActionSequence.run(ability,modifierData.onRemoved,callbacks.target,callbacks.context::isCurrent));
        var binding=bindingCallbacks; if(binding!=null) binding.removed();
        var hooks=attachments; if(hooks!=null) hooks.removed();
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
