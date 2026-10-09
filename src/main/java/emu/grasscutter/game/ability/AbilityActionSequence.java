package emu.grasscutter.game.ability;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.actions.AbilityActionHandler;
import emu.grasscutter.game.ability.actions.ActionGetFightProperty;
import emu.grasscutter.game.ability.actions.ActionSharedCooldown;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.entity.EntityAvatar;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Validated resource sequences, including callbacks of explicitly referenced modifiers. */
final class AbilityActionSequence {
    private static final Set<String> TARGETS = Set.of("Self","Target","Applier","Owner","Caster",
            "CurLocalAvatar","OriginOwner","CasterOriginOwner","AllPlayerAvatars");
    private static final Set<AbilityModifierAction.Type> ACTIONS = Set.of(
            AbilityModifierAction.Type.HealHP, AbilityModifierAction.Type.LoseHP,
            AbilityModifierAction.Type.SetGlobalValue, AbilityModifierAction.Type.SetGlobalValueV2,
            AbilityModifierAction.Type.AddGlobalValue, AbilityModifierAction.Type.ClearGlobalValue,
            AbilityModifierAction.Type.CopyGlobalValue, AbilityModifierAction.Type.GetFightProperty, AbilityModifierAction.Type.NyxSet,
            AbilityModifierAction.Type.NyxAdd, AbilityModifierAction.Type.AddHPDebts,
            AbilityModifierAction.Type.ReduceHPDebts, AbilityModifierAction.Type.AddSpecialEnergy,
            AbilityModifierAction.Type.ReviveElemEnergy,
            AbilityModifierAction.Type.AddAvatarSkillInfo, AbilityModifierAction.Type.RemoveAvatarSkillInfo,
            AbilityModifierAction.Type.AvatarShareCDSkillStart, AbilityModifierAction.Type.ActivitySkillShareCDStart,
            AbilityModifierAction.Type.ClearShareCD,
            AbilityModifierAction.Type.SetOverrideMapValue,
            AbilityModifierAction.Type.SetGlobalValueToOverrideMap,
            AbilityModifierAction.Type.Predicated, AbilityModifierAction.Type.Randomed, AbilityModifierAction.Type.RandomAction,
            AbilityModifierAction.Type.Repeated, AbilityModifierAction.Type.ApplyModifier,
            AbilityModifierAction.Type.AttachModifier, AbilityModifierAction.Type.RemoveModifier);
    private static final Set<String> PREDICATES = Set.of("ByUnlockTalentParam", "ByHasModifier",
            "ByTargetGlobalValue", "ByTargetHPRatio", "LPKOBOLMDBI", "ONJEHDDFFCH", "GKGBIPDLMMG", "DEOFBICNFHF");
    private static final Set<String> SELECTOR_FIELDS = Set.of("$type", "shapeName", "sizeRatio",
            "campTargetType", "entityTypes", "entityTypePriority", "sortType", "topLimit");
    private static final Set<String> COMPARISONS = Set.of("Equal", "NotEqual", "MoreThan", "Greater",
            "MoreThanAndEqual", "MoreOrEqual", "GreaterOrEqual", "LessThan", "Lesser",
            "LessThanAndEqual", "LessAndEqual", "LessOrEqual", "LesserOrEqual");

