# D02-R1 Avatar 初始化来源与连续构造合同

任务 `F-03d3f4d2-live-contract / D02-R1`，2026-11-07。接手 `52999ce`，按最新恢复指令继续研究；旧离线暂停文字不覆盖当前授权。本子卡只改新研究工具、测试与本文，客户端、调试器、Gradle、服务和提交由主线独占。

**新增结果已主线审核：enum 初始化 producer、System.Int32Enum 规范化和准确 HashSet 构造地址已闭合；在明确 warm managed 输入下，完整 `18163 .ctor/15183fe60` 首次连续执行并返回。** 两次新进程及主线第三回放11组原生完整/摘要逐字节相同，主线9项独立断言通过。自然 cold 初始化、完整 Avatar handler、正式 g2 和 D-02 仍未完成；没有生产 sender/Proto/配置/资源/补丁改动或部署。

## 计划与可区分假设

目标是复用已验 List/Sorted/后缀，取得 enum 和 cold 初始化的最小合法输入。原证据为三固定客户端输入、`avatar-deep`、`avatar-deep-rgctx` 和 `avatar-deep-sorted` 受审摘要。未知是 enum flag 的真实来源、20060 的规范化目标、冷 comparer 创建及同源装备/技能缓存。

旧工具把所有真实指令限定在 `.pdata` 函数区间内；新假设是初始化属于无 unwind 叶函数。最小实验先对 flag 邻域的候选指令重新分类，再以独立引用和完整叶函数边界确认来源；不把原始字节命中直接当指令。成功后只执行有来源的 leaf、规范化和完整构造；首个未知 cold/资源调用前停止，不补成功桩。验收包含固定 hash、独立路径/dirty/错误断言、双新进程结果，实际客户端由主线另验。

## 固定输入与实际证据

每次正式执行都从当前 `GAME_PATH` 读取并重新验证：

| 输入 | SHA-256 |
| --- | --- |
| EXE | `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5` |
| global metadata | `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a` |
| startup metadata | `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105` |

三份旧摘要在新工具 `INPUTS` 逐字节 pin；没有重跑旧 oracle 并重计为成果。新原始证据位于忽略目录 `local/migration72/d02-active/avatar/`：

| 产物 | SHA-256 / bytes |
| --- | --- |
| `first.json` 与 `repeat.json` | `4c65734171992e7b0ece76620f1fc364b39fde7610fdc9f5a2c0f2b19101f5ef` / 163906 |
| `first-summary.json` 与 `repeat-summary.json` | `ba05bc7bf0202a282040e4b5b0e70e615410512e908cdb5a3883823c40131464` / 62396 |
| 新工具源 | `d99db2856ad9d08aca06102c0056f56c4c9356de83c0dfd253027847c66afa49` |

`determinism.json` 逐字节核同，`tests-final.log` 记录9项全通过。完整报告附所有使用源码 hash、原生代码窗口 hash、指令访问计数、模型调用与明确边界。测试通过只支持本文范围。

## 更正旧初始化来源结论

`145bc8ef0` 本身仍是非 file-backed runtime cell，但其 producer 并非未知：

1. 前一个 `.pdata` 函数 `1402f8400` 在 `1402f8447` 结束，随后是 `int3` padding。独立叶函数从 `1402f8450` 开始，`1402f8496 ret`，没有自己的 unwind 表项。
2. `1402f847a` 取得 `1422f9988`，`1402f8481` 将其真实写入 `145bc8ef0`。完整叶函数11条原指令执行，初始null及高64位旧指针均被覆盖，未调用任何模型。
3. `1402f84a7` 独立 `lea rdx,[rip-5e]` 精确引用叶入口，随后尾调 `14050b8c0` 注册。该引用和完整 leaf 的边界共同证明这是代码；没有执行整个注册树或自然启动顺序。
4. `1422f9988` 位于只读 PE 区段，固定文件前8字节是 `01 01 00 00 07 00 00 00`。规范化函数实际只读其首 byte；因此本有来源初始化记录选择非零分支，未任选有利 flag。

leaf 字节 hash 为 `1e8b5672f2b0f6722a607b2f2ec85f64cc250ba9c4c086b747148094c1cce2d8`；引用 wrapper 为 `2e16e4ae54f072f523c5e7cce608fa06dcf210c9fc9af6d36930e10bcfca5d29`。旧 `rgctx` “raw候选不是指令边界”的结论仅反映其 `.pdata` 搜索边界，不能继续作为缺 producer 的证据。旧工具和已受审摘要保持原样，修正在本独立专题。

## enum20060 到准确共享构造地址

metadata 独立确认20060继承 `System.Enum/204`，`value__` 为 int32。`14052dd70` 的实际 enum 分支经过 `14052df40` 取得 underlying type，再由 int32 分派到 `14052de91`，读取 default-class cell `145bc9638`。

该 cell 的生产路径 `140543452…14054346c` 向 `14051f560` 传入具名 `System` 与 `Int32Enum` literal，并保存返回类。metadata 对应 `System.Int32Enum/351`。它不是普通 System.Int32，也不是凭同形代码选中的 enum57848。

