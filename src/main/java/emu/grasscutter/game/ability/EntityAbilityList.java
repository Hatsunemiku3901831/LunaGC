package emu.grasscutter.game.ability;

import emu.grasscutter.game.entity.GameEntity;
import java.util.Collection;
import java.util.Comparator;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/** Preserve snapshot iteration while reconciling root bindings after actual list mutations. */
public final class EntityAbilityList extends CopyOnWriteArrayList<Ability> {
    private final GameEntity entity;
    public EntityAbilityList(GameEntity entity) { this.entity = entity; }
    private void changed() { entity.getAbilityGlobalValueWatchers().refresh(); }
    @Override public boolean add(Ability value) { boolean added=super.add(value); if(added)changed(); return added; }
    @Override public void add(int index,Ability value) { super.add(index,value); changed(); }
    @Override public boolean addIfAbsent(Ability value) { boolean added=super.addIfAbsent(value); if(added)changed(); return added; }
    @Override public boolean addAll(Collection<? extends Ability> values) { boolean added=super.addAll(values); if(added)changed(); return added; }
    @Override public boolean addAll(int index,Collection<? extends Ability> values) { boolean added=super.addAll(index,values); if(added)changed(); return added; }
    @Override public int addAllAbsent(Collection<? extends Ability> values) { int added=super.addAllAbsent(values); if(added!=0)changed(); return added; }
    @Override public Ability set(int index,Ability value) { var previous=super.set(index,value); if(previous!=value)changed(); return previous; }
    @Override public Ability remove(int index) { var previous=super.remove(index); changed(); return previous; }
    @Override public boolean remove(Object value) { boolean removed=super.remove(value); if(removed)changed(); return removed; }
    @Override public boolean removeAll(Collection<?> values) { boolean removed=super.removeAll(values); if(removed)changed(); return removed; }
    @Override public boolean retainAll(Collection<?> values) { boolean removed=super.retainAll(values); if(removed)changed(); return removed; }
    @Override public boolean removeIf(Predicate<? super Ability> predicate) { boolean removed=super.removeIf(predicate); if(removed)changed(); return removed; }
    @Override public void clear() { super.clear(); changed(); }
    @Override public void replaceAll(UnaryOperator<Ability> operator) { super.replaceAll(operator); changed(); }
    @Override public void sort(Comparator<? super Ability> comparator) { super.sort(comparator); changed(); }
}
