# Avatar 完整构造的深入离线研究

任务 `F-03d3f4d2 / D02-R1`，国服7.2，2026-11-07。本轮新增的是准确的消费者片段与后继规格，**完整 constructor、Avatar handler、生产 g2 和 D-02 都没有完成**。复用 f1/f3/f4d1、完整 raw 图、独立适配器和已有 Java 来源投影，不把旧验收重复计为本轮成果。没有操作客户端、调试器、服务、数据库、补丁或资源；仅离线工具、测试、脱敏摘要和本文变更，无部署要求。

## 第一项已验收范围

独立工具 `tools/client72_avatar_deep.py` 每次重新校验 EXE/global/startup 三个固定 hash（沿用 `extract_client72.HASHES`），重提声明、参数、继承、具体 AOT 与30个函数边界，按 `EVIDENCE_SHA` 固定既有摘要。实际离线 CPU 执行23组新样本；11项独立断言通过；两个全新进程的完整报告和摘要逐字节相同。

| 产物（相对 `local/migration72/astra-avatar/`） | SHA-256 | bytes |
| --- | --- | --- |
| `first-native.json` / `repeat-native.json` | `1f937e7d6a8dbd19b5feb7e685d9baac4390181b785b99d98b9c7aa366c9cb16` | 1149979 |
| `first-summary.json` / `repeat-summary.json` | `fc08a41533290f671693ac1a0a0ec3608ac56c1cd93cc039f2c681f8102a44c7` | 274168 |

发布摘要 `protocol/cn-7.2/avatar-deep-observations.json` 与上述摘要相同；`determinism.json`、`tests-final.log` 记录实际比对和测试。原始反汇编、失败和路径不提交。`runtimeStatus=not-tested` 指真实客户端未测，报告另明确 `static-and-bounded-offline-native`，不会把离线 CPU 执行混写成实机。

### 61980 生命周期：构造、池边界与清理必须分开

`61980 PPNMEKJPONB` 的 `.ctor/1535bdc10` 实际只有 `ret`。全零分配后仍全零，预填20字节非零 payload 后仍原样，证明不能把 allocator 清零算作 constructor 初始化。

`1535be1a0` 实际调用 `14a42bd90`：受控 header+14为0或1时清除+18..+2b的20字节；header2先进入未恢复的反射/运行时路径，本卡在 `14a42bdbe` 的 call 指令前停止。`CopyFrom/1535be120` 在已测header0条件中先清旧，再合入非零源；非零/全零输入都独立核对。

进一步实际执行 handler `15184e3fb→15184e42d`，其中 `14d4480c0` 完整返回、父类67861虚槽9=`1495c9d00`/11=`1495c9d60`均实际运行。这两个父类方法也为 `ret`。池 rent 是明确的 typed managed 分配/预填模型，**池内部租借回收算法未运行**。在该受控分支，wrapper把header+11写10、+14写1，handler仅把payload+24写0，其他脏payload保留。这是一个具体反例：不能将“池返回对象”一概当作清洁对象，更不能据此推断真实池一定返回脏对象。实际池清理责任和生命周期仍需独立来源。

最初 `native-probe1.log/.failure.json` 的header2实验在停止helper入口后仍被既有观察器调用了同地址的数组模型，报 `Unreviewed array element class`。本卡没有改公共观察器，改在caller call指令前停，独立断言该helper未在models中出现；失败保留。

### 空装备不是空操作；非空赋值只闭合精确片段

完整空装备refresh入口 `15183f290` 在已构造容器上真实执行到 `151843490` 前：先请求 `Dictionary<uint32,uint32>` 与 `List<uint32>` 两种明确池对象，再清 `model+1f0` 旧列表。新列表/旧count3均变0。这推进了f4d1的首个停止点，但 `151843490`、其余刷新、完整 `15184ec00` 返回仍不在本项验收范围。

非空Relic分支从 `15184a710` 开始，明确给出**模型工厂返回后的 live-in**，不是执行完整工厂或完整 `15184a5f0`。实际代码将库存item ID写 `equipment+70`，slot写+48，库存对象指针写+40，Avatar GUID写+88，output保存完整物品GUID。输入0、42、uint32最大值及独立resource哨兵经原生编码/解码回读一致；owner GUID=`2^63+1`、物品GUID=`2^64-1`未截断。停止在 `151845920` 槽查询前，后续替换旧GUID、list更新和效果仍未完成。resource/state是合成受控输入，未声称真实物品资源对应或业务字段名称。

