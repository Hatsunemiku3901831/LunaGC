# E-01d1m：D-02 任务入场依赖与候选字段缺口

2026-11-07[资源深入研究](CLIENT_72_RESOURCE_DEEP_RESEARCH.md)已实际确认BGH字符串解析及已知消费链筛选GAME_TIME_TICK18、排除LuaNotify20。不能将此证据当param_str alias；LogicType对象/缺acceptCond不可从现输入无损恢复，需有来源的blob/exporter或接受规则。303显式路径及既有拒绝不变。

本专题是源码和固定数据的只读核查，不代表真实目标客户端任务、完整入场或 D-02 通过。当前生产配置及研究夹具指向旧 producer 资源，服务未部署；下述目标候选数据没有部署。账号/保存约束见 [系统](ACCOUNT_AND_SAVE_SYSTEM.md)、[数据模型](ACCOUNT_AND_SAVE_DATA_MODEL.md) 和 [运维](ACCOUNT_AND_SAVE_OPERATIONS.md)。旧账号的任务、角色和存档不能删除或重建来绕过初始化。

## 输入及证据边界

当前 checkout 分支面向国服 7.2；本次核查时的根配置 SHA-256 `0e94caea6e33aa0d75fbd041726c188121f1eb9f0ff69787c0c5bb84d4975a45`，`enableScriptInBigWorld=true`、`questing.enabled=true`、`unlockAllOpenStatesOnLogin=false`、`forceFinishMainQuestsOnLogin=false`、`firstLoginCutscene=0`。三份固定客户端输入本次重读，与既有门禁一致：EXE `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5`，global metadata `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a`，startup metadata `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105`。路径按本机 `tools/game_path.ps1` 解析，不将本机路径写入共享资料。

旧 `resources/ExcelBinOutput/QuestExcelConfigData.json` SHA-256 `aacb6edba8b97e3dd39fef80444bb180756fe1d6090c341cd0545ec08f0951a3`，旧 `resources/BinOutput/Quest/303.json` `44b646a0f29a3a1a1b780d58902ee28e7d5749ba13a45e6b9b2a6d48d12ad8cc`。目标候选源为 `d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1`，根 tree `5b334e711319726c504da95dbfe6ebb26baaa7d3`；候选 QuestExcel blob `0788265f3a267a22abf2c7e33f04705704a04b0d`、SHA-256 `c8561299f543456573b0fa16404e210bcd2ecf84ce5006eb63dd4b2845137a87`；候选 BinOutput/Quest tree `8f902fe03afdd73628c8a126c27c7ec1ddf35e43` 中 303.json blob `88c7cb4537937a33e6ca3212cd29cf7f1b06a314`、SHA-256 `aaf6901da4c30528047c1210bc636f810b368d5fb2f19aad6369bbb956dd3cca`。候选文件和原始 tree/实验留在忽略的 `local/migration72/`。

既有独立 JDK/归档 JAR 实验用当前真实 `ResourceLoader.loadFromResource` 和 `JsonUtils.decode` 处理五个旧表 level≥1 候选 ID `2101001/2101101/7101601/7101607/7155508`：目标五行均抛 `JsonSyntaxException`，内部为 `Invalid Enum definition - BEGIN_OBJECT`，装入 0；相同五个旧表行装入 5，建立 `QUEST_COND_PLAYER_LEVEL_EQUAL_GREATER1` 索引。原日志、输入切片及 class-init 在 `local/migration72/d02-r3-quest-red/`。这五个 ID 只按旧资源的接受条件选出，不能由此认定目标客户端也会接受。E-01d1m 未重跑该实验，也未运行 Gradle、数据库或客户端。

## 新旧账号会走到哪里