    private static final ThreadLocal<Integer> nesting=ThreadLocal.withInitial(()->0);
    private AbilityActionSequence() {}
    static Set<AbilityModifierAction> callbacks(AbilityData data, AbilityModifierAction[] actions) {
        Set<AbilityModifierAction> result=Collections.newSetFromMap(new IdentityHashMap<>());
        Set<AbilityModifierAction> path=Collections.newSetFromMap(new IdentityHashMap<>());
        Set<AbilityModifierAction> validated=Collections.newSetFromMap(new IdentityHashMap<>());
        return collect(data,actions,result,validated,path,0)?result:null;
    }
    static boolean isModifierAction(AbilityModifierAction.Type type) {
        return type==AbilityModifierAction.Type.ApplyModifier || type==AbilityModifierAction.Type.AttachModifier
                || type==AbilityModifierAction.Type.RemoveModifier;
    }
    /** Claim the entire tree or none of it; unknown client predicates must not become true. */
    private static boolean collect(AbilityData data, AbilityModifierAction[] actions, Set<AbilityModifierAction> callbacks,
                                   Set<AbilityModifierAction> validated,
                                   Set<AbilityModifierAction> path, int depth) {
        if(actions==null) return true;
        if(depth>32) return false;
        for(var action:actions) {
            if(action==null || action.type==null || !ACTIONS.contains(action.type) || !knownTarget(action.target)
                    || !supportedPredicates(action.predicates,0)
                    || !supportedPredicates(action.targetPredicates,0) || !supportedSelector(action)
                    || !path.add(action)) return false;
            if(action.type==AbilityModifierAction.Type.CopyGlobalValue
                    && (!knownTarget(action.srcTarget) || !knownTarget(action.dstTarget)
                        || "AllPlayerAvatars".equals(action.srcTarget) || "AllPlayerAvatars".equals(action.dstTarget))) return false;
            if(action.type==AbilityModifierAction.Type.GetFightProperty
                    && !ActionGetFightProperty.supportedSource(action)) return false;
            if((action.type==AbilityModifierAction.Type.AvatarShareCDSkillStart
                    || action.type==AbilityModifierAction.Type.ActivitySkillShareCDStart
                    || action.type==AbilityModifierAction.Type.ClearShareCD)
                    && !ActionSharedCooldown.supported(action)) return false;
            callbacks.add(action);
            if (action.randomInRange || action.type==AbilityModifierAction.Type.SetGlobalValueToOverrideMap
                    && action.abilityFormula!=null && !action.abilityFormula.isEmpty()) return false;
            validated.add(action);
            if(validated.size()>256) return false;
            if((action.type==AbilityModifierAction.Type.Predicated || action.type==AbilityModifierAction.Type.Randomed)
                    && (!collect(data,action.successActions,callbacks,validated,path,depth+1)
                        || !collect(data,action.failActions,callbacks,validated,path,depth+1))) return false;
            if(action.type==AbilityModifierAction.Type.Repeated
                    && !collect(data,action.actions,callbacks,validated,path,depth+1)) return false;
            if(action.type==AbilityModifierAction.Type.RandomAction) {
                if(!AbilityRandomQueue.supportedCount(action)
                        || action.randomQueue!=null && action.randomQueue.length>256) return false;
                if(action.randomQueue!=null) for(var group:action.randomQueue)
                    if(group!=null && (!supportedPredicates(group.randomPredicates,0)
                            || !collect(data,group.actionQueue,callbacks,validated,path,depth+1))) return false;
            }
            if(isModifierAction(action.type)) {
                if(data.modifiers==null || action.modifierName==null) return false;
                var modifier=data.modifiers.get(action.modifierName);
                // Validate referenced callbacks, but only their own instances may claim them.
                Set<AbilityModifierAction> referenced=Collections.newSetFromMap(new IdentityHashMap<>());
                if(modifier==null || !AbilityModifierStacking.supported(modifier)
                        || !collect(data,modifier.onAdded,referenced,validated,path,depth+1)
                        || !collect(data,modifier.onRemoved,referenced,validated,path,depth+1)) return false;
            }
            path.remove(action);
        }
        return true;
    }
    private static boolean knownTarget(String target) { return target==null || TARGETS.contains(target); }
    private static boolean supportedPredicates(List<?> predicates, int depth) {
        if(predicates==null) return true;
        if(depth>32) return false;
        for(var value:predicates) {
            if(value==null) continue;
            if(!(value instanceof Map<?,?> predicate) || !(predicate.get("$type") instanceof String type)
                    || !PREDICATES.contains(type)) return false;
            if("DEOFBICNFHF".equals(type) && !AbilityEntityTypePredicate.supported(predicate)) return false;
            if(predicate.containsKey("target") && !(predicate.get("target") instanceof String name
                    && ((TARGETS.contains(name) && !"AllPlayerAvatars".equals(name))
                        || "Team".equals(name) || "CasterOriginOwner".equals(name)))) return false;
            if("ByTargetGlobalValue".equals(type) && !supportedComparison(predicate,"compareType")) return false;
            if(("ByTargetHPRatio".equals(type) || "LPKOBOLMDBI".equals(type))
                    && !supportedComparison(predicate,"logic")) return false;
            if("GKGBIPDLMMG".equals(predicate.get("$type"))
                    && (!(predicate.get("predicates") instanceof List<?> children)
                        || !supportedPredicates(children,depth+1))) return false;
        }
        return true;
    }
    private static boolean supportedComparison(Map<?,?> predicate, String key) {
        return !predicate.containsKey(key) || predicate.get(key) instanceof String value && COMPARISONS.contains(value);
    }
    private static boolean supportedSelector(AbilityModifierAction action) {
        var selector=action.otherTargets;
        if(selector==null) return true;
        // Selected conditional blocks also carry client fallback semantics, not a simple pulse.
        if(action.type==AbilityModifierAction.Type.Predicated
                || !(action.target==null || Set.of("Self","Target","Applier").contains(action.target))
                || !"SelectTargetsByShape".equals(selector.get("$type"))
                || !SELECTOR_FIELDS.containsAll(selector.keySet())
                || !(selector.get("shapeName") instanceof String shape
                    && shape.matches("CircleR[0-9]+(?:\\.[0-9]+)?(?:H[0-9]+(?:\\.[0-9]+)?)?"))) return false;
        var dimensions=((String)selector.get("shapeName")).substring(7).split("H");
        for(var dimension:dimensions) {
            float value=Float.parseFloat(dimension);
            if(!Float.isFinite(value) || value<=0) return false;
        }
        if(selector.containsKey("sizeRatio") && !(selector.get("sizeRatio") instanceof Number ratio
                && ratio.doubleValue()==1d)) return false;
        if(selector.containsKey("campTargetType") && !(selector.get("campTargetType") instanceof String camp
                && Set.of("Enemy","Alliance","SelfCamp","All").contains(camp))) return false;
        if(selector.containsKey("sortType") && !"Nearest".equals(selector.get("sortType"))) return false;
        if(selector.containsKey("entityTypes") && !(selector.get("entityTypes") instanceof List<?> types
                && types.stream().allMatch(t->t instanceof String
                    && Set.of("Avatar","RemoteAvatar","Monster","Gadget","Vehicle").contains(t)))) return false;
        // A single priority has no ordering ambiguity and is already supported by the selector.
        if(selector.containsKey("entityTypePriority") && (selector.containsKey("entityTypes")
                || !(selector.get("entityTypePriority") instanceof List<?> types) || types.size()!=1
                || !(types.get(0) instanceof String type)
                || !Set.of("Avatar","RemoteAvatar","Monster","Gadget","Vehicle").contains(type))) return false;
        return !selector.containsKey("topLimit") || selector.get("topLimit") instanceof Number limit
                && limit.doubleValue()>=0 && limit.doubleValue()<=Integer.MAX_VALUE
                && limit.doubleValue()==limit.intValue();
    }

