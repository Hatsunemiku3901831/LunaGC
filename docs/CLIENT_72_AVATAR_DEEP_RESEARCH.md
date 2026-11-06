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

## 第二项：tag29、tag38和装备工厂的独立后继

新工具 `client72_avatar_deep_suffix.py` 固定第一项摘要hash，重新提取14个准确函数和两个工厂MethodSpec。没有修改第一项工具或摘要。11项新增独立断言通过，连同第一项11项共22项实际通过；第二项包含17组结果，其中8组mask分别执行6个原生bit片段。两次全新进程的完整报告/摘要逐字节相同。

| 产物（相对 `local/migration72/astra-avatar/`） | SHA-256 | bytes |
| --- | --- | --- |
| `suffix-first-native.json` / `suffix-repeat-native.json` | `c7ea819a323a6c08a68538cbb2f141820ca3976804bd53195018eec6c218aaf8` | 564154 |
| `suffix-first-summary.json` / `suffix-repeat-summary.json` | `5e03ba3486470055450cc4233f8cf3a500680569d874e5e8b1e1efad5b9e58c6` | 115617 |

发布 `protocol/cn-7.2/avatar-deep-suffix-observations.json` 使用同一摘要；`suffix-determinism.json`和`suffix-tests-final.log`为本轮实际证据。第一项测试曾把14778当作要检查的SortedDictionary重载：重新逐参数核对后，14778是`IDictionary`重载，真正comparer重载为14780。本轮只将该断言改为14780并增加参数名`comparer`验证，第一项原生/摘要没有变化，不无故重跑其旧oracle。此修正不影响此前“没有准确comparer callee”的结论。

### tag29的准确六位映射

raw `48178 tag29 EACODLJLDIL/+bc` 进入 `15184fc80`，随后 `151840eb0` 以列表索引位而非enum数值位构造辅助字典。目标静态构造 `15184dca0` 的六处store顺序为 `[2,5,6,7,8,16]`。本卡从 `15184dcf9` 执行至 `15184dedc`，明确 live-in为容量6的List/backing array、index0；真实store写出上述顺序并将List保存到GLOBAL+2a5c0。**未执行完整.cctor的分配/growth和后段字典构造**。

| wire位 | enum20060值 | 目标原始符号 |
| --- | --- | --- |
| bit0 | 2 | Fishing |
| bit1 | 5 | PlayLyre |
| bit2 | 6 | PlayQin |
| bit3 | 7 | PlayDrum |
| bit4 | 8 | PlayFrenchHorn |
| bit5 | 16 | PlayNatlanKeyBoard |

8个mask覆盖0、单低位、末位、低六位全开、bit6、bit31和uint32全1。每位都实际运行 `151840fbe→151840ff9`，核对将交付的enum key/bool；bit6与bit31均不设置这六项。完整辅助函数入口另真实清除旧count3字典，停在首个`15582ba40`调用前。未执行字典插入、`HashSet<enum20060>.Contains`或模式增删效果，因此只批准“这一目标六模式位图”的有限语义，**不能据此生产发送、批准其它位或把0当作不改变旧模式**。后续consumer会比较desired bool与已有HashSet，实际效果仍开放。

### tag38完整消费者：资源默认值与首个匹配输入

raw `48178 tag38 EIBCGPEOJLB/+60` 的实际非null列表进入完整 `15184fe30`。在显式初始化的合成资源缓存中，原生连续执行 `15437ada0`（按真实model Avatar ID1001查询）和 `15184d840→1473673b0`，最终完整返回。Array.Count/Item接口读取真正reader的array/count；资源字典key→index是明确存储模型，消费者和查询函数没有成功stub。

缓存布局已由独立metadata固定：type28604的GLOBAL+13b70为 `Dictionary<uint32,61286>`，+13b78为 `Dictionary<uint32,List<61286>>`。按Avatar ID得到有序group资源行，解码row+10作为匹配group、row+1c作为默认返回ID；每个tag38候选ID经GLOBAL+1cd98查资源，解码row+24做group比较，选择**输入中的第一个匹配ID**，否则使用默认ID。