1. 新号在 `HandlerPlayerLoginReq` 无角色分支等待选人；有效的 `HandlerSetPlayerBornDataReq` 在保存角色及玩家后调用 `player.onLogin()`（[HandlerSetPlayerBornDataReq.java](../src/main/java/emu/grasscutter/server/packet/recv/HandlerSetPlayerBornDataReq.java#L51)）。已有角色的账号由登录处理器调用同一入口。角色无效、存档不完整或更早步骤失败时，不能假定走到任务逻辑。
2. [Player.java](../src/main/java/emu/grasscutter/game/player/Player.java#L1774) 的玩家加载线程先加载 avatar，再 `QuestManager.loadFromDatabase`，再加载 inventory；主线程等待 avatar 和 inventory 的 loaded 标记。daily 加载在另一提交任务，不能套用同样时序。`QuestManager.loadFromDatabase` 要求资源加载完成且 QuestData 非空，然后按 `ownerUid` 查询 `quests`；每个存档章和子项必须与当前 MainQuestData/QuestData 的 parent/sub ID 一致才整体绑定，否则保留并登记 deferred，不因不匹配创建新章。实际旧账号拥有哪些 ID、是否 deferred 及其组套件，未读取数据库，保持未知。
3. [Player.onLogin](../src/main/java/emu/grasscutter/game/player/Player.java#L1846) 先建立并加入 World，再在第 1920 行调用 `QuestManager.onLogin`；当前两个开关满足 [isQuestingEnabled](../src/main/java/emu/grasscutter/game/quest/QuestManager.java#L99)。`enableQuests` 同步触发索引中的 STATE_EQUAL/STATE_NOT_EQUAL，以及 `QUEST_COND_NONE,0` 和 `QUEST_COND_PLAYER_LEVEL_EQUAL_GREATER,1`；子项还须章节含该 ID、未完成/未开始、各条件计算成功，才调用 `addQuest`。新角色初始 level 的源码默认值为 1。任务后续触发可排入单线程 executor，活动条件、状态递归和资源 handler 的结果不能从一个索引命中直接推成已执行。
4. `PlayerProgressManager.onPlayerLogin` 在任务登录之后运行，先按 open-state 条件尝试解锁，再 **无论 questing 开关**调用 [addStatueQuestsOnLogin](../src/main/java/emu/grasscutter/game/player/PlayerProgressManager.java#L248)：若 MainQuestData 303 不存在则返回；若玩家没有该章，调用 `addQuest(30302)`；拿到章后遍历该章声明的每个子项，对已存在且 UNSTARTED 的子项尝试 `addQuest`。这使 303/30302 成为可证的登录查询或条件尝试，**不等于**目标资源必然可加载、所有子项必定开始。当前旧 Excel 的 30302–30317 在旧 chapter 中均存在；这些旧 Excel 行的 acceptCond 只有空/无类型占位，经 `QuestData.onLoad` 过滤后会登记 NONE 索引，因此它们也可能在前面的自动接受阶段被考虑。
5. [GameQuest.start](../src/main/java/emu/grasscutter/game/quest/GameQuest.java#L73) 可根据 `QUEST_CONTENT_TRIGGER_FIRE` 查询 TriggerExcel、加载对应 group Lua，并运行 `beginExec`；返回和保存仍受世界、资源和事件条件约束。`QUEST_EXEC_REGISTER_DYNAMIC_GROUP` 在已有 scene 中 `loadDynamicGroup` 后记录 scene/group/suite；`QUEST_EXEC_REFRESH_GROUP_SUITE` 在 scene 初始化完成或回调延后满足身份/状态条件后尝试刷新并记录 override；`QUEST_EXEC_NOTIFY_GROUP_LUA` 有场景初始化、玩家在场、场景完成加载及任务状态校验。`HandlerEnterSceneDoneReq` 从未完成章的已保存 groupSuite 中取当前 scene，再调用 `loadGroupForQuest`；后者若 script manager 未初始化就返回。由此任务组可能扩展出生点的 25 个静态组选集，但本专题没有得到受控新号或旧号的真实 group 列表。

## 收敛样本：303 与旧条件的 2101001

旧 chapter 303 有 16 个子项 `30302–30317`；旧 Excel 有 61 个 mainId=303 行，但不在旧章子项表中的行不能仅凭 Excel 启动。候选 chapter 303 有 63 个子项 `30302–30364`；候选 Excel 的 mainId=303 行为 29 个 `30302–30330`，另外 `30331–30364` 不在候选 Excel 中。候选 BIN 的 63 行均有非空 finishCond、当前 Java 可识别的具名 finish 条件；acceptCond 和 beginExec 均缺席，finishExec 均存在。本机定向静态形状摘要 `local/migration72/analysis/d02-r3-quest-303-static-shape.json` SHA-256 `2598161232d7e7d4b571fe8c7c314cbc63996c4708d495086a1871403c66031c`：63 行在所检查的名字与参数形状上未报问题；这不是完整 alias/解码检查、真实 loader 或目标语义验证。

候选 30302 BIN 的 finishCond 为 `QUEST_CONTENT_COMPLETE_TALK`、参数 `[30302,0]`；finishExec 包含 `QUEST_EXEC_UNLOCK_POINT ["3","3"]` 和 `QUEST_EXEC_UNLOCK_AREA ["3","3"]`，没有具名 beginExec。由于 `ResourceLoader.loadQuests` 在 Excel 后加载 BIN，[MainQuestData.onLoad](../src/main/java/emu/grasscutter/data/binout/MainQuestData.java#L78) 对缺失的 QuestData 调用 `QuestBinFallback.recover`；其要求章节/子项 ID 唯一一致、具名 finish 条件及允许的参数/枚举。若某 BIN 行被真实恢复且缺 acceptCond，[QuestData.onLoad](../src/main/java/emu/grasscutter/data/excels/quest/QuestData.java#L66) 将 `automaticAcceptance=false`，它不会被 NONE 自动索引；ProgressManager 仍有显式 `addQuest(30302)` 的源码路径。目标 30302 是否实际恢复并成功开始仍需隔离真实 loader/业务实验，不能通过静态形状认定通过。

旧 Excel 和旧 21010 BIN 的 `2101001` 有 level≥1 的接受条件，以及 `beginExec QUEST_EXEC_REFRESH_GROUP_SUITE ["3","133105180,2"]`；当前旧脚本中该组位于 scene3 block3105，Lua SHA-256 `f4ad3568e4f199985330fa3d6a35399d79969841e47acd575b8425a0668d3a47`。条件成立且章节能实例化时，该 group 是旧 producer 路径的条件性后代，不在出生点静态 25 组中。候选 21010 BIN 同子项没有具名 beginExec，不能因此把旧刷新行为当作目标 7.2 行为，也不能据此排除目标仍通过其他字段/路径触发。

候选 71555 BIN 的 `7155508` finishCond 为 LUA_NOTIFY 字符串 `7155507_finish`，finishExec 声明 scene3 group `133106303` suite 2 的刷新和 group `133106285` 的 Lua 通知。它们是指定完成条件后的候选后代，不是入场必触发；接受条件、finish 字符串目标语义和实际时机尚未证实。

## 目标字段形状与具名证据

| 目标候选字段 | 实际形状与可核名称 | 当前 Java 期望及结果 |
| --- | --- | --- |
| `acceptCondComb` | 候选表 17,814/17,814 行都是 object；例如 30302 为 `{"param":["","","","",""]}`。全表 type 值包括 `QUEST_GUIDE_NPC` 5,585 行、`QUEST_GUIDE_LOCATION` 5,064 行等，且出现 `guideScene/guideLayer/guideStyle/autoGuide` 键。 | [QuestData.java](../src/main/java/emu/grasscutter/data/excels/quest/QuestData.java#L30) 声明 `LogicType`；[JsonAdapters.java](../src/main/java/emu/grasscutter/utils/JsonAdapters.java#L282) 的 enum adapter 接受字符串/数字，对 object 抛异常。当前 `LogicType` 的具名值是 `LOGIC_NONE/AND/OR/NOT` 及混合逻辑，不包括上述 guide 名称。Java `QuestGuideType` 中有 `QUEST_GUIDE_LOCATION/NPC`；形状和名字支持字段标签可能错位的假说，但不能据此确定目标字段映射。 |
| `finishCondComb` | 候选 17,814 行均为 `{}`。 | 当前也声明 `LogicType`，object 仍不符合 enum adapter。五行红测只给出了第一个 object 异常，不能说已逐字段执行到此字段。 |
| `failCondComb` | 6,525 行值为 `QUEST_HIDDEN`，其余缺失；30302 等六个对照样本该值与旧表 `showType` 一致。 | 当前声明 `LogicType`；未知字符串被 enum adapter 映射为 null，若对象其他字段问题被绕过，`onLoad` 会把 null 默认为 `LOGIC_NONE`，这会丢失原值。对照支持字段名错位的假说，不是语义确认。 |
| `acceptCond` | 候选 Excel 的 17,814 行均未出现该键。 | 当前 `List<QuestAcceptCondition>` 缺失会使自动接受关闭；不能将缺失当空列表或继承旧表条件。 |
| BIN 2101001 的 `BGHPAKBEPND` | 目标 `QUEST_CONTENT_LUA_NOTIFY` 条件中值为 `"2101001"`；旧同 ID BIN 的 `param_str` 为同一字符串。 | `QuestCondition` 只识别 `_param_str`/`param_str`；未知字段被忽略，默认字符串为空。[ContentLuaNotify](../src/main/java/emu/grasscutter/game/quest/content/ContentLuaNotify.java) 用该字符串查玩家进度。值对应提示可能同源，但尚无目标消费者证据，不能直接添加 alias。 |

30302 的目标 Excel 同时存在 object `acceptCondComb`、object `finishCondComb` 和字符串 `failCondComb=QUEST_HIDDEN`；因此它与现 Java 的类型冲突可以静态判定，但本轮未在真实 loader 上单独重跑 30302，不能把五行红测冒充其执行结果。目标数据的同名 `failParent` 在 30302 和上述五个样本中恰与旧 `descTextMapHash` 相同，进一步提示字段名可能错位；这仍只是候选来源之间的对应，不是足以实现转换的独立证据。

主线独立重算四份旧/目标Quest数据及根配置hash，复核16/63个章节子项、61/29条Excel行及全目标17,814行的对象/缺失字段分布，并检查30302 BIN的实际参数和`QuestData.onLoad`、`QuestBinFallback`、`MainQuestData.onLoad`及ProgressManager的调用边界。证明为`local/migration72/evidence/d02-r3-quest-entry-main-review.json`；这仍是静态审核，没有新增真实loader、数据库或客户端执行，不批准字段alias或目标任务恢复成功。

## 解除条件与最小下一实验

步骤1已由[E-01d1n1真实加载](CLIENT_72_D02_QUEST_303_RECOVERY.md)完成并经主线独立复现：30302 Excel行实际拒绝，目标303 BIN实际恢复63项，自动索引仍空。只关闭加载/指定字段回退合同；以下步骤2的真实任务启动、保存冷读及步骤3的未知目标语义仍未完成。

1. 在独立 JVM 中固定上述候选 303 Excel/章节原始字节，分别观察真实 Excel 行拒绝、`MainQuestData.onLoad → QuestBinFallback` 是否安装 30302、其自动索引是否为空；检查 63 项中哪些真实安装，不能仅数静态形状。只对独立夹具操作，保留首个拒绝及无副作用日志。
2. 若 30302 确实可恢复，使用真实业务及隔离存档验证显式登录路径、实际启动/保存/重载、对应 scene/点位及通知；旧账号使用受控独立副本，不能删除原档。观察 `GameQuest.start` 和 group/trigger 后代及等待条件，保留结果。该步需要协调独立数据库与构建窗口，当前未执行。
3. 对 `acceptCondComb` 的对象、`failCondComb`、`BGHPAKBEPND` 及真实接受条件的目标消费关系，应由固定三输入的独立目标客户端元数据/原生读写路径或独立样本确认；旧/目标 JSON 的同值与具名枚举只能构成候选。确认前不加转换 alias、不关闭 quest、不虚报兼容；动态插件、异步任务、保存章及其他 group/Lua 保持开放。

不启动服务的窄范围复核：目标文件需已按上述 tree/blob 取得；下例只输出章节 303 的计数、30302 原始字段及固定输入 hash，不解释目标语义。

```powershell
@'
from pathlib import Path
import hashlib,json
paths={'oldExcel':Path('resources/ExcelBinOutput/QuestExcelConfigData.json'),'old303':Path('resources/BinOutput/Quest/303.json'),'targetExcel':Path('local/migration72/d02-r3-candidate/ExcelBinOutput/QuestExcelConfigData.json'),'target303':Path('local/migration72/d02-r3-candidate/BinOutput/Quest/303.json')}
data={}
for name,path in paths.items():
 raw=path.read_bytes(); print(name,'sha256',hashlib.sha256(raw).hexdigest()); data[name]=json.loads(raw)
for label in ['old','target']:
 chapter=data[label+'303']; rows=[x for x in data[label+'Excel'] if x.get('mainId')==303]
 print(label,'chapter children',len(chapter['subQuests']),'excel rows',len(rows))
 print(label,'30302',json.dumps(next(x for x in rows if x.get('subId')==30302),ensure_ascii=False))
print('target acceptCondComb types',sorted({type(x.get('acceptCondComb')).__name__ for x in data['targetExcel']}))
print('target finishCondComb types',sorted({type(x.get('finishCondComb')).__name__ for x in data['targetExcel']}))
'@ | python -
```