实际离线实验在明确已初始化的 metadata class/cache 内存中执行：

- 原始 `1405269f0` trampoline 及 `15a4d6b60` 的真实 cached-class 分支；没有 class lookup 成功模型。
- 完整 `14052dd70/14052df40`，20060得到351的 type 指针；普通 int32 对照保留原指针。
- 清除20060 class-cache入口后，真实路径在 `15a4d6c03` 停止，未执行锁和创建分支。

准确 AOT 对应 `method15591 / classInstance65 / MethodSpec352233 / table418926 / pointer13283 → 156fbc0b0`。它的泛型参数是351。原20060闭合 MethodInfo/对象类仍保持20060，仅共享机器码选用规范化目标，不把对象类型替换成351。

所用 enum class、underlying class、default class、relative class reference 和 cache 数组是显式 metadata storage fixture。该实验并不声称执行了这些类的自然创建或整个 MethodInfo inflation/resolver。

## 完整 warm 构造与边界

准确 enum 构造 `156fbc0b0` 在 nonnull comparer 下真实写 `+10`，并把 `+28/+2c/+30/+34` 写为 `[0,0,0,ffffffff]`。dirty反例中 `+18..+27` 的16字节保持 `7d`；构造不等价于全面清 payload。null目标在 `156fbc0f2` 错误 call 前停止。

复用旧已验 typed allocation、空数组、warm opaque comparer 和精确 RGCTX 后，新工具一次从 `15183fe60` 运行到真实 `15184033a ret`。fresh/预置旧值两个样本都完整返回，真实穿过：

- `15183ff11 → 156fbc0b0`，保存 `model+1e8` 的 HashSet<20060>；
- `15183ff71` 的准确 Sorted comparer/TreeSet 链，保存 `model+1c0` 的 SortedDictionary<32758,51041>；
- 三个准确继承 List 构造调用，保存 `model+50/+1c8/+40` 的34054；
- 其余12处既有容器 stores、服装子构造及正常 epilogue。

每条完整路径只有31次已声明 typed allocator 模型；enum/Sorted/List/服装构造没有被换成成功返回。`+284` 写1，`+2b0` 写true，五个旧缺口全为真实非null对象。这里是**同一完整函数连续执行**，不再是把四片段拼接称完整。

`completeWarmManagedConstructor=true` 表示上述有限执行；`completeConstructor=false` 保留自然首次创建合同未闭。将 enum comparer cache 清零时，完整构造真实到 `157d7feeb` 的待解析类型读取前停止；没有进入未知反射创建。完整 `15184e100` handler、装备refresh、技能、池和场景监听者仍不在本卡执行范围。

继承观察器的 `executedAddresses` 包含停止地址的**执行前观察**。测试用准确后继未到达证明停止边界，不能把这个字段计数误读为停止指令已执行。

## cold 剩余输入与主线最小自然观察

补充静态文件 `cold.json` SHA-256 `f285884cfcea2de4b43ba6a1361d6bc4529e3d809d1a72464a20ab6b10e979ef` 核明 `159b6e720` 是 type1328 `ComparerHelpers` 的 method10494 `CreateDefaultEqualityComparer`。`EqualityComparer<T>/1234` 的 static field `defaultComparer` 为offset0，getter10288；未知不是一个可随意返成功的 helper。

getter冷路径依次使用 `157d7feeb` 的 RGCTX slot+18、`140517680` 的类解析、`140538230` 的 RuntimeType cache/创建，再进入 `159b6e720`。后者还会检查类型和 underlying enum 并做泛型 comparer 构造。静态身份已核，完整反射/泛型创建及自然cache来源尚未执行。不得以warm缓存指针替代冷创建证据。

静态VA基址为 `140000000`；自然观察须按主线重新核对的模块基址B加RVA，不能把静态VA当当次地址。主线若自然启动未到Avatar，不为本卡追加启动点或重新load。

| 位置/新输入 | 最小读取 | 可解除的问题 |
| --- | --- | --- |
| 任一安全自然停点、runtime初始化后 | `B+5bc8ef0` qword及所指首byte，`B+5bc9638` class指针；记录模块hash与来源时刻 | 复核固定producer在自然进程已执行；无需再搜未知flag producer |
| Avatar实际 `B+1183ff11`，call前 | r14=model、rdi=HashSet、rdx=comparer、r8=MethodInfo、rsi=callee；r8前0x40，`[r8]+30` context及slot | 准确闭合实例的自然callee与warm comparer来源；不能由离线模型代替 |
| `B+17d7feeb` 冷getter | rsi=MethodInfo；`[rsi]` class、class+30、slot+10 comparer class及class+38 cache、slot+18 type；每层实际指针/读取范围 | 得到反射创建所需的真实typed输入，而非全进程dump |
| `B+19b6e720` helper入口（仅自然命中） | rcx的真实RuntimeType对象头及+10类型指针，调用返回和写回defaultComparer前后 | 分辨创建分支/返回类型，继续精确离线重放；没有自然命中不写成功 |

