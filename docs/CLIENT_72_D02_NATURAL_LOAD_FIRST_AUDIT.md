# E-01/R3-NATURAL-LOAD-FIRST-AUDIT：真实 loadAll 首诊准备

本项只审当前生产入口的可执行性。2026-11-09 的本轮没有调用 `loadAll`、没有编译或构建、没有启动服务/客户端/数据库、没有查询或修改端口，没有下载、同步或执行资源脚本；没有 Git 操作。父任务提供的源码起点为 `09d2ba7`，本项未自行查询 Git。现有 152 类反射、章节加载和 303 启动/保存/未就绪拒绝成果直接复用，不重跑、不增计。

**结论：生产 `ResourceLoader.loadAll()` 有可直接调用的公开入口，无须先启动 GameServer 或 MongoDB；但“独立 JVM + 临时 config/data”不构成 Lua 沙箱，当前未选定并固定自然加载输入，也没有这三个执行脚本域的完整批准清单，因此不能直接指向原 resources 运行。** 可以在主线选定的固定部分资源快照中真实运行同一入口，所有步骤照常调用，并把缺文件、解码拒绝、异常或超时作为诊断结果；该实验只证明选定字节的实际执行，不证明完整资源兼容、revision 对齐、resourceReady 或正向 QuestManager 重绑定。

本文件是执行规格，不是执行结果。当前实际首缺仍未知；不能把下面的源码顺序、缺失候选或已有静态 manifest 当作一次 loadAll 的结果。

## 1. 固定源码、类与既有输入

本轮逐字节读取的完整指纹清单为 `local/migration72/d02-resume-20261109/r3-natural-load-audit/source-input-class-audit.private.json`，SHA-256 `f5fc932b0ea4501625a5a7db526a5ce63d25885bf5dfb0cdaa8c86899e6259cb`。它含 31 个源码/工具/配置文件的大小及 SHA、两个 JAR 的 24 个生产类族（含内部类）与当前 `build/classes/java/main` 的逐字节比较、五个 LuaJ 依赖类和本节固定输入。24 类族在两个 JAR 中均与当前编译 class 相同；这不是整个 JAR、全部资源类/活动 handler 的一致性证明，也不能单凭比较旧 class 证明当前源文件由何次构建生成。主线执行前仍应核其已有构建来源记录。

| 当前源入口 | SHA-256 |
| --- | --- |
| `src/main/java/emu/grasscutter/data/ResourceLoader.java` | `fa7b7d26ef234e92f6d7eac5790922d36dc76fb113b5a31cc80c7bad40d2a015` |
| `src/main/java/emu/grasscutter/Grasscutter.java` | `686a3618465320de72e5cf3d0101e4c673ed05489dea7e174e2e4cb1377652a9` |
| `src/main/java/emu/grasscutter/utils/FileUtils.java` | `863e4abebbfe6e7f150e7d0d36c7da7bb8268454ec19ae694bd3e9ef5c3aa883` |
| `src/main/java/emu/grasscutter/data/DataLoader.java` | `65055d1d026115fc42e44fa9851955acde04c4ae4a7de24e1b3fd369b932e019` |
| `src/main/java/emu/grasscutter/scripts/ScriptLoader.java` | `6f24ea24be4f9a0d7839e88adf7010cec05e86fb987e91cff09b5b26dd6d23e8` |
| `src/main/java/emu/grasscutter/scripts/ScriptLibUserdata.java` | `438cba69845933820ef210d3345da8cb338889f19c95ad6002b3c190e59503ad` |
| `src/main/java/emu/grasscutter/utils/Utils.java` | `5e1f7afd65b57b209d116dab05eb19a5ec37e1537435ad20659412d7ca047664` |
| `src/main/java/emu/grasscutter/config/ConfigContainer.java` | `5b87a1f13ea603d98dd18217c8d3917976e7762bd57e8084509e1b3d80d43ba6` |
| `src/main/java/emu/grasscutter/utils/lang/Language.java` | `943542d2e3e782672bd6d37e6a1a2ad804150e6ecccdef3409297d1615111aca` |

本轮实读根产物 `LunaGC-7.1.0.jar` 的 SHA 为 `8f093e19b839ff1263984c596584d1ae12e6d4fd841d82562aaf30f998267291`；既有章节归档 `local/migration72/builds/LunaGC-d02-quest-dfdebbda.jar` 为 `dfdebbda0352055540c44a54e2eebfd4ffdfe679db74270044d3d55aeb006ec3`。新 runner 必须只选其中一个明确固定的产物及构建来源，不能混用旧章节 JAR 名称与当前根 JAR hash。

