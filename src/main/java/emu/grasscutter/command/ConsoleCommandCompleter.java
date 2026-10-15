package emu.grasscutter.command;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.SoundRegistry;

import org.jline.reader.*;

import java.util.*;
import java.util.function.Supplier;

/** Resolve commands and observed sound candidates when Tab is pressed, not at server startup. */
public final class ConsoleCommandCompleter implements Completer {
    private final Supplier<CommandMap> commands;

    public ConsoleCommandCompleter() {
        this(Grasscutter::getCommandMap);
    }

    public ConsoleCommandCompleter(Supplier<CommandMap> commands) {
        this.commands = Objects.requireNonNull(commands);
    }

    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        var map = commands.get();
        if (map == null || line == null || candidates == null) return;
        var choices = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        if (line.wordIndex() == 0) {
            for (var handler : map.getHandlers().values()) {
                var annotation = handler.getClass().getAnnotation(Command.class);
                choices.add(annotation.label());
                choices.addAll(Arrays.asList(annotation.aliases()));
            }
        } else {
            var handler = map.getHandler(line.words().get(0).toLowerCase(Locale.ROOT));
            if (handler == null
                    || !"sound".equals(handler.getLabel())
                    || line.word().startsWith("@")) return;
            var before =
                    line.words().subList(1, line.wordIndex()).stream()
                            .filter(word -> !word.startsWith("@"))
                            .toList();
            if (before.isEmpty()) {
                choices.add("list");
                for (String sound : new SoundRegistry.Candidates()) choices.add(sound);
            } else if ("list".equalsIgnoreCase(before.get(0))
                    && before.stream()
                            .noneMatch(word -> word.equals("--page") || word.startsWith("--page=")))
                choices.add("--page");
        }
        String prefix = line.word().substring(0, line.wordCursor()).toLowerCase(Locale.ROOT);
        for (var choice : choices)
            if (choice.toLowerCase(Locale.ROOT).startsWith(prefix))
                candidates.add(new Candidate(choice));
    }
}
