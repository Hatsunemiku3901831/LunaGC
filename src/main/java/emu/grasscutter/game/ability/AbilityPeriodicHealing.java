package emu.grasscutter.game.ability;

import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.actions.AbilityActionHandler;
import emu.grasscutter.game.entity.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Resource-timed, payload-independent actions on live modifier instances. */
public final class AbilityPeriodicHealing {
    private static final class State {
        final AbilityModifierController controller;
        final AbilityExecutionContext context;
        final AbilityModifierAction[] actions;
        final Set<AbilityModifierAction> callbacks;
        final long start;
        volatile long lifetimeStart;
        volatile long interval, duration;
        volatile boolean client;
        long consumed;
        State(AbilityModifierController controller, GameEntity bearer, long start, long interval,
              long duration, boolean client, Set<AbilityModifierAction> callbacks) {
            this.controller=controller; this.context=new AbilityExecutionContext(controller.getAbility(),bearer);
            this.actions=controller.getModifierData().onThinkInterval.clone();
            this.callbacks=callbacks;
            this.start=start; this.lifetimeStart=start; this.interval=interval; this.duration=duration; this.client=client;
        }
        boolean contains(Ability ability, AbilityModifierAction action) {
            return controller.getAbility()==ability && callbacks.contains(action);
        }
    }
    private final GameEntity entity;
    private final LongSupplier clock;
    private final Map<AbilityModifierController,State> states=new ConcurrentHashMap<>();
    // Client instance IDs are bounded by AbilityManager; reuse clears the old claim.
    private final Map<Integer,State> retired=new ConcurrentHashMap<>();
    private final Map<Integer,AbilityModifierController> retiredCallbacks=new ConcurrentHashMap<>();

    public AbilityPeriodicHealing(GameEntity entity) { this(entity,System::nanoTime); }
    public AbilityPeriodicHealing(GameEntity entity, LongSupplier clock) {
        this.entity=entity; this.clock=Objects.requireNonNull(clock);
    }
    public void registered(AbilityModifierController controller) {
        var existing=states.get(controller);
        if(existing!=null) {
            if(entity.getInstancedModifiers().containsValue(controller)) existing.client=true;
            return;
        }
        var data=controller.getModifierData(); var actions=data.onThinkInterval;
        if(actions==null || actions.length==0) return;
        var callbacks=AbilityActionSequence.callbacks(controller.getAbilityData(),actions);
        if(callbacks==null) return;
        var properties=AbilityActionHandler.propertiesFor(controller.getAbility(),entity);
        float interval=data.thinkInterval==null?0:data.thinkInterval.get(properties,0);
        float duration=data.duration==null?0:data.duration.get(properties,0);
        boolean client=entity.getInstancedModifiers().containsValue(controller);
        var state=new State(controller,entity,clock.getAsLong(),
                Float.isFinite(interval) && interval>0?nanos(interval):0,duration>0?nanos(duration):0,client,callbacks);
        if(!state.context.isCurrent()) return;
        if(client) {
            // A client confirmation adopts the pending server instance's cadence, not a new cast.
            var pending=states.values().stream().filter(s->!s.client && s.context.isCurrent()
                    && s.controller.getAbility()==controller.getAbility()
                    && s.controller.getName().equals(controller.getName())).min(Comparator.comparingLong(s->s.start));
            if(pending.isPresent()) synchronized(pending.get()) {
                var previous=pending.get();
                state=new State(controller,entity,previous.start,
                        previous.interval>0?previous.interval:state.interval,
                        previous.interval>0?previous.duration:state.duration,true,callbacks);
                state.consumed=previous.consumed;
                state.lifetimeStart=previous.lifetimeStart;
            }
        }
        if(client || data.stacking==null || !data.stacking.startsWith("Multiple")) {
            states.entrySet().removeIf(e->!e.getValue().client && e.getKey().getAbility()==controller.getAbility()
                    && e.getKey().getName().equals(controller.getName()));
        }
        states.put(controller,state);
    }