本次独立合成group101/202、default501/502：省略、未知999、0均输出`[501,502]`；输入`[7001(group202),7002(group101)]`输出`[7002,7001]`；输入`[7003(group101),7002(group101),7001(group202)]`输出`[7003,7001]`；uint32最大候选ID完整保留。每次都先清旧model+70，再写新列表。primary缓存为空时完整函数返回，旧count3变0。没有把合成资源缺失分支当作实际入场资源可省略。

这组证据闭合**给定缓存下完整函数的选择和更新行为**，没有建立资源文件来源、parser、cache builder `15437af90`、默认记录完整性或字段业务英文名。Java `skillLevelMap/skillExtraChargeMap/getProudSkillBonusMap/getCostume`等已有字段并不据此成为tag38来源，禁止按旧名字猜映射。

### Weapon/Relic工厂准确分流

固定`15184a5f0`签名的两个来源参数分别为既有装备合同type29353（Weapon输入）和type52700（Reliquary输入）。实际callsite使用的MethodInfo经重新解码usage→MethodSpec→method294224闭合泛型参数：

| typed来源分支 | MethodInfo cell / usage / spec | 工厂模型 |
| --- | --- | --- |
| Weapon，`15184a679→15184a683` | `145994388 / 738059 / 23017` | 51522 |
| Reliquary，`15184a6f8→15184a702` | `145a30858 / 753864 / 77834` | 87122 |

因此51522可以在该准确分支关联为**Weapon模型角色**，并交叉支持Scene同一个factory binding。不是把51522的所有分支/字段都泛称Weapon；slot7用途仍单独未知。新摘要`factoryCallsites`逐项保存参数、callsite、来源合同与binding，供Scene跨卡引用。第二项没有重新执行完整工厂或重新计算第一项非空赋值样本。

### 间接构造的实际剩余输入与下一出口

本轮进一步静态解析 `140524010→1404ee6f0`：resolver先取runtime class+30的rgctx，缺槽时用class+68的泛型实例上下文；`context+8`指向每槽的32位definition word，原生以高部kind分派，再按低部index恢复/膨胀MethodSpec。三份固定输入虽包含静态metadata，但现有提取器**尚未恢复这些closed-class的definition-word来源、共享方法inflation映射和实际缓存ABI**。没有用MethodSpec存在、wrapper地址或同形ctor填补这个依赖。

下一纯离线任务应从class构建/rgctx definition表的生产路径恢复这些准确输入，优先验证：`145a30968`对应enum HashSet context+18，`145a30970`对应SortedDictionary context+10，`145a1a910`对应custom父类context+18及下一层List context；目标是各MethodInfo+8真正callee及其泛型参数，不是再跑同一个空槽失败。若需要实机辅助，需另有实机授权后仅观察同一固定版本自然构造时上述typed context/MethodInfo；本轮没有执行这些读取或调试动作。

至此本轮要求的间接构造/继承、HashSet comparer、空/非空装备、depot+68、61980池/后缀、零/省略/更新全部分别取得新证据或精确依赖出口，没有以首个未知为由停止其它独立难点。仍开放：准确constructor/comparer、完整装备工厂+槽替换+refresh、技能复杂列表/排序/效果、tag29模式效果、tag38资源来源，以及完整初始化业务来源和实机。第一项g2规格继续有效，tag29/tag38新结论只增加隔离测试可验证范围，**不批准生产g2**。

```powershell
$avatarDeepGame = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/game_path.ps1
python tools/client72_avatar_deep_suffix.py --native --client $avatarDeepGame --output local/migration72/astra-avatar/suffix-first-native.json --observations local/migration72/astra-avatar/suffix-first-summary.json
python tools/client72_avatar_deep_suffix.py --native --client $avatarDeepGame --output local/migration72/astra-avatar/suffix-repeat-native.json --observations local/migration72/astra-avatar/suffix-repeat-summary.json
python -m unittest tools.test_client72_avatar_deep tools.test_client72_avatar_deep_suffix -v
```

第二项主线22关联断言及第三原生完整/摘要与上述双跑核同，证明`local/migration72/astra-main/avatar-suffix-review.json`。首次调用漏`--native`只产出静态报告，保留且不计原生；最终带该参数执行并核每个实际stop/model。未修改production或推广完整合同。

