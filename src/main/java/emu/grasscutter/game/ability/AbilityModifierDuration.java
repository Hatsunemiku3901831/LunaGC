package emu.grasscutter.game.ability;

import com.google.protobuf.ByteString;
import emu.grasscutter.game.ability.actions.AbilityActionHandler;
import emu.grasscutter.game.entity.GameEntity;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.BooleanSupplier;

/** Resource duration and end callbacks for server instances awaiting client confirmation. */
public final class AbilityModifierDuration {
    private static final class State {
        final AbilityModifierController controller;
        final AbilityExecutionContext context;
        final long start;
        volatile long duration;
        State(AbilityModifierController controller, GameEntity entity, long start) {
            this.controller=controller;
            this.context=new AbilityExecutionContext(controller.getAbility(),entity);
            this.start=start;
        }
    }
    private final GameEntity entity;
    private final LongSupplier clock;
    private final Map<AbilityModifierController,State> states=new ConcurrentHashMap<>();

    public AbilityModifierDuration(GameEntity entity) { this(entity,System::nanoTime); }
    public AbilityModifierDuration(GameEntity entity,LongSupplier clock) {
        this.entity=entity; this.clock=Objects.requireNonNull(clock);
    }
    // Registration and removal share the entity's modifier registry lock.
    public void registered(AbilityModifierController controller) {
        if(entity.getInstancedModifiers().containsValue(controller)) {
            if(controller.hasServerCallbacks() || controller.hasBindingCallbacks()) states.remove(controller);
            else states.keySet().removeIf(c->sameModifier(c,controller));
            return;
        }
        var data=controller.getModifierData();
        if((data.duration==null || (!data.duration.isDynamic() && data.duration.getConstant()<=0))
                && (data.onRemoved==null || data.onRemoved.length==0)) return;
        var state=new State(controller,entity,clock.getAsLong());
        if(!state.context.isCurrent()) return;
        resolveDuration(state);
        states.putIfAbsent(controller,state);
    }
    private static boolean sameModifier(AbilityModifierController first,AbilityModifierController second) {
        return first.getAbility()==second.getAbility() && first.getName().equals(second.getName());
    }
    private void resolveDuration(State state) {
        if(state.duration>0) return;
        var data=state.controller.getModifierData();
        float seconds=data.duration==null?0:data.duration.get(
                AbilityActionHandler.propertiesFor(state.controller.getAbility(),entity),0);
        if(Float.isFinite(seconds) && seconds>0)
            state.duration=Math.max(1,(long)(seconds*1_000_000_000d));
    }
    public void refresh() {
        if(states.isEmpty()) return;
        synchronized(entity.getInstancedModifiers()) {
            for(var state:states.values()) if(state.context.isCurrent()) resolveDuration(state);
        }
    }
    public void cancel(AbilityModifierController controller) { states.remove(controller); }
    public void clear() { states.clear(); }

    // A refresh replaces the timer generation, retaining the controller and callback ownership.
    void restartLifetime(AbilityModifierController controller) {
        var previous=states.get(controller);
        if(previous==null || !previous.context.isCurrent()) return;
        var next=new State(controller,entity,clock.getAsLong());
        resolveDuration(next);
        states.replace(controller,previous,next);
    }

    List<Runnable> removalCallbacks(Ability ability,String name) {
        if(states.isEmpty()) return List.of();
        var active=entity.getAbilityHpModifier().activeControllers();
        var callbacks=new ArrayList<Runnable>();
        for(var state:new ArrayList<>(states.values())) {
            var controller=state.controller;
            if(controller.getAbility()!=ability || !name.equals(controller.getName())) continue;
            if(states.remove(controller,state) && active.contains(controller) && state.context.isCurrent())
                callbacks.add(()->end(state));
        }
        return callbacks;
    }
    public void tick() {
        if(states.isEmpty()) return;
        long now=clock.getAsLong();
        for(var state:new ArrayList<>(states.values())) {
            if(!state.context.isCurrent()) { states.remove(state.controller,state); continue; }
            boolean active;
            synchronized(entity.getInstancedModifiers()) {
                active=entity.getAbilityHpModifier().activeControllers().contains(state.controller);
            }
            if(!active) { states.remove(state.controller,state); continue; }
            if(state.duration<=0 || now-state.start<state.duration) continue;
            state.controller.getAbility().expireServerModifier(entity,state.controller,
                    ()->states.get(state.controller)==state && state.context.isCurrent(),()->end(state));
        }
    }
    private void end(State state) {
        AbilityGlobalValueWatchers.runRemovalCallbacks(() -> endCallbacks(state));
    }
    private void endCallbacks(State state) {
        var controller=state.controller;
        if(controller.hasServerCallbacks() || controller.hasBindingCallbacks()) { controller.runServerOnRemoved(); return; }
        if(controller.hasAttachmentCallbacks()) controller.runServerOnRemoved();
        runRemovalActions(controller, entity, state.context::isCurrent);
    }
    static void runRemovalActions(AbilityModifierController controller, GameEntity entity, BooleanSupplier current) {
        var ability=controller.getAbility();
        var actions=controller.getModifierData().onRemoved;
        if(actions==null) return;
        for(var action:actions) {
            if(!current.getAsBoolean()) return;
            if(action==null) continue;
            Collection<? extends GameEntity> targets;
            if("AllPlayerAvatars".equals(action.target)) {
                var player=ability.getPlayerOwner();
                targets=player==null?List.of():new LinkedHashSet<>(player.getTeamManager().getActiveTeam());
            } else {
                var target=AbilityActionHandler.resolveTarget(ability,entity,action.target);
                targets=target==null?List.of():List.of(target);
            }
            for(var target:targets) {
                if(!current.getAsBoolean()) return;
                if(!AbilityExecutionContext.canTrigger(target,ability.getPlayerOwner())) continue;
                @SuppressWarnings("unchecked")
                var predicates=(List<Map<String,Object>>) (List<?>) action.predicates;
                if(PredicateEvaluator.all(predicates,ability,ability.getOwner(),target,action))
                    ability.getManager().executeActionNow(ability,action,ByteString.EMPTY,target);
            }
        }
    }
}
