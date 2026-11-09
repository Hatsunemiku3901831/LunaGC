import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import com.google.gson.GsonBuilder;
import emu.grasscutter.data.*;
import emu.grasscutter.utils.FileUtils;
import org.slf4j.LoggerFactory;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** One fresh JVM, one unmodified production loadAll call. */
public final class D02NaturalLoadFirstAudit {
 static final List<Map<String,Object>> diagnostics = Collections.synchronizedList(new ArrayList<>());
 static long sequence;
 public static void main(String[] args) {
  int exit=0;
  try {
   if (args.length != 2) throw new IllegalArgumentException("result path and config SHA required");
   Logger root=(Logger)LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
   var observer=new AppenderBase<ILoggingEvent>() { protected void append(ILoggingEvent event) {
    if (event.getLevel().isGreaterOrEqual(Level.WARN)) synchronized(diagnostics) {
     var row=new LinkedHashMap<String,Object>(); row.put("sequence",++sequence); row.put("thread",event.getThreadName()); row.put("logger",event.getLoggerName()); row.put("level",event.getLevel().toString()); row.put("message",event.getFormattedMessage());
     row.put("throwable",event.getThrowableProxy()==null ? null : ThrowableProxyUtil.asString(event.getThrowableProxy())); diagnostics.add(row);
    }
   }};
   observer.setContext(root.getLoggerContext()); observer.start(); root.addAppender(observer);
   var result=new LinkedHashMap<String,Object>(); result.put("threadsBefore",threads()); boolean bootstrapped=false;
   try {
    Class.forName("emu.grasscutter.Grasscutter"); bootstrapped=true;
    var appenders=new ArrayList<String>(); root.iteratorForAppenders().forEachRemaining(a -> appenders.add(a.getClass().getName())); result.put("rootAppenders",appenders);
    if (!root.isAttached(observer)) throw new IllegalStateException("diagnostic observer detached");
    if (appenders.stream().anyMatch(s -> s.contains("Async") || s.contains("FileAppender") || s.contains("ServerLogEvent"))) throw new IllegalStateException("forbidden appender " + appenders);
    System.out.println("BOOTSTRAP_OK"); System.out.flush();
    var actualPaths=paths(); result.put("paths",actualPaths);
    validatePaths(actualPaths, Path.of(args[0]).toAbsolutePath().getParent().resolve("work"), args[1]);
    boolean initial=ResourceLoader.isLoadedAll(); result.put("initialLoadedAll",initial);
    if (initial) throw new IllegalStateException("not a fresh JVM");
    System.out.println("LOADALL_ENTERING"); System.out.flush();
    ResourceLoader.loadAll();
    result.put("outcome","RETURNED"); System.out.println("RETURNED");
   } catch(Throwable t) {
    exit=1; result.put("outcome","THREW"); var sw=new java.io.StringWriter(); t.printStackTrace(new java.io.PrintWriter(sw)); result.put("throwable",sw.toString()); System.out.println("THREW"); t.printStackTrace();
   }
   try {
    result.put("threadsAfter",threads());
    if (bootstrapped) { result.put("loadedAll",ResourceLoader.isLoadedAll()); result.put("resources",resources()); result.put("mainQuest303",GameData.getMainQuestDataMap().containsKey(303)); }
   } catch (Throwable t) {
    exit=1; var sw=new java.io.StringWriter(); t.printStackTrace(new java.io.PrintWriter(sw)); result.put("observationFailure",sw.toString()); t.printStackTrace();
   } finally { synchronized(diagnostics) { result.put("diagnostics",new ArrayList<>(diagnostics)); } }
   Files.writeString(Path.of(args[0]),new GsonBuilder().setPrettyPrinting().create().toJson(result));
  } catch(Throwable t) { t.printStackTrace(); exit=1; }
  System.exit(exit);
 }
 static void validatePaths(Map<String,Object> actual, Path work, String expectedConfigHash) throws Exception {
  work=work.toRealPath();
  if (!Path.of("").toRealPath().equals(work)) throw new IllegalStateException("unexpected CWD");
  for (String name:List.of("resources","scripts","data","plugins","cache","packets")) {
   Path expected=name.equals("scripts") ? work.resolve("resources/Scripts") : work.resolve(name);
   if (!Path.of((String)actual.get(name)).toRealPath().equals(expected.toRealPath())) throw new IllegalStateException("unexpected path: "+name);
  }
  Path config=emu.grasscutter.Grasscutter.configFile.toPath().toRealPath();
  if (!config.equals(work.resolve("config.json").toRealPath())) throw new IllegalStateException("unexpected config path");
  String actualHash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(config)));
  if (!actualHash.equals(expectedConfigHash)) throw new IllegalStateException("config hash mismatch");
  actual.put("config",config.toString()); actual.put("configSha256",actualHash);
 }
 static List<String> threads() { return Thread.getAllStackTraces().keySet().stream().map(t->t.getName()+":"+t.getState()).sorted().toList(); }
 static Map<String,Object> paths() throws Exception {
  var out=new LinkedHashMap<String,Object>();
  for(String p:List.of("resources","scripts","data","plugins","cache","packets")) {
   Path path=switch(p) { case "resources" -> FileUtils.getResourcePath(""); case "scripts" -> FileUtils.getScriptPath(""); case "data" -> FileUtils.getDataUserPath(""); case "plugins" -> FileUtils.getPluginPath(""); case "cache" -> FileUtils.getCachePath(""); default -> FileUtils.getPacketPath(""); }; out.put(p,path.toAbsolutePath().toString());
  }
  var data=new LinkedHashMap<String,Object>();
  for(String name:List.of("AbilityEmbryos.json","OpenConfig.json","Spawns.json","GadgetSpawns.json","QuestEncryptionKeys.json","BlossomConfig.json","ActivityConfig.json")) { Path p=FileUtils.getDataPath(name); var x=new LinkedHashMap<String,Object>(); x.put("uri",p.toUri().toString()); x.put("exists",Files.exists(p)); x.put("sha256",Files.exists(p)?HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p))):null); data.put(name,x); } out.put("dataResolution",data); return out;
 }
 static List<Map<String,Object>> resources() {
  var rows=new ArrayList<Map<String,Object>>();
  for(var cls:ResourceLoader.getResourceDefClasses()) { var annotation=cls.getAnnotation(ResourceType.class); var row=new LinkedHashMap<String,Object>(); row.put("class",cls.getName()); row.put("priority",annotation.loadPriority().name()); var names=new LinkedHashMap<String,Object>();
   for(String name:annotation.name()) { var variants=new LinkedHashMap<String,Boolean>(); String base=name.replaceFirst("\\.[^.]+$",""); for(String dir:List.of("Server","ExcelBinOutput")) for(String ext:List.of("tsj","json","tsv")) { Path p=FileUtils.getResourcePath(dir+"/"+base+"."+ext); variants.put(p.toString(),Files.exists(p)); } var n=new LinkedHashMap<String,Object>(); n.put("selected",FileUtils.getExcelPath(name).toString()); n.put("variants",variants); names.put(name,n); }
   row.put("paths",names); var map=GameData.getMapByResourceDef(cls); row.put("mapSize",map==null?null:map.size()); rows.add(row);
  } return rows;
 }
}
