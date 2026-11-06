# E-01d1n3：ChapterExcel 真实加载、启动通知与 loadAll 前置

状态：主线已运行双新 JVM 真实章节加载，以及独立 Mongo 的目标章节表模式两项真实启动/保存测试。只关闭本表加载、索引及这两条启动路径的通知增量；resourceReady、正向 QuestManager 重绑定及客户端仍未通过。独立文件和产物不覆盖原 resources。未调用 loadAll、未修改 loadedAll 或绕过门禁。

## 固定来源及章节索引

固定候选 commit `d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1`、根 tree `5b334e711319726c504da95dbfe6ebb26baaa7d3`；已有 Excel tree `a7767d2efe82980a7dbefe716853e76e9c9a4276` 的 `ChapterExcelConfigData.json` blob `8f8f8fa23df84686883d931f248e6ff0b626ddd7`。从该固定 commit 取到的原始字节，`git hash-object` 与 blob 一致，SHA-256 `6c291ee3b5ca1e29b1b4395c1e13103d1965e1ce687eb3e0364e08dfcc55f164`，保存于 `local/migration72/offline-sol-terra/chapter-audit/ChapterExcelConfigData.json`。已存 tree 文件 SHA-256 `7eb821344e69808720819a5511956a0153e84a2dd506484ef406d7cb9346de4f`。固定版本标签并不证明与本机客户端 revision 对齐。

静态 JSON 扫描 436 行，具名 beginQuestId/endQuestId 均无 30302；字段含义或别名及其他未知字段仍未知。`ChapterData.onLoad` 无条件将每行插入以 beginQuestId/endQuestId 为键的两个静态 HashMap，缺失整数为 0，同键后者覆盖；资源 map 的 key 为 id。`loadFromResource` 先 onLoad 后 put，逐行异常记录并继续，重复键也会覆盖，不能仅以总行数断言完整。`GameQuest.start` 在 QuestList 通知之后按 begin 索引命中发送 ChapterState BEGIN；结束路径按 end 索引发送 END。主线独立 Java 驱动核实具名字段投影及索引未命中，实际包及目标 wire 尚待验证。n2 没载该表，故它的无通知不能用作此表的验收。

`tools/D02ChapterAudit.java` 准备以新 JVM 实际调用受保护的生产 `loadFromResource` 加载整张原表，记录安装 map、begin/end 30302 与索引大小，检查 fixed JAR 与当前编译类一致，逐行按同名 JSON 字段建立独立投影，与安装 map 对照，并断言 begin/end 全部键和最后写入值、记录零键及重复键计数，捕获并拒绝 loader ERROR，同时确认 loadedAll 仍 false；不启动数据库/脚本/服务或调用任务。输出不默认断言 30302 不存在，以免静态扫描代替观察。两个新进程结果必须一致；需人工检查 stdout/stderr 中逐行拒绝和 collapse 日志，核对原始行、重复/零键、每项输出，不能只依据退出码。JAR SHA-256 `dfdebbda0352055540c44a54e2eebfd4ffdfe679db74270044d3d55aeb006ec3`。源码 hash：驱动 `a429f40b5f51442601a089963509b885cf13cc890459c6bd27db3dcce43b5d4e`；运行脚本 `b6c00e4f32c38c9292a3a45b843bad0ab29cc2a793839a4a5e8c0faeed3da9a3`；静态 manifest 脚本 `536a4e0b10fc01e5eb28b1bea82d0ef5f5d2a55e3040edf2937c6457c5a2f8ee`。原准备阶段未执行，现主线已执行并核实如下。

## 生产 loadAll 顺序与审计缺口

以下 `R` 表示配置的 resources 目录，`D` 为 data，`S` 为 scripts。每一步是源码调用顺序，不是实测清单或 revision 验收；并行 Excel loader 同优先级内部无顺序。源码注解、直接字面路径另见可重生成的 `local/migration72/offline-sol-terra/chapter-audit/static-manifest.json`；动态路径、间接调用、运行时反射和目录内每一实际文件未被该自动提取器穷举。