| 可直接复用的真实字节 | 大小 | SHA-256 |
| --- | ---: | --- |
| `local/migration72/offline-sol-terra/chapter-audit/ChapterExcelConfigData.json` | 202292 | `6c291ee3b5ca1e29b1b4395c1e13103d1965e1ce687eb3e0364e08dfcc55f164` |
| `local/migration72/d02-r3-candidate/ExcelBinOutput/QuestExcelConfigData.json` | 7903186 | `c8561299f543456573b0fa16404e210bcd2ecf84ce5006eb63dd4b2845137a87` |
| `local/migration72/d02-r3-candidate/BinOutput/Quest/303.json` | 52037 | `aaf6901da4c30528047c1210bc636f810b368d5fb2f19aad6369bbb956dd3cca` |

来源仍为候选 commit `d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1`、根 tree `5b334e711319726c504da95dbfe6ebb26baaa7d3`；章节 blob `8f8f8fa23df84686883d931f248e6ff0b626ddd7`。本轮复核 SHA，没有重新抓取 tree、声明 revision 对齐或扩大原字段许可。三文件是足以明确指定的**部分输入选项**，不是完整自然资源集；主线若采用，应将未加入文件明确记为实验缺口，不能用旧表/alias 填补。

既有 `tools/D02ChapterAudit.java` SHA `87de0a9d1bd53c3678304ce28cdfd32df3e57d613794099bfc955487a23d70d7`；`tools/audit_d02_chapter_run.py` SHA `b6c00e4f32c38c9292a3a45b843bad0ab29cc2a793839a4a5e8c0faeed3da9a3`。可复用其独立 CWD、config、归档 class 比较、日志与 90 秒预算结构；不能原样运行它代替 loadAll：它只调用 `loadFromResource`，禁止 ScriptLoader 初始化，而且缺缓存时会联网取章节。本项新 runner 应仅使用已固定本地字节并拒绝下载。

## 2. 调用与副作用图（源码审计）

下文 `W` 为唯一新 run 目录，`R`/`S`/`D` 分别为该 run 的 resources/scripts/data。括号中的行号是本轮源文件位置。

```text
新 JVM 的 driver
  -> 首次初始化 Grasscutter（Grasscutter.java:35-94）
       -> Reflections("emu.grasscutter") 扫描类元数据
       -> LoggerFactory 初始化；声明一个 6 线程 ThreadPoolExecutor（不预启动核心线程）
       -> ./config.json 读取；不存在则生成；旧 version 可迁移、写回并重读
       -> 三个 LUNAGC_* 覆盖应用于内存配置
       -> Language.getLanguage 读取 JAR /languages；FileUtils 固化所有路径
       -> Utils.startupCheck（Utils.java:146-182）
            检查 R、R/BinOutput、R/ExcelBinOutput；缺目录可能建 R 后 System.exit(1)
            缺 D 时 mkdir；Server/ScriptSceneData 缺失只记录
            DataLoader.checkAllFiles 扫默认目录，复制循环已注释，不复制默认文件
  -> ResourceLoader.isLoadedAll() 必须为 false
  -> ResourceLoader.loadAll()（ResourceLoader.java:85-132），只调用一次
       -> ScriptLoader.init：真实 LuaJ 引擎、枚举、ScriptLib/Java 绑定
       -> Config/Talent/OpenConfig/Ability/Embryo/Widget：文件读、内存 catalog
       -> loadResources(true)：同优先级 parallel/unordered，真实 decode/onLoad/map
       -> Ability 索引、GameDepot 权重、Spawns、Quest/303 fallback、ScriptSceneData
       -> Scene Point/Home/NPC/Routes/Blossom/Talent 索引
       -> ActivityManager 静态配置：DataLoader、反射构造 handler/watcher、initWatchers
       -> LevelEntity
       -> Quest Share Lua：R/S 路由见下一节，真实 compile/eval
       -> Server 映射、ActivityCond、groups_replacement Lua、TrialAvatar、GlobalCombat
       -> EntityControllerScriptManager：S/Gadget/*.lua 真实 compile/eval
       -> finish 日志；生产代码自然写 loadedAll=true
  -> 记录返回/异常、诊断、实际路径、线程；正常退出独立 JVM
```

