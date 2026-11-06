import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.ResourceLoader;
import emu.grasscutter.data.excels.ChapterData;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;

/** Offline, isolated production ChapterData loader. Does not invoke loadAll. */
public final class D02ChapterAudit {
 public static void main(String[] args) throws Exception {
  if (args.length != 3) throw new IllegalArgumentException("repo chapter output");
  Path repo=Path.of(args[0]).toAbsolutePath(), input=Path.of(args[1]).toAbsolutePath(), output=Path.of(args[2]).toAbsolutePath();
  Path jar=Path.of(ResourceLoader.class.getProtectionDomain().getCodeSource().getLocation().toURI());
  check(hash(jar).equals("dfdebbda0352055540c44a54e2eebfd4ffdfe679db74270044d3d55aeb006ec3"), "jar hash");
  check(hash(input).equals("6c291ee3b5ca1e29b1b4395c1e13103d1965e1ce687eb3e0364e08dfcc55f164"), "chapter hash");
  check(System.getenv("LUNAGC_RESOURCES_DIR")==null, "resource override");
  var classes=List.of("emu/grasscutter/data/ResourceLoader", "emu/grasscutter/data/excels/ChapterData", "emu/grasscutter/data/GameData", "emu/grasscutter/utils/JsonUtils", "emu/grasscutter/utils/JsonAdapters");
  try (var zip=new ZipFile(jar.toFile())) {
   for (String prefix:classes) {
    var entries=zip.stream().filter(e -> e.getName().equals(prefix+".class") || (e.getName().startsWith(prefix+"$") && e.getName().endsWith(".class"))).toList();
    check(!entries.isEmpty(), "missing archive class "+prefix);
    for (var entry:entries) check(Arrays.equals(zip.getInputStream(entry).readAllBytes(), Files.readAllBytes(repo.resolve("build/classes/java/main").resolve(entry.getName()))), "class mismatch "+entry.getName());
   }
  }
  check(ChapterData.getBeginQuestChapterMap().isEmpty() && ChapterData.getEndQuestChapterMap().isEmpty(), "not fresh");
  var raw=JsonParser.parseString(Files.readString(input)).getAsJsonArray();
  var expected=new LinkedHashMap<Integer,List<Integer>>();
  var begin=new LinkedHashMap<Integer,List<Integer>>(); var end=new LinkedHashMap<Integer,List<Integer>>();
  var idCounts=new TreeMap<Integer,Integer>(); var beginCounts=new TreeMap<Integer,Integer>(); var endCounts=new TreeMap<Integer,Integer>();
  for (var value:raw) {
   JsonObject row=value.getAsJsonObject(); int id=integer(row,"id"), b=integer(row,"beginQuestId"), e=integer(row,"endQuestId"), level=integer(row,"needPlayerLevel");
   expected.put(id,List.of(b,e,level)); begin.put(b,List.of(id,b,e,level)); end.put(e,List.of(id,b,e,level));
   idCounts.merge(id,1,Integer::sum); beginCounts.merge(b,1,Integer::sum); endCounts.merge(e,1,Integer::sum);
  }
  var errors=new ArrayList<String>();
  var appender=new AppenderBase<ILoggingEvent>() { protected void append(ILoggingEvent e) { if (e.getLevel().isGreaterOrEqual(Level.ERROR)) errors.add(e.getFormattedMessage()); } };
  appender.setContext(Grasscutter.getLogger().getLoggerContext()); appender.start(); Grasscutter.getLogger().addAppender(appender);
  var map=new Int2ObjectOpenHashMap<ChapterData>();
  var loader=ResourceLoader.class.getDeclaredMethod("loadFromResource",Class.class,Path.class,Int2ObjectMap.class);
  loader.setAccessible(true); loader.invoke(null,ChapterData.class,input,map);
  Grasscutter.getLogger().detachAppender(appender); appender.stop();
  check(errors.isEmpty(), "loader logged ERROR: "+errors);
  check(map.size()==expected.size(), "installed id count");
  for (var entry:expected.entrySet()) {
   var c=map.get((int)entry.getKey()); var v=entry.getValue();
   check(c!=null && c.getId()==entry.getKey() && c.getBeginQuestId()==v.get(0) && c.getEndQuestId()==v.get(1) && c.getNeedPlayerLevel()==v.get(2), "projection mismatch id "+entry.getKey());
  }
  check(ChapterData.getBeginQuestChapterMap().size()==begin.size() && ChapterData.getEndQuestChapterMap().size()==end.size(), "index sizes");
  for (var e:begin.entrySet()) check(projection(ChapterData.getBeginQuestChapterMap().get(e.getKey())).equals(e.getValue()), "begin last writer "+e.getKey());
  for (var e:end.entrySet()) check(projection(ChapterData.getEndQuestChapterMap().get(e.getKey())).equals(e.getValue()), "end last writer "+e.getKey());
  var rows=new TreeMap<Integer,Map<String,Integer>>();
  map.forEach((id,c)->rows.put(id,Map.of("beginQuestId",c.getBeginQuestId(),"endQuestId",c.getEndQuestId(),"needPlayerLevel",c.getNeedPlayerLevel())));
  var result=new TreeMap<String,Object>(); result.put("installed",rows); result.put("begin30302",id(ChapterData.getBeginQuestChapterMap().get(30302))); result.put("end30302",id(ChapterData.getEndQuestChapterMap().get(30302))); result.put("beginIndexSize",ChapterData.getBeginQuestChapterMap().size()); result.put("endIndexSize",ChapterData.getEndQuestChapterMap().size()); result.put("loadedAll",ResourceLoader.isLoadedAll()); result.put("sourceRows",raw.size()); result.put("idCounts",idCounts); result.put("beginCounts",beginCounts); result.put("endCounts",endCounts); result.put("errors",errors);
  check(!ResourceLoader.isLoadedAll(), "loadAll unexpectedly true");
  Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(result)+"\n");
 }
 static List<Integer> projection(ChapterData c) { return c==null ? List.of() : List.of(c.getId(),c.getBeginQuestId(),c.getEndQuestId(),c.getNeedPlayerLevel()); }
 static int integer(JsonObject row,String name) { return !row.has(name) || row.get(name).isJsonNull() ? 0 : row.get(name).getAsInt(); }
 static int id(ChapterData c) { return c==null ? -1 : c.getId(); }
 static String hash(Path p) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p))); }
 static void check(boolean ok,String message) { if (!ok) throw new IllegalStateException(message); }
}
