package emu.grasscutter.game.beyond;

import java.util.*;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/** Finds compatible concrete types across a graph, retaining all equally preferred solutions. */
final class MiliastraTypeConstraints {
    static final int MAX_WORK = 2_000_000;

    record Link<T>(int from, int to, Function<T, String> fromType, Function<T, String> toType) {}

    private static final class Budget {
        int remaining = MAX_WORK;

        void spend(int count) {
            if (count < 0 || count > remaining)
                throw new IllegalArgumentException("泛型约束计算超限，请拆分图或明确 giaTypes。");
            remaining -= count;
        }
    }

    private MiliastraTypeConstraints() {}

    static <T> List<List<T>> solve(List<List<T>> input, List<Link<T>> links, ToIntFunction<T> score) {
        var budget = new Budget();
        var domains = copy(input, budget);
        if (!reduce(domains, links, budget)) throw conflict();
        int[] parent = new int[domains.size()];
        for (int i = 0; i < parent.length; i++) parent[i] = i;
        var active = new ArrayList<Link<T>>();
        for (var link : links) {
            if (redundant(domains, link, budget)) continue;
            active.add(link);
            parent[root(parent, link.from)] = root(parent, link.to);
        }
        var groups = new LinkedHashMap<Integer, List<Integer>>();
        for (int i = 0; i < domains.size(); i++)
            groups.computeIfAbsent(root(parent, i), ignored -> new ArrayList<>()).add(i);
        var groupLinks = new HashMap<Integer, List<Link<T>>>();
        for (var link : active)
            groupLinks.computeIfAbsent(root(parent, link.from), ignored -> new ArrayList<>()).add(link);
        for (var entry : groups.entrySet()) {
            var group = entry.getValue();
            var local = new ArrayList<List<T>>();
            var indexes = new HashMap<Integer, Integer>();
            for (int i : group) {
                indexes.put(i, local.size());
                local.add(domains.get(i));
            }
            var edges = new ArrayList<Link<T>>();
            for (var link : groupLinks.getOrDefault(entry.getKey(), List.of()))
                edges.add(
                        new Link<>(indexes.get(link.from), indexes.get(link.to), link.fromType, link.toType));
            var choices = search(local, edges, score, budget);
            for (int i = 0; i < group.size(); i++) domains.set(group.get(i), choices.get(i));
        }
        return domains.stream().map(List::copyOf).toList();
    }

    private static int root(int[] parent, int index) {
        int result = index;
        while (parent[result] != result) result = parent[result];
        while (parent[index] != index) {
            int next = parent[index];
            parent[index] = result;
            index = next;
        }
        return result;
    }

    private static <T> List<List<T>> search(
            List<List<T>> input, List<Link<T>> links, ToIntFunction<T> score, Budget budget) {
        var pending = new ArrayDeque<List<List<T>>>();
        pending.push(input);
        long best = Long.MIN_VALUE;
        var support = new ArrayList<Set<T>>();
        for (int i = 0; i < input.size(); i++) support.add(new HashSet<>());
        while (!pending.isEmpty()) {
            var domains = pending.pop();
            if (!reduce(domains, links, budget)) continue;
            long upper = 0;
            var preferred = new ArrayList<List<T>>();
            for (var domain : domains) {
                budget.spend(domain.size());
                int maximum = domain.stream().mapToInt(score).max().orElseThrow();
                upper += maximum;
                preferred.add(
                        domain.stream().filter(option -> score.applyAsInt(option) == maximum).toList());
            }
            if (upper < best) continue;
            // Constant-typed edges do not couple independent reflective choices.
            boolean independent = true;
            for (var link : links)
                if (!redundant(domains, link, budget)) {
                    independent = false;
                    break;
                }
            if (independent) {
                if (upper > best) {
                    best = upper;
                    for (var choices : support) choices.clear();
                }
                for (int i = 0; i < support.size(); i++) support.get(i).addAll(preferred.get(i));
                continue;
            }
            int branch = -1;
            for (int i = 0; i < domains.size(); i++)
                if (domains.get(i).size() > 1
                        && (branch < 0 || domains.get(i).size() < domains.get(branch).size())) branch = i;
            if (branch < 0) throw new IllegalStateException("Nonconstant constraint without a variable");
            var options = new ArrayList<>(domains.get(branch));
            options.sort(Comparator.comparingInt(score).reversed());
            for (int i = options.size() - 1; i >= 0; i--) {
                var child = copy(domains, budget);
                child.set(branch, new ArrayList<>(List.of(options.get(i))));
                pending.push(child);
            }
        }
        if (best == Long.MIN_VALUE) throw conflict();
        var result = new ArrayList<List<T>>();
        for (int i = 0; i < input.size(); i++) {
            var supported = support.get(i);
            result.add(input.get(i).stream().filter(supported::contains).toList());
        }
        return result;
    }

    private static <T> boolean reduce(List<List<T>> domains, List<Link<T>> links, Budget budget) {
        for (var domain : domains) if (domain.isEmpty()) return false;
        boolean changed;
        do {
            changed = false;
            for (var link : links) {
                var a = domains.get(link.from);
                var b = domains.get(link.to);
                budget.spend(a.size() + b.size());
                if (link.from == link.to) {
                    changed |=
                            a.removeIf(option -> !link.fromType.apply(option).equals(link.toType.apply(option)));
                } else {
                    var fromTypes = types(a, link.fromType);
                    var toTypes = types(b, link.toType);
                    changed |= a.removeIf(option -> !toTypes.contains(link.fromType.apply(option)));
                    changed |= b.removeIf(option -> !fromTypes.contains(link.toType.apply(option)));
                }
                if (a.isEmpty() || b.isEmpty()) return false;
            }
        } while (changed);
        return true;
    }

    private static <T> boolean redundant(List<List<T>> domains, Link<T> link, Budget budget) {
        var a = domains.get(link.from);
        var b = domains.get(link.to);
        budget.spend(a.size() + b.size());
        var fromTypes = types(a, link.fromType);
        var toTypes = types(b, link.toType);
        return fromTypes.size() == 1 && fromTypes.equals(toTypes);
    }

    private static <T> Set<String> types(List<T> domain, Function<T, String> type) {
        var values = new HashSet<String>();
        for (var option : domain) values.add(type.apply(option));
        return values;
    }

    private static <T> List<List<T>> copy(List<List<T>> domains, Budget budget) {
        budget.spend(domains.size());
        var copied = new ArrayList<List<T>>();
        for (var domain : domains) {
            budget.spend(domain.size());
            copied.add(new ArrayList<>(domain));
        }
        return copied;
    }

    private static IllegalArgumentException conflict() {
        return new IllegalArgumentException("连接的泛型类型约束没有一致解，请检查连接或 giaTypes。");
    }
}