数据文件路径本身的分支均为读取，内存静态 maps/catalog/缓存会被改变。`FileUtils.getDataPath`（124）按用户 D 存在项优先，再使用 **JAR 内 defaults/data**；因此空 D 并不等于无默认输入。`DataLoader.loadReader`/loadList/loadClass 通过该路径读 Spawns、GadgetSpawns、QuestEncryptionKeys、BlossomConfig、ActivityConfig；OpenConfig 和 AbilityEmbryos 也走同样默认选择。应固定整个 JAR，并在输出记录这几个路径解析到用户文件还是 JAR URI；JAR 默认不是新增回退建议，而是现行源码的实际行为。本轮没有触发或修改该行为。

`FileUtils.getExcelPath`（152）先找 `R/Server`，再找 `R/ExcelBinOutput`，各自优先 `.tsj > .json > .tsv`；文件均缺时返回不存在的 `.tsj` 路径。不能只固定 ExcelBinOutput JSON 就声称真实读取了它：必须记录同名 Server/TSJ/TSV 的存在性及实际解析路径。R 为普通目录即可，不建议本次采用 zip；zip 模式还会扫描寻找 Excel 根并长期保留 zip 文件系统。

配置/写文件边界：`Grasscutter.configFile` 固定为相对 `./config.json`，必须以真实子进程 CWD 进入 W；运行后更改 `user.dir` 或 Java 内存 config 无法重新定位已固化的 FileUtils 静态路径。`version=14` 的有效预写 config 避免无意迁移；本轮不修改 version 逃避业务门禁。日志默认写相对 `logs/`，含异步 appender 及 ServerLogEvent 入口，应由受控 console-only logback 替换。`Language.getLanguage` 本身读内置翻译；TextMap 的磁盘 cache 是 `Language.loadTextMaps` 的后续能力，loadAll 未直接调用它，不能将其列为本轮已发生的写入。仍将 cache/temp 路径设在 W，若实际出现 TextMap 调用或额外文件则记录，不能把“可能”写成“已触发”。

直接入口没有 `DatabaseManager`/`DatabaseHelper` 启动调用，也不调用 `Grasscutter.main`、GameServer.start、HTTP/Dispatch、插件加载或控制台循环；静态 `Grasscutter` 引用不等于完整服务启动。资源 onLoad 的定向源码搜索未见直接 DB/网络/线程/文件写入入口，主要是索引和引用修整；活动静态加载会按实际配置构造 handler/watcher。此搜索不承诺任意未审 class/脚本绝无间接副作用，执行前的类初始化/线程/路径证据仍需保留。

线程边界：Excel 使用公共 ForkJoinPool，日志默认有 async worker，Reflections/Java 平台也可能创建辅助线程；ScriptLib 构造器只建 FastThreadLocal，但业务成员存在 scheduler/保存调用。独立 JVM 避免污染现有测试静态状态；不能在既有 JUnit/Mongo 进程反复调用 loadAll，第二次 ScriptLoader.init 会拒绝，半途失败也不会恢复前面已经修改的静态 maps。

## 3. 必经 Lua 的准确边界与当前停止条件

生产会自然执行的三个域如下；本轮未展开其全部正文，没有运行其中任何 Lua。

| 执行入口 | 枚举路径 | 实际取源路径 |
| --- | --- | --- |
| `ResourceLoader.loadQuestShareConfig`（944） | `R/Scripts/Quest/Share/Q*ShareConfig.lua` | `ScriptLoader.getScript("Quest/Share/" + filename)` → `S/Quest/Share/filename`；枚举后还受原正则过滤 |
| `ResourceLoader.loadGroupReplacements`（1135） | 无目录枚举 | `S/Scene/groups_replacement.lua` |
| `EntityControllerScriptManager.cacheGadgetControllers`（21） | `S/Gadget/*.lua` | `S/Gadget/filename` |

`S` 来自 `folderStructure.scripts`，可为 `resources:Scripts/` 或独立绝对目录。R/S 不同会造成“按一份目录枚举，执行另一份字节”，不能只固定其中之一。`ScriptLoader.readScript`（218）还可读 JAR `/historical-scripts/<path>`；AsterMiddle/AsterBig 两个 Common 的注释占位符也可选历史内置内容。两个受核 JAR 中，上述三个直接域的 historical 条目均为 **0**，所以若 R/S 的这三域自然没有脚本，不会在这些直接入口凭空执行内置替代；若以后加入可执行脚本，则必须把其 `require`/其他实际动态依赖及内置 Common 一起固定。