完整构造仍非完整handler。装备refresh在 `151843490` 需要GLOBAL+34900同源资源/派生项/delegate；技能要同一角色当前depot/技能资源及保存恢复时序；61980池责任、真实entity/监听者仍要来源。上述不能被本次enum突破一并关闭。

## Java来源与实现规格

复用[有限Java投影](CLIENT_72_AVATAR_JAVA_PROJECTION.md)及[Avatar深入规格](CLIENT_72_AVATAR_DEEP_RESEARCH.md)，本卡不创建Java producer。正式实现时需保留以下准确来源与缺口：

| 字段 | 当前真实Java读取点 | 当前规格/缺口 |
| --- | --- | --- |
| 48178 tag1/2 | `Avatar.getAvatarId()`、`Avatar.getGuid()` | 已验有限投影；完整uint64保留，不能用角色ID代GUID |
| tag3三属性 | `getExp()`、`getPromoteLevel()`、`getLevel()` | 1001/1002/4001，值为0仍保留既有显式oneof规格 |
| tag5装备GUID | `Avatar.getEquips()`中同一`GameItem.getGuid()` | Store同实例关联已验；真实itemID/资源/槽应用仍未闭，不拼旧0/10001/11101样本 |
| tag4/7 | `Avatar.getFightProperties()`、`getFightProperty(FightProperty)`；没有已审核的7.2生命态getter映射 | `Inventory.loadFromDatabase → AvatarStorage.postLoad → recalcStats → completeLoadingVitals → restoreLoadingVitals(true)`完成后才能取最终值。旧toProto常量生命态不作来源；空FightProp清旧仍不能作默认策略 |
| tag11当前depot | `Avatar.getSkillDepotId()` | 必须保留保存当前值与资源默认depot的区别；同版本客户端资源一致性未闭 |
| tag13/14/15/17/35 | 现有`getSkillLevelMap()`、`getTalentIdList()`、`getInherentProudSkillList()`、`getProudSkillBonusMap()`只是待比对源码来源 | 未建立这些getter到各raw字段独立语义，不能按近名接线；+68与+6c不可互换 |
| tag29 | 未有已审核业务getter | 只有六模式位图消费；本次构造出HashSet不证明完整模式差异/效果，禁止填0求通 |
| tag38 | 未有已审核业务getter | `getCostume()`不能充当Ornament来源；需要真实资源parser/cache-builder及当前选择保存合同 |

正式g2仍须完整必要handler/后缀与同源业务状态闭合，再由实际Java输出经独立adapter、目标原生完整消费和新旧号实机反核。本次明确不批准生产sender，三层/required/runtime/ready不晋级。

资源线已取得固定候选两张Ornament原JSON：Group SHA `f1a4885c2fdff06021263e4d6627feca4ff135547820e85a814eecb6495122da`，Ornament SHA `aad42c01df12aaa4956374226e26db11cdbd3d72a5586e4c9bf2e4415ae2287a`，同候选commit `d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1`。Group唯一显式avatarId是10000136，不含出生5/7；这只是候选内容，未批准未知字段alias、客户端revision或tag38空/default。后继可独立追其parser/cache builder，不能把候选直接当自然已加载缓存。

## 复现、失败与交付

```powershell
$avatarLiveClient = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/game_path.ps1
python tools/client72_avatar_live_contract.py --client $avatarLiveClient --output local/migration72/d02-active/avatar/first.json --observations local/migration72/d02-active/avatar/first-summary.json
python tools/client72_avatar_live_contract.py --client $avatarLiveClient --output local/migration72/d02-active/avatar/repeat.json --observations local/migration72/d02-active/avatar/repeat-summary.json
python -m unittest tools.test_client72_avatar_live_contract -v
```

测试默认读取`first.json`；主线第三回放可设置`CLIENT72_AVATAR_LIVE_REPORT`为独立报告路径。需要先实际生成报告，不接受缺输入skip。

失败完整保留：`probe1.failure.json/.log`中的cold类缓存越界，是旧观察模式上界没有覆盖真实 `15a4d…`，新工具改用**已有**全指令观察模式后按预期停点；公共runtime不变。`tests-probe.log`两项失败是把执行前停止观察当已执行计数，依据共享`fragment`实际实现改为11条leaf指令加return-stop、并断言冷边界后继未到达；未改原生结果或放宽未知函数行为。首次邻域候选扫描输出大于所需，后续只用精确leaf、注册引用和具名初始化窗口；不重跑大范围搜索。

主线已审核新工具/测试、明确模型与全部非零/dirty/冷错误边界，独立执行第三原生及9项断言；`local/migration72/d02-active/main/avatar-third{,-summary}.json`与代理双跑逐字节同，`avatar-third-proof.json`保存hash比较。原始旧失败保留。纯研究无需部署或重启；本项分项本地提交，不推送。cold精确typed反射输入/Ornament parser与cache-builder继续独立推进；本卡不操作主线进程或断点。