## tag38具名资源来源与主线复验

独立`client72_avatar_deep_ornaments.py`重提literal29427/40297、精确getter/loader与cache类型，确认两表为`AvatarOrnamentGroupExcelConfigData`（owner28604/cache+13b70/type61286）和`AvatarOrnamentExcelConfigData`（owner39027/cache+1cd98/type44799）。两getter实际返回原literal指针，两loader保留同指针执行到`14d6e1570`文件I/O入口前；已初始化class和managed字符串是明确fixture，未执行文件读取、parser或cache构建。

5独立断言及主线第三原生与双跑同字节：完整`eaaf81b5e823cc707cdfc77c97889346efab84c6a353553c416be3961040e3c5`，摘要`bf385528a5f1bffbd15b63a51eea7127469662f93be8c33861f1cc3a8ade3c3d`。证明`local/migration72/astra-main/avatar-ornaments-review.json`；初次缺owner class fixture的失败在`astra-avatar/ornaments-probe1.*`保留。命令：`python tools/client72_avatar_deep_ornaments.py --client <GAME_PATH> --output <独立原始路径> --observations <独立摘要路径>`；测试`python -m unittest tools.test_client72_avatar_deep_ornaments -v`。此项消除资源文件身份未知，不消除实际资源值/revision/初始化来源未知；group/default/member字段英文名仍不批准。

## RGCTX准确表来源与三个继承List构造

继续沿resolver而非停在旧未知：`140524010`的直接helper是`1404ee6f0`；固定PE内后者两个经.pdata和反汇编确认的直接caller为`140524010`和`1405240d0`，分别读取class+30/method+18 context。`14051c9d0`实际class初始化代码给出class range表和definition表；`1404eea50`给出method range表。新工具`client72_avatar_deep_rgctx.py`仅恢复本卡所需class表/三个slot，不实施通用运行时。

class range在metadata+`0x106fda4`，8928字节/1116个严格升序8字节记录；value高8位=count、低24位=definition index，definition表在metadata+`0x40925ec`。解码来源分别是目标header+50加`e113ba5c`、header+104加`938d8470`及header+9c XOR `4b5cdca3`；这些新参数由上述目标机器码取得，没有沿用旧客户端解码器。definition word的kind3再指向开放MethodSpec，原泛型参数位置独立恢复后代入实际closed class参数。

| 原call所用class / slot | definition word / spec | 准确方法及实参 |
| --- | --- | --- |
| HashSet2147 / slot1(+18) | `0c08c700 / 575232` | method15591，HashSet<enum20060> comparer构造 |
| SortedDictionary2034 / slot0(+10) | `0c08c5a3 / 574883` | method14780，SortedDictionary<32758,51041> comparer构造 |
| 父类69137 / slot1(+18) | `0c08dc1a / 580634` | **method10374，List<63831>构造** |

最后一项纠正旧“还要跳入69137 wrapper”的待查假设：wrapper被当前调用处内联，真实slot直接指向List构造。引用类型canonicalizer `14052dd70` 在显式初始化的System.Object default-class夹具中实际将63831和51041类型引用归一为Object；此快速路径没有读取/选择enum共享flag。固定AOT的`List<Object>.ctor`为spec994/Method10374/classInstance0/`159111af0`，来自准确注册，不是按代码形状套用。

在这些有来源的MethodInfo/class/rgctx/空数组夹具下，真实 `15183ff7a→15184003d` 连续执行三个`159111af0`，分别在model+50、+1c8、+40保存真正分配的type34054对象，其backing array非null且长度/count为0。初始三个model字段为0及非零哨兵均由真实store覆盖。直接List构造另验证旧backing指针被替换；缺空数组context时在`159111b1e`调用resolver前止。零length/count属于已声明的managed分配+空数组状态，不将其说成ctor逐字段清零。