原 resources 的直接目录元数据为 Quest Share 2882 文件/15373276 字节、Gadget 117 文件/93014 字节；`Scripts/Scene/groups_replacement.lua` 为 83486 字节，SHA `a11a735eedb8f8f1dd6aa5253baacf472160507be3f4da62b03e3065323a4d48`。这只是入口规模与一个文件的身份，不是批准清单或完整依赖闭包。已有 25 组/505/601 的场景测试不自动覆盖这些域；无需为此重做场景网格与事件套件。

本轮用 portable JDK 的 `javap -c -p` 静态读固定 dfdebb JAR 依赖，没有初始化目标类。证据 `local/migration72/d02-resume-20261109/r3-natural-load-audit/luaj-dependency-javap.txt`，SHA `034aaf284d70ed0d9a7a99a2fc53efccb1fc3ff02b79d5b814a97c6f9007d2f5`。LuaJ 3.0.1 的 `LuajContext(true,false)` 选择 `JsePlatform.debugGlobals()`，继续装载 standard globals 中的 `JseIoLib`、`JseOsLib`、`LuajavaLib`、PackageLib，再装 DebugLib。依赖字节码可见 RandomAccessFile、Runtime.exec、文件删除/重命名及 Java 反射能力；不是仅数据赋值的解释器。`LuajContext.class` SHA `1137950b920ec7ea6d3b4917f3c4b7d5ac0eb19dcc21c287658e12ea610b1652`，两个 JAR 的五个已核 LuaJ 类 hash 均一致。

此外 `ScriptLibUserdata.get` 最后 `delegate.get(key)`（约 174），未列出的成员仍通过完整 ScriptLib 访问，并非成员白名单。`ScriptLib.java:836` 有 scheduler 调用，1302 有 scene group `instance.save()`；这些通常需要场景上下文，本轮未断言会在顶层发生。Lua Java 互操作本身也可访问外部类，不能因 driver 不创建 Player/Scene 就宣称已隔离外部副作用。

因此，直接运行原 resources 的分支准确停在：**缺少本次 R/S 可执行脚本的固定批准闭包**。临时 config/data、堆限制、超时、关闭 HTTP 开关都不限制 Lua 的绝对文件路径/子进程/Java 调用。未审上游脚本继续禁止执行；本项不为解除此条件扩展到全量脚本迁移或审计，也不建议修改生产 Lua globals、屏蔽步骤或临时 mock ScriptLib。

可独立采用的受控分支是主线明确选定一个部分资源快照，R/S 一致，三个执行域自然没有 Lua，并核已选 JAR 的上述 historical 域为空。所有生产步骤仍实际调用，缺脚本按现行源码报错并继续。该分支只能报告“这个固定部分输入的 loadAll 首诊”，不代表运行原 resources，也不把缺全部 161 表列为 P0。若输入声明需要某个已审 Lua，则应复用其现有证据并固定精确源/依赖；缺一项入口信息就停在该脚本分支，不猜路径或填空。

## 4. 建议的新 driver 与受控配置（尚未实施）

主线授权实施后建议新增 `tools/D02NaturalLoadFirstAudit.java` 与 `tools/audit_d02_natural_load_first.py`；本轮只新增本文及忽略目录证据，没有创建这两个 driver。实现复用章节 runner 的结构，不修改生产加载器，不再扫描/重新研究 152 类反射，不复写章节/Quest 保存测试。

runner 在 `local/migration72/d02-resume-20261109/r3-natural-load-audit/run-<unique>/` 创建：`classes/`、`work/`、`input-manifest.json`、`stdout.log`、`stderr.log`、`class-init.log`、结果 JSON 和超时/退出记录。复制清单明确列出的本地普通文件；不取整仓 resources、不借原 data、不创建指向原目录的 junction/symlink/hardlink。R/S/D、plugins/cache/packets/temp 全部在 work 内；无网络下载分支，无 Mongo/Gradle/local_windows 启动调用。

工作目录中的最小 config 规格如下，`<W>` 由 runner 写入真实绝对目录；完整生成文本及 hash 要入本机证据。

