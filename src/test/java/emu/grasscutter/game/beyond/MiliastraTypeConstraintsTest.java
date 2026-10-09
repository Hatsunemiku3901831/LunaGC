package emu.grasscutter.game.beyond;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class MiliastraTypeConstraintsTest {
    private record Choice(String left, String right, int score) {}

    private static MiliastraTypeConstraints.Link<Choice> link(int from, int to) {
        return new MiliastraTypeConstraints.Link<>(from, to, Choice::right, Choice::left);
    }

    private static List<List<Choice>> solve(
            List<List<Choice>> domains, List<MiliastraTypeConstraints.Link<Choice>> links) {
        return MiliastraTypeConstraints.solve(domains, links, Choice::score);
    }

    @Test
    void choosesTheCompatibleAssignmentWithTheBestCombinedPreference() {
        var i0 = new Choice("Int", "Int", 3);
        var f0 = new Choice("Flt", "Flt", 0);
        var i1 = new Choice("Int", "Int", 0);
        var f1 = new Choice("Flt", "Flt", 4);
        var domains = List.of(List.of(i0, f0), List.of(i1, f1));
        assertEquals(List.of(List.of(f0), List.of(f1)), solve(domains, List.of(link(0, 1))));
        assertEquals(2, domains.get(0).size(), "Caller domains must remain unchanged");
    }

    @Test
    void equalPreferencesRemainAmbiguousWithoutDependingOnInputOrder() {
        var a = new Choice("Int", "Int", 2);
        var b = new Choice("Flt", "Flt", 2);
        var result = solve(List.of(List.of(a, b), List.of(b, a)), List.of(link(0, 1)));
        for (var options : result) assertEquals(Set.of(a, b), new HashSet<>(options));
    }

    private static List<List<Choice>> cycle(int count) {
        var options = List.of(new Choice("A", "B", 0), new Choice("B", "A", 0));
        return Collections.nCopies(count, options);
    }

    private static List<MiliastraTypeConstraints.Link<Choice>> cycleLinks(int count) {
        var links = new ArrayList<MiliastraTypeConstraints.Link<Choice>>();
        for (int i = 0; i < count; i++) links.add(link(i, (i + 1) % count));
        return links;
    }

    @Test
    void detectsAnImpossibleCycleEvenWhenEveryEdgeHasLocalSupport() {
        var error = assertThrows(IllegalArgumentException.class, () -> solve(cycle(3), cycleLinks(3)));
        assertTrue(error.getMessage().contains("没有一致解"));
    }

    @Test
    void retainsBothValidAssignmentsOfAnEvenCycle() {
        var result = solve(cycle(4), cycleLinks(4));
        for (var options : result) assertEquals(2, options.size());
    }

    @Test
    void aSelfConnectionMustUseTheSameConcreteChoiceOnBothSides() {
        var rejected = new Choice("A", "B", 100);
        var accepted = new Choice("B", "B", 0);
        assertEquals(
                List.of(List.of(accepted)),
                solve(List.of(List.of(rejected, accepted)), List.of(link(0, 0))));
    }

    @Test
    void fixedTypeEdgesDoNotMultiplyIndependentGenericChoices() {
        var options = List.of(new Choice("Int", "Bool", 1), new Choice("Flt", "Bool", 1));
        var domains = Collections.nCopies(2000, options);
        var links = new ArrayList<MiliastraTypeConstraints.Link<Choice>>();
        for (int i = 1; i < domains.size(); i++)
            links.add(new MiliastraTypeConstraints.Link<>(i - 1, i, Choice::right, Choice::right));
        var result = solve(domains, links);
        assertEquals(2000, result.size());
        for (var choices : result) assertEquals(2, choices.size());
    }

    @Test
    void workLimitRejectsOversizedDomainsBeforeCopyingThem() {
        var options =
                Collections.nCopies(MiliastraTypeConstraints.MAX_WORK, new Choice("Int", "Int", 0));
        var error =
                assertThrows(IllegalArgumentException.class, () -> solve(List.of(options), List.of()));
        assertTrue(error.getMessage().contains("计算超限"));
    }

    @Test
    void matchesAnExhaustiveOracleAcrossSmallGraphsAndPreferenceTies() {
        var random = new Random(0x71);
        for (int sample = 0; sample < 120; sample++) {
            int count = 1 + random.nextInt(5);
            var domains = new ArrayList<List<Choice>>();
            for (int i = 0; i < count; i++) {
                var choices = new ArrayList<Choice>();
                for (int j = 0, size = 1 + random.nextInt(3); j < size; j++)
                    choices.add(
                            new Choice("T" + random.nextInt(3), "T" + random.nextInt(3), random.nextInt(5) - 1));
                domains.add(choices);
            }
            var links = new ArrayList<MiliastraTypeConstraints.Link<Choice>>();
            for (int i = 0, size = random.nextInt(count + 2); i < size; i++)
                links.add(link(random.nextInt(count), random.nextInt(count)));
            var oracle = new Oracle(count);
            enumerate(domains, links, new ArrayList<>(), oracle);
            if (oracle.best == Long.MIN_VALUE) {
                assertThrows(IllegalArgumentException.class, () -> solve(domains, links), "case " + sample);
            } else {
                var actual = solve(domains, links);
                for (int i = 0; i < count; i++)
                    assertEquals(
                            oracle.support.get(i), new HashSet<>(actual.get(i)), "case " + sample + " node " + i);
            }
        }
    }

    private static final class Oracle {
        long best = Long.MIN_VALUE;
        final List<Set<Choice>> support = new ArrayList<>();

        Oracle(int count) {
            for (int i = 0; i < count; i++) support.add(new HashSet<>());
        }
    }

    private static void enumerate(
            List<List<Choice>> domains,
            List<MiliastraTypeConstraints.Link<Choice>> links,
            List<Choice> choices,
            Oracle oracle) {
        if (choices.size() < domains.size()) {
            for (var choice : domains.get(choices.size())) {
                choices.add(choice);
                enumerate(domains, links, choices, oracle);
                choices.remove(choices.size() - 1);
            }
            return;
        }
        for (var edge : links)
            if (!edge.fromType()
                    .apply(choices.get(edge.from()))
                    .equals(edge.toType().apply(choices.get(edge.to())))) return;
        long score = choices.stream().mapToLong(Choice::score).sum();
        if (score < oracle.best) return;
        if (score > oracle.best) {
            oracle.best = score;
            for (var options : oracle.support) options.clear();
        }
        for (int i = 0; i < choices.size(); i++) oracle.support.get(i).add(choices.get(i));
    }
}