    /** Resolve parameters arriving after attachment, retaining the original cast and lifetime. */
    public void refresh() {
        if(states.isEmpty()) return;
        synchronized(entity.getInstancedModifiers()) {
            for(var state:states.values()) {
                if(state.interval>0 || !state.context.isCurrent()) continue;
                var data=state.controller.getModifierData();
                var properties=AbilityActionHandler.propertiesFor(state.controller.getAbility(),entity);
                float interval=data.thinkInterval==null?0:data.thinkInterval.get(properties,0);
                if(!Float.isFinite(interval) || interval<=0) continue;
                float duration=data.duration==null?0:data.duration.get(properties,0);
                synchronized(state) {
                    state.duration=duration>0?nanos(duration):0;
                    state.interval=nanos(interval);
                }
            }
        }
    }
    private static long nanos(float seconds) { return Math.max(1,(long)(seconds*1_000_000_000d)); }
    void restartLifetime(AbilityModifierController controller) {
        var state=states.get(controller);
        if(state==null || !state.context.isCurrent()) return;
        var data=controller.getModifierData();
        float duration=data.duration==null?0:data.duration.get(
                AbilityActionHandler.propertiesFor(controller.getAbility(),entity),0);
        synchronized(state) {
            state.lifetimeStart=clock.getAsLong();
            state.duration=Float.isFinite(duration) && duration>0?nanos(duration):0;
        }
    }
    public void removed(AbilityModifierController controller) { states.remove(controller); }
    public void removed(Ability ability,String name) {
        states.keySet().removeIf(c->c.getAbility()==ability && c.getName().equals(name));
    }
    public void clientAdded(int id) { retired.remove(id); retiredCallbacks.remove(id); }
    public void clientRemoved(int id,AbilityModifierController controller) {
        var state=states.remove(controller);
        if(state!=null && id>0 && id<=2000) retired.put(id,state);
        if(controller.hasServerCallbacks() && id>0 && id<=2000) retiredCallbacks.put(id,controller);
    }
    public void clear() { states.clear(); retired.clear(); retiredCallbacks.clear(); }

    /** These exact callback objects are executed by the timer, including late client confirmations. */
    public boolean ownsAction(Ability ability,int id,AbilityModifierAction action) {
        if(action==null || action.type==null) return false;
        if(states.isEmpty() && retired.isEmpty() && retiredCallbacks.isEmpty()
                && entity.getAppliedAbilityModifiers().isEmpty() && entity.getInstancedModifiers().isEmpty()) return false;
        if(id!=0) {
            var controller=entity.getInstancedModifiers().get(id);
            var callbacks=controller==null?retiredCallbacks.get(id):controller;
            if(callbacks!=null && callbacks.getAbility()==ability && callbacks.ownsServerCallback(action)) return true;
            var state=controller==null?retired.get(id):states.get(controller);
            return state!=null && state.interval>0 && state.context.isCurrent() && state.contains(ability,action);
        }
        synchronized(entity.getInstancedModifiers()) {
            var active=entity.getAbilityHpModifier().activeControllers();
            if(active.stream().anyMatch(c->c.getAbility()==ability && c.ownsServerCallback(action))) return true;
            return active.stream().map(states::get).filter(Objects::nonNull)
                    .anyMatch(s->s.interval>0 && s.context.isCurrent() && s.contains(ability,action));
        }
    }
    public void tick() {
        if(states.isEmpty()) return;
        List<State> snapshot;
        Set<AbilityModifierController> active=Collections.newSetFromMap(new IdentityHashMap<>());
        synchronized(entity.getInstancedModifiers()) {
            active.addAll(entity.getAbilityHpModifier().activeControllers());
            snapshot=new ArrayList<>(states.values());
        }
        long now=clock.getAsLong();
        for(var state:snapshot) {
            if(!active.contains(state.controller) || !state.context.isCurrent()) {
                states.remove(state.controller,state); continue;
            }
            synchronized(state) {
                long elapsed=now-state.start;
                if(state.interval<=0 || elapsed<0) continue;
                // A self-removal scheduled exactly at the lifetime boundary is cleanup, even
                // when the scene tick arrives late. Expired healing pulses still cannot catch up.
                if(state.duration>0 && now-state.lifetimeStart>state.duration
                        && !AbilityModifierInterval.supported(state.controller.getAbility(),
                            state.controller.getModifierData(),state.controller.getName(),entity)) continue;
                long due=elapsed/state.interval;
                if(due<=state.consumed) continue;
                // A delayed scene tick coalesces missed pulses rather than producing a burst.
                state.consumed=due;
            }
            AbilityActionSequence.runPeriodic(state.controller.getAbility(),state.actions,entity,
                    ()->state.context.isCurrent() && isActive(state.controller));
        }
    }
    private boolean isActive(AbilityModifierController controller) {
        synchronized(entity.getInstancedModifiers()) {
            return states.containsKey(controller) && entity.getAbilityHpModifier().activeControllers().contains(controller);
        }
    }

}
