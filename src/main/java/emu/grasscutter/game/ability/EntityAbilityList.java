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

    public EntityAbilityList(GameEntity entity) {
        this.entity = entity;
    }

    private void changed() {
        entity.getAbilityRoots().rootsChanged();
        entity.getAbilityGlobalValueWatchers().refresh();
    }

    private void changed(Object[] before) {
        var after = toArray();
        int index = 0;
        while (index < before.length && index < after.length && before[index] == after[index])
            index++;
        entity.getAbilityRoots().invalidateFrom(index);
        changed();
    }

    @Override
    public boolean add(Ability value) {
        boolean added = super.add(value);
        if (added) changed();
        return added;
    }

    @Override
    public void add(int index, Ability value) {
        super.add(index, value);
        entity.getAbilityRoots().invalidateFrom(index);
        changed();
    }

    @Override
    public boolean addIfAbsent(Ability value) {
        boolean added = super.addIfAbsent(value);
        if (added) changed();
        return added;
    }

    @Override
    public boolean addAll(Collection<? extends Ability> values) {
        boolean added = super.addAll(values);
        if (added) changed();
        return added;
    }

    @Override
    public boolean addAll(int index, Collection<? extends Ability> values) {
        boolean added = super.addAll(index, values);
        if (added) {
            entity.getAbilityRoots().invalidateFrom(index);
            changed();
        }
        return added;
    }

    @Override
    public int addAllAbsent(Collection<? extends Ability> values) {
        int added = super.addAllAbsent(values);
        if (added != 0) changed();
        return added;
    }

    @Override
    public Ability set(int index, Ability value) {
        var previous = super.set(index, value);
        if (previous != value) changed();
        return previous;
    }

    @Override
    public Ability remove(int index) {
        var previous = super.remove(index);
        entity.getAbilityRoots().invalidateFrom(index);
        changed();
        return previous;
    }

    @Override
    public boolean remove(Object value) {
        int index = indexOf(value);
        boolean removed = super.remove(value);
        if (removed) {
            entity.getAbilityRoots().invalidateFrom(Math.max(0, index));
            changed();
        }
        return removed;
    }

    @Override
    public boolean removeAll(Collection<?> values) {
        var before = toArray();
        boolean removed = super.removeAll(values);
        if (removed) changed(before);
        return removed;
    }

    @Override
    public boolean retainAll(Collection<?> values) {
        var before = toArray();
        boolean removed = super.retainAll(values);
        if (removed) changed(before);
        return removed;
    }

    @Override
    public boolean removeIf(Predicate<? super Ability> predicate) {
        var before = toArray();
        boolean removed = super.removeIf(predicate);
        if (removed) changed(before);
        return removed;
    }

    @Override
    public void clear() {
        super.clear();
        entity.getAbilityRoots().clear();
        changed();
    }

    @Override
    public void replaceAll(UnaryOperator<Ability> operator) {
        var before = toArray();
        super.replaceAll(operator);
        changed(before);
    }

    @Override
    public void sort(Comparator<? super Ability> comparator) {
        var before = toArray();
        super.sort(comparator);
        changed(before);
    }
}