```json
{
  "version": 14,
  "folderStructure": {
    "resources": "<W>/resources",
    "scripts": "resources:Scripts/",
    "data": "<W>/data",
    "plugins": "<W>/plugins",
    "cache": "<W>/cache",
    "packets": "<W>/packets"
  },
  "language": {"language": "en_US", "fallback": "en_US", "document": "EN"},
  "server": {
    "fastRequire": true,
    "http": {"startImmediately": false},
    "game": {"enableConsole": false}
  }
}
```

至少预建 R、R/BinOutput、R/ExcelBinOutput 和 D，使静态 `startupCheck` 能通过目录前置；Server/ScriptSceneData 是否建空应由输入清单明确记录。不要生成空 JSON 表或别名冒充真实输入。所有路径均在初始化前写完，并在调用后读取 `FileUtils` 实际根路径核对。DB 配置不应使用真实账号/业务库；本入口不需要 DB，若出现 DB 初始化即失败停止，不能临时起库让它继续。改 URI 不能作为网络沙箱或数据库未访问的证明。

对子进程清除 `LUNAGC_RESOURCES_DIR`、`LUNAGC_MONGO_URI`、`LUNAGC_PUBLIC_ADDRESS` 的继承覆盖；固定 `LUNAGC_RUNTIME=portable`。清除额外 JVM 注入变量（`JAVA_TOOL_OPTIONS`、`JDK_JAVA_OPTIONS`、`_JAVA_OPTIONS`、`CLASSPATH`），只记录有无及使用策略，不输出凭据。`java.io.tmpdir`、子进程 TEMP/TMP 可设 W/temp。显式 classpath 仅为新 driver classes 和选定 JAR，不能把当前整个测试 classes 叠入。

使用 console-only 同步 logback：启动参数指定 W/logback.xml，并在 W/src/main/resources/logback.xml 放同一文本，覆盖 Grasscutter 静态块强设的相对配置位置。stdout/stderr 写到本 run；driver 在触及 Grasscutter 静态方法前，通过 LoggerFactory 的 logger 名称安装诊断 appender，避免“为装观察器先触发 bootstrap”。初始化后再核 root appender 中无 AsyncAppender、ServerLogEventAppender 或原日志文件。诊断 appender 用线程安全集合保留 WARN/ERROR 的 logger、thread、message、异常类型、调用顺序与完整可用 stack，不以只收 ERROR 漏掉行拒绝。配置/输入前后 hash 和新增文件清单均入证据。

Java 主流程只需：校验参数/输入/JAR → 安装日志观测 → 标记 bootstrap → 初始化并核实际路径 → 断言初始 `isLoadedAll()==false` → 写 `LOADALL_ENTERING` 标记 → **直接一次 `ResourceLoader.loadAll()`** → 记录 RETURNED 或 THREW、自然 `isLoadedAll()`、诊断和必要 map 事实 → 退出。允许在 driver 最外层捕获 Throwable 用于写失败证据，不能吞掉或修改生产异常；禁止预调 ScriptLoader.init、逐步手动调用 loader 或反射写 loadedAll。真正的 Java 调用不需要新增生产入口：

```java
if (ResourceLoader.isLoadedAll()) throw new IllegalStateException("not a fresh JVM");
ResourceLoader.loadAll();
boolean naturallyFinished = ResourceLoader.isLoadedAll();
```

复用章节 runner 的编译/运行各 **90 秒**上限；首轮只运行一个新 JVM，先人工审其输入/日志/边界，再决定是否需要第二个独立 JVM。建议部分输入首轮 `-Xms64m -Xmx1024m`，具体参数固定入结果；超时/堆不足只归为实验未完成，不能命名成资源首缺。超时仅结束该 runner 持有且身份核对的 Java 子进程，保留目录；本次受控分支没有 DB，不要复用 Quest303 runner 的 Mongo 启停部分。工具等待期间须继续按主线规则反馈进展。

class-init 审计允许真实 ScriptLoader/ScriptLib/EntityControllerScriptManager/ActivityManager 初始化；仍拒绝 `emu/grasscutter/database/`、MongoClients、HttpServer、DispatchServer、GameServer 的初始化，以及插件/控制台启动。不要沿用章节 runner 将 ScriptLoader 也列禁止的旧断言。记录 bootstrap/结束线程清单；Excel/ForkJoin/正常日志线程不冒称网络服务。若实际出现新进程、服务类、DB、网络或输入外写入，结束本次实验并停在该准确路径，不继续猜依赖。

## 5. 首缺断言及结果解释

需要分开以下事实，不使用单一退出码或 loadedAll 标志作为通过结论：