6个新原生样本、7项独立断言通过；双新进程完整报告SHA `9c3adfd698677c4cb83464c2bbc81819862361eff2a84d3dd76f33aa451f133e`（362924 bytes），摘要SHA `a8b29f1916799b754669bb1f609fd89f9a8427466217db1460a9503d7df642e0`（18547 bytes）。原始为`astra-avatar/rgctx-final-{first,repeat}-native.json`，摘要为同前缀`-summary.json`；共享`avatar-deep-rgctx-observations.json`相同。测试/比对见`rgctx-tests-final.log`、`rgctx-determinism.json`。

enum共享选项的pointer cell为`145bc8ef0`，不file-backed；其runtime值和默认类型映射未观测。按PE executable sections查限定REX.W RIP MOV/LEA，唯一raw byte候选`1402f8481`并非指令边界，因此有效写址候选为0。首次测试未区分raw candidate和真实指令而失败，原报告/`rgctx-tests-first.log`保留；现显式记录1 raw/0 valid，不把静态字节命中作为来源。该有限负搜索排除不了间接/vector/批量初始化，**不能选择有利flag或套用其它enum的共享body**。class range恢复不等于完整runtime初始化。

HashSet comparer方法身份已闭，具体enum共享target仍未闭；SortedDictionary准确14780已闭，canonical `<32758,Object>`的注册候选`1570f65b0`仍需+20/+28/+30/+38的KeyValuePairComparer/TreeSet构造继续执行，本轮另卡推进。完整18163 ctor仍缺+1e8/+1c0，因此保持`completeConstructor=false`，不把三个List片段当整体完成。

当前g2顺序规格应沿目标handler实际调用次序验证：身份/depot→生命态→装备及refresh→技能复杂输入→其它map/list→普通属性→FightProp清旧/应用→entity flush→61980池边界→标量/集合/optional child→tag29模式差异→tag38 Ornament选择→Costume reset/尾调用。此为固定目标静态顺序，不是已连续执行全链或生产发包次序许可。新tag38可关联“Avatar Ornament选择候选ID列表”的限定用途；当前`src/main/java`和旧/独立Avatar Proto的限定名称检索未见ornament业务getter，Java来源仍缺，不能直接造字段或发默认空列表。

```powershell
python tools/client72_avatar_deep_rgctx.py --client <GAME_PATH> --output <独立原始路径> --observations <独立摘要路径>
python -m unittest tools.test_client72_avatar_deep_rgctx -v
```

## 精确rgctx表与继承List：主线验收

`client72_avatar_deep_rgctx.py`从固定metadata恢复1116条有序class range和definition表（base分别106fda4/40925ec），按目标owner/slot恢复：2147/1→spec575232/method15591；2034/0→spec574883/method14780；69137/1→spec580634/method10374。open泛型实参分别膨胀为20060、32758/51041、63831。reference canonicalizer`14052dd70`实际将63831/51041引用类型映射Object，不依赖enum option；准确List<Object> ctor`159111af0`实际进入Avatar`15183ff7a→15184003d`，三次分配后由原生store写+50/+1c8/+40，backing为明确已初始化空数组。类/MethodInfo/array storage及分配是明示模型；没有执行完整resolver/完整Avatar构造。

6组原生/7独立断言及主线第三回放同双跑：完整`9c3adfd698677c4cb83464c2bbc81819862361eff2a84d3dd76f33aa451f133e`，摘要`a8b29f1916799b754669bb1f609fd89f9a8427466217db1460a9503d7df642e0`；证明`local/migration72/astra-main/avatar-rgctx-review.json`。首次把原始MOV/LEA byte命中误当指令，独立边界断言失败后修为.pdata反汇编核对：1 raw/0有效，保留原报告和失败。搜索限REX.W RIP MOV/LEA，不是全二进制无初始化证明。

enum canonicalization依赖`145bc8ef0`运行时pointer cell，当前不是file-backed且其实际值未观测；不能任选flag或套同形int/u32 comparer。SortedDictionary已恢复准确重载14780，后续comparer/TreeSet精确链继续离线研究。复现：`python tools/client72_avatar_deep_rgctx.py --client <GAME_PATH> --output <独立原始路径> --observations <独立摘要路径>`，`python -m unittest tools.test_client72_avatar_deep_rgctx -v`。上述结果替代“34054中段三个stores未执行”的旧边界，但完整constructor/handler/g2仍false。