1. `ScriptLoader.init`（脚本引擎与路径，需进一步逐引用审计）；`loadConfigData` 读 R/BinOutput/{Avatar,Monster,Gadget}/*.json，生成对应 GameData maps，记录单文件解码错误及空目录；monster 另报告 abilities 覆盖。
2. `loadTalents` 递归 R/BinOutput/Talent/AvatarTalents；`loadOpenConfig` 读 D/OpenConfig.json 与 R/BinOutput/Talent/{EquipTalents,AvatarTalents,RelicTalents,TeamTalents}；`loadAbilityModifiers` 递归 R/BinOutput/Ability/Temp；`loadAbilityEmbryos` 读 D/AbilityEmbryos.json、R/BinOutput/Avatar/ConfigAvatar_*.json、R/BinOutput/AbilityGroup/AbilityGroup_Other_PlayerElementAbility.json；`WidgetCatalog.reload` 读 R/BinOutput/WidgetNew/ConfigWidgetNew.json 与 R/BinOutput/AbilityGroup。需审计缓存/现资源优先级、逐文件和解析日志。
3. `loadResources(true)` 由反射找 GameResource 注解及 GameData 对应 map，按优先级集合并行读取注解文件；章节表在此。行级异常输出 ResourceLoadDiagnostics，文件级异常收集后 error，最后另读 R/ExcelBinOutput/ConstValueExcelConfigData.json。需枚举所有注解、映射、实际文件和 onLoad 引用，核对每项结果与日志；一次错误不阻止随后加载。
4. `buildAbilityTalentVarMaps` 基于 ProudSkill/OpenConfig 构建索引；`GameDepot.load` 基于 ReliquaryMainProp/Affix map 构建权重并报空；`loadSpawnData` 经 DataLoader 读 Spawns.json、GadgetSpawns.json，生成分块 spawn map；一份失败另一份成功仍可继续。
5. `loadQuests` 读 R/BinOutput/Quest 下每个文件，先 map put 再 MainQuestData.onLoad/fallback；输出无匹配子项警告，读 R 或 DataLoader QuestEncryptionKeys.json；`loadScriptSceneData` 读 R/ScriptSceneData。303 BIN 63 项与 QuestExcel 30302 拒绝属于 n1/n2 已验证窄范围，不代表全表。
6. `loadScenePoints` 读 R/BinOutput/Scene/Point/scene*_point.json；`loadHomeworldDefaultSaveData` 读 R/BinOutput/HomeworldDefaultSave/scene*_home_config.json；`loadNpcBornData` 读 R/BinOutput/Scene/SceneNpcBorn/*.json；`loadRoutes` 读 R/BinOutput/LevelDesign/Routes/*.json；`loadBlossomResources` 经 DataLoader 读 BlossomConfig.json；`cacheTalentLevelSets` 从 ProudSkill/AvatarSkill maps 建索引。部分逐文件异常静默，需额外逐项清点输出。
7. `ActivityManager.loadActivityConfigData` 经 DataLoader 读 ActivityConfig.json；`loadConfigLevelEntityData` 读 R/BinOutput/LevelEntity/ConfigLevelEntity_*.json；`loadQuestShareConfig` 枚举 R/Scripts/Quest/Share/Q*ShareConfig.lua，再通过 ScriptLoader 读取、执行并建 teleport/rewind maps。R 和 S 配置可能不同，需核实际路径及日志。
8. `loadGadgetMappings`、`loadSubfieldMappings`、`loadMonsterMappings`、`loadActivityCondGroups` 依次读 R/Server/{GadgetMapping,SubfieldMapping,DropSubfieldMapping,DropTableExcelConfigData,MonsterMapping,ActivityCondGroups}.json；部分 IOException 被忽略，空 map 不自动终止。`loadGroupReplacements` 使用 S/Scene/groups_replacement.lua；`loadTrialAvatarCustomData` 读取 R/CustomResources/TrialAvatarExcels/ 下三个表；`loadGlobalCombatConfig` 读 R/BinOutput/Common/ConfigGlobalCombat.json；`EntityControllerScriptManager.load` 枚举 S/Gadget/*.lua 并执行。最后输出 finish 日志并设 loadedAll=true。

重要缺口：此表不是完整逐文件闭包；外部 loader/注解/onLoad 引用、动态脚本依赖、DataLoader 解析优先级、每文件 hash/版本及输出数量仍需独立审计。生产代码中若干错误被捕获后继续，最终标志不保证无拒绝或资源完整；也不能为过门禁伪造标志。当前 30302 Excel 原始行的 LogicType object 拒绝及未知字段仍阻塞，不能猜 alias 或用旧表掩盖。正向冷读仅由主线在自然生产 loadAll 且必要记录齐全后协调。

## 复现

先由主线确认执行窗口、固定 JAR/已编译类、目标表及本脚本散列；从仓库根目录执行：

```powershell
python tools/audit_d02_chapter_manifest.py
python tools/audit_d02_chapter_run.py
```

每次 javac 与 Java 分别限制 90 秒；隔离目录、配置、类初始化、stdout/stderr、两个 JSON 和超时记录位于 `local/migration72/offline-sol-terra/chapter-audit/run-<随机>/`。检查输入与 archive class 无漂移、两个结果字节一致、行拒绝/重复/零键及索引输出、禁止初始化项均未出现。若超时、类不一致、拒绝或检查失败则保留证据并停止，不以失败输出判定通过。此驱动只验 ChapterData，不覆盖 GameQuest 通知或真正 loadAll；新增真实 start 的隔离数据库模式见下，不能据此启动服务或客户端。


## Excel 机械清单和当前首个门禁（静态核对）

`python tools/audit_d02_chapter_manifest.py` 已实际执行。结果固定在 `local/migration72/offline-sol-terra/chapter-audit/static-manifest.json`，SHA-256 `f12e46ffe9615c9d552ebe6b383d0ac42ac112b79afae61c92674573bcea5f33`。静态解析 152 个源码注解、161 个表引用：HIGHEST 8、HIGH 7、NORMAL 128、LOW 9、LOWEST 0；对应名称规则的 GameData map 字段均存在。同优先级运行时并行无顺序，不能把清单排序当执行顺序；反射实际发现集合和泛型匹配尚需实际核对。候选固定 Excel tree 对 156 个引用有 blob，5 个不存在：DropTableExcelConfigData、DropSubTableExcelConfigData、DropMaterialExcelConfigData、StatuePromoteExcelConfigData、TalkExcelConfigData；不存在不代表可用旧表替换或已经确认业务缺失。

本机三个明示位置合计仅 24 个引用有候选字节，均核 Git blob 与固定 tree 一致；其中 R3 目录是混合 fixture，只有逐文件 blob 相等才能说该文件与固定 tree 一致，不能扩大到其他文件或整个资源集。未取得字节的文件只记录 tree blob，不虚构 SHA-256、行数或重复 ID。清单对已有 JSON 逐文件提供 SHA-256、顶层类型、数组行数、具名 `id` 出现次数和重复计数；仅检查具名 `id`，不是所有类真实 getId，也不推断其他键的语义。此清单是完整源码注解表的机械覆盖，不是 P0 定义或全资源依赖闭包；本任务已证的相关前置包括目标 ChapterExcel、QuestExcel、303 BIN、生产 Quest/Chapter 索引及实际 loadAll 的其他必要输入，不能将无关表全标为 P0。

当前最先可确定的 **2B 正向 QuestManager 冷读门禁** 是 `ResourceLoader.isLoadedAll()` 为 false，n2 的真实管理器因此拒绝重绑；自然生产 loadAll 及其完整输入/结果尚未验。这里不声称已知 loadAll 的第一个运行时失败点，当前没有实测该流程；已知目标 30302 QuestExcel 被真实 loader 拒绝、完整资源输入/引用尚未核实，又有多处捕错继续，因此不能靠手工置位或静态 manifest 进入 2B。对 30302 的 ChapterState 增量，n2 的成熟夹具以真实 QuestSystem、World、异步事件及 Mongo 保存为前置；现已添加真实目标 ChapterExcel 可选模式，待主线在独立数据库执行，不伪造包捕获。

输入获取：若设置 `D02_CANDIDATE_GIT` 指向本地候选 Git 仓库，runner 从固定 commit 提取并验证根 tree、路径 blob、内容 SHA-256；本轮本机候选源是部分文件目录，没有找到对应 Git 仓库。未设置时优先用已经核验的本地副本，副本不存在时从固定 commit URL 下载，再同时验证 Git blob 和 SHA-256。无网络且无本地副本或正确的候选 Git 时明确失败，不回退旧资源。runner 每次将核验字节写入独立 run 目录；下载有 30 秒超时，Git 查询各 20 秒。


## 主线真实章节表加载与通知夹具更新

主线独立重新执行静态 manifest 和两个新 JVM 的实际生产 `loadFromResource`：436 条安装，begin/end 索引分别 339/326 个键；0 键分别被 98/111 行写入，30302 在两索引均缺席（输出 -1），loader ERROR 空，`loadedAll=false`。独立字段投影、末次写入索引及两次结果一致通过；报告 SHA-256 `47524bc3879665f68350650dcc7c94a4ab604fc3a11d4963919a3a7176814a62`，本机证据 `local/migration72/offline-sol-terra/chapter-audit/run-e399c103899740d8a6c85b615bb366a5/`。这是实际章节加载，不代表 loadAll 完成或任何客户端通知已证实。

`D02Quest303StartMongoTest` 现支持可选 JVM system property `d02.chapterExcel`，值为固定目标表绝对路径；载入前核 SHA-256 `6c291ee3b5ca1e29b1b4395c1e13103d1965e1ce687eb3e0364e08dfcc55f164`，调用真实 loader 并检查 436/339/326 和 30302 begin/end 均缺席。测试结束恢复三个原 Chapter maps；无 property 保留原 n2 路径。两个既有真实启动测试记录完整包类序列、显式断言没有 `PacketChapterStateNotify`，保留保存/冷读和未就绪门禁。静态扫描 30302–30364 均无具名 beginQuestId 命中，目标表模式的实际包序列尚待独立运行，不以此推断其他表或客户端 wire。更改后的测试源码 SHA-256 `d88d99e391b38316555e2152ac5cf7e2058332164e82436bb1e0c2ab8e878d0c`。

复现是在隔离测试 init.gradle 的 `Test` 任务中设置 `systemProperty 'd02.chapterExcel', file('<固定表绝对路径>').absolutePath`，并沿 n2 流程使用单独 Mongo 实例和唯一数据库运行指定测试类；未经运行前置核对不得直接运行。上文“待运行”为准备阶段边界，实际结果以下段为准。

## 主线实际目标表通知模式与保存验证

主线在唯一库、独立目录 `local/migration72/offline-sol-terra/mongo-8c106c20b11743a8a73280ccc13b74fc/chapter-start/` 设置上述property。实际两项测试全过、0失败/错误/跳过；真实加载436行、begin/end339/326键之后：

- 直接30302启动仍构造 `PacketFinishedParentQuestUpdateNotify`、`PacketQuestListUpdateNotify` 两包，没有 ChapterState。
- 真实Progress入口启动63个子任务，仍构造64包（1个父任务更新、63个子任务更新），没有 ChapterState；固定表30302～30364均无具名begin索引。
- 保存排空、BSON、新Player冷读及真实QuestManager未就绪拒绝断言均保留并通过；loadedAll全程false，未做正向重绑定。三张章节静态map在测试结束恢复。

这解决n2原来“没加载Chapter表”的夹具缺口，不证明章节通知目标wire、任务完成效果或完整登录。原n2不带property的模式仍保留；新增property只改变测试资源输入。该实例之后的关联套件曾因漏接相对输入失败，失败目录保留，Mongo在finally中正常shutdown；测试303库及BSON保留、不清库，27027已释放。关联回归另用新目录修正输入后全过，不能与本两项重复累计。

2B仍因真实生产loadAll及必要记录未满足而阻塞。已证目标QuestExcel30302解析拒绝和未知LogicType/字段不加猜测alias；自然加载完整输入及引用/日志、候选revision关系仍开放。当前静态清单不是全部P0或完整资源闭包，不能把最终loadedAll标志单独当成功。