1. `BOOTSTRAP_OK` 与 `LOADALL_ENTERING` 必须可见；若 `startupCheck`/config/class-init 先退出，这是启动前置失败，尚未实际进入 loadAll。
2. 主流程记录第一个逃出 loadAll 的异常及 stack；若没有逃出，记录自然 RETURNED/loadedAll。现行代码会捕获大量错误后继续，最后仍可能自然置 true；新 JVM 退出即可，不恢复或手改该标志，更不据此运行正向 QuestManager。
3. 记录真实日志中首个 WARN、首个 ERROR、全部行/entry rejection 计数与样本、缺目录/文件及关键 maps 输出。WARN 包括 ResourceLoadDiagnostics、空目录和回退信息，需逐项归因。不是每条 WARN 都是资源首缺。
4. 对按源码串行执行的早期阶段，可按诊断定位；Excel 同优先级无序并行，文件错误在加载后由队列统一输出，所以“首条打印的表错误”不等于确定的首个执行失败文件。输出该优先级/集合及每个实际路径，不人为排序出一个全局首缺。复跑比较规范化诊断集合/输入/关键状态，不要求原始线程日志和时间戳逐字节相等。
5. 缺失表只表示本次快照没提供或真实 loader 没读到；必须结合 D-02 的实际调用依赖决定下一项，不能把 161 表统一晋级 P0。已知 30302 LogicType object 拒绝保留为既有局部证据，新实验若尚未走到它，不得将它命名为本次首缺。

如果主线选择上面的三文件部分输入作为第一轮，R/BinOutput/Avatar 不存在且无其他插入文件时，源码预测的首个 config 诊断是 `Failed to load emu.grasscutter.data.binout.config.ConfigEntityAvatar folder.`；只有真实执行日志才能确认。应断言日志/stack 正确归到 `loadConfigData`，随后保留实际后续结果，不能把这个人为限定输入的缺目录泛化为原 resources 的缺陷。若主线选择更多已审固定输入，则撤下该预测，按真实新日志取首诊。

## 6. 主线需要补齐的输入与下一步

主线先核本轮原证据，再确定以下入口信息：

- 采用哪个固定 JAR及对应源/class 构建证明；新 driver 的 source/class hash。
- 准确 R/S/D 文件集合：采用三文件部分输入，还是另一个已经有逐文件来源的快照；不得仅给目录名或 static-manifest。
- 对三个 Lua 执行域选择自然无脚本的部分输入，或提供已有批准的精确脚本/动态依赖/历史内置来源。未提供时只停该可执行脚本分支，不要求补齐所有场景或所有表。
- 每个 DataLoader 用户项是否故意缺省至已固定 JAR；Server/Excel 的 TSJ/JSON/TSV 竞争情况、受控 config/logback 文本，以及预算和结果路径。

补齐后可实现并审核两个新 driver，再由主线实际运行真实 loadAll。当前没有自然运行结果，没有新增客户端验收范围；D02-R3/resourceReady、正向 QuestManager 重绑定及 D-02 父项仍未完成。未操作或重写原 `resources/ScriptSceneData/flat.luas.scenes.full_globals.lua.json` 的他人差异，未改 root config、mongodata 或 local/data；本项不产生部署需求。本轮新增审计文档及忽略证据由主线审核后纳入其文档索引/本地提交，子任务不自行提交或改现有 Project/Harness/交接文件。

## 主线收尾审核

主线已读实际生产loadAll入口及本专题，独立复核31个当前source/tool、四个固定候选、两JAR的24类族共192条class字节与当前build、10条LuaJ依赖class、固定javap来源、三个直接域的historical缺席及自然loadedAll赋值位置等10项，全部通过。证明`main-review.private.json` SHA `da6b883b644fd493697a23e4dd2ad0c6c11ca664c8a94f4eac372bdc721ae012`；只验来源/可执行性审计，不验实际loadAll或资源ready。

此处NO-GO限于“尚未确定本次隔离首诊的准确输入及可执行脚本范围”，是实验规格边界，不新增AGENTS逐项审批规则，也不要求把全部Lua或161张表审计为D-02 P0。用户现要求当前工作收尾后暂停，因此未建立或实施新driver任务；恢复时先复用既有必要脚本证据、固定选定输入，按实际需要确定执行范围。本轮不以置位loadedAll、默认成功或部分输入通过代替完整资源与正向恢复。
