import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.ResourceLoader;
import emu.grasscutter.data.binout.MainQuestData;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.utils.JsonUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipFile;

/** Isolated actual loader/recovery comparison; no quest start or database. */
public final class Client72Quest303Audit {
    private static final String JAR = "dfdebbda0352055540c44a54e2eebfd4ffdfe679db74270044d3d55aeb006ec3";
    private static final String EXCEL = "c8561299f543456573b0fa16404e210bcd2ecf84ce5006eb63dd4b2845137a87";
    private static final String CHAPTER = "aaf6901da4c30528047c1210bc636f810b368d5fb2f19aad6369bbb956dd3cca";
    private static final String[] CLASSES = {
        "emu/grasscutter/data/ResourceLoader", "emu/grasscutter/data/QuestBinFallback",
        "emu/grasscutter/data/excels/quest/QuestData", "emu/grasscutter/data/binout/MainQuestData",
        "emu/grasscutter/utils/JsonUtils", "emu/grasscutter/utils/JsonAdapters", "emu/grasscutter/data/GameData",
        "emu/grasscutter/game/quest/enums/QuestContent", "emu/grasscutter/game/quest/enums/QuestExec",
        "emu/grasscutter/game/quest/enums/QuestCond", "emu/grasscutter/game/quest/enums/LogicType"
    };

    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("repo excel chapter output required");
        Path repo = Path.of(args[0]).toAbsolutePath().normalize();
        Path excel = Path.of(args[1]).toAbsolutePath().normalize();
        Path chapterFile = Path.of(args[2]).toAbsolutePath().normalize();
        Path output = Path.of(args[3]).toAbsolutePath().normalize();
        Path work = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        require(System.getenv("LUNAGC_RESOURCES_DIR") == null || System.getenv("LUNAGC_RESOURCES_DIR").isBlank(), "clear resource directory override");
        requireHash(excel, EXCEL); requireHash(chapterFile, CHAPTER);
        requireHash(repo.resolve("resources/ExcelBinOutput/QuestExcelConfigData.json"), "aacb6edba8b97e3dd39fef80444bb180756fe1d6090c341cd0545ec08f0951a3");
        requireHash(repo.resolve("resources/BinOutput/Quest/303.json"), "44b646a0f29a3a1a1b780d58902ee28e7d5749ba13a45e6b9b2a6d48d12ad8cc");
        Path jar = Path.of(ResourceLoader.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        requireHash(jar, JAR);
        int classCount = checkClasses(repo, jar);
        var config = Grasscutter.getConfig();
        require(Grasscutter.configFile.getCanonicalFile().toPath().equals(work.resolve("config.json")), "isolated config");
        require(Path.of(config.folderStructure.resources).toAbsolutePath().normalize().equals(work.resolve("resources")), "isolated resources");
        require(Path.of(config.folderStructure.data).toAbsolutePath().normalize().equals(work.resolve("data")), "isolated data");
        require(Path.of(config.folderStructure.cache).toAbsolutePath().normalize().equals(work.resolve("cache")), "isolated cache");
        require(Grasscutter.getPluginManager() == null, "plugins absent");
        require(GameData.getQuestDataMap().isEmpty() && GameData.getMainQuestDataMap().isEmpty()
                && GameData.getBeginCondQuestMap().isEmpty(), "fresh quest maps");

        var array = JsonParser.parseString(Files.readString(excel)).getAsJsonArray();
        JsonObject sourceRow = null;
        for (var value : array) if (value.getAsJsonObject().get("subId").getAsInt() == 30302) {
            require(sourceRow == null, "duplicate excel row"); sourceRow = value.getAsJsonObject();
        }
        require(sourceRow != null, "missing 30302 excel row");
        String decodeError;
        try { JsonUtils.decode(sourceRow, QuestData.class); throw new IllegalStateException("target row unexpectedly decoded"); }
        catch (com.google.gson.JsonParseException expected) { decodeError = expected.toString(); }
        require(decodeError.contains("Invalid Enum definition - BEGIN_OBJECT"), "wrong negative failure: " + decodeError);
        JsonArray slice = new JsonArray(); slice.add(sourceRow.deepCopy());
        Path sample = work.resolve("target-excel-30302.json"); Files.writeString(sample, slice.toString() + "\n");
        var installed = new Int2ObjectOpenHashMap<QuestData>();
        var loader = ResourceLoader.class.getDeclaredMethod("loadFromResource", Class.class, Path.class, Int2ObjectMap.class);
        loader.setAccessible(true); loader.invoke(null, QuestData.class, sample, installed);
        require(installed.isEmpty() && GameData.getBeginCondQuestMap().isEmpty(), "rejected excel was installed/indexed");

        JsonObject rawChapter = JsonParser.parseString(Files.readString(chapterFile)).getAsJsonObject();
        var chapter = JsonUtils.loadToClass(chapterFile, MainQuestData.class);
        require(chapter.getId() == 303, "chapter id");
        GameData.getMainQuestDataMap().put(chapter.getId(), chapter);
        chapter.onLoad(); // Same put-then-onLoad sequence as ResourceLoader.loadQuests.
        var children = rawChapter.getAsJsonArray("subQuests");
        require(children.size() == 63 && GameData.getQuestDataMap().size() == 63, "expected 63 installed children");
        var ids = new TreeSet<Integer>(); var rows = new ArrayList<Map<String, Object>>();
        int conditionCount = 0, execCount = 0;
        var unknownConditionKeys = new TreeSet<String>();
        var unknownChildKeys = new TreeSet<String>();
        int unmappedStringFields = 0;
        for (var element : children) {
            var raw = element.getAsJsonObject();
            for (String key : raw.keySet()) if (!List.of("subId", "mainId", "order", "descTextMapHash", "finishParent", "isRewind", "acceptCondComb", "finishCondComb", "failCondComb", "acceptCond", "finishCond", "failCond", "beginExec", "finishExec", "failExec", "guide", "trialAvatarList", "gainItems").contains(key)) unknownChildKeys.add(key);
            int id = raw.get("subId").getAsInt(); require(ids.add(id), "duplicate chapter id " + id);
            var quest = GameData.getQuestDataMap().get(id);
            require(quest != null && quest.getSubId() == id && quest.getMainId() == raw.get("mainId").getAsInt()
                    && quest.getMainId() == 303 && quest.getOrder() == raw.get("order").getAsInt(), "identity/order " + id);
            require(!quest.isAutomaticAcceptance() && quest.getAcceptCond().isEmpty(), "unexpected auto acceptance " + id);
            var conditions = raw.getAsJsonArray("finishCond");
            require(conditions.size() == quest.getFinishCond().size(), "finish count " + id);
            for (int i = 0; i < conditions.size(); i++) {
                var input = conditions.get(i).getAsJsonObject(); var actual = quest.getFinishCond().get(i);
                require(actual.getType().name().equals(input.get("type").getAsString()), "finish type " + id);
                require(Arrays.equals(actual.getParam(), ints(input.getAsJsonArray("param"))), "finish params " + id);
                require(actual.getCount() == (input.has("count") ? input.get("count").getAsInt() : 0), "finish progress count " + id);
                for (String key : input.keySet()) if (!List.of("_type", "type", "_param", "param", "_param_str", "param_str", "_count", "count").contains(key)) unknownConditionKeys.add(key);
                if (input.has("BGHPAKBEPND")) {
                    require(input.get("BGHPAKBEPND").getAsString().isEmpty() && actual.getParamStr().isEmpty(), "unmapped nonempty condition string " + id);
                    unmappedStringFields++;
                }
                conditionCount++;
            }
            execCount += compareExec(id, raw, "beginExec", quest.getBeginExec());
            execCount += compareExec(id, raw, "finishExec", quest.getFinishExec());
            execCount += compareExec(id, raw, "failExec", quest.getFailExec());
            var summary = new TreeMap<String, Object>();
            summary.put("subId", id); summary.put("mainId", quest.getMainId()); summary.put("order", quest.getOrder());
            summary.put("finishConditions", conditions.size()); summary.put("finishExecutions", quest.getFinishExec().size());
            summary.put("automaticAcceptance", quest.isAutomaticAcceptance()); rows.add(summary);
        }
        require(ids.first() == 30302 && ids.last() == 30364 && ids.size() == 63, "child ID range");
        require(GameData.getBeginCondQuestMap().isEmpty(), "BIN rows must not create automatic condition indexes");
        var q = GameData.getQuestDataMap().get(30302);
        require(q.getFinishCond().size() == 1 && q.getFinishCond().get(0).getType().name().equals("QUEST_CONTENT_COMPLETE_TALK")
                && Arrays.equals(q.getFinishCond().get(0).getParam(), new int[] {30302, 0}), "30302 completion condition");
        require(q.getFinishExec().size() == 2 && q.getFinishExec().get(0).getType().name().equals("QUEST_EXEC_UNLOCK_POINT")
                && Arrays.equals(q.getFinishExec().get(0).getParam(), new String[] {"3", "3"})
                && q.getFinishExec().get(1).getType().name().equals("QUEST_EXEC_UNLOCK_AREA")
                && Arrays.equals(q.getFinishExec().get(1).getParam(), new String[] {"3", "3"}), "30302 execution parameters");
        var result = new TreeMap<String, Object>();
        result.put("scope", "fixed candidate 30302 Excel rejection and 303 BIN recovery; no quest start or client");
        result.put("excelDecodeError", decodeError); result.put("excelInstalled", installed.size());
        result.put("excelSliceSHA256", sha(sample)); result.put("chapterSHA256", sha(chapterFile));
        result.put("jarSHA256", sha(jar)); result.put("matchedJarClasses", classCount);
        result.put("recovered", rows); result.put("conditionsCompared", conditionCount); result.put("executionsCompared", execCount);
        result.put("automaticIndexKeys", GameData.getBeginCondQuestMap().keySet());
        result.put("unknownConditionKeys", unknownConditionKeys);
        result.put("unknownChildKeys", unknownChildKeys); result.put("emptyUnmappedConditionStrings", unmappedStringFields);
        result.put("unknownSemantics", "unrecognized fields, target client interpretation, quest start/persistence and runtime");
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(result) + "\n");
        System.out.println("excelRejected=1 installed=0 recovered=" + rows.size() + " finishConditions=" + conditionCount
                + " executions=" + execCount + " autoIndexKeys=" + GameData.getBeginCondQuestMap().size());
    }

    private static int compareExec(int id, JsonObject raw, String field, List<QuestData.QuestExecParam> actual) {
        JsonArray input = raw.has(field) ? raw.getAsJsonArray(field) : new JsonArray();
        require(input.size() == actual.size(), field + " count " + id);
        for (int i = 0; i < input.size(); i++) {
            var row = input.get(i).getAsJsonObject(); var item = actual.get(i);
            require(item.getType().name().equals(row.get("type").getAsString()), field + " type " + id);
            var params = row.getAsJsonArray("param"); String[] expected = new String[params.size()];
            for (int j = 0; j < expected.length; j++) expected[j] = params.get(j).getAsString();
            require(Arrays.equals(item.getParam(), expected), field + " params " + id);
        }
        return input.size();
    }

    private static int[] ints(JsonArray array) {
        int[] out = new int[array.size()]; for (int i = 0; i < out.length; i++) out[i] = array.get(i).getAsInt(); return out;
    }

    private static int checkClasses(Path repo, Path jar) throws Exception {
        int count = 0;
        try (var zip = new ZipFile(jar.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement(); String name = entry.getName();
                if (!name.endsWith(".class") || Arrays.stream(CLASSES).noneMatch(x -> name.equals(x + ".class") || name.startsWith(x + "$"))) continue;
                Path compiled = repo.resolve("build/classes/java/main").resolve(name);
                require(Files.isRegularFile(compiled) && Arrays.equals(zip.getInputStream(entry).readAllBytes(), Files.readAllBytes(compiled)), "class mismatch " + name);
                count++;
            }
        }
        require(count == 37, "unexpected class scope " + count); return count;
    }

    private static String sha(Path path) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))); }
    private static void requireHash(Path path, String expected) throws Exception { require(sha(path).equals(expected), "hash mismatch " + path); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