静态旁证：refresh会在slot6及slot7两种分支检查同一个type51522；因此不能单凭slot6路径把SceneInfo的51522全体命名为Weapon。此关联已交Scene研究分支复核。

### 技能字典与 depot+68 / tag13

实际 `151847f70→151848096` 先清 `model+1f8/+200` 两份 `Dictionary<encoded-int32,encoded-int32>`，再处理后续输入。旧count3时原生选择清理buckets4、entries3，Array.Clear字节操作仍为已有明确模型。两字典都变空；空输入不是保留旧技能状态的依据。完整技能初始化还需要未闭合的34054列表和后继效果。

handler静态读取 raw+c0=`AvatarInfo48178 tag13 ACNMNNPAHPA/uint32`，与depot ID一起传入技能函数；本轮实际执行 `151849380` 尾片段：按完整uint32查询GLOBAL+212b8的depot缓存，取row+68经原生解码。当+68和tag13都非零时，原生分别编码成将传给 `1593cff80` 的key/value；停止在 `15184946b` call前，**没有执行TryInsert或技能效果**。+6c另设123456789哨兵，实际key始终取+68，消除了与f4c Team派生链混淆的可能。

tag13来自实际raw reader：省略、显式0、42后再写0均得到0并跳过该插入；+68为0、depot查无行也跳过；非零701/42与uint32最大值成对编码正确。缓存行和字典key→位置是受控模型，row选择、条件和编码为实际机器码。尚不能给tag13命名为技能等级，更不能把字段省略称为完整handler安全。资源分支另外实际恢复了22284 parser对+68/+6c的独立位与编码；候选JSON→客户端二进制parser的映射仍待该专题审核。

## 构造间接调用与剩余难点

| 难点 | 本轮证据/停止点 | 未完成与最小解除条件 |
| --- | --- | --- |
| enum20060 HashSet comparer ctor | 精确AOT只有method15590零参wrapper=`156fbc020`；wrapper从rgctx+18取得另一MethodInfo再间接跳转。相关重载声明存在，未获取该重载的准确callee | 区分“同型专用ctor”和“共享值类型ctor”；需要该闭合实例的rgctx解析表/原生resolver证据，不可拿同形int/u32代替；cold comparer反射同样未恢复 |
| SortedDictionary ctor | 精确2034/12373零参wrapper=`1570f5f20`从rgctx+10取comparer重载 | 需要精确共享/专用目标与Comparer ABI，不将wrapper指向自身或用返回成功填空 |
| 34054继承ctor | 34054→69137<63831>→List<T>继承已重提；69137/5278 wrapper=`155b229f0`仍取rgctx+18间接尾跳，List<63831>本次选定AOT没有ctor注册 | 需要准确共享List ctor绑定与rgctx来源；五处f4d1未写字段仍不能称真实默认值 |
| 空装备 | 已从15183f290推进到151843490，真实清理旧列表 | 后继资源/派生列表/比较delegate及实际List插入未闭；下一步独立分析151843490/151843c80 |
| 非空装备 | Relic真实赋值与编码片段通过 | 工厂模型实际类型、Weapon分支、槽替换、重复更新仍开放；下一步精确工厂usage→51522和151845920/15183e450 |
| depot+68、技能 | raw tag13→两个编码参数已验，清理前缀已验 | map15/17、list14/35、34054排序和效果仍开放；没有批准业务名/必要性 |
| 61980池/后缀 | ctor、reset、CopyFrom、受控pool wrapper和handler+24已验 | 真实池清理责任、refcount异常、其它后缀仍未验；不可根据zero allocator补完整模型 |
| tag29 | 已固定raw+bc→15184fc80→151840eb0；其后依赖model+1e8 enum HashSet | 辅助映射构造、枚举/contains及非零效果另需实际执行，下一批可离线推进 |
| tag38 | 已固定raw+60→15184fe30；先清model+70，再以Avatar ID调用15437ada0资源列表查询 | 资源来源/筛选15184d840/非空列表尚未执行，下一批可离线推进 |

