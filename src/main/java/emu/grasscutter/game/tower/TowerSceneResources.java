package emu.grasscutter.game.tower;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/** Structural check of every block/group dependency; deliberately not a gameplay certificate. */
public final class TowerSceneResources {
    private TowerSceneResources() {}

    public static boolean complete(Path scripts, int sceneId) {
        try {
            var dir = scripts.resolve("Scene/" + sceneId);
            var main = text(dir.resolve("scene" + sceneId + ".lua"));
            var blocks = Pattern.compile("(?m)^\\s*blocks\\s*=\\s*\\{([^}]*)}").matcher(main);
            if (!blocks.find()) return false;
            var ids = Pattern.compile("\\d+").matcher(blocks.group(1));
            boolean hasBlock = false, hasMonsters = false, hasChallenge = false, hasWorktop = false;
            while (ids.find()) {
                hasBlock = true;
                var block = text(dir.resolve("scene" + sceneId + "_block" + ids.group() + ".lua"));
                var groups = Pattern.compile("\\bid\\s*=\\s*(\\d+)").matcher(block);
                boolean hasGroup = false;
                while (groups.find()) {
                    hasGroup = true;
                    var group =
                            text(
                                    dir.resolve(
                                            "scene"
                                                    + sceneId
                                                    + "_group"
                                                    + groups.group(1)
                                                    + ".lua"));
                    hasMonsters |=
                            Pattern.compile("\\bmonster_id\\s*=\\s*\\d+").matcher(group).find();
                    hasChallenge |= group.contains("ScriptLib.ActiveChallenge");
                    hasWorktop |= group.contains("EVENT_SELECT_OPTION");
                    var requires =
                            Pattern.compile("require\\s*\\(?\\s*[\"']([A-Za-z0-9_/-]+)[\"']")
                                    .matcher(group);
                    while (requires.find()) {
                        if (!Files.isRegularFile(
                                scripts.resolve("Common/" + requires.group(1) + ".lua")))
                            return false;
                    }
                }
                if (!hasGroup) return false;
            }
            return hasBlock && hasMonsters && hasChallenge && hasWorktop;
        } catch (IOException e) {
            return false;
        }
    }

    private static String text(Path file) throws IOException {
        return Files.readString(file).replaceAll("--[^\\r\\n]*", "");
    }
}