    static void run(Ability ability, AbilityModifierAction[] actions, GameEntity self, BooleanSupplier current) {
        run(ability,actions,self,current,false);
    }
    static void runPeriodic(Ability ability, AbilityModifierAction[] actions, GameEntity self, BooleanSupplier current) {
        run(ability,actions,self,current,true);
    }
    private static void run(Ability ability, AbilityModifierAction[] actions, GameEntity self,
                            BooleanSupplier current, boolean periodic) {
        if(actions==null) return;
        int previous=nesting.get();
        if(previous>=32) return;
        nesting.set(previous+1);
        try { AbilityActionBudget.run(()-> { for(var action:actions) execute(ability,action,self,current,0,periodic); return true; }); }
        finally { nesting.set(previous); }
    }
    private static void execute(Ability ability, AbilityModifierAction action, GameEntity self,
                                BooleanSupplier current, int depth, boolean periodic) {
        if(action==null || depth>32 || !current.getAsBoolean() || !AbilityActionBudget.consume()) return;
        // This flag applies to the callback's carrier, before resolving its recipient(s).
        if(periodic && !action.doOffStage && self instanceof EntityAvatar
                && (self.getScene()==null || self.getScene().getEntities().get(self.getId())!=self)) return;
        for(var target:targets(ability,action,self)) {
            if(!current.getAsBoolean() || !AbilityExecutionContext.canTrigger(target,ability.getPlayerOwner())) return;
            @SuppressWarnings("unchecked")
            var predicates=(List<Map<String,Object>>)(List<?>)action.predicates;
            if(!PredicateEvaluator.all(predicates,ability,ability.getOwner(),target,action)) continue;
            if(action.type==AbilityModifierAction.Type.Predicated || action.type==AbilityModifierAction.Type.Randomed) {
                var random=action.type==AbilityModifierAction.Type.Randomed?AbilityRandomBranch.choose(ability,action):null;
                if(action.type==AbilityModifierAction.Type.Randomed && random.isEmpty()) continue;
                boolean pass=random==null?PredicateEvaluator.all(action.targetPredicates,ability,ability.getOwner(),target,action):random.get();
                var branch=pass?action.successActions:action.failActions;
                if(branch!=null) for(var child:branch) execute(ability,child,target,current,depth+1,periodic);
            } else if(action.type==AbilityModifierAction.Type.RandomAction) {
                var chosen=AbilityRandomQueue.choose(ability,action,target);
                if(chosen.isPresent()) for(var child:chosen.get()) execute(ability,child,target,current,depth+1,periodic);
            } else if(action.type==AbilityModifierAction.Type.Repeated) {
                var count=AbilityRepeatCount.resolve(ability,action);
                if(count.isEmpty() || action.actions==null) continue;
                for(int i=0;i<count.getAsInt();i++)
                    for(var child:action.actions) execute(ability,child,target,current,depth+1,periodic);
            } else if(isModifierAction(action.type)) {
                var data=ability.getData().modifiers.get(action.modifierName);
                if(data==null) continue;
                if(action.type==AbilityModifierAction.Type.RemoveModifier) {
                    ability.removeModifier(target,action.modifierName);
                } else {
                    if(action.type==AbilityModifierAction.Type.ApplyModifier && "Unique".equals(data.stacking))
                        ability.removeModifier(target,action.modifierName);
                    AbilityModifierStacking.apply(ability,data,target,current);
                }
            } else ability.getManager().executeActionNow(ability,action,ByteString.EMPTY,target);
        }
    }
    private static List<GameEntity> targets(Ability ability,AbilityModifierAction action,GameEntity self) {
        return targets(ability,action,self,false);
    }
    /** Native callbacks retain their supplied target when a selector has no implementation. */
    static List<GameEntity> targets(Ability ability,AbilityModifierAction action,GameEntity self,boolean nativeFallback) {
        if(action.otherTargets!=null) {
            var selected=AbilityTargetSelector.select(action.otherTargets,ability,self);
            if(selected!=null) return selected;
            if(!nativeFallback) return List.of();
        }
        var player=ability.getPlayerOwner();
        if("AllPlayerAvatars".equals(action.target))
            return player==null?List.of():new ArrayList<>(new LinkedHashSet<>(player.getTeamManager().getActiveTeamSnapshot()));
        if(("CurLocalAvatar".equals(action.target) || "OriginOwner".equals(action.target))
                && (player==null || player.getTeamManager().getActiveTeam().isEmpty())) return List.of();
        var target=AbilityActionHandler.resolveTarget(ability,self,action.target);
        return target==null?List.of():List.of(target);
    }
}