“未完成”均保留原地址、无法区分的假设和下一入口，没有把未执行callee做成成功桩。以上前置未齐，不把片段拼接成完整构造/handler。

## g2 交付规格（限定，禁止称完整）

复用 `CLIENT_72_AVATAR_JAVA_PROJECTION.md` 的既有实际Java getter→独立raw adapter→native片段，不重跑Gradle。允许继续制作**隔离测试投影**，不批准生产sender。

| 字段/编码 | Java来源与顺序前置 | 当前允许/禁止 |
| --- | --- | --- |
| root30509 tag6→48178 tag1 uint32/tag2 uint64 | `Avatar.getAvatarId/getGuid`；GUID来自已恢复所属玩家，先有对应库存和角色集合 | 已有有限投影可复用；不缩短GUID、不用ID代GUID |
| 48178 tag3 map< uint32,78350 >；子tag1属性ID/tag2 int64 oneof | `getExp/getPromoteLevel/getLevel`→1001/1002/4001；包括显式零 | 这3个已审属性可隔离测试；不从旧表全量扩展ID |
| tag5 repeated uint64 | `getEquips`中同一`GameItem.getGuid`；Store与Avatar必须引用同一物品 | 只已有GUID关联和新赋值片段；旧投影Store item ID0与native item10001不等价，真实ID/资源/工厂/完整槽更新未闭 |
| tag4 uint32生命态、tag7 map<uint32,float> | 必须完成Avatar加载、装备恢复及最终`completeLoadingVitals→restoreLoadingVitals(true)`；SavedVitals与最终计算不能混用 | getter存在不构成许可；只批准既有7个FightProp候选ID的有限消费，空map清旧，不能省略凑最小包 |
| tag11 uint32 depot | `getSkillDepotId`是当前保存值，资源默认depot是不同来源；energySkill非零但energySkillData缺失时仍保留保存值 | 需证明当前depot/客户端资源一致；不可用默认depot替换或捏造能量值 |
| tag13 uint32、tag15/17 map、tag14/35 list | tag13与depot+68配对已验；真实业务getter及其余字段语义未验 | 继续未知原名，不生产发送、不猜等级或天赋字段 |
| root tag5 Team map/tag12当前Team | 已验`TeamManager/TeamInfo`真实getter、名称/成员顺序/GUID查找 | 可复用隔离投影；fallback/override和当前控制角色不据此批准 |
| tag29/tag38/61980/其余复杂字段 | 仍需上表后继；全raw图存在不等于业务来源存在 | 不发空壳、不以0/省略覆盖缺口，不从旧toProto常量恢复语义 |

完整g2验收出口：先闭准确constructor和所有必经后缀；再由真实Java来源输出非零、64位、嵌套、列表、错误、初次与重复更新样本，经独立raw适配器和完整native handler返回验证；明确每个清旧/保留/覆盖差异。随后仍需真实客户端新旧号初始化、选人、入场、心跳及退出重登，离线结果不替代这些条件。生产、Proto、配置或资源真正接入时，再由主线按AGENTS执行构建、portable部署和日志/健康/端口/PID/JAR核对。

```powershell
$avatarDeepGame = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/game_path.ps1
python tools/client72_avatar_deep.py --native --client $avatarDeepGame --output local/migration72/astra-avatar/first-native.json --observations local/migration72/astra-avatar/first-summary.json
python tools/client72_avatar_deep.py --native --client $avatarDeepGame --output local/migration72/astra-avatar/repeat-native.json --observations local/migration72/astra-avatar/repeat-summary.json
python -m unittest tools.test_client72_avatar_deep -v
```

Git和共享索引/交接由主线协调，本代理没有提交。下一立即可执行项是tag29辅助构造与tag38资源筛选，不需要实机或生产接入。

主线独立审核并执行11项断言及第三新进程原生回放，完整报告/摘要与上述双跑逐字节相同；证明`local/migration72/astra-main/avatar-review.json`，日志同目录`avatar-{tests,native}.log`。仅接受明确片段和模型边界，不批准完整g2。
